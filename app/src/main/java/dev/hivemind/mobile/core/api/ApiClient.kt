package dev.hivemind.mobile.core.api

import android.util.Log
import dev.hivemind.mobile.core.lan.HttpTransport
import dev.hivemind.mobile.core.lan.LanSession
import dev.hivemind.mobile.core.lan.PlainHttp
import dev.hivemind.mobile.core.lan.WireRequest
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.RoutineInfo
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.core.models.Health
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectIssue
import dev.hivemind.mobile.core.models.ProjectPullRequest
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.SessionDetail
import dev.hivemind.mobile.core.models.WorkspaceSessionsSummary
import dev.hivemind.mobile.core.relay.Redact
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Erro de API do bridge (mensagem amigável do host). */
open class ApiException(
    message: String,
    val status: Int? = null,
    val code: String? = null,
) : Exception(message)

@Serializable
data class ProjectsResponse(val projects: List<ProjectSummary> = emptyList())

@Serializable
data class ApprovalsResponse(val approvals: List<ApprovalInfo> = emptyList())

@Serializable
data class RoutinesResponse(val routines: List<RoutineInfo> = emptyList())

@Serializable
data class ReadRequest(val workspace: String, val sessionId: String)

@Serializable
data class PermissionRequest(val workspace: String, val permissionMode: String)

@Serializable
data class CommitRequest(val workspace: String, val message: String, val push: Boolean = false)

@Serializable
data class CommitResponse(val committed: Boolean = false, val summary: String = "")

@Serializable
data class PullRequestBody(val workspace: String, val title: String, val body: String = "", val baseBranch: String = "main")

@Serializable
data class PullResponse(val opened: Boolean = false, val url: String = "")

@Serializable
data class RoutineEnabledRequest(val id: String, val enabled: Boolean)

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
data class SteerRequest(val laneId: String, val message: String)

@Serializable
data class SteerResponse(val steered: Boolean = false, val laneId: String = "")

@Serializable
data class ResolveRequest(val approvalId: String, val approved: Boolean)

@Serializable
data class PollRequest(val workspace: String)

@Serializable
private data class ReadAck(val read: Boolean = false)

@Serializable
private data class PermissionAck(val permissionMode: String = "")

@Serializable
private data class RoutineAck(val id: String = "", val enabled: Boolean = false)

