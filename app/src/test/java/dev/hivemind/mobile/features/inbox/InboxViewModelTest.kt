package dev.hivemind.mobile.features.inbox

import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.SessionSummary
import dev.hivemind.mobile.core.models.WorkspaceSessionsSummary
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxViewModelTest {

    @Test
    fun sessionsSplitHiveAndEquipe() {
        val rows = InboxIndex.fromSessions(
            workspace = "/work/org-alpha",
            sessions = listOf(
                SessionSummary(id = "sess-ide", updatedAt = "2026-11-23T16:04:00Z", title = "Revisão do módulo", preview = "Sessão da equipe"),
                SessionSummary(id = "sess-hive", updatedAt = "2026-11-23T15:00:00Z", title = "Agent Hive", kind = "hive", unread = 1),
            ),
            lanes = listOf(
                LaneSnapshot(
                    workspace = "/work/org-alpha",
                    laneId = "resident",
                    runMode = "resident",
                    sessionId = "sess-hive",
                    recentEvents = listOf(LaneEventItem(kind = "text", text = "O orb fica.")),
                    running = true,
                ),
                LaneSnapshot(workspace = "/work/org-alpha", laneId = "lane-autoreview", sessionId = "skip-me"),
            ),
        )

        val hive = rows.first { it.id == "sess-hive" }
        assertEquals(ConversationSource.AGENT_HIVE, hive.source)
        assertEquals(InboxIndex.SECTION_HIVE, hive.section)
        assertEquals("resident", hive.laneId)
        assertEquals("resident", hive.runMode)
        assertEquals(1, hive.unread)
        assertEquals("O orb fica.", hive.preview)
        assertTrue(hive.running)
        val equipe = rows.first { it.id == "sess-ide" }
        assertEquals(InboxIndex.SECTION_EQUIPE, equipe.section)
        assertEquals("auto", equipe.runMode)
        assertEquals("Sessão da equipe", equipe.preview)
        assertFalse(rows.any { it.id == "skip-me" })
    }

    @Test
    fun projectsGroupByStackAndLeaveBlankLast() {
        val projects = listOf(
            ProjectSummary(workspace = "/a", name = "Alpha", stack = "", branch = "main"),
            ProjectSummary(workspace = "/b", name = "Beta", stack = "mobile", runningLanes = 1),
        )
        val rows = InboxIndex.projectRows(projects, emptyMap())
        val page = InboxIndex.group(
            rows = rows,
            query = "",
            collapsed = emptySet(),
            sectionOf = { it.section },
            titleOf = { it.title },
            previewOf = { it.preview },
            timeOf = { it.updatedAtMillis },
        )
        assertEquals(listOf("mobile", "Sem seção"), page.map { it.name })
        assertTrue(page[0].rows.single().running)
    }

    @Test
    fun searchIgnoresCollapsedSections() {
        val rows = listOf(
            ProjectSummary(workspace = "/a", name = "Alpha", stack = "mobile"),
            ProjectSummary(workspace = "/b", name = "Beta", stack = "mobile"),
        ).let { InboxIndex.projectRows(it, emptyMap()) }
        val found = InboxIndex.group(
            rows = rows,
            query = "alp",
            collapsed = setOf("mobile"),
            sectionOf = { it.section },
            titleOf = { it.title },
            previewOf = { it.preview },
            timeOf = { it.updatedAtMillis },
        )
        assertEquals(listOf("Alpha"), found.single().rows.map { it.title })
    }

    @Test
    fun ideStatusNamesTransportAndProject() {
        assertEquals(
            "IDE online · LAN · acme",
            InboxIndex.ideStatusLabel(online = true, transport = "lan", project = "acme"),
        )
        assertEquals(
            "IDE offline",
            InboxIndex.ideStatusLabel(online = false, transport = "", project = ""),
        )
        assertEquals(
            "IDE online · Relay · org-alpha",
            InboxIndex.ideStatusLabel(true, "relay", "org-alpha"),
        )
    }

    @Test
    fun formatsPortugueseTimes() {
        val zone = ZoneOffset.UTC
        val today = InboxIndex.parseActivityMillis("2026-11-23T16:04:00Z")
        val now = InboxIndex.parseActivityMillis("2026-11-23T18:00:00Z")
        assertEquals("16:04", InboxIndex.formatInboxTime(today, now, zone))
        assertEquals("Ontem", InboxIndex.formatInboxTime(InboxIndex.parseActivityMillis("2026-11-22T10:00:00Z"), now, zone))
        assertEquals("16 de nov.", InboxIndex.formatInboxTime(InboxIndex.parseActivityMillis("2026-11-16T10:00:00Z"), now, zone))
    }

    @Test
    fun viewModelReadsSessionsAndClearsUnread() = runBlocking {
        val source = FakeInbox(
            projects = listOf(
                ProjectSummary(workspace = "/wire", name = "Com fio", stack = "lab"),
                ProjectSummary(workspace = "/legacy", name = "Sem fio", stack = ""),
            ),
            sessions = mapOf(
                "/wire" to listOf(
                    SessionSummary(id = "w1", title = "Do fio", preview = "prévia", updatedAt = "2026-11-23T16:04:00Z", unread = 2, kind = "hive"),
                ),
                "/legacy" to listOf(
                    SessionSummary(id = "s1", title = "Salva", preview = "Sessão salva na IDE", updatedAt = "2026-11-23T12:00:00Z"),
                ),
            ),
        )
        val vm = InboxViewModel(source, this)
        vm.reload()
        val hive = vm.conversationPage("/wire").flatMap { it.rows }.single()
        assertEquals("Do fio", hive.title)
        assertEquals(2, hive.unread)
        assertEquals("resident", hive.runMode)
        vm.markRead("/wire", "w1")
        assertEquals(0, vm.conversationPage("/wire").flatMap { it.rows }.single().unread)
        vm.search("salva")
        assertEquals(listOf("Salva"), vm.conversationPage("/legacy").flatMap { it.rows }.map { it.title })
        assertEquals(listOf("Sem fio"), vm.projectPage().flatMap { it.rows }.map { it.title })
    }

    @Test
    fun projectUnreadAddsPendingApprovals() {
        val projects = listOf(
            ProjectSummary(workspace = "/a", name = "Alpha", pendingApprovals = 3),
            ProjectSummary(workspace = "/b", name = "Beta"),
        )
        val conv = mapOf(
            "/b" to listOf(
                ConversationRow(
                    id = "b1",
                    workspace = "/b",
                    title = "Beta",
                    preview = "x",
                    updatedAtMillis = 10,
                    unread = 2,
                    source = ConversationSource.IDE,
                    section = InboxIndex.SECTION_EQUIPE,
                    laneId = null,
                    sessionId = "b1",
                    running = false,
                ),
            ),
        )
        val rows = InboxIndex.projectRows(projects, conv)
        assertEquals(3, rows.first { it.workspace == "/a" }.unread)
        assertEquals(2, rows.first { it.workspace == "/b" }.unread)
    }
}

private class FakeInbox(
    private val projects: List<ProjectSummary>,
    private val sessions: Map<String, List<SessionSummary>> = emptyMap(),
    private val lanes: List<LaneSnapshot> = emptyList(),
) : InboxSource {
    val reads = mutableListOf<Pair<String, String>>()
    override suspend fun projects() = projects
    override suspend fun lanes(workspace: String?) = lanes
    override suspend fun sessions(workspace: String) = WorkspaceSessionsSummary(sessions[workspace].orEmpty())
    override suspend fun markRead(workspace: String, sessionId: String) {
        reads += workspace to sessionId
    }
    override suspend fun refreshApprovals(workspace: String) = Unit
}
