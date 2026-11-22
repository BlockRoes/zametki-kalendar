package ru.blok.calnotes

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Режим темы: как в системе (по умолчанию), светлая или тёмная. Без AppCompatDelegate. */
object ThemePrefs {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val PREFS = "settings"
    private const val KEY = "theme"

    private var prefs: SharedPreferences? = null
    private val _mode = MutableStateFlow(SYSTEM)
    val mode: StateFlow<String> get() = _mode

    @Synchronized
    fun init(ctx: Context) {
        try {
            if (prefs != null) return
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _mode.value = parse(runCatching { p.getString(KEY, SYSTEM) }.getOrNull())
            prefs = p
        } catch (_: Exception) {
            _mode.value = SYSTEM
        }
    }

    fun set(value: String) {
        val v = parse(value)
        _mode.value = v
        try {
            prefs?.edit()?.putString(KEY, v)?.apply()
        } catch (_: Exception) { }
    }

    fun isDark(systemDark: Boolean, mode: String = _mode.value): Boolean = when (mode) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }

    fun systemIsDark(ctx: Context): Boolean = try {
        val night = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    } catch (_: Exception) {
        false
    }

    private fun parse(raw: String?): String = when (raw) {
        LIGHT, DARK, SYSTEM -> raw
        else -> SYSTEM
    }
}
