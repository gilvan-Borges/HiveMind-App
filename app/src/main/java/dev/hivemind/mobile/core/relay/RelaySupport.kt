package dev.hivemind.mobile.core.relay

import java.security.MessageDigest
import kotlinx.serialization.json.Json

/** JSON do fio: campo ausente em vez de default, para bater com o `omitempty` do relay. */
val RelayJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
    explicitNulls = false
}

object AccountNamespaces {
    /** Alias de Keystore e nome de arquivo estáveis, sem o id cru da conta. */
    fun alias(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return "hm_a_$hex"
    }
}

object Backoff {
    /** 1s, 2s, 4s… até 30s. `attempt` começa em 1. */
    fun delayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 5)
        return minOf(30_000L, 1_000L shl shift)
    }
}

object Redact {
    private val token = Regex("hmr_(at|rt|dev)_[A-Za-z0-9_-]+")
    private val lanToken = Regex("hmlan_[A-Za-z0-9_-]+")
    private val bearer = Regex("(?i)bearer\\s+\\S+")
    private val jsonSecret = Regex(
        "(?i)(\"(?:password|currentPassword|newPassword|refreshToken|accessToken|privateKey|deviceToken)\"\\s*:\\s*\")[^\"]*\"",
    )

    fun clean(message: String): String =
        message
            .replace(lanToken, "hmlan_[redacted]")
            .replace(token, "hmr_\$1_[redacted]")
            .replace(bearer, "Bearer [redacted]")
            .replace(jsonSecret, "\$1[redacted]\"")
}

fun interface RelayLog {
    fun warn(message: String)
}

object NoopRelayLog : RelayLog {
    override fun warn(message: String) = Unit
}

/** Cursor monotônico. Quadros WS chegam em ordem; `seq` já aplicado é ignorado. */
class EventCursor(var lastApplied: Long = 0) {
    fun accept(seq: Long): Boolean {
        if (seq <= lastApplied) return false
        lastApplied = seq
        return true
    }
}

object RpcAllowlist {
    private val allowed = setOf(
        "GET /api/v1/health",
        "GET /api/v1/projects",
        "GET /api/v1/lanes",
        "GET /api/v1/sessions",
        "GET /api/v1/session",
        "POST /api/v1/runs/start",
        "POST /api/v1/runs/cancel",
        "POST /api/v1/runs/redirect",
        "POST /api/v1/approvals/resolve",
        "GET /api/v1/git/diffs",
        "GET /api/v1/git/prs",
        "GET /api/v1/git/issues",
        "GET /api/v1/autoreview/status",
        "POST /api/v1/autoreview/poll",
    )

    fun allowed(method: String, path: String): Boolean {
        val m = method.trim().uppercase()
        var p = path.trim()
        val q = p.indexOf('?')
        if (q >= 0) p = p.substring(0, q)
        if (p.length > 1) p = p.trimEnd('/')
        if (p.isEmpty() || p.contains("..") || p.contains("://")) return false
        return "$m $p" in allowed
    }
}

object SessionSecrets {
    const val REFRESH = "refreshToken"
    const val ACCESS = "accessToken"
    const val EXPIRES = "accessExpiresAt"
    const val PUBLIC = "publicKey"
    const val PRIVATE = "privateKey"
    const val SELECTED = "selectedDeviceId"

    fun seqKey(deviceId: String): String =
        "seq_" + deviceId.filter { it.isLetterOrDigit() || it == '_' }

    /** Falta menos de 30s, ou não há access token. */
    fun shouldRefresh(expiresAtMs: Long, nowMs: Long, hasAccess: Boolean): Boolean =
        !hasAccess || expiresAtMs - nowMs < 30_000L

    /**
     * Grava o par novo. O refresh anterior deixa de existir no cofre:
     * reapresentá-lo revoga a sessão inteira.
     */
    fun persistRotation(
        vault: AccountVault,
        accountId: String,
        access: String,
        refresh: String,
        expiresAtMs: Long,
    ) {
        vault.putSecret(accountId, REFRESH, refresh)
        vault.putSecret(accountId, ACCESS, access)
        vault.putSecret(accountId, EXPIRES, expiresAtMs.toString())
    }
}
