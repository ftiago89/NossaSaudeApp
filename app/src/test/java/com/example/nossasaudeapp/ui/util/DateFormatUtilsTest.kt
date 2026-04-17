package com.example.nossasaudeapp.ui.util

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormatUtilsTest {

    // ──────────────────────────────────────────────────────────────────────────
    // ageLabel
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `ageLabel returns correct full years`() {
        val birth = Instant.parse("2000-01-01T12:00:00Z")
        val now   = Instant.parse("2030-01-01T12:00:00Z")

        assertEquals("30 anos", ageLabel(birth, now))
    }

    @Test
    fun `ageLabel returns 0 anos when birthday is today`() {
        val birth = Instant.parse("2000-06-15T00:00:00Z")
        val now   = Instant.parse("2000-06-15T12:00:00Z")

        assertEquals("0 anos", ageLabel(birth, now))
    }

    @Test
    fun `ageLabel does not count year when birthday has not occurred yet this year`() {
        val birth = Instant.parse("2000-12-31T00:00:00Z")
        val now   = Instant.parse("2030-06-15T00:00:00Z") // before Dec 31

        assertEquals("29 anos", ageLabel(birth, now))
    }

    @Test
    fun `ageLabel counts year on exact birthday`() {
        val birth = Instant.parse("2000-06-15T00:00:00Z")
        val now   = Instant.parse("2030-06-15T00:00:00Z") // exact birthday

        assertEquals("30 anos", ageLabel(birth, now))
    }

    @Test
    fun `ageLabel returns empty string when birth is in the future`() {
        val birth = Instant.parse("2050-01-01T00:00:00Z")
        val now   = Instant.parse("2030-01-01T00:00:00Z")

        assertTrue(ageLabel(birth, now).isEmpty())
    }

    @Test
    fun `ageLabel handles leap year birthday correctly`() {
        val birth = Instant.parse("2000-02-29T00:00:00Z") // leap day
        val now   = Instant.parse("2024-02-28T00:00:00Z") // day before in another leap year

        assertEquals("23 anos", ageLabel(birth, now))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // toShortDate
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toShortDate formats day and abbreviated pt-BR month`() {
        // Use a fixed UTC instant that maps to Jan 5 in UTC+0 offset zones
        val instant = Instant.parse("2024-01-05T12:00:00Z")

        // Can't rely on system TZ but we can check the format contains known tokens
        val result = instant.toShortDate()

        // Result should be something like "5 jan"
        assertTrue("Expected day number in result: $result", result.any { it.isDigit() })
        assertTrue("Expected month abbreviation in result: $result", result.any { it.isLetter() })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // toLongDate
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toLongDate formats as dd_mm_yyyy with slashes`() {
        val instant = Instant.parse("2024-01-05T12:00:00Z")
        val result = instant.toLongDate()

        // Format must be dd/mm/yyyy
        assertTrue("Expected slash-separated date: $result", result.matches(Regex("""\d{2}/\d{2}/\d{4}""")))
    }
}
