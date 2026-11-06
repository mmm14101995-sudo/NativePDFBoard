package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

// سهم صغير بيتقلب حسب الحالة (إخفاء أو إظهار الشريط)
class ChevronView(ctx: Context) : View(ctx) {
    var down: Boolean = true
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.strokeWidth = w * 0.11f
        val path = Path()
        if (down) {
            path.moveTo(w * 0.3f, h * 0.4f)
            path.lineTo(w * 0.5f, h * 0.6f)
            path.lineTo(w * 0.7f, h * 0.4f)
        } else {
            path.moveTo(w * 0.3f, h * 0.6f)
            path.lineTo(w * 0.5f, h * 0.4f)
            path.lineTo(w * 0.7f, h * 0.6f)
        }
        c.drawPath(path, p)
    }
}

object Glass {
    // دايرة بتدرج شفاف (لون أو لون وإطار)
    fun oval(c: Context, color: Int, ring: Int = 0, ringDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (ringDp > 0) setStroke(Ui.dp(c, ringDp), ring)
        }

    // زرار دايري زجاجي فيه سهم، بيرجّع الزرار والسهم عشان نقلبه
    fun glassButton(c: Context, sizeDp: Int, onClick: () -> Unit): Pair<FrameLayout, ChevronView> {
        val chev = ChevronView(c)
        val box = FrameLayout(c).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66FFFFFF, 0x1AFFFFFF)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(Ui.dp(c, 1), 0x88FFFFFF.toInt())
            }
            elevation = Ui.dp(c, 6).toFloat()
            addView(chev, FrameLayout.LayoutParams(Ui.dp(c, sizeDp / 2), Ui.dp(c, sizeDp / 2), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp))
            setOnClickListener { onClick() }
        }
        return Pair(box, chev)
    }

    // نافذة ألوان مربعة: كل لون مربع بيتضغط ويتقفل
    fun swatchDialog(a: Activity, title: String, colors: IntArray, current: Int, onPick: (Int) -> Unit) {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8))
        }
        val dlg = AlertDialog.Builder(a)
            .setTitle(title)
            .setView(col)
            .setNegativeButton("إغلاق", null)
            .create()
        colors.toList().chunked(5).forEach { chunk ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { color ->
                val sw = View(a).apply {
                    background = oval(a, color, if (color == current) Color.WHITE else 0x44000000, if (color == current) 3 else 1)
                    setOnClickListener {
                        onPick(color)
                        dlg.dismiss()
                    }
                }
                row.addView(sw, LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)).apply {
                    setMargins(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5))
                })
            }
            col.addView(row)
        }
        dlg.show()
    }
}
