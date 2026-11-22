package ru.blok.calnotes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Планирование и показ «неубираемых» уведомлений (без звука или со звуком). */
object Reminders {
    const val CHANNEL_ID = "silent_reminders"
    const val ACTION_FIRE = "ru.blok.calnotes.FIRE"
    const val ACTION_CHECK = "ru.blok.calnotes.CHECK"
    const val ACTION_DISMISSED = "ru.blok.calnotes.DISMISSED"
    const val EXTRA_ID = "note_id"
    const val EXTRA_DATE = "open_date"
    private const val CHECK_REQ = -1

    private val ru = Locale("ru")
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val defaultSound: Uri get() = Settings.System.DEFAULT_NOTIFICATION_URI

    fun createChannel(ctx: Context) {
        val silent = NotificationChannel(CHANNEL_ID, "Напоминания (без звука)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Беззвучные напоминания о заметках"
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            enableLights(false)
            setShowBadge(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(silent)
    }

    fun triggerAt(note: Note): Long =
        LocalDateTime.of(note.date, note.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Пересинхронизировать всё: после загрузки, смены времени, открытия приложения. */
    fun syncAll(ctx: Context) {
        NoteStore.init(ctx)
        NoteStore.all().forEach { syncNote(ctx, it) }
        schedulePeriodicCheck(ctx)
    }

    fun syncNote(ctx: Context, note: Note) {
        if (note.withSound) ensureSoundChannel(ctx, resolveSoundUri(ctx, note.soundUri))
        if (note.done) {
            cancelAll(ctx, note.id)
            return
        }
        val t = triggerAt(note)
        if (t <= System.currentTimeMillis()) {
            cancelAlarm(ctx, note.id)
            show(ctx, note)
        } else {
            NotificationManagerCompat.from(ctx).cancel(note.id)
            scheduleAlarm(ctx, note.id, t)
        }
    }

    fun cancelAll(ctx: Context, id: Int) {
        cancelAlarm(ctx, id)
        NotificationManagerCompat.from(ctx).cancel(id)
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun alarmIntent(ctx: Context, id: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleAlarm(ctx: Context, id: Int, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, id: Int) {
        val pi = alarmIntent(ctx, id, PendingIntent.FLAG_NO_CREATE) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /** Страховка: раз в ~час проверяем, что все нужные уведомления на месте. */
    fun schedulePeriodicCheck(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, CHECK_REQ,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_HOUR, pi,
        )
    }

    fun notificationsAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun isDefaultSound(uri: Uri?): Boolean {
        if (uri == null) return true
        return uri == defaultSound || uri.toString() == defaultSound.toString()
    }

    fun soundDisplayName(ctx: Context, stored: String?): String {
        val uri = stored?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (isDefaultSound(uri)) return "По умолчанию"
        return try {
            RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx)?.takeIf { it.isNotBlank() } ?: "Выбранный звук"
        } catch (_: Exception) {
            "Выбранный звук"
        }
    }

    fun ringtonePickerIntent(current: String?): Intent {
        val existing = current?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } ?: defaultSound
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultSound)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук напоминания")
        }
    }

    fun grantSoundAccess(ctx: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) { /* системные ringtone URI обычно не persistable */ }
        for (pkg in listOf("com.android.systemui", "android")) {
            try {
                ctx.grantUriPermission(pkg, uri, flags)
            } catch (_: Exception) { }
        }
    }

    fun resolveSoundUri(ctx: Context, stored: String?): Uri {
        val parsed = stored?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val uri = parsed ?: defaultSound
        if (!isDefaultSound(uri)) {
            grantSoundAccess(ctx, uri)
            val readable = try {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
            if (!readable) return defaultSound
        }
        return uri
    }

    fun channelIdForSound(uri: Uri): String {
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "sound_$hex"
    }

    fun ensureSoundChannel(ctx: Context, uri: Uri) {
        createChannel(ctx)
        grantSoundAccess(ctx, uri)
        val id = channelIdForSound(uri)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) != null) return
        val soundAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val title = if (isDefaultSound(uri)) {
            "Напоминания (звук по умолчанию)"
        } else {
            val name = try {
                RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
            if (name != null) "Напоминания: $name" else "Напоминания (со звуком)"
        }
        val ch = NotificationChannel(id, title, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Напоминания о заметках со звуком"
            setSound(uri, soundAttrs)
            enableVibration(true)
            enableLights(false)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun show(ctx: Context, note: Note) {
        if (note.done || !notificationsAllowed(ctx)) return
        createChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, note.id,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_DATE, note.date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissed = PendingIntent.getBroadcast(
            ctx, note.id,
            Intent(ctx, DismissReceiver::class.java).setAction(ACTION_DISMISSED).putExtra(EXTRA_ID, note.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val overdue = note.date.isBefore(LocalDate.now())
        val num = NoteStore.indexInDay(note).coerceAtLeast(1)
        val title = if (overdue) "Просрочено (${note.date.format(dateFmt)})" else "Заметка на сегодня"
        val text = "$num. ${note.text}"
        val channelId: String
        if (note.withSound) {
            val uri = resolveSoundUri(ctx, note.soundUri)
            ensureSoundChannel(ctx, uri)
            channelId = channelIdForSound(uri)
        } else {
            channelId = CHANNEL_ID
        }
        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nОткройте приложение и нажмите «Выполнено»."))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(triggerAt(note))
            .setShowWhen(true)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
        if (note.withSound) {
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setSilent(true)
        }
        val n = builder.build()
        try {
            NotificationManagerCompat.from(ctx).notify(note.id, n)
        } catch (e: SecurityException) { /* нет разрешения */ }
    }
}
