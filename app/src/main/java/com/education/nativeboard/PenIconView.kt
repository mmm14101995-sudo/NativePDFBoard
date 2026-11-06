package com.education.nativeboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

// قلم زجاجي: جسم بتدرج ولمعة وسن حسب النوع (حبر، فاين، ماركر، تظليل، رصاص)
class PenIconView(ctx: Context, private val kind: String, var body: Int) : View(ctx) {
    var picked: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun shade(col: Int, f: Float): Int {
        val m = if (f > 0f) 255 else 0
        val t = Math.abs(f)
        val r = Color.red(col)
        val g = Color.green(col)
        val b = Color.blue(col)
        return Color.rgb(
            (r + (m - r) * t).toInt(),
            (g + (m - g) * t).toInt(),
            (b + (m - b) * t).toInt()
        )
    }

    // جسم زجاجي: تدرج أفقي وخط لمعة أبيض شفاف
    private fun gloss(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) {
        p.reset()
        p.isAntiAlias = true
        p.shader = LinearGradient(
            l, 0f, r, 0f,
            intArrayOf(shade(col, 0.5f), col, shade(col, -0.4f)),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
        p.shader = null
        p.color = Color.argb(150, 255, 255, 255)
        val w = r - l
        c.drawRoundRect(RectF(l + w * 0.16f, t + 3f, l + w * 0.26f, b - 3f), 2f, 2f, p)
    }

    // شكل مضلّع بلون ثابت (للسن والتفاصيل)
    private fun poly(c: Canvas, col: Int, vararg xy: Float) {
        path.reset()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.close()
        p.reset()
        p.isAntiAlias = true
        p.color = col
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        c.save()
        c.scale(width / 40f, height / 60f)
        when (kind) {
            "fountain" -> {
                gloss(c, 12f, 4f, 28f, 30f, 5f, body)
                poly(c, shade(body, -0.3f), 12f, 29f, 28f, 29f, 28f, 33f, 12f, 33f)
                poly(c, 0xFFE2E8F0.toInt(), 14f, 33f, 26f, 33f, 20f, 56f)
                poly(c, 0xFF64748B.toInt(), 19f, 40f, 21f, 40f, 20f, 52f)
            }
            "fineliner" -> {
                gloss(c, 16f, 4f, 24f, 32f, 3f, body)
                poly(c, shade(body, -0.2f), 16f, 32f, 24f, 32f, 20f, 56f)
            }
            "marker" -> {
                gloss(c, 9f, 4f, 31f, 34f, 5f, body)
                poly(c, shade(body, -0.5f), 9f, 36f, 31f, 36f, 27f, 56f, 13f, 56f)
            }
            "highlighter" -> {
                gloss(c, 7f, 4f, 33f, 34f, 9f, body)
                poly(c, shade(body, 0.25f), 9f, 32f, 31f, 32f, 29f, 47f, 11f, 47f)
            }
            "brush" -> {
                gloss(c, 12f, 4f, 28f, 32f, 8f, body)
                poly(c, shade(body, -0.3f), 12f, 32f, 28f, 32f, 24f, 50f, 16f, 50f)
                poly(c, 0xFF1E293B.toInt(), 17f, 50f, 23f, 50f, 20f, 57f)
            }
            "smart" -> {
                gloss(c, 13f, 4f, 27f, 30f, 4f, body)
                poly(c, 0xFFE2E8F0.toInt(), 15f, 30f, 25f, 30f, 20f, 50f)
                poly(c, 0xFF38BDF8.toInt(), 20f, 11f, 23f, 15f, 20f, 19f, 17f, 15f)
            }
            "pencil" -> {
                gloss(c, 14f, 4f, 26f, 36f, 3f, body)
                poly(c, 0xFFFDE68A.toInt(), 14f, 36f, 26f, 36f, 20f, 56f)
                poly(c, 0xFF111827.toInt(), 18f, 49f, 22f, 49f, 20f, 56f)
            }
        }
        c.restore()
    }
}
