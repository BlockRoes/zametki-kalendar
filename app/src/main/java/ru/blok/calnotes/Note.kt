package ru.blok.calnotes

import java.time.LocalDate
import java.time.LocalTime

data class Note(
    val id: Int,
    val date: LocalDate,
    val time: LocalTime,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val withSound: Boolean = false,
    val soundUri: String? = null,
)

val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
