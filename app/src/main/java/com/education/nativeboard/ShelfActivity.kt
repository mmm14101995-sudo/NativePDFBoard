package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// رف المكتبة: الكتب بأغلفتها على الرفوف، وآخر صفحة في كل كتاب بتتفتح منها
class ShelfActivity : Activity() {
    private var idx = 0
    private val reqPdf = 11
    private lateinit var title: TextView
    private lateinit var shelves: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        idx = intent.getIntExtra("idx", 0)

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#111A33"))
        )
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ShelfActivity, 14), Ui.dp(this@ShelfActivity, 36), Ui.dp(this@ShelfActivity, 14), Ui.dp(this@ShelfActivity, 10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Ui.iconWell(this, IconView(this, "next", Color.WHITE)) { finish() })
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(Ui.dp(this@ShelfActivity, 12), 0, Ui.dp(this@ShelfActivity, 12), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.iconWell(this, IconView(this, "plus", Color.WHITE)) { pickBook() })
        col.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp(this@ShelfActivity, 12))
        })

        shelves = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(shelves) }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }

    private fun render() {
        shelves.removeAllViews()
        val lib = Library.get(this, idx)
        if (lib == null) {
            finish()
            return
        }
        title.text = "${lib.name}  ${lib.books.size}/${Library.MAX_BOOKS}"
        if (lib.books.isEmpty()) {
            shelves.addView(TextView(this).apply {
                text = "الرف فاضي. اضغط + لإضافة كتاب."
                setTextColor(0x99FFFFFF.toInt())
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@ShelfActivity, 60), 0, 0)
            })
        }
        lib.books.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setPadding(0, Ui.dp(this@ShelfActivity, 10), 0, 0)
            }
            group.forEach { id -> row.addView(bookView(id)) }
            shelves.addView(row)
            shelves.addView(plank())
        }
    }

    private fun bookView(id: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(this@ShelfActivity, 6), 0, Ui.dp(this@ShelfActivity, 6), 0)
        }
        val cover = coverView(this, id, 100, 140)
        cover.setOnClickListener {
            startActivity(Intent(this, ReaderActivity::class.java).putExtra("id", id))
        }
        cover.setOnLongClickListener {
            confirmRemove(id)
            true
        }
        col.addView(cover)
        col.addView(TextView(this).apply {
            text = id.substringAfter("__", id)
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@ShelfActivity, 104), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp(this@ShelfActivity, 6), 0, 0)
            }
        })
        return col
    }

    // الرف الخشب اللي الكتب واقفة عليه
    private fun plank(): View = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF7C4A2D.toInt(), 0xFFB07A4F.toInt(), 0xFF7C4A2D.toInt())).apply {
            cornerRadius = Ui.dp(this@ShelfActivity, 4).toFloat()
        }
        elevation = Ui.dp(this@ShelfActivity, 6).toFloat()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@ShelfActivity, 10)).apply {
            setMargins(Ui.dp(this@ShelfActivity, 8), 0, Ui.dp(this@ShelfActivity, 8), Ui.dp(this@ShelfActivity, 6))
        }
    }

    private fun confirmRemove(id: String) {
        AlertDialog.Builder(this)
            .setMessage("هيتحذف الكتاب وكل كتابتك عليه. متأكد؟")
            .setPositiveButton("حذف") { _, _ ->
                Library.removeBook(this, idx, id)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun pickBook() {
        val lib = Library.get(this, idx) ?: return
        if (lib.books.size >= Library.MAX_BOOKS) {
            Toast.makeText(this, "الرف ممتلئ (${Library.MAX_BOOKS} كتاب)", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(i, reqPdf)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == reqPdf && resultCode == RESULT_OK && uri != null) importBook(uri)
    }

    private fun importBook(uri: Uri) {
        val safe = displayName(uri).replace(Regex("[^\\p{L}\\p{N}._ \\-]"), "_").removeSuffix(".pdf")
        val id = System.currentTimeMillis().toString() + "__" + safe
        contentResolver.openInputStream(uri)?.use { input ->
            Store.pdfFile(this, id).outputStream().use { input.copyTo(it) }
        }
        Library.makeCover(this, id)
        if (!Library.addBook(this, idx, id)) Store.delete(this, id, false)
        render()
    }

    private fun displayName(uri: Uri): String {
        var name = "كتاب.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val k = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && k >= 0) name = c.getString(k) ?: name
        }
        return name
    }
}
