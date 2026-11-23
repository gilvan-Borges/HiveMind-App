package dev.hivemind.mobile.core.state

import dev.hivemind.mobile.core.models.ActivityNotice
import dev.hivemind.mobile.core.models.LaneEvent
import dev.hivemind.mobile.core.models.OrchestratorEvent
import dev.hivemind.mobile.core.models.StreamEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `atividade ao vivo substitui o mesmo autor e fase`() {
        val first = ActivityNotice(laneId = "resident", author = "dev", phase = "file", target = "a.kt")
        val second = ActivityNotice(laneId = "resident", author = "qa", phase = "test", target = "go test")
        val updated = ActivityNotice(laneId = "resident", author = "dev", phase = "file", target = "b.kt")
        val kept = rememberActivities(listOf(first, second), updated)
        assertEquals(2, kept.size)
        assertEquals("b.kt", kept.last().target)
        assertEquals("qa", kept.first().author)
    }

    @Test
    fun `lane event cria snapshot e acumula tokens`() {
        val state = HubState(json)
        state.selectedWorkspace.value = "C:/w"

        state.onLaneEvent(
            LaneEvent("l1", "principal", OrchestratorEvent(kind = "stage", stage = "chief")),
        )
        state.onLaneEvent(
            LaneEvent("l1", "principal", OrchestratorEvent(kind = "usage", inputTokens = 100, outputTokens = 20)),
        )

        val lane = state.lane("C:/w", "l1")
        assertNotNull(lane)
        assertTrue(lane!!.running)
        assertEquals("chief", lane.stage)
        assertEquals(120L, lane.turnTokens)
    }

    @Test
    fun `approval required entra e sai da lista de pendentes`() {
        val state = HubState(json)
        state.selectedWorkspace.value = "C:/w"

        var notified: String? = null
        state.onApprovalRequest = { notified = it.approvalId }

        state.onLaneEvent(
            LaneEvent(
                "l1", "t",
                OrchestratorEvent(kind = "approval_required", approvalId = "l1:appr:1", text = "posso?"),
            ),
        )
        assertEquals("l1:appr:1", notified)
        assertEquals(1, state.pendingApprovals.value.size)

        state.onLaneEvent(
            LaneEvent("l1", "t", OrchestratorEvent(kind = "approval_resolved", approvalId = "l1:appr:1")),
        )
        assertEquals(0, state.pendingApprovals.value.size)
    }

    @Test
    fun `finish encerra a lane e limpa aprovações`() {
        val state = HubState(json)
        state.selectedWorkspace.value = "C:/w"
        state.onLaneEvent(LaneEvent("l1", "t", OrchestratorEvent(kind = "stage", stage = "chief")))
        state.onLaneEvent(
            LaneEvent("l1", "t", OrchestratorEvent(kind = "approval_required", approvalId = "a1")),
        )
        assertTrue(state.lane("C:/w", "l1")!!.running)

        state.onLaneEvent(LaneEvent("l1", "t", OrchestratorEvent(kind = "finish", text = "ok")))
        val lane = state.lane("C:/w", "l1")
        assertFalse(lane!!.running)
        assertEquals("done", lane.stage)
        assertEquals(0, lane.pendingApprovals.size)
    }

    @Test
    fun `lanes isoladas por workspace`() {
        val state = HubState(json)
        state.setLanes(
            listOf(
                dev.hivemind.mobile.core.models.LaneSnapshot(workspace = "C:/a", laneId = "l1"),
                dev.hivemind.mobile.core.models.LaneSnapshot(workspace = "C:/b", laneId = "l1"),
            ),
        )
        assertEquals(1, state.lanesOf("C:/a").size)
        assertEquals(1, state.lanesOf("C:/b").size)
        assertEquals(2, state.lanes.value.size)
    }

    @Test
    fun `provider_health alimenta o fluxo de saúde`() {
        val state = HubState(json)
        state.onStreamEvent(
            StreamEvent(
                type = "provider_health",
                data = json.parseToJsonElement(
                    """{"providers":[{"provider":"agy","state":"open","reason":"quota","dailySpentMicroUsd":1500,"dailyCapMicroUsd":2000}]}""",
                ),
            ),
        )
        val health = state.providerHealth.value
        assertEquals(1, health.size)
        assertEquals("agy", health[0].provider)
        assertEquals("open", health[0].state)
        assertEquals(1500L, health[0].dailySpentMicroUsd)
    }

    @Test
    fun `relay_gap aplica o snapshot de lanes e avisa`() {
        val state = HubState(json)
        state.onStreamEvent(
            StreamEvent(
                type = "relay_gap",
                data = json.parseToJsonElement(
                    """{"reason":"backlog_truncated","lanes":[
                        {"workspace":"C:/w","laneId":"l1","running":true,"stage":"dev",
                         "pendingApprovals":[{"approvalId":"a9","laneId":"l1","workspace":"C:/w","prompt":"ok"}]}
                    ]}""",
                ),
            ),
        )
        assertEquals(1, state.lanes.value.size)
        assertEquals("dev", state.lane("C:/w", "l1")!!.stage)
        assertEquals(1, state.pendingApprovals.value.size)
        assertEquals("backlog_truncated", state.relayGap.value!!.reason)
        state.clearRelayGap()
        assertEquals(null, state.relayGap.value)
    }

    @Test
    fun `lane event carrega metadados jev no transcript`() {
        val state = HubState(json)
        state.selectedWorkspace.value = "C:/w"
        state.onLaneEvent(
            LaneEvent(
                "l1", "t",
                OrchestratorEvent(
                    kind = "provider_failover",
                    provider = "opencode",
                    model = "gpt-5",
                    fromProvider = "agy",
                    fromModel = "gemini-2.5",
                    tier = "standard",
                    reasons = listOf("quota"),
                    estimatedMicroUsd = 4200,
                    decisionMicros = 900,
                ),
            ),
        )
        val item = state.lane("C:/w", "l1")!!.recentEvents.single()
        assertEquals("opencode", item.provider)
        assertEquals("gpt-5", item.model)
        assertEquals("agy", item.fromProvider)
        assertEquals("standard", item.tier)
        assertEquals(listOf("quota"), item.reasons)
        assertEquals(4200L, item.estimatedMicroUsd)
        assertEquals(900L, item.decisionMicros)
    }

    @Test
    fun `approval_required carrega o gate como riskTier`() {
        val state = HubState(json)
        state.selectedWorkspace.value = "C:/w"
        state.onLaneEvent(
            LaneEvent(
                "l1", "t",
                OrchestratorEvent(kind = "approval_required", approvalId = "ap1", text = "commit?", gate = "worktree"),
            ),
        )
        assertEquals("worktree", state.pendingApprovals.value.single().riskTier)
    }
}
