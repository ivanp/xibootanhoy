package org.xiboplayer.player.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xiboplayer.player.model.Command
import org.xiboplayer.player.util.Logger
import java.util.concurrent.TimeUnit

/**
 * Result of a command execution.
 */
data class CommandResult(
    val success: Boolean,
    val output: String = ""
)

/**
 * Executes CMS commands (shell, HTTP, RS232) and reports results.
 *
 * Shell commands run via Runtime.exec() with a 30s timeout.
 * HTTP commands run via OkHttp GET/POST with a 30s timeout.
 * RS232 commands are stubbed (Android lacks a standard serial port API).
 *
 * All commands are validated against the command's validationString
 * before being reported as successful.
 */
class CommandExecutor(
    private val logger: Logger
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Execute a command and return the result.
     *
     * Determines the command type from the commandString format:
     * - Starts with `http://` or `https://` → HTTP command
     * - Contains `dev=` and `baud=` → RS232 command (stub)
     * - Everything else → Shell command
     *
     * Validates the output against [Command.validationString] if set.
     * An empty commandString is a no-op that reports failure.
     */
    fun execute(command: Command): CommandResult {
        val cmdStr = command.commandString.trim()
        if (cmdStr.isEmpty()) {
            logger.warn("CommandExecutor: empty command string")
            return CommandResult(success = false, output = "Empty command string")
        }

        logger.info("CommandExecutor: executing '${cmdStr.take(100)}'")

        val result = when {
            isHttpCommand(cmdStr) -> executeHttp(cmdStr)
            isRs232Command(cmdStr) -> executeRs232(cmdStr)
            else -> executeShell(cmdStr)
        }

        // Validate against validation string
        if (command.validationString.isNotEmpty()) {
            val validated = result.output.contains(command.validationString)
            if (!validated) {
                logger.warn(
                    "CommandExecutor: validation failed — " +
                        "expected '${command.validationString}' in output, " +
                        "got '${result.output.take(200)}'"
                )
                return CommandResult(success = false, output = result.output)
            }
            logger.info("CommandExecutor: validation passed")
        }

        return result
    }

    // ─── Type detection ────────────────────────────────────────────

    private fun isHttpCommand(cmdStr: String): Boolean =
        cmdStr.startsWith("http://") || cmdStr.startsWith("https://")

    private fun isRs232Command(cmdStr: String): Boolean =
        cmdStr.contains("dev=") && cmdStr.contains("baud=")

    // ─── Shell commands ─────────────────────────────────────────────

    private fun executeShell(commandString: String): CommandResult {
        return try {
            val parts = commandString.split("\\s+".toRegex())
            if (parts.isEmpty()) {
                return CommandResult(success = false, output = "Empty command")
            }

            val process = Runtime.getRuntime().exec(parts.toTypedArray())
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val completed = process.waitFor(30, TimeUnit.SECONDS)

            process.destroy()

            val resultOutput = if (error.isNotEmpty()) error else output
            val success = completed && process.exitValue() == 0

            logger.info(
                "CommandExecutor: shell command completed, " +
                    "exit=${process.exitValue()}, " +
                    "output='${resultOutput.take(200)}'"
            )
            CommandResult(success = success, output = resultOutput.trim())
        } catch (e: Exception) {
            logger.error("CommandExecutor: shell command failed: ${e.message}")
            CommandResult(success = false, output = e.message ?: "Unknown error")
        }
    }

    // ─── HTTP commands ──────────────────────────────────────────────

    private fun executeHttp(commandString: String): CommandResult {
        return try {
            val (url, isPost) = parseHttpCommand(commandString)
            val request = if (isPost) {
                Request.Builder()
                    .url(url)
                    .post(okhttp3.RequestBody.Companion.create(null, ByteArray(0)))
                    .build()
            } else {
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            }

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val success = response.isSuccessful

            logger.info(
                "CommandExecutor: HTTP command completed, " +
                    "status=${response.code}, " +
                    "body='${body.take(200)}'"
            )
            CommandResult(success = success, output = body.trim())
        } catch (e: Exception) {
            logger.error("CommandExecutor: HTTP command failed: ${e.message}")
            CommandResult(success = false, output = e.message ?: "Unknown error")
        }
    }

    /**
     * Parse HTTP command string to extract URL and HTTP method.
     *
     * Defaults to GET. If the string contains "method=post" (case-insensitive),
     * uses POST with an empty body.
     */
    private fun parseHttpCommand(cmdStr: String): Pair<String, Boolean> {
        val lower = cmdStr.lowercase()
        val isPost = lower.contains("method=post")
        // Take the first token as the URL, stripping any method hints
        val url = cmdStr.split("\\s+".toRegex()).first()
        return Pair(url, isPost)
    }

    // ─── RS232 commands (stub) ──────────────────────────────────────

    private fun executeRs232(commandString: String): CommandResult {
        logger.warn("CommandExecutor: RS232 commands are not supported on Android: $commandString")
        return CommandResult(success = false, output = "RS232 not supported on Android")
    }
}
