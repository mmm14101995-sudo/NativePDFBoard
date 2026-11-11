package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
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

// الشاشة الرئيسية — تصميم MS Board النهائي
// النهار: زجاج أبيض شفاف، الليل: أسود زجاجي
// شريط تبويبات عمودي جانبي على الشاشات الكبيرة، سفلي على التليفون
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var body: LinearLayout
    private lateinit var tabBar: LinearLayout
    private var tab = 0
    private var clockTime: TextView? = null
    private var clockDate: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fmtTime = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    private val fmtDate = SimpleDateFormat("EEEE، d MMMM", Locale("ar"))
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clockTime?.text = fmtTime.format(now)
            clockDate?.text = fmtDate.format(now)
            handler.postDelayed(this, 1000L)
        }
    }

    // ألوان تكيّفية حسب الوضع (نهار/ليل)
    private val dark get() = Prefs.dark(this)
    private val INK get() = if (dark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()
    private val MUTED get() = if (dark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt()
    private val SURFACE get() = when {
        dark && Prefs.glass(this) -> 0x44FFFFFF
        dark -> 0xFF1E293B.toInt()
        Prefs.glass(this) -> 0xAAFFFFFF.toInt()
        else -> 0xF8FFFFFF.toInt()
    }
    private val BG get() = if (dark) 0xFF050D1A.toInt() else 0xFFF0F4FA.toInt()
    private val ACCENT_A get() = Prefs.accentA(this)
    private val ACCENT_B get() = Prefs.accentB(this)
    private val BOARD_A = 0xFFA78BFA.toInt()
    private val BOARD_B = 0xFFEC4899.toInt()

    // الشاشة واسعة (تابلت أو سبورة): شريط جانبي، وإلا شريط سفلي
    private val isWide get() = resources.configuration.screenWidthDp >= 600

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun sp(v: Float) = (v * resources.displayMetrics.scaledDensity).toInt()

    private fun glassCard(r: Int, strong: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(r).toFloat()
            setColor(if (strong)
                (if (dark) 0x55FFFFFF else 0xBBFFFFFF.toInt())
            else
                (if (dark) 0x28FFFFFF else 0x99FFFFFF.toInt()))
            setStroke(dp(1), if (dark) 0x44FFFFFF else 0x33000000)
        }

    private fun solidCard(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(SURFACE)
        setStroke(dp(1), if (dark) 0x22FFFFFF else 0x14000000)
    }

    private fun accentGrad(r: Int, vararg cols: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, cols.toIntArray()).apply {
            cornerRadius = dp(r).toFloat()
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

    private fun txt(s: String, sp: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(c)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun inset(v: View, hDp: Int = 16, vDp: Int = 6): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(hDp), dp(vDp), dp(hDp), dp(vDp))
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun iconBtn(icon: String, tint: Int, sizeDp: Int = 44, onClick: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            setOnClickListener { onClick() }
            addView(IconView(this@MainActivity, icon, tint),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }

    // ======== دورة الحياة ========

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        Ui.immersive(this)
        buildLayout()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
        render()
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildLayout()
        render()
    }

    // ======== بناء الهيكل ========

    private fun buildLayout() {
        root = FrameLayout(this)
        root.setBackgroundColor(BG)
        if (dark) root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF050D1A.toInt(), 0xFF0A1526.toInt(), 0xFF050D1A.toInt()))

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }
        tabBar = LinearLayout(this)

        if (isWide) {
            // تابلت / سبورة: شريط جانبي
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tabBar.orientation = LinearLayout.VERTICAL
            tabBar.setPadding(dp(6), dp(10), dp(6), dp(10))
            row.addView(tabBar, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.MATCH_PARENT))
            row.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            root.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            // موبايل: شريط سفلي
            tabBar.orientation = LinearLayout.HORIZONTAL
            tabBar.setPadding(dp(6), dp(6), dp(6), dp(6))
            root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, 0, dp(76))
            })
            root.addView(tabBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(10), 0, dp(10), dp(10))
            })
        }
        setContentView(root)
    }

    // ======== العرض ========

    private fun render() {
        body.removeAllViews()
        val libs = Library.all(this)
        val boards = Store.listBoards(this)
        val books = libs.sumOf { it.books.size }
        body.addView(headerView(libs.size, books, boards.size))
        when (tab) {
            0 -> mainTab(libs, books, boards)
            1 -> librariesTab(libs)
            2 -> boardsTab(boards)
            else -> settingsTab()
        }
        body.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)))
        renderTabBar()
    }

    // ======== الهيدر ========

    private fun headerView(libs: Int, books: Int, boards: Int): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(44), dp(16), dp(14))
            background = if (dark) glassCard(0, strong = true)
            else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F4FA.toInt())).apply {
                setStroke(dp(1), 0x1A000000)
            }
        }
        // شعار + اسم
        val logo = FrameLayout(this).apply {
            background = accentGrad(14, ACCENT_A, ACCENT_B)
            addView(IconView(this@MainActivity, "board", Color.WHITE),
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        }
        bar.addView(logo, LinearLayout.LayoutParams(dp(46), dp(46)))
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        nameCol.addView(txt("MS Board", 18f, INK, true))
        nameCol.addView(txt("$books كتاب · $libs مكتبة · $boards سبورة", 11f, MUTED))
        bar.addView(nameCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // ساعة + تاريخ
        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassCard(24)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val timeView = txt(fmtTime.format(Date()), 18f, INK, true)
        clockTime = timeView
        clockCol.addView(IconView(this, "settings", MUTED), LinearLayout.LayoutParams(dp(18), dp(18)))
        clockCol.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 0) })
        clockCol.addView(timeView)
        val dateView = txt("  " + fmtDate.format(Date()), 11f, MUTED)
        clockDate = dateView
        val dateTimCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dateTimCol.addView(timeView)
        dateTimCol.addView(dateView)
        clockCol.removeAllViews()
        clockCol.addView(IconView(this, "settings", MUTED).apply {
            setOnClickListener { tab = 3; render() }
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        clockCol.addView(View(this), LinearLayout.LayoutParams(dp(10), 0))
        clockCol.addView(dateTimCol)
        bar.addView(clockCol)
        return bar
    }

    // ======== شريط التبويبات ========

    private fun renderTabBar() {
        tabBar.removeAllViews()
        tabBar.background = glassCard(28, strong = true)
        lift(tabBar, 8)
        val tabs = listOf(
            Triple("board", "الرئيسية", 0),
            Triple("library", "المكتبات", 1),
            Triple("book", "السبورات", 2),
            Triple("settings", "الإعدادات", 3)
        )
        tabs.forEach { (icon, name, i) ->
            val sel = i == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = if (sel) accentGrad(20, ACCENT_A, ACCENT_B)
                else GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(0x00000000)
                }
                isClickable = true
                ripple(this)
                setOnClickListener { tab = i; render() }
            }
            item.addView(IconView(this, icon, if (sel) Color.WHITE else MUTED),
                LinearLayout.LayoutParams(dp(22), dp(22)))
            if (!isWide) {
                item.addView(txt(name, 10f, if (sel) Color.WHITE else MUTED, sel).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                })
            }
            val lp = if (isWide)
                LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            else
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            tabBar.addView(item, lp)
        }
    }

    // ======== التبويب الرئيسي: بطاقتا السبورة والقارئ + شبكة الوصول السريع ========

    private fun mainTab(libs: List<Lib>, books: Int, boards: List<String>) {
        // آخر كتاب/سبورة مفتوحة
        val prefs = getSharedPreferences("home", MODE_PRIVATE)
        val lastId = prefs.getString("last", null)
        val lastType = prefs.getInt("type", 0)
        val hasLast = lastId != null && (lastType == 1 || Store.pdfFile(this, lastId).isFile)

        // بطاقتا السبورة وقارئ الكتب
        val heroRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }
        heroRow.addView(heroCard("board", "السبورة", "ارسم .. علّق .. أبدع",
            intArrayOf(ACCENT_A, ACCENT_B)) { newBoard() },
            LinearLayout.LayoutParams(0, dp(220), 1f).apply { setMargins(0, 0, dp(8), 0) })
        heroRow.addView(heroCard("book", "قارئ الكتب", "اقرأ واستمتع بالمعرفة",
            intArrayOf(0xFF0EA5E9.toInt(), 0xFF7C3AED.toInt())) { openFirstBook(libs) },
            LinearLayout.LayoutParams(0, dp(220), 1f))
        body.addView(heroRow)

        // بطاقة المتابعة
        if (hasLast) {
            val page = getSharedPreferences("reading", MODE_PRIVATE).getInt(lastId!!, 1)
            body.addView(inset(continueCard(lastId, lastType, page)))
        }

        // شبكة الوصول السريع
        body.addView(sectionHeader("الوصول السريع", ""))
        body.addView(quickGrid())
    }

    private fun heroCard(icon: String, title: String, sub: String,
                        cols: IntArray, onClick: () -> Unit): View =
        FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, cols).apply {
                cornerRadius = dp(24).toFloat()
            }
            lift(this, 6)
            isClickable = true
            ripple(this)
            setOnClickListener { onClick() }

            // طبقة زجاجية فوق
            addView(View(this@MainActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TR_BL,
                    intArrayOf(0x33FFFFFF, 0x00FFFFFF))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.START
                setPadding(dp(18), dp(18), dp(18), dp(18))
            }
            col.addView(IconView(this@MainActivity, icon, 0xCCFFFFFF.toInt()),
                LinearLayout.LayoutParams(dp(36), dp(36)))
            col.addView(txt(title, 22f, Color.WHITE, true).apply {
                setPadding(0, dp(10), 0, 0)
            })
            col.addView(txt(sub, 12f, 0xCCFFFFFF.toInt()).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
            // زرار "ابدأ الآن"
            val btn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(0x33FFFFFF)
                    setStroke(dp(1), 0x66FFFFFF)
                }
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            btn.addView(txt("ابدأ الآن", 13f, Color.WHITE, true))
            col.addView(btn)
            addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

    private fun quickGrid(): View {
        val items = listOf(
            Triple("library", "المكتبات") { tab = 1; render() },
            Triple("pdf", "ملفات PDF") { chooseLibraryForBook() },
            Triple("book", "المكتبة العلمية") {
                startActivity(Intent(this, ScienceLibraryActivity::class.java)) },
            Triple("palette", "المفضلة") { tab = 1; render() },
            Triple("settings", "الأخيرة") { tab = 0; render() },
            Triple("plus", "المزيد") { tab = 3; render() }
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        items.chunked(3).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (icon, label, action) ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(16), dp(8), dp(12))
                    background = solidCard(20)
                    lift(this, 2)
                    isClickable = true
                    ripple(this)
                    setOnClickListener { action() }
                }
                cell.addView(IconView(this, icon, ACCENT_B),
                    LinearLayout.LayoutParams(dp(28), dp(28)))
                cell.addView(txt(label, 12f, INK, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                })
                r.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            grid.addView(r)
        }
        return grid
    }

    // ======== بطاقة متابعة القراءة ========

    private fun continueCard(id: String, type: Int, page: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(16), dp(14))
            background = solidCard(22)
            lift(this, 3)
            isClickable = true
            ripple(this)
            setOnClickListener { if (type == 0) openReader(id) else openBoard(id) }
            val thumb: View = if (type == 0) coverView(this@MainActivity, id, 52, 68)
            else FrameLayout(this@MainActivity).apply {
                background = accentGrad(12, BOARD_A, BOARD_B)
                addView(IconView(this@MainActivity, "board", Color.WHITE),
                    FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            }
            addView(thumb, LinearLayout.LayoutParams(dp(52), dp(68)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                addView(txt("متابعة", 10f, MUTED))
                addView(txt(if (type == 0) id.substringAfter("__", id)
                else Names.board(this@MainActivity, id, "السبورة"), 16f, INK, true).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
                if (type == 0) addView(txt("صفحة $page", 12f, MUTED).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(IconView(this@MainActivity, "next", ACCENT_B),
                LinearLayout.LayoutParams(dp(20), dp(20)))
        }

    private fun sectionHeader(title: String, trailing: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(10))
        addView(txt(title, 17f, INK, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing.isNotEmpty()) addView(txt(trailing, 12f, MUTED))
    }

    // ======== تبويب المكتبات ========

    private fun librariesTab(libs: List<Lib>) {
        body.addView(sectionHeader("رفوف المكتبات", "${libs.size}/${Library.MAX_LIBS}"))
        body.addView(shelfScroll(libs))
        body.addView(inset(actionCard("pdf", "استيراد كتاب PDF", "اختار مكتبة وضيف ملف PDF") { chooseLibraryForBook() }))
        body.addView(inset(actionCard("library", "المكتبة العلمية", "صور للشرح وإضافة صورك") {
            startActivity(Intent(this, ScienceLibraryActivity::class.java))
        }))
    }

    private fun shelfScroll(libs: List<Lib>): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(dp(14), dp(4), dp(4), dp(8))
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        libs.forEach { row.addView(libCard(it)) }
        if (libs.size < Library.MAX_LIBS) row.addView(addLibTile())
        addView(row)
    }

    private fun libCard(lib: Lib): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = solidCard(22)
        lift(this, 4)
        isClickable = true
        ripple(this)
        layoutParams = LinearLayout.LayoutParams(dp(148), dp(260)).apply {
            setMargins(0, 0, dp(12), dp(8))
        }
        setOnClickListener { startActivity(Intent(this@MainActivity, ShelfActivity::class.java).putExtra("idx", lib.idx)) }
        setOnLongClickListener {
            Pickers.choose(this@MainActivity, lib.name, listOf("note" to "إعادة تسمية", "trash" to "حذف"), INK) { w ->
                if (w == 0) renameDialog(lib.name) { n -> Library.rename(this@MainActivity, lib.idx, n); render() }
                else confirmDeleteLib(lib)
            }; true
        }
        val cover: View = if (lib.books.isNotEmpty()) coverView(this@MainActivity, lib.books[0], 124, 164)
        else FrameLayout(this@MainActivity).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (dark) 0xFF1E3A5F.toInt() else 0xFFEFF6FF.toInt())
            }
            addView(IconView(this@MainActivity, "library", ACCENT_B),
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        }
        addView(cover, LinearLayout.LayoutParams(dp(124), dp(164)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(txt(lib.name, 15f, INK, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        })
        // شريط الامتلاء
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(if (dark) 0xFF334155.toInt() else 0xFFE2E8F0.toInt())
            }
            addView(View(this@MainActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(ACCENT_A, ACCENT_B)).apply { cornerRadius = dp(3).toFloat() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, lib.books.size.toFloat().coerceAtLeast(0.001f)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                (Library.MAX_BOOKS - lib.books.size).toFloat()))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)).apply {
            setMargins(0, dp(8), 0, 0)
        })
        addView(txt("${lib.books.size}/${Library.MAX_BOOKS} كتاب", 11f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun addLibTile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (dark) 0x14FFFFFF else 0x0A000000)
            setStroke(dp(1), if (dark) 0x33FFFFFF else 0x33000000,
                dp(6).toFloat(), dp(4).toFloat())
        }
        isClickable = true; ripple(this)
        layoutParams = LinearLayout.LayoutParams(dp(148), dp(260)).apply {
            setMargins(0, 0, dp(12), dp(8))
        }
        setOnClickListener { newLibrary() }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) 0x22FFFFFF else 0x14000000)
            }
            addView(IconView(this@MainActivity, "plus", ACCENT_B),
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(txt("مكتبة جديدة", 13f, ACCENT_B, true).apply { setPadding(0, dp(10), 0, 0) })
    }

    private fun actionCard(icon: String, title: String, sub: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = solidCard(20)
            lift(this, 2)
            isClickable = true; ripple(this)
            setOnClickListener { onClick() }
            addView(FrameLayout(this@MainActivity).apply {
                background = accentGrad(14, ACCENT_A, ACCENT_B)
                addView(IconView(this@MainActivity, icon, Color.WHITE),
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(txt(title, 15f, INK, true))
                addView(txt(sub, 12f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    // ======== تبويب السبورات ========

    private fun boardsTab(boards: List<String>) {
        body.addView(sectionHeader("السبورات", "${boards.size}"))
        // شبكة: 2 عمود على الموبايل، 3 على التابلت
        val cols = if (isWide) 3 else 2
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        val all = ArrayList<View>()
        boards.forEachIndexed { i, id -> all.add(boardTile(i, id)) }
        all.add(newBoardTile())
        all.chunked(cols).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { v ->
                r.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            repeat(cols - row.size) {
                r.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            }
            grid.addView(r)
        }
        body.addView(grid)
    }

    private fun boardTile(i: Int, id: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = solidCard(22)
        lift(this, 4)
        isClickable = true; ripple(this)
        setOnClickListener { openBoard(id) }
        setOnLongClickListener {
            val cur = Names.board(this@MainActivity, id, "سبورة ${i + 1}")
            Pickers.choose(this@MainActivity, cur, listOf("note" to "إعادة تسمية", "trash" to "حذف"), INK) { w ->
                if (w == 0) renameDialog(cur) { n -> Names.setBoard(this@MainActivity, id, n); render() }
                else confirmDeleteBoard(i, id)
            }; true
        }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(BOARD_A, BOARD_B)).apply {
                val r = dp(22).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(IconView(this@MainActivity, "board", Color.WHITE),
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(txt(Names.board(this@MainActivity, id, "سبورة ${i + 1}"), 15f, INK, true))
            addView(txt("مطوّل للخيارات", 10f, MUTED))
        })
    }

    private fun newBoardTile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(140)
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (dark) 0x14FFFFFF else 0x0A000000)
            setStroke(dp(1), if (dark) 0x33FFFFFF else 0x33000000,
                dp(6).toFloat(), dp(4).toFloat())
        }
        isClickable = true; ripple(this)
        setOnClickListener { newBoard() }
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) 0x22FFFFFF else 0x14EC4899)
            }
            addView(IconView(this@MainActivity, "plus", BOARD_B),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(txt("سبورة جديدة", 13f, BOARD_B, true).apply { setPadding(0, dp(10), 0, 0) })
    }

    // ======== تبويب الإعدادات ========

    private fun settingsTab() {
        body.addView(sectionHeader("الوضع", ""))
        body.addView(inset(modeRow()))
        body.addView(sectionHeader("ثيم الألوان", ""))
        body.addView(inset(accentRow()))
        body.addView(sectionHeader("التأثيرات", ""))
        body.addView(inset(glassRow()))
    }

    private fun modeRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf("تلقائي" to 0, "نهاري" to 1, "ليلي" to 2).forEach { (name, i) ->
            val sel = Prefs.mode(this@MainActivity) == i
            addView(txt(name, 14f, if (sel) Color.WHITE else INK, sel).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                background = if (sel) accentGrad(16, ACCENT_A, ACCENT_B) else solidCard(16)
                isClickable = true
                setOnClickListener { Prefs.setMode(this@MainActivity, i); recreate() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
    }

    private fun accentRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, dp(8))
        (0 until Prefs.ACCENT_COUNT).forEach { i ->
            val sel = Prefs.accent(this@MainActivity) == i
            addView(FrameLayout(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Prefs.accentColor(i))
                    if (sel) setStroke(dp(3), INK)
                }
                setOnClickListener { Prefs.setAccent(this@MainActivity, i); recreate() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(8), 0, dp(8), 0) })
        }
    }

    private fun glassRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(12), dp(14))
        background = solidCard(18)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("الوضع الزجاجي", 15f, INK, true))
            addView(txt("كروت وأشرطة بلمعة زجاجية", 12f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this@MainActivity)
        sw.isChecked = Prefs.glass(this@MainActivity)
        sw.setOnCheckedChangeListener { _, v -> Prefs.setGlass(this@MainActivity, v); render() }
        addView(sw)
    }

    // ======== مساعدات ========

    private fun openFirstBook(libs: List<Lib>) {
        val id = libs.firstOrNull { it.books.isNotEmpty() }?.books?.firstOrNull()
        if (id != null) openReader(id) else { tab = 1; render() }
    }

    private fun openReader(id: String) = startActivity(Intent(this, ReaderActivity::class.java).putExtra("id", id))
    private fun openBoard(id: String) = startActivity(Intent(this, BoardActivity::class.java).putExtra("id", id))

    private fun newBoard() {
        val id = "board_${System.currentTimeMillis()}"
        Store.save(this, Doc(id, true).apply { pageCount = 1 })
        openBoard(id)
    }

    private fun newLibrary() {
        val libs = Library.all(this)
        if (libs.size >= Library.MAX_LIBS) {
            Toast.makeText(this, "وصلت للحد الأقصى (${Library.MAX_LIBS} مكتبات)", Toast.LENGTH_SHORT).show()
            return
        }
        Library.create(this, "مكتبة ${libs.size + 1}"); render()
    }

    private fun chooseLibraryForBook() {
        if (Library.all(this).isEmpty()) Library.create(this, "مكتبتي")
        val libs = Library.all(this)
        Pickers.grid(this, "اختار المكتبة",
            libs.map { lib -> (if (lib.books.isNotEmpty()) coverView(this, lib.books[0], 110, 140)
            else Pickers.iconPreview(this, "library", ACCENT_B)) to "${lib.name} (${lib.books.size}/${Library.MAX_BOOKS})" }) { w ->
            startActivity(Intent(this, ShelfActivity::class.java).putExtra("idx", libs[w].idx).putExtra("pick", true))
        }
    }

    private fun renameDialog(current: String, onSave: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            setText(current); setSelection(current.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this).setView(input)
            .setPositiveButton("حفظ") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty()) onSave(n) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun confirmDeleteLib(lib: Lib) {
        AlertDialog.Builder(this).setMessage("هتتحذف \"${lib.name}\" وكل كتبها. متأكد؟")
            .setPositiveButton("حذف") { _, _ -> Library.deleteLibrary(this, lib.idx); render() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun confirmDeleteBoard(i: Int, id: String) {
        AlertDialog.Builder(this).setMessage("هتتحذف \"سبورة ${i + 1}\" وكل كتابتها. متأكد؟")
            .setPositiveButton("حذف") { _, _ -> Store.delete(this, id, true); render() }
            .setNegativeButton("إلغاء", null).show()
    }
}
