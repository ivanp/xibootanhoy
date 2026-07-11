package org.xiboplayer.player.util

import org.xiboplayer.player.model.LogEntry
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Simple logger that buffers entries for CMS submission.
 * Mirrors arexibo's logger.rs pattern.
 */
class Logger(private val maxEntries: Int = 1000) {

    private val entries = ConcurrentLinkedQueue<LogEntry>()
    private val buffer = StringBuilder()

    fun debug(message: String) {
        log("debug", message)
    }

    fun info(message: String) {
        log("info", message)
    }

    fun warn(message: String) {
        log("warn", message)
    }

    fun error(message: String) {
        log("error", message)
    }

    private fun log(category: String, message: String) {
        val entry = LogEntry(
            date = System.currentTimeMillis() / 1000,
            category = category,
            message = message
        )
        entries.add(entry)
        if (entries.size > maxEntries) {
            entries.poll()
        }

        // Also log to logcat
        buffer.append("[${entry.date}][$category] $message\n")
        android.util.Log.d("XiboPlayer", "[$category] $message")
    }

    /**
     * Drain all buffered entries for CMS submission.
     */
    fun popEntries(): List<LogEntry> {
        val result = mutableListOf<LogEntry>()
        while (true) {
            val entry = entries.poll() ?: break
            result.add(entry)
        }
        return result
    }

    /**
     * Get the full log as a string.
     */
    fun getLogText(): String = buffer.toString()
}
