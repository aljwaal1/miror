package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SsdpScanner {
    suspend fun scan(timeoutMs: Int = 1800): List<CastDevice> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, CastDevice>()
        val request = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n\r\n"

        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(request.toByteArray(), request.length, address, 1900)
                socket.send(packet)

                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < timeoutMs) {
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(response) }.onSuccess {
                        val text = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress ?: return@onSuccess
                        val type = detectType(text)
                        val name = extractHeader(text, "SERVER")
                            ?: extractHeader(text, "USN")
                            ?: "جهاز UPnP / DLNA"
                        val services = listOfNotNull(
                            extractHeader(text, "ST"),
                            extractHeader(text, "LOCATION")
                        )
                        results[ip] = CastDevice(
                            name = name.take(80),
                            ipAddress = ip,
                            type = type,
                            services = services,
                            isReachable = true
                        )
                    }
                }
            }
        }
        results.values.toList()
    }

    private fun extractHeader(text: String, key: String): String? {
        return text.lineSequence()
            .firstOrNull { it.startsWith("$key:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun detectType(text: String): DeviceType {
        val lower = text.lowercase()
        return when {
            "dial" in lower || "chromecast" in lower -> DeviceType.CHROMECAST
            "mediarenderer" in lower || "dlna" in lower -> DeviceType.DLNA
            "upnp" in lower -> DeviceType.UPNP
            "anyview" in lower || "hisense" in lower || "miracast" in lower -> DeviceType.ANYVIEW
            else -> DeviceType.UNKNOWN
        }
    }
}
