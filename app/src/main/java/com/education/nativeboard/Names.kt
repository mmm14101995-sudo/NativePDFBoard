package com.education.nativeboard

import android.content.Context

// أسماء السبورات (بتتغير من المستخدم)
object Names {
    private const val FILE = "names"

    fun board(ctx: Context, id: String, fallback: String): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(id, null) ?: fallback

    fun setBoard(ctx: Context, id: String, name: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(id, name).apply()
    }
}
