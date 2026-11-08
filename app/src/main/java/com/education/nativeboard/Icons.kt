package com.education.nativeboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

// أيقونة بالرسم (من غير كلام)، بتتلوّن بأي لون
class IconView(ctx: Context, private val name: String, private val tint: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // الأيقونة بتترسم مرة واحدة وتتخزن كصورة، فالأداء ما بيتأثرش
    private var cache: Bitmap? = null

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        val cur = cache
        val bmp = if (cur != null && cur.width == width && cur.height == height) cur else render3d()
        cache = bmp
        c.drawBitmap(bmp, 0f, 0f, null)
    }

    private fun mix(col: Int, k: Float): Int = Color.argb(
        Color.alpha(col),
        (Color.red(col) * k).toInt().coerceIn(0, 255),
        (Color.green(col) * k).toInt().coerceIn(0, 255),
        (Color.blue(col) * k).toInt().coerceIn(0, 255)
    )

    // ثلاثي الأبعاد: ظل تحت + تدرج لوني من الفاتح لفوق للغامق لتحت
    private fun render3d(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = minOf(width, height) / 24f
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        if (name == "eraser") {
            Icons.eraser3d(c)
            return bmp
        }
        p.strokeWidth = 2f
        p.shader = null
        p.color = 0x55000000
        c.translate(0f, 1.1f)
        Icons.draw(c, name, p)
        c.translate(0f, -1.1f)
        p.shader = LinearGradient(0f, 3f, 0f, 21f, intArrayOf(mix(tint, 1.35f), tint, mix(tint, 0.65f)), null, Shader.TileMode.CLAMP)
        Icons.draw(c, name, p)
        p.shader = null
        return bmp
    }
}

