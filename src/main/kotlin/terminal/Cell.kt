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

        /**
         * Returns the display width (in terminal columns) of the given character.
         *
         * Wide characters (CJK ideographs, Hangul syllables, fullwidth forms, etc.)
         * occupy 2 columns. All other printable characters occupy 1 column.
         *
         * This is a simplified implementation covering the most common wide character
         * ranges defined by Unicode East Asian Width (W and F categories). A production
         * terminal emulator would use the full Unicode East Asian Width table or ICU.
         */
        fun charWidth(ch: Char): Int {
            val code = ch.code
            return if (isWideCharacter(code)) 2 else 1
        }

        /**
         * Returns true if the character at the given code point is considered
         * wide (occupies 2 terminal columns).
         */
        private fun isWideCharacter(code: Int): Boolean {
            return code in 0x1100..0x115F ||    // Hangul Jamo
                    code == 0x2329 || code == 0x232A || // Left/Right-Pointing Angle Bracket
                    code in 0x2E80..0x303E ||       // CJK Radicals, Kangxi Radicals, CJK Symbols
                    code in 0x3040..0x33BF ||       // Hiragana, Katakana, Bopomofo, Hangul Compat Jamo, Kanbun, CJK Strokes
                    code in 0x33C0..0x33FF ||       // CJK Compatibility
                    code in 0x3400..0x4DBF ||       // CJK Unified Ideographs Extension A
                    code in 0x4E00..0x9FFF ||       // CJK Unified Ideographs
                    code in 0xA000..0xA4CF ||       // Yi Syllables, Yi Radicals
                    code in 0xAC00..0xD7AF ||       // Hangul Syllables
                    code in 0xF900..0xFAFF ||       // CJK Compatibility Ideographs
                    code in 0xFE10..0xFE19 ||       // Vertical Forms
                    code in 0xFE30..0xFE6F ||       // CJK Compatibility Forms, Small Form Variants
                    code in 0xFF01..0xFF60 ||        // Fullwidth Forms
                    code in 0xFFE0..0xFFE6          // Fullwidth Signs
        }
    }

    /** Returns true if this cell is empty (contains a space with default attributes). */
    val isEmpty: Boolean
        get() = character == EMPTY_CHAR && attributes == CellAttributes.DEFAULT

    /** Returns true if this cell is a continuation placeholder for a wide character. */
    val isContinuation: Boolean
        get() = width == 0
}
