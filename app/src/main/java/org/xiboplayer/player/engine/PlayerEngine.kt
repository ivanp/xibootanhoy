package org.xiboplayer.player.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.xiboplayer.player.api.OkHttpClientWrapper
import org.xiboplayer.player.api.XmdsClient
import org.xiboplayer.player.model.*
import org.xiboplayer.player.storage.FileCache
import org.xiboplayer.player.util.Logger
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

/**
 * Player engine — the main collect loop and schedule management.
 *
 * Mirrors the arexibo mainloop.rs collect cycle:
 * 1. RegisterDisplay (re-auth, get updated settings)
 * 2. RequiredFiles (get list of needed media/layouts + purge list)
 * 3. Purge stale files
 * 4. GetSchedule (get current schedule)
 * 5. Download missing files (HTTP or XMDS GetFile with MD5 verification)
 * 6. SubmitMediaInventory (report download results)
 * 7. Apply schedule, notify renderer of layout changes
 * 8. SubmitLog (send buffered log entries)
 * 9. NotifyStatus (send disk space, current layout)
 */
class PlayerEngine(
    private val cmsSettings: CmsSettings,
    private val cacheDir: File,
    private val logger: Logger,
    private val scope: CoroutineScope
) {
    private val httpClient = OkHttpClientWrapper()
    private val xmds = XmdsClient(cmsSettings, httpClient, logger)
    private val cache = FileCache(cacheDir, logger)
    private val xlfParser = XlfParser(logger)

    private var playerSettings: PlayerSettings = PlayerSettings()
    private var currentSchedule: Schedule = Schedule()
    private var currentLayouts: List<Long> = emptyList()
    private var currentLayoutIndex = 0

    private val _layoutState = MutableStateFlow<LayoutState>(LayoutState.Idle)
    val layoutState: StateFlow<LayoutState> = _layoutState

    private val _playerStatus = MutableStateFlow(PlayerStatus())
    val playerStatus: StateFlow<PlayerStatus> = _playerStatus

    private var collectJob: Job? = null
    private var scheduleCheckJob: Job? = null

    /**
     * Start the player engine.
     */
    fun start() {
        logger.info("Player engine starting")
        collectJob = scope.launch(Dispatchers.IO) {
            // Initial collect immediately
            try {
                collectOnce()
            } catch (e: Exception) {
                logger.error("Initial collect failed: ${e.message}")
            }

            // Periodic collect loop
            while (isActive) {
                delay(playerSettings.collectInterval * 1000L)
                try {
                    collectOnce()
                } catch (e: Exception) {
                    logger.error("Collect cycle failed: ${e.message}")
                }
            }
        }

        // Schedule check every 60 seconds
        scheduleCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000L)
                checkSchedule()
            }
        }
    }

    /**
     * Stop the player engine.
     */
    fun stop() {
        collectJob?.cancel()
        scheduleCheckJob?.cancel()
        logger.info("Player engine stopped")
    }

    /**
     * Get the current player settings.
     */
    fun getPlayerSettings(): PlayerSettings = playerSettings

    /**
     * Get the XMDS client (for direct API access).
     */
    fun getXmdsClient(): XmdsClient = xmds

    /**
     * Get the file cache.
     */
    fun getCache(): FileCache = cache

    /**
     * Get the current layout to display.
     */
    fun getCurrentLayout(): LayoutInfo? {
        if (currentLayouts.isEmpty()) return null
        val layoutId = currentLayouts.getOrNull(currentLayoutIndex) ?: return null
        return cache.getLayout(layoutId)
    }

    /**
     * Navigate to the next layout.
     */
    fun nextLayout() {
        if (currentLayouts.isEmpty()) return
        currentLayoutIndex = (currentLayoutIndex + 1) % currentLayouts.size
        val layoutId = currentLayouts[currentLayoutIndex]
        val layout = cache.getLayout(layoutId)
        if (layout != null) {
            _layoutState.value = LayoutState.Showing(layout)
        }
    }

    /**
     * Navigate to a specific layout.
     */
    fun jumpToLayout(layoutId: Long) {
        val idx = currentLayouts.indexOf(layoutId)
        if (idx >= 0) {
            currentLayoutIndex = idx
            val layout = cache.getLayout(layoutId)
            if (layout != null) {
                _layoutState.value = LayoutState.Showing(layout)
            }
        }
    }

    // ─── Collect cycle ─────────────────────────────────────────────

    private fun collectOnce() {
        logger.info("Starting collect cycle")

        // 1. RegisterDisplay
        val settings: PlayerSettings?
        try {
            settings = xmds.registerDisplay()
        } catch (e: Exception) {
            logger.error("RegisterDisplay failed: ${e.message}")
            logger.error("Stack trace: ${e.stackTraceToString()}")
            return
        }
        if (settings == null) {
            logger.warn("Display not authorized — will retry")
            return
        }
        playerSettings = settings
        logger.info("RegisterDisplay successful, collectInterval=${settings.collectInterval}")

        // 2. RequiredFiles
        val (required, purge) = xmds.requiredFiles()

        // 3. Purge stale files
        cache.purgeFiles(purge)

        // 4. Get schedule
        currentSchedule = xmds.getSchedule()

        // 5. Download missing files
        val inventory = mutableListOf<Pair<String, Boolean>>()
        for (file in required) {
            if (!cache.hasFile(file)) {
                logger.info("Downloading: ${file.name} (${file.type})")
                try {
                    downloadFile(file)
                    inventory.add(Pair("${file.type}|${file.id}", true))
                } catch (e: Exception) {
                    logger.error("Failed to download ${file.name}: ${e.message}")
                    inventory.add(Pair("${file.type}|${file.id}", false))
                }
            }
        }

        // 6. SubmitMediaInventory
        if (inventory.isNotEmpty()) {
            xmds.submitMediaInventory(inventory)
        }

        // 7. Apply schedule
        checkSchedule()

        // 8. SubmitLog
        val logEntries = logger.popEntries()
        if (logEntries.isNotEmpty()) {
            try {
                xmds.submitLog(logEntries)
            } catch (e: Exception) {
                logger.error("Failed to submit logs: ${e.message}")
            }
        }

        // 9. NotifyStatus
        try {
            val status = PlayerStatus(
                currentLayoutId = currentLayouts.getOrNull(currentLayoutIndex) ?: 0L,
                availableSpace = cache.availableSpace(),
                totalSpace = cache.totalSpace(),
                deviceName = playerSettings.displayName,
                timeZone = java.util.TimeZone.getDefault().id
            )
            xmds.notifyStatus(status)
            _playerStatus.value = status
        } catch (e: Exception) {
            logger.error("Failed to notify status: ${e.message}")
        }

        logger.info("Collect cycle complete")
    }

    /**
     * Download a single file from the CMS.
     *
     * Small files (<5MB) are loaded in-memory and verified by MD5.
     * Large files are streamed directly to disk to avoid OOM on low-end devices.
     */
    private fun downloadFile(file: RequiredFile) {
        val STREAM_THRESHOLD = 5_000_000L  // 5MB — stream anything larger

        if (!file.downloadUrl.isNullOrEmpty()) {
            try {
                val url = file.downloadUrl.replace("&amp;", "&")
                logger.info("Downloading via HTTP: ${file.name} from $url")

                if (file.size > STREAM_THRESHOLD) {
                    // Stream directly to disk — no in-memory buffer
                    val dest = cache.getOutputPath(file)
                    val md5 = httpClient.downloadToFile(url, dest)
                    if (file.md5.isEmpty() || md5 == file.md5) {
                        logger.info("Streamed ${file.name} (${file.size} bytes) to $dest")
                        return
                    }
                    logger.warn("MD5 mismatch for ${file.name}, trying XMDS")
                    // Remove the bad file
                    java.io.File(dest).delete()
                } else {
                    // Small file — load in-memory and verify
                    val data = httpClient.getBytes(url)
                    val md5 = md5Hex(data)
                    if (file.md5.isEmpty() || md5 == file.md5) {
                        cache.storeFile(file, data)
                        return
                    }
                    logger.warn("MD5 mismatch for ${file.name}, trying XMDS")
                }
            } catch (e: Exception) {
                logger.warn("HTTP download failed for ${file.name}: ${e.message}")
            }
        }

        // Fallback to XMDS GetFile (chunked) — only for files under stream threshold
        // since XMDS GetFile returns base64 in SOAP which must be decoded in memory
        if (file.size <= STREAM_THRESHOLD) {
            val chunkSize = 1_000_000L
            var offset = 0L
            val chunks = mutableListOf<ByteArray>()

            while (true) {
                try {
                    val chunk = xmds.getFileData(file.id, file.type, offset, chunkSize)
                    if (chunk.isEmpty()) break
                    chunks.add(chunk)
                    offset += chunkSize
                } catch (e: Exception) {
                    logger.warn("XMDS GetFile failed for ${file.name}: ${e.message}")
                    break
                }
            }

            if (chunks.isNotEmpty()) {
                val data = chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
                cache.storeFile(file, data)
            }
        } else {
            logger.warn("Skipping XMDS fallback for ${file.name}: too large for SOAP transfer")
        }
    }

    /**
     * Check if the current schedule has new layouts.
     */
    private fun checkSchedule() {
        val newLayouts = currentSchedule.layoutsNow()
        if (newLayouts != currentLayouts) {
            logger.info("Schedule changed: ${newLayouts.joinToString()}")
            currentLayouts = newLayouts
            currentLayoutIndex = 0

            if (currentLayouts.isNotEmpty()) {
                val layoutId = currentLayouts[0]
                val layout = cache.getLayout(layoutId)
                if (layout != null) {
                    _layoutState.value = LayoutState.Showing(layout)
                }
            } else {
                _layoutState.value = LayoutState.Idle
            }
        }
    }

    companion object {
        private fun md5Hex(data: ByteArray): String {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(data)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Layout state for the UI.
 */
sealed class LayoutState {
    data object Idle : LayoutState()
    data class Showing(val layout: LayoutInfo) : LayoutState()
    data class Error(val message: String) : LayoutState()
}

/**
 * Extension to get active layouts from a schedule.
 */
fun Schedule.layoutsNow(): List<Long> {
    val now = System.currentTimeMillis()
    val tz = java.util.TimeZone.getDefault()

    var curPrio = Int.MAX_VALUE
    val layouts = mutableListOf<Long>()

    for (entry in entries) {
        val from = parseXiboDate(entry.fromDt, tz)
        val to = parseXiboDate(entry.toDt, tz)
        if (from != null && to != null && now in from..to) {
            when {
                entry.priority < curPrio -> {
                    curPrio = entry.priority
                    layouts.clear()
                    layouts.add(entry.layoutId)
                }
                entry.priority == curPrio -> {
                    layouts.add(entry.layoutId)
                }
            }
        }
    }

    if (layouts.isEmpty() && default != null) {
        layouts.add(default!!)
    }

    return layouts
}

/**
 * Parse a Xibo date string (e.g. "2024-01-15 09:00:00") to epoch millis.
 */
private fun parseXiboDate(dateStr: String, tz: java.util.TimeZone): Long? {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = tz
        sdf.parse(dateStr)?.time
    } catch (e: Exception) {
        null
    }
}
