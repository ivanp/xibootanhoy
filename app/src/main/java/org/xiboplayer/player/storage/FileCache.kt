package org.xiboplayer.player.storage

import org.xiboplayer.player.model.*
import org.xiboplayer.player.util.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Local file cache for layouts, media, and resources.
 *
 * Directory structure:
 *   cache/
 *     layouts/    - XLF layout files
 *     media/      - downloaded media files (images, videos, etc.)
 *     resources/  - HTML resources
 *     content.json - cache index
 */
class FileCache(
    private val cacheDir: File,
    private val logger: Logger
) {
    private val layoutsDir = File(cacheDir, "layouts")
    private val mediaDir = File(cacheDir, "media")
    private val resourcesDir = File(cacheDir, "resources")

    init {
        layoutsDir.mkdirs()
        mediaDir.mkdirs()
        resourcesDir.mkdirs()
    }

    /**
     * Check if a required file is already cached.
     */
    fun hasFile(file: RequiredFile): Boolean {
        val path = resolvePath(file)
        val f = File(path)
        return f.exists() && f.length() > 0
    }

    /**
     * Store a downloaded file (in-memory — for small files only).
     */
    fun storeFile(file: RequiredFile, data: ByteArray) {
        val path = resolvePath(file)
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeBytes(data)
        logger.debug("Stored ${file.name} (${data.size} bytes) at $path")
    }

    /**
     * Get the output path for a required file (for streaming writes).
     */
    fun getOutputPath(file: RequiredFile): String {
        return resolvePath(file)
    }

    /**
     * Get the local path for a required file.
     */
    fun getFilePath(file: RequiredFile): String {
        return resolvePath(file)
    }

    /**
     * Resolve a bare media filename (e.g. "26.jpg") to a local file:// URI.
     * Returns null if the file is not in the cache.
     */
    fun resolveMediaUri(filename: String): String? {
        val file = File(mediaDir, filename)
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    /**
     * Get a layout by ID.
     */
    fun getLayout(layoutId: Long): LayoutInfo? {
        val layoutFile = File(layoutsDir, "${layoutId}.xlf")
        if (!layoutFile.exists()) return null

        val xlf = layoutFile.readText()
        return LayoutInfo(
            id = layoutId,
            xlf = xlf
        )
    }

    /**
     * Get the XLF content for a layout.
     */
    fun getLayoutXlf(layoutId: Long): String? {
        val layoutFile = File(layoutsDir, "${layoutId}.xlf")
        if (!layoutFile.exists()) return null
        return layoutFile.readText()
    }

    /**
     * Get the local file path for a media file.
     */
    fun getMediaPath(fileId: Long, extension: String = ""): String? {
        val dir = mediaDir
        // Search by fileId pattern
        val files = dir.listFiles { f -> f.name.startsWith("${fileId}.") }
        if (files != null && files.isNotEmpty()) {
            return files[0].absolutePath
        }
        // Try exact match
        val f = File(dir, "${fileId}$extension")
        return if (f.exists()) f.absolutePath else null
    }

    /**
     * Purge specific files.
     */
    fun purgeFiles(purgeList: List<String>) {
        for (path in purgeList) {
            val f = File(cacheDir, path)
            if (f.exists()) {
                f.delete()
                logger.debug("Purged $path")
            }
        }
    }

    /**
     * Purge entire cache.
     */
    fun purge() {
        layoutsDir.listFiles()?.forEach { it.delete() }
        mediaDir.listFiles()?.forEach { it.delete() }
        resourcesDir.listFiles()?.forEach { it.delete() }
        logger.info("Cache purged")
    }

    /**
     * Get available space in bytes.
     */
    fun availableSpace(): Long {
        return cacheDir.freeSpace
    }

    /**
     * Get total space in bytes.
     */
    fun totalSpace(): Long {
        return cacheDir.totalSpace
    }

    private fun resolvePath(file: RequiredFile): String {
        return when (file.type) {
            "layout" -> File(layoutsDir, "${file.id}.xlf").absolutePath
            "media" -> {
                val ext = file.name.substringAfterLast('.', "")
                File(mediaDir, "${file.id}.$ext").absolutePath
            }
            "resource" -> File(resourcesDir, "${file.id}.html").absolutePath
            else -> File(mediaDir, file.name).absolutePath
        }
    }
}
