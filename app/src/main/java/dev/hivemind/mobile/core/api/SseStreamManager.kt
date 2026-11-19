package dev.hivemind.mobile.core.api

import android.os.Handler
import android.os.Looper
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.core.models.StreamEvent
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Stream SSE resiliente: reconecta automaticamente com backoff exponencial
 * (1s → 2s → 4s → … até 30s) em trocas 4G↔Wi-Fi e quedas de rede.
 */
class SseStreamManager(
    private val json: Json,
    private val onEvent: (StreamEvent) -> Unit,
    private val onState: (ConnState) -> Unit,
) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var source: EventSource? = null
    private var url: String = ""
    private var token: String = ""
    private var attempts = 0
    private var stopped = true
    private val reconnectRunnable = Runnable { open() }

    fun connect(url: String, token: String) {
        stop()
        stopped = false
        this.url = url.trimEnd('/')
        this.token = token
        attempts = 0
        open()
    }

    fun stop() {
        stopped = true
        attempts = 0
        handler.removeCallbacks(reconnectRunnable)
        source?.cancel()
        source = null
        onState(ConnState.Disconnected)
    }

    private fun open() {
        if (stopped || url.isEmpty()) return
        onState(if (attempts == 0) ConnState.Connecting else ConnState.Reconnecting(attempts))
        val req = Request.Builder()
            .url("$url/api/v1/events")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .build()
        source = EventSources.createFactory(client).newEventSource(req, listener)
    }

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            attempts = 0
            onState(ConnState.Connected)
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            runCatching { json.decodeFromString<StreamEvent>(data) }.onSuccess(onEvent)
        }

        override fun onClosed(eventSource: EventSource) {
            scheduleReconnect()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        attempts++
        val delayMs = min(30_000L, 1_000L shl min(attempts - 1, 5))
        handler.postDelayed(reconnectRunnable, delayMs)
    }
}
