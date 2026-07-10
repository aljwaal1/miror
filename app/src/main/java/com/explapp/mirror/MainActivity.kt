package com.explapp.mirror

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.explapp.mirror.core.ConnectionTester
import com.explapp.mirror.core.DeviceManager
import com.explapp.mirror.core.MediaSender
import com.explapp.mirror.core.NetworkScanner
import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val deviceManager = DeviceManager()
    private val connectionTester = ConnectionTester()
    private lateinit var mediaSender: MediaSender
    private lateinit var scanner: NetworkScanner
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private var selectedDevice: CastDevice? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePickedMedia(it) }
    }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePickedMedia(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = NetworkScanner(this)
        mediaSender = MediaSender(this)
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
            text = "اكتشاف الشاشات وإرسال الصور والفيديوهات"
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

            val imageButton = Button(this).apply {
                text = "اختيار صورة وتشغيل"
                setOnClickListener {
                    selectedDevice = device
                    imagePicker.launch("image/*")
                }
            }

            val videoButton = Button(this).apply {
                text = "اختيار فيديو وتشغيل"
                setOnClickListener {
                    selectedDevice = device
                    videoPicker.launch("video/*")
                }
            }

            val pauseButton = Button(this).apply {
                text = "إيقاف مؤقت"
                setOnClickListener { controlDevice(device, ControlAction.PAUSE) }
            }

            val resumeButton = Button(this).apply {
                text = "استئناف"
                setOnClickListener { controlDevice(device, ControlAction.RESUME) }
            }

            val stopButton = Button(this).apply {
                text = "إيقاف"
                setOnClickListener { controlDevice(device, ControlAction.STOP) }
            }

            container.addView(item)
            container.addView(testButton)
            container.addView(imageButton)
            container.addView(videoButton)
            container.addView(pauseButton)
            container.addView(resumeButton)
            container.addView(stopButton)

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

    private fun handlePickedMedia(uri: Uri) {
        val device = selectedDevice
        if (device == null) {
            status.text = "اختر جهازًا أولًا قبل اختيار الملف."
            return
        }

        status.text = "جاري تجهيز الملف للإرسال إلى ${device.ipAddress}..."
        lifecycleScope.launch {
            val result = mediaSender.prepareSend(device, uri)
            status.text = result.arabicSummary
        }
    }

    private fun controlDevice(device: CastDevice, action: ControlAction) {
        status.text = "جاري إرسال أمر التحكم إلى ${device.ipAddress}..."
        lifecycleScope.launch {
            val message = when (action) {
                ControlAction.PAUSE -> mediaSender.pause(device)
                ControlAction.RESUME -> mediaSender.resume(device)
                ControlAction.STOP -> {
                    val result = mediaSender.stop(device)
                    mediaSender.stopLocalServer()
                    result
                }
            }
            status.text = message
        }
    }
}

private enum class ControlAction {
    PAUSE,
    RESUME,
    STOP
}
