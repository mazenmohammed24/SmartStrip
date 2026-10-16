package com.powerk.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

private const val DEFAULT_SERVER_URL = "https://notes-thinking-amendment-charitable.trycloudflare.com"

/** Server URL (full https/http URL to the SmartStrip server), persisted in SharedPreferences. */
object Server {
    private var prefs: SharedPreferences? = null

    /** Full base URL, e.g. https://xyz.trycloudflare.com or http://192.168.1.10:4001 */
    var serverUrl by mutableStateOf(DEFAULT_SERVER_URL)
        private set
    var token by mutableStateOf("")
        private set

    /** Hostname portion for display in headers/status text. */
    val host: String get() = runCatching { java.net.URL(serverUrl).host }.getOrElse { serverUrl }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        prefs = p
        // Migrate legacy host+port to full URL if needed
        val legacyHost = p.getString("host", "").orEmpty()
        val legacyPort = p.getInt("port", 8080)
        val storedUrl = p.getString("serverUrl", "").orEmpty()
        serverUrl = when {
            storedUrl.isNotBlank() -> storedUrl
            legacyHost.isNotBlank() -> "http://$legacyHost:$legacyPort"
            else -> DEFAULT_SERVER_URL
        }
        token = p.getString("token", "").orEmpty()
    }

    fun save(newUrl: String, newToken: String) {
        serverUrl = newUrl.trim().trimEnd('/')
        token = newToken.trim()
        prefs?.edit()
            ?.putString("serverUrl", serverUrl)
            ?.putString("token", token)
            ?.apply()
    }

    val configured: Boolean get() = serverUrl.isNotBlank()
    val base: String get() = serverUrl
}


/** Local app appearance. This is kept on-device so the Android app can switch instantly. */
object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val DEFAULT_ACCENT = "#1AA34A"

    var mode by mutableStateOf(SYSTEM)
        private set
    var accentColor by mutableStateOf(DEFAULT_ACCENT)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        prefs = p
        mode = p.getString("themeMode", SYSTEM).orEmpty().ifBlank { SYSTEM }
            .takeIf { it == SYSTEM || it == LIGHT || it == DARK } ?: SYSTEM
        accentColor = normalizeColor(p.getString("accentColor", DEFAULT_ACCENT).orEmpty()).let { if (it == "#1B6B45") DEFAULT_ACCENT else it }
    }

    fun set(value: String) {
        mode = value.takeIf { it == SYSTEM || it == LIGHT || it == DARK } ?: SYSTEM
        prefs?.edit()?.putString("themeMode", mode)?.apply()
    }

    /** Server is the source of truth; a changed value is persisted so cold starts match. */
    fun applyRemote(remoteMode: String, remoteAccent: String) {
        if (remoteMode != mode) set(remoteMode)
        val next = normalizeColor(remoteAccent)
        if (next != accentColor) setAccent(next)
    }

    fun setAccent(value: String) {
        accentColor = normalizeColor(value)
        prefs?.edit()?.putString("accentColor", accentColor)?.apply()
    }

    private fun normalizeColor(value: String): String {
        val cleaned = value.trim().uppercase()
        val withHash = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
        return if (Regex("^#[0-9A-F]{6}$").matches(withHash)) withHash else DEFAULT_ACCENT
    }
}

/** In-app language: null follows the phone language. */
object Lang {
    var code: String? = null
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        prefs = p
        code = p.getString("lang", "").orEmpty().ifBlank { null }
    }

    /** Applies a new choice immediately: platform per-app locale on 33+, recreate below that. */
    fun set(context: Context, value: String?) {
        code = value
        prefs?.edit()?.putString("lang", value.orEmpty())?.apply()
        val activity = context.findActivity() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPlatformLocales(activity, value)
        } else {
            activity.applyLocale(value)
            activity.recreate()
        }
    }

    /** Called from Activity.onCreate so the first composition already uses the stored language. */
    fun applyStored(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && code != null) {
            activity.applyLocale(code)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setPlatformLocales(activity: Activity, value: String?) {
        val manager = activity.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales =
            if (value == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
    }

    @Suppress("DEPRECATION")
    private fun Activity.applyLocale(value: String?) {
        val locale = if (value == null) LocaleList.getDefault()[0] else Locale.forLanguageTag(value)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
