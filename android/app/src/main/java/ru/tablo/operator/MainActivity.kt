package ru.tablo.operator

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * «Табло — пульт» (2026-11): Android-клиент веб-панели хоккейного табло.
 *
 * Принцип: приложение — НАДЁЖНАЯ ОБОЛОЧКА той же веб-панели, которую использует
 * компьютер (HTTP-сервер программы «Табло», порт 8642). Вся логика матча живёт
 * на машине табло; второй копии логики в приложении нет — поведение 1:1 с веб-панелью,
 * включая частоту опроса (7 с в простое, 500 мс в финальное окно периода — ОП-10)
 * и защиту команд (POST — О-9).
 *
 * Доступ только для своих: подключение возможно лишь к вашему серверу в вашей сети
 * и только после ввода пароля оператора (на сервере действуют те же ограничения:
 * не более 2 устройств, таймаут сессии 30 мин, абсолютный срок 8 ч).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var errorBox: View
    private lateinit var errorDetail: TextView
    private var loadFailed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        errorBox = findViewById(R.id.errorBox)
        errorDetail = findViewById(R.id.errorDetail)
        findViewById<View>(R.id.btnRetry).setOnClickListener { loadPanel() }
        findViewById<View>(R.id.btnServer).setOnClickListener { showServerDialog() }

        val s: WebSettings = web.settings
        s.javaScriptEnabled = true      // панель — HTML+JS (тот же код, что в браузере ПК)
        s.domStorageEnabled = true      // «Запомнить вход»: токен сессии в localStorage
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.useWideViewPort = false
        s.loadWithOverviewMode = false
        // Состояние матча всегда свежее: кэш отключён, панель и сама опрашивает /state.json.
        s.cacheMode = WebSettings.LOAD_NO_CACHE

        CookieManager.getInstance().setAcceptCookie(true)

        // Метка приложения в User-Agent: устройство видно в журнале сервера и в списке
        // сессий. После правки О-7 сессия привязана к токену, а не к IP — переход
        // телефона между Wi-Fi и мобильной сетью НЕ рвёт сеанс посреди матча.
        s.userAgentString = s.userAgentString + " TabloAndroid/1.0"

        web.setBackgroundColor(Color.parseColor("#101010"))
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    loadFailed = true
                    showError(error.description?.toString() ?: getString(R.string.error_unknown))
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (loadFailed) {
                    loadFailed = false   // экран ошибки остаётся видимым до «Повторить»
                } else {
                    errorBox.visibility = View.GONE
                }
            }
        }

        applyKeepScreenOn()
        loadPanel()
    }

    private fun loadPanel() {
        loadFailed = false
        errorBox.visibility = View.GONE
        val base = Prefs.serverUrl(this).trimEnd('/')
        web.loadUrl(base + Prefs.startPage(this))
    }

    private fun showError(reason: String) {
        errorDetail.text = getString(R.string.error_detail_fmt, Prefs.serverUrl(this), reason)
        errorBox.visibility = View.VISIBLE
    }

    private fun showServerDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(Prefs.serverUrl(this))
        input.setHint(R.string.server_hint)
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_server)
            .setMessage(R.string.server_dialog_msg)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    if (!t.startsWith("http://") && !t.startsWith("https://")) {
                        t = "http://" + t
                    }
                    Prefs.setServerUrl(this, t.trimEnd('/'))
                    loadPanel()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showViewDialog() {
        val cur = Prefs.startPage(this)
        var idx = Prefs.PAGES.indexOf(cur)
        if (idx < 0) idx = 0
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_view)
            .setSingleChoiceItems(R.array.view_names, idx) { d, which ->
                Prefs.setStartPage(this, Prefs.PAGES[which])
                d.dismiss()
                loadPanel()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyKeepScreenOn() {
        if (Prefs.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_keep_screen).isChecked = Prefs.keepScreenOn(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_server -> { showServerDialog(); true }
            R.id.action_view -> { showViewDialog(); true }
            R.id.action_keep_screen -> {
                val v = !item.isChecked
                item.isChecked = v
                Prefs.setKeepScreenOn(this, v)
                applyKeepScreenOn()
                true
            }
            R.id.action_reload -> { loadPanel(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Кнопка «Назад»: если внутри панели есть история — шаг назад, иначе выход.
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
