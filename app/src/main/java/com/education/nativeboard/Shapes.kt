package com.education.nativeboard

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

// 30 شكل هندسي. كل شكل بيترسم داخل مستطيل (RectF) بنسب من حجمه
object Shapes {
    val NAMES = arrayOf(
        "مستطيل", "مربع", "دايرة", "بيضاوي", "مثلث", "مثلث قائم", "معين", "خماسي", "سداسي", "ثماني",
        "نجمة 5", "نجمة 6", "قلب", "سهم يمين", "سهم يسار", "سهم لأعلى", "سهم لأسفل", "سهم مزدوج", "علامة +", "علامة ×",
        "متوازي أضلاع", "شبه منحرف", "حلقة", "سحابة", "فقاعة كلام", "شيفرون", "مستطيل مستدير", "قوس", "ورقة مطوية", "علامة صح"
    )
    val COUNT: Int get() = NAMES.size

    // الأشكال اللي بتتعرض كخلفية للملاحظة اللاصقة
    val NOTE_SHAPES = intArrayOf(0, 26, 2, 3, 6, 7, 8, 10, 12, 21, 23, 24, 28)

    private fun fp(p: Path, r: RectF, pts: FloatArray, close: Boolean = true) {
        var i = 0
        while (i + 1 < pts.size) {
            val x = r.left + pts[i] * r.width()
            val y = r.top + pts[i + 1] * r.height()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            i += 2
        }
        if (close) p.close()
    }

    // مضلع منتظم بعدد أضلاع n
    private fun regular(n: Int, offset: Double): FloatArray {
        val out = FloatArray(2 * n)
        for (k in 0 until n) {
            val a = -Math.PI / 2 + offset + k * 2 * Math.PI / n
            out[2 * k] = (0.5 + 0.5 * Math.cos(a)).toFloat()
            out[2 * k + 1] = (0.5 + 0.5 * Math.sin(a)).toFloat()
        }
        return out
    }

    // نجمة بعدد رؤوس n ونسبة الداخل
    private fun star(n: Int, inner: Float): FloatArray {
        val out = FloatArray(4 * n)
        for (k in 0 until 2 * n) {
            val rad = if (k % 2 == 0) 0.5 else 0.5 * inner
            val a = -Math.PI / 2 + k * Math.PI / n
            out[2 * k] = (0.5 + rad * Math.cos(a)).toFloat()
            out[2 * k + 1] = (0.5 + rad * Math.sin(a)).toFloat()
        }
        return out
    }

