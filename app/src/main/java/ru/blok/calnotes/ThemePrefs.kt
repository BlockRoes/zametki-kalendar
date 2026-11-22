package ru.blok.calnotes

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Режим темы: как в системе (по умолчанию), светлая или тёмная. */
object ThemePrefs {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val PREFS = "settings"
    private const val KEY = "theme"

    private lateinit var prefs: SharedPreferences
    private val _mode = MutableStateFlow(SYSTEM)
    val mode: StateFlow<String> get() = _mode

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) {
            applyDelegate(_mode.value)
            return
        }
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = prefs.getString(KEY, SYSTEM) ?: SYSTEM
        applyDelegate(_mode.value)
    }

    fun set(value: String) {
        val v = when (value) {
            LIGHT, DARK -> value
            else -> SYSTEM
        }
        if (::prefs.isInitialized) prefs.edit().putString(KEY, v).apply()
        _mode.value = v
        applyDelegate(v)
    }

    fun isDark(systemDark: Boolean, mode: String = _mode.value): Boolean = when (mode) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }

    private fun applyDelegate(value: String) {
        val night = when (value) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night)
        }
    }
}
