package dev.hivemind.mobile.core.outbox

import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.ImageInput
import dev.hivemind.mobile.core.api.RunStartRequest
import dev.hivemind.mobile.core.api.StartResponse
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estados da fila de envio (Spec 002). */
enum class RunQueueState { QUEUED, SENDING, FAILED }

/**
 * Objetivo enfileirado para envio futuro. Espelha `RunStartRequest` com
 * identidade própria (id) e estado de entrega.
 */
data class QueuedRun(
    val id: String,
    val workspace: String,
    val laneId: String,
    val objective: String,
    val runMode: String,
    val sessionId: String?,
    val images: List<ImageInput>,
    val state: RunQueueState,
    val lastError: String?,
    val createdAt: Long,
) {
    fun toRequest() = RunStartRequest(
        laneId = laneId,
        workspace = workspace,
        objective = objective,
        sessionId = sessionId?.takeIf { it.isNotBlank() },
        runMode = runMode.takeIf { it.isNotBlank() },
        images = images,
    )
}

/** Persistência da fila (Room em produção, fake em teste). */
interface RunQueueStore {
    suspend fun all(): List<QueuedRun>
    suspend fun upsert(run: QueuedRun)
    suspend fun delete(id: String)
}

/** Gera ids e instantes; injetável para testes determinísticos. */
interface OutboxClock {
    fun newId(): String
    fun nowMs(): Long
}

/**
 * Fila durável de objetivos (Spec 002): o que a pessoa escreve com o bridge
 * inacessível não se perde — o envio acontece sozinho quando a conexão volta
 * e a lane alvo está livre.
 *
 * Política de entrega:
 * - FIFO estrito entre itens QUEUED.
 * - Falha de transporte (`IOException`/`ApiException` sem status) devolve o
 *   item à fila e para o drain — o hub nunca recebeu o comando.
 * - Rejeição do hub (`ApiException` com status) vira FAILED e exige decisão
 *   humana (Retry/Discard); o hub não tem idempotência, então reenvio
 *   automático de comando já avaliado pode duplicar run.
 * - `RESTORE_FAILED_SENDING`: item em SENDING após restart do processo é
 *   FAILED — não se repete um run sem saber se o original chegou.
 * - Cooldown por lane após envio bem-sucedido: cobre a janela entre o POST
 *   aceito e o primeiro evento `stage` marcar `running` no snapshot.
 */
class RunOutbox(
    private val store: RunQueueStore,
    private val clock: OutboxClock,
) {
    companion object {
        /** Cooldown pós-envio por lane (janela POST aceito → stage). */
        const val LANE_COOLDOWN_MS = 8_000L

        const val RESTORE_FAILED_SENDING = "Envio interrompido antes da confirmação. " +
            "Verifique se a tarefa já iniciou antes de repetir."
    }

    private val _runs = MutableStateFlow<List<QueuedRun>>(emptyList())
    val runs: StateFlow<List<QueuedRun>> = _runs.asStateFlow()

    private val drainMutex = Mutex()
    private val lastSentAtMs = HashMap<String, Long>()

    /** Carrega a fila persistida; SENDING órfão vira FAILED (estado incerto). */
    suspend fun restore() {
        val restored = store.all().map { run ->
            if (run.state == RunQueueState.SENDING) {
                run.copy(state = RunQueueState.FAILED, lastError = RESTORE_FAILED_SENDING)
            } else {
                run
            }
        }
        // SENDING → FAILED muda o estado persistido; grava de volta.
        restored.forEach { run -> if (run.state == RunQueueState.FAILED) store.upsert(run) }
        _runs.value = restored.sortedBy { it.createdAt }
    }

    suspend fun enqueue(
        laneId: String,
        workspace: String,
        objective: String,
        runMode: String,
        sessionId: String?,
        images: List<ImageInput>,
    ): QueuedRun {
        val run = QueuedRun(
            id = clock.newId(),
            workspace = workspace,
            laneId = laneId,
            objective = objective,
            runMode = runMode,
            sessionId = sessionId,
            images = images,
            state = RunQueueState.QUEUED,
            lastError = null,
            createdAt = clock.nowMs(),
        )
        store.upsert(run)
        _runs.value = _runs.value + run
        return run
    }

    /** FAILED → QUEUED: nova tentativa exige gesto humano (botão Retry). */
    suspend fun retry(id: String) {
        val run = _runs.value.firstOrNull { it.id == id } ?: return
        val requeued = run.copy(state = RunQueueState.QUEUED, lastError = null)
        store.upsert(requeued)
        _runs.value = _runs.value.map { if (it.id == id) requeued else it }
    }

    /** Descarta um item sem importar o estado. */
    suspend fun discard(id: String) {
        store.delete(id)
        _runs.value = _runs.value.filterNot { it.id == id }
    }

    /** Descarta tudo (ex.: despareamento). */
    suspend fun clear() {
        _runs.value.forEach { store.delete(it.id) }
        _runs.value = emptyList()
    }

    /**
     * Tenta entregar a fila. `connected` fecha o gate; `isLaneBusy` evita
     * empilhar runs numa lane ocupada; `sender` entrega ao bridge.
     */
    suspend fun tryDrain(
        connected: Boolean,
        isLaneBusy: (workspace: String, laneId: String) -> Boolean,
        sender: suspend (RunStartRequest) -> StartResponse,
    ) {
        if (!connected) return
        drainMutex.withLock {
            while (true) {
                val next = _runs.value.firstOrNull { it.state == RunQueueState.QUEUED } ?: return
                if (isLaneBusy(next.workspace, next.laneId)) return
                val lastSent = lastSentAtMs[next.laneId]
                if (lastSent != null && clock.nowMs() < lastSent + LANE_COOLDOWN_MS) return

                mark(next.copy(state = RunQueueState.SENDING))
                try {
                    sender(next.toRequest())
                } catch (e: IOException) {
                    mark(next.copy(state = RunQueueState.QUEUED))
                    return
                } catch (e: ApiException) {
                    if (e.status == null) {
                        // Transporte (sem pareamento, túnel não pronto, falha de
                        // rede): o hub não respondeu — volta para a fila.
                        mark(next.copy(state = RunQueueState.QUEUED))
                        return
                    }
                    mark(next.copy(state = RunQueueState.FAILED, lastError = e.message))
                    continue
                }
                lastSentAtMs[next.laneId] = clock.nowMs()
                store.delete(next.id)
                _runs.value = _runs.value.filterNot { it.id == next.id }
            }
        }
    }

    private suspend fun mark(run: QueuedRun) {
        store.upsert(run)
        _runs.value = _runs.value.map { if (it.id == run.id) run else it }
    }
}

/** Clock de produção: UUID + relógio do sistema. */
class SystemOutboxClock : OutboxClock {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
    override fun nowMs(): Long = System.currentTimeMillis()
}
