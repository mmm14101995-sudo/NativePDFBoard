package com.education.nativeboard

import android.graphics.Bitmap
import android.graphics.Matrix
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
        // الصورة بنفس نسبة الشاشة بالظبط، والـ PDF متوسّط جواها من غير ما يتقص
        val w = (view.width.coerceAtLeast(800) * 1.5f).toInt().coerceAtMost(2400)
        val h = (view.height.coerceAtLeast(1000) * 1.5f).toInt().coerceAtMost(3600)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val sc = minOf(w.toFloat() / page.width, h.toFloat() / page.height)
        val dx = (w - page.width * sc) / 2f
        val dy = (h - page.height * sc) / 2f
        val m = Matrix().apply {
            setScale(sc, sc)
            postTranslate(dx, dy)
        }
        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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
