package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

// شاشة السبورة والقارئ — تصميم MS Board النهائي
// الهيكل (زي الصورة):
//   شريط علوي: رجوع + شعار + إخفاء الأدوات + وضع مزدوج | طباعة + تحميل + مشاركة + المزيد
//   وسط: عمود أقلام يسار | لوحة الرسم | عمود أدوات يمين
//   شريط سفلي: ألوان | عروض الخط | أشكال سريعة
abstract class BaseInkActivity : Activity() {
    protected lateinit var doc: Doc
    protected lateinit var view: InkView
    protected var pageNo = 1
    protected var dirty = false

    // ألوان تكيّفية
    private val dark get() = Prefs.dark(this)
    private val ICON get() = if (dark) 0xFFCBD5E1.toInt() else 0xFF334155.toInt()
    private val ICON_ACTIVE get() = if (dark) Color.WHITE else 0xFF0F172A.toInt()
    private val WHITE = Color.WHITE

    // حالة الشريط
    private var toolsHidden = false
    private var vertical = false
    private var fullMode = false
    private var labelMode = false
    private var sizeProgress = 35

    // الأقلام الخمسة في العمود الأيسر (مع ألوانهم)
    private val slotPens = arrayListOf(0, 3, 8, 11, 12)
    private val slotInk = arrayListOf(
        0xFF111827.toInt(), 0xFFDC2626.toInt(), 0xFF2563EB.toInt(),
        0xFF16A34A.toInt(), 0xFFA855F7.toInt()
    )
    private val penViews = ArrayList<PenIconView>()
    private val penSlots = ArrayList<FrameLayout>()
    private val colorTiles = ArrayList<FrameLayout>()

    // ألوان الشريط السفلي (7 + قوس قزح)
    private val quickColors = intArrayOf(
        0xFF000000.toInt(), 0xFFDC2626.toInt(), 0xFF2563EB.toInt(),
        0xFF16A34A.toInt(), 0xFFA855F7.toInt(), 0xFFF59E0B.toInt()
    )

    // مراجع العناصر التي تحتاج تحديث
    private var pageLabel: TextView? = null
    private var undoBtnRef: FrameLayout? = null
    private var redoBtnRef: FrameLayout? = null
    private var eraseBtnRef: FrameLayout? = null
    private var selectBtnRef: FrameLayout? = null
    private var handBtnRef: FrameLayout? = null
    private var sideSelectBtn: FrameLayout? = null
    private var sideHandBtn: FrameLayout? = null
    private var sizeDotView: View? = null

    // undo/redo
    private val undo = ArrayList<ArrayList<Stroke>>()
    private val redo = ArrayList<ArrayList<Stroke>>()

    // دوال مجردة
    abstract fun pageTotal(): Int
    abstract fun showPageBitmap(n: Int)
    open fun extraButtons(row: LinearLayout) {}
    open fun docTitle(): String = ""
    open fun usesTabs(): Boolean = false
    protected open fun startPage(): Int = 1
    protected open fun onPageShown(n: Int) {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUi()
    }

    private fun applySystemUi() {
        if (fullMode) Ui.immersive(this)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    // ======== مساعدات الرسم ========

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun glass(r: Int, strong: Boolean = false): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(if (strong)
            (if (dark) 0x55FFFFFF else 0xBBFFFFFF.toInt())
        else
            (if (dark) 0x28FFFFFF else 0x99FFFFFF.toInt()))
        setStroke(dp(1), if (dark) 0x44FFFFFF else 0x33000000)
    }

    private fun solidPanel(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(if (dark) 0xCC0A1526.toInt() else 0xDDFFFFFF.toInt())
        setStroke(dp(1), if (dark) 0x33FFFFFF else 0x22000000)
    }

