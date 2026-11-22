package dev.hivemind.mobile.core.relay

import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.BridgeTunnel
import dev.hivemind.mobile.core.api.TunnelResult
import dev.hivemind.mobile.core.models.StreamEvent
import dev.hivemind.mobile.core.state.ConnState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * A chave pública da IDE mudou em relação ao pin TOFU. Nada é adotado antes
 * de uma confirmação explícita (Spec 10: nunca aceitar silenciosamente).
 */
data class KeyRotationNotice(
    val deviceId: String,
    val deviceName: String,
    val oldFingerprint: String,
    val newFingerprint: String,
    val atMs: Long,
)

/**
 * Sessão do app com o relay: contas isoladas, rotação de refresh, lista de IDEs,
 * vínculo e o WebSocket cifrado. A senha nunca é gravada.
 */
class RelayController(
    private val http: RelayHttp,
    private val vault: AccountVault,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    var log: RelayLog = NoopRelayLog,
    var onConn: (ConnState) -> Unit = {},
    var onEvent: (StreamEvent) -> Unit = {},
    var onHello: suspend (DeviceInfo) -> Unit = {},
    var onTunnelClosed: suspend () -> Unit = {},
    var onBeforeTunnel: () -> Unit = {},
) : BridgeTunnel {

    private val session = AccountSession()
    private val refreshMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TunnelResult>>()
    private val wsClient = RelayHttp.defaultClient().newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private val accountsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<AccountMeta>>(emptyList())
    private val activeFlow = kotlinx.coroutines.flow.MutableStateFlow<AccountMeta?>(null)
    private val loggedInFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val devicesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<DeviceInfo>>(emptyList())
    private val selectedFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val errorFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val linkFlow = kotlinx.coroutines.flow.MutableStateFlow<LinkInfo?>(null)
    private val forceLoginFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val draftFlow = kotlinx.coroutines.flow.MutableStateFlow<AccountMeta?>(null)
    private val ideOnlineFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val e2eFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val readyFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val keyRotationFlow = kotlinx.coroutines.flow.MutableStateFlow<KeyRotationNotice?>(null)

    /** Nova chave da IDE aguardando confirmação do usuário (TOFU). */
    @Volatile
    private var pendingPeerKey: String? = null

    val accounts = accountsFlow
    val active = activeFlow
    val loggedIn = loggedInFlow
    val devices = devicesFlow
    val selectedDeviceId = selectedFlow
    val lastError = errorFlow
    val link = linkFlow
    val forceLogin = forceLoginFlow
    val loginDraft = draftFlow
    val ideOnlineState = ideOnlineFlow
    val e2eReady = e2eFlow

    /** Rotação de chave TOFU pendente de confirmação do usuário, se houver. */
    val keyRotation: kotlinx.coroutines.flow.StateFlow<KeyRotationNotice?> get() = keyRotationFlow

    override val ideOnline: Boolean get() = ideOnlineFlow.value
    override val ready: Boolean get() = readyFlow.value

    private var job: Job? = null
    private var pollJob: Job? = null
    private var linkJob: Job? = null
    private var socket: WebSocket? = null
    private var engine: RelaySessionEngine? = null
    private var running = false
    private var currentDevice: String? = null
    private var fatalAuth = false
    private var fatalDevice = false
    private val lastFrameAt = AtomicLong(0L)

    fun load() {
        accountsFlow.value = vault.listAccounts()
        val id = vault.activeAccountId() ?: return
        val meta = accountsFlow.value.find { it.id == id } ?: return
        session.load(id, vault)
        val access = session.accessToken
        val refresh = session.refreshToken
        // Mesma política do login/refresh: tokens sem prefixo não voltam à rede.
        if (refresh.isNullOrBlank() ||
            !refresh.startsWith("hmr_rt_") ||
            (access != null && !access.startsWith("hmr_at_"))
        ) {
            session.wipe()
            draftFlow.value = meta
            loggedInFlow.value = false
            activeFlow.value = null
            return
        }
        activeFlow.value = meta
        loggedInFlow.value = true
        forceLoginFlow.value = false
        selectedFlow.value = vault.getSecret(id, SessionSecrets.SELECTED)
    }

    fun preferred(): String? = vault.preferred()

    fun preferLan() {
        vault.setPreferred("lan")
    }

    fun canResumeDevice(): Boolean = loggedInFlow.value && !selectedFlow.value.isNullOrBlank()

    fun resumeSelected() {
        val id = selectedFlow.value ?: return
        scope.launch {
            runCatching { connect(id) }.onFailure { errorFlow.value = safeMessage(it) }
        }
    }

    fun openLogin(draft: AccountMeta? = null) {
        draftFlow.value = draft
        forceLoginFlow.value = true
    }

    fun closeLogin() {
        forceLoginFlow.value = false
    }

    suspend fun login(relayUrl: String, email: String, password: String) {
        val trimmedMail = email.trim()
        if (trimmedMail.length !in 3..254 || trimmedMail.any { it.isWhitespace() }) {
            throw IllegalArgumentException("e-mail ou usuário inválido")
        }
        if (password.length !in 8..200) {
            throw IllegalArgumentException("a senha precisa ter entre 8 e 200 caracteres")
        }
        val base = RelayUrls.normalize(relayUrl)
        val resp = http.login(base, trimmedMail, password)
        adoptLoginResponse(base, trimmedMail, resp)
    }

    suspend fun redeemQrLogin(relayUrl: String, ticket: String) {
        val cleanTicket = ticket.trim()
        if (cleanTicket.isEmpty()) {
            throw IllegalArgumentException("QR Code de login inválido")
        }
        val base = RelayUrls.normalize(relayUrl)
        val resp = http.redeemQrLogin(base, cleanTicket)
        adoptLoginResponse(base, "", resp)
    }

    private suspend fun adoptLoginResponse(base: String, fallbackEmail: String, resp: TokenResponse) {
        if (!resp.accessToken.startsWith("hmr_at_") || !resp.refreshToken.startsWith("hmr_rt_") || resp.account.id.isBlank()) {
            throw IllegalArgumentException("resposta de login inválida")
        }
        stopAndJoin()
        session.wipe()
        val meta = AccountMeta(resp.account.id, resp.account.email.ifBlank { fallbackEmail }, resp.account.role, base)
        vault.saveMeta(meta)
        val expires = clock() + resp.expiresIn.coerceAtLeast(1) * 1000L
        session.adoptTokens(resp.accessToken, resp.refreshToken, expires)
        SessionSecrets.persistRotation(vault, meta.id, resp.accessToken, resp.refreshToken, expires)
        // A sessão em memória já tem os tokens; recarregar do cofre confirma o isolamento.
        session.load(meta.id, vault)
        session.adoptTokens(resp.accessToken, resp.refreshToken, expires)
        ensureKeys(meta.id)
        vault.setActiveAccountId(meta.id)
        activeFlow.value = meta
        loggedInFlow.value = true
        forceLoginFlow.value = false
        draftFlow.value = null
        accountsFlow.value = vault.listAccounts()
        errorFlow.value = null
        selectedFlow.value = vault.getSecret(meta.id, SessionSecrets.SELECTED)
        refreshDeviceList()
    }

    suspend fun logout() {
        val meta = activeFlow.value
        val access = session.accessToken
        val refresh = session.refreshToken
        disconnectIde()
        if (meta != null && (!access.isNullOrBlank() || !refresh.isNullOrBlank())) {
            runCatching { http.logout(meta.relayUrl, access, refresh) }
                .onFailure { errorFlow.value = "saiu neste aparelho; o relay não confirmou a revogação" }
        }
        if (meta != null) {
            vault.removeSecret(meta.id, SessionSecrets.REFRESH)
            vault.removeSecret(meta.id, SessionSecrets.ACCESS)
            vault.removeSecret(meta.id, SessionSecrets.EXPIRES)
            vault.removeSecret(meta.id, SessionSecrets.SELECTED)
        }
        session.wipe()
        vault.setActiveAccountId(null)
        activeFlow.value = null
        loggedInFlow.value = false
        devicesFlow.value = emptyList()
        selectedFlow.value = null
        accountsFlow.value = vault.listAccounts()
    }

    suspend fun switchAccount(id: String) {
        val meta = vault.listAccounts().find { it.id == id } ?: return
        stopAndJoin()
        scope.launch { onTunnelClosed() }
        session.wipe()
        devicesFlow.value = emptyList()
        linkFlow.value = null
        linkJob?.cancel()
        vault.setActiveAccountId(id)
        session.load(id, vault)
        accountsFlow.value = vault.listAccounts()
        errorFlow.value = null
        val access = session.accessToken
        val refresh = session.refreshToken
        // Same gate as load(): refuse non-hmr_* vault blobs before any Bearer/WebSocket use.
        if (refresh.isNullOrBlank() ||
            !refresh.startsWith("hmr_rt_") ||
            (access != null && !access.startsWith("hmr_at_"))
        ) {
            session.wipe()
            activeFlow.value = null
            loggedInFlow.value = false
            selectedFlow.value = null
            draftFlow.value = meta
            forceLoginFlow.value = true
            return
        }
        activeFlow.value = meta
        loggedInFlow.value = true
        forceLoginFlow.value = false
        draftFlow.value = null
        selectedFlow.value = vault.getSecret(id, SessionSecrets.SELECTED)
        refreshDeviceList()
    }

    suspend fun removeAccount(id: String) {
        if (session.accountId == id || activeFlow.value?.id == id) {
            stopAndJoin()
            session.wipe()
            activeFlow.value = null
            loggedInFlow.value = false
            devicesFlow.value = emptyList()
            selectedFlow.value = null
            scope.launch { onTunnelClosed() }
        }
        vault.deleteAccount(id)
        accountsFlow.value = vault.listAccounts()
        if (draftFlow.value?.id == id) draftFlow.value = null
    }

    suspend fun refreshDeviceList() {
        val meta = activeFlow.value ?: return
        refreshIfNeeded(force = false)
        val access = session.accessToken ?: return
        val list = http.devices(meta.relayUrl, access)
        devicesFlow.value = list
        val sel = currentDevice
        if (sel != null) {
            list.find { it.id == sel }?.let { ideOnlineFlow.value = it.online }
        }
    }

    suspend fun connect(deviceId: String) {
        val meta = requireMeta()
        refreshIfNeeded(force = false)
        var dev = devicesFlow.value.find { it.id == deviceId }
            ?: http.device(meta.relayUrl, requireAccess(), deviceId)
        if (dev.idePublicKey.isBlank()) {
            throw IllegalStateException("a IDE ainda não publicou a chave pública")
        }
        val keys = ensureKeys(meta.id)
        val pub = NaClBox.encodeKey(keys.publicKey)
        if (!NaClBox.sameKey(dev.appPublicKey, pub)) {
            dev = http.peerKey(meta.relayUrl, requireAccess(), dev.id, pub)
            refreshDeviceList()
        }
        vault.putSecret(meta.id, SessionSecrets.SELECTED, dev.id)
        vault.setPreferred("relay")
        selectedFlow.value = dev.id
        fatalAuth = false
        fatalDevice = false
        errorFlow.value = null
        onBeforeTunnel()
        stopAndJoin()
        currentDevice = dev.id
        running = true
        job = scope.launch { loop(dev) }
        pollJob = scope.launch {
            while (running) {
                delay(20_000)
                if (!running) break
                runCatching { refreshDeviceList() }
            }
        }
    }

    suspend fun disconnectIde() {
        val id = session.accountId
        if (id != null) vault.removeSecret(id, SessionSecrets.SELECTED)
        selectedFlow.value = null
        currentDevice = null
        suspendTunnel()
        onTunnelClosed()
    }

    /** Para o socket sem apagar a conta nem o device selecionado (troca para a LAN). */
    suspend fun suspendTunnel() {
        stopAndJoin()
        readyFlow.value = false
        ideOnlineFlow.value = false
        e2eFlow.value = false
        onConn(ConnState.Disconnected)
    }

    suspend fun revoke(deviceId: String) {
        val meta = requireMeta()
        refreshIfNeeded(force = false)
        http.revoke(meta.relayUrl, requireAccess(), deviceId)
        if (currentDevice == deviceId || selectedFlow.value == deviceId) {
            disconnectIde()
        }
        refreshDeviceList()
    }

    suspend fun confirmLink(rawCode: String, relayFromQr: String?) {
        val meta = requireMeta()
        if (!relayFromQr.isNullOrBlank() && !RelayUrls.same(relayFromQr, meta.relayUrl)) {
            throw IllegalArgumentException("este código é de outro relay")
        }
        if (!LinkCodes.isValid(rawCode)) throw IllegalArgumentException("código de vínculo inválido")
        refreshIfNeeded(force = false)
        val keys = ensureKeys(meta.id)
        http.confirmLink(meta.relayUrl, requireAccess(), rawCode, NaClBox.encodeKey(keys.publicKey))
        linkFlow.value = null
        refreshDeviceList()
    }

    suspend fun beginAppLink(name: String): LinkInfo {
        val meta = requireMeta()
        val trimmed = name.trim()
        if (trimmed.length !in 1..64) {
            throw IllegalArgumentException("o nome da IDE precisa ter de 1 a 64 caracteres")
        }
        refreshIfNeeded(force = false)
        val keys = ensureKeys(meta.id)
        val info = http.createLink(meta.relayUrl, requireAccess(), "app", trimmed, NaClBox.encodeKey(keys.publicKey))
        linkFlow.value = info
        linkJob?.cancel()
        val deadline = clock() + 10 * 60_000L
        linkJob = scope.launch {
            while (clock() < deadline) {
                delay(2_000)
                val access = session.accessToken ?: break
                val cur = runCatching { http.getLink(meta.relayUrl, access, info.code) }.getOrNull() ?: continue
                linkFlow.value = cur
                if (cur.status == "ready") {
                    runCatching { refreshDeviceList() }
                    break
                }
                if (cur.status == "expired") break
            }
        }
        return info
    }

    fun clearLink() {
        linkJob?.cancel()
        linkFlow.value = null
    }

    suspend fun changePassword(current: String, next: String) {
        if (next.length !in 8..200) {
            throw IllegalArgumentException("a senha nova precisa ter entre 8 e 200 caracteres")
        }
        val meta = requireMeta()
        refreshIfNeeded(force = false)
        val resp = http.changePassword(meta.relayUrl, requireAccess(), current, next)
        val expires = clock() + resp.expiresIn.coerceAtLeast(1) * 1000L
        session.adoptTokens(resp.accessToken, resp.refreshToken, expires)
        SessionSecrets.persistRotation(vault, meta.id, resp.accessToken, resp.refreshToken, expires)
        val device = currentDevice
        if (device != null && job != null) {
            connect(device)
        }
    }

    suspend fun createMember(email: String, password: String): AccountInfo {
        val meta = requireMeta()
        if (meta.role != "owner") throw RelayException("forbidden", "somente o owner cria contas", 403)
        val trimmed = email.trim()
        if (trimmed.length !in 3..254 || trimmed.any { it.isWhitespace() }) {
            throw IllegalArgumentException("e-mail ou usuário inválido")
        }
        if (password.length !in 8..200) {
            throw IllegalArgumentException("a senha precisa ter entre 8 e 200 caracteres")
        }
        refreshIfNeeded(force = false)
        return http.createMember(meta.relayUrl, requireAccess(), trimmed, password)
    }

    override suspend fun rpc(method: String, path: String, query: String, bodyJson: String?): TunnelResult {
        if (!RpcAllowlist.allowed(method, path)) throw ApiException("rota não tunelada pelo relay")
        if (!readyFlow.value) throw ApiException("túnel ainda não está pronto")
        if (!e2eFlow.value) throw ApiException("criptografia de ponta a ponta ainda não está pronta")
        if (!ideOnlineFlow.value && path != "/api/v1/approvals/resolve") throw ApiException("IDE offline")
        val eng = engine ?: throw ApiException("túnel fechado")
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<TunnelResult>()
        pending[id] = deferred
        val frame = try {
            eng.sealRpc(id, method, path, query, bodyJson)
        } catch (e: Exception) {
            pending.remove(id)
            throw ApiException(safeMessage(e))
        }
        val sock = socket
        if (sock == null || !sock.send(frame)) {
            pending.remove(id)
            throw ApiException("túnel fechado")
        }
        val timeout = if (path == "/api/v1/approvals/resolve") 180_000L else 35_000L
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            if (path == "/api/v1/approvals/resolve") {
                throw ApiException("a IDE não respondeu; a aprovação pode ficar na fila por até 3 minutos")
            }
            throw ApiException("a IDE não respondeu; confira as lanes antes de enviar de novo")
        }
    }

    private suspend fun loop(device: DeviceInfo) {
        var attempt = 0
        try {
            while (running && currentDevice == device.id) {
                try {
                    onConn(if (attempt == 0) ConnState.Connecting else ConnState.Reconnecting(attempt))
                    runSession(device)
                    if (!running || currentDevice != device.id) break
                    if (fatalDevice) {
                        errorFlow.value = errorFlow.value ?: "não foi possível usar esta IDE"
                        break
                    }
                    if (fatalAuth) {
                        val ok = runCatching { refreshIfNeeded(force = true) }.isSuccess
                        fatalAuth = false
                        if (!ok) {
                            expireSession()
                            break
                        }
                    }
                    val refreshDue = SessionSecrets.shouldRefresh(
                        session.accessExpiresAt,
                        clock(),
                        session.accessToken != null,
                    )
                    if (refreshDue) {
                        attempt = 0
                        continue
                    }
                    attempt++
                    onConn(ConnState.Reconnecting(attempt))
                    delay(Backoff.delayMs(attempt))
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    warn("canal remoto caiu: ${e.javaClass.simpleName}")
                    readyFlow.value = false
                    failPending(ApiException("túnel reconectando"))
                    if (!running || currentDevice != device.id) break
                    if (fatalDevice) {
                        errorFlow.value = errorFlow.value ?: "não foi possível usar esta IDE"
                        break
                    }
                    if (fatalAuth || (e is RelayException && e.code == "unauthorized")) {
                        val ok = runCatching { refreshIfNeeded(force = true) }.isSuccess
                        fatalAuth = false
                        if (!ok) {
                            expireSession()
                            break
                        }
                    }
                    attempt++
                    onConn(ConnState.Reconnecting(attempt))
                    val extra = (e as? RelayException)?.retryAfterSec?.times(1000L) ?: 0L
                    delay(maxOf(Backoff.delayMs(attempt), extra))
                }
            }
        } finally {
            readyFlow.value = false
            failPending(ApiException("túnel fechado"))
            if (!running) onConn(ConnState.Disconnected)
        }
    }

    private suspend fun runSession(device: DeviceInfo) {
        val meta = activeFlow.value ?: return
        refreshIfNeeded(force = false)
        val access = session.accessToken ?: throw RelayException("unauthorized", "sessão expirada", 401)
        val keys = session.keys ?: ensureKeys(meta.id)
        val resume = vault.getSecret(meta.id, SessionSecrets.seqKey(device.id))?.toLongOrNull() ?: 0L
        val eng = RelaySessionEngine(keys, device.id, resume)
        if (device.idePublicKey.isNotBlank()) {
            // TOFU: primeira chave é pinada; divergência vira aviso, nunca adoção silenciosa.
            adoptPeerKey(eng, device.id, device.idePublicKey)
        }
        engine = eng
        readyFlow.value = false
        val url = RelayUrls.websocket(meta.relayUrl)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $access")
            .build()
        val done = CompletableDeferred<Unit>()
        lastFrameAt.set(clock())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lastFrameAt.set(clock())
                webSocket.send(eng.hello())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > MAX_FRAME) {
                    webSocket.close(1009, "")
                    return
                }
                lastFrameAt.set(clock())
                handleFrame(webSocket, eng, device, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.completeExceptionally(t)
            }
        }
        val opened = wsClient.newWebSocket(request, listener)
        socket = opened
        try {
            kotlinx.coroutines.coroutineScope {
                val watch = launch {
                    while (true) {
                        delay(10_000)
                        if (clock() - lastFrameAt.get() > 65_000) {
                            opened.close(1001, "")
                            done.completeExceptionally(java.io.IOException("silêncio no relay"))
                            return@launch
                        }
                    }
                }
                val refresh = launch {
                    val wait = (session.accessExpiresAt - 30_000L - clock()).coerceAtLeast(5_000L)
                    delay(wait)
                    opened.close(1000, "")
                    done.complete(Unit)
                }
                try {
                    done.await()
                } finally {
                    watch.cancel()
                    refresh.cancel()
                }
            }
        } finally {
            if (socket === opened) socket = null
            opened.close(1000, "")
        }
    }

    private fun handleFrame(webSocket: WebSocket, eng: RelaySessionEngine, device: DeviceInfo, text: String) {
        when (val effect = eng.onFrame(text)) {
            is FrameEffect.Send -> webSocket.send(effect.text)
            is FrameEffect.Hello -> {
                if (effect.peerPublicKey.isNotBlank() && keyRotationFlow.value == null) {
                    adoptPeerKey(eng, device.id, effect.peerPublicKey)
                }
                e2eFlow.value = effect.e2eReady && eng.peerPublic != null
                ideOnlineFlow.value = effect.online
                readyFlow.value = e2eFlow.value
                onConn(ConnState.Connected)
                errorFlow.value = null
                scope.launch { runCatching { onHello(device) }.onFailure { warn("sync inicial falhou") } }
            }
            is FrameEffect.Events -> {
                val accountId = session.accountId
                effect.applied.forEach { (seq, ev) ->
                    onEvent(ev)
                    if (accountId != null) {
                        vault.putSecret(accountId, SessionSecrets.seqKey(device.id), seq.toString())
                    }
                }
            }
            is FrameEffect.Rpc -> pending.remove(effect.id)?.complete(TunnelResult(effect.status, effect.body))
            is FrameEffect.RemoteError -> {
                val message = effect.message.ifBlank { effect.code }
                val waiter = if (effect.id.isNotBlank()) pending.remove(effect.id) else null
                if (waiter != null) waiter.completeExceptionally(ApiException(message))
                else if (message.isNotBlank()) errorFlow.value = message
                when (effect.code) {
                    "unauthorized" -> fatalAuth = true
                    "forbidden", "not_found", "key_mismatch" -> fatalDevice = true
                }
                if (fatalAuth || fatalDevice) webSocket.close(1008, "")
            }
            is FrameEffect.Peer -> {
                if (effect.publicKey.isNotBlank() && effect.role != "app") {
                    // Rotação publicada pela IDE no meio da sessão: mesma política TOFU.
                    adoptPeerKey(eng, device.id, effect.publicKey)
                }
            }
            is FrameEffect.Presence -> {
                // Presença instantânea (Spec 09/W5-02): o polling de 20s vira fallback.
                if (effect.role != "app") {
                    ideOnlineFlow.value = effect.online
                }
            }
            FrameEffect.Ignored -> Unit
        }
    }

    /**
     * Aplica a política TOFU à chave pública que o relay apresentou para a IDE:
     * pin na primeira vista, adoção silenciosa quando confere, aviso bloqueante
     * quando divergir do pin (E2E cai até a pessoa decidir).
     */
    private fun adoptPeerKey(eng: RelaySessionEngine, deviceId: String, incomingKey: String) {
        val accountId = session.accountId ?: return
        val pinned = vault.getSecret(accountId, SessionSecrets.peerKeyKey(deviceId))
        when (val trust = KeyTrustPolicy.evaluate(pinned, incomingKey)) {
            is KeyTrust.NoKey -> Unit
            is KeyTrust.FirstUse -> {
                vault.putSecret(accountId, SessionSecrets.peerKeyKey(deviceId), trust.newKey)
                pendingPeerKey = null
                eng.peerPublic = runCatching { NaClBox.parseKey(trust.newKey) }.getOrNull()
            }
            is KeyTrust.Match -> {
                pendingPeerKey = null
                eng.peerPublic = runCatching { NaClBox.parseKey(trust.key) }.getOrNull()
            }
            is KeyTrust.Rotated -> {
                pendingPeerKey = trust.incomingKey
                keyRotationFlow.value = KeyRotationNotice(
                    deviceId = deviceId,
                    deviceName = devicesFlow.value.find { it.id == deviceId }?.name.orEmpty(),
                    oldFingerprint = KeyTrustPolicy.fingerprint(trust.pinnedKey),
                    newFingerprint = KeyTrustPolicy.fingerprint(trust.incomingKey),
                    atMs = clock(),
                )
                // Falha fechada: sem chave nova adotada, a E2E fica inoperante até a decisão.
                eng.peerPublic = null
                e2eFlow.value = false
                readyFlow.value = false
                warn("chave da IDE divergiu do pin TOFU; aguardando confirmação do usuário")
            }
        }
    }

    /** Confirma a rotação: pin a nova chave, reativa a E2E e segue no mesmo socket. */
    fun acceptKeyRotation() {
        val notice = keyRotationFlow.value ?: return
        val newKey = pendingPeerKey ?: return
        val accountId = session.accountId ?: return
        vault.putSecret(accountId, SessionSecrets.peerKeyKey(notice.deviceId), newKey)
        pendingPeerKey = null
        keyRotationFlow.value = null
        val eng = engine
        if (eng != null) {
            eng.peerPublic = runCatching { NaClBox.parseKey(newKey) }.getOrNull()
            e2eFlow.value = eng.peerPublic != null
            readyFlow.value = e2eFlow.value
            if (e2eFlow.value) onConn(ConnState.Connected)
        }
    }

    /** Recusa a rotação: encerra o túnel sem adotar a chave desconhecida. */
    fun rejectKeyRotation() {
        pendingPeerKey = null
        keyRotationFlow.value = null
        scope.launch { disconnectIde() }
    }

    private suspend fun refreshIfNeeded(force: Boolean) {
        val meta = activeFlow.value ?: return
        refreshMutex.withLock {
            val refresh = session.refreshToken
            if (refresh.isNullOrBlank()) {
                throw RelayException("unauthorized", "entre na conta do relay", 401)
            }
            if (!force && !SessionSecrets.shouldRefresh(session.accessExpiresAt, clock(), session.accessToken != null)) {
                return
            }
            val resp = try {
                http.refresh(meta.relayUrl, refresh)
            } catch (e: RelayException) {
                if (e.code == "unauthorized") fatalAuth = true
                throw e
            }
            if (!resp.accessToken.startsWith("hmr_at_") || !resp.refreshToken.startsWith("hmr_rt_")) {
                throw RelayException("unauthorized", "resposta de refresh inválida", 401)
            }
            val expires = clock() + resp.expiresIn.coerceAtLeast(1) * 1000L
            // Memória primeiro: o refresh antigo não pode ser enviado de novo.
            session.adoptTokens(resp.accessToken, resp.refreshToken, expires)
            SessionSecrets.persistRotation(vault, meta.id, resp.accessToken, resp.refreshToken, expires)
        }
    }

    private fun ensureKeys(accountId: String): NaClBox.KeyPair {
        session.keys?.let { return it }
        val pair = NaClBox.generate()
        vault.putSecret(accountId, SessionSecrets.PUBLIC, NaClBox.encodeKey(pair.publicKey))
        vault.putSecret(accountId, SessionSecrets.PRIVATE, NaClBox.encodeKey(pair.privateKey))
        session.adoptKeys(pair)
        return pair
    }

    private fun expireSession() {
        val meta = activeFlow.value
        val id = session.accountId ?: meta?.id
        if (id != null) {
            vault.removeSecret(id, SessionSecrets.REFRESH)
            vault.removeSecret(id, SessionSecrets.ACCESS)
            vault.removeSecret(id, SessionSecrets.EXPIRES)
        }
        session.wipe()
        loggedInFlow.value = false
        activeFlow.value = null
        running = false
        draftFlow.value = meta
        forceLoginFlow.value = meta != null
        errorFlow.value = "sessão expirada; entre de novo"
        scope.launch { onTunnelClosed() }
    }

    private suspend fun stopAndJoin() {
        running = false
        val previous = job
        pollJob?.cancel()
        pollJob = null
        socket?.cancel()
        socket = null
        engine = null
        pendingPeerKey = null
        keyRotationFlow.value = null
        previous?.cancel()
        previous?.join()
        job = null
        failPending(ApiException("túnel fechado"))
    }

    private fun failPending(err: Exception) {
        val all = pending.values.toList()
        pending.clear()
        all.forEach { if (!it.isCompleted) it.completeExceptionally(err) }
    }

    private fun requireMeta(): AccountMeta =
        activeFlow.value ?: throw IllegalStateException("entre na conta do relay")

    private fun requireAccess(): String =
        session.accessToken ?: throw RelayException("unauthorized", "sessão expirada", 401)

    private fun safeMessage(error: Throwable): String =
        Redact.clean(error.message?.takeIf { it.isNotBlank() } ?: "falha no relay")

    private fun warn(message: String) {
        log.warn(Redact.clean(message))
    }

    companion object {
        private const val MAX_FRAME = 12 * 1024 * 1024
    }
}
