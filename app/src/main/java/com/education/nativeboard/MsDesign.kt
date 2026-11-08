package com.education.nativeboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

// نظام التصميم لـ MS Board: ألوان وأبعاد وخلفيات موحّدة
// ملف إضافي فقط: مش بيغيّر أي شاشة لحد ما تستخدمه
object MsDesign {

    // ---------- الألوان ----------
    val NAVY_DEEP = 0xFF0A1226.toInt()
    val NAVY = 0xFF0F1B38.toInt()
    val ROYAL = 0xFF1D4ED8.toInt()
    val ROYAL_LIGHT = 0xFF2563EB.toInt()
    val ELECTRIC = 0xFF38BDF8.toInt()
    val SILVER = 0xFFCBD5E1.toInt()
    val TEXT_PRIMARY = 0xFFF8FAFC.toInt()
    val TEXT_MUTED = 0xFF94A3B8.toInt()
    val CANVAS = 0xFFFFFFFF.toInt()
    val GLASS_FILL = 0x1AFFFFFF
    val GLASS_FILL_STRONG = 0x2EFFFFFF
    val GLASS_STROKE = 0x33FFFFFF

    // ---------- الأبعاد (بالـ dp) ----------
    const val RADIUS_CARD = 20
    const val RADIUS_BUTTON = 14
    const val RADIUS_PILL = 28
    const val TOUCH_MIN = 48
    const val SPACE_S = 8
    const val SPACE_M = 12
    const val SPACE_L = 16
    const val SPACE_XL = 24

    private fun dp(ctx: Context, v: Int): Int = Ui.dp(ctx, v)

    // كارت زجاجي: تعبئة شفافة وحافة فاتحة
    fun glassCard(ctx: Context, radiusDp: Int = RADIUS_CARD, strong: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setColor(if (strong) GLASS_FILL_STRONG else GLASS_FILL)
            setStroke(dp(ctx, 1), GLASS_STROKE)
        }

    // زرار أساسي: تدرج أزرق ملكي
    fun primaryButton(ctx: Context, radiusDp: Int = RADIUS_BUTTON): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ROYAL, ROYAL_LIGHT)).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    // مؤشر التبويب النشط: كبسولة زرقا
    fun pillIndicator(ctx: Context): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(ctx, RADIUS_PILL).toFloat()
        setColor(ROYAL_LIGHT)
    }

    // زرار أيقونة بمساحة لمس مريحة (48 دب على الأقل)
    fun iconButton(ctx: Context, icon: String, tint: Int = TEXT_PRIMARY, onClick: () -> Unit): FrameLayout =
        FrameLayout(ctx).apply {
            minimumWidth = dp(ctx, TOUCH_MIN)
            minimumHeight = dp(ctx, TOUCH_MIN)
            background = glassCard(ctx, RADIUS_BUTTON)
            isClickable = true
            setOnClickListener { onClick() }
            addView(IconView(ctx, icon, tint), FrameLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22), Gravity.CENTER))
        }

    // نص بخط واضح يدعم العربي
    fun label(ctx: Context, text: String, sizeSp: Float, color: Int = TEXT_PRIMARY, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    // اتجاه من اليمين لليسار للعناصر العربية
    fun rtl(v: View): View {
        v.layoutDirection = View.LAYOUT_DIRECTION_RTL
        return v
    }
}
