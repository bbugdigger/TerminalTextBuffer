package terminal

/**
 * Serializes and deserializes [TerminalBuffer] state to and from JSON strings.
 *
 * This is a standalone object — the buffer itself has no knowledge of serialization.
 * Uses [JsonWriter] for output and [JsonParser] for input.
 *
 * ## Serialized state
 * - Constructor parameters: `width`, `height`, `maxScrollbackSize`
 * - Cursor position: `cursorCol`, `cursorRow`
 * - Current text attributes: `currentAttributes` (foreground, background, bold, italic, underline)
 * - `scrollback` and `screen` lines, each with `wrappedFromPrevious` and a `cells` array
 * - Each cell: `char`, `fg`, `bg`, `bold`, `italic`, `underline`, `width`
 *
 * ## Excluded state (reset to defaults on deserialization)
 * - Dirty flags (new lines start dirty)
 * - Scroll region (reset to full screen)
 * - Selection (ephemeral)
 */
object TerminalBufferSerializer {

    /**
     * Serializes a [TerminalBuffer] to a JSON string.
     */
    fun serialize(buffer: TerminalBuffer): String = JsonWriter.build {
        writeObject {
            writeField("width", buffer.width)
            writeField("height", buffer.height)
            writeField("maxScrollbackSize", buffer.maxScrollbackSize)
            writeField("cursorCol", buffer.cursorCol)
            writeField("cursorRow", buffer.cursorRow)
            writeFieldObject("currentAttributes") {
                writeAttributes(buffer.currentAttributes)
            }
            writeFieldArray("scrollback") {
                for (line in buffer.getScrollbackLines()) {
                    writeValueObject { writeLine(line) }
                }
            }
            writeFieldArray("screen") {
                for (line in buffer.getScreenLines()) {
                    writeValueObject { writeLine(line) }
                }
            }
        }
    }

    /**
     * Deserializes a JSON string into a new [TerminalBuffer].
     *
     * @throws IllegalArgumentException if the JSON is malformed or missing required fields.
     */
    fun deserialize(json: String): TerminalBuffer {
        val root = JsonParser.parse(json).asObject()

        val width = root.getInt("width")
        val height = root.getInt("height")
        val maxScrollbackSize = root.getInt("maxScrollbackSize")
        val cursorCol = root.getInt("cursorCol")
        val cursorRow = root.getInt("cursorRow")
        val currentAttributes = readAttributes(root.getObject("currentAttributes"))

        val scrollbackArray = root.getArray("scrollback")
        val scrollbackLines = (0 until scrollbackArray.size).map { i ->
            readLine(scrollbackArray[i].asObject())
        }

        val screenArray = root.getArray("screen")
        val screenLines = (0 until screenArray.size).map { i ->
            readLine(screenArray[i].asObject())
        }

        return TerminalBuffer.fromState(
            width = width,
            height = height,
            maxScrollbackSize = maxScrollbackSize,
            cursorCol = cursorCol,
            cursorRow = cursorRow,
            currentAttributes = currentAttributes,
            screenLines = screenLines,
            scrollbackLines = scrollbackLines,
        )
    }

    // --- Private serialize helpers ---

    private fun JsonWriter.writeAttributes(attrs: CellAttributes) {
        writeField("foreground", attrs.foreground.name)
        writeField("background", attrs.background.name)
        writeField("bold", attrs.style.bold)
        writeField("italic", attrs.style.italic)
        writeField("underline", attrs.style.underline)
    }

    private fun JsonWriter.writeLine(line: TerminalLine) {
        writeField("wrappedFromPrevious", line.wrappedFromPrevious)
        writeFieldArray("cells") {
            for (cell in line.getCells()) {
                writeValueObject { writeCell(cell) }
            }
        }
    }

    private fun JsonWriter.writeCell(cell: Cell) {
        writeField("char", cell.character.toString())
        writeField("fg", cell.attributes.foreground.name)
        writeField("bg", cell.attributes.background.name)
        writeField("bold", cell.attributes.style.bold)
        writeField("italic", cell.attributes.style.italic)
        writeField("underline", cell.attributes.style.underline)
        writeField("width", cell.width)
    }

    // --- Private deserialize helpers ---

    private fun readAttributes(obj: JsonObject): CellAttributes {
        val fg = TerminalColor.valueOf(obj.getString("foreground"))
        val bg = TerminalColor.valueOf(obj.getString("background"))
        val bold = obj.getBool("bold")
        val italic = obj.getBool("italic")
        val underline = obj.getBool("underline")
        return CellAttributes(
            foreground = fg,
            background = bg,
            style = TextStyle(bold = bold, italic = italic, underline = underline),
        )
    }

    private fun readLine(obj: JsonObject): TerminalLine {
        val wrappedFromPrevious = obj.getBool("wrappedFromPrevious")
        val cellsArray = obj.getArray("cells")
        val cells = (0 until cellsArray.size).map { i ->
            readCell(cellsArray[i].asObject())
        }
        return TerminalLine.fromCells(cells, wrappedFromPrevious)
    }

    private fun readCell(obj: JsonObject): Cell {
        val charStr = obj.getString("char")
        val character = if (charStr.isNotEmpty()) charStr[0] else Cell.EMPTY_CHAR
        val fg = TerminalColor.valueOf(obj.getString("fg"))
        val bg = TerminalColor.valueOf(obj.getString("bg"))
        val bold = obj.getBool("bold")
        val italic = obj.getBool("italic")
        val underline = obj.getBool("underline")
        val width = obj.getInt("width")
        return Cell(
            character = character,
            attributes = CellAttributes(
                foreground = fg,
                background = bg,
                style = TextStyle(bold = bold, italic = italic, underline = underline),
            ),
            width = width,
        )
    }
}
