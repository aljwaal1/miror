package com.explapp.mirror.core

import android.content.Context
import android.net.wifi.WifiManager
import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress

class NetworkScanner(private val context: Context) {
    private val ssdpScanner = SsdpScanner()

    suspend fun scanLocalNetwork(): List<CastDevice> = withContext(Dispatchers.IO) {
        val discovered = linkedMapOf<String, CastDevice>()

        ssdpScanner.scan().forEach { device ->
            discovered[device.ipAddress] = device
        }

        scanByPing().forEach { device ->
            val current = discovered[device.ipAddress]
            discovered[device.ipAddress] = current ?: device
        }

        discovered.values.sortedWith(compareBy<CastDevice> { it.type == DeviceType.UNKNOWN }.thenBy { it.ipAddress })
    }

    private suspend fun scanByPing(): List<CastDevice> = withContext(Dispatchers.IO) {
        val prefix = getWifiPrefix() ?: return@withContext emptyList()
        coroutineScope {
            (1..254).map { last ->
                async {
                    val ip = "$prefix.$last"
                    val reachable = runCatching {
                        InetAddress.getByName(ip).isReachable(320)
                    }.getOrDefault(false)

                    if (reachable) {
                        CastDevice(
                            name = guessName(ip),
                            ipAddress = ip,
                            type = guessType(ip),
                            isReachable = true,
                            services = listOf("Ping")
                        )
                    } else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun getWifiPrefix(): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifi?.connectionInfo?.ipAddress ?: return null
        if (ipInt == 0) return null
        val a = ipInt and 0xff
        val b = ipInt shr 8 and 0xff
        val c = ipInt shr 16 and 0xff
        return "$a.$b.$c"
    }

    private fun guessName(ip: String): String = "جهاز على الشبكة $ip"

    private fun guessType(ip: String): DeviceType = when {
        ip.endsWith(".1") -> DeviceType.ROUTER
        else -> DeviceType.UNKNOWN
    }
}
