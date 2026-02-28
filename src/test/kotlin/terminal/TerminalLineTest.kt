package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class TerminalLineTest {

    private val defaultAttrs = CellAttributes.DEFAULT
    private val redAttrs = CellAttributes(foreground = TerminalColor.RED)
    private val boldAttrs = CellAttributes(style = TextStyle(bold = true))

    // --- Construction ---

    @Test
    fun `new line has all empty cells`() {
        val line = TerminalLine(10)
        for (col in 0 until 10) {
            assertEquals(Cell.EMPTY, line.getCell(col))
        }
    }

    @Test
    fun `width must be positive`() {
        assertFailsWith<IllegalArgumentException> { TerminalLine(0) }
        assertFailsWith<IllegalArgumentException> { TerminalLine(-1) }
    }

    @Test
    fun `width is stored correctly`() {
        assertEquals(80, TerminalLine(80).width)
    }

    // --- getCell / setCell ---

    @Test
    fun `setCell and getCell round-trip`() {
        val line = TerminalLine(10)
        val cell = Cell(character = 'X', attributes = redAttrs)
        line.setCell(3, cell)
        assertEquals(cell, line.getCell(3))
    }

    @Test
    fun `setCell does not affect other columns`() {
        val line = TerminalLine(5)
        line.setCell(2, Cell(character = 'A'))
        assertEquals(Cell.EMPTY, line.getCell(0))
        assertEquals(Cell.EMPTY, line.getCell(1))
        assertEquals(Cell(character = 'A'), line.getCell(2))
        assertEquals(Cell.EMPTY, line.getCell(3))
        assertEquals(Cell.EMPTY, line.getCell(4))
    }

    @Test
    fun `getCell throws on negative column`() {
        val line = TerminalLine(10)
        assertFailsWith<IndexOutOfBoundsException> { line.getCell(-1) }
    }

    @Test
    fun `getCell throws on column equal to width`() {
        val line = TerminalLine(10)
        assertFailsWith<IndexOutOfBoundsException> { line.getCell(10) }
    }

    @Test
    fun `setCell throws on out-of-bounds column`() {
        val line = TerminalLine(10)
        assertFailsWith<IndexOutOfBoundsException> { line.setCell(10, Cell.EMPTY) }
        assertFailsWith<IndexOutOfBoundsException> { line.setCell(-1, Cell.EMPTY) }
    }

    // --- writeText ---

    @Test
    fun `writeText writes characters with attributes`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hi", redAttrs)
        assertEquals(Cell(character = 'H', attributes = redAttrs), line.getCell(0))
        assertEquals(Cell(character = 'i', attributes = redAttrs), line.getCell(1))
        assertEquals(Cell.EMPTY, line.getCell(2))
    }

    @Test
    fun `writeText returns cursor position after last character`() {
        val line = TerminalLine(10)
        val newCol = line.writeText(3, "ABC", defaultAttrs)
        assertEquals(6, newCol)
    }

    @Test
    fun `writeText at the start of the line`() {
        val line = TerminalLine(5)
        val newCol = line.writeText(0, "Hello", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals("Hello", line.getText())
    }

    @Test
    fun `writeText overwrites existing content`() {
        val line = TerminalLine(10)
        line.writeText(0, "AAAA", defaultAttrs)
        line.writeText(1, "BB", redAttrs)
        assertEquals(Cell(character = 'A', attributes = defaultAttrs), line.getCell(0))
        assertEquals(Cell(character = 'B', attributes = redAttrs), line.getCell(1))
        assertEquals(Cell(character = 'B', attributes = redAttrs), line.getCell(2))
        assertEquals(Cell(character = 'A', attributes = defaultAttrs), line.getCell(3))
    }

    @Test
    fun `writeText truncates at line width`() {
        val line = TerminalLine(5)
        val newCol = line.writeText(3, "ABCDEF", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals(Cell(character = 'A', attributes = defaultAttrs), line.getCell(3))
        assertEquals(Cell(character = 'B', attributes = defaultAttrs), line.getCell(4))
    }

    @Test
    fun `writeText with empty string does nothing`() {
        val line = TerminalLine(10)
        val newCol = line.writeText(5, "", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals(Cell.EMPTY, line.getCell(5))
    }

    @Test
    fun `writeText starting at last column writes one character`() {
        val line = TerminalLine(5)
        val newCol = line.writeText(4, "XY", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals(Cell(character = 'X', attributes = defaultAttrs), line.getCell(4))
    }

    @Test
    fun `writeText starting at width returns same position`() {
        val line = TerminalLine(5)
        val newCol = line.writeText(5, "ABC", defaultAttrs)
        assertEquals(5, newCol)
    }

    // --- insertText ---

    @Test
    fun `insertText shifts existing content right`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCD", defaultAttrs)
        line.insertText(1, "XX", redAttrs)
        assertEquals(Cell(character = 'A', attributes = defaultAttrs), line.getCell(0))
        assertEquals(Cell(character = 'X', attributes = redAttrs), line.getCell(1))
        assertEquals(Cell(character = 'X', attributes = redAttrs), line.getCell(2))
        assertEquals(Cell(character = 'B', attributes = defaultAttrs), line.getCell(3))
        assertEquals(Cell(character = 'C', attributes = defaultAttrs), line.getCell(4))
        assertEquals(Cell(character = 'D', attributes = defaultAttrs), line.getCell(5))
    }

    @Test
    fun `insertText returns cursor position after inserted text`() {
        val line = TerminalLine(10)
        val newCol = line.insertText(2, "XYZ", defaultAttrs)
        assertEquals(5, newCol)
    }

    @Test
    fun `insertText truncates shifted content that overflows`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertText(2, "XX", redAttrs)
        // A, B shifted content starts after XX: C at col 4, D and E are gone
        assertEquals("ABXXC", line.getText())
    }

    @Test
    fun `insertText at start of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABC", defaultAttrs)
        line.insertText(0, "XY", redAttrs)
        assertEquals("XYABC", line.getRawText())
    }

    @Test
    fun `insertText at end of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "AB", defaultAttrs)
        line.insertText(5, "XY", redAttrs)
        // startCol is at width, nothing can be inserted
        assertEquals("AB", line.getText())
    }

    @Test
    fun `insertText with empty string does nothing`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCD", defaultAttrs)
        val newCol = line.insertText(2, "", defaultAttrs)
        assertEquals(2, newCol)
        assertEquals("ABCD", line.getText())
    }

    @Test
    fun `insertText that exactly fills remaining space`() {
        val line = TerminalLine(5)
        line.writeText(0, "AB", defaultAttrs)
        line.insertText(2, "CDE", redAttrs)
        assertEquals("ABCDE", line.getRawText())
    }

    // --- fill ---

    @Test
    fun `fill with character sets all cells`() {
        val line = TerminalLine(5)
        line.fill('-', redAttrs)
        for (col in 0 until 5) {
            assertEquals(Cell(character = '-', attributes = redAttrs), line.getCell(col))
        }
    }

    @Test
    fun `fill with null clears to EMPTY`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", redAttrs)
        line.fill(null)
        for (col in 0 until 5) {
            assertEquals(Cell.EMPTY, line.getCell(col))
        }
    }

    @Test
    fun `fill overwrites existing content`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.fill('X', boldAttrs)
        assertEquals("XXXXX", line.getText())
        assertEquals(boldAttrs, line.getCell(0).attributes)
    }

    // --- clear ---

    @Test
    fun `clear resets all cells to EMPTY`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", redAttrs)
        line.clear()
        for (col in 0 until 5) {
            assertEquals(Cell.EMPTY, line.getCell(col))
        }
    }

    // --- getText ---

    @Test
    fun `getText returns written text`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        assertEquals("Hello", line.getText())
    }

    @Test
    fun `getText trims trailing spaces`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hi", defaultAttrs)
        assertEquals("Hi", line.getText())
    }

    @Test
    fun `getText on empty line returns empty string`() {
        val line = TerminalLine(10)
        assertEquals("", line.getText())
    }

    @Test
    fun `getText preserves leading and internal spaces`() {
        val line = TerminalLine(10)
        line.writeText(0, " A B ", defaultAttrs)
        assertEquals(" A B", line.getText())
    }

    @Test
    fun `getText with text starting at offset`() {
        val line = TerminalLine(10)
        line.writeText(3, "XY", defaultAttrs)
        // Columns 0-2 are spaces, then X, Y
        assertEquals("   XY", line.getText())
    }

    // --- getRawText ---

    @Test
    fun `getRawText does not trim trailing spaces`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hi", defaultAttrs)
        assertEquals("Hi   ", line.getRawText())
    }

    @Test
    fun `getRawText on full line`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", defaultAttrs)
        assertEquals("Hello", line.getRawText())
    }

    // --- copy ---

    @Test
    fun `copy creates independent line`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", redAttrs)

        val copied = line.copy()
        assertNotSame(line, copied)
        assertEquals(line.width, copied.width)
        assertEquals(line.getText(), copied.getText())

        // Modifying the original does not affect the copy
        line.setCell(0, Cell(character = 'X'))
        assertEquals(Cell(character = 'H', attributes = redAttrs), copied.getCell(0))
    }

    @Test
    fun `copy preserves attributes`() {
        val line = TerminalLine(3)
        line.writeText(0, "A", redAttrs)
        line.writeText(1, "B", boldAttrs)

        val copied = line.copy()
        assertEquals(redAttrs, copied.getCell(0).attributes)
        assertEquals(boldAttrs, copied.getCell(1).attributes)
    }
}