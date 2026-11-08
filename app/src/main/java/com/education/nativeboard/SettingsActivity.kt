package com.education.nativeboard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// الإعدادات: الوضع الداكن والوضع الزجاجي
class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(40), dp(18), dp(18))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val dark = Prefs.dark(this)
        val ink = if (dark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()
        val muted = 0xFF64748B.toInt()
        root.removeAllViews()
        root.setBackgroundColor(if (dark) 0xFF0B1020.toInt() else 0xFFF1F5F9.toInt())

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = FrameLayout(this)
        back.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x14808080)
        }
        back.setOnClickListener { finish() }
        back.addView(IconView(this, "prev", ink), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        head.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val title = TextView(this)
        title.text = "الإعدادات"
        title.textSize = 22f
        title.setTextColor(ink)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(dp(12), 0, 0, 0)
        head.addView(title)
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(20))
        })

        root.addView(switchRow("الوضع الليلي", "ألوان داكنة للتطبيق كله · إطفاؤه = الوضع النهاري", Prefs.dark(this), ink, muted) { v ->
            Prefs.setDark(this, v)
            render()
        })
        root.addView(switchRow("الوضع الزجاجي المعدني", "كروت وأشرطة بلمعة معدنية وحافة مضيئة", Prefs.glass(this), ink, muted) { v ->
            Prefs.setGlass(this, v)
            render()
        })
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, ink: Int, muted: Int, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(14), dp(12), dp(14))
        row.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (Prefs.dark(this@SettingsActivity)) 0xFF1E293B.toInt() else Color.WHITE)
        }
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t1 = TextView(this)
        t1.text = title
        t1.textSize = 16f
        t1.setTextColor(ink)
        t1.setTypeface(t1.typeface, Typeface.BOLD)
        val t2 = TextView(this)
        t2.text = sub
        t2.textSize = 12f
        t2.setTextColor(muted)
        t2.setPadding(0, dp(4), 0, 0)
        texts.addView(t1)
        texts.addView(t2)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(12))
        }
        return row
    }
}
