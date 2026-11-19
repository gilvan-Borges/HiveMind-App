package dev.hivemind.mobile.core.relay

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Um alias do Android Keystore e um arquivo de preferências por conta.
 * O índice (e-mail, URL, papel) também fica cifrado, mas sem tokens.
 * A chave AES nunca sai do hardware/keystore.
 */
class KeystoreAccountVault(context: Context) : AccountVault {

    private val app = context.applicationContext
    private val lock = Any()

    override fun listAccounts(): List<AccountMeta> = readIndex().accounts

    override fun activeAccountId(): String? = readIndex().active

    override fun setActiveAccountId(id: String?) {
        synchronized(lock) {
            val idx = readIndex()
            writeIndex(idx.copy(active = id))
        }
    }

    override fun preferred(): String? = readIndex().preferred

    override fun setPreferred(value: String?) {
        synchronized(lock) {
            val idx = readIndex()
            writeIndex(idx.copy(preferred = value))
        }
    }

    override fun saveMeta(meta: AccountMeta) {
        synchronized(lock) {
            val idx = readIndex()
            val accounts = idx.accounts.filterNot { it.id == meta.id } + meta
            writeIndex(idx.copy(accounts = accounts))
        }
    }

    override fun deleteAccount(id: String) {
        synchronized(lock) {
            val idx = readIndex()
            writeIndex(
                idx.copy(
                    accounts = idx.accounts.filterNot { it.id == id },
                    active = if (idx.active == id) null else idx.active,
                ),
            )
            app.deleteSharedPreferences(prefsName(id))
            deleteKey(alias(id))
        }
    }

    override fun putSecret(accountId: String, key: String, value: String) {
        val cipher = encrypt(alias(accountId), value)
        if (!prefs(accountId).edit().putString(key, cipher).commit()) {
            throw IllegalStateException("não foi possível gravar o cofre")
        }
    }

    override fun getSecret(accountId: String, key: String): String? {
        val raw = prefs(accountId).getString(key, null) ?: return null
        return runCatching { decrypt(alias(accountId), raw) }.getOrNull()
    }

    override fun removeSecret(accountId: String, key: String) {
        if (!prefs(accountId).edit().remove(key).commit()) {
            throw IllegalStateException("não foi possível gravar o cofre")
        }
    }

    private fun readIndex(): VaultIndex {
        val raw = indexPrefs().getString(INDEX_KEY, null) ?: return VaultIndex()
        val plain = runCatching { decrypt(INDEX_ALIAS, raw) }.getOrNull() ?: return VaultIndex()
        return runCatching { RelayJson.decodeFromString<VaultIndex>(plain) }.getOrDefault(VaultIndex())
    }

    private fun writeIndex(index: VaultIndex) {
        val plain = RelayJson.encodeToString(VaultIndex.serializer(), index)
        val cipher = encrypt(INDEX_ALIAS, plain)
        if (!indexPrefs().edit().putString(INDEX_KEY, cipher).commit()) {
            throw IllegalStateException("não foi possível gravar o cofre")
        }
    }

    private fun indexPrefs(): SharedPreferences =
        app.getSharedPreferences(INDEX_PREFS, Context.MODE_PRIVATE)

    private fun prefs(accountId: String): SharedPreferences =
        app.getSharedPreferences(prefsName(accountId), Context.MODE_PRIVATE)

    private fun prefsName(accountId: String) = "hm_s_${alias(accountId)}"

    private fun alias(accountId: String) = AccountNamespaces.alias(accountId)

    private fun secretKey(alias: String): SecretKey {
        synchronized(lock) {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(alias, null) as? SecretKey
            if (existing != null) return existing
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            gen.init(spec)
            return gen.generateKey()
        }
    }

    private fun deleteKey(alias: String) {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        }
    }

    private fun encrypt(alias: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return Base64.getEncoder().encodeToString(out)
    }

    private fun decrypt(alias: String, encoded: String): String {
        val raw = Base64.getDecoder().decode(encoded)
        if (raw.size < 13) throw IllegalArgumentException("cofre vazio")
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val INDEX_ALIAS = "hm_relay_index"
        private const val INDEX_PREFS = "hm_relay_index"
        private const val INDEX_KEY = "blob"
    }
}
