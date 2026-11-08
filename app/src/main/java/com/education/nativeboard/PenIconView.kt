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

// رسم أشكال الأقلام: كل نوع له شكل مختلف (حبر، قلم جاف، ريشة خط، فاين، فرشاة، ماركر، تظليل، رصاص، نقط، ذكي)
// الإحداثيات بنسبة من حجم الأيقونة عشان تكبر وتصغر
class PenIconView(ctx: Context, private val kind: String, var body: Int) : View(ctx) {
    var picked = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun X(f: Float): Float = f * width
    private fun Y(f: Float): Float = f * height

    private fun shade(color: Int, k: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * k).toInt().coerceIn(0, 255),
        (Color.green(color) * k).toInt().coerceIn(0, 255),
        (Color.blue(color) * k).toInt().coerceIn(0, 255)
    )

    // جسم القلم بتدرج لمعان على الجنب
    private fun barrel(c: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float, rad: Float = 0.06f) {
        p.reset()
        p.isAntiAlias = true
        p.shader = LinearGradient(
            X(l), 0f, X(r), 0f,
            intArrayOf(shade(color, 0.55f), color, shade(color, 1.35f), color, shade(color, 0.6f)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(X(l), Y(t), X(r), Y(b)), X(rad), X(rad), p)
        p.shader = null
    }

    private fun fill(c: Canvas, color: Int, vararg f: Float) {
        val path = Path()
        var i = 0
        while (i + 1 < f.size) {
            if (i == 0) path.moveTo(X(f[i]), Y(f[i + 1])) else path.lineTo(X(f[i]), Y(f[i + 1]))
            i += 2
        }
        path.close()
        p.reset()
        p.isAntiAlias = true
        p.color = color
        c.drawPath(path, p)
    }

    private fun dot(c: Canvas, color: Int, x: Float, y: Float, r: Float) {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        c.drawCircle(X(x), Y(y), X(r), p)
    }

    private fun gloss(c: Canvas, l: Float, t: Float, b: Float) {
        p.reset()
        p.isAntiAlias = true
        p.color = 0x66FFFFFF
        c.drawRect(X(l), Y(t), X(l + 0.035f), Y(b), p)
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        val silver = 0xFFE2E8F0.toInt()
        val dark = 0xFF1E293B.toInt()
        when (kind) {
            "fountain" -> {
                barrel(c, 0xFF475569.toInt(), 0.3f, 0.42f, 0.7f, 0.6f, 0.04f)
                barrel(c, body, 0.27f, 0.08f, 0.73f, 0.44f)
                gloss(c, 0.3f, 0.12f, 0.4f)
                fill(c, silver, 0.34f, 0.6f, 0.66f, 0.6f, 0.5f, 0.96f)
                fill(c, dark, 0.49f, 0.64f, 0.51f, 0.64f, 0.51f, 0.9f, 0.49f, 0.9f)
                dot(c, dark, 0.5f, 0.66f, 0.02f)
            }
            "gel" -> {
                barrel(c, 0xFF334155.toInt(), 0.3f, 0.06f, 0.7f, 0.62f)
                barrel(c, body, 0.3f, 0.16f, 0.7f, 0.22f, 0.02f)
                gloss(c, 0.3f, 0.26f, 0.56f)
                fill(c, silver, 0.42f, 0.62f, 0.58f, 0.62f, 0.5f, 0.92f)
                dot(c, dark, 0.5f, 0.93f, 0.015f)
            }
            "nib" -> {
                barrel(c, 0xFF475569.toInt(), 0.3f, 0.42f, 0.7f, 0.6f, 0.04f)
                barrel(c, body, 0.27f, 0.08f, 0.73f, 0.44f)
                gloss(c, 0.3f, 0.12f, 0.4f)
                fill(c, silver, 0.18f, 0.6f, 0.82f, 0.6f, 0.5f, 0.97f)
                fill(c, dark, 0.49f, 0.66f, 0.51f, 0.66f, 0.51f, 0.93f, 0.49f, 0.93f)
            }
            "fineliner" -> {
                barrel(c, body, 0.36f, 0.06f, 0.64f, 0.7f, 0.05f)
                fill(c, shade(body, 0.7f), 0.36f, 0.7f, 0.64f, 0.7f, 0.64f, 0.74f, 0.36f, 0.74f)
                gloss(c, 0.36f, 0.1f, 0.6f)
                fill(c, dark, 0.42f, 0.72f, 0.58f, 0.72f, 0.5f, 0.96f)
            }
            "brush" -> {
                barrel(c, body, 0.3f, 0.06f, 0.7f, 0.5f)
                gloss(c, 0.3f, 0.1f, 0.45f)
                fill(c, silver, 0.3f, 0.5f, 0.7f, 0.5f, 0.6f, 0.62f, 0.4f, 0.62f)
                fill(c, shade(body, 1.2f), 0.4f, 0.62f, 0.6f, 0.62f, 0.52f, 0.97f, 0.48f, 0.97f)
            }
            "marker" -> {
                barrel(c, body, 0.25f, 0.06f, 0.75f, 0.66f, 0.03f)
                fill(c, dark, 0.25f, 0.06f, 0.75f, 0.06f, 0.75f, 0.14f, 0.25f, 0.14f)
                gloss(c, 0.25f, 0.18f, 0.6f)
                fill(c, shade(body, 0.8f), 0.25f, 0.66f, 0.75f, 0.66f, 0.75f, 0.84f, 0.25f, 0.84f)
                fill(c, shade(body, 0.6f), 0.3f, 0.84f, 0.7f, 0.84f, 0.7f, 0.94f, 0.3f, 0.94f)
            }
            "neon" -> {
                fill(c, (body and 0x00FFFFFF) or 0x44000000, 0.2f, 0.06f, 0.8f, 0.06f, 0.8f, 0.94f, 0.2f, 0.94f)
                barrel(c, body, 0.25f, 0.06f, 0.75f, 0.66f, 0.03f)
                gloss(c, 0.25f, 0.18f, 0.6f)
                fill(c, shade(body, 1.2f), 0.3f, 0.66f, 0.7f, 0.66f, 0.7f, 0.84f, 0.3f, 0.84f)
                fill(c, dark, 0.3f, 0.84f, 0.7f, 0.84f, 0.5f, 0.96f)
            }
            "highlighter" -> {
                barrel(c, body, 0.24f, 0.06f, 0.76f, 0.6f, 0.04f)
                gloss(c, 0.24f, 0.1f, 0.5f)
                fill(c, shade(body, 0.85f), 0.24f, 0.6f, 0.76f, 0.6f, 0.66f, 0.9f, 0.34f, 0.9f)
                fill(c, dark, 0.24f, 0.6f, 0.76f, 0.6f, 0.76f, 0.64f, 0.24f, 0.64f)
            }
            "pencil" -> {
                fill(c, 0xFFF9A8D4.toInt(), 0.36f, 0.08f, 0.64f, 0.08f, 0.64f, 0.18f, 0.36f, 0.18f)
                fill(c, 0xFFF5D7A1.toInt(), 0.36f, 0.18f, 0.64f, 0.18f, 0.64f, 0.6f, 0.36f, 0.6f)
                fill(c, 0xFFF5D7A1.toInt(), 0.36f, 0.6f, 0.64f, 0.6f, 0.5f, 0.9f)
                fill(c, dark, 0.45f, 0.82f, 0.55f, 0.82f, 0.5f, 0.96f)
            }
            "dotted" -> {
                barrel(c, body, 0.32f, 0.08f, 0.68f, 0.6f)
                gloss(c, 0.32f, 0.12f, 0.5f)
                fill(c, silver, 0.36f, 0.6f, 0.64f, 0.6f, 0.5f, 0.94f)
                dot(c, dark, 0.5f, 0.7f, 0.02f)
                dot(c, dark, 0.46f, 0.78f, 0.02f)
                dot(c, dark, 0.54f, 0.84f, 0.02f)
                dot(c, dark, 0.5f, 0.9f, 0.02f)
            }
            else -> { // smart
                barrel(c, body, 0.3f, 0.06f, 0.7f, 0.64f)
                gloss(c, 0.3f, 0.1f, 0.5f)
                fill(c, 0xFF38BDF8.toInt(), 0.5f, 0.2f, 0.58f, 0.3f, 0.5f, 0.4f, 0.42f, 0.3f)
                fill(c, silver, 0.36f, 0.64f, 0.64f, 0.64f, 0.5f, 0.92f)
            }
        }
    }
}
