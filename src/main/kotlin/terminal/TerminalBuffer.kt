package terminal

/**
 * A terminal text buffer that stores and manipulates displayed text.
 *
 * The buffer consists of two logical parts:
 * - **Screen**: The last [height] lines that fit the screen dimensions. This is the editable
 *   part and what users see. The screen is addressed with rows 0 (top) to height-1 (bottom).
 * - **Scrollback**: Lines that have scrolled off the top of the screen, preserved for history.
 *   Scrollback lines are read-only and bounded by [maxScrollbackSize].
 *
 * The buffer maintains a cursor position (column, row) within the screen, and a set of
 * current [CellAttributes] that are applied to all subsequent editing operations.
 *
 * Content access methods use an absolute row addressing scheme where row 0 is the oldest
 * scrollback line and row [scrollbackSize] is the first screen line. This provides a unified
 * view over the entire buffer history.
 *
 * @property width The number of columns in the buffer.
 * @property height The number of rows visible on screen.
 * @property maxScrollbackSize The maximum number of lines preserved in scrollback history.
 */
class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollbackSize: Int = 1000,
) {
    init {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(maxScrollbackSize >= 0) { "Max scrollback size must be non-negative, got $maxScrollbackSize" }
    }

    // --- Internal storage ---

    private val screen: MutableList<TerminalLine> = MutableList(height) { TerminalLine(width) }
    private val scrollback: ArrayDeque<TerminalLine> = ArrayDeque()

    // --- Current attributes ---

    /**
     * The current text attributes applied to all subsequent editing operations.
     */
    var currentAttributes: CellAttributes = CellAttributes.DEFAULT
        private set

    /**
     * Sets the current text attributes used for subsequent editing operations.
     */
    fun setCurrentAttributes(attributes: CellAttributes) {
        currentAttributes = attributes
    }

    // --- Cursor ---

    /**
     * The current cursor column (0-based). Clamped to [0, width - 1].
     */
    var cursorCol: Int = 0
        private set

    /**
     * The current cursor row (0-based, relative to screen). Clamped to [0, height - 1].
     */
    var cursorRow: Int = 0
        private set

    /**
     * Sets the cursor position, clamping to screen bounds.
     */
    fun setCursorPosition(col: Int, row: Int) {
        cursorCol = col.coerceIn(0, width - 1)
        cursorRow = row.coerceIn(0, height - 1)
    }

    /**
     * Moves the cursor up by [n] rows. Stops at the top of the screen (row 0).
     */
    fun moveCursorUp(n: Int = 1) {
        require(n >= 0) { "Movement amount must be non-negative, got $n" }
        cursorRow = (cursorRow - n).coerceAtLeast(0)
    }

    /**
     * Moves the cursor down by [n] rows. Stops at the bottom of the screen (row height - 1).
     */
    fun moveCursorDown(n: Int = 1) {
        require(n >= 0) { "Movement amount must be non-negative, got $n" }
        cursorRow = (cursorRow + n).coerceAtMost(height - 1)
    }

    /**
     * Moves the cursor left by [n] columns. Stops at the left edge (column 0).
     */
    fun moveCursorLeft(n: Int = 1) {
        require(n >= 0) { "Movement amount must be non-negative, got $n" }
        cursorCol = (cursorCol - n).coerceAtLeast(0)
    }

    /**
     * Moves the cursor right by [n] columns. Stops at the right edge (column width - 1).
     */
    fun moveCursorRight(n: Int = 1) {
        require(n >= 0) { "Movement amount must be non-negative, got $n" }
        cursorCol = (cursorCol + n).coerceAtMost(width - 1)
    }

    // --- Editing operations (cursor + attributes dependent) ---

    /**
     * Writes text at the current cursor position using the current attributes, overwriting
     * existing content.
     *
     * If the text extends beyond the right edge of the current line, it wraps to the
     * beginning of the next line. If wrapping reaches the bottom of the screen, the top
     * screen line is scrolled into the scrollback and a new empty line is added at the bottom.
     *
     * The cursor is advanced to the position after the last written character.
     */
    fun writeText(text: String) {
        for (char in text) {
            if (cursorCol >= width) {
                // Wrap to the next line
                cursorCol = 0
                advanceCursorRow()
            }
            screen[cursorRow].setCell(cursorCol, Cell(character = char, attributes = currentAttributes))
            cursorCol++
        }
    }

    /**
     * Inserts text at the current cursor position using the current attributes, shifting
     * existing content on the current line to the right.
     *
     * Characters pushed past the line width are truncated. If the inserted text itself
     * extends beyond the right edge, it wraps to the next line (inserting on each subsequent
     * line as well). If wrapping reaches the bottom of the screen, the top screen line is
     * scrolled into scrollback.
     *
     * The cursor is advanced to the position after the last inserted character.
     */
    fun insertText(text: String) {
        for (char in text) {
            if (cursorCol >= width) {
                cursorCol = 0
                advanceCursorRow()
            }
            screen[cursorRow].insertText(cursorCol, char.toString(), currentAttributes)
            cursorCol++
        }
    }

    /**
     * Fills the current cursor's line with the given character using the current attributes.
     *
     * If [char] is null, the line is cleared to empty cells.
     * The cursor position is not modified.
     */
    fun fillLine(char: Char? = null) {
        if (char == null) {
            screen[cursorRow].fill(null)
        } else {
            screen[cursorRow].fill(char, currentAttributes)
        }
    }

    // --- Editing operations (cursor/attributes independent) ---

    /**
     * Inserts an empty line at the bottom of the screen.
     *
     * The top screen line is pushed into scrollback (respecting [maxScrollbackSize]),
     * all remaining screen lines shift up by one position, and a new empty line is
     * appended at the bottom.
     *
     * The cursor position is not modified.
     */
    fun insertEmptyLineAtBottom() {
        scrollTopLine()
        screen.add(TerminalLine(width))
    }

    /**
     * Clears the entire screen, resetting all screen lines to empty.
     *
     * The cursor is reset to position (0, 0). Scrollback is not affected.
     */
    fun clearScreen() {
        for (line in screen) {
            line.clear()
        }
        cursorCol = 0
        cursorRow = 0
    }

    /**
     * Clears the entire screen and all scrollback history.
     *
     * The cursor is reset to position (0, 0).
     */
    fun clearAll() {
        clearScreen()
        scrollback.clear()
    }

    // --- Content access ---

    /**
     * The number of lines currently in the scrollback.
     */
    val scrollbackSize: Int
        get() = scrollback.size

    /**
     * The total number of lines in the buffer (scrollback + screen).
     */
    val totalLineCount: Int
        get() = scrollback.size + height

    /**
     * Returns the character at the given absolute position.
     *
     * Absolute row 0 is the oldest scrollback line. Row [scrollbackSize] is the first screen line.
     *
     * @throws IndexOutOfBoundsException if [col] or [absoluteRow] is out of bounds.
     */
    fun getCharAt(col: Int, absoluteRow: Int): Char {
        return getLineByAbsoluteRow(absoluteRow).getCell(col).character
    }

    /**
     * Returns the text attributes at the given absolute position.
     *
     * Absolute row 0 is the oldest scrollback line. Row [scrollbackSize] is the first screen line.
     *
     * @throws IndexOutOfBoundsException if [col] or [absoluteRow] is out of bounds.
     */
    fun getAttributesAt(col: Int, absoluteRow: Int): CellAttributes {
        return getLineByAbsoluteRow(absoluteRow).getCell(col).attributes
    }

    /**
     * Returns the text content of the line at the given absolute row.
     *
     * Absolute row 0 is the oldest scrollback line. Row [scrollbackSize] is the first screen line.
     *
     * @throws IndexOutOfBoundsException if [absoluteRow] is out of bounds.
     */
    fun getLine(absoluteRow: Int): String {
        return getLineByAbsoluteRow(absoluteRow).getText()
    }

    /**
     * Returns the text content of a screen line by its screen-relative row index.
     *
     * @param screenRow Row index relative to the screen (0 = top of screen).
     * @throws IndexOutOfBoundsException if [screenRow] is out of bounds.
     */
    fun getScreenLine(screenRow: Int): String {
        requireValidScreenRow(screenRow)
        return screen[screenRow].getText()
    }

    /**
     * Returns the entire screen content as a string, with lines separated by newlines.
     *
     * Trailing empty lines are included. Each line has trailing spaces trimmed.
     */
    fun getScreenContent(): String {
        return screen.joinToString("\n") { it.getText() }
    }

    /**
     * Returns the entire buffer content (scrollback + screen) as a string,
     * with lines separated by newlines.
     *
     * Each line has trailing spaces trimmed.
     */
    fun getFullContent(): String {
        val lines = mutableListOf<String>()
        for (line in scrollback) {
            lines.add(line.getText())
        }
        for (line in screen) {
            lines.add(line.getText())
        }
        return lines.joinToString("\n")
    }

    // --- Internal helpers ---

    /**
     * Advances the cursor to the next row. If the cursor is already at the bottom
     * of the screen, scrolls the top line into scrollback and adds a new empty line
     * at the bottom.
     */
    private fun advanceCursorRow() {
        if (cursorRow < height - 1) {
            cursorRow++
        } else {
            // At the bottom of the screen — scroll up
            scrollTopLine()
            screen.add(TerminalLine(width))
        }
    }

    /**
     * Removes the top screen line and pushes it into scrollback.
     * If scrollback exceeds [maxScrollbackSize], the oldest scrollback line is dropped.
     */
    private fun scrollTopLine() {
        val topLine = screen.removeFirst()
        if (maxScrollbackSize > 0) {
            scrollback.addLast(topLine)
            while (scrollback.size > maxScrollbackSize) {
                scrollback.removeFirst()
            }
        }
    }

    /**
     * Returns the [TerminalLine] at the given absolute row index.
     *
     * @throws IndexOutOfBoundsException if [absoluteRow] is out of bounds.
     */
    private fun getLineByAbsoluteRow(absoluteRow: Int): TerminalLine {
        if (absoluteRow < 0 || absoluteRow >= totalLineCount) {
            throw IndexOutOfBoundsException(
                "Absolute row $absoluteRow is out of bounds (total lines: $totalLineCount)"
            )
        }
        return if (absoluteRow < scrollback.size) {
            scrollback[absoluteRow]
        } else {
            screen[absoluteRow - scrollback.size]
        }
    }

    private fun requireValidScreenRow(screenRow: Int) {
        if (screenRow < 0 || screenRow >= height) {
            throw IndexOutOfBoundsException(
                "Screen row $screenRow is out of bounds for screen height $height"
            )
        }
    }
}
