package ru.blok.calnotes

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface Phase {
    data object Offer : Phase
    data class Downloading(val percent: Int) : Phase
    data class Ready(val file: File) : Phase
    data object Failed : Phase
}

/**
 * Проверка обновлений: автоматически при запуске (молча) и вручную (checkTick > 0, с сообщениями).
 */
@Composable
fun UpdateHost(activity: MainActivity, checkTick: Int) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableStateOf<Phase>(Phase.Offer) }
    var checkedAuto by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(checkTick) {
        val manual = checkTick > 0
        if (!manual) {
            if (checkedAuto) return@LaunchedEffect
            checkedAuto = true
        }
        try {
            val result = withContext(Dispatchers.IO) { runCatching { Updater.checkBlocking(activity) } }
            val found = result.getOrNull()
            when {
                found != null -> { info = found; phase = Phase.Offer }
                manual && result.isFailure ->
                    Toast.makeText(activity, "Не удалось проверить обновления. Проверьте интернет.", Toast.LENGTH_LONG).show()
                manual ->
                    Toast.makeText(activity, "У вас последняя версия (${Updater.installedName(activity)})", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            if (manual) {
                try { Toast.makeText(activity, "Не удалось проверить обновления. Проверьте интернет.", Toast.LENGTH_LONG).show() } catch (_: Exception) { }
            }
        }
    }

    val cur = info ?: return
    val ph = phase
    AlertDialog(
        onDismissRequest = { if (ph !is Phase.Downloading) info = null },
        title = { Text("Доступно обновление ${cur.versionName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (cur.changelog.isNotBlank()) Text(cur.changelog)
                when (ph) {
                    is Phase.Downloading -> {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { ph.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Скачивание… ${ph.percent}%")
                    }
                    is Phase.Ready -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (Updater.canInstall(activity)) "Файл скачан. Нажмите «Установить»."
                            else "Файл скачан. Android просит разрешить установку из этого приложения: " +
                                "нажмите «Установить», включите переключатель, вернитесь назад и нажмите «Установить» ещё раз."
                        )
                    }
                    Phase.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Не удалось скачать обновление. Проверьте интернет и попробуйте ещё раз.")
                    }
                    Phase.Offer -> {}
                }
            }
        },
        confirmButton = {
            when (ph) {
                Phase.Offer, Phase.Failed -> TextButton(onClick = {
                    phase = Phase.Downloading(0)
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { Updater.downloadBlocking(activity, cur) { p -> phase = Phase.Downloading(p) } }
                        }
                        phase = r.getOrNull()?.let { Phase.Ready(it) } ?: Phase.Failed
                    }
                }) { Text(if (ph == Phase.Failed) "Повторить" else "Обновить") }
                is Phase.Downloading -> {}
                is Phase.Ready -> TextButton(onClick = {
                    if (Updater.canInstall(activity)) Updater.install(activity, ph.file)
                    else Updater.openInstallPermissionSettings(activity)
                }) { Text("Установить") }
            }
        },
        dismissButton = {
            if (ph !is Phase.Downloading) TextButton(onClick = { info = null }) { Text("Позже") }
        },
    )
}
