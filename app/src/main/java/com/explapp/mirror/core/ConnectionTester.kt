package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionTester {
    suspend fun test(device: CastDevice): ConnectionResult = withContext(Dispatchers.IO) {
        val pingOk = runCatching {
            InetAddress.getByName(device.ipAddress).isReachable(700)
        }.getOrDefault(false)

        val openPorts = mutableListOf<Int>()
        val ports = listOf(80, 8008, 8009, 1900, 7000, 7100, 8080, 49152)
        ports.forEach { port ->
            if (isPortOpen(device.ipAddress, port)) openPorts.add(port)
        }

        ConnectionResult(
            ipAddress = device.ipAddress,
            pingOk = pingOk,
            openPorts = openPorts,
            canTryMedia = openPorts.any { it in listOf(80, 8008, 8080, 49152) }
        )
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 450)
                true
            }
        }.getOrDefault(false)
    }
}

data class ConnectionResult(
    val ipAddress: String,
    val pingOk: Boolean,
    val openPorts: List<Int>,
    val canTryMedia: Boolean
) {
    val arabicSummary: String
        get() = buildString {
            append(if (pingOk) "الجهاز يستجيب" else "لا توجد استجابة Ping")
            append("\n")
            append("المنافذ المفتوحة: ")
            append(if (openPorts.isEmpty()) "لا يوجد" else openPorts.joinToString())
            append("\n")
            append(if (canTryMedia) "يمكن تجربة إرسال وسائط لاحقًا" else "يحتاج بروتوكول خاص أو فحص أعمق")
        }
}
