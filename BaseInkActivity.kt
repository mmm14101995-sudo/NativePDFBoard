package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// الشاشة المشتركة بين القارئ والسبورة: سطح الرسم + شريط الرموز العايم + التراجع + الحفظ
abstract class BaseInkActivity : Activity() {
    protected lateinit var doc: Doc
    protected lateinit var view: InkView
    protected var pageNo = 1
    protected var dirty = false
    protected val ICON = Color.parseColor("#E2E8F0")

    private val undo = ArrayList<ArrayList<Stroke>>()
    private val redo = ArrayList<ArrayList<Stroke>>()
    private var sizeIdx = 1
    private val quickPens = intArrayOf(0, 3, 7, 8, 6)
    private val quickKinds = arrayOf("fountain", "fineliner", "marker", "highlighter", "pencil")
    private val quickBodies = intArrayOf(0xFF334155.toInt(), 0xFF1E293B.toInt(), 0xFFDC2626.toInt(), 0xFFFACC15.toInt(), 0xFF64748B.toInt())
    private val penViews = ArrayList<PenIconView>()
    private lateinit var pageLabel: TextView
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var eraseBtn: View
    private lateinit var colorBtn: View
    private lateinit var colorDot: View
    private lateinit var sizeBtn: View
    private lateinit var sizeDot: View
    private lateinit var bar: View
    private lateinit var eye: View
    private lateinit var chev: ChevronView

    abstract fun pageTotal(): Int
    abstract fun showPageBitmap(n: Int)
    open fun extraButtons(row: LinearLayout) {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }

    protected fun buildUi() {
        view = InkView(this).apply {
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
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val (scroll, row) = Ui.bar(this)
        bar = scroll

        // تراجع وإعادة
        undoBtn = Ui.iconWell(this, IconView(this, "undo", ICON)) { doUndo() }
        redoBtn = Ui.iconWell(this, IconView(this, "redo", ICON)) { doRedo() }
        row.addView(undoBtn)
        row.addView(redoBtn)
        row.addView(Ui.sep(this))

        // الأقلام الخمسة بأشكالها
        penViews.clear()
        for (i in quickPens.indices) {
            val pv = PenIconView(this, quickKinds[i], quickBodies[i])
            penViews.add(pv)
            row.addView(penSlot(pv, i))
        }
        row.addView(Ui.sep(this))

        // لون الحبر (دايرة باللون الحالي) وحجم القلم (دايرة بحجمه)
        colorDot = View(this)
        colorBtn = Ui.iconWell(this, colorDot) { openInkPalette() }
        row.addView(colorBtn)
        sizeDot = View(this)
        sizeBtn = Ui.iconWell(this, sizeDot) { cycleSize() }
        row.addView(sizeBtn)

        // الممحاة
        eraseBtn = Ui.penButton(this, "eraser") { toggleEraser() }
        row.addView(eraseBtn)
        row.addView(Ui.sep(this))

        // التنقل بين الصفحات
        row.addView(Ui.iconWell(this, IconView(this, "next", ICON)) { goPage(pageNo + 1) })
        pageLabel = Ui.label(this)
        row.addView(pageLabel)
        row.addView(Ui.iconWell(this, IconView(this, "prev", ICON)) { goPage(pageNo - 1) })
        row.addView(Ui.sep(this))

        extraButtons(row)
        row.addView(Ui.iconWell(this, IconView(this, "close", ICON)) { closeWithPrompt() })

        val dp12 = Ui.dp(this, 12)
        val dp8 = Ui.dp(this, 8)
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp8, 0, dp8, dp12)
        })

        // زرار الإخفاء والإظهار: دايرة زجاجية بسهم
        val (eyeView, chevView) = Glass.glassButton(this, 46) { toggleBar() }
        eye = eyeView
        chev = chevView
        root.addView(eye, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp12, dp12, 0)
        })

        setContentView(root)
        refreshBar()
    }

    private fun penSlot(pv: PenIconView, i: Int): FrameLayout = FrameLayout(this).apply {
        background = Ui.pill(this@BaseInkActivity, 0x14FFFFFF, 14)
        addView(pv, FrameLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 28), Ui.dp(this@BaseInkActivity, 50), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@BaseInkActivity, 42), Ui.dp(this@BaseInkActivity, 56)).apply {
            setMargins(Ui.dp(this@BaseInkActivity, 3), 0, Ui.dp(this@BaseInkActivity, 3), 0)
        }
        setOnClickListener { selectPen(i) }
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
        view.invalidate()
        refreshBar()
    }

    private fun selectPen(i: Int) {
        view.penIndex = quickPens[i]
        view.eraserMode = false
        refreshBar()
    }

    private fun openInkPalette() {
        Glass.swatchDialog(this, "", Ui.INK_PALETTE, view.ink) { c ->
            view.ink = c
            view.eraserMode = false
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

    private fun toggleBar() {
        val hidden = bar.visibility == View.GONE
        bar.visibility = if (hidden) View.VISIBLE else View.GONE
        chev.down = hidden
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
