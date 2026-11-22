package ru.blok.calnotes

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try { ThemePrefs.init(this) } catch (_: Exception) { }
        try { NoteStore.init(this) } catch (_: Exception) { }
        try { Reminders.createChannel(this) } catch (_: Exception) { }
    }
}
