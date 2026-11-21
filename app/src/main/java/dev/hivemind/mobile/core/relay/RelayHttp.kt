package dev.hivemind.mobile.core.relay

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Plano de controle do relay. Não registra corpo, token nem senha. */
class RelayHttp(
    private val client: OkHttpClient = defaultClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun login(base: String, email: String, password: String): TokenResponse =
        post(base, "/api/v1/auth/login", null, buildJsonObject {
            put("email", email)
            put("password", password)
        })

    suspend fun refresh(base: String, refreshToken: String): TokenResponse =
        post(base, "/api/v1/auth/refresh", null, buildJsonObject {
            put("refreshToken", refreshToken)
        })

    suspend fun logout(base: String, accessToken: String?, refreshToken: String?) {
        send(
            "POST",
            base,
            "/api/v1/auth/logout",
            accessToken,
            buildJsonObject {
                if (!refreshToken.isNullOrBlank()) put("refreshToken", refreshToken)
            },
            allowEmpty = true,
        )
    }

    suspend fun changePassword(base: String, accessToken: String, current: String, next: String): TokenResponse =
        post(base, "/api/v1/auth/password", accessToken, buildJsonObject {
            put("currentPassword", current)
            put("newPassword", next)
        })

    suspend fun redeemQrLogin(base: String, ticket: String): TokenResponse =
        post(base, "/api/v1/auth/qr-redeem", null, buildJsonObject {
            put("ticket", ticket)
        })

    suspend fun createMember(base: String, accessToken: String, email: String, password: String): AccountInfo =
        post(base, "/api/v1/accounts", accessToken, buildJsonObject {
            put("email", email)
            put("password", password)
        }, expected = 201)

    suspend fun devices(base: String, accessToken: String): List<DeviceInfo> =
        get<DevicesResponse>(base, "/api/v1/devices", accessToken).devices

    suspend fun device(base: String, accessToken: String, id: String): DeviceInfo =
        get(base, "/api/v1/devices/${encodePath(id)}", accessToken)

    suspend fun peerKey(base: String, accessToken: String, id: String, publicKey: String): DeviceInfo =
        post(base, "/api/v1/devices/${encodePath(id)}/peer-key", accessToken, buildJsonObject {
            put("publicKey", publicKey)
        })

    suspend fun revoke(base: String, accessToken: String, id: String) {
        send(
            "POST",
            base,
            "/api/v1/devices/${encodePath(id)}/revoke",
            accessToken,
            buildJsonObject { },
            allowEmpty = true,
        )
    }

    suspend fun createLink(base: String, accessToken: String, role: String, name: String, publicKey: String): LinkInfo =
        post(base, "/api/v1/link-codes", accessToken, buildJsonObject {
            put("role", role)
            put("name", name)
            put("publicKey", publicKey)
        }, expected = 201)

    suspend fun getLink(base: String, accessToken: String, code: String): LinkInfo =
        get(base, "/api/v1/link-codes/${encodePath(LinkCodes.normalize(code))}", accessToken)

    suspend fun confirmLink(base: String, accessToken: String, code: String, publicKey: String): ConfirmLinkResponse =
        post(base, "/api/v1/link-codes/${encodePath(LinkCodes.normalize(code))}/confirm", accessToken, buildJsonObject {
            put("publicKey", publicKey)
        })

    private suspend inline fun <reified T> get(base: String, path: String, access: String): T {
        val text = send("GET", base, path, access, null, allowEmpty = false)
        return RelayJson.decodeFromString(text)
    }

    private suspend inline fun <reified T> post(
        base: String,
        path: String,
        access: String?,
        body: JsonObject,
        expected: Int? = null,
    ): T {
        val text = send("POST", base, path, access, body, allowEmpty = false)
        if (expected != null && text.isBlank()) {
            throw RelayException("internal", "resposta vazia do relay", expected)
        }
        return RelayJson.decodeFromString(text.ifBlank { "{}" })
    }

    private suspend fun send(
        method: String,
        base: String,
        path: String,
        access: String?,
        body: JsonObject?,
        allowEmpty: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val root = RelayUrls.normalize(base)
        val url = (root + path).toHttpUrl()
        if (url.scheme == "http" && !RelayUrls.isLoopback(url.host)) {
            throw RelayException("invalid_request", "um relay público exige https", 0)
        }
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (!access.isNullOrBlank()) builder.header("Authorization", "Bearer $access")
        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val raw = body?.toString() ?: "{}"
                builder.post(raw.toRequestBody(jsonMedia))
            }
            else -> error("método não suportado")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 204 || (allowEmpty && resp.isSuccessful && text.isBlank())) {
                return@withContext ""
            }
            if (!resp.isSuccessful) {
                throw parseFailure(resp.code, resp.header("Retry-After"), text)
            }
            text
        }
    }

    private fun parseFailure(status: Int, retryAfter: String?, text: String): RelayException {
        val doc = runCatching { RelayJson.decodeFromString<RelayErrorDoc>(text) }.getOrNull()
        val code = doc?.error?.code?.ifBlank { null } ?: "internal"
        val message = Redact.clean(doc?.error?.message?.ifBlank { null } ?: "relay respondeu $status")
        val retry = retryAfter?.trim()?.toIntOrNull()
        return RelayException(code, message, status, retry)
    }

    private fun encodePath(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val url = chain.request().url
                if (url.scheme == "http" && !RelayUrls.isLoopback(url.host)) {
                    throw java.io.IOException("um relay público exige https")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
}
