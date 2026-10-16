package com.powerk.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Server push channel with automatic reconnect; callbacks arrive on OkHttp threads. */
class Realtime(
    private val scope: CoroutineScope,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onEvent: (JSONObject) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
    private var loop: Job? = null
    @Volatile private var socket: WebSocket? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        loop?.cancel()
        socket?.cancel()
    }

    /** Drops the current socket; the loop reconnects right away. */
    fun reconnect() {
        socket?.cancel()
    }

    private suspend fun run() {
        var failures = 0
        while (coroutineContext.isActive) {
            val closed = CompletableDeferred<Unit>()
            var opened = false
            val request = Request.Builder()
                .url(Server.base.replaceFirst("http", "ws") + "/ws")
                .apply { if (Server.token.isNotBlank()) header("X-Token", Server.token) }
                .build()
            val ws = runCatching {
                client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened = true
                        onConnected()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { JSONObject(text) }.getOrNull()?.let(onEvent)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Diag.w("ws", "failure: ${t.javaClass.simpleName} ${t.message} http=${response?.code}")
                        closed.complete(Unit)
                    }
                })
            }.getOrNull()
            if (ws == null) {
                failures++
            } else {
                socket = ws
                try {
                    closed.await()
                } finally {
                    ws.cancel()
                }
                onDisconnected()
                failures = if (opened) 0 else failures + 1
            }
            delay(minOf(1000L shl minOf(failures, 4), 15_000L))
        }
    }
}
