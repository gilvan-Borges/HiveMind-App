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
}
