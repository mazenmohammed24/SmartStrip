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

/**
 * Backend endpoint. Locked in the Android client: the app always talks to this server and
 * users cannot see or change it.
 */
object Server {
    /** Hardcoded SmartStrip backend. */
    const val BASE_URL = "http://remotedsk.tplinkdns.com:3000/SmartStrip"

    private var prefs: SharedPreferences? = null

    var token by mutableStateOf("")
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        token = prefs?.getString("token", "").orEmpty()
    }

    val configured: Boolean get() = true
    val base: String get() = BASE_URL
}

/** Local app appearance. This is kept on-device so the Android app can switch instantly. */
object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val DEFAULT_ACCENT = "#1AA34A"
    const val COMFORTABLE = "comfortable"
    const val COMPACT = "compact"

    var mode by mutableStateOf(SYSTEM)
        private set
    var accentColor by mutableStateOf(DEFAULT_ACCENT)
        private set

    /** Material You: follow wallpaper colors instead of the brand accent (Android 12+). */
    var dynamicColor by mutableStateOf(false)
        private set

    /** Trim decorative animation; the system animator scale can also force this on. */
    var reducedMotion by mutableStateOf(false)
        private set

    /** List density: comfortable (default) or compact. */
    var density by mutableStateOf(COMFORTABLE)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        prefs = p
        mode = p.getString("themeMode", SYSTEM).orEmpty().ifBlank { SYSTEM }
            .takeIf { it == SYSTEM || it == LIGHT || it == DARK } ?: SYSTEM
        accentColor = normalizeColor(p.getString("accentColor", DEFAULT_ACCENT).orEmpty()).let { if (it == "#1B6B45") DEFAULT_ACCENT else it }
        dynamicColor = p.getBoolean("dynamicColor", false)
        reducedMotion = p.getBoolean("reducedMotion", false)
        density = p.getString("density", COMFORTABLE).orEmpty().takeIf { it == COMPACT } ?: COMFORTABLE
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

    fun setDynamic(value: Boolean) {
        dynamicColor = value
        prefs?.edit()?.putBoolean("dynamicColor", value)?.apply()
    }

    fun setMotion(value: Boolean) {
        reducedMotion = value
        prefs?.edit()?.putBoolean("reducedMotion", value)?.apply()
    }

    fun setDensityMode(value: String) {
        density = if (value == COMPACT) COMPACT else COMFORTABLE
        prefs?.edit()?.putString("density", density)?.apply()
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