package com.explapp.mirror

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createMainView())
    }

    private fun createMainView(): LinearLayout {
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

        val status = TextView(this).apply {
            text = "الحالة: جاهز للبحث"
            textSize = 15f
            setTextColor(0xFF5EEAD4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        scanButton.setOnClickListener {
            status.text = "جاري تجهيز طبقة اكتشاف الأجهزة..."
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scanButton)
        root.addView(status)
        return root
    }
}
