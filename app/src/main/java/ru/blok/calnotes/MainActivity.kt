package ru.blok.calnotes

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    val openDate = mutableStateOf<LocalDate?>(null)
    val resumeTick = mutableIntStateOf(0)
    private var fromButton = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && fromButton) openNotificationSettings()
            fromButton = false
            Reminders.syncAll(this)
            resumeTick.intValue++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.init(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.notificationsAllowed(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CalNotesTheme {
                MainScreen(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Reminders.syncAll(this)
        resumeTick.intValue++
    }

    private fun handleIntent(intent: Intent?) {
        val s = intent?.getStringExtra(Reminders.EXTRA_DATE) ?: return
        runCatching { LocalDate.parse(s) }.getOrNull()?.let { openDate.value = it }
        intent.removeExtra(Reminders.EXTRA_DATE)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fromButton = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    fun openNotificationSettings() {
        safeStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    fun isIgnoringBattery(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun requestBatteryExemption() {
        if (!safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))) {
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun safeStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        false
    }
}
