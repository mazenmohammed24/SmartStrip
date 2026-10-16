package com.powerk.app.components

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powerk.app.Strip

/**
 * Per-strip "sensitive info" masking, modelled on Voltra: a strip still carrying its auto-generated
 * device ID as name shows only the first 5 characters until the user reveals it. Custom names are never masked.
 */
object Privacy {
    private var prefs: android.content.SharedPreferences? = null
    private var revealed by mutableStateOf(emptySet<String>())

    private val autoName = Regex("^(?:[0-9A-Fa-f]{10,}|MTTL[ _-]?[0-9A-Fa-f]{4,})$", RegexOption.IGNORE_CASE)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("powerk", Context.MODE_PRIVATE)
        prefs = p
        revealed = p.getStringSet("revealed_strips", emptySet()).orEmpty().toSet()
    }

    fun isSensitive(strip: Strip): Boolean = autoName.matches(strip.name.trim()) || strip.name.trim().equals(strip.mac, true)

    fun isMasked(strip: Strip): Boolean = isSensitive(strip) && strip.mac !in revealed

    fun toggle(mac: String) {
        revealed = if (mac in revealed) revealed - mac else revealed + mac
        prefs?.edit()?.putStringSet("revealed_strips", revealed)?.apply()
    }

    fun name(strip: Strip): String = if (isMasked(strip)) strip.name.trim().take(5) + "*******" else strip.name

    /** Masks a bare id (e.g. a MAC suffix) when the strip is masked. */
    fun id(strip: Strip, text: String): String = if (isMasked(strip)) "*".repeat(text.length.coerceAtMost(6)) else text
}

/** Eye toggle shown next to a strip name; only visible when the name is an auto-generated ID. */
@Composable
fun NameEye(strip: Strip, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    if (!Privacy.isSensitive(strip)) return
    val masked = Privacy.isMasked(strip)
    Icon(
        if (masked) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
        if (masked) "Show strip name" else "Hide strip name",
        modifier.size(size).tap { Privacy.toggle(strip.mac) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
