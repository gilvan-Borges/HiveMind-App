package dev.hivemind.mobile.core.store.db

import dev.hivemind.mobile.core.api.ImageInput
import dev.hivemind.mobile.core.outbox.QueuedRun
import dev.hivemind.mobile.core.outbox.RunQueueState
import dev.hivemind.mobile.core.outbox.RunQueueStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Codifica as imagens anexadas em JSON no campo único da entidade. */
object RunImagesCodec {
    private val serializer = ListSerializer(ImageInput.serializer())

    fun encode(images: List<ImageInput>, json: Json): String = json.encodeToString(serializer, images)

    fun decode(raw: String, json: Json): List<ImageInput> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

fun QueuedRun.pendingRunEntity(json: Json) = PendingRunEntity(
    id = id,
    workspace = workspace,
    laneId = laneId,
    objective = objective,
    runMode = runMode,
    sessionId = sessionId.orEmpty(),
    imagesJson = RunImagesCodec.encode(images, json),
    state = state.name,
    lastError = lastError.orEmpty(),
    createdAt = createdAt,
)

fun PendingRunEntity.toQueuedRun(json: Json) = QueuedRun(
    id = id,
    workspace = workspace,
    laneId = laneId,
    objective = objective,
    runMode = runMode,
    sessionId = sessionId.takeIf { it.isNotBlank() },
    images = RunImagesCodec.decode(imagesJson, json),
    state = runCatching { RunQueueState.valueOf(state) }.getOrDefault(RunQueueState.FAILED),
    lastError = lastError.takeIf { it.isNotBlank() },
    createdAt = createdAt,
)

/** Fila de envio persistida em Room (Spec 002). */
class RunQueueStoreRoom(
    private val dao: CacheDao,
    private val json: Json,
) : RunQueueStore {

    override suspend fun all(): List<QueuedRun> =
        dao.pendingRunsAll().map { it.toQueuedRun(json) }

    override suspend fun upsert(run: QueuedRun) {
        dao.upsertPendingRun(run.pendingRunEntity(json))
    }

    override suspend fun delete(id: String) {
        dao.deletePendingRun(id)
    }
}
