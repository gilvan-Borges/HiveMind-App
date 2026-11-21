package dev.hivemind.mobile.core.store.db

import dev.hivemind.mobile.core.models.DiffLine
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCacheMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `project faz roundtrip modelo entidade modelo`() {
        val model = ProjectSummary(
            workspace = "C:/w",
            name = "w",
            branch = "main",
            stack = "Go",
            activeTeam = "squad",
            runningLanes = 2,
            pendingApprovals = 1,
        )
        val back = model.toEntity(123L).toModel()
        assertEquals(model, back)
        assertEquals(123L, model.toEntity(123L).cachedAt)
    }

    @Test
    fun `lane com mensagens faz roundtrip incluindo metadados jev`() {
        val lane = LaneSnapshot(
            workspace = "C:/w",
            laneId = "l1",
            teamName = "squad",
            running = true,
            stage = "dev",
            turnTokens = 42,
            recentEvents = listOf(
                LaneEventItem(
                    kind = "provider_failover",
                    author = "jev",
                    text = "rota trocada",
                    provider = "opencode",
                    model = "gpt-5",
                    fromProvider = "agy",
                    tier = "standard",
                    reasons = listOf("quota", "outage"),
                    estimatedMicroUsd = 99,
                    decisionMicros = 7,
                ),
            ),
        )
        val entity = lane.laneEntity(5L)
        val messages = lane.recentEvents.map { it.messageEntity(lane.key) }
        val back = entity.toModel(messages.map { it.toModel() })
        assertEquals(lane, back)

        val msg = messages.single()
        assertEquals("C:/w|l1", msg.laneKey)
        assertEquals(listOf("quota", "outage"), msg.toModel().reasons)
        assertEquals(99L, msg.toModel().estimatedMicroUsd)
    }

    @Test
    fun `mensagens sem reasons voltam como lista vazia`() {
        val msg = LaneEventItem(kind = "text", text = "oi").messageEntity("C:/w|l1")
        assertTrue(msg.toModel().reasons.isEmpty())
    }

    @Test
    fun `diff faz roundtrip com linhas em json`() {
        val diff = FileDiff(
            path = "internal/x.go",
            status = "M",
            staged = true,
            added = 3,
            removed = 1,
            lines = listOf(DiffLine(kind = "add", text = "+a"), DiffLine(kind = "del", text = "-b")),
        )
        val entity = diff.diffEntity("C:/w", 9L, DiffLinesCodec.encode(diff.lines, json))
        val back = entity.toModel(DiffLinesCodec.decode(entity.linesJson, json))
        assertEquals(diff, back)
    }

    @Test
    fun `codec de linhas tolera json corrompido`() {
        assertTrue(DiffLinesCodec.decode("não é json", json).isEmpty())
    }
}