/** Cliente HTTP do bridge. Na LAN o corpo é um quadro; Bearer em claro não autentica. */
class ApiClient(
    private val json: Json,
    private val http: HttpTransport = PlainHttp(),
) {
    @Volatile
    var baseUrl: String = ""

    /** Marcador do túnel relay. O token LAN não fica neste campo. */
    @Volatile
    var token: String = ""

    @Volatile
    var lanSession: LanSession? = null

    /** Quando preenchido, o plano de dados vai pelo túnel cifrado do relay. */
    @Volatile
    var tunnel: BridgeTunnel? = null

    val isConfigured: Boolean get() = tunnel != null || (baseUrl.isNotBlank() && lanSession != null)

    /** Testa a saúde de uma URL candidata. `/health` não exige quadro nem Bearer. */
    suspend fun healthAt(url: String): Health = withContext(Dispatchers.IO) {
        try {
            val resp = http.execute(WireRequest("GET", url.trimEnd('/') + "/api/v1/health"))
            if (resp.status !in 200..299) throw ApiException("HTTP ${resp.status}", resp.status)
            json.decodeFromString(resp.body.ifBlank { "{}" })
        } catch (e: ApiException) {
            Log.w("HiveMindMobile", Redact.clean("healthAt($url): ${e.message}"))
            throw e
        } catch (e: IOException) {
            Log.w("HiveMindMobile", Redact.clean("healthAt($url): falha de rede: ${e.message}"))
            throw ApiException("falha de rede: ${e.message}", null)
        }
    }

    suspend fun health(): Health = if (tunnel != null) get("/api/v1/health") else healthAt(baseUrl)

    suspend fun projects(): List<ProjectSummary> = get<ProjectsResponse>("/api/v1/projects").projects

    suspend fun lanes(workspace: String?): List<LaneSnapshot> =
        get<LanesResponse>("/api/v1/lanes", mapOf("workspace" to workspace)).lanes

    suspend fun sessions(workspace: String): WorkspaceSessionsSummary =
        get("/api/v1/sessions", mapOf("workspace" to workspace))

    suspend fun markSessionRead(workspace: String, sessionId: String) {
        post<ReadAck, ReadRequest>("/api/v1/session/read", ReadRequest(workspace, sessionId))
    }

    suspend fun approvals(workspace: String): List<ApprovalInfo> =
        get<ApprovalsResponse>("/api/v1/approvals", mapOf("workspace" to workspace)).approvals

    suspend fun setPermission(workspace: String, permissionMode: String) {
        post<PermissionAck, PermissionRequest>(
            "/api/v1/project/permission",
            PermissionRequest(workspace, permissionMode),
        )
    }

    suspend fun commit(workspace: String, message: String, push: Boolean = false): CommitResponse =
        post("/api/v1/git/commit", CommitRequest(workspace, message, push))

    suspend fun openPullRequest(workspace: String, title: String, body: String, baseBranch: String = "main"): PullResponse =
        post("/api/v1/git/pr", PullRequestBody(workspace, title, body, baseBranch))

    suspend fun routines(): List<RoutineInfo> = get<RoutinesResponse>("/api/v1/routines").routines

    suspend fun setRoutineEnabled(id: String, enabled: Boolean) {
        post<RoutineAck, RoutineEnabledRequest>("/api/v1/routines/enabled", RoutineEnabledRequest(id, enabled))
    }

    suspend fun session(workspace: String, id: String): SessionDetail =
        get("/api/v1/session", mapOf("workspace" to workspace, "id" to id))

    suspend fun startRun(req: RunStartRequest): StartResponse = post("/api/v1/runs/start", req)

    suspend fun redirect(req: RunStartRequest): StartResponse = post("/api/v1/runs/redirect", req)

    /** Injeta a nota na execução viva. Host antigo responde 404; sem run viva, 409. */
    suspend fun steer(laneId: String, message: String): SteerResponse =
        post("/api/v1/runs/steer", SteerRequest(laneId, message))

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

    private fun buildUrl(path: String, query: Map<String, String?>): String {
        val q = query.filterValues { !it.isNullOrBlank() }
            .map { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v!!, "UTF-8")}" }
        val sep = if (q.isEmpty()) "" else "?" + q.joinToString("&")
        return baseUrl.trimEnd('/') + path + sep
    }

    private suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T {
        val tun = tunnel
        if (tun != null) return tunnelCall(tun, "GET", path, query, null)
        return lanCall("GET", buildUrl(path, query), null)
    }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B): T {
        val jsonBody = json.encodeToString(body)
        val tun = tunnel
        if (tun != null) return tunnelCall(tun, "POST", path, emptyMap(), jsonBody)
        return lanCall("POST", buildUrl(path, emptyMap()), jsonBody)
    }

    private suspend inline fun <reified T> lanCall(method: String, url: String, body: String?): T {
        val session = lanSession ?: throw ApiException("sem pareamento")
        val text = withContext(Dispatchers.IO) { session.call(method, url, body) }
        return json.decodeFromString(if (text.isBlank()) "{}" else text)
    }

    private suspend inline fun <reified T> tunnelCall(
        tun: BridgeTunnel,
        method: String,
        path: String,
        query: Map<String, String?>,
        body: String?,
    ): T {
        if (!tun.ready) throw ApiException("túnel ainda não está pronto")
        if (!tun.ideOnline && path != "/api/v1/approvals/resolve") throw ApiException("IDE offline")
        val result = tun.rpc(method, path, queryString(query), body)
        if (result.status !in 200..299) {
            throw ApiException(errorMessage(result.body) ?: "HTTP ${result.status}", result.status)
        }
        val text = result.body.let { if (it.isBlank() || it == "null") "{}" else it }
        return json.decodeFromString(text)
    }

    private fun queryString(query: Map<String, String?>): String =
        query.filterValues { !it.isNullOrBlank() }
            .map { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v!!, "UTF-8")}" }
            .joinToString("&")

    private fun errorMessage(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val err = root["error"] ?: return null
        return when (err) {
            is JsonPrimitive -> err.contentOrNull
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }
}
