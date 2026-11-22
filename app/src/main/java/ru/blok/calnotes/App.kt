package ru.blok.calnotes

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NoteStore.init(this)
        Reminders.createChannel(this)
    }
}
