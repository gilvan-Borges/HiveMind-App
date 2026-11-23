package dev.hivemind.mobile.features.chat

import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.RestoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranscriptTest {

    @Test
    fun liveSessionReplacesRestoredCopy() {
        val restored = listOf(RestoredMessage(id = "1", kind = "user", text = "antiga"))
        val live = listOf(LaneEventItem(kind = "text", author = "user", label = "Você", text = "viva"))
        val items = transcriptFor(restored, live, laneSessionId = "s1", openedSessionId = "s1")
        assertEquals(1, items.size)
        assertEquals("viva", (items.single() as ChatDisplayItem.TextBubble).text)
    }

    @Test
    fun savedSessionShowsHistoryWhenLaneIsAnotherSession() {
        val restored = listOf(
            RestoredMessage(id = "1", kind = "user", text = "oi"),
            RestoredMessage(id = "2", kind = "chief", label = "Chefe", text = "olá"),
        )
        val live = listOf(LaneEventItem(kind = "text", author = "user", text = "outra lane"))
        val items = transcriptFor(restored, live, laneSessionId = "other", openedSessionId = "s1")
        assertEquals(3, items.size)
        assertTrue((items[0] as ChatDisplayItem.TextBubble).isUser)
        assertEquals("olá", (items[1] as ChatDisplayItem.TextBubble).text)
        assertEquals(false, (items[1] as ChatDisplayItem.TextBubble).isUser)
    }

    @Test
    fun novasDividerStaysBeforeTheMarkedBubble() {
        val items = listOf(
            ChatDisplayItem.TextBubble("Você", "antes", true),
            ChatDisplayItem.StageBadge("Dev"),
            ChatDisplayItem.TextBubble("Chefe", "nova", false),
        )
        val entries = withNovasAt(items, 2)
        assertTrue(entries[2] is TranscriptEntry.Novas)
        val last = entries[3] as TranscriptEntry.Item
        assertEquals("nova", (last.item as ChatDisplayItem.TextBubble).text)
        assertEquals(items.size, withNovasAt(items, 0).size)
    }
}
