package dev.hivemind.mobile.core.relay

import java.util.concurrent.ConcurrentHashMap

/**
 * Cofre por conta. Segredos de uma conta não são legíveis pelo namespace de outra.
 * A implementação de produção é [KeystoreAccountVault]; os testes usam a memória.
 */
interface AccountVault {
    fun listAccounts(): List<AccountMeta>
    fun activeAccountId(): String?
    fun setActiveAccountId(id: String?)
    fun preferred(): String?
    fun setPreferred(value: String?)
    fun saveMeta(meta: AccountMeta)
    fun deleteAccount(id: String)
    fun putSecret(accountId: String, key: String, value: String)
    fun getSecret(accountId: String, key: String): String?
    fun removeSecret(accountId: String, key: String)
}

class MemoryAccountVault : AccountVault {
    private var active: String? = null
    private var preferred: String? = null
    private val accounts = LinkedHashMap<String, AccountMeta>()
    private val secrets = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    override fun listAccounts(): List<AccountMeta> = accounts.values.toList()

    override fun activeAccountId(): String? = active

    override fun setActiveAccountId(id: String?) {
        active = id
    }

    override fun preferred(): String? = preferred

    override fun setPreferred(value: String?) {
        preferred = value
    }

    override fun saveMeta(meta: AccountMeta) {
        accounts[meta.id] = meta
    }

    override fun deleteAccount(id: String) {
        accounts.remove(id)
        secrets.remove(id)
        if (active == id) active = null
    }

    override fun putSecret(accountId: String, key: String, value: String) {
        secrets.computeIfAbsent(accountId) { ConcurrentHashMap() }[key] = value
    }

    override fun getSecret(accountId: String, key: String): String? =
        secrets[accountId]?.get(key)

    override fun removeSecret(accountId: String, key: String) {
        secrets[accountId]?.remove(key)
    }

    /** Só para testes: concatena os segredos de uma conta. */
    fun debugSecrets(accountId: String): String =
        secrets[accountId]?.entries?.joinToString("|") { "${it.key}=${it.value}" }.orEmpty()

    fun debugAll(): String {
        val metas = accounts.values.joinToString("|") { "${it.id}:${it.email}:${it.relayUrl}" }
        val secs = secrets.entries.joinToString("||") { (id, map) ->
            id + "=" + map.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
        return "$metas ## $secs"
    }
}

/**
 * Segredos da conta que está na memória. [load] zera o que havia antes,
 * então a conta anterior não permanece em campo nenhum.
 */
class AccountSession {
    var accountId: String? = null
        private set
    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var accessExpiresAt: Long = 0L
        private set
    var keys: NaClBox.KeyPair? = null
        private set

    fun wipe() {
        accessToken = null
        refreshToken = null
        accessExpiresAt = 0L
        keys?.wipe()
        keys = null
        accountId = null
    }

    fun load(id: String, vault: AccountVault) {
        wipe()
        accountId = id
        accessToken = vault.getSecret(id, SessionSecrets.ACCESS)?.takeIf { it.isNotBlank() }
        refreshToken = vault.getSecret(id, SessionSecrets.REFRESH)?.takeIf { it.isNotBlank() }
        accessExpiresAt = vault.getSecret(id, SessionSecrets.EXPIRES)?.toLongOrNull() ?: 0L
        val pub = vault.getSecret(id, SessionSecrets.PUBLIC)
        val priv = vault.getSecret(id, SessionSecrets.PRIVATE)
        if (!pub.isNullOrBlank() && !priv.isNullOrBlank()) {
            val publicKey = runCatching { NaClBox.parseKey(pub) }.getOrNull()
            val privateKey = runCatching { NaClBox.parseKey(priv) }.getOrNull()
            if (publicKey != null && privateKey != null) {
                keys = NaClBox.KeyPair(publicKey, privateKey)
            }
        }
    }

    fun adoptTokens(access: String, refresh: String, expiresAtMs: Long) {
        accessToken = access
        refreshToken = refresh
        accessExpiresAt = expiresAtMs
    }

    fun adoptKeys(pair: NaClBox.KeyPair) {
        if (keys != null && keys !== pair) keys?.wipe()
        keys = pair
    }
}
