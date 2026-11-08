package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// الصفحة الرئيسية: هيدر متدرج بالإحصائيات، إجراءات سريعة، متابعة القراءة،
// رفوف المكتبات بالأغلفة، وشبكة السبورات
class MainActivity : Activity() {
    private lateinit var body: LinearLayout

    private val INK = 0xFF0F172A.toInt()
    private val MUTED = 0xFF64748B.toInt()
    private val LINE = 0xFFE2E8F0.toInt()
    private val SURFACE = Color.WHITE
    private val BG = 0xFFF1F5F9.toInt()
    private val NAVY_A = 0xFF1E3A8A.toInt()
    private val NAVY_B = 0xFF2563EB.toInt()
    private val BOARD_A = 0xFFA78BFA.toInt()
    private val BOARD_B = 0xFFEC4899.toInt()

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(body)
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
        body.removeAllViews()
        val libs = Library.all(this)
        val boards = Store.listBoards(this)
        val books = libs.sumOf { it.books.size }
        body.addView(header(libs.size, books, boards.size))

        val prefs = getSharedPreferences("home", MODE_PRIVATE)
        val last = prefs.getString("last", null)
        val type = prefs.getInt("type", 0)
        if (last != null && (type == 1 || Store.pdfFile(this, last).isFile)) {
            val page = getSharedPreferences("reading", MODE_PRIVATE).getInt(last, 1)
            body.addView(inset(continueCard(last, type, page)))
        }

