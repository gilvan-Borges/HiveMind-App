package dev.hivemind.mobile.features.chat

import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.RestoredMessage

sealed interface TranscriptEntry {
    data class Item(val item: ChatDisplayItem) : TranscriptEntry
    data object Novas : TranscriptEntry
}

fun restoredMessages(messages: List<RestoredMessage>): List<ChatDisplayItem> =
    messages.mapNotNull { message ->
        if (message.text.isBlank()) return@mapNotNull null
        val user = message.kind.equals("user", true) || message.label.equals("Você", true)
        ChatDisplayItem.TextBubble(
            authorLabel = message.label.ifBlank { if (user) "Você" else message.kind.ifBlank { "Equipe" } },
            text = message.text,
            isUser = user,
        )
    }

/**
 * Sessão aberta com a lane viva da mesma sessão mostra o fluxo SSE.
 * Sessão salva sem essa lane mostra o histórico restaurado e, em seguida,
 * eventos novos que ainda não carregam o mesmo sessionId.
 */
fun transcriptFor(
    restored: List<RestoredMessage>,
    live: List<LaneEventItem>,
    laneSessionId: String?,
    openedSessionId: String?,
): List<ChatDisplayItem> {
    val liveItems = coalesceChatEvents(live)
    val opening = !openedSessionId.isNullOrBlank()
    val liveMatches = opening && openedSessionId == laneSessionId && liveItems.isNotEmpty()
    return when {
        liveMatches -> liveItems
        opening && restored.isNotEmpty() -> restoredMessages(restored) + liveItems
        else -> liveItems
    }
}

fun withNovasAt(items: List<ChatDisplayItem>, index: Int): List<TranscriptEntry> {
    if (index <= 0 || index > items.lastIndex) return items.map { TranscriptEntry.Item(it) }
    return buildList {
        items.forEachIndexed { i, item ->
            if (i == index) add(TranscriptEntry.Novas)
            add(TranscriptEntry.Item(item))
        }
    }
}
