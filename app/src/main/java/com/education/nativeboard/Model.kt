package com.education.nativeboard

import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject

// قلم: الاسم وسُمكه النسبي وشفافيته وشكل طرفه
class Pen(
    val name: String,
    val width: Float,
    val alpha: Int,
    val cap: Paint.Cap,
    val highlight: Boolean = false,
    val dash: Float = 0f,
    val glow: Float = 0f,
    val grain: Boolean = false
)

object Pens {
    val ALL = listOf(
        Pen("حبر", 1f, 255, Paint.Cap.ROUND),
        Pen("قلم جاف", 0.9f, 255, Paint.Cap.ROUND),
        Pen("حبر ناعم", 2f, 140, Paint.Cap.ROUND),
        Pen("فاين لاينر", 0.5f, 255, Paint.Cap.ROUND),
        Pen("قلم رفيع", 0.22f, 255, Paint.Cap.ROUND),
        Pen("ريشة", 1.4f, 255, Paint.Cap.SQUARE),
        Pen("رصاص", 0.7f, 190, Paint.Cap.ROUND, grain = true),
        Pen("ماركر", 3f, 225, Paint.Cap.SQUARE),
        Pen("تظليل", 3.2f, 80, Paint.Cap.SQUARE, highlight = true),
        Pen("متقطع", 1f, 255, Paint.Cap.BUTT, dash = 3f),
        Pen("نيون", 1.2f, 255, Paint.Cap.ROUND, glow = 2.5f)
    )
}

// خط مرسوم. النقط مخزنة كنسبة من حجم الصفحة (0..1)، فالخط بيفضل في مكانه مهما الصفحة تكبّر
class Stroke(val pen: Int, val color: Int, val size: Float, val pts: ArrayList<Float>) {

    fun toJson(): JSONObject = JSONObject()
        .put("p", pen)
        .put("c", color)
        .put("s", size.toDouble())
        .put("pts", JSONArray(pts.map { it.toDouble() }))

    companion object {
        fun fromJson(o: JSONObject): Stroke {
            val arr = o.getJSONArray("pts")
            val pts = ArrayList<Float>(arr.length())
            for (i in 0 until arr.length()) pts.add(arr.getDouble(i).toFloat())
            return Stroke(o.getInt("p"), o.getInt("c"), o.getDouble("s").toFloat(), pts)
        }
    }
}
