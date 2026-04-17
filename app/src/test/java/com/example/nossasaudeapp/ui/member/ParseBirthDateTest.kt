package com.example.nossasaudeapp.ui.member

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseBirthDateTest {

    // ──────────────────────────────────────────────────────────────────────────
    // parseBirthDate — valid inputs
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseBirthDate returns Instant for valid date 01012000`() {
        assertNotNull(parseBirthDate("01012000"))
    }

    @Test
    fun `parseBirthDate returns Instant for valid date 31122023`() {
        assertNotNull(parseBirthDate("31122023"))
    }

    @Test
    fun `parseBirthDate accepts date with non-digit separators by stripping them`() {
        // The function filters digits — "01/01/2000" has 8 digits → valid
        assertNotNull(parseBirthDate("01/01/2000"))
    }

    @Test
    fun `parseBirthDate handles Feb 29 in a leap year`() {
        assertNotNull(parseBirthDate("29022024")) // 2024 is a leap year
    }

    // ──────────────────────────────────────────────────────────────────────────
    // parseBirthDate — invalid inputs
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseBirthDate returns null for empty string`() {
        assertNull(parseBirthDate(""))
    }

    @Test
    fun `parseBirthDate returns null when fewer than 8 digits`() {
        assertNull(parseBirthDate("0101200")) // 7 digits
    }

    @Test
    fun `parseBirthDate returns null for invalid month 13`() {
        assertNull(parseBirthDate("01132000")) // month = 13
    }

    @Test
    fun `parseBirthDate returns null for day 00`() {
        assertNull(parseBirthDate("00012000")) // day = 0
    }

    @Test
    fun `parseBirthDate returns null for Feb 29 in non-leap year`() {
        assertNull(parseBirthDate("29022023")) // 2023 is not a leap year
    }

    @Test
    fun `parseBirthDate returns null for day 32`() {
        assertNull(parseBirthDate("32012000")) // day = 32
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MemberFormState — derived validation properties
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `nameError is true when name is blank`() {
        assertTrue(MemberFormState(name = "").nameError)
        assertTrue(MemberFormState(name = "   ").nameError)
    }

    @Test
    fun `nameError is false when name has content`() {
        assertFalse(MemberFormState(name = "João").nameError)
    }

    @Test
    fun `birthDateError is false when birthDateText is empty`() {
        assertFalse(MemberFormState(birthDateText = "").birthDateError)
    }

    @Test
    fun `birthDateError is true when birthDateText is set but unparseable`() {
        assertTrue(MemberFormState(birthDateText = "99999999").birthDateError)
    }

    @Test
    fun `birthDateError is false when birthDateText is a valid date`() {
        assertFalse(MemberFormState(birthDateText = "01012000").birthDateError)
    }

    @Test
    fun `weightError is false when weightText is empty`() {
        assertFalse(MemberFormState(weightText = "").weightError)
    }

    @Test
    fun `weightError is true when weightText is not a valid number`() {
        assertTrue(MemberFormState(weightText = "abc").weightError)
    }

    @Test
    fun `weightError is false for valid decimal weight`() {
        assertFalse(MemberFormState(weightText = "75.5").weightError)
    }

    @Test
    fun `isValid requires name and valid optional fields`() {
        val valid = MemberFormState(name = "Maria", birthDateText = "01012000", weightText = "60")
        assertTrue(valid.isValid)
    }

    @Test
    fun `isValid is false when any field has an error`() {
        val withBadDate = MemberFormState(name = "Maria", birthDateText = "99999999")
        assertFalse(withBadDate.isValid)
    }
}
