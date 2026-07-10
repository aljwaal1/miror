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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.explapp.mirror.core.ConnectionTester
import com.explapp.mirror.core.DeviceManager
import com.explapp.mirror.core.MediaSender
import com.explapp.mirror.core.MirroringLauncher
import com.explapp.mirror.core.NetworkScanner
import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val deviceManager = DeviceManager()
    private val connectionTester = ConnectionTester()
    private lateinit var mediaSender: MediaSender
    private lateinit var mirroringLauncher: MirroringLauncher
    private lateinit var scanner: NetworkScanner
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var queueView: TextView
    private lateinit var selectedDeviceView: TextView
    private lateinit var volumeView: TextView
    private lateinit var debugView: TextView
    private lateinit var mirroringInfoView: TextView
    private var selectedDevice: CastDevice? = null
    private val queue = mutableListOf<Uri>()
    private var queueIndex = -1
    private var currentVolume = 30

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addToQueue(it) }
    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addToQueue(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = NetworkScanner(this)
        mediaSender = MediaSender(this)
        mirroringLauncher = MirroringLauncher(this)
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

        root.addView(textView("ExplApp Mirror", 28f, 0xFFF8FAFC.toInt(), Gravity.CENTER))
        root.addView(textView("DLNA للوسائط + AnyView / مرآة الشاشة", 16f, 0xFFCBD5E1.toInt(), Gravity.CENTER).apply {
            setPadding(0, 12, 0, 20)
        })

        root.addView(Button(this).apply {
            text = "1) بحث DLNA عن الأجهزة"
            setOnClickListener { startScan() }
        })
        root.addView(Button(this).apply {
            text = "2) فتح مرآة الشاشة / AnyView"
            setOnClickListener { openScreenMirroring() }
        })
        root.addView(Button(this).apply {
            text = "3) فتح Wi‑Fi Direct"
            setOnClickListener { openWifiDirect() }
        })

        mirroringInfoView = textView(mirroringLauncher.availabilitySummary(), 13f, 0xFFFDE68A.toInt(), Gravity.CENTER).apply {
            setPadding(8, 8, 8, 12)
        }
        selectedDeviceView = textView("الجهاز المحدد: لا يوجد", 14f, 0xFF93C5FD.toInt()).apply {
            setPadding(0, 16, 0, 6)
        }
        queueView = textView("قائمة التشغيل: فارغة", 14f, 0xFFCBD5E1.toInt()).apply {
            setPadding(0, 8, 0, 8)
        }

        root.addView(mirroringInfoView)
        root.addView(selectedDeviceView)
        root.addView(queueView)
        root.addView(horizontalRow(
            controlButton("السابق") { playPrevious() },
            controlButton("تشغيل") { playCurrent() },
            controlButton("التالي") { playNext() }
        ))
        root.addView(horizontalRow(
            controlButton("إيقاف مؤقت") { pausePlayback() },
            controlButton("استئناف") { resumePlayback() },
            controlButton("إيقاف") { stopPlayback() }
        ))
        root.addView(horizontalRow(
            controlButton("مسح القائمة") { clearQueue() },
            controlButton("معلومات التوافق") { showSelectedCompatibility() },
            controlButton("إعادة ضبط التوافق") { resetSelectedCompatibility() }
        ))

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        volumeRow.addView(controlButton("- الصوت") { changeVolume(-5) })
        volumeView = textView("الصوت: $currentVolume%", 14f, 0xFFF8FAFC.toInt(), Gravity.CENTER).apply {
            setPadding(20, 0, 20, 0)
        }
        volumeRow.addView(volumeView)
        volumeRow.addView(controlButton("+ الصوت") { changeVolume(5) })
        root.addView(volumeRow)

        status = textView("الحالة: اختر طريقة الاتصال", 15f, 0xFF5EEAD4.toInt(), Gravity.CENTER).apply {
            setPadding(0, 20, 0, 18)
        }
        debugView = textView("التشخيص: لم يتم إرسال ملف بعد", 13f, 0xFFFDE68A.toInt()).apply {
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1E293B.toInt())
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(Button(this).apply {
            text = "تحديث التشخيص"
            setOnClickListener { refreshDiagnostics() }
        })
        root.addView(status)
        root.addView(debugView)
        root.addView(list)
        return ScrollView(this).apply { addView(root) }
    }

    private fun textView(textValue: String, size: Float, color: Int, gravityValue: Int = Gravity.START) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = gravityValue
    }

    private fun controlButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun horizontalRow(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        views.forEach { addView(it) }
    }

    private fun openScreenMirroring() {
        val result = mirroringLauncher.openBestAvailable()
        mirroringInfoView.text = mirroringLauncher.availabilitySummary()
        status.text = result.message
    }

    private fun openWifiDirect() {
        val result = mirroringLauncher.openWifiDirect()
        mirroringInfoView.text = mirroringLauncher.availabilitySummary()
        status.text = result.message
    }

    private fun startScan() {
        status.text = "جاري فحص الشبكة المحلية لمسار DLNA..."
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
            "لم يتم العثور على أجهزة DLNA. لشاشة G-Guard استخدم مرآة الشاشة / AnyView."
        } else {
            "تم العثور على ${devices.size} جهاز"
        }

        devices.forEach { device ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF172033.toInt())
                setPadding(24, 20, 24, 20)
            }
            container.addView(textView(device.detailsSummary, 15f, 0xFFF8FAFC.toInt()))
            container.addView(Button(this).apply {
                text = "اتصال واختيار الجهاز"
                setOnClickListener { selectDevice(device) }
            })
            container.addView(Button(this).apply {
                text = "اختبار الاتصال"
                setOnClickListener { testDevice(device) }
            })
            container.addView(Button(this).apply {
                text = "معلومات التوافق"
                setOnClickListener { showCompatibility(device) }
            })
            container.addView(Button(this).apply {
                text = "إعادة ضبط توافق الجهاز"
                setOnClickListener { confirmResetCompatibility(device) }
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
            list.addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) })
        }
    }

    private fun selectDevice(device: CastDevice) {
        selectedDevice = device
        selectedDeviceView.text = buildString {
            append("الجهاز المحدد: ${device.name} (${device.ipAddress})")
            if (device.manufacturer.isNotBlank()) append("\nالشركة: ${device.manufacturer}")
            if (device.modelName.isNotBlank()) append(" — ${device.modelName}")
            append("\nDLNA: ${if (device.supportsDlna) "مدعوم" else "غير مؤكد"}")
            append(" — الصوت: ${if (device.supportsVolumeControl) "مدعوم" else "غير متوفر"}")
        }
        status.text = "تم اختيار ${device.name}"
    }

    private fun testDevice(device: CastDevice) {
        status.text = "جاري اختبار الاتصال مع ${device.ipAddress}..."
        lifecycleScope.launch { status.text = connectionTester.test(device).arabicSummary }
    }

    private fun showSelectedCompatibility() {
        val device = selectedDevice ?: run {
            status.text = "اختر جهازًا أولًا."
            return
        }
        showCompatibility(device)
    }

    private fun showCompatibility(device: CastDevice) {
        AlertDialog.Builder(this)
            .setTitle("معلومات التوافق")
            .setMessage(mediaSender.compatibilitySummary(device))
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun resetSelectedCompatibility() {
        val device = selectedDevice ?: run {
            status.text = "اختر جهازًا أولًا."
            return
        }
        confirmResetCompatibility(device)
    }

    private fun confirmResetCompatibility(device: CastDevice) {
        AlertDialog.Builder(this)
            .setTitle("إعادة ضبط التوافق")
            .setMessage("سيعيد التطبيق اختيار أفضل وضع في المحاولة القادمة. هل تريد مسح الوضع المحفوظ فقط أم السجل كاملًا؟")
            .setPositiveButton("مسح الوضع فقط") { _, _ ->
                status.text = mediaSender.resetCompatibility(device, false)
            }
            .setNegativeButton("مسح السجل كاملًا") { _, _ ->
                status.text = mediaSender.resetCompatibility(device, true)
            }
            .setNeutralButton("إلغاء", null)
            .show()
    }

    private fun addToQueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (uri !in queue) queue.add(uri)
        }
        if (queueIndex < 0 && queue.isNotEmpty()) queueIndex = 0
        updateQueueStatus()
        playCurrent()
    }

    private fun playCurrent() {
        val device = selectedDevice ?: run {
            status.text = "اختر جهاز DLNA أولًا، أو استخدم مرآة الشاشة."
            return
        }
        if (queueIndex !in queue.indices) {
            status.text = "اختر صورًا أو فيديوهات أولًا."
            return
        }
        val uri = queue[queueIndex]
        status.text = "جاري إرسال الملف ${queueIndex + 1} من ${queue.size} إلى ${device.name}..."
        lifecycleScope.launch {
            status.text = mediaSender.prepareSend(device, uri).arabicSummary
            updateQueueStatus()
            refreshDiagnostics()
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

    private fun pausePlayback() = withSelectedDevice { device ->
        lifecycleScope.launch { status.text = mediaSender.pause(device) }
    }

    private fun resumePlayback() = withSelectedDevice { device ->
        lifecycleScope.launch { status.text = mediaSender.resume(device) }
    }

    private fun stopPlayback() = withSelectedDevice { device ->
        lifecycleScope.launch { status.text = mediaSender.stop(device) }
    }

    private fun changeVolume(delta: Int) = withSelectedDevice { device ->
        if (!device.supportsVolumeControl) {
            status.text = "الجهاز لا يدعم التحكم بالصوت عبر DLNA."
            return@withSelectedDevice
        }
        currentVolume = (currentVolume + delta).coerceIn(0, 100)
        volumeView.text = "الصوت: $currentVolume%"
        lifecycleScope.launch { status.text = mediaSender.setVolume(device, currentVolume) }
    }

    private fun withSelectedDevice(action: (CastDevice) -> Unit) {
        val device = selectedDevice ?: run {
            status.text = "اختر جهازًا أولًا."
            return
        }
        action(device)
    }

    private fun clearQueue() {
        queue.clear()
        queueIndex = -1
        mediaSender.stopLocalServer()
        updateQueueStatus()
        refreshDiagnostics()
        status.text = "تم مسح قائمة التشغيل."
    }

    private fun updateQueueStatus() {
        queueView.text = if (queue.isEmpty()) "قائمة التشغيل: فارغة" else "قائمة التشغيل: ${queue.size} ملفات — الحالي ${queueIndex + 1}"
    }

    private fun refreshDiagnostics() {
        debugView.text = mediaSender.diagnosticsSummary()
    }
}
