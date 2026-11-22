package dev.hivemind.mobile.core.relay

import java.security.MessageDigest

/**
 * Política TOFU (trust-on-first-use) da chave pública da IDE.
 *
 * A primeira chave vista é pinada no cofre da conta e adotada. Toda divergência
 * posterior é tratada como rotação **pendente de confirmação explícita do
 * usuário** — nunca aceita silenciosamente (Spec 10 §3.1.3).
 */
sealed interface KeyTrust {
    /** A IDE ainda não publicou chave nenhuma. */
    data object NoKey : KeyTrust

    /** Primeiro uso: pin e adote (TOFU). */
    data class FirstUse(val newKey: String) : KeyTrust

    /** Chave confere com o pin: adote sem alarde. */
    data class Match(val key: String) : KeyTrust

    /** Chave diverge do pin: exija confirmação antes de adotar. */
    data class Rotated(val pinnedKey: String, val incomingKey: String) : KeyTrust
}

object KeyTrustPolicy {

    /** Decide o que fazer com a chave que o relay/IDE apresentou agora. */
    fun evaluate(pinnedKey: String?, incomingKey: String): KeyTrust = when {
        incomingKey.isBlank() -> KeyTrust.NoKey
        pinnedKey.isNullOrBlank() -> KeyTrust.FirstUse(incomingKey)
        incomingKey == pinnedKey -> KeyTrust.Match(incomingKey)
        else -> KeyTrust.Rotated(pinnedKey, incomingKey)
    }

    /**
     * Fingerprint curto e legível de uma chave pública (base64url): 16 hex
     * maiúsculos de SHA-256, para a pessoa comparar olho-a-olho no diálogo.
     */
    fun fingerprint(publicKeyB64: String): String {
        val raw = runCatching { NaClBox.decodeFlexible(publicKeyB64) }.getOrNull()
        if (raw == null || raw.isEmpty()) return "—"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.joinToString("") { "%02x".format(it) }
            .take(16)
            .uppercase()
    }
}
