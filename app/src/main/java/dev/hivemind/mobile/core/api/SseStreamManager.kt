package dev.hivemind.mobile.core.api

import dev.hivemind.mobile.core.lan.LanSession
import dev.hivemind.mobile.core.lan.LanStreamRestart
import dev.hivemind.mobile.core.lan.LanUnauthorized
import dev.hivemind.mobile.core.lan.WireStream
import dev.hivemind.mobile.core.models.StreamEvent
import dev.hivemind.mobile.core.relay.Redact
import dev.hivemind.mobile.core.state.ConnState
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * SSE da LAN. Cada linha `data:` é uma caixa; o tipo do evento está no plaintext.
 * Reconecta com backoff exponencial (1s → 2s → … até 30s) com jitter para não
 * sincronizar a rajada de reconexões com o retorno do host. 401 força
 * pareamento novo.
 *
 * Cada `connect()`/`stop()` avança a geração; o loop da geração anterior é
 * cancelado e a leitura bloqueante é destravada pelo `close()` do stream ativo.
 */
class SseStreamManager(
    private val json: Json,
    private val session: () -> LanSession?,
    private val onEvent: (StreamEvent) -> Unit,
    private val onState: (ConnState) -> Unit,
    private val onRebind: () -> Unit,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val workScope = scope
    private val generation = AtomicInteger(0)
    private val random = Random.Default
    private var url: String = ""
    private var job: Job? = null

    @Volatile
    private var active: WireStream? = null

    fun connect(url: String) {
        cancelLoop(notify = false)
        this.url = url.trimEnd('/')
        launchLoop(generation.get())
    }

    fun stop(notify: Boolean = true) {
        cancelLoop(notify = notify)
    }

    private fun cancelLoop(notify: Boolean) {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        active?.close()
        active = null
        if (notify) onState(ConnState.Disconnected)
    }

    private fun launchLoop(gen: Int) {
        job = workScope.launch { runLoop(gen) }
    }

    private suspend fun runLoop(gen: Int) {
        var attempts = 0
        while (gen == generation.get()) {
            if (url.isEmpty()) return
            onState(if (attempts == 0) ConnState.Connecting else ConnState.Reconnecting(attempts))
            val current = session()
            if (current == null) {
                scheduleReconnect(gen, attempts++)
                continue
            }
            try {
                val stream = current.openStream("$url/api/v1/events")
                active = stream
                if (gen != generation.get()) {
                    stream.close()
                    return
                }
                onState(ConnState.Connected)
                attempts = 0
                val data = StringBuilder()
                try {
                    stream.lines { line ->
                        if (gen != generation.get()) return@lines
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
                    runCatching { stream.close() }
                }
                scheduleReconnect(gen, attempts++)
            } catch (_: LanUnauthorized) {
                if (gen == generation.get()) onRebind()
                return
            } catch (_: LanStreamRestart) {
                scheduleReconnect(gen, attempts++)
            } catch (e: Exception) {
                Redact.clean(e.message.orEmpty())
                scheduleReconnect(gen, attempts++)
            }
        }
    }

    private fun dispatch(current: LanSession, payload: String) {
        val plain = runCatching { current.openEvent(payload) }.getOrElse {
            throw it
        }
        runCatching { json.decodeFromString<StreamEvent>(plain) }.onSuccess(onEvent)
    }

    private suspend fun scheduleReconnect(gen: Int, attempt: Int) {
        if (gen != generation.get()) return
        delay(backoffDelayMs(attempt, random.nextDouble()))
    }

    companion object {
        /**
         * Backoff exponencial com jitter ±40%: `attempt=0` (primeira reconexão)
         * espera 1s, dobra até o teto de 30s. O jitter evita que todos os
         * dispositivos de um mesmo host batam na porta ao mesmo tempo quando
         * o host volta.
         */
        fun backoffDelayMs(attempt: Int, jitter: Double): Long {
            val base = min(30_000L, 1_000L shl min(attempt, 5))
            val factor = 0.6 + 0.8 * jitter.coerceIn(0.0, 1.0)
            return (base * factor).toLong().coerceIn(250L, 30_000L)
        }
    }
}