    fun path(type: Int, r: RectF): Path {
        val p = Path()
        val w = r.width()
        val h = r.height()
        val cx = r.centerX()
        val cy = r.centerY()
        when (type) {
            0 -> p.addRect(r, Path.Direction.CW)
            1 -> {
                val s = minOf(w, h) / 2f
                p.addRect(cx - s, cy - s, cx + s, cy + s, Path.Direction.CW)
            }
            2 -> {
                val s = minOf(w, h) / 2f
                p.addOval(cx - s, cy - s, cx + s, cy + s, Path.Direction.CW)
            }
            3 -> p.addOval(r, Path.Direction.CW)
            4 -> fp(p, r, floatArrayOf(0.5f, 0f, 1f, 1f, 0f, 1f))
            5 -> fp(p, r, floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f))
            6 -> fp(p, r, floatArrayOf(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f))
            7 -> fp(p, r, regular(5, 0.0))
            8 -> fp(p, r, regular(6, 0.0))
            9 -> fp(p, r, regular(8, Math.PI / 8))
            10 -> fp(p, r, star(5, 0.45f))
            11 -> fp(p, r, star(6, 0.6f))
            12 -> {
                val t = r.top
                val l = r.left
                val rr = r.right
                val b = r.bottom
                p.moveTo(cx, t + h * 0.3f)
                p.cubicTo(cx, t, l, t, l, t + h * 0.35f)
                p.cubicTo(l, t + h * 0.65f, cx, t + h * 0.8f, cx, b)
                p.cubicTo(cx, t + h * 0.8f, rr, t + h * 0.65f, rr, t + h * 0.35f)
                p.cubicTo(rr, t, cx, t, cx, t + h * 0.3f)
                p.close()
            }
            13 -> fp(p, r, floatArrayOf(0f, 0.3f, 0.5f, 0.3f, 0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.7f, 0f, 0.7f))
            14 -> fp(p, r, floatArrayOf(1f, 0.3f, 0.5f, 0.3f, 0.5f, 0f, 0f, 0.5f, 0.5f, 1f, 0.5f, 0.7f, 1f, 0.7f))
            15 -> fp(p, r, floatArrayOf(0.3f, 1f, 0.7f, 1f, 0.7f, 0.5f, 1f, 0.5f, 0.5f, 0f, 0f, 0.5f, 0.3f, 0.5f))
            16 -> fp(p, r, floatArrayOf(0.3f, 0f, 0.7f, 0f, 0.7f, 0.5f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f, 0.3f, 0.5f))
            17 -> fp(p, r, floatArrayOf(0f, 0.5f, 0.3f, 0f, 0.3f, 0.3f, 0.7f, 0.3f, 0.7f, 0f, 1f, 0.5f, 0.7f, 1f, 0.7f, 0.7f, 0.3f, 0.7f, 0.3f, 1f))
            18 -> fp(p, r, floatArrayOf(0.35f, 0f, 0.65f, 0f, 0.65f, 0.35f, 1f, 0.35f, 1f, 0.65f, 0.65f, 0.65f, 0.65f, 1f, 0.35f, 1f, 0.35f, 0.65f, 0f, 0.65f, 0f, 0.35f, 0.35f, 0.35f))
            19 -> {
                val m = Matrix()
                m.setRotate(45f, cx, cy)
                p.addPath(path(18, r))
                p.transform(m)
            }
            20 -> fp(p, r, floatArrayOf(0.2f, 0f, 1f, 0f, 0.8f, 1f, 0f, 1f))
            21 -> fp(p, r, floatArrayOf(0.2f, 0f, 0.8f, 0f, 1f, 1f, 0f, 1f))
            22 -> {
                p.addOval(r, Path.Direction.CW)
                p.addOval(RectF(r.left + 0.25f * w, r.top + 0.25f * h, r.right - 0.25f * w, r.bottom - 0.25f * h), Path.Direction.CCW)
                p.fillType = Path.FillType.EVEN_ODD
            }
            23 -> {
                val l = r.left
                val t = r.top
                p.addOval(RectF(l, t + 0.4f * h, l + 0.5f * w, t + 0.95f * h), Path.Direction.CW)
                p.addOval(RectF(l + 0.2f * w, t + 0.12f * h, l + 0.72f * w, t + 0.75f * h), Path.Direction.CW)
                p.addOval(RectF(l + 0.5f * w, t + 0.3f * h, r.right, t + 0.95f * h), Path.Direction.CW)
                p.addRect(RectF(l + 0.25f * w, t + 0.6f * h, r.right - 0.1f * w, t + 0.95f * h), Path.Direction.CW)
            }
            24 -> {
                p.addRoundRect(RectF(r.left, r.top, r.right, r.top + 0.75f * h), 0.15f * w, 0.15f * h, Path.Direction.CW)
                fp(p, r, floatArrayOf(0.2f, 0.7f, 0.35f, 1f, 0.5f, 0.7f))
            }
            25 -> fp(p, r, floatArrayOf(0f, 1f, 0.5f, 0f, 1f, 1f, 1f, 0.7f, 0.5f, 0.3f, 0f, 0.7f))
            26 -> p.addRoundRect(r, 0.25f * w, 0.25f * h, Path.Direction.CW)
            27 -> p.addArc(r, 180f, 180f)
            28 -> fp(p, r, floatArrayOf(0f, 0f, 1f, 0f, 1f, 0.75f, 0.75f, 1f, 0f, 1f))
            else -> fp(p, r, floatArrayOf(0f, 0.5f, 0.35f, 1f, 1f, 0f), false)
        }
        return p
    }
}
