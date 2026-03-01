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
 * Wide characters (CJK ideographs, fullwidth forms, etc.) are supported. They occupy
 * 2 columns each: a main cell with `width = 2` followed by a [Cell.CONTINUATION] placeholder.
 *
 * The buffer supports configurable scroll regions (DECSTBM). A scroll region defines a
 * contiguous range of rows on screen. When the cursor reaches the bottom of the scroll
 * region, only the lines within the region scroll — lines above and below stay fixed.
 * By default, the scroll region covers the entire screen.
 *
 * @property width The number of columns in the buffer.
 * @property height The number of rows visible on screen.
 * @property maxScrollbackSize The maximum number of lines preserved in scrollback history.
 */
class TerminalBuffer(
    var width: Int,
    var height: Int,
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

    // --- Scroll region ---

    /**
     * The top row (inclusive) of the scroll region, relative to the screen.
     * Defaults to 0 (top of screen).
     */
    var scrollTop: Int = 0
        private set

    /**
     * The bottom row (inclusive) of the scroll region, relative to the screen.
     * Defaults to [height] - 1 (bottom of screen).
     */
    var scrollBottom: Int = height - 1
        private set

    /**
     * Sets the scroll region to the given top and bottom margins (both inclusive).
     *
     * When the cursor reaches [bottom] and needs to advance, only lines within
     * [top]..[bottom] scroll. Lines outside the region are unaffected.
     *
     * The cursor is reset to position (0, 0) after setting the scroll region,
     * matching standard DECSTBM terminal behavior.
     *
     * @throws IllegalArgumentException if [top] >= [bottom], [top] < 0, or [bottom] >= [height].
     */
    fun setScrollRegion(top: Int, bottom: Int) {
        require(top >= 0) { "Scroll region top must be non-negative, got $top" }
        require(bottom < height) { "Scroll region bottom must be less than height ($height), got $bottom" }
        require(top < bottom) { "Scroll region top ($top) must be less than bottom ($bottom)" }
        scrollTop = top
        scrollBottom = bottom
        cursorCol = 0
        cursorRow = 0
    }

    /**
     * Scrolls content within the scroll region up by [n] lines.
     *
     * For each scrolled line, the line at [scrollTop] is removed and a new empty line
     * is inserted at [scrollBottom]. Lines outside the scroll region are unaffected.
     *
     * If [scrollTop] is 0, removed lines are pushed to scrollback (respecting
     * [maxScrollbackSize]). Otherwise they are discarded.
     *
     * If [n] exceeds the region height, the entire region is cleared.
     * The cursor position is not modified.
     *
     * @throws IllegalArgumentException if [n] is negative.
     */
    fun scrollUp(n: Int = 1) {
        require(n >= 0) { "Scroll amount must be non-negative, got $n" }
        val effectiveN = n.coerceAtMost(scrollBottom - scrollTop + 1)
        for (i in 0 until effectiveN) {
            val removedLine = screen.removeAt(scrollTop)
            if (scrollTop == 0 && maxScrollbackSize > 0) {
                scrollback.addLast(removedLine)
                while (scrollback.size > maxScrollbackSize) {
                    scrollback.removeFirst()
                }
            }
            screen.add(scrollBottom, TerminalLine(width))
        }
    }

    /**
     * Scrolls content within the scroll region down by [n] lines.
     *
     * For each scrolled line, the line at [scrollBottom] is removed (discarded) and a
     * new empty line is inserted at [scrollTop]. Lines outside the scroll region are
     * unaffected.
     *
     * Lines removed from the bottom of the region are never pushed to scrollback.
     *
     * If [n] exceeds the region height, the entire region is cleared.
     * The cursor position is not modified.
     *
     * @throws IllegalArgumentException if [n] is negative.
     */
    fun scrollDown(n: Int = 1) {
        require(n >= 0) { "Scroll amount must be non-negative, got $n" }
        val effectiveN = n.coerceAtMost(scrollBottom - scrollTop + 1)
        for (i in 0 until effectiveN) {
            screen.removeAt(scrollBottom)
            screen.add(scrollTop, TerminalLine(width))
        }
    }

    // --- Editing operations (cursor + attributes dependent) ---

    /**
     * Writes text at the current cursor position using the current attributes, overwriting
     * existing content.
     *
     * Wide characters occupy 2 columns. If a wide character doesn't fit at the end of the
     * current line (only 1 column remaining), the cursor wraps to the next line first.
     *
     * If the text extends beyond the right edge of the current line, it wraps to the
     * beginning of the next line. If wrapping reaches the bottom of the screen, the top
     * screen line is scrolled into the scrollback and a new empty line is added at the bottom.
     *
     * The cursor is advanced to the position after the last written character.
     */
    fun writeText(text: String) {
        for (char in text) {
            val charW = Cell.charWidth(char)

            if (charW == 2) {
                // Wide char: if only 1 col left, wrap first
                if (cursorCol >= width - 1) {
                    cursorCol = 0
                    advanceCursorRow()
                }
                screen[cursorRow].setCell(cursorCol, Cell(character = char, attributes = currentAttributes, width = 2))
                screen[cursorRow].setCell(cursorCol + 1, Cell.CONTINUATION)
                cursorCol += 2
            } else {
                if (cursorCol >= width) {
                    cursorCol = 0
                    advanceCursorRow()
                }
                screen[cursorRow].setCell(cursorCol, Cell(character = char, attributes = currentAttributes))
                cursorCol++
            }
        }
    }

    /**
     * Inserts text at the current cursor position using the current attributes, shifting
     * existing content on the current line to the right.
     *
     * Wide characters occupy 2 columns. If a wide character doesn't fit at the end of
     * the current line, the cursor wraps to the next line first.
     *
     * Characters pushed past the line width are truncated. If the cursor reaches the
     * right edge, it wraps to the next line. If wrapping reaches the bottom of the screen,
     * the top screen line is scrolled into scrollback.
     *
     * The cursor is advanced to the position after the last inserted character.
     */
    fun insertText(text: String) {
        for (char in text) {
            val charW = Cell.charWidth(char)

            if (charW == 2) {
                if (cursorCol >= width - 1) {
                    cursorCol = 0
                    advanceCursorRow()
                }
                screen[cursorRow].insertText(cursorCol, char.toString(), currentAttributes)
                cursorCol += 2
            } else {
                if (cursorCol >= width) {
                    cursorCol = 0
                    advanceCursorRow()
                }
                screen[cursorRow].insertText(cursorCol, char.toString(), currentAttributes)
                cursorCol++
            }
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
     * The cursor is reset to position (0, 0). The scroll region is reset to the
     * full screen. Scrollback is not affected.
     */
    fun clearScreen() {
        for (line in screen) {
            line.clear()
        }
        cursorCol = 0
        cursorRow = 0
        scrollTop = 0
        scrollBottom = height - 1
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

    // --- Resize ---

    /**
     * Resizes the buffer to the given dimensions.
     *
     * Content handling strategy:
     * - Lines are truncated or padded to fit the new width. Wide characters that would be
     *   split at the new boundary are cleaned up (replaced with empty cells).
     * - If the new height is smaller, excess screen lines are moved to scrollback (from the top).
     * - If the new height is larger, lines are pulled back from scrollback (if available)
     *   to fill the new screen space, otherwise empty lines are added.
     * - The cursor is clamped to the new screen bounds.
     * - The scroll region is reset to the full screen.
     *
     * @throws IllegalArgumentException if [newWidth] or [newHeight] is not positive.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "New width must be positive, got $newWidth" }
        require(newHeight > 0) { "New height must be positive, got $newHeight" }

        // --- Adjust width ---
        if (newWidth != width) {
            // Resize all screen lines
            for (i in screen.indices) {
                screen[i] = screen[i].copyWithWidth(newWidth)
            }
            // Resize all scrollback lines
            for (i in scrollback.indices) {
                scrollback[i] = scrollback[i].copyWithWidth(newWidth)
            }
        }

        // --- Adjust height ---
        if (newHeight < height) {
            // Shrink: move excess top screen lines to scrollback
            val excessLines = height - newHeight
            for (i in 0 until excessLines) {
                val line = screen.removeFirst()
                if (maxScrollbackSize > 0) {
                    scrollback.addLast(line)
                    while (scrollback.size > maxScrollbackSize) {
                        scrollback.removeFirst()
                    }
                }
            }
        } else if (newHeight > height) {
            // Grow: pull lines from scrollback or add empty lines
            val extraLines = newHeight - height
            val fromScrollback = minOf(extraLines, scrollback.size)

            // Pull lines from the end of scrollback (most recent) and insert at top of screen
            for (i in 0 until fromScrollback) {
                val line = scrollback.removeLast()
                val resized = if (newWidth != width) line else line
                screen.add(0, resized)
            }

            // Fill remaining with empty lines at the bottom
            val emptyLines = extraLines - fromScrollback
            for (i in 0 until emptyLines) {
                screen.add(TerminalLine(newWidth))
            }
        }

        // Update dimensions
        width = newWidth
        height = newHeight

        // Clamp cursor to new bounds
        cursorCol = cursorCol.coerceIn(0, width - 1)
        cursorRow = cursorRow.coerceIn(0, height - 1)

        // Reset scroll region to full screen
        scrollTop = 0
        scrollBottom = height - 1
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
     * Advances the cursor to the next row. If the cursor is at the bottom of the
     * scroll region, scrolls content within the region up by one line.
     *
     * Scrolling behavior:
     * - If the cursor is at [scrollBottom], the line at [scrollTop] is removed and
     *   a new empty line is inserted at [scrollBottom]. Lines outside the region are
     *   unaffected.
     * - If [scrollTop] is 0, the removed line is pushed to scrollback.
     * - If [scrollTop] > 0, the removed line is discarded (it's an internal region
     *   scroll, not history).
     * - If the cursor is not at the bottom of the region, it simply increments.
     */
    private fun advanceCursorRow() {
        if (cursorRow == scrollBottom) {
            // At the bottom of the scroll region — scroll within the region
            val removedLine = screen.removeAt(scrollTop)
            if (scrollTop == 0 && maxScrollbackSize > 0) {
                scrollback.addLast(removedLine)
                while (scrollback.size > maxScrollbackSize) {
                    scrollback.removeFirst()
                }
            }
            screen.add(scrollBottom, TerminalLine(width))
        } else if (cursorRow < height - 1) {
            cursorRow++
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
