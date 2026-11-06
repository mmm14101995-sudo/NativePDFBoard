package com.education.nativeboard

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        view.bgColor = if (doc.bg == -1) Color.WHITE else doc.bg
        val file = doc.images[n]
        view.pageBitmap = if (file == null) null else loadLibraryImage(file)
    }

    // تحميل الصورة بدقة تناسب الشاشة (مش أصغر ومش أكبر من اللازم)
    private fun loadLibraryImage(file: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open("library/$file").use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            val target = 2048
            var sample = 1
            while (maxSide / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            assets.open("library/$file").use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    override fun extraButtons(row: LinearLayout) {
        row.addView(Ui.btn(this, "+ صفحة") { addPage() })
        row.addView(Ui.btn(this, "لون السبورة") { pickBackground() })
        row.addView(Ui.btn(this, "مكتبة علمية") { pickFromLibrary() })
    }

    private fun addPage() {
        doc.pageCount += 1
        markDirty()
        goPage(doc.pageCount)
    }

    private fun pickBackground() {
        Glass.swatchDialog(this, "لون السبورة", Ui.BG_PALETTE, doc.bg) { c ->
            doc.bg = c
            view.bgColor = c
            if (Ui.isDark(c) && Ui.isDark(view.ink)) view.ink = Color.WHITE
            if (!Ui.isDark(c) && view.ink == Color.WHITE) view.ink = Color.BLACK
            markDirty()
            view.invalidate()
        }
    }

    private fun pickFromLibrary() {
        val items = try {
            val arr = org.json.JSONArray(assets.open("library/library.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("المكتبة فاضية حالياً. ضيف صور في assets/library وسجّلها في library.json.")
                .setPositiveButton("تمام", null)
                .show()
            return
        }
        val titles = items.map { it.getString("title") as CharSequence }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("المكتبة العلمية")
            .setItems(titles) { _, which ->
                doc.images[pageNo] = items[which].getString("file")
                markDirty()
                goPage(pageNo)
            }
            .show()
    }
}
