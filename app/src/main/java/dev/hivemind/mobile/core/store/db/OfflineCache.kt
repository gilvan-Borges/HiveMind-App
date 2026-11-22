package dev.hivemind.mobile.core.store.db

import androidx.room.Room
import android.content.Context
import dev.hivemind.mobile.core.models.DiffLine
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// --- Mapeamento modelo <-> entidade (puro, sem Android; testável em JUnit) ---

fun ProjectSummary.toEntity(nowMs: Long) = ProjectEntity(
    workspace = workspace,
    name = name,
    branch = branch,
    stack = stack,
    activeTeam = activeTeam,
    runningLanes = runningLanes,
    pendingApprovals = pendingApprovals,
    cachedAt = nowMs,
)

fun ProjectEntity.toModel() = ProjectSummary(
    workspace = workspace,
    name = name,
    branch = branch,
    stack = stack,
    activeTeam = activeTeam,
    runningLanes = runningLanes,
    pendingApprovals = pendingApprovals,
)

fun LaneSnapshot.laneEntity(nowMs: Long) = LaneEntity(
    laneKey = key,
    workspace = workspace,
    laneId = laneId,
    teamName = teamName,
    sessionId = sessionId,
    runMode = runMode,
    objective = objective,
    running = running,
    stage = stage,
    lastError = lastError,
    startedAt = startedAt,
    turnTokens = turnTokens,
    cachedAt = nowMs,
)

fun LaneEventItem.messageEntity(laneKey: String) = LaneMessageEntity(
    laneKey = laneKey,
    kind = kind,
    stage = stage,
    author = author,
    label = label,
    text = text,
    toolName = toolName,
    tokens = tokens,
    provider = provider,
    model = model,
    fromProvider = fromProvider,
    fromModel = fromModel,
    tier = tier,
    reasons = reasons.joinToString("\n"),
    estimatedMicroUsd = estimatedMicroUsd,
    decisionMicros = decisionMicros,
    at = at,
)

fun LaneMessageEntity.toModel() = LaneEventItem(
    kind = kind,
    stage = stage,
    author = author,
    label = label,
    text = text,
    toolName = toolName,
    tokens = tokens,
    provider = provider,
    model = model,
    fromProvider = fromProvider,
    fromModel = fromModel,
    tier = tier,
    reasons = if (reasons.isBlank()) emptyList() else reasons.split("\n"),
    estimatedMicroUsd = estimatedMicroUsd,
    decisionMicros = decisionMicros,
    at = at,
)

fun LaneEntity.toModel(events: List<LaneEventItem>) = LaneSnapshot(
    workspace = workspace,
    laneId = laneId,
    teamName = teamName,
    sessionId = sessionId,
    runMode = runMode,
    objective = objective,
    running = running,
    stage = stage,
    lastError = lastError,
    startedAt = startedAt,
    turnTokens = turnTokens,
    recentEvents = events,
)

fun FileDiff.diffEntity(workspace: String, nowMs: Long, linesJson: String) = DiffEntity(
    workspace = workspace,
    path = path,
    status = status,
    staged = staged,
    added = added,
    removed = removed,
    linesJson = linesJson,
    cachedAt = nowMs,
)

fun DiffEntity.toModel(lines: List<DiffLine>) = FileDiff(
    path = path,
    status = status,
    staged = staged,
    added = added,
    removed = removed,
    lines = lines,
)

/** Codifica/decodifica as linhas do diff em JSON no campo único da entidade. */
object DiffLinesCodec {
    private val serializer = ListSerializer(DiffLine.serializer())

    fun encode(lines: List<DiffLine>, json: Json): String = json.encodeToString(serializer, lines)

    fun decode(raw: String, json: Json): List<DiffLine> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

/**
 * Cache offline (Spec 10 §3.1.2): persiste o último snapshot de projetos,
 * lanes, mensagens e diffs em Room e devolve em forma de modelo quando a
 * rede não responde. Cache puro — nenhuma gravação acontece fora de
 * `AppContainer`, que só persiste estados vindos do bridge.
 */
class OfflineCache(
    private val dao: CacheDao,
    private val json: Json,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun saveProjects(list: List<ProjectSummary>) {
        val now = nowMs()
        if (list.isEmpty()) {
            dao.clearProjects()
            return
        }
        dao.upsertProjects(list.map { it.toEntity(now) })
        dao.pruneProjects(list.map { it.workspace })
    }

    suspend fun saveLanes(list: List<LaneSnapshot>) {
        val now = nowMs()
        if (list.isEmpty()) {
            dao.clearLanes()
            dao.clearMessages()
            return
        }
        dao.upsertLanes(list.map { it.laneEntity(now) })
        dao.pruneLanes(list.map { it.key })
        list.forEach { lane ->
            dao.replaceMessages(lane.key, lane.recentEvents.map { it.messageEntity(lane.key) })
        }
    }

    suspend fun saveLane(lane: LaneSnapshot) {
        dao.upsertLanes(listOf(lane.laneEntity(nowMs())))
        dao.replaceMessages(lane.key, lane.recentEvents.map { it.messageEntity(lane.key) })
    }

    suspend fun saveDiffs(workspace: String, files: List<FileDiff>) {
        val now = nowMs()
        if (files.isEmpty()) {
            dao.pruneDiffs(workspace, emptyList())
            return
        }
        dao.upsertDiffs(files.map { it.diffEntity(workspace, now, DiffLinesCodec.encode(it.lines, json)) })
        dao.pruneDiffs(workspace, files.map { it.path })
    }

    suspend fun cachedProjects(): List<ProjectSummary> = dao.projectsAll().map { it.toModel() }

    suspend fun cachedLanes(): List<LaneSnapshot> =
        dao.lanesAll().map { lane -> lane.toModel(dao.messagesFor(lane.laneKey).map { it.toModel() }) }

    suspend fun cachedDiffs(workspace: String): List<FileDiff> =
        dao.diffsFor(workspace).map { it.toModel(DiffLinesCodec.decode(it.linesJson, json)) }

    suspend fun clear() = dao.clearAll()

    companion object {
        /**
         * Banco único do app (cache descartável: migração destrutiva em vez de
         * crash no upgrade). AppContainer o constrói uma vez e deriva cache +
         * fila de envio do mesmo `cacheDao()`.
         */
        fun database(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hivemind-cache.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
