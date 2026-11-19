package dev.hivemind.mobile.core.state

import dev.hivemind.mobile.core.models.LaneEvent
import dev.hivemind.mobile.core.models.OrchestratorEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStateTest {

    private val json = Json { ignoreUnknownKeys = true }

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
}
