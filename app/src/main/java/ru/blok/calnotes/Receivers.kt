package ru.blok.calnotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NoteStore.init(context)
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                val note = NoteStore.get(intent.getIntExtra(Reminders.EXTRA_ID, -1)) ?: return
                if (!note.done) Reminders.show(context, note)
            }
            Reminders.ACTION_CHECK -> Reminders.syncAll(context)
        }
    }
}

/** Пользователь смахнул уведомление (Android 14+ это позволяет) — показываем снова, пока не выполнено. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NoteStore.init(context)
        val note = NoteStore.get(intent.getIntExtra(Reminders.EXTRA_ID, -1)) ?: return
        if (!note.done && Reminders.triggerAt(note) <= System.currentTimeMillis()) {
            Reminders.show(context, note)
        }
    }
}

/** После перезагрузки, обновления приложения или смены времени — всё перепланировать. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.syncAll(context)
    }
}
