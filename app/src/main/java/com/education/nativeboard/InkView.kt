package com.education.nativeboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
    // نصف قطر الممحاة (نسبة من الصفحة) مستقل عن سُمك القلم
    var eraserRadius = 0.025f
    var fingerDraws = true
    var handMode = false
    var pattern = 0
    var patternLevel = 1
    private var exportMode = false
    // العناصر فوق الصفحة: صور وملاحظات وخرايط، وتحديد وقص
    var objects: ArrayList<PageObj> = ArrayList()
    var imageLoader: ((String) -> Bitmap?)? = null
    var selectMode = false
    var cropMode = false
    var selected = -1
    // الكتابة المحددة (الخط نفسه) وإجراء الضغط المطوّل عليها
    var selStroke = -1
    var onStrokeLongPress: (() -> Unit)? = null
    var onObjectsChanged: (() -> Unit)? = null
    var onObjectLongPress: ((Int) -> Unit)? = null
    var onCropDone: ((Float, Float, Float, Float) -> Unit)? = null
    private val bmpCache = HashMap<String, Bitmap?>()
    private var objDrag = 0
    private var objMoved = false
    private var objDownAt = 0L
    private var objLastX = 0f
    private var objLastY = 0f
    private var objSX = 0f
    private var objSY = 0f
    private var cropping = false
    private var cropX0 = 0f
    private var cropY0 = 0f
    private var cropX1 = 0f
    private var cropY1 = 0f

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

    // الصفحة المسموح فيها بالكتابة بتملأ الشاشة كلها دايماً، والتكبير بيتعمل على الصفحة كلها زي الـ PDF
    // السبورة الفاضية بتملأ الشاشة كلها. صفحة الـ PDF بتتعرض بنسبتها من غير ما تتشوّه
    private fun computePageRect() {
        if (width <= 0 || height <= 0) return
        val bmp = pageBitmap
        if (bmp == null) {
            pageRect.set(0f, 0f, width.toFloat(), height.toFloat())
            return
        }
        val ratio = bmp.height.toFloat() / bmp.width
        var w = width.toFloat()
        var h = w * ratio
        if (h > height) {
            h = height.toFloat()
            w = h / ratio
        }
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        pageRect.set(l, t, l + w, t + h)
    }

    override fun onDraw(c: Canvas) {
        if (!exportMode) {
            // خلفية السبورة بتملأ الشاشة كلها، والصفحة بتتصغر جواها مع الكتابة
            paint.reset()
            paint.color = bgColor
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        computePageRect()
        c.save()
        c.translate(panX, panY)
        c.scale(zoom, zoom)
        paint.reset()
        paint.color = bgColor
        c.drawRect(pageRect, paint)
        pageBitmap?.let { c.drawBitmap(it, null, pageRect, null) }
        drawPattern(c)
        // لمعة زجاجية خفيفة على الصفحة
        paint.reset()
        paint.shader = LinearGradient(pageRect.left, pageRect.top, pageRect.left, pageRect.top + pageRect.height() * 0.35f, intArrayOf(0x1CFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(pageRect, paint)
        paint.shader = null
        drawObjects(c)
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
        if (pen.brush && st.pts.size > 3) {
            drawBrush(c, st, w, pen)
            return
        }
        if (pen.highlight) {
            paint.alpha = 255
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        if (pen.grain) {
            // الرصاص: 3 خطوط رفيعة متجاورة بشفافية، فبيطلع شكل الجرافيت
            val base = paint.alpha
            paint.strokeWidth = w * 0.35f
            paint.alpha = (base * 0.55f).toInt()
            for (k in -1..1) {
                c.save()
                c.translate(k * w * 0.3f, k * w * 0.15f)
                c.drawPath(path, paint)
                c.restore()
            }
            return
        }
        c.drawPath(path, paint)
    }

    // أقرب كتابة لنقطة اللمس (بالمسافة على الشاشة)
    private fun hitStroke(nx: Float, ny: Float): Int {
        val w = pageRect.width()
        val h = pageRect.height()
        if (w <= 0f || h <= 0f) return -1
        val tol = 22f / zoom
        val px = nx * w
        val py = ny * h
        for (i in strokes.indices.reversed()) {
            val pts = strokes[i].pts
            var k = 0
            while (k + 1 < pts.size) {
                val dx = pts[k] * w - px
                val dy = pts[k + 1] * h - py
                if (dx * dx + dy * dy <= tol * tol) return i
                k += 2
            }
        }
        return -1
    }

    // حذف الكتابة المحددة (بيتسجّل في التراجع)
    fun deleteSelectedStroke() {
        if (selStroke !in strokes.indices) return
        onBeforeChange?.invoke()
        strokes.removeAt(selStroke)
        selStroke = -1
        invalidate()
        onChanged?.invoke()
    }

    // ---------- العناصر فوق الصفحة ----------

    private fun objRect(o: PageObj): RectF = RectF(
        pageRect.left + o.x * pageRect.width(),
        pageRect.top + o.y * pageRect.height(),
        pageRect.left + (o.x + o.w) * pageRect.width(),
        pageRect.top + (o.y + o.h) * pageRect.height()
    )

    private fun drawObjects(c: Canvas) {
        if (pageRect.width() <= 0f) return
        for (i in objects.indices) {
            val o = objects[i]
            val r = objRect(o)
            when (o.kind) {
                "image" -> drawImageObj(c, o, r)
                "note" -> drawNoteObj(c, o, r)
                "mind" -> drawMindObj(c, o, r)
                "shape" -> drawShapeObj(c, o, r)
            }
            if (i == selected) {
                paint.reset()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f / zoom
                paint.color = 0xFF2563EB.toInt()
                paint.pathEffect = DashPathEffect(floatArrayOf(10f / zoom, 6f / zoom), 0f)
                c.drawRect(r, paint)
                paint.pathEffect = null
                paint.style = Paint.Style.FILL
                c.drawCircle(r.right, r.bottom, 9f / zoom, paint)
            }
        }
        if (selStroke in strokes.indices && strokes[selStroke].pts.size >= 2) {
            val pts = strokes[selStroke].pts
            var minX = 1f
            var minY = 1f
            var maxX = 0f
            var maxY = 0f
            var k = 0
            while (k + 1 < pts.size) {
                minX = minOf(minX, pts[k])
                maxX = maxOf(maxX, pts[k])
                minY = minOf(minY, pts[k + 1])
                maxY = maxOf(maxY, pts[k + 1])
                k += 2
            }
            val box = RectF(
                pageRect.left + minX * pageRect.width(), pageRect.top + minY * pageRect.height(),
                pageRect.left + maxX * pageRect.width(), pageRect.top + maxY * pageRect.height()
            )
            paint.reset()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f / zoom
            paint.color = 0xFF2563EB.toInt()
            paint.pathEffect = DashPathEffect(floatArrayOf(8f / zoom, 6f / zoom), 0f)
            c.drawRect(box, paint)
            paint.pathEffect = null
        }
        if (cropping) {
            val r = RectF(
                pageRect.left + minOf(cropX0, cropX1) * pageRect.width(),
                pageRect.top + minOf(cropY0, cropY1) * pageRect.height(),
                pageRect.left + maxOf(cropX0, cropX1) * pageRect.width(),
                pageRect.top + maxOf(cropY0, cropY1) * pageRect.height()
            )
            paint.reset()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f / zoom
            paint.color = 0xFF2563EB.toInt()
            paint.pathEffect = DashPathEffect(floatArrayOf(8f / zoom, 6f / zoom), 0f)
            c.drawRect(r, paint)
            paint.pathEffect = null
        }
    }

    private fun drawImageObj(c: Canvas, o: PageObj, r: RectF) {
        val bmp = bmpCache.getOrPut(o.key) { imageLoader?.invoke(o.key) } ?: return
        paint.reset()
        c.drawBitmap(bmp, null, r, paint)
    }

    private fun drawNoteObj(c: Canvas, o: PageObj, r: RectF) {
        val pw = pageRect.width()
        paint.reset()
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFEF08A.toInt()
        val nt = o.key.toIntOrNull()
        if (nt != null) c.drawPath(Shapes.path(nt, r), paint) else c.drawRoundRect(r, pw * 0.01f, pw * 0.01f, paint)
        val pad = pw * 0.012f
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = 0xFF1E293B.toInt()
        tp.textSize = pw * 0.028f
        val wrap = (r.width() - 2 * pad).toInt().coerceAtLeast(10)
        val layout = StaticLayout.Builder.obtain(o.text, 0, o.text.length, tp, wrap).build()
        c.save()
        c.translate(r.left + pad, r.top + pad)
        c.clipRect(0f, 0f, wrap.toFloat(), r.height() - 2 * pad)
        layout.draw(c)
        c.restore()
    }

    // الشكل الهندسي: key = "نوع:لون"
    private fun drawShapeObj(c: Canvas, o: PageObj, r: RectF) {
        val parts = o.key.split(":")
        val type = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val col = parts.getOrNull(1)?.toIntOrNull() ?: 0xFF2563EB.toInt()
        val path = Shapes.path(type, r)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = (col and 0x00FFFFFF) or 0x33000000
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = pageRect.width() * 0.004f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = col
        c.drawPath(path, paint)
    }

    private fun drawMindObj(c: Canvas, o: PageObj, r: RectF) {
        val nodes = MindMap.decode(o.text)
        if (nodes.isEmpty()) return
        val pw = pageRect.width()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = pw * 0.022f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        lp.style = Paint.Style.STROKE
        lp.strokeWidth = pw * 0.003f
        lp.color = 0xFF64748B.toInt()
        val pad = pw * 0.012f
        val centers = nodes.map { PointF(r.left + it.x * r.width(), r.top + it.y * r.height()) }
        for (i in nodes.indices) {
            val p = nodes[i].parent
            if (p in nodes.indices) c.drawLine(centers[p].x, centers[p].y, centers[i].x, centers[i].y, lp)
        }
        for (i in nodes.indices) {
            val pt = centers[i]
            val raw = if (nodes[i].text.isBlank()) "…" else nodes[i].text
            val label = TextUtils.ellipsize(raw, tp, r.width() * 0.3f, TextUtils.TruncateAt.END).toString()
            val tw = tp.measureText(label)
            val fm = tp.fontMetrics
            val box = RectF(pt.x - tw / 2 - pad, pt.y - tp.textSize / 2 - pad, pt.x + tw / 2 + pad, pt.y + tp.textSize / 2 + pad)
            paint.reset()
            paint.style = Paint.Style.FILL
            paint.color = if (i == 0) 0xFF2563EB.toInt() else 0xFFFFFFFF.toInt()
            c.drawRoundRect(box, pad * 2, pad * 2, paint)
            tp.color = if (i == 0) Color.WHITE else 0xFF0F172A.toInt()
            c.drawText(label, pt.x - tw / 2, pt.y - (fm.ascent + fm.descent) / 2, tp)
        }
    }

    // اللمس في وضع التحديد أو القص: تحريك، تكبير من الزاوية، تحديد منطقة، أو تحريك الصفحة
    private fun objectTouch(e: MotionEvent): Boolean {
        if (!selectMode && !cropMode) return false
        if (e.pointerCount > 1) {
            objDrag = 0
            cropping = false
            return false
        }
        val n = toNorm(e.x, e.y)
        val nx = n[0]
        val ny = n[1]
        val tol = (24f / zoom) / pageRect.width().coerceAtLeast(1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                objSX = e.x
                objSY = e.y
                objLastX = nx
                objLastY = ny
                objMoved = false
                objDownAt = System.currentTimeMillis()
                if (cropMode) {
                    cropping = true
                    cropX0 = nx
                    cropY0 = ny
                    cropX1 = nx
                    cropY1 = ny
                    invalidate()
                    return true
                }
                selStroke = -1
                val sel = objects.getOrNull(selected)
                if (sel != null && Math.abs(nx - (sel.x + sel.w)) < tol && Math.abs(ny - (sel.y + sel.h)) < tol) {
                    objDrag = 2
                } else {
                    selected = -1
                    for (i in objects.indices.reversed()) {
                        val o = objects[i]
                        if (nx >= o.x && nx <= o.x + o.w && ny >= o.y && ny <= o.y + o.h) {
                            selected = i
                            break
                        }
                    }
                    if (selected >= 0) {
                        objDrag = 1
                    } else {
                        val si = hitStroke(nx, ny)
                        if (si >= 0) {
                            selStroke = si
                            objDrag = 4
                            onBeforeChange?.invoke()
                        } else {
                            objDrag = 3
                        }
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (cropping) {
                    cropX1 = nx
                    cropY1 = ny
                    invalidate()
                    return true
                }
                val o = objects.getOrNull(selected)
                when (objDrag) {
                    1 -> if (o != null) {
                        o.x += nx - objLastX
                        o.y += ny - objLastY
                        objMoved = true
                    }
                    2 -> if (o != null) {
                        o.w = (o.w + nx - objLastX).coerceAtLeast(0.04f)
                        o.h = (o.h + ny - objLastY).coerceAtLeast(0.04f)
                        objMoved = true
                    }
                    4 -> {
                        val st = strokes.getOrNull(selStroke)
                        if (st != null) {
                            var k = 0
                            while (k + 1 < st.pts.size) {
                                st.pts[k] = st.pts[k] + (nx - objLastX)
                                st.pts[k + 1] = st.pts[k + 1] + (ny - objLastY)
                                k += 2
                            }
                            objMoved = true
                        }
                    }
                    3 -> {
                        panX += e.x - objSX
                        panY += e.y - objSY
                        clampPan()
                    }
                }
                objLastX = nx
                objLastY = ny
                objSX = e.x
                objSY = e.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (cropping) {
                    cropping = false
                    cropMode = false
                    val x = minOf(cropX0, cropX1)
                    val y = minOf(cropY0, cropY1)
                    val w = Math.abs(cropX1 - cropX0)
                    val h = Math.abs(cropY1 - cropY0)
                    if (w > 0.01f && h > 0.01f) onCropDone?.invoke(x, y, w, h)
                } else if (objDrag == 1 || objDrag == 2 || objDrag == 4) {
                    if (objMoved) {
                        onObjectsChanged?.invoke()
                    } else if (System.currentTimeMillis() - objDownAt > 450) {
                        if (objDrag == 4) {
                            if (selStroke >= 0) onStrokeLongPress?.invoke()
                        } else if (selected >= 0) {
                            onObjectLongPress?.invoke(selected)
                        }
                    }
                }
                objDrag = 0
                invalidate()
                return true
            }
        }
        return false
    }

    // التسطير: بيغطي الشاشة كلها (مش الصفحة بس)، وبيكبر ويصغر مع الكتابة
    private fun drawPattern(c: Canvas) {
        if (pattern == 0 || pageBitmap != null || pageRect.width() <= 0f) return
        val cells = intArrayOf(30, 18, 12)[patternLevel.coerceIn(0, 2)]
        val s = pageRect.width() / cells
        val vl = -panX / zoom
        val vt = -panY / zoom
        val vr = (width - panX) / zoom
        val vb = (height - panY) / zoom
        val x0 = pageRect.left + Math.floor(((vl - pageRect.left) / s).toDouble()).toFloat() * s
        val y0 = pageRect.top + Math.floor(((vt - pageRect.top) / s).toDouble()).toFloat() * s
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0x33334155
        paint.strokeWidth = 1f / zoom
        when (pattern) {
            1 -> {
                paint.style = Paint.Style.STROKE
                var y = y0
                while (y <= vb) { c.drawLine(vl, y, vr, y, paint); y += s }
            }
            2 -> {
                paint.style = Paint.Style.STROKE
                var y = y0
                while (y <= vb) { c.drawLine(vl, y, vr, y, paint); y += s }
                var x = x0
                while (x <= vr) { c.drawLine(x, vt, x, vb, paint); x += s }
            }
            3 -> {
                val count = ((vr - vl) / s) * ((vb - vt) / s)
                if (count > 20000f) return
                paint.style = Paint.Style.FILL
                var y = y0
                while (y <= vb) {
                    var x = x0
                    while (x <= vr) { c.drawCircle(x, y, 1.8f / zoom, paint); x += s }
                    y += s
                }
            }
        }
    }

    // رسم الصفحة كاملة بدون تكبير ولا خلفية الشاشة (للتصدير PDF وصورة)
    fun drawForExport(c: Canvas) {
        val z = zoom
        val px = panX
        val py = panY
        zoom = 1f
        panX = 0f
        panY = 0f
        exportMode = true
        onDraw(c)
        exportMode = false
        zoom = z
        panX = px
        panY = py
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
            strokes.add(if (Pens.ALL[st.pen].smart) snapShape(st) else st)
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
        val r = eraserRadius
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

    // الفرشاة: الخط بيتخن ويرفع حسب السرعة، زي الضغط على الورق
    private fun drawBrush(c: Canvas, st: Stroke, w: Float, pen: Pen) {
        paint.reset()
        paint.color = st.color
        paint.alpha = pen.alpha
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        var px = pageRect.left + st.pts[0] * pageRect.width()
        var py = pageRect.top + st.pts[1] * pageRect.height()
        var speed = 0f
        var i = 2
        while (i + 1 < st.pts.size) {
            val x = pageRect.left + st.pts[i] * pageRect.width()
            val y = pageRect.top + st.pts[i + 1] * pageRect.height()
            val d = Math.hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
            speed = speed * 0.7f + d * 0.3f
            paint.strokeWidth = (w * (1.6f - speed / 18f)).coerceIn(w * 0.45f, w * 1.6f)
            c.drawLine(px, py, x, y, paint)
            px = x
            py = y
            i += 2
        }
    }

    // القلم الذكي: لو الخط مستقيم بيتحول لخط نضيف، ولو مقفول بيتحول لدايرة أو بيضاوي أو مستطيل
    private fun snapShape(st: Stroke): Stroke {
        val n = st.pts.size / 2
        if (n < 6 || pageRect.width() <= 0f || pageRect.height() <= 0f) return st
        val wd = pageRect.width()
        val ht = pageRect.height()
        val xs = FloatArray(n) { st.pts[it * 2] * wd }
        val ys = FloatArray(n) { st.pts[it * 2 + 1] * ht }
        var minX = xs[0]
        var maxX = xs[0]
        var minY = ys[0]
        var maxY = ys[0]
        for (k in 1 until n) {
            if (xs[k] < minX) minX = xs[k]
            if (xs[k] > maxX) maxX = xs[k]
            if (ys[k] < minY) minY = ys[k]
            if (ys[k] > maxY) maxY = ys[k]
        }
        val bw = maxX - minX
        val bh = maxY - minY
        if (bw < 8f && bh < 8f) return st
        val ax = xs[0]
        val ay = ys[0]
        val bx = xs[n - 1]
        val by = ys[n - 1]
        val len = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        val diag = Math.hypot(bw.toDouble(), bh.toDouble()).toFloat()
        val out = ArrayList<Float>()
        fun emit(px: Float, py: Float) {
            out.add(px / wd)
            out.add(py / ht)
        }
        if (len > 0.8f * diag && len > 40f) {
            var dev = 0f
            for (k in 0 until n) {
                val d = Math.abs((xs[k] - ax) * (by - ay) - (ys[k] - ay) * (bx - ax)) / len
                if (d > dev) dev = d
            }
            if (dev > 0.05f * len) return st
            emit(ax, ay)
            emit(bx, by)
            return Stroke(st.pen, st.color, st.size, out)
        }
        if (len > 0.3f * diag) return st
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = bw / 2f
        val ry = bh / 2f
        var err = 0.0
        for (k in 0 until n) {
            val ex = (xs[k] - cx) / rx
            val ey = (ys[k] - cy) / ry
            err += Math.abs(ex * ex + ey * ey - 1.0)
        }
        if (err / n < 0.25) {
            for (k in 0..72) {
                val t = k / 72.0 * Math.PI * 2
                emit(cx + rx * Math.cos(t).toFloat(), cy + ry * Math.sin(t).toFloat())
            }
            return Stroke(st.pen, st.color, st.size, out)
        }
        val tol = 0.12f * minOf(bw, bh)
        var edge = 0
        for (k in 0 until n) {
            val d = minOf(minOf(Math.abs(xs[k] - minX), Math.abs(xs[k] - maxX)), minOf(Math.abs(ys[k] - minY), Math.abs(ys[k] - maxY)))
            if (d <= tol) edge++
        }
        if (edge < 0.85f * n) return st
        emit(minX, minY)
        emit(maxX, minY)
        emit(maxX, maxY)
        emit(minX, maxY)
        emit(minX, minY)
        return Stroke(st.pen, st.color, st.size, out)
    }

    private fun dist(a: PointF, b: PointF): Float = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    // زي الـ PDF: لو الصفحة أكبر من الشاشة التحريك بيقف عند حوافها، ولو أصغر بتفضل في النص
    private fun clampPan() {
        if (pageRect.width() <= 0f) return
        val pw = pageRect.width() * zoom
        val ph = pageRect.height() * zoom
        panX = if (pw > width) {
            panX.coerceIn(width - (pageRect.left + pageRect.width()) * zoom, -pageRect.left * zoom)
        } else {
            (width - pw) / 2f - pageRect.left * zoom
        }
        panY = if (ph > height) {
            panY.coerceIn(height - (pageRect.top + pageRect.height()) * zoom, -pageRect.top * zoom)
        } else {
            (height - ph) / 2f - pageRect.top * zoom
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
        zoom = (pinchZoom * d / pinchD).coerceIn(0.3f, 5f)
        panX = mx - pinchPre.x * zoom
        panY = my - pinchPre.y * zoom
        clampPan()
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (objectTouch(e)) return true
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
                } else if (!handMode && (stylus || fingerDraws || e.getToolType(i) == MotionEvent.TOOL_TYPE_MOUSE)) {
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
