package com.education.nativeboard

import android.content.Context
import android.content.res.Configuration

// إعدادات التطبيق: الوضع (تلقائي/نهاري/ليلي)، ثيم الألوان، والوضع الزجاجي
object Prefs {
    private const val FILE = "app_prefs"
    private val A = intArrayOf(0xFF1E3A8A.toInt(), 0xFF065F46.toInt(), 0xFF4C1D95.toInt(), 0xFF9F1239.toInt())
    private val B = intArrayOf(0xFF2563EB.toInt(), 0xFF10B981.toInt(), 0xFF8B5CF6.toInt(), 0xFFF43F5E.toInt())
    val ACCENT_COUNT = 4

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // 0 تلقائي حسب الجهاز، 1 نهاري، 2 ليلي
    fun mode(ctx: Context): Int = p(ctx).getInt("mode", 0)

    fun setMode(ctx: Context, v: Int) {
        p(ctx).edit().putInt("mode", v).apply()
    }

    fun dark(ctx: Context): Boolean = when (mode(ctx)) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    // للتوافق مع الشاشات القديمة
    fun setDark(ctx: Context, v: Boolean) = setMode(ctx, if (v) 2 else 1)

    fun glass(ctx: Context): Boolean = p(ctx).getBoolean("glass", false)

    fun setGlass(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("glass", v).apply()
    }

    fun accent(ctx: Context): Int = p(ctx).getInt("accent", 0).coerceIn(0, ACCENT_COUNT - 1)

    fun setAccent(ctx: Context, i: Int) {
        p(ctx).edit().putInt("accent", i).apply()
    }

    fun accentA(ctx: Context): Int = A[accent(ctx)]

    fun accentB(ctx: Context): Int = B[accent(ctx)]

    // لون معاينة الثيم رقم i (لمربعات الاختيار)
    fun accentColor(i: Int): Int = B[i]

    fun themeRes(ctx: Context): Int = if (dark(ctx)) R.style.AppDark else R.style.AppLight
}
