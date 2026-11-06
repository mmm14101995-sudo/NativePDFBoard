package com.education.nativeboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Lib(val idx: Int, val name: String, val books: ArrayList<String>)

// المكتبات: حتى 10 مكتبات، وكل مكتبة فيها حتى 20 كتاب
object Library {
    const val MAX_LIBS = 10
    const val MAX_BOOKS = 20

    private fun dir(ctx: Context): File = File(ctx.filesDir, "libs").apply { mkdirs() }
    private fun metaFile(ctx: Context, idx: Int): File = File(dir(ctx), "lib$idx.json")

    fun coverFile(ctx: Context, id: String): File =
        File(File(ctx.filesDir, "covers").apply { mkdirs() }, "$id.png")

    fun all(ctx: Context): List<Lib> {
        migrate(ctx)
        val out = ArrayList<Lib>()
        for (i in 0 until MAX_LIBS) {
            val f = metaFile(ctx, i)
            if (!f.isFile) continue
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("books") ?: JSONArray()
            val books = ArrayList<String>()
            for (k in 0 until arr.length()) books.add(arr.getString(k))
            out.add(Lib(i, o.optString("name", "مكتبة"), books))
        }
        return out
    }

    fun get(ctx: Context, idx: Int): Lib? = all(ctx).firstOrNull { it.idx == idx }

    fun create(ctx: Context, name: String): Int? {
        migrate(ctx)
        val free = (0 until MAX_LIBS).firstOrNull { !metaFile(ctx, it).isFile } ?: return null
        save(ctx, free, name, ArrayList())
        return free
    }

    private fun save(ctx: Context, idx: Int, name: String, books: List<String>) {
        metaFile(ctx, idx).writeText(JSONObject().put("name", name).put("books", JSONArray(books)).toString())
    }

    fun addBook(ctx: Context, idx: Int, id: String): Boolean {
        val lib = get(ctx, idx) ?: return false
        if (lib.books.size >= MAX_BOOKS) return false
        lib.books.add(id)
        save(ctx, idx, lib.name, lib.books)
        return true
    }

    fun removeBook(ctx: Context, idx: Int, id: String) {
        val lib = get(ctx, idx) ?: return
        lib.books.remove(id)
        save(ctx, idx, lib.name, lib.books)
        Store.delete(ctx, id, false)
        coverFile(ctx, id).delete()
    }

    fun deleteLibrary(ctx: Context, idx: Int) {
        val lib = get(ctx, idx) ?: return
        for (id in lib.books) {
            Store.delete(ctx, id, false)
            coverFile(ctx, id).delete()
        }
        metaFile(ctx, idx).delete()
    }

    // أول مرة بس: الـ PDF اللي كانت موجودة قبل المكتبات بتتحط في "مكتبتي"
    private fun migrate(ctx: Context) {
        val flag = File(dir(ctx), "migrated")
        if (flag.isFile) return
        flag.writeText("1")
        val pdfs = Store.listPdfs(ctx)
        if (pdfs.isNotEmpty() && !metaFile(ctx, 0).isFile) {
            save(ctx, 0, "مكتبتي", pdfs.take(MAX_BOOKS))
        }
    }

    // صورة الغلاف: أول صفحة من الـ PDF، بتتعمل مرة واحدة وبتتحفظ
    fun makeCover(ctx: Context, id: String) {
        val out = coverFile(ctx, id)
        if (out.isFile) return
        try {
            val pfd = ParcelFileDescriptor.open(Store.pdfFile(ctx, id), ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            val page = r.openPage(0)
            val w = 360
            val h = (w * page.height.toFloat() / page.width).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            r.close()
            pfd.close()
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        } catch (e: Exception) {
            // مفيش غلاف: هيتعرض رمز الكتاب بدل الصورة
        }
    }
}

// غلاف الكتاب: صورة مصغرة بزوايا مستديرة، ولو مفيش صورة بيظهر رمز الـ PDF
fun coverView(ctx: Context, id: String, wDp: Int, hDp: Int): FrameLayout {
    return FrameLayout(ctx).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1E293B.toInt(), 0xFF334155.toInt())).apply {
            cornerRadius = Ui.dp(ctx, 8).toFloat()
        }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        val file = Library.coverFile(ctx, id)
        if (file.isFile) {
            val bmp = BitmapFactory.decodeFile(file.path)
            if (bmp != null) {
                addView(ImageView(ctx).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        } else {
            addView(IconView(ctx, "pdf", 0xCCFFFFFF.toInt()), FrameLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 40), Gravity.CENTER))
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp))
    }
}
