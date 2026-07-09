package com.explapp.mirror

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.explapp.mirror.core.ConnectionTester
import com.explapp.mirror.core.DeviceManager
import com.explapp.mirror.core.NetworkScanner
import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val deviceManager = DeviceManager()
    private val connectionTester = ConnectionTester()
    private lateinit var scanner: NetworkScanner
    private lateinit var status: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = NetworkScanner(this)
        setContentView(createMainView())
    }

    private fun createMainView(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(0xFF0F172A.toInt())
        }

        val title = TextView(this).apply {
            text = "ExplApp Mirror"
            textSize = 28f
            setTextColor(0xFFF8FAFC.toInt())
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "اكتشاف الشاشات والأجهزة على نفس الشبكة"
            textSize = 16f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }

        val scanButton = Button(this).apply {
            text = "بحث عن الأجهزة"
        }

        status = TextView(this).apply {
            text = "الحالة: جاهز للبحث"
            textSize = 15f
            setTextColor(0xFF5EEAD4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 18)
        }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scanButton.setOnClickListener { startScan() }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scanButton)
        root.addView(status)
        root.addView(list)
        return ScrollView(this).apply { addView(root) }
    }

    private fun startScan() {
        status.text = "جاري فحص الشبكة المحلية..."
        list.removeAllViews()
        lifecycleScope.launch {
            val devices = scanner.scanLocalNetwork()
            deviceManager.clear()
            deviceManager.addDevices(devices)
            renderDevices(deviceManager.getDevices())
        }
    }

    private fun renderDevices(devices: List<CastDevice>) {
        list.removeAllViews()
        status.text = if (devices.isEmpty()) {
            "لم يتم العثور على أجهزة. تأكد أن الهاتف والشاشة على نفس الشبكة."
        } else {
            "تم العثور على ${devices.size} جهاز"
        }

        devices.forEach { device ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF172033.toInt())
                setPadding(24, 20, 24, 20)
            }

            val item = TextView(this).apply {
                text = "${device.name}\nIP: ${device.ipAddress}\nالنوع: ${device.displayType}\nالخدمات: ${device.services.joinToString()}"
                textSize = 15f
                setTextColor(0xFFF8FAFC.toInt())
            }

            val testButton = Button(this).apply {
                text = "اختبار الاتصال"
                setOnClickListener { testDevice(device) }
            }

            container.addView(item)
            container.addView(testButton)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            list.addView(container, params)
        }
    }

    private fun testDevice(device: CastDevice) {
        status.text = "جاري اختبار الاتصال مع ${device.ipAddress}..."
        lifecycleScope.launch {
            val result = connectionTester.test(device)
            status.text = result.arabicSummary
        }
    }
}
