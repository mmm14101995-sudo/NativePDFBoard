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
    // صورة من المكتبة العلمية لكل صفحة (اسم الملف جوه assets/library)
    val images = HashMap<Int, String>()
    // العناصر فوق كل صفحة (صور قص، ملاحظات، خرايط)
    val objs = HashMap<Int, ArrayList<PageObj>>()

    fun strokesOf(n: Int): ArrayList<Stroke> = pages.getOrPut(n) { ArrayList() }

    fun objsOf(n: Int): ArrayList<PageObj> = objs.getOrPut(n) { ArrayList() }
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
            val imgs = o.optJSONObject("imgs")
            if (imgs != null) {
                val ik = imgs.keys()
                while (ik.hasNext()) {
                    val k = ik.next()
                    doc.images[k.toInt()] = imgs.getString(k)
                }
            }
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
            val ob = o.optJSONObject("objs")
            if (ob != null) {
                val ks = ob.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    val arr = ob.getJSONArray(k)
                    val list = ArrayList<PageObj>()
                    for (i in 0 until arr.length()) list.add(PageObj.fromJson(arr.getJSONObject(i)))
                    doc.objs[k.toInt()] = list
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
        val imgs = JSONObject()
        doc.images.forEach { (n, f) -> imgs.put(n.toString(), f) }
        val objsJson = JSONObject()
        doc.objs.forEach { (n, list) ->
            if (list.isNotEmpty()) objsJson.put(n.toString(), JSONArray(list.map { it.toJson() }))
        }
        val o = JSONObject().put("bg", doc.bg).put("count", doc.pageCount).put("pages", pages).put("imgs", imgs).put("objs", objsJson)
        jsonFile(ctx, doc.id).writeText(o.toString())
    }

    fun delete(ctx: Context, id: String, isBoard: Boolean) {
        if (!isBoard) pdfFile(ctx, id).delete()
        jsonFile(ctx, id).delete()
    }
}
