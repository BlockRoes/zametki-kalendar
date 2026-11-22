package ru.blok.calnotes

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale("ru")
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val dayTitleFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
private val shortFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun String.cap() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(RU) else it.toString() }
private fun monthTitle(ym: YearMonth) = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).cap() + " " + ym.year

private data class EditState(val note: Note?, val date: LocalDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val notes by NoteStore.notes.collectAsStateWithLifecycle()
    val tick by activity.resumeTick
    val today = remember(tick) { LocalDate.now() }
    var selected by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var editing by remember { mutableStateOf<EditState?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var updateCheck by remember { mutableIntStateOf(0) }

    // открытие из уведомления
    val open by activity.openDate
    LaunchedEffect(open) {
        open?.let { selected = it; month = YearMonth.from(it); activity.openDate.value = null }
    }

    val notifOk = remember(tick) { Reminders.notificationsAllowed(activity) }
    val exactOk = remember(tick) { Reminders.canExact(activity) }
    val overdue = notes.filter { !it.done && it.date.isBefore(today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметки-календарь") },
                actions = {
                    IconButton(onClick = { selected = today; month = YearMonth.from(today) }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Сегодня")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = EditState(null, selected) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Заметка") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!notifOk) item {
                Banner(
                    "Уведомления выключены — напоминания не появятся.",
                    "Включить", MaterialTheme.colorScheme.errorContainer,
                ) { activity.requestNotifications() }
            }
            if (!exactOk) item {
                Banner(
                    "Нет разрешения на точные будильники — напоминание может прийти с опозданием.",
                    "Разрешить", MaterialTheme.colorScheme.tertiaryContainer,
                ) { activity.openExactAlarmSettings() }
            }
            if (overdue.isNotEmpty()) item {
                val first = overdue.minOf { it.date }
                Banner(
                    "Просрочено невыполненных: ${overdue.size}",
                    "Показать", MaterialTheme.colorScheme.errorContainer,
                ) { selected = first; month = YearMonth.from(first) }
            }
            item {
                MonthCalendar(
                    month = month, selected = selected, today = today, notes = notes,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelect = { selected = it },
                )
            }
            val dayNotes = NoteStore.forDate(selected, notes)
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selected.format(dayTitleFmt).cap(),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    if (dayNotes.isNotEmpty()) {
                        val left = dayNotes.count { !it.done }
                        Text(
                            if (left == 0) "всё выполнено ✓" else "осталось: $left",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (left == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (dayNotes.isEmpty()) item {
                Text(
                    "Нет заметок. Нажмите «Заметка», чтобы добавить.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(dayNotes, key = { _, n -> n.id }) { i, n ->
                NoteCard(
                    index = i + 1, note = n, overdue = !n.done && n.date.isBefore(today),
                    onDone = { NoteStore.setDone(n.id, true) },
                    onUndo = { NoteStore.setDone(n.id, false) },
                    onEdit = { editing = EditState(n, n.date) },
                    onDelete = { deleting = n },
                )
            }
        }
    }

    editing?.let { st ->
        NoteDialog(
            initial = st.note, date = st.date,
            onDismiss = { editing = null },
            onSave = { text, date, time, withSound, soundUri ->
                if (st.note == null) NoteStore.add(date, time, text, withSound, soundUri)
                else NoteStore.update(st.note.copy(text = text.trim(), date = date, time = time, withSound = withSound, soundUri = soundUri))
                selected = date; month = YearMonth.from(date)
                editing = null
            },
        )
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить заметку?") },
            text = { Text(n.text) },
            confirmButton = { TextButton(onClick = { NoteStore.delete(n.id); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
    if (showSettings) SettingsDialog(activity, tick, onCheckUpdates = { updateCheck++ }) { showSettings = false }
    UpdateHost(activity, updateCheck)
}

private val LocalDateSaver = androidx.compose.runtime.saveable.Saver<MutableState<LocalDate>, String>(
    save = { it.value.toString() },
    restore = { mutableStateOf(LocalDate.parse(it)) },
)

@Composable
private fun rememberSaveable(stateSaver: androidx.compose.runtime.saveable.Saver<MutableState<LocalDate>, String>, init: () -> MutableState<LocalDate>): MutableState<LocalDate> =
    androidx.compose.runtime.saveable.rememberSaveable(saver = stateSaver, init = init)

@Composable
private fun Banner(text: String, action: String, color: Color, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth, selected: LocalDate, today: LocalDate, notes: List<Note>,
    onPrev: () -> Unit, onNext: () -> Unit, onSelect: (LocalDate) -> Unit,
) {
    val byDate = remember(notes) { notes.groupBy { it.date } }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowLeft, "Предыдущий месяц") }
                Text(
                    monthTitle(month), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowRight, "Следующий месяц") }
            }
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEachIndexed { i, d ->
                    Text(
                        d, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i >= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val rows = (offset + days + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                            if (d in 1..days) {
                                val date = month.atDay(d)
                                DayCell(date, date == selected, date == today, byDate[date].orEmpty(), today) { onSelect(date) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                Legend(MaterialTheme.colorScheme.tertiary, "есть дела")
                Spacer(Modifier.width(12.dp))
                Legend(MaterialTheme.colorScheme.error, "просрочено")
                Spacer(Modifier.width(12.dp))
                Legend(MaterialTheme.colorScheme.outline, "выполнено")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, dayNotes: List<Note>, today: LocalDate, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val undone = dayNotes.count { !it.done }
    val overdue = undone > 0 && date.isBefore(today)
    val markColor = when {
        dayNotes.isEmpty() -> Color.Transparent
        overdue -> cs.error
        undone > 0 -> cs.tertiary
        else -> cs.outline
    }
    val bg = when {
        isSelected -> cs.primary
        overdue -> cs.errorContainer
        undone > 0 -> cs.tertiaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> cs.onPrimary
        overdue -> cs.onErrorContainer
        undone > 0 -> cs.onTertiaryContainer
        else -> cs.onSurface
    }
    Column(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(2.dp, cs.primary), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}", color = fg, fontSize = 15.sp,
            fontWeight = if (undone > 0 || isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(Modifier.height(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val dots = dayNotes.size.coerceAtMost(3)
            repeat(dots) {
                Box(
                    Modifier.padding(horizontal = 1.dp).size(5.dp).clip(CircleShape)
                        .background(if (isSelected) cs.onPrimary else markColor)
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    index: Int, note: Note, overdue: Boolean,
    onDone: () -> Unit, onUndo: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                note.done -> cs.surfaceVariant
                overdue -> cs.errorContainer
                else -> cs.secondaryContainer
            }
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "$index. ${note.text}",
                    modifier = Modifier.weight(1f).alpha(if (note.done) 0.6f else 1f),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null,
                )
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Изменить") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Удалить") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    buildString {
                        append(note.time.format(timeFmt))
                        if (note.withSound) append(" · со звуком")
                        if (overdue) append(" · просрочено")
                        else if (note.done) append(" · выполнено")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue) cs.error else cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (note.done) {
                    TextButton(onClick = onUndo) { Text("Вернуть") }
                } else {
                    Button(onClick = onDone) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Выполнено")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDialog(
    initial: Note?, date: LocalDate,
    onDismiss: () -> Unit, onSave: (String, LocalDate, LocalTime, Boolean, String?) -> Unit,
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var d by remember { mutableStateOf(initial?.date ?: date) }
    var t by remember { mutableStateOf(initial?.time ?: DEFAULT_TIME) }
    var withSound by remember { mutableStateOf(initial?.withSound ?: false) }
    var soundUri by remember { mutableStateOf(initial?.soundUri) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data
        val uri = if (data == null) null else if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (uri != null) {
            Reminders.grantSoundAccess(ctx, uri)
            soundUri = uri.toString()
        } else {
            // На части устройств «По умолчанию» возвращается как null
            soundUri = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая заметка" else "Изменить заметку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Что сделать") },
                    placeholder = { Text("Полить огород") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Дата: " + d.format(shortFmt))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Напомнить в " + t.format(timeFmt), maxLines = 1, softWrap = false)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 88.dp)) {
                        Switch(checked = withSound, onCheckedChange = { withSound = it })
                        Text(
                            if (withSound) "Со звуком" else "Без звука",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                if (withSound) {
                    OutlinedButton(
                        onClick = { ringtonePicker.launch(Reminders.ringtonePickerIntent(soundUri)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Звук: " + Reminders.soundDisplayName(ctx, soundUri), maxLines = 1, softWrap = false)
                    }
                }
                Text(
                    if (withSound)
                        "Уведомление придёт выбранным звуком и не уберётся, пока вы не нажмёте «Выполнено»."
                    else
                        "Уведомление будет без звука и не уберётся, пока вы не нажмёте «Выполнено».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, d, t, withSound, soundUri) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = st) }
    }
    if (pickTime) {
        val st = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Время напоминания") },
            text = { TimeInput(state = st) },
            confirmButton = { TextButton(onClick = { t = LocalTime.of(st.hour, st.minute); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SettingsDialog(activity: MainActivity, tick: Int, onCheckUpdates: () -> Unit, onDismiss: () -> Unit) {
    val notifOk = remember(tick) { Reminders.notificationsAllowed(activity) }
    val exactOk = remember(tick) { Reminders.canExact(activity) }
    val batteryOk = remember(tick) { activity.isIgnoringBattery() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Разрешения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermRow("Уведомления", notifOk) { activity.requestNotifications() }
                if (Build.VERSION.SDK_INT >= 31) PermRow("Точные будильники", exactOk) { activity.openExactAlarmSettings() }
                PermRow("Без ограничений батареи", batteryOk) { activity.requestBatteryExemption() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Версия " + Updater.installedName(activity), Modifier.weight(1f))
                    TextButton(onClick = { onCheckUpdates(); onDismiss() }) { Text("Проверить обновления") }
                }
                Text(
                    "Чтобы напоминания приходили вовремя даже после перезагрузки, разрешите всё выше. " +
                        "На Xiaomi/Huawei/Samsung также включите «Автозапуск» и уберите приложение из «спящих».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✅ " else "⚠️ ") + label, Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text("Включить") }
    }
}
