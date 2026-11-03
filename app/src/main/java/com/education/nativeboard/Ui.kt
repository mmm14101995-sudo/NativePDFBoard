package com.education.nativeboard

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val INKS = intArrayOf(
        Color.BLACK, Color.parseColor("#2563eb"), Color.parseColor("#dc2626"),
        Color.parseColor("#16a34a"), Color.parseColor("#facc15"), Color.WHITE
    )
    val BGS = intArrayOf(
        Color.WHITE, Color.parseColor("#fdf6e3"), Color.parseColor("#111111"),
        Color.parseColor("#0f172a"), Color.parseColor("#1f3d2b"), Color.parseColor("#3b2f2f")
    )
    val BG_NAMES: Array<CharSequence> = arrayOf("أبيض", "كريمي", "أسود", "كحلي", "أخضر سبورة", "بني غامق")
    val SIZES = floatArrayOf(3f, 6f, 12f)

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun isDark(c: Int): Boolean =
        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0 < 0.45

    @Suppress("DEPRECATION")
    fun immersive(a: Activity) {
        a.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    fun pill(c: Context, color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(c, radius).toFloat()
            setColor(color)
        }

    fun btn(c: Context, label: String, onClick: () -> Unit): TextView = TextView(c).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        minWidth = dp(c, 44)
        setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6))
        background = pill(c, 0x33FFFFFF, 14)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 44))
            .apply { setMargins(dp(c, 3), 0, dp(c, 3), 0) }
        setOnClickListener { onClick() }
    }

    fun label(c: Context): TextView = TextView(c).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(c, 8), 0, dp(c, 8), 0)
    }

    fun sep(c: Context): View = View(c).apply {
        setBackgroundColor(0x33FFFFFF)
        layoutParams = LinearLayout.LayoutParams(dp(c, 1), dp(c, 26)).apply { setMargins(dp(c, 6), 0, dp(c, 6), 0) }
    }

    // شريط الأدوات العايم: صف أفقي قابل للسحب
    fun bar(c: Context): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6))
        }
        val scroll = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            background = pill(c, 0xEE1D212C.toInt(), 30)
            addView(row)
        }
        return Pair(scroll, row)
    }
}
