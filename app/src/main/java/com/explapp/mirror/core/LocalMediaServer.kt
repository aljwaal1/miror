package com.explapp.mirror.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
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

            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
            }

            val method = requestLine.substringBefore(" ").uppercase()
            val output = socket.getOutputStream()

            if (method != "GET" && method != "HEAD") {
                output.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${media.mimeType}\r\n")
                if (media.size > 0) append("Content-Length: ${media.size}\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray())

            if (method == "GET") {
                context.contentResolver.openInputStream(media.uri)?.use { input ->
                    input.copyTo(output)
                }
            }
            output.flush()
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
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
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
