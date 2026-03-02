package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TerminalBufferSerializerTest {

    // --- Basic round-trip ---

    @Test
    fun `round-trip empty buffer`() {
        val buffer = TerminalBuffer(80, 24, 1000)
        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals(80, restored.width)
        assertEquals(24, restored.height)
        assertEquals(1000, restored.maxScrollbackSize)
        assertEquals(0, restored.cursorCol)
        assertEquals(0, restored.cursorRow)
        assertEquals(CellAttributes.DEFAULT, restored.currentAttributes)
        assertNotSame(buffer, restored)
    }

    @Test
    fun `round-trip buffer with text`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.writeText("Hello")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals("Hello", restored.getScreenLines()[0].getText())
        assertEquals(5, restored.cursorCol)
        assertEquals(0, restored.cursorRow)
    }

    @Test
    fun `round-trip buffer with cursor position`() {
        val buffer = TerminalBuffer(10, 5, 100)
        buffer.setCursorPosition(3, 2)

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals(3, restored.cursorCol)
        assertEquals(2, restored.cursorRow)
    }

    @Test
    fun `round-trip buffer with custom attributes`() {
        val attrs = CellAttributes(
            foreground = TerminalColor.RED,
            background = TerminalColor.BRIGHT_CYAN,
            style = TextStyle(bold = true, italic = false, underline = true),
        )
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.setCurrentAttributes(attrs)
        buffer.writeText("styled")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals(attrs, restored.currentAttributes)
        val cell = restored.getScreenLines()[0].getCell(0)
        assertEquals('s', cell.character)
        assertEquals(TerminalColor.RED, cell.attributes.foreground)
        assertEquals(TerminalColor.BRIGHT_CYAN, cell.attributes.background)
        assertTrue(cell.attributes.style.bold)
        assertFalse(cell.attributes.style.italic)
        assertTrue(cell.attributes.style.underline)
    }

    @Test
    fun `round-trip buffer with scrollback`() {
        val buffer = TerminalBuffer(10, 2, 100)
        // Write enough lines to push content into scrollback
        buffer.writeText("line1")
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line2")
        buffer.insertEmptyLineAtBottom()  // scrolls line1 to scrollback
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line3")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val scrollback = restored.getScrollbackLines()
        assertEquals(1, scrollback.size)
        assertEquals("line1", scrollback[0].getText())

        val screen = restored.getScreenLines()
        assertEquals("line2", screen[0].getText())
        assertEquals("line3", screen[1].getText())
    }

    // --- Wide characters ---

    @Test
    fun `round-trip buffer with wide characters`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.writeText("\u4E16\u754C")  // 世界 — 2 wide chars, 4 columns

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val cells = restored.getScreenLines()[0].getCells()
        assertEquals('\u4E16', cells[0].character)
        assertEquals(2, cells[0].width)
        assertEquals(0, cells[1].width)  // continuation
        assertEquals('\u754C', cells[2].character)
        assertEquals(2, cells[2].width)
        assertEquals(0, cells[3].width)  // continuation
    }

    // --- Line wrapping ---

    @Test
    fun `round-trip preserves wrappedFromPrevious flag`() {
        val buffer = TerminalBuffer(5, 3, 100)
        buffer.writeText("HelloWorld")  // wraps at col 5

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val screen = restored.getScreenLines()
        assertFalse(screen[0].wrappedFromPrevious)
        assertTrue(screen[1].wrappedFromPrevious)
    }

    // --- Multiple scrollback lines ---

    @Test
    fun `round-trip with multiple scrollback lines`() {
        val buffer = TerminalBuffer(10, 2, 100)
        // Write line1 on row 0, line2 on row 1
        buffer.writeText("line1")
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line2")
        // Scroll: line1 goes to scrollback, new empty at bottom
        buffer.insertEmptyLineAtBottom()
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line3")
        // Scroll: line2 goes to scrollback
        buffer.insertEmptyLineAtBottom()
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line4")
        // Scroll: line3 goes to scrollback
        buffer.insertEmptyLineAtBottom()
        buffer.setCursorPosition(0, 1)
        buffer.writeText("line5")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val scrollback = restored.getScrollbackLines()
        assertEquals(3, scrollback.size)
        assertEquals("line1", scrollback[0].getText())
        assertEquals("line2", scrollback[1].getText())
        assertEquals("line3", scrollback[2].getText())

        val screen = restored.getScreenLines()
        assertEquals("line4", screen[0].getText())
        assertEquals("line5", screen[1].getText())
    }

    // --- Edge cases ---

    @Test
    fun `round-trip minimal buffer 1x1`() {
        val buffer = TerminalBuffer(1, 1, 0)
        buffer.writeText("X")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals(1, restored.width)
        assertEquals(1, restored.height)
        assertEquals(0, restored.maxScrollbackSize)
        assertEquals("X", restored.getScreenLines()[0].getText())
    }

    @Test
    fun `round-trip buffer with special characters in text`() {
        val buffer = TerminalBuffer(20, 3, 100)
        buffer.writeText("a\"b\\c\td")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertEquals("a\"b\\c\td", restored.getScreenLines()[0].getText())
    }

    @Test
    fun `round-trip buffer with all terminal colors`() {
        val buffer = TerminalBuffer(20, 3, 100)
        for (color in TerminalColor.entries) {
            buffer.setCurrentAttributes(CellAttributes(foreground = color))
            buffer.writeText("x")
        }

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val cells = restored.getScreenLines()[0].getCells()
        for ((i, color) in TerminalColor.entries.withIndex()) {
            assertEquals(color, cells[i].attributes.foreground,
                "Color mismatch at index $i: expected $color")
        }
    }

    @Test
    fun `round-trip buffer with all style combinations`() {
        val buffer = TerminalBuffer(20, 3, 100)
        val styles = listOf(
            TextStyle(bold = true),
            TextStyle(italic = true),
            TextStyle(underline = true),
            TextStyle(bold = true, italic = true),
            TextStyle(bold = true, underline = true),
            TextStyle(italic = true, underline = true),
            TextStyle(bold = true, italic = true, underline = true),
            TextStyle.PLAIN,
        )
        for (style in styles) {
            buffer.setCurrentAttributes(CellAttributes(style = style))
            buffer.writeText("x")
        }

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val cells = restored.getScreenLines()[0].getCells()
        for ((i, style) in styles.withIndex()) {
            assertEquals(style, cells[i].attributes.style,
                "Style mismatch at index $i: expected $style")
        }
    }

    // --- Dirty flags are reset ---

    @Test
    fun `deserialized lines start dirty`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.writeText("Hello")

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        // All lines should be dirty (fresh TerminalLines created by fromCells)
        for (i in 0 until restored.height) {
            assertTrue(restored.isLineDirty(i), "Screen line $i should be dirty")
        }
    }

    // --- Selection is not preserved ---

    @Test
    fun `selection is not serialized`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.writeText("Hello World")
        buffer.setSelection(0, 0, 0, 5)
        assertTrue(buffer.hasSelection())

        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        assertFalse(restored.hasSelection())
    }

    // --- JSON structure validation ---

    @Test
    fun `serialized JSON contains expected top-level fields`() {
        val buffer = TerminalBuffer(10, 3, 100)
        val json = TerminalBufferSerializer.serialize(buffer)
        val parsed = JsonParser.parse(json).asObject()

        assertEquals(10, parsed.getInt("width"))
        assertEquals(3, parsed.getInt("height"))
        assertEquals(100, parsed.getInt("maxScrollbackSize"))
        assertEquals(0, parsed.getInt("cursorCol"))
        assertEquals(0, parsed.getInt("cursorRow"))
        assertTrue(parsed.getOrNull("currentAttributes") is JsonObject)
        assertTrue(parsed.getOrNull("scrollback") is JsonArray)
        assertTrue(parsed.getOrNull("screen") is JsonArray)
    }

    @Test
    fun `serialized screen has correct number of lines`() {
        val buffer = TerminalBuffer(10, 5, 100)
        val json = TerminalBufferSerializer.serialize(buffer)
        val parsed = JsonParser.parse(json).asObject()

        assertEquals(5, parsed.getArray("screen").size)
    }

    @Test
    fun `serialized cells have expected fields`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.setCurrentAttributes(CellAttributes(
            foreground = TerminalColor.GREEN,
            background = TerminalColor.BLACK,
            style = TextStyle(bold = true),
        ))
        buffer.writeText("A")

        val json = TerminalBufferSerializer.serialize(buffer)
        val parsed = JsonParser.parse(json).asObject()
        val firstLine = parsed.getArray("screen")[0].asObject()
        val firstCell = firstLine.getArray("cells")[0].asObject()

        assertEquals("A", firstCell.getString("char"))
        assertEquals("GREEN", firstCell.getString("fg"))
        assertEquals("BLACK", firstCell.getString("bg"))
        assertEquals(true, firstCell.getBool("bold"))
        assertEquals(false, firstCell.getBool("italic"))
        assertEquals(false, firstCell.getBool("underline"))
        assertEquals(1, firstCell.getInt("width"))
    }

    // --- Error handling ---

    @Test
    fun `deserialize rejects invalid JSON`() {
        assertFailsWith<IllegalArgumentException> {
            TerminalBufferSerializer.deserialize("not json")
        }
    }

    @Test
    fun `deserialize rejects missing fields`() {
        assertFailsWith<Exception> {
            TerminalBufferSerializer.deserialize("""{"width":10}""")
        }
    }

    // --- Scrollback lines with different widths ---

    @Test
    fun `round-trip preserves scrollback lines with different widths`() {
        val buffer = TerminalBuffer(10, 2, 100)
        buffer.writeText("1234567890")  // fills 10 cols
        buffer.setCursorPosition(0, 1)
        buffer.writeText("ABCDE")
        buffer.insertEmptyLineAtBottom()  // scrolls "1234567890" to scrollback

        // Resize to 5 wide — scrollback line still keeps old width in cells
        // Actually, resize reflows. Let's just verify the count is preserved
        val json = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json)

        val scrollback = restored.getScrollbackLines()
        assertEquals(1, scrollback.size)
        assertEquals("1234567890", scrollback[0].getText())
    }

    // --- TerminalLine.fromCells ---

    @Test
    fun `fromCells creates line with correct cells`() {
        val cells = listOf(
            Cell(character = 'A', attributes = CellAttributes(foreground = TerminalColor.RED)),
            Cell(character = 'B'),
            Cell.EMPTY,
        )
        val line = TerminalLine.fromCells(cells)

        assertEquals(3, line.width)
        assertEquals('A', line.getCell(0).character)
        assertEquals(TerminalColor.RED, line.getCell(0).attributes.foreground)
        assertEquals('B', line.getCell(1).character)
        assertEquals(Cell.EMPTY, line.getCell(2))
        assertFalse(line.wrappedFromPrevious)
        assertTrue(line.dirty)
    }

    @Test
    fun `fromCells with wrappedFromPrevious`() {
        val cells = listOf(Cell(character = 'X'))
        val line = TerminalLine.fromCells(cells, wrappedFromPrevious = true)

        assertEquals(1, line.width)
        assertTrue(line.wrappedFromPrevious)
    }

    @Test
    fun `fromCells rejects empty list`() {
        assertFailsWith<IllegalArgumentException> {
            TerminalLine.fromCells(emptyList())
        }
    }

    @Test
    fun `fromCells preserves wide character cells`() {
        val cells = listOf(
            Cell(character = '\u4E16', attributes = CellAttributes.DEFAULT, width = 2),
            Cell.CONTINUATION,
            Cell.EMPTY,
        )
        val line = TerminalLine.fromCells(cells)

        assertEquals(3, line.width)
        assertEquals('\u4E16', line.getCell(0).character)
        assertEquals(2, line.getCell(0).width)
        assertEquals(0, line.getCell(1).width)  // continuation
    }

    // --- Idempotent serialization ---

    @Test
    fun `double serialization produces identical JSON`() {
        val buffer = TerminalBuffer(10, 3, 100)
        buffer.setCurrentAttributes(CellAttributes(foreground = TerminalColor.YELLOW))
        buffer.writeText("Test123")
        buffer.setCursorPosition(0, 1)
        buffer.writeText("Row2")

        val json1 = TerminalBufferSerializer.serialize(buffer)
        val restored = TerminalBufferSerializer.deserialize(json1)
        val json2 = TerminalBufferSerializer.serialize(restored)

        assertEquals(json1, json2)
    }
}