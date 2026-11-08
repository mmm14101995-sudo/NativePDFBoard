package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

// التصميم حسب الصورة (وضع عادي):
// فوق: شريط كحلي متدرج (أيقونة، اسم الملف، تراجع، إعادة، ملء الشاشة، إغلاق)
// على الشمال: عمود الألوان وحجم القلم
// تحت: الأقلام + الممحاة + المسح + التنقل بالصفحات + الإضافات، بكلام صغير تحت كل أيقونة
// ملء الشاشة: شريط عايم واحد فيه كل الأدوات، بيتحرك وبيتبدل أفقي/رأسي
abstract class BaseInkActivity : Activity() {
    protected lateinit var doc: Doc
    protected lateinit var view: InkView
    protected var pageNo = 1
    protected var dirty = false
    protected val ICON = Color.parseColor("#1E293B")
    private val WHITE = Color.WHITE

    private val undo = ArrayList<ArrayList<Stroke>>()
    private val redo = ArrayList<ArrayList<Stroke>>()
    private var sizeIdx = 1
    private var fullMode = false
    private var vertical = false
    private var labelMode = false
    private var built = false
    // الأقلام الظاهرة: نبدأ بتلاتة، وعلامة + بتضيف أي قلم تاني
    private val slotPens = arrayListOf(0, 3, 8)
    private val slotInk = arrayListOf(0xFF111827.toInt(), 0xFF2563EB.toInt(), 0xFFFACC15.toInt())
    private val penViews = ArrayList<PenIconView>()
    private val penSlots = ArrayList<FrameLayout>()
    private val colorTiles = ArrayList<FrameLayout>()
    private var colorDot: View? = null
    private var host: FrameLayout? = null
    private lateinit var pageLabel: TextView
    private lateinit var undoBtn: FrameLayout
    private lateinit var redoBtn: FrameLayout
    private lateinit var eraseBtn: FrameLayout
    private lateinit var sizeDot: View
    private var tabRow: LinearLayout? = null
    private var tabScroll: HorizontalScrollView? = null
    private lateinit var handBtn: FrameLayout
    private var sizeProgress = 35

    abstract fun pageTotal(): Int
    abstract fun showPageBitmap(n: Int)
    open fun extraButtons(row: LinearLayout) {}
    open fun docTitle(): String = ""
    // السبورة بتستخدم تبويبات للصفحات بدل الرقم
    open fun usesTabs(): Boolean = false
    protected open fun startPage(): Int = 1
    protected open fun onPageShown(n: Int) {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUi()
    }

    private fun applySystemUi() {
        if (fullMode) Ui.immersive(this) else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    // ---------- عناصر مشتركة ----------

    private fun roundFill(c: Int, r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(c)
    }

    private fun panelBg(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(0xF2F8FAFC.toInt())
        setStroke(dp(1), 0x1F0F172A)
    }

    private fun headerBg(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFF1E3A8A.toInt(), 0xFF2563EB.toInt())
    )

    private fun oval(c: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(c)
    }

