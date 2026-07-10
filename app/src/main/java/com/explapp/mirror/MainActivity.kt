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
    private lateinit var queueView: TextView
    private var selectedDevice: CastDevice? = null
    private val queue = mutableListOf<Uri>()
    private var queueIndex = -1

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addToQueue(uris)
    }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addToQueue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = NetworkScanner(this)
        mediaSender = MediaSender(this)
        setContentView(createMainView())
    }

    override fun onDestroy() {
        mediaSender.stopLocalServer()
        super.onDestroy()
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
            text = "اكتشاف الشاشات وإرسال صور وفيديوهات متعددة"
            textSize = 16f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        }

        val scanButton = Button(this).apply {
            text = "بحث عن الأجهزة"
            setOnClickListener { startScan() }
        }

        queueView = TextView(this).apply {
            text = "قائمة التشغيل: فارغة"
            textSize = 14f
            setTextColor(0xFFCBD5E1.toInt())
            setPadding(0, 18, 0, 8)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(Button(this).apply {
            text = "السابق"
            setOnClickListener { playPrevious() }
        })
        controls.addView(Button(this).apply {
            text = "تشغيل"
            setOnClickListener { playCurrent() }
        })
        controls.addView(Button(this).apply {
            text = "التالي"
            setOnClickListener { playNext() }
        })
        controls.addView(Button(this).apply {
            text = "إيقاف"
            setOnClickListener { stopPlayback() }
        })

        status = TextView(this).apply {
            text = "الحالة: جاهز للبحث"
            textSize = 15f
            setTextColor(0xFF5EEAD4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 18)
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scanButton)
        root.addView(queueView)
        root.addView(controls)
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

            val selectButton = Button(this).apply {
                text = "اتصال واختيار الجهاز"
                setOnClickListener {
                    selectedDevice = device
                    status.text = "تم اختيار ${device.name}"
                }
            }

            val testButton = Button(this).apply {
                text = "اختبار الاتصال"
                setOnClickListener { testDevice(device) }
            }

            val imageButton = Button(this).apply {
                text = "اختيار عدة صور"
                setOnClickListener {
                    selectedDevice = device
                    imagePicker.launch(arrayOf("image/*"))
                }
            }

            val videoButton = Button(this).apply {
                text = "اختيار عدة فيديوهات"
                setOnClickListener {
                    selectedDevice = device
                    videoPicker.launch(arrayOf("video/*"))
                }
            }

            container.addView(item)
            container.addView(selectButton)
            container.addView(testButton)
            container.addView(imageButton)
            container.addView(videoButton)

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

    private fun addToQueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        queue.addAll(uris)
        if (queueIndex < 0) queueIndex = 0
        updateQueueStatus()
        playCurrent()
    }

    private fun playCurrent() {
        val device = selectedDevice
        if (device == null) {
            status.text = "اختر شاشة أولًا."
            return
        }
        if (queueIndex !in queue.indices) {
            status.text = "اختر صورًا أو فيديوهات أولًا."
            return
        }
        val uri = queue[queueIndex]
        status.text = "جاري إرسال الملف ${queueIndex + 1} من ${queue.size} إلى ${device.name}..."
        lifecycleScope.launch {
            val result = mediaSender.prepareSend(device, uri)
            status.text = result.arabicSummary
            updateQueueStatus()
        }
    }

    private fun playNext() {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex + 1) % queue.size
        playCurrent()
    }

    private fun playPrevious() {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex - 1 + queue.size) % queue.size
        playCurrent()
    }

    private fun stopPlayback() {
        val device = selectedDevice ?: return
        lifecycleScope.launch {
            status.text = mediaSender.stop(device)
        }
    }

    private fun updateQueueStatus() {
        queueView.text = if (queue.isEmpty()) {
            "قائمة التشغيل: فارغة"
        } else {
            "قائمة التشغيل: ${queue.size} ملفات — الحالي ${queueIndex + 1}"
        }
    }
}
