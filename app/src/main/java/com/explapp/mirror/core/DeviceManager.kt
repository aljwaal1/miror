package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice

class DeviceManager {
    private val devices = linkedMapOf<String, CastDevice>()

    fun clear() {
        devices.clear()
    }

    fun addDevice(device: CastDevice) {
        devices[device.ipAddress] = device
    }

    fun addDevices(items: List<CastDevice>) {
        items.forEach { addDevice(it) }
    }

    fun getDevices(): List<CastDevice> {
        return devices.values.toList()
    }
}
