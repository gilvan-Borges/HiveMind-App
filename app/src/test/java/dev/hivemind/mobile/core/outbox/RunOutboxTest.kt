package dev.hivemind.mobile.core.outbox

import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.RunStartRequest
import dev.hivemind.mobile.core.api.StartResponse
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fakes determinísticos: loja em memória e clock controlável. */
private class FakeStore : RunQueueStore {
    val rows = linkedMapOf<String, QueuedRun>()
    override suspend fun all(): List<QueuedRun> = rows.values.toList()
    override suspend fun upsert(run: QueuedRun) { rows[run.id] = run }
    override suspend fun delete(id: String) { rows.remove(id) }
}

private class FakeClock(var now: Long = 1_000L) : OutboxClock {
    var nextId = 0
    override fun newId(): String = "q${nextId++}"
    override fun nowMs(): Long = now
}

class RunOutboxTest {

    private fun run(id: String, lane: String = "lane-a", state: RunQueueState = RunQueueState.QUEUED) = QueuedRun(
        id = id,
        workspace = "acme",
        laneId = lane,
        objective = "objetivo $id",
        runMode = "auto",
        sessionId = null,
        images = emptyList(),
        state = state,
        lastError = null,
        createdAt = 0L,
    )

    private fun outboxOf(store: FakeStore, clock: FakeClock) = RunOutbox(store, clock)

    private fun okSender(sent: MutableList<RunStartRequest>): suspend (RunStartRequest) -> StartResponse = { req ->
        sent.add(req)
        StartResponse(accepted = true)
    }

    @Test
    fun drainIsNoOpWhenDisconnected() = runBlocking {
        val store = FakeStore()
        val outbox = outboxOf(store, FakeClock())
        outbox.enqueue("lane-a", "acme", "resolver bug", "auto", null, emptyList())

        outbox.tryDrain(connected = false, isLaneBusy = { _, _ -> false }, sender = { throw AssertionError() })

        assertEquals(1, outbox.runs.value.size)
        assertEquals(RunQueueState.QUEUED, outbox.runs.value.first().state)
    }

    @Test
    fun deliversFifoAndClearsOnSuccess() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        val first = outbox.enqueue("lane-a", "acme", "primeiro", "auto", null, emptyList())
        val second = outbox.enqueue("lane-b", "acme", "segundo", "auto", null, emptyList())
        val sent = mutableListOf<RunStartRequest>()

        outbox.tryDrain(true, { _, _ -> false }, okSender(sent))

