package com.education.nativeboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

// سبورة فاضية بصفحات (تبويبات)، ولون خلفية، وتسطير، ومكتبة علمية (جاهزة + صور المدرس)
class BoardActivity : BaseInkActivity() {

    companion object {
        private const val REQ_IMG = 21
        private const val ADD = "__add__"
        private const val REMOVE = "__remove__"
    }

    override fun pageTotal(): Int = doc.pageCount

    override fun usesTabs(): Boolean = true

    override fun docTitle(): String = Names.board(this, doc.id, "سبورة")

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        val id = intent.getStringExtra("id") ?: run {
            finish()
            return
        }
        doc = Store.load(this, id, true)
        getSharedPreferences("home", MODE_PRIVATE).edit().putString("last", id).putInt("type", 1).apply()
        if (doc.pageCount < 1) doc.pageCount = 1
        buildUi()
        view.post { goPage(1) }
    }

    override fun showPageBitmap(n: Int) {
        view.bgColor = if (doc.bg == -1) Color.WHITE else doc.bg
        val pf = getSharedPreferences("board", MODE_PRIVATE)
        view.pattern = pf.getInt("pat_" + doc.id, 0)
        view.patternLevel = pf.getInt("lvl_" + doc.id, 1)
        val key = doc.images[n]
        view.pageBitmap = if (key == null) null else SciLibrary.load(this, key)
    }

    // الأزرار الإضافية للسبورة (بكلام صغير في الوضع العادي)
    override fun extraButtons(row: LinearLayout) {
        row.addView(ibtn("plus") { addPage() })
        row.addView(ibtn("palette") { pickBackground() })
        row.addView(ibtn("library") { pickFromLibrary() })
        row.addView(ibtn("lines") { pickPattern() })
    }

    private fun addPage() {
        doc.pageCount += 1
        markDirty()
        goPage(doc.pageCount)
    }

    private fun pickBackground() {
        Glass.swatchDialog(this, "", Ui.BG_PALETTE, doc.bg) { c ->
            doc.bg = c
            view.bgColor = c
            if (Ui.isDark(c) && Ui.isDark(view.ink)) view.ink = Color.WHITE
            if (!Ui.isDark(c) && view.ink == Color.WHITE) view.ink = Color.BLACK
            markDirty()
            view.invalidate()
        }
    }

    // التسطير: ساده أو سطور أو مربعات أو نقط، وبعدين الحجم
    private fun pickPattern() {
        val kinds = listOf("ساده", "سطور", "مربعات", "نقط")
        val cards = kinds.mapIndexed { k, name -> Pickers.patternPreview(this, k, 1) to name }
        Pickers.grid(this, "نوع التسطير", cards) { k ->
            val levels = listOf("صغير", "وسط", "كبير")
            val lc = levels.mapIndexed { l, name -> Pickers.patternPreview(this, k, l) to name }
            Pickers.grid(this, "حجم التسطير", lc) { l ->
                getSharedPreferences("board", MODE_PRIVATE).edit()
                    .putInt("pat_" + doc.id, k)
                    .putInt("lvl_" + doc.id, l)
                    .apply()
                view.pattern = k
                view.patternLevel = l
                view.invalidate()
            }
        }
    }

    // الصور الجاهزة من ملف المكتبة (library.json): اسم الملف + العنوان
    private fun readAssetLibrary(): List<Pair<String, String>> = try {
        val arr = org.json.JSONArray(assets.open("library/library.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("file") to o.getString("title")
        }
    } catch (e: Exception) {
        emptyList()
    }

    // المكتبة العلمية: صور جاهزة + صور المدرس + إضافة صورة من الجهاز
    private fun pickFromLibrary() {
        val cards = ArrayList<Pair<View, String>>()
        val actions = ArrayList<() -> Unit>()
        cards.add(Pickers.iconPreview(this, "plus", ICON) to "إضافة صورة من جهازي")
        actions.add { pickImageFile() }
        if (doc.images[pageNo] != null) {
            cards.add(Pickers.iconPreview(this, "eraser", ICON) to "إزالة من الصفحة")
            actions.add { removeImage() }
        }
        readAssetLibrary().forEach { (file, title) ->
            cards.add(Pickers.imagePreview(this, file) to title)
            actions.add { setPageImage(file) }
        }
        SciLibrary.userImages(this).forEach { key ->
            cards.add(Pickers.imagePreview(this, key) to SciLibrary.title(key))
            actions.add { setPageImage(key) }
        }
        cards.add(Pickers.iconPreview(this, "trash", ICON) to "حذف صورة من جهازي")
        actions.add { deleteUserImage() }
        Pickers.grid(this, "المكتبة العلمية", cards) { i -> actions[i]() }
    }

    private fun setPageImage(key: String) {
        doc.images[pageNo] = key
        markDirty()
        goPage(pageNo)
    }

    private fun removeImage() {
        doc.images.remove(pageNo)
        markDirty()
        goPage(pageNo)
    }

    private fun pickImageFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, REQ_IMG)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_IMG && resultCode == RESULT_OK && uri != null) {
            val key = SciLibrary.importImage(this, uri)
            if (key != null) {
                doc.images[pageNo] = key
                markDirty()
                goPage(pageNo)
                Toast.makeText(this, "اتضافت الصورة للمكتبة", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ما قدرناش نفتح الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteUserImage() {
        val users = SciLibrary.userImages(this)
        if (users.isEmpty()) {
            Toast.makeText(this, "مفيش صور مضافة من جهازك", Toast.LENGTH_SHORT).show()
            return
        }
        val cards = users.map { Pickers.imagePreview(this, it) to SciLibrary.title(it) }
        Pickers.grid(this, "اختار الصورة اللي تتحذف", cards) { w -> SciLibrary.delete(this, users[w]) }
    }
}
