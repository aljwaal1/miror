package com.explapp.mirror.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.util.concurrent.atomic.AtomicBoolean

class LocalMediaServer(
    private val context: Context,
    private val port: Int = 8787
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var currentUri: Uri? = null
    private var currentMime: String = "application/octet-stream"

    fun setMedia(uri: Uri) {
        currentUri = uri
        currentMime = context.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(uri.lastPathSegment)
            ?: "application/octet-stream"
    }

    fun start(): Boolean {
        if (running.get()) return true
        return runCatching {
            serverSocket = ServerSocket(port)
            running.set(true)
            serverJob = scope.launch {
                while (running.get()) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    fun mediaUrl(localIp: String): String = "http://$localIp:$port/media"

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                val index = line.indexOf(':')
                if (index > 0) headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
            }

            val method = requestLine.substringBefore(' ')
            val uri = currentUri
            if (uri == null || (method != "GET" && method != "HEAD")) {
                writeSimple(client, 404, "Not Found")
                return
            }

            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (descriptor == null) {
                writeSimple(client, 404, "Not Found")
                return
            }

            descriptor.use { afd ->
                val total = afd.length
                val rangeHeader = headers["range"]
                val range = parseRange(rangeHeader, total)
                val start = range.first
                val end = range.second
                val length = if (total >= 0) end - start + 1 else -1
                val partial = rangeHeader != null && total >= 0

                val out = BufferedOutputStream(client.getOutputStream())
                val status = if (partial) "206 Partial Content" else "200 OK"
                out.write("HTTP/1.1 $status\r\n".toByteArray())
                out.write("Content-Type: $currentMime\r\n".toByteArray())
                out.write("Accept-Ranges: bytes\r\n".toByteArray())
                if (length >= 0) out.write("Content-Length: $length\r\n".toByteArray())
                if (partial) out.write("Content-Range: bytes $start-$end/$total\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())

                if (method == "HEAD") {
                    out.flush()
                    return
                }

                val raw = context.contentResolver.openInputStream(uri) ?: return
                BufferedInputStream(raw).use { media ->
                    var skipped = 0L
                    while (skipped < start) {
                        val value = media.skip(start - skipped)
                        if (value <= 0) break
                        skipped += value
                    }

                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining != 0L) {
                        val max = if (remaining > 0) minOf(buffer.size.toLong(), remaining).toInt() else buffer.size
                        val read = media.read(buffer, 0, max)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (remaining > 0) remaining -= read
                    }
                    out.flush()
                }
            }
        }
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long> {
        if (header == null || total <= 0 || !header.startsWith("bytes=")) return 0L to if (total > 0) total - 1 else 0L
        val value = header.removePrefix("bytes=").substringBefore(',')
        val start = value.substringBefore('-').toLongOrNull() ?: 0L
        val end = value.substringAfter('-', "").toLongOrNull() ?: (total - 1)
        return start.coerceIn(0, total - 1) to end.coerceIn(start, total - 1)
    }

    private fun writeSimple(socket: Socket, code: Int, message: String) {
        val body = message.toByteArray()
        socket.getOutputStream().use { out ->
            out.write("HTTP/1.1 $code $message\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
        }
    }
}