    private fun ring(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(2), 0xFF2563EB.toInt())
    }

    private fun slotBg(sel: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (sel) {
            setColor(0x332563EB)
            setStroke(dp(2), 0xFF2563EB.toInt())
        } else {
            setColor(0x140F172A)
        }
    }

    // زرار أيقونة (40 دب) بيقبل أي لون
    private fun ibtn(icon: String, tint: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { onClick() }
        addView(IconView(this@BaseInkActivity, icon, tint), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    // كلام صغير تحت الأيقونة (في الوضع العادي بس)
    protected fun labeled(well: View, text: String): View {
        if (!labelMode || text.isEmpty()) return well
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(well)
            addView(TextView(this@BaseInkActivity).apply {
                this.text = text
                setTextColor(0xFF334155.toInt())
                textSize = 10f
                gravity = Gravity.CENTER
            })
        }
    }

    // زرار إجراء للأزرار الإضافية في الكلاس اللي بيورث
    protected fun actionBtn(icon: String, label: String, onClick: () -> Unit): View =
        labeled(ibtn(icon, ICON, onClick), label)

    private fun sep(v: Boolean): View = View(this).apply {
        setBackgroundColor(0x22000000)
        layoutParams = if (v) {
            LinearLayout.LayoutParams(dp(26), dp(1)).apply { setMargins(0, dp(6), 0, dp(6)) }
        } else {
            LinearLayout.LayoutParams(dp(1), dp(26)).apply { setMargins(dp(6), 0, dp(6), 0) }
        }
    }

    private fun headerSep(): View = View(this).apply {
        setBackgroundColor(0x66FFFFFF)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(24)).apply { setMargins(dp(6), 0, dp(6), 0) }
    }

    private fun hSep(): View = View(this).apply {
        setBackgroundColor(0x22000000)
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(1)).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    // ---------- بناء الشاشة ----------

    protected fun buildUi() {
        if (!::view.isInitialized) {
            view = InkView(this).apply {
                ink = slotInk[0]
                onBeforeChange = {
                    undo.add(snapshot())
                    redo.clear()
                    refreshBar()
                }
                onChanged = {
                    doc.pages[pageNo] = strokes
                    dirty = true
                    refreshBar()
                }
            }
        }
        (view.parent as? ViewGroup)?.removeView(view)
        labelMode = !fullMode
        colorTiles.clear()
        colorDot = null

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFFE2E8F0.toInt())
        if (fullMode) {
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val h = FrameLayout(this)
            h.addView(floatingBar(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            host = h
            root.addView(h, placement())
        } else {
            host = null
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.HORIZONTAL
            mid.setPadding(dp(8), dp(8), dp(8), dp(8))
            mid.addView(sideBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, dp(8), 0)
            })
            mid.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            col.addView(mid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            col.addView(bottomBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            })
            root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        built = true
        applySystemUi()
        refreshBar()
    }

    // الشريط العلوي (الكحلي)
    private fun topBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(10), dp(6), dp(10), dp(6))
        bar.background = headerBg()

        val logo = FrameLayout(this)
        logo.background = roundFill(0x33FFFFFF, 10)
        logo.addView(IconView(this, "board", WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        bar.addView(logo, LinearLayout.LayoutParams(dp(34), dp(34)))

        val title = TextView(this)
        title.text = docTitle()
        title.setTextColor(WHITE)
        title.textSize = 16f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setPadding(dp(10), 0, dp(6), 0)
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        addHistory(bar, WHITE)
        bar.addView(headerSep())
        bar.addView(ibtn("expand", WHITE) { toggleFull() })
        bar.addView(glassBtn("home") { closeWithPrompt() })
        return bar
    }

    // العمود الجانبي (على الشمال): الألوان + حجم القلم
    private fun sideBar(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(6), dp(10), dp(6), dp(10))
        col.background = panelBg(18)
        for (c in Ui.INK_PALETTE.take(6)) {
            val t = colorTile(c) { setInk(c) }
            colorTiles.add(t)
            col.addView(t)
        }
        col.addView(rainbowTile { openCustomColor() })
        col.addView(hSep())
        col.addView(sizeTile())
        return col
    }

    // الشريط السفلي (وضع عادي): التنقل والأقلام والممحاة والمسح والإضافات
    private fun bottomBar(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.background = panelBg(18)
        addNav(row)
        row.addView(sep(false))
        addPens(row)
        row.addView(sep(false))
        addEraseClear(row, ICON)
        row.addView(sep(false))
        row.addView(labeled(ibtn("export", ICON) { exportMenu() }, "تصدير"))
        extraButtons(row)
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.isFillViewport = true
        scroll.addView(row)
        return scroll
    }

    // الشريط العايم (ملء الشاشة): كل الأدوات
    private fun floatingBar(): View {
        val v = vertical
        val row = LinearLayout(this)
        row.orientation = if (v) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        row.background = panelBg(24)

        val handle = ibtn("grip", ICON) {}
        handle.setOnTouchListener(dragListener())
        row.addView(handle)
        row.addView(sep(v))
        addHistory(row, ICON)
        row.addView(sep(v))
        addPens(row)
        row.addView(sep(v))
        row.addView(currentColorTile())
        row.addView(sizeTile())
        addEraseClear(row, ICON)
        row.addView(sep(v))
        addNav(row)
        row.addView(sep(v))
        extraButtons(row)
        row.addView(ibtn("export", ICON) { exportMenu() })
        row.addView(ibtn("orient", ICON) { toggleOrient() })
        row.addView(ibtn("shrink", ICON) { toggleFull() })

        val wrap: View = if (v) {
            ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(row) }
        } else {
            HorizontalScrollView(this).apply { isFillViewport = true; isHorizontalScrollBarEnabled = false; addView(row) }
        }
        return wrap
    }

    private fun placement(): FrameLayout.LayoutParams {
        val m = dp(12)
        return if (vertical) {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                setMargins(m, dp(70), 0, dp(70))
            }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(m, 0, m, m)
            }
        }
    }

    // ---------- أجزاء الأدوات ----------

    private fun addHistory(row: LinearLayout, tint: Int) {
        undoBtn = ibtn("undo", tint) { doUndo() }
        redoBtn = ibtn("redo", tint) { doRedo() }
        row.addView(undoBtn)
        row.addView(redoBtn)
    }

    private fun addPens(row: LinearLayout) {
        penSlots.clear()
        penViews.clear()
        for (i in slotPens.indices) {
            val pv = PenIconView(this, kindOf(slotPens[i]), slotInk[i])
            penViews.add(pv)
            val s = penSlot(pv, i)
            penSlots.add(s)
            row.addView(labeled(s, Pens.ALL[slotPens[i]].name))
        }
        row.addView(labeled(ibtn("plus", ICON) { openPenPicker() }, "إضافة"))
    }

    private fun addEraseClear(row: LinearLayout, tint: Int) {
        eraseBtn = ibtn("eraser", tint) { toggleEraser() }
        row.addView(labeled(eraseBtn, "ممحاة"))
        row.addView(labeled(ibtn("trash", tint) { confirmClear() }, "مسح"))
        handBtn = ibtn("hand", tint) { toggleHand() }
        row.addView(labeled(handBtn, "يد"))
    }

    private fun addNav(row: LinearLayout) {
        pageLabel = TextView(this)
        pageLabel.textSize = 13f
        pageLabel.setTextColor(ICON)
        pageLabel.gravity = Gravity.CENTER
        pageLabel.minWidth = dp(48)
        if (usesTabs()) {
            row.addView(ibtn("next", ICON) { goPage(pageNo + 1) })
            val scroll = HorizontalScrollView(this)
            scroll.isHorizontalScrollBarEnabled = false
            val tr = LinearLayout(this)
            tr.orientation = LinearLayout.HORIZONTAL
            tr.gravity = Gravity.CENTER_VERTICAL
            scroll.addView(tr)
            tabScroll = scroll
            tabRow = tr
            row.addView(scroll, LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(ibtn("prev", ICON) { goPage(pageNo - 1) })
        } else {
            tabRow = null
            tabScroll = null
            val pill = LinearLayout(this)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            pill.setPadding(dp(4), 0, dp(4), 0)
            pill.background = roundFill(0x140F172A, 20)
            pill.addView(ibtn("next", ICON) { goPage(pageNo + 1) })
            pill.addView(pageLabel)
            pill.addView(ibtn("prev", ICON) { goPage(pageNo - 1) })
            row.addView(pill)
        }
    }

    private fun colorTile(c: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        tag = c
        setOnClickListener { onClick() }
        addView(View(this@BaseInkActivity).apply { background = oval(c) }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    private fun rainbowTile(onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { onClick() }
        addView(View(this@BaseInkActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.SWEEP_GRADIENT
                colors = intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEF4444.toInt())
            }
        }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    // لون الحبر الحالي (في الشريط العايم)
    private fun currentColorTile(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { openCustomColor() }
        val dot = View(this@BaseInkActivity)
        colorDot = dot
        addView(dot, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    private fun sizeTile(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { openSizeDialog() }
        val dot = View(this@BaseInkActivity)
        sizeDot = dot
        addView(dot, FrameLayout.LayoutParams(dp(6), dp(6), Gravity.CENTER))
    }

    private fun penSlot(pv: PenIconView, i: Int): FrameLayout = FrameLayout(this).apply {
        background = slotBg(false)
        addView(pv, FrameLayout.LayoutParams(dp(28), dp(46), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(54)).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { selectPen(i) }
        setOnLongClickListener {
            if (slotPens.size > 1) {
                slotPens.removeAt(i)
                slotInk.removeAt(i)
                buildUi()
            }
            true
        }
    }

    // نافذة اختيار قلم: كل الأقلام الباقية
    private fun openPenPicker() {
        val available = (0 until Pens.ALL.size).filter { !slotPens.contains(it) }
        if (available.isEmpty()) {
            Toast.makeText(this, "كل الأقلام ظاهرة بالفعل", Toast.LENGTH_SHORT).show()
            return
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(8), dp(12), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(col) })
            .setNegativeButton("إغلاق", null)
            .create()
        for (group in available.chunked(4)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (idx in group) {
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(dp(4), dp(6), dp(4), dp(6))
                cell.background = roundFill(0x140F172A, 12)
                cell.addView(PenIconView(this, kindOf(idx), defaultInk(idx)), LinearLayout.LayoutParams(dp(30), dp(48)))
                cell.addView(TextView(this).apply {
                    text = Pens.ALL[idx].name
                    textSize = 11f
                    setTextColor(ICON)
                    gravity = Gravity.CENTER
                })
                cell.setOnClickListener {
                    if (slotPens.size < 5) slotPens.add(idx) else slotPens[4] = idx
                    if (slotInk.size < 5) slotInk.add(defaultInk(idx)) else slotInk[4] = defaultInk(idx)
                    dlg.dismiss()
                    buildUi()
                }
                r.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            col.addView(r)
        }
        dlg.show()
    }

    private fun kindOf(p: Int): String = when (p) {
        0, 1, 2 -> "fountain"
        3, 4, 9 -> "fineliner"
        5, 11 -> "brush"
        6 -> "pencil"
        7, 10 -> "marker"
        8 -> "highlighter"
        12 -> "smart"
        else -> "fountain"
    }

    private fun defaultInk(p: Int): Int = if (Pens.ALL[p].highlight) 0xFFFACC15.toInt() else 0xFF111827.toInt()

    // سحب الشريط العايم من مقبضه
    private fun dragListener(): View.OnTouchListener {
        var dx = 0f
        var dy = 0f
        return View.OnTouchListener { _, e ->
            val h = host ?: return@OnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX - h.translationX
                    dy = e.rawY - h.translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    h.translationX = e.rawX - dx
                    h.translationY = e.rawY - dy
                }
            }
            true
        }
    }

    // ---------- الأفعال ----------

    private fun toggleFull() {
        fullMode = !fullMode
        if (!fullMode) vertical = false
        buildUi()
    }

    private fun toggleOrient() {
        vertical = !vertical
        buildUi()
    }

    protected fun markDirty() {
        dirty = true
        refreshBar()
    }

    protected fun goPage(n: Int) {
        if (n < 1 || n > pageTotal()) return
        pageNo = n
        undo.clear()
        redo.clear()
        view.strokes = doc.strokesOf(n)
        showPageBitmap(n)
        onPageShown(n)
        view.invalidate()
        refreshBar()
    }

    private fun selectPen(i: Int) {
        view.penIndex = slotPens[i]
        view.ink = slotInk[i]
        view.eraserMode = false
        refreshBar()
    }

    // تغيير اللون: بيتحفظ مع القلم الحالي وبيظهر في شكله
    private fun setInk(c: Int) {
        view.ink = c
        view.eraserMode = false
        val i = slotPens.indexOf(view.penIndex)
        if (i >= 0) {
            slotInk[i] = c
            penViews[i].body = c
            penViews[i].invalidate()
        }
        refreshBar()
    }

    private fun openInkPalette() {
        Glass.swatchDialog(this, "", Ui.INK_PALETTE, view.ink) { c -> setInk(c) }
    }

    private fun cycleSize() {
        sizeIdx = (sizeIdx + 1) % Ui.SIZES.size
        view.sizeLevel = Ui.SIZES[sizeIdx]
        refreshBar()
    }

    private fun toggleEraser() {
        view.eraserMode = !view.eraserMode
        refreshBar()
    }

    private fun doUndo() {
        if (undo.isEmpty()) return
        redo.add(view.snapshot())
        view.restore(undo.removeAt(undo.size - 1))
    }

    private fun doRedo() {
        if (redo.isEmpty()) return
        undo.add(view.snapshot())
        view.restore(redo.removeAt(redo.size - 1))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("تمسح كل كتابة الصفحة دي؟")
            .setPositiveButton("مسح") { _, _ -> clearPage() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun clearPage() {
        if (view.strokes.isEmpty()) return
        undo.add(view.snapshot())
        redo.clear()
        view.restore(ArrayList<Stroke>())
        doc.pages[pageNo] = view.strokes
        dirty = true
        view.invalidate()
        refreshBar()
    }

    protected fun refreshBar() {
        if (!built) return
        pageLabel.text = "${pageNo}/${pageTotal()}"
        undoBtn.alpha = if (undo.isEmpty()) 0.35f else 1f
        redoBtn.alpha = if (redo.isEmpty()) 0.35f else 1f
        eraseBtn.alpha = if (view.eraserMode) 1f else 0.5f
        handBtn.alpha = if (view.handMode) 1f else 0.5f
        colorDot?.background = oval(view.ink)
        colorTiles.forEach { t -> t.background = if (t.tag == view.ink) ring() else null }
        val d = (4 + sizeProgress * 0.36f).toInt()
        sizeDot.layoutParams = FrameLayout.LayoutParams(dp(d), dp(d), Gravity.CENTER)
        sizeDot.background = oval(ICON)
        penSlots.forEachIndexed { i, s ->
            s.background = slotBg(!view.eraserMode && view.penIndex == slotPens[i])
        }
        penViews.forEachIndexed { i, pv ->
            pv.picked = !view.eraserMode && view.penIndex == slotPens[i]
        }
        rebuildTabs()
    }

    private fun toggleHand() {
        view.handMode = !view.handMode
        if (view.handMode) view.eraserMode = false
        refreshBar()
    }

    // حجم القلم: شريط من رفيع (شمال) لتخين (يمين)، ودايرة فوق بتكبر وتصغر
    private fun sizeLevelOf(p: Int): Float = 2f + p * 0.14f

    private fun openSizeDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(12), dp(20), dp(8))
        val stage = FrameLayout(this)
        val preview = View(this)
        preview.background = oval(ICON)
        stage.addView(preview, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        box.addView(stage, LinearLayout.LayoutParams(dp(120), dp(120)))
        fun showSize(p: Int) {
            val d = (4 + p * 0.36f).toInt()
            preview.layoutParams = FrameLayout.LayoutParams(dp(d), dp(d), Gravity.CENTER)
        }
        showSize(sizeProgress)
        val track = LinearLayout(this)
        track.orientation = LinearLayout.HORIZONTAL
        track.gravity = Gravity.CENTER_VERTICAL
        val thin = View(this)
        thin.background = oval(ICON)
        track.addView(thin, LinearLayout.LayoutParams(dp(4), dp(4)))
        val bar = SeekBar(this)
        bar.max = 100
        bar.progress = sizeProgress
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                sizeProgress = p
                view.sizeLevel = sizeLevelOf(p)
                showSize(p)
                refreshBar()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        track.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val thick = View(this)
        thick.background = oval(ICON)
        track.addView(thick, LinearLayout.LayoutParams(dp(16), dp(16)))
        box.addView(track, LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this).setView(box).setPositiveButton("تم", null).show()
    }

    // ألوان مخصصة: RGB بسلايدرز ومعاينة
    private fun openCustomColor() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(12), dp(20), dp(8))
        val prev = View(this)
        box.addView(prev, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, dp(12))
        })
        var r = Color.red(view.ink)
        var g = Color.green(view.ink)
        var b = Color.blue(view.ink)
        fun update() {
            prev.background = oval(Color.rgb(r, g, b))
        }
        update()
        fun channel(init: Int, onChange: (Int) -> Unit) {
            val s = SeekBar(this)
            s.max = 255
            s.progress = init
            s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    onChange(p)
                    update()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            box.addView(s, LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        channel(r) { r = it }
        channel(g) { g = it }
        channel(b) { b = it }
        AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("تم") { _, _ -> setInk(Color.rgb(r, g, b)) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- التصدير ----------

    private fun exportDir(): File = (getExternalFilesDir("exports") ?: filesDir).apply { mkdirs() }

    private fun exportMenu() {
        val items = arrayOf("ملف PDF", "صورة PNG للصفحة", "سكرين شوت للشاشة")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportPdf()
                    1 -> exportPng()
                    2 -> exportScreenshot()
                }
            }
            .show()
    }

    private fun exportPng() {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.drawForExport(Canvas(bmp))
        savePng(bmp, "page")
    }

    private fun exportScreenshot() {
        val d = window.decorView
        val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        d.draw(Canvas(bmp))
        savePng(bmp, "screen")
    }

    private fun savePng(bmp: Bitmap, tag: String) {
        val f = File(exportDir(), "${tag}_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        Toast.makeText(this, "اتحفظ في: ${f.path}", Toast.LENGTH_LONG).show()
    }

    // PDF: كل صفحات الكتاب/السبورة بكتابتها
    private fun exportPdf() {
        val origPage = pageNo
        val pdf = PdfDocument()
        val w = view.width
        val h = view.height
        for (n in 1..pageTotal()) {
            view.strokes = doc.strokesOf(n)
            showPageBitmap(n)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
            view.drawForExport(page.canvas)
            pdf.finishPage(page)
        }
        val f = File(exportDir(), "export_${System.currentTimeMillis()}.pdf")
        f.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        view.strokes = doc.strokesOf(origPage)
        showPageBitmap(origPage)
        refreshBar()
        Toast.makeText(this, "اتحفظ في: ${f.path}", Toast.LENGTH_LONG).show()
    }

    // زرار زجاجي (بيت / غيره)
    private fun glassBtn(icon: String, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x66FFFFFF, 0x1AFFFFFF)).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), 0x88FFFFFF.toInt())
        }
        setOnClickListener { onClick() }
        addView(IconView(this@BaseInkActivity, icon, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    // تبويب لكل صفحة: "صفحة N"، والتبويب الحالي باللون الكحلي
    private fun rebuildTabs() {
        val tr = tabRow ?: return
        tr.removeAllViews()
        var selected: View? = null
        for (n in 1..pageTotal()) {
            val sel = n == pageNo
            val t = TextView(this)
            t.text = "صفحة $n"
            t.textSize = 12f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(12), dp(6), dp(12), dp(6))
            t.setTextColor(if (sel) Color.WHITE else ICON)
            t.background = if (sel) headerBg() else roundFill(0x140F172A, 14)
            t.setOnClickListener { goPage(n) }
            tr.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
            if (sel) selected = t
        }
        val sv = selected
        val sc = tabScroll
        if (sv != null && sc != null) sv.post { sc.smoothScrollTo(sv.left, 0) }
    }

    protected fun closeWithPrompt() {
        if (!dirty) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setMessage("في كتابة جديدة. تحب تحفظها قبل ما تقفل؟")
            .setPositiveButton("حفظ") { _, _ ->
                Store.save(this, doc)
                finish()
            }
            .setNegativeButton("بدون حفظ") { _, _ -> finish() }
            .setNeutralButton("إلغاء", null)
            .show()
    }

    override fun onBackPressed() {
        closeWithPrompt()
    }
}
