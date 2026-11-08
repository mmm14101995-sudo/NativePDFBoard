package com.education.nativeboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

// المكتبة العلمية: صور جاهزة مع التطبيق + صور يضيفها المدرس من جهازه
object SciLibrary {
    const val USER_PREFIX = "user:"

    private fun dir(ctx: Context): File = File(ctx.filesDir, "sci").apply { mkdirs() }

    // صور المدرس اللي اتضافت من الجهاز
    fun userImages(ctx: Context): List<String> =
        (dir(ctx).listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedBy { it.name }
            .map { USER_PREFIX + it.name }

    // الاسم اللي بيظهر في القايمة (من غير الامتداد ورقم النسخ)
    fun title(key: String): String =
        key.removePrefix(USER_PREFIX).substringAfter("_").substringBeforeLast('.')

    // نسخ الصورة من الجهاز لمكتبة التطبيق
    fun importImage(ctx: Context, uri: Uri): String? {
        return try {
            val name = displayName(ctx, uri).replace(Regex("[^\\p{L}\\p{N}._ \\-]"), "_")
            val target = File(dir(ctx), System.currentTimeMillis().toString() + "_" + name)
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> target.outputStream().use { src.copyTo(it) } }
            USER_PREFIX + target.name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, key: String) {
        File(dir(ctx), key.removePrefix(USER_PREFIX)).delete()
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        var name = "صورة.jpg"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val k = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && k >= 0) name = c.getString(k) ?: name
        }
        return name
    }

    private fun stream(ctx: Context, key: String): InputStream = when {
        key.startsWith("crop:") -> File(File(ctx.filesDir, "crops"), key.removePrefix("crop:")).inputStream()
        key.startsWith(USER_PREFIX) -> File(dir(ctx), key.removePrefix(USER_PREFIX)).inputStream()
        else -> ctx.assets.open("library/$key")
    }

    // تحميل الصورة بدقة مناسبة للشاشة (مش أكبر من اللازم)
    fun load(ctx: Context, key: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream(ctx, key).use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide / (sample * 2) >= 2048) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            stream(ctx, key).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }
}
