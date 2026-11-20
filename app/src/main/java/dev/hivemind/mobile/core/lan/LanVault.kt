package dev.hivemind.mobile.core.lan

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.hivemind.mobile.core.relay.NaClBox
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable

/** Segredos do aparelho na LAN. O JSON só existe dentro do AES-GCM. */
class StoredLan(
    val deviceId: String,
    val privateKey: ByteArray,
    val token: String,
    val boxPub: String,
    val seq: Long,
) {
    fun withSeq(seq: Long) = StoredLan(deviceId, privateKey, token, boxPub, seq)
}

interface LanVault {
    fun load(): StoredLan?
    fun save(value: StoredLan)
    fun updateSeq(seq: Long)
    fun delete()
}

/**
 * Alias `hm_lan_device`: a chave AES/GCM/NoPadding fica no Android Keystore.
 * O blob cifrado guarda a privada X25519, o token `hmlan_` e a boxPub do PC.
 */
class KeystoreLanVault(context: Context) : LanVault {
    private val app = context.applicationContext
    private val lock = Any()

    override fun load(): StoredLan? = synchronized(lock) {
        val raw = prefs().getString(BLOB, null) ?: return null
        val plain = runCatching { decrypt(raw) }.getOrNull() ?: return null
        val dto = runCatching { LanJson.decodeFromString(StoredDto.serializer(), plain) }.getOrNull() ?: return null
        val priv = runCatching { NaClBox.decodeFlexible(dto.privateKey) }.getOrNull() ?: return null
        if (priv.size != 32 || dto.deviceId.isBlank() || !dto.token.startsWith(LanProtocol.TOKEN_PREFIX)) return null
        runCatching { NaClBox.parseKey(dto.boxPub) }.getOrNull() ?: return null
        StoredLan(dto.deviceId, priv, dto.token, dto.boxPub, dto.seq)
    }

    override fun save(value: StoredLan) {
        val dto = StoredDto(
            deviceId = value.deviceId,
            privateKey = NaClBox.encodeKey(value.privateKey),
            token = value.token,
            boxPub = value.boxPub,
            seq = value.seq,
        )
        val cipher = encrypt(LanJson.encodeToString(StoredDto.serializer(), dto))
        synchronized(lock) {
            if (!prefs().edit().putString(BLOB, cipher).commit()) {
                throw IllegalStateException("não foi possível gravar o cofre")
            }
        }
    }

    override fun updateSeq(seq: Long) {
        val current = load() ?: return
        save(current.withSeq(seq))
    }

    override fun delete() {
        synchronized(lock) {
            prefs().edit().clear().commit()
            runCatching {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            }
        }
    }

    private fun prefs(): SharedPreferences =
        app.getSharedPreferences(ALIAS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
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

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return Base64.getEncoder().encodeToString(out)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.getDecoder().decode(encoded)
        if (raw.size < 13) throw IllegalArgumentException("cofre vazio")
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    @Serializable
    private data class StoredDto(
        val deviceId: String,
        val privateKey: String,
        val token: String,
        val boxPub: String,
        val seq: Long = 0,
    )

    companion object {
        const val ALIAS = "hm_lan_device"
        private const val BLOB = "blob"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
