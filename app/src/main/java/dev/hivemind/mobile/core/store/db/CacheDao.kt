package dev.hivemind.mobile.core.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Escrita/leitura do cache offline. Sempre last-write-wins por chave. */
@Dao
interface CacheDao {

    // --- Projects ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE workspace NOT IN (:keep)")
    suspend fun pruneProjects(keep: List<String>)

    @Query("SELECT * FROM projects ORDER BY name")
    suspend fun projectsAll(): List<ProjectEntity>

    // --- Lanes ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLanes(lanes: List<LaneEntity>)

    @Query("DELETE FROM lanes WHERE laneKey NOT IN (:keep)")
    suspend fun pruneLanes(keep: List<String>)

    @Query("SELECT * FROM lanes ORDER BY workspace, laneId")
    suspend fun lanesAll(): List<LaneEntity>

    // --- Mensagens por lane ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<LaneMessageEntity>)

    @Query("DELETE FROM lane_messages WHERE laneKey = :laneKey")
    suspend fun deleteMessagesFor(laneKey: String)

    @Query("SELECT * FROM lane_messages WHERE laneKey = :laneKey ORDER BY id")
    suspend fun messagesFor(laneKey: String): List<LaneMessageEntity>

    /** Substitui o transcript da lane inteiro (snapshot recente é a fonte da verdade). */
    @Transaction
    suspend fun replaceMessages(laneKey: String, messages: List<LaneMessageEntity>) {
        deleteMessagesFor(laneKey)
        insertMessages(messages)
    }

    // --- Diffs ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiffs(diffs: List<DiffEntity>)

    @Query("DELETE FROM diffs WHERE workspace = :workspace AND path NOT IN (:keep)")
    suspend fun pruneDiffs(workspace: String, keep: List<String>)

    @Query("SELECT * FROM diffs WHERE workspace = :workspace ORDER BY path")
    suspend fun diffsFor(workspace: String): List<DiffEntity>

    // --- Fila de envio (Spec 002) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingRun(run: PendingRunEntity)

    @Query("DELETE FROM pending_runs WHERE id = :id")
    suspend fun deletePendingRun(id: String)

    @Query("SELECT * FROM pending_runs ORDER BY createdAt")
    suspend fun pendingRunsAll(): List<PendingRunEntity>

    @Query("DELETE FROM pending_runs")
    suspend fun clearPendingRuns()

    // --- Manutenção ---

    @Query("DELETE FROM projects")
    suspend fun clearProjects()

    @Query("DELETE FROM lanes")
    suspend fun clearLanes()

    @Query("DELETE FROM lane_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM diffs")
    suspend fun clearDiffs()

    @Transaction
    suspend fun clearAll() {
        clearProjects()
        clearLanes()
        clearMessages()
        clearDiffs()
        clearPendingRuns()
    }
}
