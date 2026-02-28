package terminal

/**
 * Represents the complete set of visual attributes for a terminal cell.
 *
 * Combines foreground color, background color, and text style into a single unit.
 * This is what gets stamped onto each [Cell] when text is written to the buffer.
 */
data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.DEFAULT,
    val background: TerminalColor = TerminalColor.DEFAULT,
    val style: TextStyle = TextStyle.PLAIN,
) {
    companion object {
        /** Default attributes: default colors, no styling. */
        val DEFAULT = CellAttributes()
    }
}
