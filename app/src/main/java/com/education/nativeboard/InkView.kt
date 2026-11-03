package com.education.nativeboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

// سطح الرسم: بيعرض الصفحة (PDF أو خلفية) والكتابة فوقها، وبيتعامل مع اللمس
class InkView(ctx: Context) : View(ctx) {
    var pageBitmap: Bitmap? = null
    var bgColor: Int = Color.WHITE
    var strokes: ArrayList<Stroke> = ArrayList()
    var penIndex = 0
    var ink: Int = Color.BLACK
    var sizeLevel = 6f
    var eraserMode = false
    var fingerDraws = true

    // نداءات للشاشة الأب: قبل أي تعديل (عشان التراجع)، وبعد أي تعديل (عشان الحفظ)
    var onBeforeChange: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var current: Stroke? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val pageRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surface = Paint().apply { color = Color.parseColor("#1d212c") }

    private val points = HashMap<Int, PointF>()
    private var mode = 0 // 0 لا شيء، 1 رسم، 2 ممحاة، 3 تحريك الصفحة، 4 تكبير
    private var activeId = -1
    private var lastStylusAt = 0L
    private var pinchD = 1f
    private var pinchZoom = 1f
    private var pinchPre = PointF()
    private var lastEX = 0f
    private var lastEY = 0f

    fun snapshot(): ArrayList<Stroke> = ArrayList(strokes)

    fun restore(list: ArrayList<Stroke>) {
        strokes = list
        invalidate()
        onChanged?.invoke()
    }

