package com.education.nativeboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

// المكتبة العلمية: صور جاهزة + صور المدرس اللي بيضيفها بنفسه من جهازه
class ScienceLibraryActivity : Activity() {
    private val REQ_ADD = 31
    private lateinit var list: LinearLayout

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        val dark = Prefs.dark(this)
        val ink = if (dark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(40), dp(16), dp(12))
        root.setBackgroundColor(if (dark) 0xFF0B1020.toInt() else 0xFFF1F5F9.toInt())

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = FrameLayout(this)
        back.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x14808080)
        }
        back.setOnClickListener { finish() }
        back.addView(IconView(this, "prev", ink), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        head.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val title = TextView(this)
        title.text = "المكتبة العلمية"
        title.textSize = 22f
        title.setTextColor(ink)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(dp(12), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val add = FrameLayout(this)
        add.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Prefs.accentB(this@ScienceLibraryActivity))
        }
        add.setOnClickListener { pickImage() }
        add.addView(IconView(this, "plus", Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        head.addView(add, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(12))
        })

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val items = ArrayList<Pair<String, String>>()
        items.addAll(readAssetItems())
        SciLibrary.userImages(this).forEach { key -> items.add(key to SciLibrary.title(key)) }
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "المكتبة فاضية. اضغط + وضيف صورك من جهازك."
                textSize = 14f
                setTextColor(0x99808080.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(60), 0, 0)
            })
            return
        }
        for (pair in items.chunked(2)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for ((key, name) in pair) {
                row.addView(card(key, name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            if (pair.size == 1) row.addView(android.view.View(this), LinearLayout.LayoutParams(0, 0, 1f))
            list.addView(row)
        }
    }

    // كارت صورة: الصورة فوق والاسم تحتها. صور المدرس بتتمسح بالضغط المطوّل
    private fun card(key: String, name: String): LinearLayout {
        val dark = Prefs.dark(this)
        val ink = if (dark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()
        val user = key.startsWith(SciLibrary.USER_PREFIX)
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(8), dp(8), dp(8), dp(10))
        c.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (dark) 0xFF1E293B.toInt() else Color.WHITE)
        }
        c.addView(Pickers.imagePreview(this, key), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)))
        c.addView(TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(ink)
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
        if (user) {
            c.setOnLongClickListener {
                confirmDelete(key)
                true
            }
        }
        return c
    }

    private fun readAssetItems(): List<Pair<String, String>> = try {
        val arr = JSONArray(assets.open("library/library.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("file") to o.getString("title")
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, REQ_ADD)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_ADD && resultCode == RESULT_OK && uri != null) {
            SciLibrary.importImage(this, uri)
            render()
        }
    }

    private fun confirmDelete(key: String) {
        AlertDialog.Builder(this)
            .setMessage("تحذف الصورة \"${SciLibrary.title(key)}\" من المكتبة؟")
            .setPositiveButton("حذف") { _, _ ->
                SciLibrary.delete(this, key)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
