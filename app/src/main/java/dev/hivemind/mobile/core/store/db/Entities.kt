package dev.hivemind.mobile.core.store.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Último snapshot de projetos visto (Spec 10: cache offline Room). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val workspace: String,
    val name: String,
    val branch: String,
    val stack: String,
    val activeTeam: String,
    val runningLanes: Int,
    val pendingApprovals: Int,
    val cachedAt: Long,
)

/** Estado vivo de uma lane (workspace|laneId). */
@Entity(tableName = "lanes")
data class LaneEntity(
    @PrimaryKey val laneKey: String,
    val workspace: String,
    val laneId: String,
    val teamName: String,
    val sessionId: String,
    val runMode: String,
    val objective: String,
    val running: Boolean,
    val stage: String,
    val lastError: String,
    val startedAt: String,
    val turnTokens: Long,
    val cachedAt: Long,
)

/** Uma mensagem/evento do transcript da lane, na ordem em que chegou. */
@Entity(tableName = "lane_messages", indices = [Index("laneKey")])
data class LaneMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val laneKey: String,
    val kind: String,
    val stage: String,
    val author: String,
    val label: String,
    val text: String,
    val toolName: String,
    val tokens: Long,
    val provider: String,
    val model: String,
    val fromProvider: String,
    val fromModel: String,
    val tier: String,
    val reasons: String,
    val estimatedMicroUsd: Long,
    val decisionMicros: Long,
    val at: String,
)

/**
 * Objetivo enfileirado para envio futuro (Spec 002): criado quando o bridge
 * está inacessível, enviado automaticamente quando a conexão volta e a lane
 * está livre. `SENDING` sobrevivente de um crash é reclassificado como
 * FAILED no restore — repetir um run sem certeza custa tokens.
 */
@Entity(tableName = "pending_runs")
data class PendingRunEntity(
    @PrimaryKey val id: String,
    val workspace: String,
    val laneId: String,
    val objective: String,
    val runMode: String,
    val sessionId: String,
    val imagesJson: String,
    val state: String,
    val lastError: String,
    val createdAt: Long,
)

/** Último diff conhecido de um arquivo num workspace. */
@Entity(tableName = "diffs", primaryKeys = ["workspace", "path"])
data class DiffEntity(
    val workspace: String,
    val path: String,
    val status: String,
    val staged: Boolean,
    val added: Int,
    val removed: Int,
    val linesJson: String,
    val cachedAt: Long,
)
