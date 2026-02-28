package terminal

/**
 * Represents a single character cell in the terminal grid.
 *
 * Each cell holds a character, its visual attributes, and how many columns
 * it occupies. Most characters occupy 1 column, but wide characters
 * (e.g., CJK ideographs, some emoji) occupy 2 columns.
 *
 * When a wide character is placed, it occupies its own cell plus a continuation
 * cell to the right. The continuation cell should use [CONTINUATION] to indicate
 * it is the trailing half of a wide character.
 *
 * @property character The character stored in this cell. A null character ('\u0000') represents an empty cell.
 * @property attributes The visual attributes (colors and style) for this cell.
 * @property width The number of columns this character occupies (1 for normal, 2 for wide characters).
 */
data class Cell(
    val character: Char = EMPTY_CHAR,
    val attributes: CellAttributes = CellAttributes.DEFAULT,
    val width: Int = 1,
) {
    companion object {
        /** The character value used to represent an empty cell. */
        const val EMPTY_CHAR: Char = ' '

        /** An empty cell with default attributes. */
        val EMPTY = Cell()

        /**
         * A continuation cell, used as a placeholder for the trailing half of a wide character.
         * Its width is 0 to indicate it does not represent a standalone character.
         */
        val CONTINUATION = Cell(character = EMPTY_CHAR, width = 0)
    }

    /** Returns true if this cell is empty (contains a space with default attributes). */
    val isEmpty: Boolean
        get() = character == EMPTY_CHAR && attributes == CellAttributes.DEFAULT

    /** Returns true if this cell is a continuation placeholder for a wide character. */
    val isContinuation: Boolean
        get() = width == 0
}
