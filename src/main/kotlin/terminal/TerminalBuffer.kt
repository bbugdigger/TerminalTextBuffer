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
                    advanceCursorRow(softWrap = true)
                }
                screen[cursorRow].setCell(cursorCol, Cell(character = char, attributes = currentAttributes, width = 2))
                screen[cursorRow].setCell(cursorCol + 1, Cell.CONTINUATION)
                cursorCol += 2
            } else {
                if (cursorCol >= width) {
                    cursorCol = 0
                    advanceCursorRow(softWrap = true)
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
                    advanceCursorRow(softWrap = true)
                }
                screen[cursorRow].insertText(cursorCol, char.toString(), currentAttributes)
                cursorCol += 2
            } else {
                if (cursorCol >= width) {
                    cursorCol = 0
                    advanceCursorRow(softWrap = true)
                }
                screen[cursorRow].insertText(cursorCol, char.toString(), currentAttributes)
                cursorCol++
            }
        }
    }

    /**
     * Deletes [n] characters at the current cursor position on the current line,
     * shifting the remaining content left. Empty cells fill in from the right edge.
     *
     * This is the buffer-level implementation of the DCH (Delete Character) escape sequence.
     * The operation is purely horizontal — it affects only the current line.
     * The cursor position is NOT modified.
     *
     * @param n The number of characters to delete. Clamped to the remaining line width.
     */
    fun deleteChars(n: Int) {
        screen[cursorRow].deleteChars(cursorCol, n)
    }

    /**
     * Inserts [n] blank cells at the current cursor position on the current line,
     * shifting existing content to the right. Content pushed past the line width is discarded.
     *
     * The inserted blank cells use the current [currentAttributes]. This matches real
     * terminal behavior where inserted blanks carry the current SGR attributes.
     *
     * This is the buffer-level implementation of the ICH (Insert Character) escape sequence.
     * The operation is purely horizontal — it affects only the current line.
     * The cursor position is NOT modified.
     *
     * @param n The number of blank cells to insert. Clamped to the remaining line width.
     */
    fun insertBlanks(n: Int) {
        screen[cursorRow].insertBlanks(cursorCol, n, currentAttributes)
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
            line.wrappedFromPrevious = false
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
     * Resizes the buffer to the given dimensions with content reflow.
     *
     * Content handling strategy:
     * - Soft-wrapped lines (those connected by [TerminalLine.wrappedFromPrevious]) are unwrapped
     *   back into their logical lines and then re-wrapped to the new width.
     * - Hard line breaks (where [TerminalLine.wrappedFromPrevious] is false) are preserved.
     * - Wide characters that don't fit at the end of a line during re-wrapping are moved to the
     *   next line, matching the behavior of [writeText].
     * - If re-wrapping produces more lines than fit on screen, excess lines are pushed to scrollback.
     * - If re-wrapping produces fewer lines, lines are pulled from scrollback (if available) to
     *   fill the screen, otherwise empty lines are added at the bottom.
     * - The cursor position is recomputed based on its absolute offset within the logical line,
     *   so it tracks its logical position through the reflow.
     * - The scroll region is reset to the full screen.
     *
     * @throws IllegalArgumentException if [newWidth] or [newHeight] is not positive.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "New width must be positive, got $newWidth" }
        require(newHeight > 0) { "New height must be positive, got $newHeight" }

        // If nothing changed, no work to do (still reset scroll region for consistency)
        if (newWidth == width && newHeight == height) {
            scrollTop = 0
            scrollBottom = height - 1
            return
        }

        // --- Phase 1: Build unified physical line list and locate cursor ---

        val allLines = ArrayList<TerminalLine>(scrollback.size + screen.size)
        for (line in scrollback) allLines.add(line)
        for (line in screen) allLines.add(line)

        // Cursor's absolute position in the physical line list
        val cursorPhysicalRow = scrollback.size + cursorRow

        // Trim trailing empty lines from the end of the buffer.
        // Empty screen lines below content are just padding and shouldn't produce
        // extra logical lines that push content into scrollback during reflow.
        // We keep at least up to the cursor row + 1.
        val keepAtLeast = cursorPhysicalRow + 1
        while (allLines.size > keepAtLeast && isLineEmpty(allLines.last())) {
            allLines.removeAt(allLines.size - 1)
        }

        // --- Phase 2: Group physical lines into logical lines ---
        // A logical line is a sequence of physical lines where all but the first have
        // wrappedFromPrevious = true.

        val logicalLines = mutableListOf<LogicalLine>()
        var currentLogicalCells = mutableListOf<Cell>()
        var currentLogicalStartRow = 0

        for (i in allLines.indices) {
            val line = allLines[i]

            if (i > 0 && line.wrappedFromPrevious) {
                // Continuation of the previous logical line.
                // Strip trailing empty cells from the PREVIOUS segment before appending,
                // because those trailing empties are just padding to fill the line width.
                stripTrailingEmptyCells(currentLogicalCells)
                // Append this line's cells
                currentLogicalCells.addAll(line.getCells())
            } else {
                // Start of a new logical line
                if (i > 0) {
                    // Save the previous logical line
                    logicalLines.add(LogicalLine(currentLogicalCells, currentLogicalStartRow))
                }
                currentLogicalCells = line.getCells().toMutableList()
                currentLogicalStartRow = i
            }
        }

        // Don't forget the last logical line
        logicalLines.add(LogicalLine(currentLogicalCells, currentLogicalStartRow))

        // --- Phase 3: Compute cursor's absolute offset within its logical line ---

        var cursorAbsoluteOffset = 0
        var cursorLogicalLineIndex = -1

        for ((logIdx, logical) in logicalLines.withIndex()) {
            val logicalEndRow = if (logIdx + 1 < logicalLines.size) {
                logicalLines[logIdx + 1].startPhysicalRow - 1
            } else {
                allLines.size - 1
            }

            if (cursorPhysicalRow in logical.startPhysicalRow..logicalEndRow) {
                cursorLogicalLineIndex = logIdx
                // Count the offset: sum up columns in all physical lines before cursorPhysicalRow,
                // then add cursorCol.
                var offset = 0
                for (physRow in logical.startPhysicalRow until cursorPhysicalRow) {
                    offset += countContentColumns(allLines[physRow], width)
                }
                offset += cursorCol
                cursorAbsoluteOffset = offset
                break
            }
        }

        // --- Phase 4: Re-wrap each logical line to newWidth ---

        val rewrappedLines = mutableListOf<TerminalLine>()
        var newCursorRow = 0
        var newCursorCol = 0

        for ((logIdx, logical) in logicalLines.withIndex()) {
            // Strip trailing empty cells from the logical line
            val cells = logical.cells.toMutableList()
            stripTrailingEmptyCells(cells)

            val linesBeforeThisLogical = rewrappedLines.size

            if (cells.isEmpty()) {
                // Empty logical line → produce one empty physical line
                rewrappedLines.add(TerminalLine(newWidth))
            } else {
                // Re-wrap cells into lines of newWidth
                var col = 0
                var currentLine = TerminalLine(newWidth)
                rewrappedLines.add(currentLine)

                var cellIdx = 0
                while (cellIdx < cells.size) {
                    val cell = cells[cellIdx]

                    // Skip continuation cells — they're part of a wide char and will be
                    // re-created when we place the wide char's main cell
                    if (cell.isContinuation) {
                        cellIdx++
                        continue
                    }

                    if (cell.width == 2) {
                        // Wide character: needs 2 columns
                        if (col >= newWidth - 1) {
                            // Doesn't fit on this line, wrap to next
                            val newLine = TerminalLine(newWidth)
                            newLine.wrappedFromPrevious = true
                            rewrappedLines.add(newLine)
                            currentLine = newLine
                            col = 0
                        }
                        currentLine.setCell(col, cell)
                        currentLine.setCell(col + 1, Cell.CONTINUATION)
                        col += 2
                    } else {
                        // Normal character
                        if (col >= newWidth) {
                            // Wrap to next line
                            val newLine = TerminalLine(newWidth)
                            newLine.wrappedFromPrevious = true
                            rewrappedLines.add(newLine)
                            currentLine = newLine
                            col = 0
                        }
                        currentLine.setCell(col, cell)
                        col++
                    }

                    cellIdx++
                }
            }

            // --- Recompute cursor position if this is the cursor's logical line ---
            if (logIdx == cursorLogicalLineIndex) {
                // Walk through the rewrapped physical lines for this logical line
                // to find where cursorAbsoluteOffset lands
                var remaining = cursorAbsoluteOffset
                var found = false

                for (physIdx in linesBeforeThisLogical until rewrappedLines.size) {
                    val lineColCount = countContentColumns(rewrappedLines[physIdx], newWidth)
                    if (remaining <= lineColCount) {
                        // Cursor is on this physical line
                        newCursorRow = physIdx
                        newCursorCol = remaining
                        found = true
                        break
                    }
                    remaining -= lineColCount
                }

                if (!found) {
                    // Cursor was past the end of content — place at end of last line
                    newCursorRow = rewrappedLines.size - 1
                    newCursorCol = countContentColumns(rewrappedLines.last(), newWidth)
                }
            }
        }

        // --- Phase 5: Split rewrapped lines into scrollback and screen ---

        scrollback.clear()
        screen.clear()

        val totalLines = rewrappedLines.size

        if (totalLines <= newHeight) {
            // Fewer lines than screen height: all go to screen, pad with empties
            for (line in rewrappedLines) {
                screen.add(line)
            }
            val emptyCount = newHeight - totalLines
            for (i in 0 until emptyCount) {
                screen.add(TerminalLine(newWidth))
            }
            // Adjust cursor row (it's currently an index into rewrappedLines)
            // newCursorRow is already correct as a screen row since all lines are on screen
        } else {
            // More lines than screen height: excess go to scrollback
            val scrollbackCount = totalLines - newHeight

            for (i in 0 until scrollbackCount) {
                scrollback.addLast(rewrappedLines[i])
            }
            for (i in scrollbackCount until totalLines) {
                screen.add(rewrappedLines[i])
            }

            // Adjust cursor row: it's an index into rewrappedLines, convert to screen row
            newCursorRow -= scrollbackCount

            // Trim scrollback to max size
            while (scrollback.size > maxScrollbackSize) {
                scrollback.removeFirst()
            }
        }

        // --- Phase 6: Update dimensions and cursor ---
        width = newWidth
        height = newHeight

        // Clamp cursor to screen bounds (safety measure)
        cursorCol = newCursorCol.coerceIn(0, width - 1)
        cursorRow = newCursorRow.coerceIn(0, height - 1)

        // Reset scroll region to full screen
        scrollTop = 0
        scrollBottom = height - 1
    }

    /**
     * Represents a logical line: a sequence of cells that form a single logical line of text,
     * possibly spanning multiple physical lines due to soft wrapping.
     *
     * @property cells All cells in this logical line, concatenated from the physical lines.
     * @property startPhysicalRow The index of the first physical line in the unified line list.
     */
    private data class LogicalLine(
        val cells: List<Cell>,
        val startPhysicalRow: Int,
    )

    /**
     * Counts the number of "content columns" used by a physical line.
     *
     * This returns the line width (number of columns), accounting for the fact that
     * content may fill the entire line during wrapping. For cursor offset computation,
     * we count the effective width that was used for content on this line.
     */
    private fun countContentColumns(line: TerminalLine, lineWidth: Int): Int {
        // For wrapped lines, the entire line width was used for content
        // (trailing empties were just padding within the line width).
        // For non-wrapped last lines, we still need to count the full width
        // to properly compute cursor positions.
        return lineWidth
    }

    /**
     * Removes trailing empty cells from a mutable cell list in-place.
     * Empty cells are those that are [Cell.EMPTY] (space char with default attributes).
     * Continuation cells at the end are also removed.
     */
    private fun stripTrailingEmptyCells(cells: MutableList<Cell>) {
        while (cells.isNotEmpty() && (cells.last().isEmpty || cells.last().isContinuation)) {
            cells.removeAt(cells.size - 1)
        }
        // If the last remaining cell is a wide char (width=2), make sure its continuation isn't lost
        // (it would have been removed above). We need to re-add it.
        if (cells.isNotEmpty() && cells.last().width == 2) {
            cells.add(Cell.CONTINUATION)
        }
    }

    /**
     * Returns true if a [TerminalLine] is entirely empty (all cells are [Cell.EMPTY])
     * and it is not a soft-wrap continuation.
     */
    private fun isLineEmpty(line: TerminalLine): Boolean {
        if (line.wrappedFromPrevious) return false
        for (cell in line.getCells()) {
            if (!cell.isEmpty) return false
        }
        return true
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

    // --- Dirty tracking ---

    /**
     * Returns whether the screen line at the given row has been modified since it was
     * last marked clean.
     *
     * This is intended for use by a rendering layer: only dirty lines need to be redrawn.
     *
     * @param screenRow Row index relative to the screen (0 = top of screen).
     * @throws IndexOutOfBoundsException if [screenRow] is out of bounds.
     */
    fun isLineDirty(screenRow: Int): Boolean {
        requireValidScreenRow(screenRow)
        return screen[screenRow].dirty
    }

    /**
     * Marks all screen lines as clean (not dirty).
     *
     * A rendering layer should call this after drawing the entire screen, so that
     * subsequent queries via [isLineDirty] only report lines that changed since
     * the last render pass.
     *
     * Only screen lines are affected — scrollback lines are not touched, since they
     * are not part of the visible display.
     */
    fun clearDirtyFlags() {
        for (line in screen) {
            line.markClean()
        }
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
     *
     * @param softWrap If true, the destination line is marked as a soft-wrap continuation
     *        of the previous line. This is used to track which line breaks are caused by text
     *        overflow (soft) vs. actual newlines (hard), enabling content reflow on resize.
     */
    private fun advanceCursorRow(softWrap: Boolean = false) {
        if (cursorRow == scrollBottom) {
            // At the bottom of the scroll region — scroll within the region
            val removedLine = screen.removeAt(scrollTop)
            if (scrollTop == 0 && maxScrollbackSize > 0) {
                scrollback.addLast(removedLine)
                while (scrollback.size > maxScrollbackSize) {
                    scrollback.removeFirst()
                }
            }
            val newLine = TerminalLine(width)
            if (softWrap) {
                newLine.wrappedFromPrevious = true
            }
            screen.add(scrollBottom, newLine)
        } else if (cursorRow < height - 1) {
            cursorRow++
            if (softWrap) {
                screen[cursorRow].wrappedFromPrevious = true
            }
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
