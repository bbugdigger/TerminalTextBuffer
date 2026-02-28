package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

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

    // --- Wide character: writeText ---

    @Test
    fun `writeText places wide char with main cell and continuation`() {
        val line = TerminalLine(10)
        // U+4E16 = 世 (CJK, width 2)
        line.writeText(0, "\u4E16", defaultAttrs)
        assertEquals('\u4E16', line.getCell(0).character)
        assertEquals(2, line.getCell(0).width)
        assertTrue(line.getCell(1).isContinuation)
        assertEquals(Cell.EMPTY, line.getCell(2))
    }

    @Test
    fun `writeText with wide char advances cursor by 2`() {
        val line = TerminalLine(10)
        val newCol = line.writeText(0, "\u4E16", defaultAttrs)
        assertEquals(2, newCol)
    }

    @Test
    fun `writeText with multiple wide chars`() {
        val line = TerminalLine(10)
        // 世界 = U+4E16 U+754C
        val newCol = line.writeText(0, "\u4E16\u754C", defaultAttrs)
        assertEquals(4, newCol)
        assertEquals('\u4E16', line.getCell(0).character)
        assertEquals(2, line.getCell(0).width)
        assertTrue(line.getCell(1).isContinuation)
        assertEquals('\u754C', line.getCell(2).character)
        assertEquals(2, line.getCell(2).width)
        assertTrue(line.getCell(3).isContinuation)
    }

    @Test
    fun `writeText with mixed narrow and wide chars`() {
        val line = TerminalLine(10)
        // A世B = narrow, wide, narrow
        val newCol = line.writeText(0, "A\u4E16B", defaultAttrs)
        assertEquals(4, newCol)
        assertEquals('A', line.getCell(0).character)
        assertEquals(1, line.getCell(0).width)
        assertEquals('\u4E16', line.getCell(1).character)
        assertEquals(2, line.getCell(1).width)
        assertTrue(line.getCell(2).isContinuation)
        assertEquals('B', line.getCell(3).character)
    }

    @Test
    fun `writeText wide char does not fit at end of line`() {
        val line = TerminalLine(5)
        // Write "ABC" (3 cols), then a wide char needs 2 cols, only 2 left -> fits
        val newCol = line.writeText(0, "ABC\u4E16", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals('\u4E16', line.getCell(3).character)
        assertTrue(line.getCell(4).isContinuation)
    }

    @Test
    fun `writeText wide char skipped when only 1 col remains`() {
        val line = TerminalLine(5)
        // Write "ABCD" (4 cols), then wide char needs 2, only 1 left -> skip
        val newCol = line.writeText(0, "ABCD\u4E16", defaultAttrs)
        assertEquals(4, newCol) // wide char didn't fit, stops at 4
        assertEquals('D', line.getCell(3).character)
        assertEquals(Cell.EMPTY, line.getCell(4))
    }

    @Test
    fun `writeText wide char at start offset`() {
        val line = TerminalLine(10)
        val newCol = line.writeText(3, "\u4E16", defaultAttrs)
        assertEquals(5, newCol)
        assertEquals(Cell.EMPTY, line.getCell(2))
        assertEquals('\u4E16', line.getCell(3).character)
        assertTrue(line.getCell(4).isContinuation)
    }

    @Test
    fun `writeText overwrites first half of existing wide char`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        // Now overwrite col 0 with a narrow char — should clean up continuation at col 1
        line.writeText(0, "A", redAttrs)
        assertEquals('A', line.getCell(0).character)
        assertEquals(Cell.EMPTY, line.getCell(1)) // continuation cleaned up
    }

    @Test
    fun `writeText overwrites second half of existing wide char`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        // Now overwrite col 1 (the continuation) — should clean up main cell at col 0
        line.writeText(1, "B", redAttrs)
        assertEquals(Cell.EMPTY, line.getCell(0)) // main cell cleaned up
        assertEquals('B', line.getCell(1).character)
    }

    @Test
    fun `writeText wide char overwriting another wide char`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        line.writeText(0, "\u754C", redAttrs)     // overwrite with another wide at 0-1
        assertEquals('\u754C', line.getCell(0).character)
        assertEquals(2, line.getCell(0).width)
        assertTrue(line.getCell(1).isContinuation)
    }

    @Test
    fun `getText skips continuation cells for wide chars`() {
        val line = TerminalLine(10)
        line.writeText(0, "A\u4E16B", defaultAttrs)
        // Should be "A世B" — continuation cell skipped
        assertEquals("A\u4E16B", line.getText())
    }

    @Test
    fun `getRawText skips continuation cells for wide chars`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs)
        val raw = line.getRawText()
        // Width 10, wrote 世 (2 cols), 8 remaining spaces. Continuation skipped.
        assertEquals(9, raw.length) // 1 wide char + 8 spaces
        assertEquals('\u4E16', raw[0])
    }

    // --- Wide character: insertText ---

    @Test
    fun `insertText with wide char shifts content right`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCD", defaultAttrs)
        line.insertText(1, "\u4E16", redAttrs) // insert wide at col 1
        assertEquals('A', line.getCell(0).character)
        assertEquals('\u4E16', line.getCell(1).character)
        assertEquals(2, line.getCell(1).width)
        assertTrue(line.getCell(2).isContinuation)
        assertEquals('B', line.getCell(3).character) // shifted right by 2
        assertEquals('C', line.getCell(4).character)
        assertEquals('D', line.getCell(5).character)
    }

    @Test
    fun `insertText wide char that does not fit`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCD", defaultAttrs)
        // At col 4, only 1 col left, wide char needs 2
        val newCol = line.insertText(4, "\u4E16", defaultAttrs)
        assertEquals(4, newCol) // didn't fit
        assertEquals('D', line.getCell(3).character)
    }

    @Test
    fun `insertText wide char truncates content at right boundary`() {
        val line = TerminalLine(6)
        line.writeText(0, "ABCDEF", defaultAttrs)
        // Insert wide char at col 0, shifts everything right by 2, E and F truncated
        line.insertText(0, "\u4E16", redAttrs)
        assertEquals('\u4E16', line.getCell(0).character)
        assertTrue(line.getCell(1).isContinuation)
        assertEquals('A', line.getCell(2).character)
        assertEquals('B', line.getCell(3).character)
        assertEquals('C', line.getCell(4).character)
        assertEquals('D', line.getCell(5).character)
    }

    // --- Wide character: setCell cleanup ---

    @Test
    fun `setCell on wide char main cell cleans up continuation`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        line.setCell(0, Cell(character = 'X'))
        assertEquals('X', line.getCell(0).character)
        assertEquals(1, line.getCell(0).width)
        assertEquals(Cell.EMPTY, line.getCell(1)) // continuation cleared
    }

    @Test
    fun `setCell on continuation cleans up main cell`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        line.setCell(1, Cell(character = 'Y'))
        assertEquals(Cell.EMPTY, line.getCell(0)) // main cell cleared
        assertEquals('Y', line.getCell(1).character)
    }

    // --- copyWithWidth ---

    @Test
    fun `copyWithWidth to larger width pads with empty cells`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABC", defaultAttrs)
        val wider = line.copyWithWidth(10)
        assertEquals(10, wider.width)
        assertEquals("ABC", wider.getText())
        assertEquals(Cell.EMPTY, wider.getCell(5))
    }

    @Test
    fun `copyWithWidth to smaller width truncates`() {
        val line = TerminalLine(10)
        line.writeText(0, "HelloWorld", defaultAttrs)
        val narrower = line.copyWithWidth(5)
        assertEquals(5, narrower.width)
        assertEquals("Hello", narrower.getText())
    }

    @Test
    fun `copyWithWidth same width is equivalent to copy`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", defaultAttrs)
        val same = line.copyWithWidth(5)
        assertEquals(line.getText(), same.getText())
    }

    @Test
    fun `copyWithWidth cleans up wide char split at boundary - main cell at edge`() {
        val line = TerminalLine(6)
        // Place wide char at cols 4-5
        line.writeText(4, "\u4E16", defaultAttrs)
        // Shrink to 5 — continuation at col 5 is lost, main cell at col 4 should be cleaned up
        val narrower = line.copyWithWidth(5)
        assertEquals(Cell.EMPTY, narrower.getCell(4))
    }

    @Test
    fun `copyWithWidth cleans up wide char split at boundary - continuation at edge`() {
        val line = TerminalLine(6)
        // Place wide char at cols 3-4
        line.writeText(3, "\u4E16", defaultAttrs)
        // Shrink to 4 — continuation at col 4 is cut off. But main cell at 3 is last cell.
        // Actually the continuation is at col 4, and copyCount = 4, so we copy cols 0-3.
        // Col 3 is the main cell (width=2). Its continuation at 4 is cut off -> main at 3 should be cleared.
        val narrower = line.copyWithWidth(4)
        assertEquals(Cell.EMPTY, narrower.getCell(3))
    }

    @Test
    fun `copyWithWidth preserves wide char that fits`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16", defaultAttrs) // wide at 0-1
        val wider = line.copyWithWidth(5)
        assertEquals('\u4E16', wider.getCell(0).character)
        assertEquals(2, wider.getCell(0).width)
        assertTrue(wider.getCell(1).isContinuation)
    }

    @Test
    fun `copyWithWidth is independent from original`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hello", defaultAttrs)
        val copy = line.copyWithWidth(8)
        line.setCell(0, Cell(character = 'X'))
        assertEquals('H', copy.getCell(0).character)
    }
}
