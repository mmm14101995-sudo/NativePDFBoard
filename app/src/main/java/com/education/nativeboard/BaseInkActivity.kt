package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
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
import android.widget.TextView

// الشاشة المشتركة بين القارئ والسبورة:
// - شريط علوي ثابت: العنوان، تراجع، إعادة، ملء الشاشة، إخفاء، إغلاق
// - شريط جانبي ثابت: الأقلام، لون الحبر، حجم القلم، الممحاة
// - شريط سفلي: التنقل بين الصفحات والإضافات (في ملء الشاشة بيبقى عايم ويتحرك)
abstract class BaseInkActivity : Activity() {
    protected lateinit var doc: Doc
    protected lateinit var view: InkView
    protected var pageNo = 1
    protected var dirty = false
    protected val ICON = Color.parseColor("#E2E8F0")

    private val undo = ArrayList<ArrayList<Stroke>>()
    private val redo = ArrayList<ArrayList<Stroke>>()
    private var sizeIdx = 1
    private var vertical = false
    private var fullMode = false
    private var labelMode = false
    private val quickPens = intArrayOf(0, 3, 11, 8, 12)
    private val quickKinds = arrayOf("fountain", "fineliner", "brush", "highlighter", "smart")
    private val penLabels = arrayOf("قلم", "فاين", "فرشاة", "تظليل", "ذكي")
    private val slotInk = intArrayOf(0xFF111827.toInt(), 0xFF2563EB.toInt(), 0xFF0EA5E9.toInt(), 0xFFFACC15.toInt(), 0xFFA855F7.toInt())
    private val penViews = ArrayList<PenIconView>()
    private val penSlots = ArrayList<FrameLayout>()
    private lateinit var host: FrameLayout
    private lateinit var pageLabel: TextView
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var fullBtn: FrameLayout
    private lateinit var eraseBtn: View
    private lateinit var colorDot: View
    private lateinit var sizeDot: View
    private lateinit var chev: ChevronView

    abstract fun pageTotal(): Int
    abstract fun showPageBitmap(n: Int)
    open fun extraButtons(row: LinearLayout) {}
    open fun docTitle(): String = ""
    protected open fun startPage(): Int = 1
    protected open fun onPageShown(n: Int) {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUi()
    }

    private fun applySystemUi() {
        if (fullMode) Ui.immersive(this) else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    protected fun buildUi() {
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
        val root = FrameLayout(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(buildTop(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val mid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mid.addView(buildSide(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mid.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        content.addView(mid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host = FrameLayout(this)
        root.addView(host, placement())
        setContentView(root)
        applySystemUi()
        installBar()
    }

    // الشريط العلوي الثابت
    private fun buildTop(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@BaseInkActivity, 10), Ui.dp(this@BaseInkActivity, 6), Ui.dp(this@BaseInkActivity, 10), Ui.dp(this@BaseInkActivity, 6))
            setBackgroundColor(0xF2101828.toInt())
        }
        bar.addView(TextView(this).apply {
            text = docTitle()
            setTextColor(ICON)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(Ui.dp(this@BaseInkActivity, 6), 0, Ui.dp(this@BaseInkActivity, 6), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        undoBtn = Ui.iconWell(this, IconView(this, "undo", ICON)) { doUndo() }
        redoBtn = Ui.iconWell(this, IconView(this, "redo", ICON)) { doRedo() }
        bar.addView(undoBtn)
        bar.addView(redoBtn)
        bar.addView(Ui.sep(this))
        fullBtn = Ui.iconWell(this, IconView(this, "expand", ICON)) { toggleFull() }
        bar.addView(fullBtn)
        chev = ChevronView(this)
        bar.addView(Ui.iconWell(this, chev) { toggleBar() })
        bar.addView(Ui.iconWell(this, IconView(this, "close", ICON)) { closeWithPrompt() })
        return bar
    }

    // الشريط الجانبي الثابت: الأقلام + الحبر + الحجم + الممحاة
    private fun buildSide(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(this@BaseInkActivity, 6), Ui.dp(this@BaseInkActivity, 8), Ui.dp(this@BaseInkActivity, 6), Ui.dp(this@BaseInkActivity, 8))
            setBackgroundColor(0xF2101828.toInt())
        }
        penSlots.clear()
        penViews.clear()
        for (i in quickPens.indices) {
            val pv = PenIconView(this, quickKinds[i], slotInk[i])
            penViews.add(pv)
            val slot = penSlot(pv, i)
            penSlots.add(slot)
            col.addView(slot)
        }
        col.addView(hSep())
        colorDot = View(this)
        col.addView(Ui.iconWell(this, colorDot) { openInkPalette() })
        sizeDot = View(this)
        col.addView(Ui.iconWell(this, sizeDot) { cycleSize() })
        eraseBtn = Ui.penButton(this, "eraser") { toggleEraser() }
        col.addView(eraseBtn)
        return col
    }

    private fun hSep(): View = View(this).apply {
        setBackgroundColor(0x33FFFFFF)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 26), Ui.dp(this@BaseInkActivity, 1)).apply {
            setMargins(0, Ui.dp(this@BaseInkActivity, 6), 0, Ui.dp(this@BaseInkActivity, 6))
        }
    }

    // مكان الشريط السفلي حسب الوضع
    private fun placement(): FrameLayout.LayoutParams {
        val m = Ui.dp(this, 12)
        return when {
            fullMode && vertical -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                setMargins(m + Ui.dp(this@BaseInkActivity, 70), Ui.dp(this@BaseInkActivity, 70), 0, Ui.dp(this@BaseInkActivity, 70))
            }
            fullMode -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(m, 0, m, m)
            }
            else -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(Ui.dp(this@BaseInkActivity, 8), 0, Ui.dp(this@BaseInkActivity, 8), Ui.dp(this@BaseInkActivity, 8))
            }
        }
    }

