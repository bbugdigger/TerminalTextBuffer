package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalColorTest {

    @Test
    fun `DEFAULT color is not bright`() {
        assertFalse(TerminalColor.DEFAULT.isBright)
    }

    @Test
    fun `DEFAULT color is default`() {
        assertTrue(TerminalColor.DEFAULT.isDefault)
    }

    @Test
    fun `normal colors are not bright`() {
        val normalColors = listOf(
            TerminalColor.BLACK, TerminalColor.RED, TerminalColor.GREEN, TerminalColor.YELLOW,
            TerminalColor.BLUE, TerminalColor.MAGENTA, TerminalColor.CYAN, TerminalColor.WHITE,
        )
        for (color in normalColors) {
            assertFalse(color.isBright, "$color should not be bright")
        }
    }

    @Test
    fun `bright colors are bright`() {
        val brightColors = listOf(
            TerminalColor.BRIGHT_BLACK, TerminalColor.BRIGHT_RED, TerminalColor.BRIGHT_GREEN,
            TerminalColor.BRIGHT_YELLOW, TerminalColor.BRIGHT_BLUE, TerminalColor.BRIGHT_MAGENTA,
            TerminalColor.BRIGHT_CYAN, TerminalColor.BRIGHT_WHITE,
        )
        for (color in brightColors) {
            assertTrue(color.isBright, "$color should be bright")
        }
    }

    @Test
    fun `non-DEFAULT colors are not default`() {
        TerminalColor.entries
            .filter { it != TerminalColor.DEFAULT }
            .forEach { assertFalse(it.isDefault, "$it should not be default") }
    }

    @Test
    fun `there are exactly 17 terminal colors`() {
        // 1 DEFAULT + 8 normal + 8 bright
        assertEquals(17, TerminalColor.entries.size)
    }
}
