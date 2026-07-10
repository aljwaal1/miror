package com.explapp.mirror.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

class LocalMediaServer(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private var sharedMedia: SharedMedia? = null

    @Synchronized
    fun start(mediaUri: Uri, mimeType: String): LocalMediaServerResult {
        stop()

        val socket = ServerSocket(0)
        val port = socket.localPort
        sharedMedia = SharedMedia(
            uri = mediaUri,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            size = querySize(mediaUri)
        )
        serverSocket = socket

        thread(start = true, isDaemon = true, name = "ExplAppMirrorMediaServer") {
            serve(socket)
        }

        val host = findLocalIpv4Address() ?: "127.0.0.1"
        return LocalMediaServerResult(
            host = host,
            port = port,
            url = "http://$host:$port/media",
            mimeType = sharedMedia?.mimeType ?: "application/octet-stream",
            size = sharedMedia?.size ?: -1L
        )
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        sharedMedia = null
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(start = true, isDaemon = true, name = "ExplAppMirrorMediaClient") {
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            val media = sharedMedia ?: return
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val headers = mutableMapOf<String, String>()

            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val key = line.substringBefore(":").trim().lowercase()
                val value = line.substringAfter(":", "").trim()
                if (key.isNotBlank()) headers[key] = value
            }

            val method = requestLine.substringBefore(" ").uppercase()
            val output = socket.getOutputStream()

            if (method != "GET" && method != "HEAD") {
                output.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            val range = parseRange(headers["range"], media.size)
            val partial = range != null
            val start = range?.first ?: 0L
            val end = range?.second ?: ((media.size - 1).coerceAtLeast(0L))
            val contentLength = if (media.size > 0) (end - start + 1).coerceAtLeast(0L) else -1L

            val responseHeaders = buildString {
                append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${media.mimeType}\r\n")
                append("Accept-Ranges: bytes\r\n")
                if (partial && media.size > 0) append("Content-Range: bytes $start-$end/${media.size}\r\n")
                if (contentLength >= 0) append("Content-Length: $contentLength\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(responseHeaders.toByteArray())

            if (method == "GET") {
                context.contentResolver.openInputStream(media.uri)?.use { input ->
                    skipFully(input, start)
                    if (contentLength >= 0) {
                        copyLimited(input, output, contentLength)
                    } else {
                        input.copyTo(output)
                    }
                }
            }
            output.flush()
        }
    }

    private fun parseRange(rangeHeader: String?, size: Long): Pair<Long, Long>? {
        if (rangeHeader.isNullOrBlank() || size <= 0) return null
        if (!rangeHeader.startsWith("bytes=", ignoreCase = true)) return null
        val value = rangeHeader.substringAfter("=").substringBefore(",").trim()
        val startText = value.substringBefore("-").trim()
        val endText = value.substringAfter("-", "").trim()

        val start = startText.toLongOrNull() ?: return null
        val requestedEnd = endText.toLongOrNull() ?: (size - 1)
        val end = requestedEnd.coerceAtMost(size - 1)
        if (start < 0 || start > end) return null
        return start to end
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun querySize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun findLocalIpv4Address(): String? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        return interfaces.asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }
}

data class LocalMediaServerResult(
    val host: String,
    val port: Int,
    val url: String,
    val mimeType: String,
    val size: Long
)

private data class SharedMedia(
    val uri: Uri,
    val mimeType: String,
    val size: Long
)