    private fun installBar() {
        labelMode = !fullMode
        host.removeAllViews()
        host.addView(buildBar(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.layoutParams = placement()
        host.translationX = 0f
        host.translationY = 0f
        host.visibility = View.VISIBLE
        refreshFullIcon()
        refreshBar()
    }

    private fun refreshFullIcon() {
        if (!::fullBtn.isInitialized) return
        fullBtn.removeAllViews()
        fullBtn.addView(IconView(this, if (fullMode) "shrink" else "expand", ICON), FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER))
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
                setTextColor(0xCCFFFFFF.toInt())
                textSize = 10f
                gravity = Gravity.CENTER
            })
        }
    }

    // زرار إجراء (للأزرار الإضافية في الكلاس اللي بيورث)
    protected fun actionBtn(icon: String, label: String, onClick: () -> Unit): View =
        labeled(Ui.iconWell(this, IconView(this, icon, ICON), onClick), label)

    // الشريط السفلي: التنقل بين الصفحات والإضافات
    private fun buildBar(): View {
        val v = fullMode && vertical
        val row = LinearLayout(this).apply {
            orientation = if (v) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@BaseInkActivity, 8), Ui.dp(this@BaseInkActivity, 8), Ui.dp(this@BaseInkActivity, 8), Ui.dp(this@BaseInkActivity, 8))
            background = glassBg()
        }
        val wrap: View = if (v) {
            ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(row) }
        } else {
            HorizontalScrollView(this).apply { isFillViewport = true; isHorizontalScrollBarEnabled = false; addView(row) }
        }

        if (fullMode) {
            val handle = Ui.iconWell(this, IconView(this, "grip", ICON)) {}
            handle.setOnTouchListener(dragListener())
            row.addView(handle)
            row.addView(sep(v))
        }

        row.addView(labeled(Ui.iconWell(this, IconView(this, "next", ICON)) { goPage(pageNo + 1) }, "التالي"))
        pageLabel = Ui.label(this)
        row.addView(pageLabel)
        row.addView(labeled(Ui.iconWell(this, IconView(this, "prev", ICON)) { goPage(pageNo - 1) }, "السابق"))
        row.addView(sep(v))

        extraButtons(row)
        if (fullMode) row.addView(Ui.iconWell(this, IconView(this, "orient", ICON)) { toggleOrient() })
        return wrap
    }

    private fun penSlot(pv: PenIconView, i: Int): FrameLayout = FrameLayout(this).apply {
        background = normBg()
        addView(pv, FrameLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 28), Ui.dp(this@BaseInkActivity, 46), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 44), Ui.dp(this@BaseInkActivity, 54)).apply {
            setMargins(Ui.dp(this@BaseInkActivity, 2), Ui.dp(this@BaseInkActivity, 3), Ui.dp(this@BaseInkActivity, 2), Ui.dp(this@BaseInkActivity, 3))
        }
        setOnClickListener { selectPen(i) }
    }

    private fun sep(v: Boolean): View = View(this).apply {
        setBackgroundColor(0x33FFFFFF)
        layoutParams = if (v) {
            LinearLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 26), Ui.dp(this@BaseInkActivity, 1)).apply {
                setMargins(0, Ui.dp(this@BaseInkActivity, 6), 0, Ui.dp(this@BaseInkActivity, 6))
            }
        } else {
            LinearLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 1), Ui.dp(this@BaseInkActivity, 26)).apply {
                setMargins(Ui.dp(this@BaseInkActivity, 6), 0, Ui.dp(this@BaseInkActivity, 6), 0)
            }
        }
    }

    private fun glassBg(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0x66334A6B, 0xCC0C1222.toInt())
    ).apply {
        cornerRadius = Ui.dp(this@BaseInkActivity, 30).toFloat()
        setStroke(Ui.dp(this@BaseInkActivity, 1), 0x55FFFFFF)
    }

    private fun normBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = Ui.dp(this@BaseInkActivity, 12).toFloat()
        setColor(0x14FFFFFF)
    }

    private fun selBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = Ui.dp(this@BaseInkActivity, 12).toFloat()
        setColor(0x33FFFFFF)
        setStroke(Ui.dp(this@BaseInkActivity, 2), 0xFF38BDF8.toInt())
    }

    // سحب الشريط السفلي من مقبضه (في ملء الشاشة بس)
    private fun dragListener(): View.OnTouchListener {
        var dx = 0f
        var dy = 0f
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX - host.translationX
                    dy = e.rawY - host.translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    host.translationX = e.rawX - dx
                    host.translationY = e.rawY - dy
                }
            }
            true
        }
    }

    private fun toggleFull() {
        fullMode = !fullMode
        if (!fullMode) vertical = false
        applySystemUi()
        installBar()
    }

    private fun toggleOrient() {
        vertical = !vertical
        installBar()
    }

    private fun toggleBar() {
        val hidden = host.visibility == View.GONE
        host.visibility = if (hidden) View.VISIBLE else View.GONE
        chev.down = hidden
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

    // اختيار قلم: بياخد لونه الخاص
    private fun selectPen(i: Int) {
        view.penIndex = quickPens[i]
        view.ink = slotInk[i]
        view.eraserMode = false
        refreshBar()
    }

    private fun openInkPalette() {
        Glass.swatchDialog(this, "", Ui.INK_PALETTE, view.ink) { c ->
            view.ink = c
            view.eraserMode = false
            val i = quickPens.indexOf(view.penIndex)
            if (i >= 0) {
                slotInk[i] = c
                penViews[i].body = c
                penViews[i].invalidate()
            }
            refreshBar()
        }
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

    protected fun refreshBar() {
        if (!::view.isInitialized || !::pageLabel.isInitialized) return
        pageLabel.text = "${pageNo}/${pageTotal()}"
        undoBtn.alpha = if (undo.isEmpty()) 0.35f else 1f
        redoBtn.alpha = if (redo.isEmpty()) 0.35f else 1f
        eraseBtn.alpha = if (view.eraserMode) 1f else 0.6f
        colorDot.background = Glass.oval(this, view.ink, 0xFFFFFFFF.toInt(), 2)
        val d = arrayOf(6, 10, 16)[sizeIdx]
        sizeDot.layoutParams = FrameLayout.LayoutParams(Ui.dp(this, d), Ui.dp(this, d), Gravity.CENTER)
        sizeDot.background = Glass.oval(this, ICON)
        penSlots.forEachIndexed { i, s ->
            val selected = !view.eraserMode && view.penIndex == quickPens[i]
            s.background = if (selected) selBg() else normBg()
        }
        penViews.forEachIndexed { i, pv ->
            pv.picked = !view.eraserMode && view.penIndex == quickPens[i]
        }
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
