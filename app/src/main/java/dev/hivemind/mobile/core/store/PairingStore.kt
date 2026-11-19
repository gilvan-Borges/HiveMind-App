package dev.hivemind.mobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pairingDataStore by preferencesDataStore("hivemind_pairing")

/** Pareamento persistido com o PC host (URLs Tailscale + LAN, token, host). */
data class Pairing(
    val urls: List<String>,
    val token: String,
    val host: String,
    val activeUrl: String?,
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
        val TOKEN = stringPreferencesKey("token")
        val HOST = stringPreferencesKey("host")
        val ACTIVE_URL = stringPreferencesKey("active_url")
        val BACKGROUND = booleanPreferencesKey("background")
    }

    val pairing: Flow<Pairing?> = context.pairingDataStore.data.map { prefs ->
        val urls = prefs[Keys.URLS]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val token = prefs[Keys.TOKEN].orEmpty()
        if (urls.isEmpty() || token.isEmpty()) null
        else Pairing(urls, token, prefs[Keys.HOST].orEmpty(), prefs[Keys.ACTIVE_URL])
    }

    val backgroundEnabled: Flow<Boolean> =
        context.pairingDataStore.data.map { it[Keys.BACKGROUND] ?: false }

    suspend fun save(urls: List<String>, token: String, host: String, activeUrl: String? = null) {
        context.pairingDataStore.edit { prefs ->
            prefs[Keys.URLS] = urls.joinToString(",")
            prefs[Keys.TOKEN] = token
            prefs[Keys.HOST] = host
            if (activeUrl != null) prefs[Keys.ACTIVE_URL] = activeUrl
        }
    }

    suspend fun setActiveUrl(url: String) {
        context.pairingDataStore.edit { it[Keys.ACTIVE_URL] = url }
    }

    suspend fun setBackground(on: Boolean) {
        context.pairingDataStore.edit { it[Keys.BACKGROUND] = on }
    }

    suspend fun clear() {
        context.pairingDataStore.edit { it.clear() }
    }
}
