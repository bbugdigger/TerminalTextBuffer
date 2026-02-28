package terminal

/**
 * Represents a single line (row) in the terminal buffer.
 *
 * A line is a fixed-width array of [Cell] objects. It provides operations
 * for reading and modifying cells, writing/inserting text, and clearing content.
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
     * @throws IndexOutOfBoundsException if [col] is outside [0, width).
     */
    fun setCell(col: Int, cell: Cell) {
        requireValidColumn(col)
        cells[col] = cell
    }

    /**
     * Writes text starting at [startCol] using the given [attributes], overwriting existing content.
     *
     * Characters are placed one per column starting at [startCol]. Characters that would
     * be placed beyond the line width are silently dropped.
     *
     * @return The column position after the last written character (i.e., the new cursor column).
     *         This value may equal [width] if writing reached the end of the line.
     */
    fun writeText(startCol: Int, text: String, attributes: CellAttributes): Int {
        var col = startCol
        for (char in text) {
            if (col >= width) break
            cells[col] = Cell(character = char, attributes = attributes)
            col++
        }
        return col
    }

    /**
     * Inserts text at [startCol] using the given [attributes], shifting existing content to the right.
     *
     * Existing cells from [startCol] onward are shifted right to make room for the inserted text.
     * Any cells shifted beyond the line width are discarded (truncated).
     *
     * @return The column position after the last inserted character (i.e., the new cursor column).
     *         This value may equal [width] if insertion reached the end of the line.
     */
    fun insertText(startCol: Int, text: String, attributes: CellAttributes): Int {
        val insertLength = text.length.coerceAtMost(width - startCol)
        if (insertLength <= 0) return startCol

        // Shift existing cells to the right to make room.
        // We shift from the right end to avoid overwriting cells we haven't moved yet.
        val shiftEnd = (width - 1)
        val shiftStart = startCol + insertLength
        for (i in shiftEnd downTo shiftStart) {
            cells[i] = cells[i - insertLength]
        }

        // Place the inserted characters.
        var col = startCol
        for (i in 0 until insertLength) {
            cells[col] = Cell(character = text[i], attributes = attributes)
            col++
        }
        return col
    }

    /**
     * Fills the entire line with the given character and attributes.
     *
     * If [char] is null, the line is filled with empty cells (using [Cell.EMPTY]).
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

    private fun requireValidColumn(col: Int) {
        if (col < 0 || col >= width) {
            throw IndexOutOfBoundsException("Column $col is out of bounds for line width $width")
        }
    }
}
