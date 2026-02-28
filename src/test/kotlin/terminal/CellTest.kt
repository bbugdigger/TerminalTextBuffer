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
}