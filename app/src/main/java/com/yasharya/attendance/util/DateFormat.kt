package com.yasharya.attendance.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timestamps are stored as UTC epoch millis and formatted here, at the edge.
 * Keeping formatting out of the data layer means a record does not change
 * meaning when the device crosses a time zone.
 */

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val fullDayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

fun Long.formatTime(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(timeFormatter)

fun Long.formatDay(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(dayFormatter)

fun Long.formatFullDay(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(fullDayFormatter)

fun Long.formatMonth(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(monthFormatter)

fun Long.formatDateTime(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(dateTimeFormatter)

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** "Good morning" and friends, so the home screen greeting is not stuck at one time of day. */
fun greetingFor(hour: Int): String = when (hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
