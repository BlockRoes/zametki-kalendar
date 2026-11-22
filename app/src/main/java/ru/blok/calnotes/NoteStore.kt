package ru.blok.calnotes

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Простое локальное хранилище заметок в JSON-файле (атомарная запись). */
object NoteStore {
    private lateinit var appContext: Context
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> get() = _notes

    fun init(context: Context) {
        synchronized(lock) {
            if (::file.isInitialized) return
            appContext = context.applicationContext
            file = AtomicFile(File(appContext.filesDir, "notes.json"))
            _notes.value = load()
        }
    }

    fun all(): List<Note> = _notes.value
    fun get(id: Int): Note? = _notes.value.firstOrNull { it.id == id }

    /** Заметки дня в порядке нумерации (1., 2., ...). */
    fun forDate(date: LocalDate, list: List<Note> = _notes.value): List<Note> =
        list.filter { it.date == date }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    fun indexInDay(note: Note): Int = forDate(note.date).indexOfFirst { it.id == note.id } + 1

    fun add(date: LocalDate, time: LocalTime, text: String, withSound: Boolean = false, soundUri: String? = null): Note {
        val note: Note
        synchronized(lock) {
            val nextId = (_notes.value.maxOfOrNull { it.id } ?: 0) + 1
            note = Note(nextId, date, time, text.trim(), withSound = withSound, soundUri = soundUri)
            commit(_notes.value + note)
        }
        Reminders.syncNote(appContext, note)
        return note
    }

    fun update(note: Note) {
        synchronized(lock) {
            commit(_notes.value.map { if (it.id == note.id) note else it })
        }
        Reminders.syncNote(appContext, note)
    }

    fun setDone(id: Int, done: Boolean) {
        val n = get(id) ?: return
        update(n.copy(done = done))
    }

    fun delete(id: Int) {
        synchronized(lock) { commit(_notes.value.filterNot { it.id == id }) }
        Reminders.cancelAll(appContext, id)
    }

    private fun commit(list: List<Note>) {
        save(list)
        _notes.value = list
    }

    private fun load(): List<Note> = try {
        if (!file.baseFile.exists()) emptyList() else {
            val arr = JSONArray(String(file.readFully(), Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.getInt("id"),
                    date = LocalDate.parse(o.getString("date")),
                    time = LocalTime.parse(o.optString("time", "09:00")),
                    text = o.getString("text"),
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("createdAt", 0L),
                    withSound = o.optBoolean("withSound", false),
                    soundUri = o.optString("soundUri", "").takeIf { it.isNotBlank() },
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id); put("date", n.date.toString()); put("time", n.time.toString())
                put("text", n.text); put("done", n.done); put("createdAt", n.createdAt); put("withSound", n.withSound)
                if (!n.soundUri.isNullOrBlank()) put("soundUri", n.soundUri)
            })
        }
        val out = file.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
