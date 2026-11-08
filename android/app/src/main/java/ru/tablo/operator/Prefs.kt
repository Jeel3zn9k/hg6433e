package ru.tablo.operator

import android.content.Context
import android.content.SharedPreferences

/**
 * Настройки приложения: адрес сервера табло, стартовая страница, «экран всегда включён».
 * Хранятся в SharedPreferences (локально на телефоне, никуда не передаются).
 */
object Prefs {
    private const val FILE = "tablo_prefs"
    private const val KEY_SERVER = "server_url"
    private const val KEY_PAGE = "start_page"
    private const val KEY_SCREEN = "keep_screen_on"

    const val DEFAULT_SERVER = "http://192.168.1.50:8642"
    const val DEFAULT_PAGE = "/panel"

    /** Доступные виды (пути на веб-сервере табло): телефонный пульт, ПК-панель, витрина. */
    val PAGES = arrayOf("/panel", "/operator", "/display")

    private fun p(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun serverUrl(ctx: Context): String = p(ctx).getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
    fun setServerUrl(ctx: Context, v: String) { p(ctx).edit().putString(KEY_SERVER, v).apply() }

    fun startPage(ctx: Context): String = p(ctx).getString(KEY_PAGE, DEFAULT_PAGE) ?: DEFAULT_PAGE
    fun setStartPage(ctx: Context, v: String) { p(ctx).edit().putString(KEY_PAGE, v).apply() }

    fun keepScreenOn(ctx: Context): Boolean = p(ctx).getBoolean(KEY_SCREEN, true)
    fun setKeepScreenOn(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean(KEY_SCREEN, v).apply() }
}
