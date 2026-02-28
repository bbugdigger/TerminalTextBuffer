package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalBufferTest {

    // --- Construction ---

    @Test
    fun `constructor stores dimensions correctly`() {
        val buf = TerminalBuffer(80, 24)
        assertEquals(80, buf.width)
        assertEquals(24, buf.height)
    }

    @Test
    fun `constructor with custom scrollback size`() {
        val buf = TerminalBuffer(80, 24, maxScrollbackSize = 500)
        assertEquals(500, buf.maxScrollbackSize)
    }

    @Test
    fun `constructor rejects zero width`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(0, 24) }
    }

    @Test
    fun `constructor rejects negative height`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(80, -1) }
    }

    @Test
    fun `constructor rejects negative scrollback size`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(80, 24, maxScrollbackSize = -1) }
    }

    @Test
    fun `new buffer has empty screen`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals("\n\n", buf.getScreenContent())
    }

    @Test
    fun `new buffer has no scrollback`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(0, buf.scrollbackSize)
    }

    // --- Current attributes ---

    @Test
    fun `default current attributes are DEFAULT`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(CellAttributes.DEFAULT, buf.currentAttributes)
    }

    @Test
    fun `setCurrentAttributes updates current attributes`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.RED, style = TextStyle(bold = true))
        buf.setCurrentAttributes(attrs)
        assertEquals(attrs, buf.currentAttributes)
    }

    @Test
    fun `current attributes are used by writeText`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.GREEN)
        buf.setCurrentAttributes(attrs)
        buf.writeText("A")
        assertEquals(attrs, buf.getAttributesAt(0, buf.scrollbackSize))
    }

    // --- Cursor initial state ---

    @Test
    fun `cursor starts at 0 0`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    // --- setCursorPosition ---

    @Test
    fun `setCursorPosition sets valid position`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(5, 3)
        assertEquals(5, buf.cursorCol)
        assertEquals(3, buf.cursorRow)
    }

    @Test
    fun `setCursorPosition clamps negative column to 0`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(-5, 2)
        assertEquals(0, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
    }

    @Test
    fun `setCursorPosition clamps column beyond width`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(100, 2)
        assertEquals(9, buf.cursorCol)
    }

    @Test
    fun `setCursorPosition clamps negative row to 0`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(3, -10)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `setCursorPosition clamps row beyond height`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(3, 100)
        assertEquals(4, buf.cursorRow)
    }

    @Test
    fun `setCursorPosition to last valid position`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(9, 4)
        assertEquals(9, buf.cursorCol)
        assertEquals(4, buf.cursorRow)
    }

    // --- Cursor movement ---

    @Test
    fun `moveCursorRight moves within bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.moveCursorRight(3)
        assertEquals(3, buf.cursorCol)
    }

    @Test
    fun `moveCursorRight clamps at right edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.moveCursorRight(100)
        assertEquals(9, buf.cursorCol)
    }

    @Test
    fun `moveCursorLeft moves within bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(5, 0)
        buf.moveCursorLeft(3)
        assertEquals(2, buf.cursorCol)
    }

    @Test
    fun `moveCursorLeft clamps at left edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(3, 0)
        buf.moveCursorLeft(10)
        assertEquals(0, buf.cursorCol)
    }

    @Test
    fun `moveCursorDown moves within bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.moveCursorDown(3)
        assertEquals(3, buf.cursorRow)
    }

    @Test
    fun `moveCursorDown clamps at bottom edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.moveCursorDown(100)
        assertEquals(4, buf.cursorRow)
    }

    @Test
    fun `moveCursorUp moves within bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(0, 4)
        buf.moveCursorUp(2)
        assertEquals(2, buf.cursorRow)
    }

    @Test
    fun `moveCursorUp clamps at top edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(0, 2)
        buf.moveCursorUp(10)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `moveCursorRight by 0 does nothing`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(3, 2)
        buf.moveCursorRight(0)
        assertEquals(3, buf.cursorCol)
    }

    @Test
    fun `moveCursor rejects negative amounts`() {
        val buf = TerminalBuffer(10, 5)
        assertFailsWith<IllegalArgumentException> { buf.moveCursorUp(-1) }
        assertFailsWith<IllegalArgumentException> { buf.moveCursorDown(-1) }
        assertFailsWith<IllegalArgumentException> { buf.moveCursorLeft(-1) }
        assertFailsWith<IllegalArgumentException> { buf.moveCursorRight(-1) }
    }

    @Test
    fun `cursor movement does not affect column when moving vertically`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(5, 0)
        buf.moveCursorDown(3)
        assertEquals(5, buf.cursorCol)
        assertEquals(3, buf.cursorRow)
    }

    @Test
    fun `cursor movement does not affect row when moving horizontally`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(0, 3)
        buf.moveCursorRight(5)
        assertEquals(5, buf.cursorCol)
        assertEquals(3, buf.cursorRow)
    }

    // --- writeText ---

    @Test
    fun `writeText writes characters at cursor position`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hello")
        assertEquals("Hello", buf.getScreenLine(0))
    }

    @Test
    fun `writeText advances cursor`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hi")
        assertEquals(2, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `writeText overwrites existing content`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("AAAA")
        buf.setCursorPosition(1, 0)
        buf.writeText("BB")
        assertEquals("ABBA", buf.getScreenLine(0))
    }

    @Test
    fun `writeText wraps to next line at end of width`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("HelloWorld")
        assertEquals("Hello", buf.getScreenLine(0))
        assertEquals("World", buf.getScreenLine(1))
    }

    @Test
    fun `writeText wraps and advances cursor correctly`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("HelloW")
        assertEquals(1, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `writeText scrolls when wrapping past bottom of screen`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("HelloWorldExtra")
        // "Hello" scrolled to scrollback, "World" scrolled to scrollback,
        // "Extra" is on the screen
        assertEquals(1, buf.scrollbackSize) // only 1 because maxScrollback is 1000 but only 1 full scroll happened... let me think
        // Screen was 2 lines. We wrote 15 chars across 3 lines (5 each).
        // Line 0: "Hello" — scrolled out when "World" wrapped to line 1 which was bottom, so "Hello" goes to scrollback
        // Line 1: "World" — then "Extra" wraps, we're at bottom again, "World" scrolls out
        // Actually: initially screen rows 0,1. Write "Hello" fills row 0. Cursor at col 5.
        // Next char 'W': col >= width, wrap: col=0, advanceCursorRow. cursorRow was 0, goes to 1.
        // Write "World" on row 1. Cursor at col 5.
        // Next char 'E': col >= width, wrap: col=0, advanceCursorRow. cursorRow is 1 == height-1, so scroll.
        // Top line ("Hello") goes to scrollback. New empty line added. cursorRow stays at 1.
        // Write "Extra" on row 1. Cursor at col 5.
        assertEquals(1, buf.scrollbackSize)
        assertEquals("Hello", buf.getLine(0)) // scrollback
        assertEquals("World", buf.getScreenLine(0))
        assertEquals("Extra", buf.getScreenLine(1))
    }

    @Test
    fun `writeText with empty string does nothing`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("")
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
        assertEquals("", buf.getScreenLine(0))
    }

    @Test
    fun `writeText from middle of line`() {
        val buf = TerminalBuffer(10, 3)
        buf.setCursorPosition(3, 0)
        buf.writeText("XY")
        assertEquals("   XY", buf.getScreenLine(0))
        assertEquals(5, buf.cursorCol)
    }

    @Test
    fun `writeText uses current attributes`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.RED)
        buf.setCurrentAttributes(attrs)
        buf.writeText("AB")
        val row = buf.scrollbackSize
        assertEquals(attrs, buf.getAttributesAt(0, row))
        assertEquals(attrs, buf.getAttributesAt(1, row))
        assertEquals(CellAttributes.DEFAULT, buf.getAttributesAt(2, row))
    }

    @Test
    fun `writeText exactly fills line without wrapping`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        assertEquals("Hello", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
        assertEquals(5, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `writeText on multiple lines with setCursorPosition`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Line1")
        buf.setCursorPosition(0, 1)
        buf.writeText("Line2")
        buf.setCursorPosition(0, 2)
        buf.writeText("Line3")
        assertEquals("Line1\nLine2\nLine3", buf.getScreenContent())
    }

    // --- insertText ---

    @Test
    fun `insertText inserts and shifts content right`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("ABCD")
        buf.setCursorPosition(1, 0)
        buf.insertText("XX")
        assertEquals("AXXBCD", buf.getScreenLine(0))
    }

    @Test
    fun `insertText advances cursor`() {
        val buf = TerminalBuffer(10, 3)
        buf.insertText("Hi")
        assertEquals(2, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `insertText truncates overflow on the line`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("ABCDE")
        buf.setCursorPosition(2, 0)
        buf.insertText("XX")
        assertEquals("ABXXC", buf.getScreenLine(0))
    }

    @Test
    fun `insertText wraps to next line`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(4, 0)
        buf.insertText("XY")
        // 'X' inserted at col 4 of row 0, cursorCol becomes 5.
        // 'Y': cursorCol(5) >= width(5), wrap: col=0, advanceCursorRow -> row 1.
        // 'Y' inserted at col 0 of row 1, cursorCol becomes 1.
        assertEquals("X", buf.getScreenLine(0).trimEnd().takeLast(1))
        assertEquals(1, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
        assertEquals("Y", buf.getScreenLine(1).trimEnd())
    }

    @Test
    fun `insertText uses current attributes`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.BLUE)
        buf.setCurrentAttributes(attrs)
        buf.insertText("A")
        assertEquals(attrs, buf.getAttributesAt(0, buf.scrollbackSize))
    }

    // --- fillLine ---

    @Test
    fun `fillLine fills current line with character`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(0, 1)
        val attrs = CellAttributes(foreground = TerminalColor.RED)
        buf.setCurrentAttributes(attrs)
        buf.fillLine('-')
        assertEquals("-----", buf.getScreenLine(1))
        assertEquals(attrs, buf.getAttributesAt(0, buf.scrollbackSize + 1))
    }

    @Test
    fun `fillLine with null clears line`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("Hello")
        buf.setCursorPosition(0, 0)
        buf.fillLine(null)
        assertEquals("", buf.getScreenLine(0))
    }

    @Test
    fun `fillLine does not move cursor`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(3, 1)
        buf.fillLine('X')
        assertEquals(3, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `fillLine only affects current line`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("AAAAA")
        buf.setCursorPosition(0, 1)
        buf.writeText("BBBBB")
        buf.setCursorPosition(0, 1)
        buf.fillLine('X')
        assertEquals("AAAAA", buf.getScreenLine(0))
        assertEquals("XXXXX", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
    }

    // --- insertEmptyLineAtBottom ---

    @Test
    fun `insertEmptyLineAtBottom scrolls top line to scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.setCursorPosition(0, 1)
        buf.writeText("World")
        buf.insertEmptyLineAtBottom()
        assertEquals(1, buf.scrollbackSize)
        assertEquals("Hello", buf.getLine(0))
        assertEquals("World", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
    }

    @Test
    fun `insertEmptyLineAtBottom does not move cursor`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(2, 1)
        buf.insertEmptyLineAtBottom()
        assertEquals(2, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `insertEmptyLineAtBottom respects max scrollback size`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 2)
        // Fill 4 lines that will scroll through
        buf.writeText("AAA")
        buf.setCursorPosition(0, 1)
        buf.writeText("BBB")
        buf.insertEmptyLineAtBottom() // "AAA" to scrollback
        buf.setCursorPosition(0, 1)
        buf.writeText("CCC")
        buf.insertEmptyLineAtBottom() // "BBB" to scrollback
        buf.setCursorPosition(0, 1)
        buf.writeText("DDD")
        buf.insertEmptyLineAtBottom() // "CCC" to scrollback, "AAA" dropped (max 2)
        assertEquals(2, buf.scrollbackSize)
        assertEquals("BBB", buf.getLine(0))
        assertEquals("CCC", buf.getLine(1))
    }

    @Test
    fun `insertEmptyLineAtBottom with zero scrollback discards top line`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 0)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        assertEquals(0, buf.scrollbackSize)
    }

    // --- clearScreen ---

    @Test
    fun `clearScreen resets all screen lines to empty`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("Hello")
        buf.setCursorPosition(0, 1)
        buf.writeText("World")
        buf.clearScreen()
        assertEquals("\n\n", buf.getScreenContent())
    }

    @Test
    fun `clearScreen resets cursor to 0 0`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(5, 3)
        buf.clearScreen()
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `clearScreen does not affect scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom() // "Hello" to scrollback
        buf.setCursorPosition(0, 0)
        buf.writeText("World")
        buf.clearScreen()
        assertEquals(1, buf.scrollbackSize)
        assertEquals("Hello", buf.getLine(0))
    }

    // --- clearAll ---

    @Test
    fun `clearAll resets screen and scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("World")
        buf.clearAll()
        assertEquals(0, buf.scrollbackSize)
        assertEquals("\n", buf.getScreenContent())
    }

    @Test
    fun `clearAll resets cursor to 0 0`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(7, 3)
        buf.clearAll()
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    // --- Content access: getCharAt ---

    @Test
    fun `getCharAt returns character from screen`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hi")
        val screenStart = buf.scrollbackSize
        assertEquals('H', buf.getCharAt(0, screenStart))
        assertEquals('i', buf.getCharAt(1, screenStart))
    }

    @Test
    fun `getCharAt returns space for empty cell`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(' ', buf.getCharAt(0, buf.scrollbackSize))
    }

    @Test
    fun `getCharAt returns character from scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        assertEquals('H', buf.getCharAt(0, 0))
        assertEquals('o', buf.getCharAt(4, 0))
    }

    @Test
    fun `getCharAt throws on out of bounds row`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IndexOutOfBoundsException> { buf.getCharAt(0, -1) }
        assertFailsWith<IndexOutOfBoundsException> { buf.getCharAt(0, 3) }
    }

    @Test
    fun `getCharAt throws on out of bounds column`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IndexOutOfBoundsException> { buf.getCharAt(-1, 0) }
        assertFailsWith<IndexOutOfBoundsException> { buf.getCharAt(10, 0) }
    }

    // --- Content access: getAttributesAt ---

    @Test
    fun `getAttributesAt returns attributes from screen`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.RED, style = TextStyle(bold = true))
        buf.setCurrentAttributes(attrs)
        buf.writeText("A")
        assertEquals(attrs, buf.getAttributesAt(0, buf.scrollbackSize))
    }

    @Test
    fun `getAttributesAt returns DEFAULT for empty cell`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(CellAttributes.DEFAULT, buf.getAttributesAt(0, buf.scrollbackSize))
    }

    @Test
    fun `getAttributesAt returns attributes from scrollback`() {
        val buf = TerminalBuffer(5, 2)
        val attrs = CellAttributes(foreground = TerminalColor.GREEN)
        buf.setCurrentAttributes(attrs)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        assertEquals(attrs, buf.getAttributesAt(0, 0))
    }

    // --- Content access: getLine ---

    @Test
    fun `getLine returns screen line by absolute row`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hello")
        assertEquals("Hello", buf.getLine(buf.scrollbackSize))
    }

    @Test
    fun `getLine returns scrollback line by absolute row`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("First")
        buf.insertEmptyLineAtBottom()
        assertEquals("First", buf.getLine(0))
    }

    @Test
    fun `getLine returns empty string for empty line`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals("", buf.getLine(buf.scrollbackSize))
    }

    @Test
    fun `getLine throws on out of bounds`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IndexOutOfBoundsException> { buf.getLine(-1) }
        assertFailsWith<IndexOutOfBoundsException> { buf.getLine(3) }
    }

    // --- Content access: getScreenLine ---

    @Test
    fun `getScreenLine returns line by screen-relative row`() {
        val buf = TerminalBuffer(10, 3)
        buf.setCursorPosition(0, 1)
        buf.writeText("Middle")
        assertEquals("", buf.getScreenLine(0))
        assertEquals("Middle", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
    }

    @Test
    fun `getScreenLine throws on out of bounds`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IndexOutOfBoundsException> { buf.getScreenLine(-1) }
        assertFailsWith<IndexOutOfBoundsException> { buf.getScreenLine(3) }
    }

    // --- Content access: getScreenContent ---

    @Test
    fun `getScreenContent returns all screen lines joined`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Line0")
        buf.setCursorPosition(0, 1)
        buf.writeText("Line1")
        buf.setCursorPosition(0, 2)
        buf.writeText("Line2")
        assertEquals("Line0\nLine1\nLine2", buf.getScreenContent())
    }

    @Test
    fun `getScreenContent on empty buffer`() {
        val buf = TerminalBuffer(5, 3)
        assertEquals("\n\n", buf.getScreenContent())
    }

    // --- Content access: getFullContent ---

    @Test
    fun `getFullContent returns scrollback plus screen`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("AAAAA")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("BBBBB")
        buf.setCursorPosition(0, 1)
        buf.writeText("CCCCC")
        assertEquals("AAAAA\nBBBBB\nCCCCC", buf.getFullContent())
    }

    @Test
    fun `getFullContent with no scrollback equals screen content`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hello")
        assertEquals(buf.getScreenContent(), buf.getFullContent())
    }

    @Test
    fun `getFullContent with multiple scrollback lines`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("One")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("Two")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("Three")
        assertEquals("One\nTwo\nThree\n", buf.getFullContent())
    }

    // --- totalLineCount ---

    @Test
    fun `totalLineCount with no scrollback`() {
        val buf = TerminalBuffer(10, 3)
        assertEquals(3, buf.totalLineCount)
    }

    @Test
    fun `totalLineCount with scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        assertEquals(3, buf.totalLineCount) // 1 scrollback + 2 screen
    }

    // --- Scrollback behavior ---

    @Test
    fun `scrollback preserves lines in order`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 10)
        buf.writeText("First")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("Secnd")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("Third")
        buf.insertEmptyLineAtBottom()
        assertEquals(3, buf.scrollbackSize)
        assertEquals("First", buf.getLine(0))
        assertEquals("Secnd", buf.getLine(1))
        assertEquals("Third", buf.getLine(2))
    }

    @Test
    fun `scrollback drops oldest lines when exceeding max size`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 2)
        buf.writeText("AAA")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("BBB")
        buf.insertEmptyLineAtBottom()
        buf.setCursorPosition(0, 0)
        buf.writeText("CCC")
        buf.insertEmptyLineAtBottom()
        // "AAA" should have been dropped, only "BBB" and "CCC" remain
        assertEquals(2, buf.scrollbackSize)
        assertEquals("BBB", buf.getLine(0))
        assertEquals("CCC", buf.getLine(1))
    }

    @Test
    fun `writeText scrolling respects max scrollback size`() {
        val buf = TerminalBuffer(3, 2, maxScrollbackSize = 1)
        // Write enough to cause multiple scrolls
        buf.writeText("AAABBBCCC")
        // 3 lines of 3 chars: "AAA", "BBB", "CCC"
        // Only 1 scroll happens: when wrapping from row 1 to a new row,
        // "AAA" (top line) goes to scrollback, screen becomes ["BBB", new empty],
        // then "CCC" is written on the new bottom line.
        assertEquals(1, buf.scrollbackSize)
        assertEquals("AAA", buf.getLine(0))
        assertEquals("BBB", buf.getScreenLine(0))
        assertEquals("CCC", buf.getScreenLine(1))
    }

    @Test
    fun `scrollback lines are read-only via content access`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.insertEmptyLineAtBottom()
        // Writing to screen should not affect scrollback
        buf.setCursorPosition(0, 0)
        buf.writeText("World")
        assertEquals("Hello", buf.getLine(0))
        assertEquals("World", buf.getScreenLine(0))
    }

    // --- Integration / complex scenarios ---

    @Test
    fun `write then clear then write again`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hello")
        buf.clearScreen()
        buf.writeText("World")
        assertEquals("World", buf.getScreenLine(0))
    }

    @Test
    fun `alternating attributes on same line`() {
        val buf = TerminalBuffer(10, 3)
        val red = CellAttributes(foreground = TerminalColor.RED)
        val blue = CellAttributes(foreground = TerminalColor.BLUE)
        buf.setCurrentAttributes(red)
        buf.writeText("RR")
        buf.setCurrentAttributes(blue)
        buf.writeText("BB")
        val row = buf.scrollbackSize
        assertEquals(red, buf.getAttributesAt(0, row))
        assertEquals(red, buf.getAttributesAt(1, row))
        assertEquals(blue, buf.getAttributesAt(2, row))
        assertEquals(blue, buf.getAttributesAt(3, row))
        assertEquals("RRBB", buf.getScreenLine(0))
    }

    @Test
    fun `cursor does not move during fillLine`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(3, 2)
        buf.fillLine('X')
        assertEquals(3, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
    }

    @Test
    fun `multiple wraps during single writeText`() {
        val buf = TerminalBuffer(3, 3)
        buf.writeText("ABCDEFGHI")
        assertEquals("ABC", buf.getScreenLine(0))
        assertEquals("DEF", buf.getScreenLine(1))
        assertEquals("GHI", buf.getScreenLine(2))
        assertEquals(3, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
    }

    @Test
    fun `multiple wraps with scrolling during single writeText`() {
        val buf = TerminalBuffer(3, 2, maxScrollbackSize = 10)
        buf.writeText("ABCDEFGHI")
        // 3 lines of 3: ABC, DEF, GHI
        // Screen is 2 lines, so ABC scrolls to scrollback
        assertEquals(1, buf.scrollbackSize)
        assertEquals("ABC", buf.getLine(0))
        assertEquals("DEF", buf.getScreenLine(0))
        assertEquals("GHI", buf.getScreenLine(1))
    }

    @Test
    fun `1x1 buffer works`() {
        val buf = TerminalBuffer(1, 1)
        buf.writeText("A")
        assertEquals("A", buf.getScreenLine(0))
        assertEquals(1, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
        // Writing another char should scroll
        buf.writeText("B")
        assertEquals(1, buf.scrollbackSize)
        assertEquals("A", buf.getLine(0))
        assertEquals("B", buf.getScreenLine(0))
    }

    @Test
    fun `clearAll after scrollback accumulation`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 100)
        for (i in 0 until 10) {
            buf.setCursorPosition(0, 0)
            buf.writeText("L$i")
            buf.insertEmptyLineAtBottom()
        }
        val savedScrollback = buf.scrollbackSize
        assert(savedScrollback > 0)
        buf.clearAll()
        assertEquals(0, buf.scrollbackSize)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    // ===== Wide character tests =====

    @Test
    fun `writeText with wide char places main and continuation cells`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("\u4E16") // 世
        val row = buf.scrollbackSize
        assertEquals('\u4E16', buf.getCharAt(0, row))
        assertEquals(2, buf.cursorCol)
    }

    @Test
    fun `writeText with wide char advances cursor by 2`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("\u4E16")
        assertEquals(2, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `writeText mixed narrow and wide chars`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("A\u4E16B")
        assertEquals("A\u4E16B", buf.getScreenLine(0))
        assertEquals(4, buf.cursorCol)
    }

    @Test
    fun `writeText wide char wraps when only 1 col remaining`() {
        val buf = TerminalBuffer(5, 3)
        // Write 4 narrow chars (fills cols 0-3), then wide char needs 2 cols
        // Only 1 col left (col 4), so wide char wraps to next line
        buf.writeText("ABCD\u4E16")
        assertEquals("ABCD", buf.getScreenLine(0))
        assertEquals("\u4E16", buf.getScreenLine(1))
        assertEquals(2, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `writeText wide char at exactly end of line fits`() {
        val buf = TerminalBuffer(4, 3)
        // 2 narrow + 1 wide = 4 cols, fits exactly
        buf.writeText("AB\u4E16")
        assertEquals("AB\u4E16", buf.getScreenLine(0))
        assertEquals(4, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `writeText wide char wraps and scrolls at bottom`() {
        val buf = TerminalBuffer(4, 2)
        // Fill both rows: "ABCD" on row 0, "EF世" on row 1
        buf.writeText("ABCD")
        buf.writeText("EF")
        // cursor at col 2, row 1. Write wide char: 2 cols left, fits on row 1
        buf.writeText("\u4E16")
        assertEquals("ABCD", buf.getScreenLine(0))
        assertEquals("EF\u4E16", buf.getScreenLine(1))

        // Now write another wide char: cursor at col 4, wraps -> col 0, row needs advance
        // We're at bottom (row 1), so scrolls. "ABCD" goes to scrollback.
        buf.writeText("\u4E16")
        assertEquals(1, buf.scrollbackSize)
        assertEquals("ABCD", buf.getLine(0))
        assertEquals("EF\u4E16", buf.getScreenLine(0))
        assertEquals("\u4E16", buf.getScreenLine(1))
    }

    @Test
    fun `writeText multiple wide chars filling screen`() {
        val buf = TerminalBuffer(4, 2)
        // Each wide char takes 2 cols, so 2 per row, 4 total fills the screen
        buf.writeText("\u4E16\u754C\u4F60\u597D") // 世界你好
        // Row 0: 世界 (4 cols), Row 1: 你好 (4 cols)
        assertEquals("\u4E16\u754C", buf.getScreenLine(0))
        assertEquals("\u4F60\u597D", buf.getScreenLine(1))
    }

    @Test
    fun `writeText wide char uses current attributes`() {
        val buf = TerminalBuffer(10, 3)
        val attrs = CellAttributes(foreground = TerminalColor.RED)
        buf.setCurrentAttributes(attrs)
        buf.writeText("\u4E16")
        val row = buf.scrollbackSize
        assertEquals(attrs, buf.getAttributesAt(0, row))
    }

    @Test
    fun `writeText wide char overwrites existing wide char`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("\u4E16") // wide at 0-1
        buf.setCursorPosition(0, 0)
        buf.writeText("AB") // overwrite both cells with narrow chars
        assertEquals('A', buf.getCharAt(0, buf.scrollbackSize))
        assertEquals('B', buf.getCharAt(1, buf.scrollbackSize))
    }

    @Test
    fun `insertText with wide char`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("ABCD")
        buf.setCursorPosition(1, 0)
        buf.insertText("\u4E16")
        // A, 世(2cols), B, C, D
        assertEquals("A\u4E16BCD", buf.getScreenLine(0))
        assertEquals(3, buf.cursorCol)
    }

    @Test
    fun `insertText wide char wraps when 1 col remaining`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(4, 0)
        buf.insertText("\u4E16")
        // Only 1 col left at col 4, wide char wraps to next line
        assertEquals("\u4E16", buf.getScreenLine(1))
        assertEquals(2, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    // ===== Resize tests =====

    // --- Resize: width changes ---

    @Test
    fun `resize to wider width preserves content`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("Hello")
        buf.resize(10, 3)
        assertEquals(10, buf.width)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    @Test
    fun `resize to narrower width truncates content`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("HelloWorld")
        buf.resize(5, 3)
        assertEquals(5, buf.width)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    @Test
    fun `resize width changes scrollback lines too`() {
        val buf = TerminalBuffer(10, 2)
        buf.writeText("Scrollback")
        buf.insertEmptyLineAtBottom()
        assertEquals("Scrollback", buf.getLine(0))
        buf.resize(5, 2)
        assertEquals("Scrol", buf.getLine(0))
    }

    @Test
    fun `resize same width does not change content`() {
        val buf = TerminalBuffer(10, 3)
        buf.writeText("Hello")
        buf.resize(10, 3)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    // --- Resize: height changes ---

    @Test
    fun `resize to shorter height moves lines to scrollback`() {
        val buf = TerminalBuffer(5, 4)
        buf.writeText("AAAAA")
        buf.setCursorPosition(0, 1)
        buf.writeText("BBBBB")
        buf.setCursorPosition(0, 2)
        buf.writeText("CCCCC")
        buf.setCursorPosition(0, 3)
        buf.writeText("DDDDD")
        buf.resize(5, 2)
        assertEquals(2, buf.height)
        // Top 2 lines ("AAAAA", "BBBBB") should go to scrollback
        assertEquals(2, buf.scrollbackSize)
        assertEquals("AAAAA", buf.getLine(0))
        assertEquals("BBBBB", buf.getLine(1))
        assertEquals("CCCCC", buf.getScreenLine(0))
        assertEquals("DDDDD", buf.getScreenLine(1))
    }

    @Test
    fun `resize to taller height pulls from scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("AAAAA")
        buf.insertEmptyLineAtBottom() // "AAAAA" to scrollback
        buf.setCursorPosition(0, 0)
        buf.writeText("BBBBB")
        // Now: scrollback=["AAAAA"], screen=["BBBBB", ""]
        buf.resize(5, 4)
        assertEquals(4, buf.height)
        assertEquals(0, buf.scrollbackSize) // pulled from scrollback
        assertEquals("AAAAA", buf.getScreenLine(0)) // pulled from scrollback
        assertEquals("BBBBB", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
        assertEquals("", buf.getScreenLine(3))
    }

    @Test
    fun `resize to taller height adds empty lines when no scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("Hello")
        buf.resize(5, 5)
        assertEquals(5, buf.height)
        assertEquals(0, buf.scrollbackSize)
        assertEquals("Hello", buf.getScreenLine(0))
        for (i in 1 until 5) {
            assertEquals("", buf.getScreenLine(i))
        }
    }

    @Test
    fun `resize to taller with partial scrollback fills remainder with empty`() {
        val buf = TerminalBuffer(5, 2)
        buf.writeText("AAAAA")
        buf.insertEmptyLineAtBottom() // "AAAAA" to scrollback
        buf.setCursorPosition(0, 0)
        buf.writeText("BBBBB")
        // scrollback=["AAAAA"], screen=["BBBBB", ""]
        // Grow to 5 rows: pull 1 from scrollback, need 2 more empty
        buf.resize(5, 5)
        assertEquals(5, buf.height)
        assertEquals(0, buf.scrollbackSize)
        assertEquals("AAAAA", buf.getScreenLine(0))
        assertEquals("BBBBB", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
        assertEquals("", buf.getScreenLine(3))
        assertEquals("", buf.getScreenLine(4))
    }

    @Test
    fun `resize same height does not change anything`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("Hello")
        buf.resize(5, 3)
        assertEquals(3, buf.height)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    // --- Resize: cursor clamping ---

    @Test
    fun `resize clamps cursor column to new width`() {
        val buf = TerminalBuffer(10, 3)
        buf.setCursorPosition(8, 0)
        buf.resize(5, 3)
        assertEquals(4, buf.cursorCol) // clamped to width-1
    }

    @Test
    fun `resize clamps cursor row to new height`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(0, 4)
        buf.resize(10, 3)
        assertEquals(2, buf.cursorRow) // clamped to height-1
    }

    @Test
    fun `resize cursor stays if within new bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(3, 2)
        buf.resize(10, 5)
        assertEquals(3, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
    }

    // --- Resize: both dimensions ---

    @Test
    fun `resize both width and height simultaneously`() {
        val buf = TerminalBuffer(10, 4)
        buf.writeText("HelloWorld")
        buf.setCursorPosition(0, 1)
        buf.writeText("FooBar")
        buf.resize(5, 2)
        assertEquals(5, buf.width)
        assertEquals(2, buf.height)
        // Top 2 rows moved to scrollback, bottom 2 remain on screen
        assertEquals(2, buf.scrollbackSize)
        assertEquals("Hello", buf.getLine(0))  // truncated from "HelloWorld"
        assertEquals("FooBa", buf.getLine(1))  // truncated from "FooBar"
    }

    // --- Resize: edge cases ---

    @Test
    fun `resize to 1x1`() {
        val buf = TerminalBuffer(10, 5)
        buf.writeText("Hello")
        buf.resize(1, 1)
        assertEquals(1, buf.width)
        assertEquals(1, buf.height)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `resize rejects zero width`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IllegalArgumentException> { buf.resize(0, 3) }
    }

    @Test
    fun `resize rejects zero height`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IllegalArgumentException> { buf.resize(10, 0) }
    }

    @Test
    fun `resize rejects negative dimensions`() {
        val buf = TerminalBuffer(10, 3)
        assertFailsWith<IllegalArgumentException> { buf.resize(-1, 3) }
        assertFailsWith<IllegalArgumentException> { buf.resize(10, -1) }
    }

    @Test
    fun `resize shrink height respects max scrollback`() {
        val buf = TerminalBuffer(5, 4, maxScrollbackSize = 1)
        buf.writeText("AAAAA")
        buf.setCursorPosition(0, 1)
        buf.writeText("BBBBB")
        buf.setCursorPosition(0, 2)
        buf.writeText("CCCCC")
        buf.setCursorPosition(0, 3)
        buf.writeText("DDDDD")
        // Shrink from 4 to 2 rows: "AAAAA" and "BBBBB" go to scrollback, but max is 1
        buf.resize(5, 2)
        assertEquals(1, buf.scrollbackSize) // only last one kept
        assertEquals("BBBBB", buf.getLine(0))
    }

    @Test
    fun `resize with empty buffer`() {
        val buf = TerminalBuffer(10, 5)
        buf.resize(20, 10)
        assertEquals(20, buf.width)
        assertEquals(10, buf.height)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
        assertEquals("", buf.getScreenLine(0))
    }

    @Test
    fun `resize width cleans up wide char split at boundary`() {
        val buf = TerminalBuffer(6, 3)
        // Write a wide char at cols 4-5
        buf.setCursorPosition(4, 0)
        buf.writeText("\u4E16")
        // Shrink to width 5: continuation at col 5 is cut, main at col 4 should be cleaned up
        buf.resize(5, 3)
        assertEquals(' ', buf.getCharAt(4, buf.scrollbackSize))
    }

    @Test
    fun `resize preserves content across grow then shrink`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("Hello")
        buf.resize(10, 5)
        assertEquals("Hello", buf.getScreenLine(0))
        buf.resize(5, 3)
        // When shrinking from 5 to 3 rows, top 2 lines move to scrollback.
        // "Hello" was on row 0, so it goes to scrollback along with row 1 (empty).
        assertEquals(2, buf.scrollbackSize)
        assertEquals("Hello", buf.getLine(0)) // "Hello" in scrollback
    }

    @Test
    fun `resize shrink and grow restores scrollback lines`() {
        val buf = TerminalBuffer(5, 3)
        buf.writeText("AAAAA")
        buf.setCursorPosition(0, 1)
        buf.writeText("BBBBB")
        buf.setCursorPosition(0, 2)
        buf.writeText("CCCCC")
        // Shrink to 1 row: 2 lines go to scrollback
        buf.resize(5, 1)
        assertEquals(2, buf.scrollbackSize)
        assertEquals("CCCCC", buf.getScreenLine(0))
        // Grow back to 3: pulls 2 from scrollback
        buf.resize(5, 3)
        assertEquals(0, buf.scrollbackSize)
        assertEquals("AAAAA", buf.getScreenLine(0))
        assertEquals("BBBBB", buf.getScreenLine(1))
        assertEquals("CCCCC", buf.getScreenLine(2))
    }
}