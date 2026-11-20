package dev.hivemind.mobile.core.api

import android.os.Handler
import android.os.Looper
import dev.hivemind.mobile.core.lan.LanSession
import dev.hivemind.mobile.core.lan.LanStreamRestart
import dev.hivemind.mobile.core.lan.LanUnauthorized
import dev.hivemind.mobile.core.lan.WireStream
import dev.hivemind.mobile.core.models.StreamEvent
import dev.hivemind.mobile.core.relay.Redact
import dev.hivemind.mobile.core.state.ConnState
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.serialization.json.Json

/**
 * SSE da LAN. Cada linha `data:` é uma caixa; o tipo do evento está no plaintext.
 * Reconecta com backoff (1s → 2s → 4s → … até 30s). 401 força pareamento novo.
 */
class SseStreamManager(
    private val json: Json,
    private val session: () -> LanSession?,
    private val onEvent: (StreamEvent) -> Unit,
    private val onState: (ConnState) -> Unit,
    private val onRebind: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private var url: String = ""
    private var attempts = 0
    private var stopped = true
    private var worker: Thread? = null
    @Volatile private var active: WireStream? = null
    private val reconnectRunnable = Runnable { open(generation.get()) }

    fun connect(url: String) {
        stop(notify = false)
        stopped = false
        this.url = url.trimEnd('/')
        attempts = 0
        open(generation.get())
    }

    fun stop(notify: Boolean = true) {
        stopped = true
        attempts = 0
        generation.incrementAndGet()
        handler.removeCallbacks(reconnectRunnable)
        active?.close()
        worker?.interrupt()
        worker = null
        if (notify) onState(ConnState.Disconnected)
    }

    private fun open(gen: Int) {
        if (stopped || gen != generation.get() || url.isEmpty()) return
        onState(if (attempts == 0) ConnState.Connecting else ConnState.Reconnecting(attempts))
        val thread = Thread({
            if (stopped || gen != generation.get()) return@Thread
            val current = session()
            if (current == null) {
                if (!stopped && gen == generation.get()) scheduleReconnect(gen)
                return@Thread
            }
            try {
                val stream = current.openStream("$url/api/v1/events")
                active = stream
                if (stopped || gen != generation.get()) {
                    stream.close()
                    return@Thread
                }
                onState(ConnState.Connected)
                attempts = 0
                val data = StringBuilder()
                try {
                    stream.lines { line ->
                        if (stopped || gen != generation.get()) return@lines
                        when {
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substring(5).trimStart())
                            }
                            line.isEmpty() && data.isNotEmpty() -> {
                                val payload = data.toString()
                                data.clear()
                                dispatch(current, payload)
                            }
                        }
                    }
                } finally {
                    if (active === stream) active = null
                    stream.close()
                }
                if (!stopped && gen == generation.get()) scheduleReconnect(gen)
            } catch (_: LanUnauthorized) {
                if (gen == generation.get()) onRebind()
            } catch (_: LanStreamRestart) {
                if (!stopped && gen == generation.get()) scheduleReconnect(gen)
            } catch (e: Exception) {
                if (!stopped && gen == generation.get()) {
                    Redact.clean(e.message.orEmpty())
                    scheduleReconnect(gen)
                }
            }
        }, "hivemind-sse")
        worker = thread
        thread.start()
    }

    private fun dispatch(current: LanSession, payload: String) {
        val plain = runCatching { current.openEvent(payload) }.getOrElse {
            throw it
        }
        runCatching { json.decodeFromString<StreamEvent>(plain) }.onSuccess(onEvent)
    }

    private fun scheduleReconnect(gen: Int) {
        if (stopped || gen != generation.get()) return
        attempts++
        val delayMs = min(30_000L, 1_000L shl min(attempts - 1, 5))
        handler.postDelayed(reconnectRunnable, delayMs)
    }
}
