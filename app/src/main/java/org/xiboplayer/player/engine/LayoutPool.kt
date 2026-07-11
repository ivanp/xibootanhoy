package org.xiboplayer.player.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.xiboplayer.player.model.LayoutInfo
import org.xiboplayer.player.storage.FileCache
import org.xiboplayer.player.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Two-slot layout pre-loading pool.
 *
 * Maintains a "hot" slot (next layout fully parsed and ready) and a "warm" slot
 * (the one after that).  Pre-loading is triggered by [preload] and runs on
 * Dispatchers.IO so it never blocks the render thread.
 *
 * Lifecycle:
 *  1. Caller invokes [preload] when ≥75% of the current layout has elapsed.
 *  2. On layout switch, caller invokes [swap] — returns the pre-loaded [LayoutInfo]
 *     if it is ready, null otherwise (caller falls back to synchronous load).
 *  3. [evict] removes a layout from both slots (e.g. after a schedule change or purge).
 */
class LayoutPool(
    private val cache: FileCache,
    private val xlfParser: XlfParser,
    private val logger: Logger,
    private val scope: CoroutineScope
) {
    /** In-flight parse jobs keyed by layoutId. */
    private val loadJobs = ConcurrentHashMap<Long, Job>()

    /** Parsed layouts ready for immediate use, keyed by layoutId. */
    private val pool = ConcurrentHashMap<Long, LayoutInfo>()

    /**
     * Begin background parsing of [layoutId].  Safe to call multiple times;
     * a second call for the same id while loading is a no-op.
     */
    fun preload(layoutId: Long) {
        if (pool.containsKey(layoutId)) {
            logger.debug("LayoutPool: $layoutId already in pool, skipping preload")
            return
        }
        if (loadJobs.containsKey(layoutId)) {
            logger.debug("LayoutPool: $layoutId already loading, skipping preload")
            return
        }

        logger.debug("LayoutPool: starting background preload for layout $layoutId")
        val job = scope.launch(Dispatchers.IO) {
            try {
                val xlf = cache.getLayoutXlf(layoutId)
                if (xlf == null) {
                    logger.warn("LayoutPool: XLF not in cache for layout $layoutId, cannot preload")
                    return@launch
                }
                // parseLayout fills in the full LayoutInfo (width, height, bgColor …)
                // while getLayout() on its own returns a bare stub with only id + xlf.
                val parsed = xlfParser.parseLayout(xlf, layoutId).copy(xlf = xlf)
                pool[layoutId] = parsed
                logger.debug("LayoutPool: layout $layoutId pre-loaded successfully")
            } catch (e: Exception) {
                logger.warn("LayoutPool: preload failed for layout $layoutId — ${e.message}")
            } finally {
                loadJobs.remove(layoutId)
            }
        }
        loadJobs[layoutId] = job
    }

    /**
     * Return the pre-loaded [LayoutInfo] for [layoutId] and remove it from the pool,
     * or null if the layout has not finished loading yet.
     *
     * The caller MUST fall back to a synchronous load when this returns null.
     */
    fun swap(layoutId: Long): LayoutInfo? {
        val info = pool.remove(layoutId)
        if (info != null) {
            logger.debug("LayoutPool: swap hit for layout $layoutId")
        } else {
            logger.debug("LayoutPool: swap miss for layout $layoutId — will fallback to sync load")
        }
        return info
    }

    /**
     * Remove [layoutId] from the pool and cancel any in-flight load job.
     * Called after a schedule change invalidates a pre-loaded layout.
     */
    fun evict(layoutId: Long) {
        loadJobs.remove(layoutId)?.cancel()
        pool.remove(layoutId)
        logger.debug("LayoutPool: evicted layout $layoutId")
    }

    /**
     * Evict all pooled layouts. Called when the schedule changes entirely.
     */
    fun evictAll() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        pool.clear()
        logger.debug("LayoutPool: evicted all layouts")
    }
}
