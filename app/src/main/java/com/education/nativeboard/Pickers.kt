package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// نوافذ اختيار بصرية: كروت مستطيلة، كل كارت فيه معاينة
object Pickers {

    // شبكة كروت: كل كارت = معاينة + اسم، والضغط بيختار
    fun grid(act: Activity, title: String, cards: List<Pair<View, String>>, onPick: (Int) -> Unit) {
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(act, 10)
        col.setPadding(pad, pad, pad, pad)
        val holder = arrayOfNulls<AlertDialog>(1)
        var idx = 0
        for (rowCards in cards.chunked(2)) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (pair in rowCards) {
                val i = idx
                idx++
                val card = LinearLayout(act)
                card.orientation = LinearLayout.VERTICAL
                card.gravity = Gravity.CENTER_HORIZONTAL
                card.setPadding(pad, pad, pad, pad)
                card.background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(act, 16).toFloat()
                    setColor(0xFFF8FAFC.toInt())
                    setStroke(Ui.dp(act, 1), 0x33334155)
                }
                card.addView(pair.first, LinearLayout.LayoutParams(Ui.dp(act, 130), Ui.dp(act, 96)))
                card.addView(TextView(act).apply {
                    text = pair.second
                    textSize = 12f
                    setTextColor(0xFF0F172A.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(act, 6), 0, 0)
                })
                card.setOnClickListener {
                    holder[0]?.dismiss()
                    onPick(i)
                }
                row.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4))
                })
            }
            if (rowCards.size == 1) row.addView(View(act), LinearLayout.LayoutParams(0, 0, 1f))
            col.addView(row)
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle(title)
            .setView(ScrollView(act).apply { addView(col) })
            .setNegativeButton("إغلاق", null)
            .create()
        holder[0] = dlg
        dlg.show()
    }

    // معاينة لأيقونة في كارت
    fun iconPreview(ctx: Context, icon: String, tint: Int): View = FrameLayout(ctx).apply {
        background = GradientDrawable().apply {
            cornerRadius = Ui.dp(ctx, 12).toFloat()
            setColor(0x141E293B)
        }
        addView(IconView(ctx, icon, tint), FrameLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 40), Gravity.CENTER))
    }

    // صورة مصغرة من المكتبة
    fun imagePreview(ctx: Context, key: String): View = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(0xFFFFFFFF.toInt())
        val b = SciLibrary.thumb(ctx, key)
        if (b != null) setImageBitmap(b)
    }

    // معاينة التسطير (سطور / مربعات / نقط)
    fun patternPreview(ctx: Context, kind: Int, level: Int): View = PatternPreview(ctx, kind, level)

    // معاينة شكل الخريطة الذهنية (مرسومة، من غير كلام)
    fun mindPreview(ctx: Context, nodes: List<MindNode>): View = MindPreview(ctx, nodes)
}

// رسم شكل الخريطة الذهنية في كارت الاختيار
class MindPreview(ctx: Context, private val nodes: List<MindNode>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0xFF94A3B8.toInt()
        for (n in nodes) {
            if (n.parent in nodes.indices) {
                val a = nodes[n.parent]
                c.drawLine(a.x * w, a.y * h, n.x * w, n.y * h, p)
            }
        }
        for (i in nodes.indices) {
            val n = nodes[i]
            val bw = if (i == 0) w * 0.26f else w * 0.17f
            val bh = if (i == 0) h * 0.24f else h * 0.17f
            val r = RectF(n.x * w - bw / 2, n.y * h - bh / 2, n.x * w + bw / 2, n.y * h + bh / 2)
            p.style = Paint.Style.FILL
            p.color = if (i == 0) 0xFF2563EB.toInt() else 0xFFFFFFFF.toInt()
            c.drawRoundRect(r, 8f, 8f, p)
            if (i != 0) {
                p.style = Paint.Style.STROKE
                p.color = 0xFF94A3B8.toInt()
                c.drawRoundRect(r, 8f, 8f, p)
            }
        }
    }
}

// رسم التسطير في كارت الاختيار
class PatternPreview(ctx: Context, private val kind: Int, private val level: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        c.drawColor(0xFFFFFFFF.toInt())
        if (kind == 0) return
        val cells = intArrayOf(10, 6, 4)[level.coerceIn(0, 2)]
        val s = w / cells
        p.color = 0xFF64748B.toInt()
        p.strokeWidth = 1.5f
        when (kind) {
            1 -> {
                p.style = Paint.Style.STROKE
                var y = s
                while (y < h) { c.drawLine(0f, y, w, y, p); y += s }
            }
            2 -> {
                p.style = Paint.Style.STROKE
                var y = s
                while (y < h) { c.drawLine(0f, y, w, y, p); y += s }
                var x = s
                while (x < w) { c.drawLine(x, 0f, x, h, p); x += s }
            }
            3 -> {
                p.style = Paint.Style.FILL
                var y = s
                while (y < h) {
                    var x = s
                    while (x < w) { c.drawCircle(x, y, 2.5f, p); x += s }
                    y += s
                }
            }
        }
    }
}
