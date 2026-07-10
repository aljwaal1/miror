package com.explapp.mirror

import android.content.Intent
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
    private lateinit var selectedDeviceView: TextView
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

        root.addView(TextView(this).apply {
            text = "ExplApp Mirror"
            textSize = 28f
            setTextColor(0xFFF8FAFC.toInt())
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "اكتشاف الشاشات وإرسال صور وفيديوهات متعددة"
            textSize = 16f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        })

        root.addView(Button(this).apply {
            text = "بحث عن الأجهزة"
            setOnClickListener { startScan() }
        })

        selectedDeviceView = TextView(this).apply {
            text = "الجهاز المحدد: لا يوجد"
            textSize = 14f
            setTextColor(0xFF93C5FD.toInt())
            setPadding(0, 16, 0, 6)
        }

        queueView = TextView(this).apply {
            text = "قائمة التشغيل: فارغة"
            textSize = 14f
            setTextColor(0xFFCBD5E1.toInt())
            setPadding(0, 8, 0, 8)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row1.addView(controlButton("السابق") { playPrevious() })
        row1.addView(controlButton("تشغيل") { playCurrent() })
        row1.addView(controlButton("التالي") { playNext() })

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row2.addView(controlButton("إيقاف مؤقت") { pausePlayback() })
        row2.addView(controlButton("استئناف") { resumePlayback() })
        row2.addView(controlButton("إيقاف") { stopPlayback() })
        row2.addView(controlButton("مسح القائمة") { clearQueue() })

        status = TextView(this).apply {
            text = "الحالة: جاهز للبحث"
            textSize = 15f
            setTextColor(0xFF5EEAD4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 18)
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(selectedDeviceView)
        root.addView(queueView)
        root.addView(row1)
        root.addView(row2)
        root.addView(status)
        root.addView(list)
        return ScrollView(this).apply { addView(root) }
    }

    private fun controlButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
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

            container.addView(TextView(this).apply {
                text = "${device.name}\nIP: ${device.ipAddress}\nالنوع: ${device.displayType}\nالخدمات: ${device.services.joinToString()}"
                textSize = 15f
                setTextColor(0xFFF8FAFC.toInt())
            })

            container.addView(Button(this).apply {
                text = "اتصال واختيار الجهاز"
                setOnClickListener {
                    selectedDevice = device
                    selectedDeviceView.text = "الجهاز المحدد: ${device.name} (${device.ipAddress})"
                    status.text = "تم اختيار ${device.name}"
                }
            })

            container.addView(Button(this).apply {
                text = "اختبار الاتصال"
                setOnClickListener { testDevice(device) }
            })

            container.addView(Button(this).apply {
                text = "اختيار عدة صور"
                setOnClickListener {
                    selectDevice(device)
                    imagePicker.launch(arrayOf("image/*"))
                }
            })

            container.addView(Button(this).apply {
                text = "اختيار عدة فيديوهات"
                setOnClickListener {
                    selectDevice(device)
                    videoPicker.launch(arrayOf("video/*"))
                }
            })

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            list.addView(container, params)
        }
    }

    private fun selectDevice(device: CastDevice) {
        selectedDevice = device
        selectedDeviceView.text = "الجهاز المحدد: ${device.name} (${device.ipAddress})"
    }

    private fun testDevice(device: CastDevice) {
        status.text = "جاري اختبار الاتصال مع ${device.ipAddress}..."
        lifecycleScope.launch {
            status.text = connectionTester.test(device).arabicSummary
        }
    }

    private fun addToQueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (uri !in queue) queue.add(uri)
        }
        if (queueIndex < 0 && queue.isNotEmpty()) queueIndex = 0
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

    private fun pausePlayback() {
        val device = selectedDevice ?: return
        lifecycleScope.launch { status.text = mediaSender.pause(device) }
    }

    private fun resumePlayback() {
        val device = selectedDevice ?: return
        lifecycleScope.launch { status.text = mediaSender.resume(device) }
    }

    private fun stopPlayback() {
        val device = selectedDevice ?: return
        lifecycleScope.launch { status.text = mediaSender.stop(device) }
    }

    private fun clearQueue() {
        queue.clear()
        queueIndex = -1
        mediaSender.stopLocalServer()
        updateQueueStatus()
        status.text = "تم مسح قائمة التشغيل."
    }

    private fun updateQueueStatus() {
        queueView.text = if (queue.isEmpty()) {
            "قائمة التشغيل: فارغة"
        } else {
            "قائمة التشغيل: ${queue.size} ملفات — الحالي ${queueIndex + 1}"
        }
    }
}