    private fun computePageRect() {
        val ratio = pageBitmap?.let { it.height.toFloat() / it.width } ?: 1.414f
        val maxW = width - 24f
        val maxH = height - 24f
        var w = maxW
        var h = w * ratio
        if (h > maxH) {
            h = maxH
            w = h / ratio
        }
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        pageRect.set(l, t, l + w, t + h)
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), surface)
        computePageRect()
        c.save()
        c.translate(panX, panY)
        c.scale(zoom, zoom)
        paint.reset()
        paint.color = bgColor
        c.drawRect(pageRect, paint)
        pageBitmap?.let { c.drawBitmap(it, null, pageRect, null) }
        val all = ArrayList(strokes)
        current?.let { all.add(it) }
        // التظليل تحت، والكتابة فوقه
        for (st in all) if (Pens.ALL[st.pen].highlight) drawStroke(c, st)
        for (st in all) if (!Pens.ALL[st.pen].highlight) drawStroke(c, st)
        c.restore()
    }

    private fun strokeWidth(st: Stroke): Float =
        (st.size * Pens.ALL[st.pen].width * pageRect.width() / 700f).coerceAtLeast(1f)

    private fun drawStroke(c: Canvas, st: Stroke) {
        if (st.pts.size < 2) return
        val pen = Pens.ALL[st.pen]
        val w = strokeWidth(st)
        paint.reset()
        paint.color = st.color
        paint.alpha = pen.alpha
        if (st.pts.size == 2) {
            // نقطة واحدة
            paint.style = Paint.Style.FILL
            c.drawCircle(pageRect.left + st.pts[0] * pageRect.width(), pageRect.top + st.pts[1] * pageRect.height(), w / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = pen.cap
        paint.strokeJoin = Paint.Join.ROUND
        if (pen.dash > 0f) paint.pathEffect = DashPathEffect(floatArrayOf(pen.dash * w, 2f * w), 0f)
        if (pen.glow > 0f) paint.setShadowLayer(pen.glow * w, 0f, 0f, st.color)
        val path = Path()
        var i = 0
        while (i + 1 < st.pts.size) {
            val x = pageRect.left + st.pts[i] * pageRect.width()
            val y = pageRect.top + st.pts[i + 1] * pageRect.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        c.drawPath(path, paint)
    }

    // تحويل نقطة الشاشة لنسبة من الصفحة
    private fun toNorm(x: Float, y: Float): FloatArray {
        if (pageRect.width() <= 0f) computePageRect()
        val px = (x - panX) / zoom
        val py = (y - panY) / zoom
        return floatArrayOf((px - pageRect.left) / pageRect.width(), (py - pageRect.top) / pageRect.height())
    }

    private fun beginStroke(id: Int, x: Float, y: Float) {
        onBeforeChange?.invoke()
        activeId = id
        lastEX = x
        lastEY = y
        if (eraserMode) {
            mode = 2
            eraseAt(x, y)
            invalidate()
            return
        }
        mode = 1
        val n = toNorm(x, y)
        current = Stroke(penIndex, ink, sizeLevel, arrayListOf(n[0], n[1]))
        invalidate()
    }

    private fun addPoint(x: Float, y: Float) {
        val st = current ?: return
        val n = toNorm(x, y)
        val lx = st.pts[st.pts.size - 2]
        val ly = st.pts[st.pts.size - 1]
        val dx = (n[0] - lx) * pageRect.width()
        val dy = (n[1] - ly) * pageRect.height()
        if (dx * dx + dy * dy < 2f) return
        st.pts.add(n[0])
        st.pts.add(n[1])
        invalidate()
    }

    private fun commitStroke() {
        val st = current
        current = null
        mode = 0
        if (st != null && st.pts.size >= 2) {
            strokes.add(st)
            onChanged?.invoke()
        }
        invalidate()
    }

    private fun cancelStroke() {
        current = null
        if (mode == 1) mode = 0
    }

    // الممحاة: بتمسح جزء من الخط اللي بتلمسه وتقسّمه، والباقي بيفضل
    private fun eraseAt(x: Float, y: Float) {
        val n = toNorm(x, y)
        val r = 0.012f + sizeLevel * 0.003f
        val ratio = if (pageRect.width() > 0f) pageRect.height() / pageRect.width() else 1.414f
        fun near(ax: Float, ay: Float): Boolean {
            val dx = ax - n[0]
            val dy = (ay - n[1]) * ratio
            return dx * dx + dy * dy <= r * r
        }
        val out = ArrayList<Stroke>()
        var changed = false
        for (st in strokes) {
            var hit = false
            var i = 0
            while (i + 1 < st.pts.size) {
                if (near(st.pts[i], st.pts[i + 1])) { hit = true; break }
                i += 2
            }
            if (!hit) {
                out.add(st)
                continue
            }
            changed = true
            var run = ArrayList<Float>()
            i = 0
            while (i + 1 < st.pts.size) {
                if (near(st.pts[i], st.pts[i + 1])) {
                    if (run.size >= 4) out.add(Stroke(st.pen, st.color, st.size, run))
                    run = ArrayList()
                } else {
                    run.add(st.pts[i])
                    run.add(st.pts[i + 1])
                }
                i += 2
            }
            if (run.size >= 4) out.add(Stroke(st.pen, st.color, st.size, run))
        }
        if (changed) {
            strokes = out
            onChanged?.invoke()
        }
    }

    private fun dist(a: PointF, b: PointF): Float = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun clampPan() {
        if (zoom <= 1.001f) {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }

    private fun startPinch() {
        mode = 4
        cancelStroke()
        val vals = points.values.toList()
        val a = vals[0]
        val b = vals[1]
        pinchD = dist(a, b).coerceAtLeast(1f)
        pinchZoom = zoom
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        pinchPre = PointF((mx - panX) / zoom, (my - panY) / zoom)
    }

    private fun updatePinch(e: MotionEvent) {
        for (i in 0 until e.pointerCount) points[e.getPointerId(i)]?.set(e.getX(i), e.getY(i))
        val vals = points.values.toList()
        if (vals.size < 2) return
        val a = vals[0]
        val b = vals[1]
        val d = dist(a, b).coerceAtLeast(1f)
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        zoom = (pinchZoom * d / pinchD).coerceIn(1f, 5f)
        panX = mx - pinchPre.x * zoom
        panY = my - pinchPre.y * zoom
        clampPan()
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val stylus = e.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS
                if (stylus) {
                    lastStylusAt = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastStylusAt < 1200) {
                    return true // رفض كف اليد لما القلم يكون شغال
                }
                points[id] = PointF(e.getX(i), e.getY(i))
                if (points.size >= 2) {
                    startPinch()
                } else if (stylus || fingerDraws || e.getToolType(i) == MotionEvent.TOOL_TYPE_MOUSE) {
                    beginStroke(id, e.getX(i), e.getY(i))
                } else {
                    mode = 3
                    activeId = id
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == 4) {
                    updatePinch(e)
                    return true
                }
                val idx = e.findPointerIndex(activeId)
                if (idx < 0) return true
                val x = e.getX(idx)
                val y = e.getY(idx)
                when (mode) {
                    1 -> {
                        for (h in 0 until e.historySize) addPoint(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
                        addPoint(x, y)
                    }
                    2 -> {
                        val steps = 6
                        for (k in 1..steps) {
                            val t = k / steps.toFloat()
                            eraseAt(lastEX + (x - lastEX) * t, lastEY + (y - lastEY) * t)
                        }
                        lastEX = x
                        lastEY = y
                        invalidate()
                    }
                    3 -> {
                        val p = points[activeId]
                        if (p != null) {
                            panX += x - p.x
                            panY += y - p.y
                            p.set(x, y)
                            clampPan()
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                points.remove(id)
                if (mode == 1 && id == activeId) commitStroke()
                if (mode == 2 && id == activeId) mode = 0
                if (mode == 3 && id == activeId) mode = 0
                if (mode == 4 && points.size < 2) mode = 0
                if (points.isEmpty()) mode = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelStroke()
                points.clear()
                mode = 0
                invalidate()
            }
        }
        return true
    }
}
