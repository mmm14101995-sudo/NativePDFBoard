package com.education.nativeboard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor

// قارئ PDF: كل صفحة بتتعرض، والكتابة بتتحفظ لكل صفحة
class ReaderActivity : BaseInkActivity() {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    override fun pageTotal(): Int = renderer?.pageCount ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        val id = intent.getStringExtra("id") ?: run {
            finish()
            return
        }
        doc = Store.load(this, id, false)
        pfd = ParcelFileDescriptor.open(Store.pdfFile(this, id), ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd!!)
        buildUi()
        view.post { goPage(1) }
    }

    override fun showPageBitmap(n: Int) {
        val r = renderer ?: return
        val page = r.openPage(n - 1)
        val w = (view.width.coerceAtLeast(800) * 1.4f).toInt().coerceAtMost(1700)
        val h = (w * page.height.toFloat() / page.width).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        view.pageBitmap = bmp
        view.bgColor = Color.WHITE
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.close()
        pfd?.close()
    }
}
