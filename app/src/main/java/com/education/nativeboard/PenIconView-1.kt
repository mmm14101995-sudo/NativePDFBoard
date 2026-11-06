package com.education.nativeboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

// قلم مرسوم بشكل حقيقي: جسم ملون وسن حسب نوعه (حبر، فاين، ماركر، تظليل، رصاص)
class PenIconView(ctx: Context, private val kind: String, private val body: Int) : View(ctx) {
    var selected: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun poly(c: Canvas, pts: List<Pair<Float, Float>>) {
        path.reset()
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) path.lineTo(pts[i].first, pts[i].second)
        path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val bw = w * 0.5f
        val left = cx - bw / 2f
        val right = cx + bw / 2f
        val tipTop = h * 0.06f
        val ringTop = h * 0.40f
        val ringBot = ringTop + h * 0.05f
        val bodyBot = h * 0.94f

        if (selected) {
            p.reset()
            p.color = 0x3338BDF8
            c.drawRoundRect(RectF(0f, 0f, w, h), w * 0.3f, w * 0.3f, p)
        }

        // السن حسب نوع القلم
        p.reset()
        p.isAntiAlias = true
        p.color = if (kind == "pencil") 0xFFFDE68A.toInt() else body
        when (kind) {
            "fineliner" -> poly(c, listOf(cx - bw * 0.04f to tipTop, cx + bw * 0.04f to tipTop, cx + bw * 0.2f to ringTop, cx - bw * 0.2f to ringTop))
            "marker" -> poly(c, listOf(left to tipTop + h * 0.04f, right to tipTop + h * 0.04f, right to ringTop, left to ringTop))
            "highlighter" -> poly(c, listOf(cx - bw * 0.4f to tipTop, cx + bw * 0.4f to tipTop, right to ringTop, left to ringTop))
            "pencil" -> {
                poly(c, listOf(cx - bw * 0.12f to tipTop, cx + bw * 0.12f to tipTop, right to ringTop, left to ringTop))
                p.color = 0xFF1F2937.toInt()
                poly(c, listOf(cx - bw * 0.05f to tipTop, cx + bw * 0.05f to tipTop, cx to tipTop - h * 0.02f))
            }
            else -> poly(c, listOf(cx - bw * 0.08f to tipTop, cx + bw * 0.08f to tipTop, right to ringTop, left to ringTop))
        }

        // الحلقة الفضية
        p.color = 0xFFCBD5E1.toInt()
        c.drawRect(left, ringTop, right, ringBot, p)

        // جسم القلم
        p.color = body
        c.drawRoundRect(RectF(left, ringBot, right, bodyBot), bw * 0.2f, bw * 0.2f, p)

        // لمعة على الجسم
        p.color = 0x44FFFFFF
        c.drawRect(left + bw * 0.18f, ringBot + h * 0.04f, left + bw * 0.32f, bodyBot - h * 0.06f, p)
    }
}
