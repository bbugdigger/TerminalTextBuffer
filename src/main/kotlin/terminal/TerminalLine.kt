package terminal

/**
 * Represents a single line (row) in the terminal buffer.
 *
 * A line is a fixed-width array of [Cell] objects. It provides operations
 * for reading and modifying cells, writing/inserting text, and clearing content.
 *
 * Wide characters (CJK, fullwidth, etc.) occupy 2 columns: the character is stored
 * in the first cell with `width = 2`, and the second cell is a [Cell.CONTINUATION]
 * placeholder. When overwriting part of a wide character, the orphaned half is
 * automatically cleaned up (replaced with an empty cell).
 *
 * @property width The number of columns in this line.
 */
class TerminalLine(val width: Int) {

    init {
        require(width > 0) { "Line width must be positive, got $width" }
    }

    private val cells: Array<Cell> = Array(width) { Cell.EMPTY }

    /**
     * Returns the cell at the given column.
     *
     * @throws IndexOutOfBoundsException if [col] is outside [0, width).
     */
    fun getCell(col: Int): Cell {
        requireValidColumn(col)
        return cells[col]
    }

    /**
     * Sets the cell at the given column.
     *
     * If the cell being overwritten is part of a wide character (either the main cell
     * or its continuation), the orphaned counterpart is cleaned up.
     *
     * @throws IndexOutOfBoundsException if [col] is outside [0, width).
     */
    fun setCell(col: Int, cell: Cell) {
        requireValidColumn(col)
        cleanUpWideChar(col)
        cells[col] = cell
    }

    /**
     * Writes text starting at [startCol] using the given [attributes], overwriting existing content.
     *
     * Wide characters occupy 2 columns: the character cell followed by a [Cell.CONTINUATION].
     * If a wide character doesn't fit at the end of the line (only 1 column remaining),
     * it is skipped. Overwriting part of an existing wide character cleans up the orphaned half.
     *
     * @return The column position after the last written character (i.e., the new cursor column).
     *         This value may equal [width] if writing reached the end of the line.
     */
    fun writeText(startCol: Int, text: String, attributes: CellAttributes): Int {
        var col = startCol
        for (char in text) {
            val charW = Cell.charWidth(char)
            if (charW == 2) {
                // Wide character needs 2 columns
                if (col + 1 >= width) break // doesn't fit
                cleanUpWideChar(col)
                cleanUpWideChar(col + 1)
                cells[col] = Cell(character = char, attributes = attributes, width = 2)
                cells[col + 1] = Cell.CONTINUATION
                col += 2
            } else {
                if (col >= width) break
                cleanUpWideChar(col)
                cells[col] = Cell(character = char, attributes = attributes)
                col++
            }
        }
        return col
    }

    /**
     * Inserts text at [startCol] using the given [attributes], shifting existing content to the right.
     *
     * Existing cells from [startCol] onward are shifted right to make room for the inserted text.
     * Any cells shifted beyond the line width are discarded (truncated).
     * Wide characters that would be split by the shift boundary are cleaned up.
     *
     * @return The column position after the last inserted character (i.e., the new cursor column).
     *         This value may equal [width] if insertion reached the end of the line.
     */
    fun insertText(startCol: Int, text: String, attributes: CellAttributes): Int {
        var col = startCol
        for (char in text) {
            val charW = Cell.charWidth(char)
            val neededCols = charW

            if (col + neededCols > width) break // doesn't fit

            // Shift existing cells right by neededCols to make room
            shiftCellsRight(col, neededCols)

            if (charW == 2) {
                cells[col] = Cell(character = char, attributes = attributes, width = 2)
                cells[col + 1] = Cell.CONTINUATION
                col += 2
            } else {
                cells[col] = Cell(character = char, attributes = attributes)
                col++
            }
        }
        return col
    }

    /**
     * Fills the entire line with the given character and attributes.
     *
     * If [char] is null, the line is filled with empty cells (using [Cell.EMPTY]).
     * Wide characters are not supported for fill — only single-width characters are used.
     */
    fun fill(char: Char? = null, attributes: CellAttributes = CellAttributes.DEFAULT) {
        val fillCell = if (char == null) {
            Cell.EMPTY
        } else {
            Cell(character = char, attributes = attributes)
        }
        cells.fill(fillCell)
    }