object Icons {
    // ممحاة ثلاثية الأبعاد: جسم بتدرج لوني وظل ولمعة
    fun eraser3d(c: Canvas) {
        val body = Path()
        body.moveTo(3f, 15f)
        body.lineTo(10.5f, 6.5f)
        body.lineTo(20.5f, 16f)
        body.lineTo(13f, 21f)
        body.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x33000000
        c.drawOval(RectF(4f, 19.5f, 19f, 22f), p)
        p.shader = LinearGradient(
            3f, 6f, 20f, 21f,
            intArrayOf(0xFFFFE4E6.toInt(), 0xFFFB7185.toInt(), 0xFFBE123C.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawPath(body, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = 0xCCFFFFFF.toInt()
        c.drawLine(10.5f, 7.5f, 18.5f, 15.5f, p)
        p.strokeWidth = 0.8f
        p.color = 0x66000000
        c.drawPath(body, p)
    }

    fun draw(c: Canvas, name: String, p: Paint) {
        val path = Path()
        when (name) {
            "undo" -> {
                path.moveTo(9f, 14f); path.lineTo(4f, 9f); path.lineTo(9f, 4f)
                path.moveTo(4f, 9f); path.lineTo(13f, 9f)
                path.cubicTo(18.5f, 9f, 20f, 12.5f, 20f, 16f)
            }
            "redo" -> {
                path.moveTo(15f, 14f); path.lineTo(20f, 9f); path.lineTo(15f, 4f)
                path.moveTo(20f, 9f); path.lineTo(11f, 9f)
                path.cubicTo(5.5f, 9f, 4f, 12.5f, 4f, 16f)
            }
            "next" -> { path.moveTo(15f, 18f); path.lineTo(9f, 12f); path.lineTo(15f, 6f) }
            "prev" -> { path.moveTo(9f, 18f); path.lineTo(15f, 12f); path.lineTo(9f, 6f) }
            "eraser" -> {
                path.moveTo(4f, 14f); path.lineTo(11f, 6f); path.lineTo(20f, 15f); path.lineTo(13f, 21f); path.close()
                path.moveTo(9f, 17.5f); path.lineTo(15.5f, 11f)
            }
            "download" -> {
                path.moveTo(12f, 4f); path.lineTo(12f, 15f)
                path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                path.moveTo(5f, 19f); path.lineTo(19f, 19f)
            }
            "hand" -> {
                path.moveTo(7f, 13f); path.lineTo(7f, 6f)
                path.moveTo(10f, 12f); path.lineTo(10f, 4f)
                path.moveTo(13f, 12f); path.lineTo(13f, 4f)
                path.moveTo(16f, 12f); path.lineTo(16f, 6f)
                path.moveTo(7f, 13f); path.cubicTo(5f, 11f, 4f, 13f, 6f, 17f); path.lineTo(9f, 21f); path.lineTo(16f, 21f)
                path.cubicTo(19f, 21f, 19f, 17f, 19f, 13f); path.lineTo(16f, 12f)
            }
            "lines" -> {
                path.moveTo(4f, 6f); path.lineTo(20f, 6f)
                path.moveTo(4f, 11f); path.lineTo(20f, 11f)
                path.moveTo(4f, 16f); path.lineTo(20f, 16f)
            }
            "export" -> {
                path.moveTo(12f, 15f); path.lineTo(12f, 4f)
                path.moveTo(8f, 8f); path.lineTo(12f, 4f); path.lineTo(16f, 8f)
                path.moveTo(5f, 12f); path.lineTo(5f, 19f); path.lineTo(19f, 19f); path.lineTo(19f, 12f)
            }
            "select" -> {
                p.pathEffect = DashPathEffect(floatArrayOf(3f, 2.5f), 0f)
                c.drawRect(RectF(4f, 4f, 20f, 20f), p)
                p.pathEffect = null
            }
            "note" -> {
                path.moveTo(5f, 4f); path.lineTo(19f, 4f); path.lineTo(19f, 15f); path.lineTo(15f, 19f); path.lineTo(5f, 19f); path.close()
                path.moveTo(15f, 19f); path.lineTo(15f, 15f); path.lineTo(19f, 15f)
            }
            "mind" -> {
                c.drawCircle(12f, 12f, 3f, p)
                c.drawCircle(5f, 6f, 2f, p)
                c.drawCircle(19f, 6f, 2f, p)
                c.drawCircle(5f, 18f, 2f, p)
                c.drawCircle(19f, 18f, 2f, p)
                path.moveTo(9f, 10.5f); path.lineTo(6.8f, 7.6f)
                path.moveTo(15f, 10.5f); path.lineTo(17.2f, 7.6f)
                path.moveTo(9f, 13.5f); path.lineTo(6.8f, 16.4f)
                path.moveTo(15f, 13.5f); path.lineTo(17.2f, 16.4f)
            }
            "crop" -> {
                path.moveTo(4f, 8f); path.lineTo(4f, 4f); path.lineTo(8f, 4f)
                path.moveTo(16f, 4f); path.lineTo(20f, 4f); path.lineTo(20f, 8f)
                path.moveTo(20f, 16f); path.lineTo(20f, 20f); path.lineTo(16f, 20f)
                path.moveTo(8f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 16f)
            }
            "settings" -> {
                c.drawCircle(12f, 12f, 3.5f, p)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    path.moveTo((12 + 6f * Math.cos(a)).toFloat(), (12 + 6f * Math.sin(a)).toFloat())
                    path.lineTo((12 + 9.5f * Math.cos(a)).toFloat(), (12 + 9.5f * Math.sin(a)).toFloat())
                }
            }
            "book" -> {
                path.moveTo(3f, 5f); path.lineTo(9f, 5f); path.quadTo(11f, 5f, 12f, 7f)
                path.quadTo(13f, 5f, 15f, 5f); path.lineTo(21f, 5f); path.lineTo(21f, 19f); path.lineTo(15f, 19f)
                path.quadTo(13f, 19f, 12f, 20f); path.quadTo(11f, 19f, 9f, 19f); path.lineTo(3f, 19f); path.close()
            }
            "shape" -> {
                path.moveTo(4f, 20f); path.lineTo(11f, 20f); path.lineTo(11f, 13f); path.lineTo(4f, 13f); path.close()
                path.moveTo(15f, 4f); path.lineTo(21f, 14f); path.lineTo(9f, 14f); path.close()
            }
            "home" -> {
                path.moveTo(4f, 11f); path.lineTo(12f, 4f); path.lineTo(20f, 11f)
                path.moveTo(6f, 10f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 10f)
            }
            "expand" -> {
                path.moveTo(4f, 9f); path.lineTo(4f, 4f); path.lineTo(9f, 4f)
                path.moveTo(15f, 4f); path.lineTo(20f, 4f); path.lineTo(20f, 9f)
                path.moveTo(20f, 15f); path.lineTo(20f, 20f); path.lineTo(15f, 20f)
                path.moveTo(9f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 15f)
            }
            "shrink" -> {
                path.moveTo(9f, 4f); path.lineTo(9f, 9f); path.lineTo(4f, 9f)
                path.moveTo(20f, 9f); path.lineTo(15f, 9f); path.lineTo(15f, 4f)
                path.moveTo(15f, 20f); path.lineTo(15f, 15f); path.lineTo(20f, 15f)
                path.moveTo(4f, 15f); path.lineTo(9f, 15f); path.lineTo(9f, 20f)
            }
            "grip" -> {
                val style = p.style
                p.style = Paint.Style.FILL
                for (y in listOf(7f, 12f, 17f)) for (x in listOf(9f, 15f)) c.drawCircle(x, y, 1.4f, p)
                p.style = style
            }
            "orient" -> {
                c.drawRoundRect(RectF(4f, 7f, 20f, 17f), 2f, 2f, p)
                path.moveTo(12f, 7f); path.lineTo(12f, 17f)
            }
            "plus" -> { path.moveTo(12f, 5f); path.lineTo(12f, 19f); path.moveTo(5f, 12f); path.lineTo(19f, 12f) }
            "close" -> { path.moveTo(6f, 6f); path.lineTo(18f, 18f); path.moveTo(18f, 6f); path.lineTo(6f, 18f) }
            "trash" -> {
                path.moveTo(4f, 7f); path.lineTo(20f, 7f)
                path.moveTo(9f, 7f); path.lineTo(9f, 4f); path.lineTo(15f, 4f); path.lineTo(15f, 7f)
                path.moveTo(6f, 7f); path.lineTo(7f, 20f); path.lineTo(17f, 20f); path.lineTo(18f, 7f)
            }
            "board" -> {
                c.drawRoundRect(RectF(3f, 4f, 21f, 16f), 2.5f, 2.5f, p)
                path.moveTo(8f, 20f); path.lineTo(16f, 20f)
                path.moveTo(12f, 16f); path.lineTo(12f, 20f)
                path.moveTo(6f, 12f); path.cubicTo(9f, 8f, 11f, 13f, 14f, 9f)
            }
            "pdf" -> {
                path.moveTo(6f, 3f); path.lineTo(14f, 3f); path.lineTo(19f, 8f); path.lineTo(19f, 21f); path.lineTo(6f, 21f); path.close()
                path.moveTo(14f, 3f); path.lineTo(14f, 8f); path.lineTo(19f, 8f)
                path.moveTo(9f, 13f); path.lineTo(16f, 13f)
                path.moveTo(9f, 17f); path.lineTo(16f, 17f)
            }
            "palette" -> {
                c.drawCircle(12f, 12f, 9f, p)
                val style = p.style
                p.style = Paint.Style.FILL
                c.drawCircle(8f, 10f, 1.6f, p)
                c.drawCircle(12f, 7.5f, 1.6f, p)
                c.drawCircle(16f, 10f, 1.6f, p)
                p.style = style
            }
            "library" -> {
                path.moveTo(4f, 4f); path.lineTo(8f, 4f); path.lineTo(8f, 20f); path.lineTo(4f, 20f); path.close()
                path.moveTo(10f, 4f); path.lineTo(14f, 4f); path.lineTo(14f, 20f); path.lineTo(10f, 20f); path.close()
                path.moveTo(16f, 5f); path.lineTo(20f, 4f); path.lineTo(20f, 20f); path.lineTo(16f, 20f)
            }
        }
        c.drawPath(path, p)
    }
}
