package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextStyleTest {

    @Test
    fun `PLAIN style has no flags set`() {
        val plain = TextStyle.PLAIN
        assertFalse(plain.bold)
        assertFalse(plain.italic)
        assertFalse(plain.underline)
    }

    @Test
    fun `PLAIN style hasAny returns false`() {
        assertFalse(TextStyle.PLAIN.hasAny())
    }

    @Test
    fun `default constructor creates plain style`() {
        assertEquals(TextStyle.PLAIN, TextStyle())
    }

    @Test
    fun `bold only style`() {
        val style = TextStyle(bold = true)
        assertTrue(style.bold)
        assertFalse(style.italic)
        assertFalse(style.underline)
        assertTrue(style.hasAny())
    }

    @Test
    fun `italic only style`() {
        val style = TextStyle(italic = true)
        assertFalse(style.bold)
        assertTrue(style.italic)
        assertFalse(style.underline)
        assertTrue(style.hasAny())
    }

    @Test
    fun `underline only style`() {
        val style = TextStyle(underline = true)
        assertFalse(style.bold)
        assertFalse(style.italic)
        assertTrue(style.underline)
        assertTrue(style.hasAny())
    }

    @Test
    fun `all flags combined`() {
        val style = TextStyle(bold = true, italic = true, underline = true)
        assertTrue(style.bold)
        assertTrue(style.italic)
        assertTrue(style.underline)
        assertTrue(style.hasAny())
    }

    @Test
    fun `data class equality works correctly`() {
        val a = TextStyle(bold = true, italic = false, underline = true)
        val b = TextStyle(bold = true, italic = false, underline = true)
        val c = TextStyle(bold = true, italic = true, underline = true)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = TextStyle(bold = true)
        val copied = original.copy(italic = true)
        assertTrue(copied.bold)
        assertTrue(copied.italic)
        assertFalse(copied.underline)
    }
}
