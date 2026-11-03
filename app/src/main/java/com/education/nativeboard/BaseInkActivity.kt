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

// الشاشة المشتركة بين القارئ والسبورة: سطح الرسم + شريط الأدوات العايم + التراجع + الحفظ
abstract class BaseInkActivity : Activity() {
    protected lateinit var doc: Doc
    protected lateinit var view: InkView
    protected var pageNo = 1
    protected var dirty = false

    private val undo = ArrayList<ArrayList<Stroke>>()
    private val redo = ArrayList<ArrayList<Stroke>>()
    private var sizeIdx = 1
    private var colorIdx = 0
    private val quickPens = intArrayOf(0, 1, 2, 8, 10)
    private val penBtns = ArrayList<TextView>()
    private lateinit var pageLabel: TextView
    private lateinit var undoBtn: TextView
    private lateinit var redoBtn: TextView
    private lateinit var eraseBtn: TextView
    private lateinit var colorBtn: TextView
    private lateinit var sizeBtn: TextView
    private lateinit var bar: View
    private lateinit var eye: TextView

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
        pageLabel = Ui.label(this)
        row.addView(Ui.btn(this, "← التالية") { goPage(pageNo + 1) })
        row.addView(pageLabel)
        row.addView(Ui.btn(this, "السابقة →") { goPage(pageNo - 1) })
        row.addView(Ui.sep(this))
        for (p in quickPens) {
            val t = Ui.btn(this, Pens.ALL[p].name) { selectPen(p) }
            penBtns.add(t)
            row.addView(t)
        }
        row.addView(Ui.sep(this))
        colorBtn = Ui.btn(this, "●") { cycleColor() }
        sizeBtn = Ui.btn(this, "حجم") { cycleSize() }
        eraseBtn = Ui.btn(this, "ممحاة") { toggleEraser() }
        row.addView(colorBtn)
        row.addView(sizeBtn)
        row.addView(eraseBtn)
        row.addView(Ui.sep(this))
        undoBtn = Ui.btn(this, "تراجع") { doUndo() }
        redoBtn = Ui.btn(this, "إعادة") { doRedo() }
        row.addView(undoBtn)
        row.addView(redoBtn)
        extraButtons(row)
        row.addView(Ui.sep(this))
        row.addView(Ui.btn(this, "✕ إغلاق") { closeWithPrompt() })

        val dp12 = Ui.dp(this, 12)
        val dp8 = Ui.dp(this, 8)
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp8, 0, dp8, dp12)
        })

        // زرار الإخفاء والإظهار للشريط
        eye = Ui.btn(this, "👁") { toggleBar() }
        root.addView(eye, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp12, dp12, 0)
        })

        setContentView(root)
        refreshBar()
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

    private fun selectPen(p: Int) {
        view.penIndex = p
        view.eraserMode = false
        refreshBar()
    }

    private fun cycleColor() {
        colorIdx = (colorIdx + 1) % Ui.INKS.size
        view.ink = Ui.INKS[colorIdx]
        view.eraserMode = false
        refreshBar()
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
        eye.text = if (hidden) "👁" else "🙈"
    }

    protected fun refreshBar() {
        if (!::view.isInitialized || !::pageLabel.isInitialized) return
        pageLabel.text = "$pageNo / ${pageTotal()}"
        undoBtn.alpha = if (undo.isEmpty()) 0.35f else 1f
        redoBtn.alpha = if (redo.isEmpty()) 0.35f else 1f
        eraseBtn.alpha = if (view.eraserMode) 1f else 0.6f
        colorBtn.setTextColor(Ui.INKS[colorIdx])
        sizeBtn.text = "حجم " + Ui.SIZES[sizeIdx].toInt()
        penBtns.forEachIndexed { i, t ->
            t.alpha = if (!view.eraserMode && view.penIndex == quickPens[i]) 1f else 0.6f
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

    fun isDarkColor(c: Int): Boolean = Ui.isDark(c)
}
