package com.explapp.mirror

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.explapp.mirror.core.MediaSender
import com.explapp.mirror.session.CastSessionManager
import com.explapp.mirror.session.PlaybackState
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private lateinit var mediaSender: MediaSender
    private lateinit var stateView: TextView
    private lateinit var volumeView: TextView
    private val commandButtons = mutableListOf<Button>()
    private var commandInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = CastSessionManager.device
        if (device == null) {
            finish()
            return
        }

        mediaSender = MediaSender(this)
        setContentView(buildView())
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        if (::stateView.isInitialized) refreshState()
    }

    private fun buildView(): View {
        val device = requireNotNull(CastSessionManager.device)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(30, 46, 30, 30)
            setBackgroundColor(0xFF0F172A.toInt())
        }

        root.addView(label("شاشة التحكم بالبث", 26f, 0xFFF8FAFC.toInt(), Gravity.CENTER))
        root.addView(label(device.name, 20f, 0xFF93C5FD.toInt(), Gravity.CENTER).apply {
            setPadding(0, 18, 0, 6)
        })
        root.addView(label(device.ipAddress, 13f, 0xFF94A3B8.toInt(), Gravity.CENTER))
        root.addView(label(
            CastSessionManager.mediaTitle.ifBlank { "وسائط قيد التشغيل" },
            18f,
            0xFFF8FAFC.toInt(),
            Gravity.CENTER
        ).apply { setPadding(0, 28, 0, 18) })

        stateView = label("", 15f, 0xFF5EEAD4.toInt(), Gravity.CENTER).apply {
            setPadding(18, 16, 18, 16)
            setBackgroundColor(0xFF172033.toInt())
        }
        root.addView(stateView, matchWrap())

        root.addView(row(
            commandButton("تشغيل") { resumePlayback() },
            commandButton("إيقاف مؤقت") { pausePlayback() },
            commandButton("إيقاف") { stopPlayback() }
        ))

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 24, 0, 10)
        }
        volumeRow.addView(commandButton("−") { changeVolume(-5) })
        volumeView = label("", 16f, 0xFFF8FAFC.toInt(), Gravity.CENTER).apply {
            setPadding(24, 0, 24, 0)
        }
        volumeRow.addView(volumeView)
        volumeRow.addView(commandButton("+") { changeVolume(5) })
        root.addView(volumeRow)

        root.addView(button("العودة إلى التطبيق") { finish() }.apply {
            layoutParams = matchWrap()
        })
        return root
    }

    private fun pausePlayback() = runCommand(
        pending = "جاري الإيقاف المؤقت...",
        successState = PlaybackState.PAUSED
    ) { mediaSender.pause(requireNotNull(CastSessionManager.device)) }

    private fun resumePlayback() = runCommand(
        pending = "جاري استئناف التشغيل...",
        successState = PlaybackState.PLAYING
    ) { mediaSender.resume(requireNotNull(CastSessionManager.device)) }

    private fun stopPlayback() = runCommand(
        pending = "جاري إيقاف البث...",
        successState = PlaybackState.STOPPED,
        finishOnSuccess = true
    ) { mediaSender.stop(requireNotNull(CastSessionManager.device)) }

    private fun runCommand(
        pending: String,
        successState: PlaybackState,
        finishOnSuccess: Boolean = false,
        action: suspend () -> String
    ) {
        if (!beginCommand()) return
        CastSessionManager.updateState(PlaybackState.CONNECTING, pending)
        refreshState()
        lifecycleScope.launch {
            try {
                val message = runCatching { action() }
                    .getOrElse { "تعذر تنفيذ الأمر: ${it.message.orEmpty()}" }
                val failed = isFailureMessage(message)
                CastSessionManager.updateState(
                    if (failed) PlaybackState.ERROR else successState,
                    message
                )
                if (successState == PlaybackState.STOPPED && !failed) {
                    CastSessionManager.clearPlayback()
                }
                refreshState()
                if (finishOnSuccess && !failed) finish()
            } finally {
                endCommand()
            }
        }
    }

    private fun changeVolume(delta: Int) {
        val device = requireNotNull(CastSessionManager.device)
        if (!device.supportsVolumeControl) {
            CastSessionManager.updateState(PlaybackState.ERROR, "هذا الجهاز لا يدعم التحكم بالصوت عبر DLNA.")
            refreshState()
            return
        }

        val previousVolume = CastSessionManager.volume
        val requestedVolume = (previousVolume + delta).coerceIn(0, 100)
        if (requestedVolume == previousVolume || !beginCommand()) return

        val playbackStateBeforeCommand = CastSessionManager.state
        CastSessionManager.updateVolume(requestedVolume)
        CastSessionManager.updateState(PlaybackState.CONNECTING, "جاري تغيير مستوى الصوت...")
        refreshState()
        lifecycleScope.launch {
            try {
                val message = runCatching { mediaSender.setVolume(device, requestedVolume) }
                    .getOrElse { "تعذر تغيير مستوى الصوت: ${it.message.orEmpty()}" }
                val failed = isFailureMessage(message)
                if (failed) {
                    CastSessionManager.updateVolume(previousVolume)
                    CastSessionManager.updateState(PlaybackState.ERROR, message)
                } else {
                    CastSessionManager.updateState(playbackStateBeforeCommand, message)
                }
                refreshState()
            } finally {
                endCommand()
            }
        }
    }

    private fun beginCommand(): Boolean {
        if (commandInFlight) return false
        commandInFlight = true
        commandButtons.forEach { it.isEnabled = false }
        return true
    }

    private fun endCommand() {
        commandInFlight = false
        commandButtons.forEach { it.isEnabled = true }
    }

    private fun isFailureMessage(message: String): Boolean =
        message.contains("تعذر") || message.contains("فشل")

    private fun refreshState() {
        val stateText = when (CastSessionManager.state) {
            PlaybackState.IDLE -> "جاهز"
            PlaybackState.CONNECTING -> "جارٍ تنفيذ الأمر"
            PlaybackState.PLAYING -> "قيد التشغيل"
            PlaybackState.PAUSED -> "متوقف مؤقتًا"
            PlaybackState.STOPPED -> "تم إيقاف البث"
            PlaybackState.ERROR -> "حدثت مشكلة"
        }
        stateView.text = buildString {
            append("الحالة: $stateText")
            if (CastSessionManager.lastMessage.isNotBlank()) {
                append("\n${CastSessionManager.lastMessage}")
            }
        }
        volumeView.text = "الصوت: ${CastSessionManager.volume}%"
    }

    private fun label(text: String, size: Float, color: Int, gravityValue: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = gravityValue
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun commandButton(text: String, action: () -> Unit) = button(text, action).also {
        commandButtons += it
    }

    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
