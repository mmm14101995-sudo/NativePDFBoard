package com.education.nativeboard

import android.content.Context
import android.graphics.Canvas
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

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        val s = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        p.color = tint
        p.strokeWidth = 2f
        Icons.draw(c, name, p)
        c.restore()
    }
}

object Icons {
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
