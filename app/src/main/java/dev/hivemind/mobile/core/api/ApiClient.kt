package dev.hivemind.mobile.core.api

import dev.hivemind.mobile.core.models.ApiErrorBody
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.core.models.Health
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectIssue
import dev.hivemind.mobile.core.models.ProjectPullRequest
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.SessionDetail
import dev.hivemind.mobile.core.models.WorkspaceSessionsSummary
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Erro de API do bridge (mensagem amigável do host). */
class ApiException(message: String, val status: Int? = null) : Exception(message)

// --- Payloads ---

@Serializable
data class ProjectsResponse(val projects: List<ProjectSummary> = emptyList())

@Serializable
data class LanesResponse(val lanes: List<LaneSnapshot> = emptyList())

@Serializable
data class FilesResponse(val files: List<FileDiff> = emptyList())

@Serializable
data class PRsResponse(val prs: List<ProjectPullRequest> = emptyList())

@Serializable
data class IssuesResponse(val issues: List<ProjectIssue> = emptyList())

@Serializable
data class StartResponse(val accepted: Boolean = false, val laneId: String = "", val workspace: String = "")

@Serializable
data class CancelResponse(val cancelled: Boolean = false)

@Serializable
data class ResolveResponse(val resolved: Boolean = false)

@Serializable
data class ImageInput(val name: String = "", val mimeType: String = "", val dataBase64: String = "")

@Serializable
data class RunStartRequest(
    val laneId: String,
    val workspace: String,
    val team: String? = null,
    val objective: String? = null,
    val jiraKey: String? = null,
    val sessionId: String? = null,
    val runMode: String? = null,
    val images: List<ImageInput> = emptyList(),
)

@Serializable
data class CancelRequest(val laneId: String)

@Serializable
data class ResolveRequest(val approvalId: String, val approved: Boolean)

@Serializable
data class PollRequest(val workspace: String)

// --- Parser do QR hivemind://pair ---

object PairingParser {
    /** Decodifica hivemind://pair?urls=...&token=...&host=... → (urls, token, host). */
    fun parse(raw: String): Triple<List<String>, String, String>? {
        val text = raw.trim()
        if (!text.startsWith("hivemind://pair")) return null
        val query = text.substringAfter("?", "")
        if (query.isEmpty()) return null
        val params = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
        val urls = params["urls"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val token = params["token"].orEmpty()
        if (urls.isEmpty() || token.isEmpty()) return null
        return Triple(urls, token, params["host"].orEmpty())
    }
}

// --- Cliente HTTP do bridge ---

class ApiClient(private val json: Json) {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var token: String = ""

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    /** Testa a saúde de uma URL candidata (usado no pareamento com fallback). */
    suspend fun healthAt(url: String, token: String): Health {
        val req = Request.Builder()
            .url(url.trimEnd('/') + "/api/v1/health")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}", resp.code)
                    json.decodeFromString<Health>(text)
                }
            } catch (e: ApiException) {
                Log.w("HiveMindMobile", "healthAt($url): ${e.message}")
                throw e
            } catch (e: java.io.IOException) {
                Log.w("HiveMindMobile", "healthAt($url): falha de rede: ${e.message}")
                throw ApiException("falha de rede: ${e.message}", null)
            }
        }
    }

    suspend fun health(): Health = get("/api/v1/health")

    suspend fun projects(): List<ProjectSummary> = get<ProjectsResponse>("/api/v1/projects").projects

    suspend fun lanes(workspace: String?): List<LaneSnapshot> =
        get<LanesResponse>("/api/v1/lanes", mapOf("workspace" to workspace)).lanes

    suspend fun sessions(workspace: String): WorkspaceSessionsSummary =
        get("/api/v1/sessions", mapOf("workspace" to workspace))

    suspend fun session(workspace: String, id: String): SessionDetail =
        get("/api/v1/session", mapOf("workspace" to workspace, "id" to id))

    suspend fun startRun(req: RunStartRequest): StartResponse = post("/api/v1/runs/start", req)

    suspend fun redirect(req: RunStartRequest): StartResponse = post("/api/v1/runs/redirect", req)

    suspend fun cancel(laneId: String): CancelResponse = post("/api/v1/runs/cancel", CancelRequest(laneId))

    suspend fun resolveApproval(approvalId: String, approved: Boolean): ResolveResponse =
        post("/api/v1/approvals/resolve", ResolveRequest(approvalId, approved))

    suspend fun diffs(workspace: String): List<FileDiff> =
        get<FilesResponse>("/api/v1/git/diffs", mapOf("workspace" to workspace)).files

    suspend fun prs(workspace: String): List<ProjectPullRequest> =
        get<PRsResponse>("/api/v1/git/prs", mapOf("workspace" to workspace)).prs

    suspend fun issues(workspace: String): List<ProjectIssue> =
        get<IssuesResponse>("/api/v1/git/issues", mapOf("workspace" to workspace)).issues

    suspend fun autoreviewStatus(workspace: String): AutoReviewStatus =
        get("/api/v1/autoreview/status", mapOf("workspace" to workspace))

    suspend fun autoreviewPoll(workspace: String): AutoReviewStatus =
        post("/api/v1/autoreview/poll", PollRequest(workspace))

    // --- internos ---

    private fun buildUrl(path: String, query: Map<String, String?>): String {
        val q = query.filterValues { !it.isNullOrBlank() }
            .map { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v!!, "UTF-8")}" }
        val sep = if (q.isEmpty()) "" else "?" + q.joinToString("&")
        return baseUrl.trimEnd('/') + path + sep
    }

    private fun authed(builder: Request.Builder) {
        builder.header("Authorization", "Bearer $token")
    }

    private suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T {
        val req = Request.Builder().url(buildUrl(path, query)).get()
        authed(req)
        return execute(req.build())
    }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B): T {
        val jsonBody = json.encodeToString(body)
        val req = Request.Builder()
            .url(buildUrl(path, emptyMap()))
            .post(jsonBody.toRequestBody(mediaType))
        authed(req)
        return execute(req.build())
    }

    private suspend inline fun <reified T> execute(req: Request): T = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { json.decodeFromString<ApiErrorBody>(text).error }.getOrNull()
                throw ApiException(msg ?: "HTTP ${resp.code}", resp.code)
            }
            if (text.isBlank()) return@withContext json.decodeFromString<T>("{}")
            json.decodeFromString<T>(text)
        }
    }
}
