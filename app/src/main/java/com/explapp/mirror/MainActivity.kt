package com.explapp.mirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
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
    private lateinit var urlInput: EditText

    private var selectedDevice: CastDevice? = null
    private val queue = mutableListOf<Uri>()
    private var queueIndex = -1
    private var currentVolume = 30

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addToQueue(it) }
    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addToQueue(it) }
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addToQueue(it) }

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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(28, 42, 28, 32)
            setBackgroundColor(0xFF0F172A.toInt())
        }

        root.addView(textView("ExplApp Mirror", 28f, 0xFFF8FAFC.toInt(), Gravity.CENTER))
        root.addView(textView("إرسال الوسائط والروابط عبر DLNA أو فتح مرآة الشاشة", 15f, 0xFFCBD5E1.toInt(), Gravity.CENTER).apply {
            setPadding(0, 10, 0, 20)
        })

        root.addView(sectionTitle("الاتصال"))
        root.addView(horizontalRow(
            controlButton("بحث عن الأجهزة") { startScan() },
            controlButton("مرآة الشاشة") { openScreenMirroring() }
        ))
        root.addView(fullWidthButton("فتح Wi‑Fi Direct") { openWifiDirect() })

        mirroringInfoView = textView(mirroringLauncher.availabilitySummary(), 13f, 0xFFFDE68A.toInt(), Gravity.CENTER).apply {
            setPadding(8, 8, 8, 14)
        }
        root.addView(mirroringInfoView)

        selectedDeviceView = textView("الجهاز المحدد: لا يوجد", 14f, 0xFF93C5FD.toInt()).apply {
            setPadding(12, 14, 12, 14)
            setBackgroundColor(0xFF172033.toInt())
        }
        root.addView(selectedDeviceView)

        root.addView(sectionTitle("الوسائط المحلية"))
        root.addView(horizontalRow(
            controlButton("صور") { pickImages() },
            controlButton("فيديو") { pickVideos() },
            controlButton("صوت") { pickAudio() }
        ))

        queueView = textView("قائمة التشغيل: فارغة", 14f, 0xFFCBD5E1.toInt(), Gravity.CENTER).apply {
            setPadding(0, 10, 0, 10)
        }
        root.addView(queueView)

        root.addView(sectionTitle("رابط مباشر"))
        urlInput = EditText(this).apply {
            hint = "https://example.com/video.mp4"
            setTextColor(0xFFF8FAFC.toInt())
            setHintTextColor(0xFF64748B.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xFF172033.toInt())
        }
        root.addView(urlInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(fullWidthButton("إرسال الرابط إلى الجهاز") { castDirectUrl() })
        root.addView(textView(
            "يدعم الروابط المباشرة للوسائط مثل MP4 وMP3 وM3U8. صفحات المواقع المحمية ليست روابط فيديو مباشرة.",
            12f,
            0xFF94A3B8.toInt(),
            Gravity.CENTER
        ).apply { setPadding(8, 6, 8, 10) })

        root.addView(sectionTitle("التحكم"))
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

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        volumeRow.addView(controlButton("- الصوت") { changeVolume(-5) })
        volumeView = textView("الصوت: $currentVolume%", 14f, 0xFFF8FAFC.toInt(), Gravity.CENTER).apply {
            setPadding(20, 0, 20, 0)
        }
        volumeRow.addView(volumeView)
        volumeRow.addView(controlButton("+ الصوت") { changeVolume(5) })
        root.addView(volumeRow)

        root.addView(horizontalRow(
            controlButton("مسح القائمة") { clearQueue() },
            controlButton("معلومات التوافق") { showSelectedCompatibility() }
        ))

        status = textView("الحالة: ابحث عن جهاز ثم اختره", 15f, 0xFF5EEAD4.toInt(), Gravity.CENTER).apply {
            setPadding(8, 20, 8, 16)
        }
        root.addView(status)

        root.addView(sectionTitle("الأجهزة المتاحة"))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(list)

        root.addView(sectionTitle("التشخيص"))
        root.addView(horizontalRow(
            controlButton("تحديث التشخيص") { refreshDiagnostics() },
            controlButton("إعادة ضبط التوافق") { resetSelectedCompatibility() }
        ))
        debugView = textView("لم يتم إرسال ملف أو رابط بعد", 13f, 0xFFFDE68A.toInt()).apply {
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1E293B.toInt())
        }
        root.addView(debugView)

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
    }

    private fun textView(
        textValue: String,
        size: Float,
        color: Int,
        gravityValue: Int = Gravity.START
    ) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = gravityValue
    }

    private fun sectionTitle(title: String) = textView(title, 17f, 0xFFF8FAFC.toInt()).apply {
        setPadding(0, 20, 0, 8)
    }

    private fun controlButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun fullWidthButton(label: String, action: () -> Unit) = controlButton(label, action).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun horizontalRow(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        views.forEach { button ->
            addView(button, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ))
        }
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
        status.text = "جاري البحث عن أجهزة DLNA..."
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
            "لم يتم العثور على أجهزة DLNA. جرّب مرآة الشاشة أو تأكد أن الجهازين على الشبكة نفسها."
        } else {
            "تم العثور على ${devices.size} جهاز"
        }

        devices.forEach { device ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setBackgroundColor(0xFF172033.toInt())
                setPadding(22, 18, 22, 18)
            }
            container.addView(textView(device.detailsSummary, 15f, 0xFFF8FAFC.toInt()))
            container.addView(horizontalRow(
                controlButton(if (selectedDevice == device) "محدد" else "اختيار") { selectDevice(device) },
                controlButton("اختبار") { testDevice(device) },
                controlButton("التوافق") { showCompatibility(device) }
            ))
            list.addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) })
        }
    }

    private fun selectDevice(device: CastDevice) {
        selectedDevice = device
        selectedDeviceView.text = buildString {
            append("الجهاز المحدد: ${device.name} (${device.ipAddress})")
            if (device.manufacturer.isNotBlank()) append("\nالشركة: ${device.manufacturer}")
            if (device.modelName.isNotBlank()) append(" — ${device.modelName}")
            append("\nDLNA: ${if (device.supportsDlna) "مدعوم" else "غير مؤكد"}")
            append(" — التحكم بالصوت: ${if (device.supportsVolumeControl) "مدعوم" else "غير متوفر"}")
        }
        status.text = "تم اختيار ${device.name}. اختر وسيطًا محليًا أو أدخل رابطًا مباشرًا."
        renderDevices(deviceManager.getDevices())
    }

    private fun testDevice(device: CastDevice) {
        status.text = "جاري اختبار الاتصال مع ${device.ipAddress}..."
        lifecycleScope.launch { status.text = connectionTester.test(device).arabicSummary }
    }

    private fun pickImages() = pickForSelectedDevice { imagePicker.launch(arrayOf("image/*")) }

    private fun pickVideos() = pickForSelectedDevice { videoPicker.launch(arrayOf("video/*")) }

    private fun pickAudio() = pickForSelectedDevice { audioPicker.launch(arrayOf("audio/*")) }

    private fun pickForSelectedDevice(openPicker: () -> Unit) {
        if (selectedDevice == null) {
            status.text = "اختر جهازًا من قائمة الأجهزة أولًا."
            return
        }
        openPicker()
    }

    private fun castDirectUrl() {
        val device = selectedDevice ?: run {
            status.text = "اختر جهازًا أولًا."
            return
        }
        val url = urlInput.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            status.text = "أدخل رابطًا مباشرًا يبدأ بـ http:// أو https://"
            return
        }

        status.text = "جاري إرسال الرابط إلى ${device.name}..."
        lifecycleScope.launch {
            status.text = runCatching {
                mediaSender.prepareSendUrl(device, url).arabicSummary
            }.getOrElse {
                "تعذر إرسال الرابط: ${it.message.orEmpty()}"
            }
            refreshDiagnostics()
        }
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
            .setMessage("سيعيد التطبيق اختيار أفضل وضع في المحاولة القادمة.")
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
        val device = selectedDevice ?: run {
            status.text = "اختر جهاز DLNA أولًا، أو استخدم مرآة الشاشة."
            return
        }
        if (queueIndex !in queue.indices) {
            status.text = "اختر صورة أو فيديو أو ملفًا صوتيًا أولًا."
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
        queueView.text = if (queue.isEmpty()) {
            "قائمة التشغيل: فارغة"
        } else {
            "قائمة التشغيل: ${queue.size} ملفات — الحالي ${queueIndex + 1}"
        }
    }

    private fun refreshDiagnostics() {
        debugView.text = mediaSender.diagnosticsSummary()
    }
}