        body.addView(sectionTitle("رفوف المكتبات", "${libs.size}/${Library.MAX_LIBS}"))
        body.addView(shelfRow(libs))
        body.addView(sectionTitle("السبورات", "${boards.size}"))
        body.addView(boardsGrid(boards))
        body.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
    }

    // ---------- عناصر مشتركة ----------

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun roundBg(color: Int, r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(color)
    }

    private fun dashedBg(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(0x0AFFFFFF)
        setStroke(dp(1), 0xFF94A3B8.toInt(), dp(6).toFloat(), dp(4).toFloat())
    }

    // ظل خفيف يتبع شكل الكارت
    private fun lift(v: View, e: Int) {
        v.elevation = dp(e).toFloat()
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    // تأثير الضغط (ripple)
    private fun ripple(v: View) {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        v.foreground = getDrawable(tv.resourceId)
    }

    private fun inset(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(6), dp(16), dp(6))
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ---------- الهيدر ----------

    private fun header(libs: Int, books: Int, boards: Int): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setPadding(dp(20), dp(44), dp(20), dp(22))
        h.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(NAVY_A, NAVY_B)).apply {
            val r = dp(28).toFloat()
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label("السبورة والكتب", 26f, Color.WHITE, true))
        titles.addView(label("$books كتاب  ·  $libs مكتبة  ·  $boards سبورة", 13f, 0xCCFFFFFF.toInt()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(glassBtn("plus") { newLibrary() })
        h.addView(top)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(quickTile("pdf", "كتاب جديد") { chooseLibraryForBook() }, quickParams(0, 6))
        actions.addView(quickTile("board", "سبورة جديدة") { newBoard() }, quickParams(6, 6))
        actions.addView(quickTile("library", "مكتبة جديدة") { newLibrary() }, quickParams(6, 0))
        h.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(20), 0, 0)
        })
        return h
    }

    private fun quickParams(left: Int, right: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(left), 0, dp(right), 0)
        }

    private fun glassBtn(icon: String, onClick: () -> Unit): View = FrameLayout(this).apply {
        background = roundBg(0x33FFFFFF, 22)
        ripple(this)
        setOnClickListener { onClick() }
        addView(IconView(this@MainActivity, icon, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun quickTile(icon: String, text: String, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(14), dp(10), dp(14))
        background = roundBg(0x26FFFFFF, 18)
        ripple(this)
        setOnClickListener { onClick() }
        addView(IconView(this@MainActivity, icon, Color.WHITE), LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(label(text, 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
    }

    // ---------- متابعة القراءة ----------

    private fun continueCard(id: String, type: Int, page: Int): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(12), dp(12), dp(14), dp(12))
        card.background = roundBg(SURFACE, 20)
        lift(card, 3)
        ripple(card)
        val thumb: View = if (type == 0) {
            coverView(this, id, 52, 72)
        } else {
            FrameLayout(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BOARD_A, BOARD_B)).apply {
                    cornerRadius = dp(10).toFloat()
                }
                addView(IconView(this@MainActivity, "board", Color.WHITE), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            }
        }
        card.addView(thumb, LinearLayout.LayoutParams(dp(52), dp(72)))
        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.setPadding(dp(14), 0, dp(8), 0)
        info.addView(label("متابعة القراءة", 11f, MUTED))
        info.addView(label(if (type == 0) id.substringAfter("__", id) else Names.board(this, id, "السبورة"), 16f, INK, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
        if (type == 0) info.addView(label("صفحة $page", 13f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(IconView(this, "next", NAVY_B), LinearLayout.LayoutParams(dp(22), dp(22)))
        card.setOnClickListener {
            if (type == 0) openReader(id) else openBoard(id)
        }
        return card
    }

    // ---------- الأقسام ----------

    private fun sectionTitle(title: String, trailing: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(12))
        addView(label(title, 18f, INK, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(trailing, 13f, MUTED))
    }

    // رفوف المكتبات: صف أفقي بكروت بالأغلفة
    private fun shelfRow(libs: List<Lib>): View {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.setPadding(dp(16), dp(4), dp(4), dp(8))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        libs.forEach { row.addView(libCard(it)) }
        if (libs.size < Library.MAX_LIBS) row.addView(addLibTile())
        scroll.addView(row)
        return scroll
    }

    private fun libCard(lib: Lib): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.background = roundBg(SURFACE, 22)
        lift(card, 4)
        ripple(card)
        card.layoutParams = LinearLayout.LayoutParams(dp(150), dp(264)).apply { setMargins(0, 0, dp(12), dp(8)) }

        val cover: View = if (lib.books.isNotEmpty()) {
            coverView(this, lib.books[0], 126, 168)
        } else {
            FrameLayout(this).apply {
                background = roundBg(0xFFEFF6FF.toInt(), 8)
                addView(IconView(this@MainActivity, "library", NAVY_B), FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            }
        }
        card.addView(cover, LinearLayout.LayoutParams(dp(126), dp(168)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        card.addView(label(lib.name, 15f, INK, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        })
        card.addView(progressBar(lib.books.size))
        card.addView(label("${lib.books.size}/${Library.MAX_BOOKS} كتاب", 12f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        card.setOnClickListener {
            startActivity(Intent(this, ShelfActivity::class.java).putExtra("idx", lib.idx))
        }
        card.setOnLongClickListener {
            itemMenu(lib.name, { renameDialog(lib.name) { n -> Library.rename(this, lib.idx, n); render() } }, { confirmDeleteLib(lib) })
            true
        }
        return card
    }

    // شريط امتلاء الرف
    private fun progressBar(n: Int): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = roundBg(LINE, 3)
        val fill = View(this)
        fill.background = roundBg(NAVY_B, 3)
        bar.addView(fill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, n.toFloat().coerceAtLeast(0.0001f)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (Library.MAX_BOOKS - n).toFloat()))
        bar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply { setMargins(0, dp(10), 0, 0) }
        return bar
    }

    private fun addLibTile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = dashedBg(22)
        ripple(this)
        layoutParams = LinearLayout.LayoutParams(dp(150), dp(264)).apply { setMargins(0, 0, dp(12), dp(8)) }
        setOnClickListener { newLibrary() }
        addView(FrameLayout(this@MainActivity).apply {
            background = roundBg(0x1A2563EB, 26)
            addView(IconView(this@MainActivity, "plus", NAVY_B), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(label("مكتبة جديدة", 13f, NAVY_B, true).apply { setPadding(0, dp(10), 0, 0) })
    }

    // شبكة السبورات: كارتين في الصف
    private fun boardsGrid(boards: List<String>): View {
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        val items = ArrayList<View>()
        boards.forEachIndexed { i, id -> items.add(boardTile(i, id)) }
        items.add(newBoardTile())
        for (pair in items.chunked(2)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (v in pair) {
                row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            grid.addView(row)
        }
        return inset(grid)
    }

    private fun boardTile(i: Int, id: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundBg(SURFACE, 22)
        lift(this, 4)
        ripple(this)
        setOnClickListener { openBoard(id) }
        setOnLongClickListener {
            val cur = Names.board(this@MainActivity, id, "سبورة ${i + 1}")
            itemMenu(cur, { renameDialog(cur) { n -> Names.setBoard(this@MainActivity, id, n); render() } }, { confirmDeleteBoard(i, id) })
            true
        }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BOARD_A, BOARD_B)).apply {
                val r = dp(22).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(IconView(this@MainActivity, "board", Color.WHITE), FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(label(Names.board(this@MainActivity, id, "سبورة ${i + 1}"), 15f, INK, true))
            addView(label("اضغط للفتح · مطوّل للحذف", 11f, MUTED))
        })
    }

    private fun newBoardTile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = dashedBg(22)
        ripple(this)
        minimumHeight = dp(150)
        setOnClickListener { newBoard() }
        addView(FrameLayout(this@MainActivity).apply {
            background = roundBg(0x1AEC4899, 26)
            addView(IconView(this@MainActivity, "plus", BOARD_B), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(label("سبورة جديدة", 13f, BOARD_B, true).apply { setPadding(0, dp(10), 0, 0) })
    }

    // ---------- الأفعال ----------

    // قايمة الضغط المطوّل: إعادة تسمية أو حذف
    private fun itemMenu(title: String, onRename: () -> Unit, onDelete: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf("إعادة تسمية", "حذف")) { _, w -> if (w == 0) onRename() else onDelete() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun renameDialog(current: String, onSave: (String) -> Unit) {
        val input = EditText(this)
        input.setText(current)
        input.setSelection(current.length)
        input.setPadding(dp(20), dp(12), dp(20), dp(12))
        AlertDialog.Builder(this)
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) onSave(name)
            }
            .setNegativeButton("إلغاء", null)
            .show()
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

    // كتاب جديد: بنختار المكتبة الأول، وبعدين بنفتح ملف الـ PDF
    private fun chooseLibraryForBook() {
        if (Library.all(this).isEmpty()) Library.create(this, "مكتبتي")
        val libs = Library.all(this)
        val names = libs.map { "${it.name}  (${it.books.size}/${Library.MAX_BOOKS})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختار المكتبة")
            .setItems(names) { _, w ->
                startActivity(Intent(this, ShelfActivity::class.java)
                    .putExtra("idx", libs[w].idx)
                    .putExtra("pick", true))
            }
            .setNegativeButton("إلغاء", null)
            .show()
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

    private fun confirmDeleteBoard(i: Int, id: String) {
        AlertDialog.Builder(this)
            .setMessage("هتتحذف \"سبورة ${i + 1}\" وكل كتابتها. متأكد؟")
            .setPositiveButton("حذف") { _, _ ->
                Store.delete(this, id, true)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