        // FIFO entre lanes distintas, sem colisão de cooldown.
        assertEquals(listOf("primeiro", "segundo"), sent.map { it.objective })
        assertTrue(outbox.runs.value.isEmpty())
        assertTrue(store.rows.isEmpty())
        assertEquals("lane-a", first.laneId)
        assertEquals("lane-b", second.laneId)
    }

    @Test
    fun transportFailureRequeuesAndStopsDrain() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        outbox.enqueue("lane-a", "acme", "primeiro", "auto", null, emptyList())
        outbox.enqueue("lane-a", "acme", "segundo", "auto", null, emptyList())
        var calls = 0

        outbox.tryDrain(
            connected = true,
            isLaneBusy = { _, _ -> false },
            sender = { _ -> calls++; throw IOException("host sumiu") },
        )

        assertEquals(1, calls)
        assertEquals(2, outbox.runs.value.size)
        assertTrue(outbox.runs.value.all { it.state == RunQueueState.QUEUED })
    }

    @Test
    fun hubRejectionMarksFailedAndContinuesQueue() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        outbox.enqueue("lane-a", "acme", "rejeitado", "auto", null, emptyList())
        // Cooldown não deixa o segundo sair na mesma lane: usa lane distinta.
        outbox.enqueue("lane-b", "acme", "aceito", "auto", null, emptyList())
        val sent = mutableListOf<RunStartRequest>()

        outbox.tryDrain(
            connected = true,
            isLaneBusy = { _, _ -> false },
            sender = { req ->
                sent.add(req)
                if (req.objective == "rejeitado") throw ApiException("lane inválida", 400)
                StartResponse(accepted = true)
            },
        )

        assertEquals(listOf("rejeitado", "aceito"), sent.map { it.objective })
        val failed = outbox.runs.value.single()
        assertEquals(RunQueueState.FAILED, failed.state)
        assertEquals("lane inválida", failed.lastError)
        assertEquals(RunQueueState.FAILED, store.rows[failed.id]?.state)
    }

    @Test
    fun busyLaneHoldsDeliveryUntilFinishRetriggers() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        outbox.enqueue("lane-a", "acme", "próximo turno", "auto", null, emptyList())
        var busy = true
        val sent = mutableListOf<RunStartRequest>()

        outbox.tryDrain(true, { _, _ -> busy }, okSender(sent))
        assertEquals(0, sent.size)

        busy = false
        outbox.tryDrain(true, { _, _ -> busy }, okSender(sent))
        assertEquals(1, sent.size)
        assertTrue(outbox.runs.value.isEmpty())
    }

    @Test
    fun postSendCooldownBlocksImmediateSecondRunOnSameLane() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        outbox.enqueue("lane-a", "acme", "primeiro", "auto", null, emptyList())
        outbox.enqueue("lane-a", "acme", "segundo", "auto", null, emptyList())
        val sent = mutableListOf<RunStartRequest>()

        outbox.tryDrain(true, { _, _ -> false }, okSender(sent))
        assertEquals(1, sent.size)
        assertEquals(RunQueueState.QUEUED, outbox.runs.value.single().state)

        // Janela POST aceito → primeiro `stage` ainda não chegou: cooldown segura.
        clock.now += RunOutbox.LANE_COOLDOWN_MS - 1
        outbox.tryDrain(true, { _, _ -> false }, okSender(sent))
        assertEquals(1, sent.size)

        // Cooldown expirou e o `stage` já marcou running: espera o finish.
        clock.now += 2
        outbox.tryDrain(true, { _, _ -> true }, okSender(sent))
        assertEquals(1, sent.size)

        clock.now += 1
        outbox.tryDrain(true, { _, _ -> false }, okSender(sent))
        assertEquals(listOf("primeiro", "segundo"), sent.map { it.objective })
    }

    @Test
    fun retryResetsFailedItemAndDeliversAgain() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        val item = outbox.enqueue("lane-a", "acme", "de novo", "auto", null, emptyList())
        outbox.tryDrain(
            true,
            { _, _ -> false },
            sender = { throw ApiException("erro permanente", 500) },
        )
        assertEquals(RunQueueState.FAILED, outbox.runs.value.single().state)

        outbox.retry(item.id)
        val sent = mutableListOf<RunStartRequest>()
        outbox.tryDrain(true, { _, _ -> false }, okSender(sent))

        assertEquals(1, sent.size)
        assertTrue(outbox.runs.value.isEmpty())
    }

    @Test
    fun discardRemovesItemAndPersists() = runBlocking {
        val store = FakeStore()
        val clock = FakeClock()
        val outbox = outboxOf(store, clock)
        val item = outbox.enqueue("lane-a", "acme", "nunca mind", "auto", null, emptyList())

        outbox.discard(item.id)

        assertTrue(outbox.runs.value.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun restoreReclassifiesOrphanSendingAsFailed() = runBlocking {
        val store = FakeStore()
        store.upsert(run("q9", state = RunQueueState.SENDING))
        val outbox = outboxOf(store, FakeClock())

        outbox.restore()

        val restored = outbox.runs.value.single()
        assertEquals(RunQueueState.FAILED, restored.state)
        assertEquals(RunOutbox.RESTORE_FAILED_SENDING, restored.lastError)
        assertEquals(RunQueueState.FAILED, store.rows["q9"]?.state)
        assertEquals(RunOutbox.RESTORE_FAILED_SENDING, store.rows["q9"]?.lastError)
    }

    @Test
    fun restoreKeepsQueuedAndFailedUntouched() = runBlocking {
        val store = FakeStore()
        store.upsert(run("q1", state = RunQueueState.QUEUED))
        val failed = run("q2", state = RunQueueState.FAILED).copy(lastError = "motivo")
        store.upsert(failed)
        val outbox = outboxOf(store, FakeClock())

        outbox.restore()

        val byId = outbox.runs.value.associateBy { it.id }
        assertEquals(RunQueueState.QUEUED, byId["q1"]?.state)
        assertNull(byId["q1"]?.lastError)
        assertEquals(RunQueueState.FAILED, byId["q2"]?.state)
        assertEquals("motivo", byId["q2"]?.lastError)
    }
}
