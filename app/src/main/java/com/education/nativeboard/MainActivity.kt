package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// الشاشة الرئيسية: المكتبات (حتى 10) والسبورات
class MainActivity : Activity() {
    private lateinit var content: LinearLayout

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
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 10))
        }

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
        header.addView(Ui.iconWell(this, IconView(this, "plus", Color.WHITE)) { newLibrary() })
        col.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp(this@MainActivity, 16))
        })

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
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
        content.removeAllViews()

        // شبكة المكتبات: كل صف فيه مكتبتين
        val libs = Library.all(this)
        val cards = ArrayList<View>()
        libs.forEach { cards.add(libCard(it)) }
        if (libs.size < Library.MAX_LIBS) cards.add(addLibCard())
        cards.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { v ->
                row.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(Ui.dp(this@MainActivity, 5), 0, Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 10))
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            content.addView(row)
        }

        // قسم السبورات
        content.addView(sectionHeader("السبورات") { newBoard() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 10))
        })
        val boards = Store.listBoards(this)
        if (boards.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "لا توجد سبورات بعد"
                setTextColor(0x66FFFFFF.toInt())
                textSize = 13f
            })
        }
        boards.forEachIndexed { i, id ->
            val name = "سبورة ${i + 1}"
            content.addView(boardRow(name) { openBoard(id) } ?: View(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(this@MainActivity, 10))
            })
        }
    }

    private fun glass(radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = Ui.dp(this@MainActivity, radius).toFloat()
        setColor(0x14FFFFFF)
        setStroke(Ui.dp(this@MainActivity, 1), 0x22FFFFFF)
    }

    private fun libCard(lib: Lib): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(18)
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12))
            setOnClickListener { startActivity(Intent(this@MainActivity, ShelfActivity::class.java).putExtra("idx", lib.idx)) }
            setOnLongClickListener {
                confirmDeleteLib(lib)
                true
            }
        }
        val thumb: View = if (lib.books.isNotEmpty()) {
            coverView(this, lib.books[0], 110, 140)
        } else {
            FrameLayout(this).apply {
                addView(IconView(this@MainActivity, "library", 0x99FFFFFF.toInt()), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 44), Ui.dp(this@MainActivity, 44), Gravity.CENTER))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 110), Ui.dp(this@MainActivity, 140))
            }
        }
        card.addView(thumb)
        card.addView(TextView(this).apply {
            text = lib.name
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, Ui.dp(this@MainActivity, 10), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = "${lib.books.size}/${Library.MAX_BOOKS} كتاب"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12f
        })
        return card
    }

    private fun addLibCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = glass(18)
        setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12))
        addView(FrameLayout(this@MainActivity).apply {
            addView(IconView(this@MainActivity, "plus", 0xCCFFFFFF.toInt()), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 36), Ui.dp(this@MainActivity, 36), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 110), Ui.dp(this@MainActivity, 140))
        })
        setOnClickListener { newLibrary() }
    }

    private fun sectionHeader(text: String, onAdd: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Ui.iconWell(this@MainActivity, IconView(this@MainActivity, "plus", Color.WHITE)) { onAdd() })
    }

    private fun boardRow(name: String, onOpen: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = glass(16)
        setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 10))
        setOnClickListener { onOpen() }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#A78BFA"), Color.parseColor("#EC4899"))).apply {
                cornerRadius = Ui.dp(this@MainActivity, 12).toFloat()
            }
            addView(IconView(this@MainActivity, "board", Color.WHITE), FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 24), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 46), Ui.dp(this@MainActivity, 46))
        })
        addView(TextView(this@MainActivity).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Ui.iconWell(this@MainActivity, IconView(this@MainActivity, "trash", Color.parseColor("#FCA5A5"))) {
            confirmDeleteBoard(name)
        })
    }

    private fun newLibrary() {
        val libs = Library.all(this)
        if (libs.size >= Library.MAX_LIBS) {
            Toast.makeText(this, "وصلت للحد الأقصى (${Library.MAX_LIBS} مكتبات)", Toast.LENGTH_SHORT).show()
            return
        }
        Library.create(this, "مكتبة ${libs.size + 1}")
        render()
    }

    private fun confirmDeleteLib(lib: Lib) {
        AlertDialog.Builder(this)
            .setMessage("هتتحذف \"${lib.name}\" وكل كتبها. متأكد؟")
            .setPositiveButton("حذف") { _, _ ->
                Library.deleteLibrary(this, lib.idx)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDeleteBoard(name: String) {
        AlertDialog.Builder(this)
            .setMessage("هتتحذف \"$name\" وكل كتابتها. متأكد؟")
            .setPositiveButton("حذف") { _, _ ->
                val id = Store.listBoards(this).getOrNull(name.removePrefix("سبورة ").toIntOrNull()?.minus(1) ?: -1)
                if (id != null) Store.delete(this, id, true)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun newBoard() {
        val id = "board_" + System.currentTimeMillis()
        Store.save(this, Doc(id, true).apply { pageCount = 1 })
        openBoard(id)
    }

    private fun openBoard(id: String) {
        startActivity(Intent(this, BoardActivity::class.java).putExtra("id", id))
    }
}
