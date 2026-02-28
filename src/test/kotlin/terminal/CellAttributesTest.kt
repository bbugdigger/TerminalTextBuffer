package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CellAttributesTest {
    @Test
    fun `DEFAULT attributes have default colors and plain style`() {
        val attrs = CellAttributes.DEFAULT
        assertEquals(TerminalColor.DEFAULT, attrs.foreground)
        assertEquals(TerminalColor.DEFAULT, attrs.background)
        assertEquals(TextStyle.PLAIN, attrs.style)
    }

    @Test
    fun `default constructor creates DEFAULT attributes`() {
        assertEquals(CellAttributes.DEFAULT, CellAttributes())
    }

    @Test
    fun `custom foreground color`() {
        val attrs = CellAttributes(foreground = TerminalColor.RED)
        assertEquals(TerminalColor.RED, attrs.foreground)
        assertEquals(TerminalColor.DEFAULT, attrs.background)
        assertEquals(TextStyle.PLAIN, attrs.style)
    }

    @Test
    fun `custom background color`() {
        val attrs = CellAttributes(background = TerminalColor.BLUE)
        assertEquals(TerminalColor.DEFAULT, attrs.foreground)
        assertEquals(TerminalColor.BLUE, attrs.background)
        assertEquals(TextStyle.PLAIN, attrs.style)
    }

    @Test
    fun `custom style`() {
        val style = TextStyle(bold = true, underline = true)
        val attrs = CellAttributes(style = style)
        assertEquals(TerminalColor.DEFAULT, attrs.foreground)
        assertEquals(TerminalColor.DEFAULT, attrs.background)
        assertEquals(style, attrs.style)
    }

    @Test
    fun `fully customized attributes`() {
        val style = TextStyle(bold = true, italic = true, underline = true)
        val attrs = CellAttributes(
            foreground = TerminalColor.BRIGHT_GREEN,
            background = TerminalColor.BLACK,
            style = style,
        )
        assertEquals(TerminalColor.BRIGHT_GREEN, attrs.foreground)
        assertEquals(TerminalColor.BLACK, attrs.background)
        assertEquals(style, attrs.style)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = CellAttributes(foreground = TerminalColor.RED, style = TextStyle(bold = true))
        val b = CellAttributes(foreground = TerminalColor.RED, style = TextStyle(bold = true))
        val c = CellAttributes(foreground = TerminalColor.BLUE, style = TextStyle(bold = true))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = CellAttributes(foreground = TerminalColor.RED)
        val copied = original.copy(background = TerminalColor.WHITE)
        assertEquals(TerminalColor.RED, copied.foreground)
        assertEquals(TerminalColor.WHITE, copied.background)
        assertEquals(TextStyle.PLAIN, copied.style)
    }
}