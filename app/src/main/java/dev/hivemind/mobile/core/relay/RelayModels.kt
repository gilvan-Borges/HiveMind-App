package dev.hivemind.mobile.core.relay

import kotlinx.serialization.Serializable

@Serializable
data class AccountInfo(
    val id: String = "",
    val email: String = "",
    val role: String = "",
)

@Serializable
data class TokenResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Int = 0,
    val tokenType: String = "",
    val account: AccountInfo = AccountInfo(),
)

@Serializable
data class DeviceInfo(
    val id: String = "",
    val name: String = "",
    val online: Boolean = false,
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val idePublicKey: String = "",
    val appPublicKey: String = "",
    val e2eReady: Boolean = false,
    val eventSeq: Long = 0,
)

@Serializable
data class DevicesResponse(val devices: List<DeviceInfo> = emptyList())

@Serializable
data class LinkInfo(
    val code: String = "",
    val role: String = "",
    val name: String = "",
    val expiresAt: String = "",
    val uri: String = "",
    val status: String = "",
    val deviceId: String = "",
    val idePublicKey: String = "",
    val appPublicKey: String = "",
)

@Serializable
data class ConfirmLinkResponse(
    val device: DeviceInfo = DeviceInfo(),
)

@Serializable
data class RelayErrorBody(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class RelayErrorDoc(
    val error: RelayErrorBody = RelayErrorBody(),
)

@Serializable
data class AccountMeta(
    val id: String,
    val email: String,
    val role: String,
    val relayUrl: String,
)

@Serializable
data class VaultIndex(
    val active: String? = null,
    val preferred: String? = null,
    val accounts: List<AccountMeta> = emptyList(),
)

class RelayException(
    val code: String,
    message: String,
    val status: Int,
    val retryAfterSec: Int? = null,
) : Exception(message)
