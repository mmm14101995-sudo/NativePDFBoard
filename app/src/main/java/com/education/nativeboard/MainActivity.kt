package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// المكتبة: الـ PDF والسبورات، وإضافة وحذف
class MainActivity : Activity() {
    private val reqPdf = 7
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        val pad = Ui.dp(this, 16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b1120"))
            setPadding(pad, Ui.dp(this@MainActivity, 36), pad, pad)
        }

        val title = TextView(this).apply {
            text = "المكتبة"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 12))
        }
        root.addView(title)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(Ui.btn(this, "+ إضافة PDF") { pickPdf() })
        actions.addView(Ui.btn(this, "+ سبورة جديدة") { newBoard() })
        root.addView(actions)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            setPadding(0, Ui.dp(this@MainActivity, 12), 0, 0)
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }

    private fun refresh() {
        list.removeAllViews()
        val pdfs = Store.listPdfs(this)
        val boards = Store.listBoards(this)
        if (pdfs.isEmpty() && boards.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "لا توجد ملفات بعد.\nاضغط + إضافة PDF أو + سبورة جديدة."
                setTextColor(0xAAFFFFFF.toInt())
                textSize = 15f
                setPadding(0, Ui.dp(this@MainActivity, 40), 0, 0)
            })
        }
        pdfs.forEach { id ->
            addRow(id.substringAfter("__", id), "PDF", { openReader(id) }, { confirmDelete(id, false, id.substringAfter("__", id)) })
        }
        boards.forEachIndexed { i, id ->
            addRow("سبورة ${i + 1}", "سبورة فاضية", { openBoard(id) }, { confirmDelete(id, true, "سبورة ${i + 1}") })
        }
    }

    private fun addRow(title: String, sub: String, onOpen: () -> Unit, onDelete: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.pill(this@MainActivity, 0x14FFFFFF, 16)
            setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 12))
            setOnClickListener { onOpen() }
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(this).apply {
            this.text = title
            setTextColor(Color.WHITE)
            textSize = 17f
        })
        text.addView(TextView(this).apply {
            this.text = sub
            setTextColor(0x99FFFFFF.toInt())
            textSize = 13f
        })
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.btn(this, "حذف") { onDelete() })
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp(this, 10))
        list.addView(row, lp)
    }

    private fun confirmDelete(id: String, isBoard: Boolean, name: String) {
        AlertDialog.Builder(this)
            .setMessage("هيتحذف \"$name\" وكل كتابتك عليه. متأكد؟")
            .setPositiveButton("حذف") { _, _ ->
                Store.delete(this, id, isBoard)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun pickPdf() {
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
        if (requestCode == reqPdf && resultCode == RESULT_OK && uri != null) importPdf(uri)
    }

    private fun importPdf(uri: Uri) {
        val safe = displayName(uri).replace(Regex("[^\\p{L}\\p{N}._ \\-]"), "_").removeSuffix(".pdf")
        val id = System.currentTimeMillis().toString() + "__" + safe
        contentResolver.openInputStream(uri)?.use { input ->
            Store.pdfFile(this, id).outputStream().use { input.copyTo(it) }
        }
        openReader(id)
    }

    private fun displayName(uri: Uri): String {
        var name = "ملف.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
        }
        return name
    }

    private fun newBoard() {
        val id = "board_" + System.currentTimeMillis()
        Store.save(this, Doc(id, true).apply { pageCount = 1 })
        openBoard(id)
    }

    private fun openReader(id: String) {
        startActivity(Intent(this, ReaderActivity::class.java).putExtra("id", id))
    }

    private fun openBoard(id: String) {
        startActivity(Intent(this, BoardActivity::class.java).putExtra("id", id))
    }
}
