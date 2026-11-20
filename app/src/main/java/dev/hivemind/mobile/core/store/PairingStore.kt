package dev.hivemind.mobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pairingDataStore by preferencesDataStore("hivemind_pairing")

/**
 * Pareamento LAN persistido. Token, privada e boxPub do PC não entram aqui:
 * ficam no alias `hm_lan_device`.
 */
data class Pairing(
    val urls: List<String>,
    val host: String,
    val activeUrl: String?,
    val deviceId: String = "",
) {
    /** Rota amigável: relay, Tailscale (100.x) ou Wi-Fi (LAN). */
    val routeLabel: String
        get() = when {
            activeUrl?.startsWith("relay://") == true -> if (host.isNotBlank()) "Relay · $host" else "Relay"
            activeUrl == null -> "—"
            activeUrl.contains("100.") -> "Tailscale"
            else -> "Wi-Fi"
        }
}

class PairingStore(private val context: Context) {

    private object Keys {
        val URLS = stringPreferencesKey("urls")
        val HOST = stringPreferencesKey("host")
        val ACTIVE_URL = stringPreferencesKey("active_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val BACKGROUND = booleanPreferencesKey("background")
        val LEGACY_TOKEN = stringPreferencesKey("token")
    }

    val pairing: Flow<Pairing?> = context.pairingDataStore.data.map { prefs ->
        val urls = prefs[Keys.URLS]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val deviceId = prefs[Keys.DEVICE_ID].orEmpty()
        if (urls.isEmpty() || deviceId.isBlank()) null
        else Pairing(urls, prefs[Keys.HOST].orEmpty(), prefs[Keys.ACTIVE_URL], deviceId)
    }

    val backgroundEnabled: Flow<Boolean> =
        context.pairingDataStore.data.map { it[Keys.BACKGROUND] ?: false }

    suspend fun save(urls: List<String>, host: String, activeUrl: String?, deviceId: String) {
        context.pairingDataStore.edit { prefs ->
            prefs.remove(Keys.LEGACY_TOKEN)
            prefs[Keys.URLS] = urls.joinToString(",")
            prefs[Keys.HOST] = host
            prefs[Keys.DEVICE_ID] = deviceId
            if (activeUrl != null) prefs[Keys.ACTIVE_URL] = activeUrl
        }
    }

    suspend fun setActiveUrl(url: String) {
        context.pairingDataStore.edit { it[Keys.ACTIVE_URL] = url }
    }

    suspend fun setBackground(on: Boolean) {
        context.pairingDataStore.edit { it[Keys.BACKGROUND] = on }
    }

    /**
     * Apaga token legado. Se não houver deviceId, o pareamento v1 inteiro sai:
     * Bearer em claro não autentica mais.
     * @return true quando sobrou um pareamento inutilizável e ele foi apagado.
     */
    suspend fun discardCleartextToken(): Boolean {
        var blocked = false
        context.pairingDataStore.edit { prefs ->
            val hadToken = prefs.contains(Keys.LEGACY_TOKEN)
            if (hadToken) prefs.remove(Keys.LEGACY_TOKEN)
            val device = prefs[Keys.DEVICE_ID].orEmpty()
            val leftover = hadToken || prefs.contains(Keys.URLS) || prefs.contains(Keys.HOST) || prefs.contains(Keys.ACTIVE_URL)
            if (device.isBlank() && leftover) {
                blocked = true
                prefs.remove(Keys.URLS)
                prefs.remove(Keys.HOST)
                prefs.remove(Keys.ACTIVE_URL)
            }
        }
        return blocked
    }

    suspend fun clear() {
        context.pairingDataStore.edit { it.clear() }
    }

    companion object {
        /** Chaves públicas do DataStore. Segredo nenhum. `background` é a flag antiga de serviço. */
        val persistedNames = setOf("urls", "host", "active_url", "device_id", "background")
    }
}
