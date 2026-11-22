package dev.hivemind.mobile.features.chat

import dev.hivemind.mobile.core.models.LaneEventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEventCoalesceTest {

    @Test
    fun mergesConsecutiveTextFromSameAuthor() {
        val events = listOf(
            LaneEventItem(kind = "text", author = "chief", label = "Chefe", text = "Olá, "),
            LaneEventItem(kind = "text", author = "chief", label = "Chefe", text = "analisando."),
            LaneEventItem(kind = "text", author = "user", label = "Você", text = "Obrigado"),
        )

        val result = coalesceChatEvents(events)

        assertEquals(2, result.size)
        val first = result[0] as ChatDisplayItem.TextBubble
        assertEquals("Chefe", first.authorLabel)
        assertEquals("Olá, analisando.", first.text)
        assertFalse(first.isUser)

        val second = result[1] as ChatDisplayItem.TextBubble
        assertEquals("Você", second.authorLabel)
        assertEquals("Obrigado", second.text)
        assertTrue(second.isUser)
    }

    @Test
    fun collapsesConsecutiveIdenticalToolCallsAndDedupsStages() {
        val events = listOf(
            LaneEventItem(kind = "stage", stage = "Dev"),
            LaneEventItem(kind = "stage", stage = "Dev"),
            LaneEventItem(kind = "tool_call", toolName = "read_file"),
            LaneEventItem(kind = "tool_call", toolName = "read_file"),
            LaneEventItem(kind = "tool_call", toolName = "read_file"),
            LaneEventItem(kind = "tool_call", toolName = "grep_search"),
            LaneEventItem(kind = "approval_required", text = "ignore me"),
            LaneEventItem(kind = "finish", text = "Pronto", tokens = 1500),
        )

        val result = coalesceChatEvents(events)

        assertEquals(4, result.size)
        assertEquals(ChatDisplayItem.StageBadge("Dev"), result[0])
        assertEquals(ChatDisplayItem.ToolGroup("read_file", 3), result[1])
        assertEquals(ChatDisplayItem.ToolGroup("grep_search", 1), result[2])
        assertEquals(ChatDisplayItem.FinishItem("Pronto", 1500), result[3])
    }

    @Test
    fun formatsTokensHumanReadable() {
        assertEquals("950", formatTokens(950))
        assertEquals("1.5k", formatTokens(1500))
        assertEquals("2.0M", formatTokens(2_000_000))
    }

    @Test
    fun mergesJevRouteAndFailoverBadges() {
        val events = listOf(
            LaneEventItem(
                kind = "model_route",
                provider = "opencode",
                model = "gpt-5",
                tier = "standard",
                estimatedMicroUsd = 1234,
            ),
            LaneEventItem(
                kind = "provider_failover",
                fromProvider = "agy",
                fromModel = "gemini-2.5",
                provider = "opencode",
                model = "gpt-5",
                reasons = listOf("quota"),
            ),
            LaneEventItem(kind = "provider_exhausted", text = ""),
        )

        val result = coalesceChatEvents(events)

        val route = result[0] as ChatDisplayItem.RouteBadge
        assertEquals("opencode", route.provider)
        assertEquals("gpt-5", route.model)
        assertEquals(1234L, route.estimatedMicroUsd)

        val failover = result[1] as ChatDisplayItem.FailoverItem
        assertEquals("agy", failover.fromProvider)
        assertEquals("opencode", failover.provider)
        assertEquals(listOf("quota"), failover.reasons)

        // exhausted sem texto ainda vira erro com mensagem padrão legível.
        val exhausted = result[2] as ChatDisplayItem.ErrorItem
        assertTrue(exhausted.text.contains("esgotados"))
    }

    @Test
    fun formatsMicroUsdHumanReadable() {
        assertEquals("$0.0123", formatMicroUsd(12_300))
        assertEquals("$0.001234", formatMicroUsd(1_234))
        assertEquals("$1.25", formatMicroUsd(1_250_000))
    }
}
