package com.education.nativeboard

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout

// سبورة فاضية بصفحات كتير ولون خلفية تختاره
class BoardActivity : BaseInkActivity() {

    override fun pageTotal(): Int = doc.pageCount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        val id = intent.getStringExtra("id") ?: run {
            finish()
            return
        }
        doc = Store.load(this, id, true)
        if (doc.pageCount < 1) doc.pageCount = 1
        buildUi()
        view.post { goPage(1) }
    }

    override fun showPageBitmap(n: Int) {
        view.pageBitmap = null
        view.bgColor = if (doc.bg == -1) Color.WHITE else doc.bg
    }

    override fun extraButtons(row: LinearLayout) {
        row.addView(Ui.btn(this, "+ صفحة") { addPage() })
        row.addView(Ui.btn(this, "لون السبورة") { pickBackground() })
    }

    private fun addPage() {
        doc.pageCount += 1
        markDirty()
        goPage(doc.pageCount)
    }

    private fun pickBackground() {
        AlertDialog.Builder(this)
            .setTitle("لون السبورة")
            .setItems(Ui.BG_NAMES) { _, which ->
                doc.bg = Ui.BGS[which]
                view.bgColor = doc.bg
                // الحبر يتعدّل عشان يبان على الخلفية
                if (Ui.isDark(doc.bg) && Ui.isDark(view.ink)) view.ink = Color.WHITE
                if (!Ui.isDark(doc.bg) && view.ink == Color.WHITE) view.ink = Color.BLACK
                markDirty()
                view.invalidate()
            }
            .show()
    }
}
