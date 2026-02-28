package terminal

/**
 * Represents text styling flags for a terminal cell.
 *
 * Each flag is independent and can be combined freely.
 * For example, text can be both bold and underlined simultaneously.
 */
data class TextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    companion object {
        /** A style with no flags set. */
        val PLAIN = TextStyle()
    }

    /**
     * Returns true if any style flag is set.
     */
    fun hasAny(): Boolean = bold || italic || underline
}
