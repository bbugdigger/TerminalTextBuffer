package terminal

/**
 * Represents a stream selection range in the terminal buffer.
 *
 * The range is always normalized so that the start position is before or equal
 * to the end position. Rows use absolute addressing (row 0 is the oldest
 * scrollback line). The end column is exclusive — it is one past the last
 * selected column, forming a half-open range `[startCol, endCol)` on the
 * end row.
 *
 * @property startRow The absolute row of the selection start (inclusive).
 * @property startCol The column of the selection start (inclusive).
 * @property endRow The absolute row of the selection end (inclusive).
 * @property endCol The column one past the last selected column on [endRow] (exclusive).
 */
data class SelectionRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
)
