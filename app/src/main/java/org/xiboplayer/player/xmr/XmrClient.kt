package org.xiboplayer.player.xmr

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import org.xiboplayer.player.model.XmrMessage
import org.xiboplayer.player.util.Logger
import java.util.concurrent.TimeUnit

/**
 * XMR WebSocket client for receiving real-time messages from the CMS.
 *
 * Connects to the XMR relay, sends an init handshake with channel and key,
 * and dispatches incoming JSON messages by their `action` field.
 *
 * Handles reconnection with exponential backoff (1s -> 2s -> 4s -> max 30s)
 * and drops messages past their TTL expiry.
 */
class XmrClient(
    private val networkAddress: String,
    private val channel: String,
    private val key: String,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val onMessage: (XmrMessage) -> Unit
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for long-lived WS
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // Keepalive pings
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var backoffDelay = 1_000L
    private val maxBackoffDelay = 30_000L

    /**
     * Start the WebSocket connection.
     */
    fun start() {
        connect()
    }

    /**
     * Stop the WebSocket connection and cancel reconnection.
     */
    fun stop() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Player stopping")
        webSocket = null
        backoffDelay = 1_000L
    }

    private fun connect() {
        val wsUrl = "ws://$networkAddress"
        logger.info("XMR connecting to $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                logger.info("XMR WebSocket opened")
                backoffDelay = 1_000L // Reset backoff on successful connection
                sendInit(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                logger.debug("XMR WebSocket closing: $code $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                logger.info("XMR WebSocket closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                logger.warn("XMR WebSocket failure: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    /**
     * Send the init handshake with channel and key.
     */
    private fun sendInit(ws: WebSocket) {
        val init = JsonObject().apply {
            addProperty("type", "init")
            addProperty("channel", channel)
            addProperty("key", key)
        }
        val json = gson.toJson(init)
        val sent = ws.send(json)
        if (sent) {
            logger.info("XMR init handshake sent (channel=$channel)")
        } else {
            logger.warn("XMR init handshake send failed")
        }
    }

    /**
     * Handle an incoming JSON message.
     */
    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val action = json.get("action")?.asString ?: return
            val ttl = json.get("ttl")?.asLong ?: 0L

            // Drop messages past TTL expiry
            if (ttl > 0 && System.currentTimeMillis() / 1000 > ttl) {
                logger.debug("XMR dropping TTL-expired message: $action")
                return
            }

            logger.info("XMR received action: $action")

            val message = parseAction(action, json)
            if (message != null) {
                onMessage(message)
            } else {
                logger.debug("XMR unknown action: $action")
            }
        } catch (e: Exception) {
            logger.warn("XMR failed to parse message: ${e.message}")
        }
    }

    /**
     * Parse an action string into an XmrMessage.
     */
    private fun parseAction(action: String, json: JsonObject): XmrMessage? {
        return when (action.lowercase()) {
            "collectnow" -> XmrMessage.CollectNow
            "screenshot" -> XmrMessage.Screenshot
            "purge" -> XmrMessage.Purge
            "changelayout" -> {
                val layoutId = json.get("layoutId")?.asLong
                    ?: json.get("layoutid")?.asLong
                    ?: return null
                XmrMessage.ChangeLayout(layoutId)
            }
            "overlaylayout" -> {
                val layoutId = json.get("layoutId")?.asLong
                    ?: json.get("layoutid")?.asLong
                    ?: return null
                XmrMessage.OverlayLayout(layoutId)
            }
            "reverttoschedule" -> XmrMessage.RevertToSchedule
            "webhook" -> {
                val code = json.get("code")?.asString ?: return null
                XmrMessage.WebHook(code)
            }
            "command" -> {
                val code = json.get("code")?.asString ?: return null
                XmrMessage.Command(code)
            }
            else -> null
        }
    }

    /**
     * Schedule reconnection with exponential backoff.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            logger.info("XMR reconnecting in ${backoffDelay}ms")
            delay(backoffDelay)
            backoffDelay = (backoffDelay * 2).coerceAtMost(maxBackoffDelay)
            connect()
        }
    }
}
