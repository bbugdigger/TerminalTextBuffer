package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CellTest {

    @Test
    fun `EMPTY cell has space character and default attributes`() {
        val cell = Cell.EMPTY
        assertEquals(Cell.EMPTY_CHAR, cell.character)
        assertEquals(CellAttributes.DEFAULT, cell.attributes)
        assertEquals(1, cell.width)
    }

    @Test
    fun `EMPTY cell is reported as empty`() {
        assertTrue(Cell.EMPTY.isEmpty)
    }

    @Test
    fun `default constructor creates empty cell`() {
        assertEquals(Cell.EMPTY, Cell())
    }

    @Test
    fun `cell with non-space character is not empty`() {
        val cell = Cell(character = 'A')
        assertFalse(cell.isEmpty)
    }

    @Test
    fun `cell with non-default attributes is not empty`() {
        val cell = Cell(attributes = CellAttributes(foreground = TerminalColor.RED))
        assertFalse(cell.isEmpty)
    }

    @Test
    fun `CONTINUATION cell has width 0`() {
        assertEquals(0, Cell.CONTINUATION.width)
    }

    @Test
    fun `CONTINUATION cell is reported as continuation`() {
        assertTrue(Cell.CONTINUATION.isContinuation)
    }

    @Test
    fun `normal cell is not continuation`() {
        assertFalse(Cell.EMPTY.isContinuation)
        assertFalse(Cell(character = 'X').isContinuation)
    }

    @Test
    fun `wide character cell has width 2`() {
        val cell = Cell(character = '\u4e16', width = 2) // CJK character
        assertEquals(2, cell.width)
        assertFalse(cell.isContinuation)
    }

    @Test
    fun `cell stores character and attributes correctly`() {
        val attrs = CellAttributes(
            foreground = TerminalColor.GREEN,
            background = TerminalColor.BLACK,
            style = TextStyle(bold = true),
        )
        val cell = Cell(character = 'Z', attributes = attrs)
        assertEquals('Z', cell.character)
        assertEquals(attrs, cell.attributes)
        assertEquals(1, cell.width)
    }

    @Test
    fun `data class equality works correctly`() {
        val attrs = CellAttributes(foreground = TerminalColor.RED)
        val a = Cell(character = 'A', attributes = attrs)
        val b = Cell(character = 'A', attributes = attrs)
        val c = Cell(character = 'B', attributes = attrs)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `data class copy works correctly`() {
        val cell = Cell(character = 'A', attributes = CellAttributes(foreground = TerminalColor.RED))
        val copied = cell.copy(character = 'B')
        assertEquals('B', copied.character)
        assertEquals(TerminalColor.RED, copied.attributes.foreground)
    }

    // --- charWidth ---

    @Test
    fun `charWidth returns 1 for ASCII letter`() {
        assertEquals(1, Cell.charWidth('A'))
    }

    @Test
    fun `charWidth returns 1 for ASCII digit`() {
        assertEquals(1, Cell.charWidth('0'))
    }

    @Test
    fun `charWidth returns 1 for ASCII space`() {
        assertEquals(1, Cell.charWidth(' '))
    }

    @Test
    fun `charWidth returns 1 for ASCII punctuation`() {
        assertEquals(1, Cell.charWidth('!'))
        assertEquals(1, Cell.charWidth('.'))
    }

    @Test
    fun `charWidth returns 2 for CJK unified ideograph`() {
        // U+4E16 = 世 (world)
        assertEquals(2, Cell.charWidth('\u4E16'))
        // U+4E2D = 中 (middle)
        assertEquals(2, Cell.charWidth('\u4E2D'))
    }

    @Test
    fun `charWidth returns 2 for Hangul syllable`() {
        // U+AC00 = 가 (first Hangul syllable)
        assertEquals(2, Cell.charWidth('\uAC00'))
        // U+D7A3 = last Hangul syllable
        assertEquals(2, Cell.charWidth('\uD7A3'))
    }

    @Test
    fun `charWidth returns 2 for fullwidth Latin letter`() {
        // U+FF21 = Ａ (fullwidth A)
        assertEquals(2, Cell.charWidth('\uFF21'))
    }

    @Test
    fun `charWidth returns 2 for fullwidth digit`() {
        // U+FF10 = ０ (fullwidth 0)
        assertEquals(2, Cell.charWidth('\uFF10'))
    }

    @Test
    fun `charWidth returns 2 for Hiragana`() {
        // U+3042 = あ
        assertEquals(2, Cell.charWidth('\u3042'))
    }

    @Test
    fun `charWidth returns 2 for Katakana`() {
        // U+30A2 = ア
        assertEquals(2, Cell.charWidth('\u30A2'))
    }

    @Test
    fun `charWidth returns 2 for CJK compatibility ideograph`() {
        // U+F900
        assertEquals(2, Cell.charWidth('\uF900'))
    }

    @Test
    fun `charWidth returns 1 for regular non-ASCII Latin`() {
        // U+00E9 = é (Latin small letter e with acute)
        assertEquals(1, Cell.charWidth('\u00E9'))
    }

    @Test
    fun `charWidth returns 1 for Cyrillic character`() {
        // U+0410 = А (Cyrillic capital A)
        assertEquals(1, Cell.charWidth('\u0410'))
    }

    @Test
    fun `charWidth returns 1 for Arabic character`() {
        // U+0627 = ا (Arabic Alef)
        assertEquals(1, Cell.charWidth('\u0627'))
    }

    @Test
    fun `charWidth returns 2 for Hangul Jamo`() {
        // U+1100 = first Hangul Jamo
        assertEquals(2, Cell.charWidth('\u1100'))
    }

    @Test
    fun `charWidth returns 2 for fullwidth sign`() {
        // U+FFE0 = ￠ (fullwidth cent sign)
        assertEquals(2, Cell.charWidth('\uFFE0'))
    }
}