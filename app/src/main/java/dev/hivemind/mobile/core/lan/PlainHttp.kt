package dev.hivemind.mobile.core.lan

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

data class WireRequest(
    val method: String,
    val url: String,
    val body: ByteArray? = null,
    val accept: String = "application/json",
    val readTimeoutMs: Int = 60_000,
    val connectTimeoutMs: Int = 5_000,
)

data class WireResponse(val status: Int, val body: String)

interface WireStream : Closeable {
    val status: Int
    fun bodyText(): String
    fun lines(onLine: (String) -> Unit)
}

interface HttpTransport {
    fun execute(request: WireRequest): WireResponse
    fun open(request: WireRequest): WireStream
}

/**
 * HTTP/1.1 mínimo para a LAN. O bridge exige corpo em GET; o OkHttp recusa
 * esse método, então o quadro sai direto no socket. Sem TLS e sem redirecionar.
 */
class PlainHttp : HttpTransport {
    override fun execute(request: WireRequest): WireResponse {
        open(request).use { stream ->
            return WireResponse(stream.status, stream.bodyText())
        }
    }

    override fun open(request: WireRequest): WireStream {
        val target = parseHttp(request.url)
        val method = request.method.trim().uppercase()
        if (method != "GET" && method != "POST") throw IOException("método inválido")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(target.host, target.port), request.connectTimeoutMs)
            socket.soTimeout = request.readTimeoutMs
            val out = socket.getOutputStream()
            val header = buildString {
                append(method).append(' ').append(target.path).append(" HTTP/1.1\r\n")
                append("Host: ").append(target.hostHeader).append("\r\n")
                append("Accept: ").append(request.accept).append("\r\n")
                append("Connection: close\r\n")
                val body = request.body
                if (body != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ").append(body.size).append("\r\n")
                }
                append("\r\n")
            }
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            request.body?.let { out.write(it) }
            out.flush()
            val input = socket.getInputStream()
            val headerBytes = readHeaderBlock(input)
            val (status, headers) = parseHeaders(headerBytes)
            val body = bodyStream(input, headers)
            return SocketStream(status, body, socket)
        } catch (e: IOException) {
            socket.close()
            throw e
        } catch (e: RuntimeException) {
            socket.close()
            throw e
        }
    }

    private class SocketStream(
        override val status: Int,
        private val body: InputStream,
        private val socket: Socket,
    ) : WireStream {
        private var drained = false

        override fun bodyText(): String {
            if (drained) return ""
            drained = true
            return readCapped(body, MAX_BODY).toString(Charsets.UTF_8)
        }

        override fun lines(onLine: (String) -> Unit) {
            if (drained) return
            drained = true
            body.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            }
        }

        override fun close() {
            runCatching { body.close() }
            runCatching { socket.close() }
        }
    }

    private data class Target(val host: String, val port: Int, val hostHeader: String, val path: String)

    private fun parseHttp(raw: String): Target {
        if (raw.contains('\r') || raw.contains('\n')) throw IOException("URL inválida")
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            throw IOException("URL inválida")
        }
        if (!uri.scheme.equals("http", ignoreCase = true)) throw IOException("a URL do pareamento precisa ser HTTP")
        if (!uri.userInfo.isNullOrBlank()) throw IOException("URL inválida")
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || host.contains(' ')) throw IOException("URL inválida")
        // Cleartext só para LAN privada/Tailscale (Spec 10): host público exige HTTPS.
        if (!CleartextGuard.isPrivateHost(host)) {
            throw IOException("host público em HTTP é proibido: use HTTPS para $host")
        }
        val port = if (uri.port == -1) 80 else uri.port
        val path = buildString {
            val rawPath = uri.rawPath
            append(if (rawPath.isNullOrBlank()) "/" else rawPath)
            if (!uri.rawQuery.isNullOrEmpty()) {
                append('?')
                append(uri.rawQuery)
            }
        }
        if (path.contains(' ') || path.contains('\r') || path.contains('\n')) throw IOException("URL inválida")
        val hostHeader = if (':' in host) "[$host]:$port" else if (port == 80) host else "$host:$port"
        return Target(host, port, hostHeader, path)
    }

    private fun readHeaderBlock(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val tail = IntArray(4) { -1 }
        while (out.size() < MAX_HEADER) {
            val b = input.read()
            if (b < 0) throw IOException("cabeçalho incompleto")
            out.write(b)
            tail[0] = tail[1]
            tail[1] = tail[2]
            tail[2] = tail[3]
            tail[3] = b
            if (tail[0] == 13 && tail[1] == 10 && tail[2] == 13 && tail[3] == 10) return out.toByteArray()
        }
        throw IOException("cabeçalho grande demais")
    }

    private fun parseHeaders(bytes: ByteArray): Pair<Int, Map<String, String>> {
        val text = bytes.toString(Charsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IOException("status inválido")
        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return status to headers
    }

    private fun bodyStream(input: InputStream, headers: Map<String, String>): InputStream {
        val encoding = headers["transfer-encoding"].orEmpty().lowercase()
        if ("chunked" in encoding) return ChunkedInputStream(input, MAX_BODY)
        val length = headers["content-length"]?.toIntOrNull()
        if (length != null) {
            if (length < 0 || length > MAX_BODY) throw IOException("resposta grande demais")
            return LimitedInputStream(input, length)
        }
        return CappedInputStream(input, MAX_BODY)
    }

    private class LimitedInputStream(private val raw: InputStream, private var left: Int) : InputStream() {
        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n < 0) -1 else buf[0].toInt() and 255
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = raw.read(b, off, minOf(len, left))
            if (n < 0) {
                if (left > 0) throw IOException("resposta incompleta")
                return -1
            }
            left -= n
            return n
        }
    }

    private class CappedInputStream(private val raw: InputStream, private val max: Int) : InputStream() {
        private var total = 0

        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n < 0) -1 else buf[0].toInt() and 255
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = raw.read(b, off, len)
            if (n > 0) {
                total += n
                if (total > max) throw IOException("resposta grande demais")
            }
            return n
        }
    }

    private class ChunkedInputStream(private val raw: InputStream, private val maxTotal: Int) : InputStream() {
        private var remaining = 0
        private var done = false
        private var total = 0

        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n < 0) -1 else buf[0].toInt() and 255
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (done) return -1
            if (len == 0) return 0
            if (remaining == 0 && !nextChunk()) return -1
            val n = raw.read(b, off, minOf(len, remaining))
            if (n < 0) throw IOException("chunk incompleto")
            remaining -= n
            total += n
            if (total > maxTotal) throw IOException("resposta grande demais")
            if (remaining == 0) readCrlf()
            return n
        }

        private fun nextChunk(): Boolean {
            val line = readLine()
            val size = line.substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("chunk inválido")
            if (size == 0L) {
                while (readLine().isNotEmpty()) Unit
                done = true
                return false
            }
            if (size > maxTotal) throw IOException("chunk grande demais")
            remaining = size.toInt()
            return true
        }

        private fun readCrlf() {
            val a = raw.read()
            if (a == '\n'.code) return
            if (a == '\r'.code && raw.read() == '\n'.code) return
            throw IOException("chunk inválido")
        }

        private fun readLine(): String {
            val out = ByteArrayOutputStream()
            while (out.size() < 8192) {
                val b = raw.read()
                if (b < 0) throw IOException("chunk incompleto")
                if (b == '\n'.code) {
                    val bytes = out.toByteArray()
                    val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                    return bytes.copyOf(end).toString(Charsets.ISO_8859_1)
                }
                out.write(b)
            }
            throw IOException("chunk inválido")
        }
    }

    companion object {
        private const val MAX_HEADER = 64 * 1024
        private const val MAX_BODY = 32 * 1024 * 1024

        private fun readCapped(input: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (out.size() + n > max) throw IOException("resposta grande demais")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
