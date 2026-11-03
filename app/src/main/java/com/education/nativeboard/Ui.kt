package com.education.nativeboard

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

    // رسم القلم بشكله: fountain (حبر)، fine (فاين)، pencil (رصاص)، hl (تظليل)، marker (ماركر)، eraser (ممحاة)
    fun drawPen(c: Canvas, kind: String, w: Float, h: Float) {
        c.save()
        c.scale(w / 40f, h / 60f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        fun rect(l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) {
            p.color = col
            c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
        }
        fun tri(pts: FloatArray, col: Int) {
            val path = Path()
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i + 1 < pts.size) {
                path.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            path.close()
            p.color = col
            c.drawPath(path, p)
        }
        when (kind) {
            "fountain" -> {
                rect(12f, 4f, 28f, 30f, 4f, 0xFF64748B.toInt())
                rect(12f, 26f, 28f, 31f, 0f, 0xFF334155.toInt())
                tri(floatArrayOf(14f, 31f, 26f, 31f, 20f, 56f), 0xFFE2E8F0.toInt())
            }
            "fine" -> {
                rect(16f, 4f, 24f, 32f, 2f, 0xFF1E293B.toInt())
                tri(floatArrayOf(17f, 32f, 23f, 32f, 20f, 56f), 0xFF94A3B8.toInt())
            }
            "pencil" -> {
                rect(14f, 4f, 26f, 36f, 2f, 0xFFFBBF24.toInt())
                tri(floatArrayOf(14f, 36f, 26f, 36f, 20f, 56f), 0xFFFDE68A.toInt())
                tri(floatArrayOf(17f, 49f, 23f, 49f, 20f, 56f), 0xFF111827.toInt())
            }
            "hl" -> {
                rect(8f, 4f, 32f, 34f, 6f, 0xFFFACC15.toInt())
                rect(10f, 32f, 30f, 46f, 3f, 0xFFFDE047.toInt())
            }
            "marker" -> {
                rect(10f, 4f, 30f, 36f, 4f, 0xFF6366F1.toInt())
                tri(floatArrayOf(10f, 40f, 30f, 40f, 26f, 56f, 14f, 56f), 0xFF374151.toInt())
            }
            "eraser" -> {
                rect(12f, 30f, 28f, 54f, 4f, 0xFFCBD5E1.toInt())
                rect(12f, 6f, 28f, 30f, 4f, 0xFFF472B6.toInt())
            }
        }
        c.restore()
    }

    // زرار فيه شكل القلم بدل الاسم
    fun penButton(c: Context, kind: String, onClick: () -> Unit): FrameLayout {
        val icon = object : View(c) {
            override fun onDraw(canvas: Canvas) = drawPen(canvas, kind, width.toFloat(), height.toFloat())
        }
        return FrameLayout(c).apply {
            background = pill(c, 0x14FFFFFF, 14)
            setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
            addView(icon, FrameLayout.LayoutParams(dp(c, 30), dp(c, 52), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(c, 46), dp(c, 60)).apply { setMargins(dp(c, 3), 0, dp(c, 3), 0) }
            setOnClickListener { onClick() }
        }
    }
}
