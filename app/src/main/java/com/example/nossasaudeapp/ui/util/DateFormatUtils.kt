package com.example.nossasaudeapp.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun ageLabel(birthDate: Instant, now: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val birth = birthDate.toLocalDateTime(tz).date
    val today = now.toLocalDateTime(tz).date
    var years = today.year - birth.year
    if (today.monthNumber < birth.monthNumber ||
        (today.monthNumber == birth.monthNumber && today.dayOfMonth < birth.dayOfMonth)
    ) years--
    return if (years >= 0) "$years anos" else ""
}

private val ptBrMonths = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun",
    "jul", "ago", "set", "out", "nov", "dez",
)

fun Instant.toShortDate(): String {
    val ld = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${ld.dayOfMonth} ${ptBrMonths[ld.monthNumber - 1]}"
}

fun Instant.toLongDate(): String {
    val ld = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthNumber, ld.year)
}
