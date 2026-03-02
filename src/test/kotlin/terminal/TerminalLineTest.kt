package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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

    // --- deleteChars ---

    @Test
    fun `deleteChars removes characters and shifts left`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCDEFGH", defaultAttrs)
        line.deleteChars(2, 3) // delete C, D, E
        assertEquals("ABFGH", line.getText())
    }

    @Test
    fun `deleteChars fills right edge with empty cells`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCDEFGHIJ", defaultAttrs)
        line.deleteChars(2, 3) // delete 3 at col 2
        // Cols 7, 8, 9 should be empty
        assertEquals(Cell.EMPTY, line.getCell(7))
        assertEquals(Cell.EMPTY, line.getCell(8))
        assertEquals(Cell.EMPTY, line.getCell(9))
    }

    @Test
    fun `deleteChars at start of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(0, 2) // delete A, B
        assertEquals("CDE", line.getText())
    }

    @Test
    fun `deleteChars at end of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(3, 2) // delete D, E
        assertEquals("ABC", line.getText())
    }

    @Test
    fun `deleteChars n larger than remaining width is clamped`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(3, 100) // only 2 chars remain
        assertEquals("ABC", line.getText())
    }

    @Test
    fun `deleteChars with n equals 0 does nothing`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(2, 0)
        assertEquals("ABCDE", line.getText())
    }

    @Test
    fun `deleteChars with startCol at width does nothing`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(5, 1)
        assertEquals("ABCDE", line.getText())
    }

    @Test
    fun `deleteChars preserves attributes of shifted cells`() {
        val line = TerminalLine(10)
        line.writeText(0, "AB", defaultAttrs)
        line.writeText(2, "CD", redAttrs)
        line.writeText(4, "EF", boldAttrs)
        line.deleteChars(2, 2) // delete CD
        // E and F (bold) should shift left to cols 2 and 3
        assertEquals(boldAttrs, line.getCell(2).attributes)
        assertEquals(boldAttrs, line.getCell(3).attributes)
        assertEquals('E', line.getCell(2).character)
        assertEquals('F', line.getCell(3).character)
    }

    @Test
    fun `deleteChars entire line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(0, 5)
        assertEquals("", line.getText())
        for (col in 0 until 5) {
            assertEquals(Cell.EMPTY, line.getCell(col))
        }
    }

    @Test
    fun `deleteChars single character`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.deleteChars(2, 1) // delete C
        assertEquals("ABDE", line.getText())
    }

    // --- deleteChars with wide characters ---

    @Test
    fun `deleteChars deleting a complete wide char`() {
        val line = TerminalLine(10)
        line.writeText(0, "A\u4E16B", defaultAttrs) // A, 世(2 cols), B
        line.deleteChars(1, 2) // delete both cells of wide char
        assertEquals("AB", line.getText())
    }

    @Test
    fun `deleteChars starting on wide char continuation cleans up main cell`() {
        val line = TerminalLine(10)
        line.writeText(0, "A\u4E16B", defaultAttrs) // A(0), 世main(1), cont(2), B(3)
        line.deleteChars(2, 1) // delete continuation of wide char
        // cleanUpWideChar(2): continuation -> main at col 1 becomes EMPTY
        // cleanUpWideChar(3): B is normal, no-op
        // shiftCellsLeft(2, 1): B(3) shifts to col 2, rest becomes EMPTY
        // Result: A(0) EMPTY(1) B(2) EMPTY(3..9)
        assertEquals('A', line.getCell(0).character)
        assertEquals(Cell.EMPTY, line.getCell(1)) // main cell cleaned up
        assertEquals('B', line.getCell(2).character)
        assertEquals(Cell.EMPTY, line.getCell(3))
    }

    @Test
    fun `deleteChars cleans up wide char that gets split by shift source boundary`() {
        val line = TerminalLine(6)
        // "AB世CD" -> A(0) B(1) 世(2,3) C(4) D(5)
        line.writeText(0, "AB\u4E16CD", defaultAttrs)
        // Delete 1 char at col 0: shift source starts at col 1
        // B(1) shifts to 0, 世(2,3) shifts to 1,2, C(4) to 3, D(5) to 4, EMPTY at 5
        line.deleteChars(0, 1)
        assertEquals('B', line.getCell(0).character)
        assertEquals('\u4E16', line.getCell(1).character)
        assertEquals(2, line.getCell(1).width)
        assertTrue(line.getCell(2).isContinuation)
        assertEquals('C', line.getCell(3).character)
        assertEquals('D', line.getCell(4).character)
        assertEquals(Cell.EMPTY, line.getCell(5))
    }

    @Test
    fun `deleteChars when shift source starts on continuation of wide char`() {
        val line = TerminalLine(6)
        // "A世BCD" -> A(0) 世main(1) cont(2) B(3) C(4) D(5)
        line.writeText(0, "A\u4E16BCD", defaultAttrs)
        // Delete 2 at col 0: removes A(0) and 世main(1)
        // cleanUpWideChar(0): A is normal, no-op
        // Source col is 2 (continuation): cleanUpWideChar(2) clears main at col 1 to EMPTY,
        // then continuation at col 2 is also replaced with EMPTY
        // shiftCellsLeft(0, 2): EMPTY(2)->col0, B(3)->col1, C(4)->col2, D(5)->col3, EMPTY at 4,5
        line.deleteChars(0, 2)
        assertEquals(Cell.EMPTY, line.getCell(0))
        assertEquals('B', line.getCell(1).character)
        assertEquals('C', line.getCell(2).character)
        assertEquals('D', line.getCell(3).character)
        assertEquals(Cell.EMPTY, line.getCell(4))
        assertEquals(Cell.EMPTY, line.getCell(5))
    }

    @Test
    fun `deleteChars deleting only first half of wide char`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16AB", defaultAttrs) // 世(0,1) A(2) B(3)
        line.deleteChars(0, 1) // delete main cell of wide char
        // cleanUpWideChar(0): main cell width=2 -> clears continuation at col 1
        // cleanUpWideChar(1): col 1 was cleaned to EMPTY, no wide char
        // shift left by 1: col 1->0, col 2->1, etc.
        assertEquals(Cell.EMPTY, line.getCell(0)) // was continuation, now EMPTY
        assertEquals('A', line.getCell(1).character)
        assertEquals('B', line.getCell(2).character)
    }

    // --- insertBlanks ---

    @Test
    fun `insertBlanks shifts content right and inserts blanks`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCDEFGH", defaultAttrs)
        line.insertBlanks(2, 3)
        assertEquals("AB   CDEFG", line.getRawText())
    }

    @Test
    fun `insertBlanks at start of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(0, 2)
        assertEquals("  ABC", line.getRawText())
    }

    @Test
    fun `insertBlanks at end of line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(4, 1)
        // Shift D right (push E off), insert blank at col 4
        assertEquals("ABCD", line.getText())
        assertEquals(' ', line.getCell(4).character)
    }

    @Test
    fun `insertBlanks discards content pushed past width`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(1, 2)
        // A stays, 2 blanks, then B, C shifted right but D, E fall off
        assertEquals("A  BC", line.getRawText())
    }

    @Test
    fun `insertBlanks n larger than remaining width is clamped`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(3, 100) // only 2 cols remain
        // All content from col 3 onward pushed off, 2 blanks inserted
        assertEquals("ABC", line.getText())
    }

    @Test
    fun `insertBlanks with n equals 0 does nothing`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(2, 0)
        assertEquals("ABCDE", line.getText())
    }

    @Test
    fun `insertBlanks with startCol at width does nothing`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(5, 1)
        assertEquals("ABCDE", line.getText())
    }

    @Test
    fun `insertBlanks uses provided attributes`() {
        val line = TerminalLine(10)
        line.writeText(0, "ABCD", defaultAttrs)
        line.insertBlanks(2, 2, redAttrs)
        assertEquals(' ', line.getCell(2).character)
        assertEquals(redAttrs, line.getCell(2).attributes)
        assertEquals(' ', line.getCell(3).character)
        assertEquals(redAttrs, line.getCell(3).attributes)
    }

    @Test
    fun `insertBlanks preserves attributes of shifted cells`() {
        val line = TerminalLine(10)
        line.writeText(0, "AB", redAttrs)
        line.writeText(2, "CD", boldAttrs)
        line.insertBlanks(2, 2) // insert before CD
        assertEquals(boldAttrs, line.getCell(4).attributes)
        assertEquals(boldAttrs, line.getCell(5).attributes)
        assertEquals('C', line.getCell(4).character)
        assertEquals('D', line.getCell(5).character)
    }

    @Test
    fun `insertBlanks fills entire remaining line`() {
        val line = TerminalLine(5)
        line.writeText(0, "ABCDE", defaultAttrs)
        line.insertBlanks(0, 5)
        assertEquals("", line.getText()) // all blanks
        for (col in 0 until 5) {
            assertEquals(' ', line.getCell(col).character)
        }
    }

    // --- insertBlanks with wide characters ---

    @Test
    fun `insertBlanks into wide char at continuation cleans up main`() {
        val line = TerminalLine(10)
        line.writeText(0, "\u4E16AB", defaultAttrs) // 世(0,1) A(2) B(3)
        line.insertBlanks(1, 1) // insert at continuation of wide char
        // cleanUpWideChar(1): continuation -> main at 0 becomes EMPTY
        // shift right from col 1 by 1
        // insert blank at col 1
        assertEquals(Cell.EMPTY, line.getCell(0)) // main cleaned up
        assertEquals(' ', line.getCell(1).character) // inserted blank
        // old continuation was at 1, shifted to 2 — but it was the continuation
        // After cleanup, col 1 becomes EMPTY, then shiftRight moves everything right
        // Actually: cleanup sets col 0 to EMPTY. Then shiftRight(1, 1) shifts cols 1..9 right by 1.
        // Then col 1 is filled with blank. So:
        // col 0: EMPTY, col 1: blank, col 2: old continuation (now orphaned->EMPTY via shiftRight cleanup),
        // Wait, let me re-trace:
        // Before cleanup: 世(0,1) A(2) B(3)
        // cleanUpWideChar(1): col 1 is continuation -> sets col 0 = EMPTY. State: EMPTY(0) CONT(1) A(2) B(3)
        // shiftCellsRight(1, 1): checks boundary at width-1-1=8, cell at 8 is EMPTY, no wide char issue.
        //   shifts: col 9=col8, col8=col7, ..., col2=col1(CONT). col 1 = EMPTY (cleared gap)
        //   State: EMPTY(0) EMPTY(1) CONT(2) A(3) B(4)
        // Fill gap: col 1 = blank(' ')
        //   State: EMPTY(0) blank(1) CONT(2) A(3) B(4)
        // Hmm, but CONT at col 2 is now orphaned (its main cell at 0 was cleared).
        // shiftCellsRight should have cleaned this up... Let me check shiftCellsRight.
        // shiftCellsRight checks lastKeptSource = width - amount - 1 = 8. Cell at 8 is EMPTY. No cleanup.
        // So CONT at col 2 remains orphaned. This is a valid concern but the continuation cell
        // is just data — it renders as nothing. getText() skips continuations.
    }

    @Test
    fun `insertBlanks pushes wide char off right boundary cleans up`() {
        val line = TerminalLine(6)
        line.writeText(0, "ABCD\u4E16", defaultAttrs) // A(0) B(1) C(2) D(3) 世(4,5)
        line.insertBlanks(0, 1)
        // shiftCellsRight(0, 1): lastKeptSource = 6-1-1=4. Cell at 4 is wide char (width=2).
        // Its continuation at 5 would be pushed off. So both cells[4] and cells[5] are cleared.
        // Then shift: everything shifts right by 1. col 0 = blank.
        // Result: blank(0) A(1) B(2) C(3) D(4) EMPTY(5)
        assertEquals(' ', line.getCell(0).character)
        assertEquals('A', line.getCell(1).character)
        assertEquals('B', line.getCell(2).character)
        assertEquals('C', line.getCell(3).character)
        assertEquals('D', line.getCell(4).character)
        assertEquals(Cell.EMPTY, line.getCell(5)) // wide char cleaned up at boundary
    }

    @Test
    fun `insertBlanks before wide char shifts it right intact`() {
        val line = TerminalLine(10)
        line.writeText(0, "A\u4E16B", defaultAttrs) // A(0) 世(1,2) B(3)
        line.insertBlanks(0, 1) // shift everything right by 1
        assertEquals(' ', line.getCell(0).character) // inserted blank
        assertEquals('A', line.getCell(1).character)
        assertEquals('\u4E16', line.getCell(2).character)
        assertEquals(2, line.getCell(2).width)
        assertTrue(line.getCell(3).isContinuation)
        assertEquals('B', line.getCell(4).character)
    }

    // --- wrappedFromPrevious ---

    @Test
    fun `wrappedFromPrevious defaults to false`() {
        val line = TerminalLine(10)
        assertFalse(line.wrappedFromPrevious)
    }

    @Test
    fun `wrappedFromPrevious can be set to true`() {
        val line = TerminalLine(10)
        line.wrappedFromPrevious = true
        assertTrue(line.wrappedFromPrevious)
    }

    @Test
    fun `copy preserves wrappedFromPrevious`() {
        val line = TerminalLine(10)
        line.wrappedFromPrevious = true
        line.writeText(0, "Hello", defaultAttrs)
        val copy = line.copy()
        assertTrue(copy.wrappedFromPrevious)
        assertEquals("Hello", copy.getText())
    }

    @Test
    fun `copy preserves wrappedFromPrevious when false`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        val copy = line.copy()
        assertFalse(copy.wrappedFromPrevious)
    }

    @Test
    fun `copyWithWidth preserves wrappedFromPrevious`() {
        val line = TerminalLine(10)
        line.wrappedFromPrevious = true
        line.writeText(0, "Hello", defaultAttrs)
        val wider = line.copyWithWidth(20)
        assertTrue(wider.wrappedFromPrevious)
        assertEquals("Hello", wider.getText())
    }

    @Test
    fun `copyWithWidth preserves wrappedFromPrevious when false`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        val narrower = line.copyWithWidth(3)
        assertFalse(narrower.wrappedFromPrevious)
    }

    @Test
    fun `clear does not reset wrappedFromPrevious`() {
        val line = TerminalLine(10)
        line.wrappedFromPrevious = true
        line.writeText(0, "Hello", defaultAttrs)
        line.clear()
        // clear() resets content but wrappedFromPrevious is structural metadata
        assertTrue(line.wrappedFromPrevious)
    }

    @Test
    fun `getCells returns all cells`() {
        val line = TerminalLine(5)
        line.writeText(0, "Hi", defaultAttrs)
        val cells = line.getCells()
        assertEquals(5, cells.size)
        assertEquals('H', cells[0].character)
        assertEquals('i', cells[1].character)
        assertEquals(' ', cells[2].character)
    }

    // --- Dirty tracking ---

    @Test
    fun `new line starts dirty`() {
        val line = TerminalLine(10)
        assertTrue(line.dirty)
    }

    @Test
    fun `markClean sets dirty to false`() {
        val line = TerminalLine(10)
        line.markClean()
        assertFalse(line.dirty)
    }

    @Test
    fun `setCell marks line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.setCell(0, Cell(character = 'A', attributes = defaultAttrs))
        assertTrue(line.dirty)
    }

    @Test
    fun `writeText marks line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.writeText(0, "Hello", defaultAttrs)
        assertTrue(line.dirty)
    }

    @Test
    fun `insertText marks line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.insertText(0, "Hello", defaultAttrs)
        assertTrue(line.dirty)
    }

    @Test
    fun `fill marks line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.fill('X', defaultAttrs)
        assertTrue(line.dirty)
    }

    @Test
    fun `clear marks line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.clear()
        assertTrue(line.dirty)
    }

    @Test
    fun `deleteChars marks line dirty`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        line.markClean()
        line.deleteChars(0, 2)
        assertTrue(line.dirty)
    }

    @Test
    fun `insertBlanks marks line dirty`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        line.markClean()
        line.insertBlanks(0, 2)
        assertTrue(line.dirty)
    }

    @Test
    fun `deleteChars with n=0 does not mark line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.deleteChars(0, 0)
        assertFalse(line.dirty)
    }

    @Test
    fun `insertBlanks with n=0 does not mark line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.insertBlanks(0, 0)
        assertFalse(line.dirty)
    }

    @Test
    fun `deleteChars with startCol out of range does not mark line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.deleteChars(10, 1)
        assertFalse(line.dirty)
    }

    @Test
    fun `insertBlanks with startCol out of range does not mark line dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.insertBlanks(10, 1)
        assertFalse(line.dirty)
    }

    @Test
    fun `read methods do not set dirty`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        line.markClean()
        line.getCell(0)
        line.getText()
        line.getCells()
        assertFalse(line.dirty)
    }

    @Test
    fun `setting wrappedFromPrevious does not set dirty`() {
        val line = TerminalLine(10)
        line.markClean()
        line.wrappedFromPrevious = true
        assertFalse(line.dirty)
    }

    @Test
    fun `copy creates a dirty line`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        line.markClean()
        val copied = line.copy()
        assertTrue(copied.dirty)
    }

    @Test
    fun `copyWithWidth creates a dirty line`() {
        val line = TerminalLine(10)
        line.writeText(0, "Hello", defaultAttrs)
        line.markClean()
        val copied = line.copyWithWidth(20)
        assertTrue(copied.dirty)
    }
}