    /**
     * Clears the entire line, resetting all cells to [Cell.EMPTY].
     */
    fun clear() {
        cells.fill(Cell.EMPTY)
    }

    /**
     * Returns the text content of this line as a string.
     *
     * Continuation cells (trailing half of wide characters) are skipped.
     * The result is trimmed of trailing spaces.
     */
    fun getText(): String {
        val sb = StringBuilder(width)
        for (cell in cells) {
            if (!cell.isContinuation) {
                sb.append(cell.character)
            }
        }
        return sb.toString().trimEnd()
    }

    /**
     * Returns the raw text content of this line without trimming trailing spaces.
     */
    fun getRawText(): String {
        val sb = StringBuilder(width)
        for (cell in cells) {
            if (!cell.isContinuation) {
                sb.append(cell.character)
            }
        }
        return sb.toString()
    }

    /**
     * Creates a deep copy of this line.
     */
    fun copy(): TerminalLine {
        val newLine = TerminalLine(width)
        cells.copyInto(newLine.cells)
        return newLine
    }

    /**
     * Creates a new TerminalLine with a different width, copying cell content.
     *
     * If [newWidth] is larger, extra cells are filled with [Cell.EMPTY].
     * If [newWidth] is smaller, cells beyond the new width are dropped.
     * Wide characters that would be split at the new boundary are cleaned up.
     */
    fun copyWithWidth(newWidth: Int): TerminalLine {
        val newLine = TerminalLine(newWidth)
        val copyCount = minOf(width, newWidth)
        for (i in 0 until copyCount) {
            newLine.cells[i] = cells[i]
        }
        // Clean up a wide char that got split at the boundary
        if (copyCount < width && copyCount > 0 && newLine.cells[copyCount - 1].isContinuation) {
            newLine.cells[copyCount - 1] = Cell.EMPTY
        }
        if (copyCount < width && copyCount > 0 && newLine.cells[copyCount - 1].width == 2) {
            // The main cell of a wide char is at the boundary but continuation is cut off
            newLine.cells[copyCount - 1] = Cell.EMPTY
        }
        return newLine
    }
    
    // --- Private helpers ---

    /**
     * Cleans up the wide character pair if the cell at [col] is part of one.
     *
     * If [col] is a continuation cell (trailing half), the preceding main cell is replaced with EMPTY.
     * If [col] is a wide main cell (width == 2), the following continuation cell is replaced with EMPTY.
     *
     * This prevents orphaned halves of wide characters when overwriting a single column.
     */
    private fun cleanUpWideChar(col: Int) {
        if (col < 0 || col >= width) return

        val cell = cells[col]
        if (cell.isContinuation && col > 0) {
            // This is the trailing half — clear the main cell
            cells[col - 1] = Cell.EMPTY
        } else if (cell.width == 2 && col + 1 < width) {
            // This is the main cell — clear the continuation
            cells[col + 1] = Cell.EMPTY
        }
    }

    /**
     * Shifts cells from [startCol] onward to the right by [amount] positions.
     * Cells shifted beyond the line width are discarded.
     * Wide characters split by the truncation boundary are cleaned up.
     */
    private fun shiftCellsRight(startCol: Int, amount: Int) {
        // Before shifting, check if shifting will split a wide char at the right boundary
        val lastKeptSource = width - amount - 1
        if (lastKeptSource >= startCol && lastKeptSource < width) {
            val cell = cells[lastKeptSource]
            if (cell.width == 2 && lastKeptSource + 1 < width) {
                // This wide char's continuation would be pushed out; clear the main cell too
                cells[lastKeptSource] = Cell.EMPTY
                cells[lastKeptSource + 1] = Cell.EMPTY
            }
        }

        // Shift from right to left to avoid overwriting
        for (i in (width - 1) downTo (startCol + amount)) {
            cells[i] = cells[i - amount]
        }

        // Clear the insertion gap
        for (i in startCol until (startCol + amount).coerceAtMost(width)) {
            cells[i] = Cell.EMPTY
        }
    }

    private fun requireValidColumn(col: Int) {
        if (col < 0 || col >= width) {
            throw IndexOutOfBoundsException("Column $col is out of bounds for line width $width")
        }
    }
}
