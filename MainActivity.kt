package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// المكتبة: كروت زجاجية لكل PDF وسبورة، بأيقونات واضحة
class MainActivity : Activity() {
    private val reqPdf = 7
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#111A33"))
        )

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 14))
        }

        // الرأس: الشعار + العنوان + أزرار الإضافة (بالرموز)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#6366F1"))).apply {
                cornerRadius = Ui.dp(this@MainActivity, 14).toFloat()
            }
            addView(IconView(this@MainActivity, "board", Color.WHITE), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 26), Ui.dp(this@MainActivity, 26), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 46), Ui.dp(this@MainActivity, 46))
        }
        val title = TextView(this).apply {
            text = "السبورة والكتب"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0)
        }
        header.addView(logo)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.iconWell(this, IconView(this, "pdf", Color.WHITE)) { pickPdf() })
        header.addView(Ui.iconWell(this, IconView(this, "plus", Color.WHITE)) { newBoard() })
        col.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp(this@MainActivity, 16))
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@MainActivity, 60), 0, 0)
            }
            empty.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x14FFFFFF)
                    setStroke(Ui.dp(this@MainActivity, 1), 0x33FFFFFF)
                }
                addView(IconView(this@MainActivity, "board", 0xAAFFFFFF.toInt()), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 44), Ui.dp(this@MainActivity, 44), Gravity.CENTER))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 110), Ui.dp(this@MainActivity, 110))
            })
            list.addView(empty)
        }
        pdfs.forEach { id ->
            val name = id.substringAfter("__", id)
            addCard("pdf", Color.parseColor("#38BDF8"), Color.parseColor("#6366F1"), name, "PDF", { openReader(id) }, { confirmDelete(id, false, name) })
        }
        boards.forEachIndexed { i, id ->
            val name = "سبورة ${i + 1}"
            addCard("board", Color.parseColor("#A78BFA"), Color.parseColor("#EC4899"), name, "سبورة فاضية", { openBoard(id) }, { confirmDelete(id, true, name) })
        }
    }

    private fun addCard(icon: String, c1: Int, c2: Int, title: String, sub: String, onOpen: () -> Unit, onDelete: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(this@MainActivity, 18).toFloat()
                setColor(0x14FFFFFF)
                setStroke(Ui.dp(this@MainActivity, 1), 0x22FFFFFF)
            }
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 12))
            setOnClickListener { onOpen() }
        }
        val tile = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
                cornerRadius = Ui.dp(this@MainActivity, 14).toFloat()
            }
            addView(IconView(this@MainActivity, icon, Color.WHITE), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 26), Ui.dp(this@MainActivity, 26), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 52), Ui.dp(this@MainActivity, 52))
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 14), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub
                setTextColor(0x99FFFFFF.toInt())
                textSize = 13f
            })
        }
        row.addView(tile)
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.iconWell(this, IconView(this, "trash", Color.parseColor("#FCA5A5"))) { onDelete() })
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
