package com.education.nativeboard

import org.json.JSONArray
import org.json.JSONObject

// عنصر فوق الصفحة: صورة (قص أو مكتبة) أو ملاحظة لاصقة أو خريطة ذهنية
// المكان والحجم بنسبة من الصفحة (0..1) عشان يفضلوا في مكانهم مهما اتغير حجم الشاشة
class PageObj(
    var kind: String,        // "image" | "note" | "mind"
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var text: String = "",   // نص الملاحظة، أو بيانات الخريطة الذهنية
    var key: String = ""     // اسم الصورة (لو صورة)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("k", kind)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("w", w.toDouble())
        .put("h", h.toDouble())
        .put("t", text)
        .put("s", key)

    companion object {
        fun fromJson(o: JSONObject): PageObj = PageObj(
            o.getString("k"),
            o.getDouble("x").toFloat(),
            o.getDouble("y").toFloat(),
            o.getDouble("w").toFloat(),
            o.getDouble("h").toFloat(),
            o.optString("t", ""),
            o.optString("s", "")
        )
    }
}

// عقدة في الخريطة الذهنية: نص، ومكان داخل الخريطة (0..1)، والأب (-1 للمركز)
class MindNode(var text: String, var x: Float, var y: Float, var parent: Int)

object MindMap {
    fun encode(nodes: List<MindNode>): String {
        val arr = JSONArray()
        for (n in nodes) {
            arr.put(JSONObject().put("t", n.text).put("x", n.x.toDouble()).put("y", n.y.toDouble()).put("p", n.parent))
        }
        return arr.toString()
    }

    fun decode(s: String): ArrayList<MindNode> {
        val out = ArrayList<MindNode>()
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(MindNode(o.getString("t"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getInt("p")))
            }
        } catch (e: Exception) {
            // بيانات فاسدة: بنرجع فاضي
        }
        return out
    }

    // قوالب جاهزة: 0 دايرية، 1 شجرة، 2 أربع فقاعات
    fun template(kind: Int): ArrayList<MindNode> {
        val n = ArrayList<MindNode>()
        when (kind) {
            0 -> {
                n.add(MindNode("الفكرة الرئيسية", 0.5f, 0.5f, -1))
                for (k in 0 until 5) {
                    val a = -Math.PI / 2 + k * 2 * Math.PI / 5
                    n.add(MindNode("فرع ${k + 1}", (0.5 + 0.38 * Math.cos(a)).toFloat(), (0.5 + 0.36 * Math.sin(a)).toFloat(), 0))
                }
            }
            1 -> {
                n.add(MindNode("الفكرة الرئيسية", 0.5f, 0.12f, -1))
                val xs = floatArrayOf(0.18f, 0.5f, 0.82f)
                for (k in 0 until 3) n.add(MindNode("فرع ${k + 1}", xs[k], 0.5f, 0))
                for (k in 0 until 3) n.add(MindNode("تفصيل ${k + 1}", xs[k], 0.9f, k + 1))
            }
            else -> {
                n.add(MindNode("الفكرة الرئيسية", 0.5f, 0.5f, -1))
                val pos = arrayOf(floatArrayOf(0.18f, 0.22f), floatArrayOf(0.82f, 0.22f), floatArrayOf(0.18f, 0.78f), floatArrayOf(0.82f, 0.78f))
                for (k in pos.indices) n.add(MindNode("فكرة ${k + 1}", pos[k][0], pos[k][1], 0))
            }
        }
        return n
    }
}
