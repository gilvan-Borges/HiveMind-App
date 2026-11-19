package dev.hivemind.mobile.core.api

/** Transporte do bridge quando a chamada não vai por HTTP direto (túnel do relay). */
interface BridgeTunnel {
    val ideOnline: Boolean
    val ready: Boolean
    suspend fun rpc(method: String, path: String, query: String, bodyJson: String?): TunnelResult
}

data class TunnelResult(val status: Int, val body: String)
