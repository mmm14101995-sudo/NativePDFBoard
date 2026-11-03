package com.education.nativeboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// مستند: PDF أو سبورة، ومعاه كتابة كل صفحة
class Doc(val id: String, val isBoard: Boolean) {
    var bg: Int = -1
    var pageCount: Int = 1
    val pages = HashMap<Int, ArrayList<Stroke>>()

    fun strokesOf(n: Int): ArrayList<Stroke> = pages.getOrPut(n) { ArrayList() }
}

// التخزين كله على الجهاز: ملفات PDF في فولدر، والكتابة في JSON
object Store {
    private fun dir(ctx: Context, name: String): File = File(ctx.filesDir, name).apply { mkdirs() }

    fun pdfFile(ctx: Context, id: String): File = File(dir(ctx, "pdfs"), "$id.pdf")
    private fun jsonFile(ctx: Context, id: String): File = File(dir(ctx, "data"), "$id.json")

    fun listPdfs(ctx: Context): List<String> =
        dir(ctx, "pdfs").listFiles()?.filter { it.name.endsWith(".pdf") }?.map { it.name.removeSuffix(".pdf") }?.sorted() ?: emptyList()

    fun listBoards(ctx: Context): List<String> =
        dir(ctx, "data").listFiles()?.filter { it.name.startsWith("board_") && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }?.sorted() ?: emptyList()

    fun load(ctx: Context, id: String, isBoard: Boolean): Doc {
        val doc = Doc(id, isBoard)
        val f = jsonFile(ctx, id)
        if (f.isFile) {
            val o = JSONObject(f.readText())
            doc.bg = o.optInt("bg", -1)
            doc.pageCount = o.optInt("count", 1)
            val pages = o.optJSONObject("pages")
            if (pages != null) {
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = pages.getJSONArray(k)
                    val list = ArrayList<Stroke>()
                    for (i in 0 until arr.length()) list.add(Stroke.fromJson(arr.getJSONObject(i)))
                    doc.pages[k.toInt()] = list
                }
            }
        }
        return doc
    }

    fun save(ctx: Context, doc: Doc) {
        val pages = JSONObject()
        doc.pages.forEach { (n, list) ->
            if (list.isNotEmpty()) pages.put(n.toString(), JSONArray(list.map { it.toJson() }))
        }
        val o = JSONObject().put("bg", doc.bg).put("count", doc.pageCount).put("pages", pages)
        jsonFile(ctx, doc.id).writeText(o.toString())
    }

    fun delete(ctx: Context, id: String, isBoard: Boolean) {
        if (!isBoard) pdfFile(ctx, id).delete()
        jsonFile(ctx, id).delete()
    }
}