    private fun accentGrad(r: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Prefs.accentA(this), Prefs.accentB(this))
    ).apply { cornerRadius = dp(r).toFloat() }

    private fun selSlotBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(if (dark) 0x442563EB else 0x222563EB)
        setStroke(dp(2), 0xFF2563EB.toInt())
    }

    private fun normSlotBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(if (dark) 0x22FFFFFF else 0x14000000)
    }

    private fun hDiv(): View = View(this).apply {
        setBackgroundColor(if (dark) 0x33FFFFFF else 0x22000000)
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(1)).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
    }

    private fun vDiv(): View = View(this).apply {
        setBackgroundColor(if (dark) 0x33FFFFFF else 0x22000000)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(26)).apply {
            setMargins(dp(6), 0, dp(6), 0)
        }
    }

    // زرار أيقونة قابل للضغط
    private fun ibtn(icon: String, tint: Int = ICON, size: Int = 44, onClick: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            background = glass(14)
            setOnClickListener { onClick() }
            addView(IconView(this@BaseInkActivity, icon, tint),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }

    // ======== بناء الواجهة ========

    protected fun buildUi() {
        view = InkView(this).apply {
            ink = slotInk[0]
            imageLoader = { k -> SciLibrary.load(this@BaseInkActivity, k) }
            onBeforeChange = { undo.add(snapshot()); redo.clear() }
            onChanged = { doc.pages[pageNo] = strokes; dirty = true; refreshBar() }
            onObjectsChanged = { dirty = true }
            onObjectLongPress = { i -> showObjectMenu(i) }
            onCropDone = { x, y, w, h -> onCropped(x, y, w, h) }
            onStrokeLongPress = { showStrokeMenu() }
        }

        labelMode = !fullMode
        (view.parent as? ViewGroup)?.removeView(view)
        val root = FrameLayout(this)

        // خلفية حسب الوضع
        if (dark) root.setBackgroundColor(0xFF050D1A.toInt())
        else root.background = GradientDrawable(GradientDrawable.Orientation.TR_BL,
            intArrayOf(0xFFEEF2FF.toInt(), 0xFFF8FAFF.toInt()))

        if (fullMode) {
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(buildFloating(), floatingPlacement())
        } else {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(buildTop(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val mid = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            mid.addView(buildLeftColumn(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, dp(8), 0)
            })
            // لوحة الرسم بإطار زجاجي
            val canvasFrame = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(0xFFFFFFFF.toInt())
                    setStroke(dp(1), if (dark) 0x44FFFFFF else 0x33000000)
                }
                elevation = dp(3).toFloat()
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            canvasFrame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            mid.addView(canvasFrame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            mid.addView(buildRightColumn(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(8), 0, 0, 0)
            })
            col.addView(mid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            if (!toolsHidden) col.addView(buildBottom(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        setContentView(root)
        applySystemUi()
        refreshBar()
    }

    // ======== الشريط العلوي ========

    private fun buildTop(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = solidPanel(0)
        }
        // رجوع
        val back = ibtn("home", ICON) { closeWithPrompt() }
        bar.addView(back)

        // شعار + اسم
        val logo = FrameLayout(this).apply {
            background = glass(12)
            setPadding(dp(8), dp(6), dp(14), dp(6))
        }
        logo.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(IconView(this@BaseInkActivity, "board", Prefs.accentB(this@BaseInkActivity)),
                LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(this@BaseInkActivity).apply {
                text = "MS Board"
                textSize = 15f
                setTextColor(ICON_ACTIVE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(8), 0, 0, 0)
            })
        })
        bar.addView(logo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            setMargins(dp(8), 0, 0, 0)
        })

        bar.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // إخفاء الأدوات
        bar.addView(topPill("إخفاء الأدوات", "settings") { toolsHidden = !toolsHidden; buildUi() })

        // الوضع الداكن
        bar.addView(topPill(if (dark) "الوضع النهاري" else "الوضع الداكن", "settings") {
            Prefs.setMode(this, if (dark) 1 else 2); recreate()
        })

        bar.addView(View(this), LinearLayout.LayoutParams(dp(10), 0))
        bar.addView(vDiv())

        // أدوات اليمين
        listOf("download", "export", "settings").forEach { icon ->
            bar.addView(ibtn(icon, ICON) { onTopAction(icon) })
        }
        bar.addView(ibtn("plus", ICON) { showMoreMenu() })

        return bar
    }

    private fun topPill(label: String, icon: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(20)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
            addView(TextView(this@BaseInkActivity).apply {
                text = label; textSize = 12f; setTextColor(ICON)
            })
            addView(View(this@BaseInkActivity), LinearLayout.LayoutParams(dp(4), 0))
            addView(IconView(this@BaseInkActivity, icon, ICON), LinearLayout.LayoutParams(dp(16), dp(16)))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        }

    // ======== العمود الأيسر: الأقلام ========

    private fun buildLeftColumn(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = solidPanel(22)
            elevation = dp(3).toFloat()
        }
        penSlots.clear(); penViews.clear()
        slotPens.forEachIndexed { i, penIdx ->
            val pv = PenIconView(this, kindOf(penIdx), slotInk[i])
            penViews.add(pv)
            val slot = FrameLayout(this).apply {
                background = normSlotBg()
                elevation = if (!view.eraserMode && view.penIndex == slotPens[i]) dp(2).toFloat() else 0f
                addView(pv, FrameLayout.LayoutParams(dp(32), dp(52), Gravity.CENTER))
                setOnClickListener { selectPen(i) }
            }
            penSlots.add(slot)
            col.addView(slot, LinearLayout.LayoutParams(dp(50), dp(62)).apply {
                setMargins(0, dp(3), 0, dp(3))
            })
        }
        // زرار + إضافة قلم
        col.addView(ibtn("plus", Prefs.accentB(this), 44) { openPenPicker() })
        col.addView(hDiv())
        // ممحاة + تحديد
        eraseBtnRef = ibtn("eraser", ICON) { toggleEraser() }.also { col.addView(it) }
        sideSelectBtn = ibtn("select", ICON) { toggleSelect() }.also { col.addView(it) }
        sideHandBtn = ibtn("hand", ICON) { toggleHand() }.also { col.addView(it) }
        return col
    }

    // ======== العمود الأيمن: أدوات ========

    private fun buildRightColumn(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = solidPanel(22)
            elevation = dp(3).toFloat()
        }
        // PDF + الأشكال
        col.addView(pdfBtn())
        col.addView(shapesBtn())
        col.addView(hDiv())
        // تراجع + إعادة
        undoBtnRef = ibtn("undo", ICON) { doUndo() }.also { col.addView(it) }
        redoBtnRef = ibtn("redo", ICON) { doRedo() }.also { col.addView(it) }
        col.addView(hDiv())
        // تكبير + تصغير + ملء الشاشة
        col.addView(ibtn("zoom_in", ICON) { view.zoomBy(1.25f) })
        col.addView(ibtn("zoom_out", ICON) { view.zoomBy(0.8f) })
        col.addView(ibtn("expand", ICON) { toggleFull() })
        col.addView(hDiv())
        // أزرار إضافية من الكلاس الوارث
        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        extraButtons(extra)
        col.addView(extra)
        return col
    }

    private fun pdfBtn(): View = FrameLayout(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFDC2626.toInt(), 0xFFEF4444.toInt())).apply {
            cornerRadius = dp(14).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(50), dp(54)).apply {
            setMargins(0, dp(3), 0, dp(3))
        }
        setOnClickListener { exportMenu() }
        addView(LinearLayout(this@BaseInkActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(IconView(this@BaseInkActivity, "pdf", Color.WHITE), LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(this@BaseInkActivity).apply {
                text = "+"; textSize = 10f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun shapesBtn(): View = FrameLayout(this).apply {
        background = glass(14)
        layoutParams = LinearLayout.LayoutParams(dp(50), dp(54)).apply {
            setMargins(0, dp(3), 0, dp(3))
        }
        setOnClickListener { addShape() }
        val inner = LinearLayout(this@BaseInkActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        inner.addView(LinearLayout(this@BaseInkActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(View(this@BaseInkActivity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2563EB.toInt()) }
            }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(dp(1), 0, dp(1), 0) })
            addView(View(this@BaseInkActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xFFF59E0B.toInt())
                    cornerRadius = dp(2).toFloat()
                }
            }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(dp(1), 0, dp(1), 0) })
        })
        inner.addView(LinearLayout(this@BaseInkActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(View(this@BaseInkActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xFF16A34A.toInt())
                    cornerRadius = dp(2).toFloat()
                }
            }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(dp(1), 0, dp(1), 0) })
            addView(IconView(this@BaseInkActivity, "next", ICON),
                LinearLayout.LayoutParams(dp(14), dp(14)).apply { setMargins(dp(1), 0, dp(1), 0) })
        })
        addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ======== الشريط السفلي ========

    private fun buildBottom(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = solidPanel(0)
        }
        // ألوان سريعة
        quickColors.forEach { c ->
            val t = colorTile(c) { setInk(c) }
            colorTiles.add(t)
            bar.addView(t, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                setMargins(0, 0, dp(5), 0)
            })
        }
        // قوس قزح
        bar.addView(rainbowTile { openCustomColor() }, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            setMargins(0, 0, dp(10), 0)
        })
        bar.addView(vDiv())
        // عروض الخط
        listOf(1f to "خط", 3f to "خط", 6f to "خط").forEach { (w, _) ->
            bar.addView(lineStyleTile(w), LinearLayout.LayoutParams(dp(44), dp(30)).apply {
                setMargins(0, 0, dp(4), 0)
            })
        }
        bar.addView(vDiv())
        // أشكال سريعة
        listOf(0, 2, 4, 10).forEach { t ->
            bar.addView(quickShapeTile(t), LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                setMargins(0, 0, dp(4), 0)
            })
        }
        // سهم >
        bar.addView(ibtn("next", ICON, 32) { addShape() })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        // تنقل الصفحات
        bar.addView(buildPageNav())
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(bar)
        }
    }

    private fun colorTile(c: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        tag = c
        setOnClickListener { onClick() }
        addView(View(this@BaseInkActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(c)
            }
        }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
    }

    private fun rainbowTile(onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        setOnClickListener { onClick() }
        addView(View(this@BaseInkActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.SWEEP_GRADIENT
                colors = intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEF4444.toInt())
            }
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
    }

    private fun lineStyleTile(w: Float): View = View(this).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(3).toFloat()
            setColor(ICON)
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), (w * resources.displayMetrics.density).toInt().coerceAtLeast(dp(2)))
        setOnClickListener { view.sizeLevel = w; refreshBar() }
    }

    private fun quickShapeTile(type: Int): View = android.graphics.Canvas().let {
        FrameLayout(this).apply {
            setOnClickListener {
                view.objects.add(PageObj("shape", 0.3f, 0.3f, 0.3f, 0.3f, "", "$type:${view.ink}"))
                view.selected = view.objects.size - 1
                view.selectMode = true; dirty = true; view.invalidate(); refreshBar()
            }
            addView(Pickers.shapePreview(this@BaseInkActivity, type),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildPageNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = glass(20)
        setPadding(dp(6), dp(4), dp(6), dp(4))
        addView(ibtn("next", ICON, 36) { goPage(pageNo + 1) })
        pageLabel = TextView(this@BaseInkActivity).apply {
            textSize = 13f; setTextColor(ICON_ACTIVE)
            gravity = Gravity.CENTER; minWidth = dp(52)
        }
        addView(pageLabel)
        addView(ibtn("prev", ICON, 36) { goPage(pageNo - 1) })
    }

    // ======== الشريط العائم (ملء الشاشة) ========

    private fun buildFloating(): View {
        val v = vertical
        val row = LinearLayout(this).apply {
            orientation = if (v) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = solidPanel(30)
        }
        val handle = ibtn("grip", ICON) {}
        handle.setOnTouchListener(dragListener())
        row.addView(handle)
        undoBtnRef = ibtn("undo", ICON) { doUndo() }.also { row.addView(it) }
        redoBtnRef = ibtn("redo", ICON) { doRedo() }.also { row.addView(it) }
        penSlots.clear(); penViews.clear()
        slotPens.forEachIndexed { i, penIdx ->
            val pv = PenIconView(this, kindOf(penIdx), slotInk[i])
            penViews.add(pv)
            val slot = FrameLayout(this).apply {
                background = normSlotBg()
                addView(pv, FrameLayout.LayoutParams(dp(28), dp(46), Gravity.CENTER))
                setOnClickListener { selectPen(i) }
            }
            penSlots.add(slot)
            row.addView(slot, LinearLayout.LayoutParams(dp(44), dp(54)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        val dot = View(this)
        colorTiles.clear()
        row.addView(FrameLayout(this).apply {
            setOnClickListener { openCustomColor() }
            addView(dot, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        })
        eraseBtnRef = ibtn("eraser", ICON) { toggleEraser() }.also { row.addView(it) }
        row.addView(ibtn("zoom_in", ICON) { view.zoomBy(1.25f) })
        row.addView(ibtn("zoom_out", ICON) { view.zoomBy(0.8f) })
        row.addView(ibtn("orient", ICON) { vertical = !vertical; buildUi() })
        row.addView(ibtn("shrink", ICON) { toggleFull() })
        extraButtons(row)
        return if (v) ScrollView(this).apply { addView(row) }
        else HorizontalScrollView(this).apply { addView(row) }
    }

    private fun floatingPlacement(): FrameLayout.LayoutParams {
        val m = dp(12)
        return if (vertical) FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            setMargins(m, dp(70), 0, dp(70))
        } else FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(m, 0, m, m)
        }
    }

    private fun dragListener(): View.OnTouchListener {
        var dx = 0f; var dy = 0f
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY }
                MotionEvent.ACTION_MOVE -> { }
            }; true
        }
    }

    // ======== الأفعال ========

    private fun selectPen(i: Int) {
        view.penIndex = slotPens[i]; view.ink = slotInk[i]; view.eraserMode = false
        refreshBar()
    }

    private fun setInk(c: Int) {
        view.ink = c; view.eraserMode = false
        val i = slotPens.indexOf(view.penIndex)
        if (i >= 0) { slotInk[i] = c; penViews.getOrNull(i)?.body = c; penViews.getOrNull(i)?.invalidate() }
        refreshBar()
    }

    private fun openCustomColor() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val prev = View(this)
        box.addView(prev, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, dp(12))
        })
        var r = Color.red(view.ink); var g = Color.green(view.ink); var b = Color.blue(view.ink)
        fun update() {
            prev.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(r, g, b)) }
        }
        update()
        fun channel(init: Int, onChange: (Int) -> Unit) {
            val sb = SeekBar(this).apply {
                max = 255; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { onChange(p); update() }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            box.addView(sb, LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        channel(r) { r = it }; channel(g) { g = it }; channel(b) { b = it }
        AlertDialog.Builder(this).setView(box)
            .setPositiveButton("تم") { _, _ -> setInk(Color.rgb(r, g, b)) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun openPenPicker() {
        val available = (0 until Pens.ALL.size).filter { !slotPens.contains(it) }
        if (available.isEmpty()) { Toast.makeText(this, "كل الأقلام ظاهرة", Toast.LENGTH_SHORT).show(); return }
        val cards = available.map { Pickers.iconPreview(this, kindOf(it), Pens.ALL[it].let { p -> if (p.highlight) 0xFFFACC15.toInt() else 0xFF334155.toInt() }) to Pens.ALL[it].name }
        Pickers.grid(this, "أضف قلماً", cards) { w ->
            val idx = available[w]
            if (slotPens.size < 5) { slotPens.add(idx); slotInk.add(if (Pens.ALL[idx].highlight) 0xFFFACC15.toInt() else 0xFF334155.toInt()) }
            else { slotPens[4] = idx; slotInk[4] = if (Pens.ALL[idx].highlight) 0xFFFACC15.toInt() else 0xFF334155.toInt() }
            buildUi()
        }
    }

    private fun toggleEraser() { view.eraserMode = !view.eraserMode; if (view.eraserMode) { view.handMode = false; view.selectMode = false }; refreshBar() }
    private fun toggleSelect() { view.selectMode = !view.selectMode; if (view.selectMode) { view.eraserMode = false; view.handMode = false }; refreshBar() }
    private fun toggleHand() { view.handMode = !view.handMode; if (view.handMode) view.eraserMode = false; refreshBar() }
    private fun toggleFull() { fullMode = !fullMode; if (!fullMode) vertical = false; applySystemUi(); buildUi() }

    private fun doUndo() { if (undo.isEmpty()) return; redo.add(view.snapshot()); view.restore(undo.removeAt(undo.size - 1)) }
    private fun doRedo() { if (redo.isEmpty()) return; undo.add(view.snapshot()); view.restore(redo.removeAt(redo.size - 1)) }

    protected fun goPage(n: Int) {
        if (n < 1 || n > pageTotal()) return
        pageNo = n; undo.clear(); redo.clear()
        view.strokes = doc.strokesOf(n); view.objects = doc.objsOf(n); view.selected = -1; view.selStroke = -1
        showPageBitmap(n); onPageShown(n); view.invalidate(); refreshBar()
    }

    protected fun markDirty() { dirty = true }

    // ======== قوائم ========

    private fun onTopAction(icon: String) {
        when (icon) {
            "download" -> exportMenu()
            "export" -> exportPng()
            "settings" -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showMoreMenu() {
        Pickers.choose(this, "المزيد", listOf(
            "note" to "ملاحظة لاصقة", "mind" to "خريطة ذهنية",
            "crop" to "قص من PDF", "export" to "تصدير PDF",
            "lines" to "تسطير الصفحة", "trash" to "مسح الصفحة"
        ), ICON) { w ->
            when (w) {
                0 -> addNote(); 1 -> addMind(); 2 -> startCrop()
                3 -> exportPdf(); 4 -> { }; 5 -> confirmClear()
            }
        }
    }

    private fun showObjectMenu(i: Int) {
        val o = view.objects.getOrNull(i) ?: return
        val opts = ArrayList<Pair<String, String>>()
        if (o.kind == "note") opts.add("note" to "تعديل النص")
        if (o.kind == "mind") { opts.add("mind" to "تعديل فرع"); opts.add("mind" to "إضافة فرع") }
        opts.add("trash" to "حذف")
        Pickers.choose(this, "العنصر", opts, ICON) { w ->
            when (opts[w].second) {
                "تعديل النص" -> editNoteText(i)
                "تعديل فرع" -> pickNode(i) { idx -> renameNode(i, idx) }
                "إضافة فرع" -> pickNode(i) { idx -> addChild(i, idx) }
                "حذف" -> deleteObject(i)
            }
        }
    }

    private fun showStrokeMenu() {
        Pickers.choose(this, "الكتابة", listOf("trash" to "حذف الكتابة"), ICON) { _ ->
            view.deleteSelectedStroke()
        }
    }

    private fun confirmClear() {
        Pickers.grid(this, "اختار اللي عايز تمسحه", listOf(
            Pickers.iconPreview(this, "eraser", ICON) to "الكتابة",
            Pickers.iconPreview(this, "note", ICON) to "الملاحظات والخرايط",
            Pickers.iconPreview(this, "trash", ICON) to "كل الصفحة"
        )) { i ->
            if (i == 0 || i == 2) clearPage()
            if (i == 1 || i == 2) { view.objects.clear(); view.selected = -1; dirty = true; view.invalidate() }
        }
    }

    private fun clearPage() {
        if (view.strokes.isEmpty()) return
        undo.add(view.snapshot()); redo.clear()
        view.restore(ArrayList()); doc.pages[pageNo] = view.strokes
        dirty = true; view.invalidate(); refreshBar()
    }

    // ======== ملاحظات وخرايط ========

    private fun addNote() {
        Pickers.grid(this, "شكل الملاحظة", Shapes.NOTE_SHAPES.map { t -> Pickers.shapePreview(this, t) to Shapes.NAMES[t] }) { k ->
            view.objects.add(PageObj("note", 0.36f, 0.36f, 0.24f, 0.18f, "اكتب هنا", Shapes.NOTE_SHAPES[k].toString()))
            view.selected = view.objects.size - 1; view.selectMode = false; dirty = true; view.invalidate()
            editNoteText(view.objects.size - 1)
        }
    }

    private fun addMind() {
        val names = listOf("دايرية 5", "دايرية 8", "شجرة", "أفقية", "4 فقاعات", "6 فقاعات", "سلسلة", "هرمية")
        Pickers.grid(this, "شكل الخريطة", names.indices.map { k -> Pickers.mindPreview(this, MindMap.template(k)) to names[k] }) { k ->
            view.objects.add(PageObj("mind", 0.25f, 0.3f, 0.5f, 0.42f, MindMap.encode(MindMap.template(k))))
            view.selected = view.objects.size - 1; view.selectMode = true; dirty = true; view.invalidate(); refreshBar()
        }
    }

    private fun addShape() {
        Pickers.grid(this, "الأشكال", (0 until Shapes.COUNT).map { Pickers.shapePreview(this, it) to Shapes.NAMES[it] }) { t ->
            view.objects.add(PageObj("shape", 0.3f, 0.3f, 0.3f, 0.3f, "", "$t:${view.ink}"))
            view.selected = view.objects.size - 1; view.selectMode = true; dirty = true; view.invalidate(); refreshBar()
        }
    }

    private fun editNoteText(i: Int) {
        val o = view.objects.getOrNull(i) ?: return
        val input = EditText(this).apply { setText(o.text); setSelection(o.text.length); minLines = 3; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        AlertDialog.Builder(this).setTitle("نص الملاحظة").setView(input)
            .setPositiveButton("حفظ") { _, _ -> o.text = input.text.toString().trim(); dirty = true; view.invalidate() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun pickNode(i: Int, onPick: (Int) -> Unit) {
        val nodes = MindMap.decode(view.objects[i].text)
        if (nodes.isEmpty()) return
        Pickers.choose(this, "اختار الفرع", nodes.map { "mind" to it.text.ifBlank { "…" } }, ICON) { w -> onPick(w) }
    }

    private fun renameNode(i: Int, idx: Int) {
        val o = view.objects[i]; val nodes = MindMap.decode(o.text); val node = nodes.getOrNull(idx) ?: return
        val input = EditText(this).apply { setText(node.text); setSelection(node.text.length) }
        AlertDialog.Builder(this).setView(input)
            .setPositiveButton("حفظ") { _, _ -> node.text = input.text.toString().trim(); o.text = MindMap.encode(nodes); dirty = true; view.invalidate() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun addChild(i: Int, parent: Int) {
        val o = view.objects[i]; val nodes = MindMap.decode(o.text); val p = nodes.getOrNull(parent) ?: return
        val newIdx = nodes.size; val nx = (p.x + (if (p.x < 0.6f) 0.22f else -0.22f)).coerceIn(0.05f, 0.95f)
        val ny = (p.y + (if (nodes.size % 2 == 0) 0.2f else -0.2f)).coerceIn(0.05f, 0.95f)
        nodes.add(MindNode("فرع جديد", nx, ny, parent)); o.text = MindMap.encode(nodes); dirty = true; view.invalidate()
        renameNode(i, newIdx)
    }

    private fun deleteObject(i: Int) {
        if (i !in view.objects.indices) return
        view.objects.removeAt(i); view.selected = -1; dirty = true; view.invalidate(); refreshBar()
    }

    // ======== تصدير ========

    private fun exportMenu() {
        Pickers.choose(this, "تصدير", listOf("pdf" to "ملف PDF", "download" to "صورة PNG", "select" to "سكرين شوت"), ICON) { w ->
            when (w) { 0 -> exportPdf(); 1 -> exportPng(); 2 -> exportScreenshot() }
        }
    }

    private fun exportDir() = (getExternalFilesDir("exports") ?: filesDir).apply { mkdirs() }

    private fun exportPng() {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.drawForExport(Canvas(bmp)); savePng(bmp, "page")
    }

    private fun exportScreenshot() {
        val d = window.decorView; val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        d.draw(Canvas(bmp)); savePng(bmp, "screen")
    }

    private fun savePng(bmp: Bitmap, tag: String) {
        val f = File(exportDir(), "${tag}_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        Toast.makeText(this, "اتحفظ: ${f.path}", Toast.LENGTH_LONG).show()
    }

    private fun exportPdf() {
        val orig = pageNo; val pdf = PdfDocument(); val w = view.width; val h = view.height
        for (n in 1..pageTotal()) {
            view.strokes = doc.strokesOf(n); view.objects = doc.objsOf(n); showPageBitmap(n)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
            view.drawForExport(page.canvas); pdf.finishPage(page)
        }
        val f = File(exportDir(), "export_${System.currentTimeMillis()}.pdf")
        f.outputStream().use { pdf.writeTo(it) }; pdf.close()
        view.strokes = doc.strokesOf(orig); view.objects = doc.objsOf(orig); showPageBitmap(orig); refreshBar()
        Toast.makeText(this, "PDF: ${f.path}", Toast.LENGTH_LONG).show()
    }

    // ======== قص PDF ========

    protected fun startCrop() {
        if (view.pageBitmap == null) { Toast.makeText(this, "مفيش صفحة مفتوحة", Toast.LENGTH_SHORT).show(); return }
        view.selectMode = false; view.eraserMode = false; view.cropMode = true
        Toast.makeText(this, "حدد الجزء اللي عايز تقصه", Toast.LENGTH_LONG).show(); refreshBar()
    }

    private fun onCropped(x: Float, y: Float, w: Float, h: Float) {
        val src = view.pageBitmap ?: return
        val sx = (x * src.width).toInt().coerceIn(0, src.width - 1)
        val sy = (y * src.height).toInt().coerceIn(0, src.height - 1)
        val sw = (w * src.width).toInt().coerceIn(1, src.width - sx)
        val sh = (h * src.height).toInt().coerceIn(1, src.height - sy)
        val crop = Bitmap.createBitmap(src, sx, sy, sw, sh)
        val dir = File(filesDir, "crops").apply { mkdirs() }
        val name = "crop_${System.currentTimeMillis()}.png"
        File(dir, name).outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 95, it) }
        pickBoardForCrop("crop:$name", sw.toFloat() / sh.toFloat())
    }

    private fun pickBoardForCrop(key: String, aspect: Float) {
        val ids = Store.listBoards(this)
        val opts = ArrayList<Pair<String, String>>().apply {
            ids.forEach { add("board" to Names.board(this@BaseInkActivity, it, "سبورة")) }
            add("plus" to "سبورة جديدة")
        }
        Pickers.choose(this, "أضف القص لأنهي سبورة؟", opts, ICON) { w ->
            val id = if (w < ids.size) ids[w] else "board_${System.currentTimeMillis()}"
            val target = Store.load(this, id, true).apply { if (pageCount < 1) pageCount = 1 }
            target.objsOf(1).add(PageObj("image", 0.25f, 0.2f, 0.5f, 0.5f / aspect / 1.414f, "", key))
            Store.save(this, target)
            Toast.makeText(this, "اتضافت للسبورة", Toast.LENGTH_SHORT).show()
        }
    }

    // ======== مساعدات الأقلام ========

    private fun kindOf(p: Int): String = when (p) {
        0 -> "fountain"; 1 -> "gel"; 2 -> "nib"
        3, 4 -> "fineliner"; 5, 11 -> "brush"
        6 -> "pencil"; 7 -> "marker"; 8 -> "highlighter"
        9 -> "dotted"; 10 -> "neon"; 12 -> "smart"
        else -> "fountain"
    }

    // ======== تحديث الواجهة ========

    protected fun refreshBar() {
        pageLabel?.text = "${pageNo}/${pageTotal()}"
        undoBtnRef?.alpha = if (undo.isEmpty()) 0.35f else 1f
        redoBtnRef?.alpha = if (redo.isEmpty()) 0.35f else 1f
        eraseBtnRef?.alpha = if (view.eraserMode) 1f else 0.5f
        sideSelectBtn?.alpha = if (view.selectMode) 1f else 0.5f
        sideHandBtn?.alpha = if (view.handMode) 1f else 0.5f
        penSlots.forEachIndexed { i, s ->
            s.background = if (!view.eraserMode && view.penIndex == slotPens[i]) selSlotBg() else normSlotBg()
        }
        penViews.forEachIndexed { i, pv ->
            pv.picked = !view.eraserMode && view.penIndex == slotPens[i]
        }
        colorTiles.forEach { t ->
            t.background = if (t.tag == view.ink) GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(0x00000000)
                setStroke(dp(2), Prefs.accentB(this@BaseInkActivity))
            } else null
        }
    }

    // ======== الإغلاق ========

    protected fun closeWithPrompt() {
        if (!dirty) { finish(); return }
        AlertDialog.Builder(this)
            .setMessage("في كتابة جديدة. تحب تحفظها؟")
            .setPositiveButton("حفظ") { _, _ -> Store.save(this, doc); finish() }
            .setNegativeButton("بدون حفظ") { _, _ -> finish() }
            .setNeutralButton("إلغاء", null).show()
    }

    override fun onBackPressed() { closeWithPrompt() }
}
