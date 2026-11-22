package ru.blok.calnotes

import android.app.Application
import android.content.Context

class App : Application() {
    override fun attachBaseContext(base: Context) {
        ThemePrefs.init(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        ThemePrefs.init(this)
        NoteStore.init(this)
        Reminders.createChannel(this)
    }
}
