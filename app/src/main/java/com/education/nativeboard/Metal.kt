package com.education.nativeboard

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

// الوضع الزجاجي المعدني: تدرج فضي/داكن + لمعة علوية + حافة مضيئة
// (ملحوظة: ده شكل معدني شفاف، مش ضبابية حقيقية للمحتوى اللي ورا)
object Metal {
    fun panel(ctx: Context, radius: Int, dark: Boolean): Drawable {
        val r = Ui.dp(ctx, radius).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (dark) intArrayOf(0xCC64748B.toInt(), 0xCC1E293B.toInt(), 0xEE0B1222.toInt())
            else intArrayOf(0xF2FFFFFF.toInt(), 0xE6E2E8F0.toInt(), 0xE6A5B4C8.toInt())
        ).apply {
            cornerRadius = r
            setStroke(Ui.dp(ctx, 1), if (dark) 0x66FFFFFF else 0x99FFFFFF.toInt())
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x73FFFFFF, 0x00FFFFFF)
        ).apply { cornerRadius = r }
        return LayerDrawable(arrayOf<Drawable>(base, gloss))
    }
}
