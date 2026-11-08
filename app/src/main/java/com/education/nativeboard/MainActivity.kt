package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// الشاشة الرئيسية: هيدر بالساعة، وأربع تبويبات: القارئ، المكتبات، السبورات، الإعدادات
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var body: LinearLayout
    private lateinit var tabBar: LinearLayout
    private var tab = 0
    private var clockTime: TextView? = null
    private var clockDate: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fmtTime = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    private val fmtDate = SimpleDateFormat("EEEE d MMMM", Locale("ar"))
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clockTime?.text = fmtTime.format(now)
            clockDate?.text = fmtDate.format(now)
            handler.postDelayed(this, 1000L)
        }
    }

    private val INK: Int get() = if (Prefs.dark(this)) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()
    private val MUTED = 0xFF64748B.toInt()
    private val LINE: Int get() = if (Prefs.dark(this)) 0xFF334155.toInt() else 0xFFE2E8F0.toInt()
    private val SURFACE: Int get() = when {
        Prefs.dark(this) && Prefs.glass(this) -> 0x661E293B
        Prefs.dark(this) -> 0xFF1E293B.toInt()
        Prefs.glass(this) -> 0x99FFFFFF.toInt()
        else -> Color.WHITE
    }
    private val BG: Int get() = if (Prefs.dark(this)) 0xFF0B1020.toInt() else 0xFFF1F5F9.toInt()
    private val NAVY_A: Int get() = Prefs.accentA(this)
    private val NAVY_B: Int get() = Prefs.accentB(this)
    private val BOARD_A = 0xFFA78BFA.toInt()
    private val BOARD_B = 0xFFEC4899.toInt()

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        root = FrameLayout(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(body)
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(0, 0, 0, dp(84))
        })
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(6), dp(6), dp(6), dp(6))
        root.addView(tabBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        root.setBackgroundColor(BG)
        render()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
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
        when (tab) {
            0 -> readerTab(libs, books)
            1 -> librariesTab(libs)
            2 -> boardsTab(boards)
            else -> settingsTab()
        }
        body.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))
        renderTabBar()
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

    private fun lift(v: View, e: Int) {
        v.elevation = dp(e).toFloat()
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

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

    private fun emptyText(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 13f
        setTextColor(MUTED)
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(24), dp(20), dp(8))
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
        titles.addView(label("السبورة والكتب", 24f, Color.WHITE, true))
        titles.addView(label("$books كتاب  ·  $libs مكتبة  ·  $boards سبورة", 12f, 0xCCFFFFFF.toInt()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(clockBlock())
        h.addView(top)
        return h
    }

    private fun clockBlock(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.END
        val t = label(fmtTime.format(Date()), 20f, Color.WHITE, true)
        t.gravity = Gravity.END
        val d = label(fmtDate.format(Date()), 11f, 0xCCFFFFFF.toInt())
        d.gravity = Gravity.END
        clockTime = t
        clockDate = d
        col.addView(t)
        col.addView(d)
        return col
    }

    // ---------- شريط التبويبات السفلي ----------

    private fun renderTabBar() {
        tabBar.removeAllViews()
        tabBar.background = roundBg(if (Prefs.dark(this)) 0xF20F172A.toInt() else 0xF2FFFFFF.toInt(), 26)
        lift(tabBar, 8)
        val items = listOf("book" to "القارئ", "library" to "المكتبات", "board" to "السبورات", "settings" to "الإعدادات")
        items.forEachIndexed { i, pair ->
            val sel = i == tab
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(dp(4), dp(8), dp(4), dp(8))
            item.background = roundBg(if (sel) NAVY_B else 0x00000000, 20)
            ripple(item)
            item.setOnClickListener {
                tab = i
                render()
            }
            item.addView(IconView(this, pair.first, if (sel) Color.WHITE else MUTED), LinearLayout.LayoutParams(dp(22), dp(22)))
            item.addView(label(pair.second, 11f, if (sel) Color.WHITE else MUTED, sel).apply {
                setPadding(0, dp(2), 0, 0)
            })
            tabBar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
    }

    // ---------- تبويب القارئ: متابعة القراءة + كل الكتب ----------

    private fun readerTab(libs: List<Lib>, books: Int) {
        val prefs = getSharedPreferences("home", MODE_PRIVATE)
        val last = prefs.getString("last", null)
        val type = prefs.getInt("type", 0)
        if (last != null && (type == 1 || Store.pdfFile(this, last).isFile)) {
            val page = getSharedPreferences("reading", MODE_PRIVATE).getInt(last, 1)
            body.addView(inset(continueCard(last, type, page)))
        }
        body.addView(sectionTitle("كل الكتب", "$books كتاب"))
        val all = libs.flatMap { it.books }
        if (all.isEmpty()) {
            body.addView(emptyText("مفيش كتب لسه. ضيف كتاب PDF من تبويب المكتبات."))
            return
        }
        all.forEach { body.addView(inset(bookRow(it))) }
    }

    private fun bookRow(id: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(12), dp(10))
        background = roundBg(SURFACE, 18)
        lift(this, 2)
        ripple(this)
        setOnClickListener { openReader(id) }
        addView(coverView(this@MainActivity, id, 40, 56))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label(id.substringAfter("__", id), 15f, INK, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val page = getSharedPreferences("reading", MODE_PRIVATE).getInt(id, 1)
            addView(label("صفحة $page", 12f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(IconView(this@MainActivity, "next", NAVY_B), LinearLayout.LayoutParams(dp(20), dp(20)))
    }

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

    private fun sectionTitle(title: String, trailing: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(12))
        addView(label(title, 18f, INK, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(trailing, 13f, MUTED))
    }

    // ---------- تبويب المكتبات: الرفوف + استيراد PDF + المكتبة العلمية ----------

    private fun librariesTab(libs: List<Lib>) {
        body.addView(sectionTitle("رفوف المكتبات", "${libs.size}/${Library.MAX_LIBS}"))
        body.addView(shelfRow(libs))
        body.addView(inset(genericCard("pdf", "استيراد كتاب PDF", "اختار مكتبة وضيف ملف PDF فيها") { chooseLibraryForBook() }))
        body.addView(inset(genericCard("library", "المكتبة العلمية", "صور للشرح، وإضافة صورك من جهازك") {
            startActivity(Intent(this, ScienceLibraryActivity::class.java))
        }))
    }

    private fun genericCard(icon: String, title: String, sub: String, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(14), dp(12), dp(14))
        background = roundBg(SURFACE, 20)
        lift(this, 2)
        ripple(this)
        setOnClickListener { onClick() }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(NAVY_A, NAVY_B)).apply {
                cornerRadius = dp(12).toFloat()
            }
            addView(IconView(this@MainActivity, icon, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label(title, 15f, INK, true))
            addView(label(sub, 12f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

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

    // ---------- تبويب السبورات ----------

    private fun boardsTab(boards: List<String>) {
        body.addView(sectionTitle("السبورات", "${boards.size}"))
        body.addView(boardsGrid(boards))
    }

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
            addView(label("اضغط للفتح · مطوّل للخيارات", 11f, MUTED))
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

    // ---------- تبويب الإعدادات: الوضع + الثيم + الزجاجي ----------

    private fun settingsTab() {
        body.addView(sectionTitle("الوضع", ""))
        body.addView(inset(modeRow()))
        body.addView(sectionTitle("ثيم الألوان", ""))
        body.addView(inset(accentRow()))
        body.addView(sectionTitle("التأثيرات", ""))
        body.addView(inset(glassRow()))
    }

    private fun modeRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val names = listOf("تلقائي", "نهاري", "ليلي")
        names.forEachIndexed { i, name ->
            val sel = Prefs.mode(this) == i
            row.addView(label(name, 14f, if (sel) Color.WHITE else INK, sel).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                background = roundBg(if (sel) NAVY_B else SURFACE, 16)
                ripple(this)
                setOnClickListener {
                    Prefs.setMode(this@MainActivity, i)
                    recreate()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        return row
    }

    private fun accentRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(0, dp(8), 0, dp(8))
        for (i in 0 until Prefs.ACCENT_COUNT) {
            val sel = Prefs.accent(this) == i
            val dot = FrameLayout(this)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Prefs.accentColor(i))
                if (sel) setStroke(dp(3), INK)
            }
            dot.setOnClickListener {
                Prefs.setAccent(this, i)
                recreate()
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(8), 0, dp(8), 0) })
        }
        return row
    }

    private fun glassRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(14), dp(12), dp(14))
        row.background = roundBg(SURFACE, 18)
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("الوضع الزجاجي", 15f, INK, true))
            addView(label("كروت وأشرطة شفافة بإطار مضيء", 12f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = Prefs.glass(this)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.setGlass(this, v)
            render()
        }
        row.addView(sw)
        return row
    }

    // ---------- الأفعال ----------

    private fun itemMenu(title: String, onRename: () -> Unit, onDelete: () -> Unit) {
        Pickers.choose(this, title, listOf("note" to "إعادة تسمية", "trash" to "حذف"), INK) { w ->
            if (w == 0) onRename() else onDelete()
        }
    }

    private fun renameDialog(current: String, onSave: (String) -> Unit) {
        val input = android.widget.EditText(this)
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

    private fun chooseLibraryForBook() {
        if (Library.all(this).isEmpty()) Library.create(this, "مكتبتي")
        val libs = Library.all(this)
        val cards = libs.map { lib ->
            val preview: View = if (lib.books.isNotEmpty()) {
                coverView(this, lib.books[0], 110, 140)
            } else {
                Pickers.iconPreview(this, "library", NAVY_B)
            }
            preview to "${lib.name}  (${lib.books.size}/${Library.MAX_BOOKS})"
        }
        Pickers.grid(this, "اختار المكتبة", cards) { w ->
            startActivity(Intent(this, ShelfActivity::class.java)
                .putExtra("idx", libs[w].idx)
                .putExtra("pick", true))
        }
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
