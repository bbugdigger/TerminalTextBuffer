package terminal

/**
 * Represents the 16 standard ANSI terminal colors plus a default color.
 *
 * DEFAULT indicates that the terminal's default foreground or background color should be used.
 * The 8 normal colors and their bright variants correspond to the standard ANSI color codes 0-15.
 */
enum class TerminalColor {
    DEFAULT,

    // Normal colors (ANSI codes 0-7)
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,

    // Bright colors (ANSI codes 8-15)
    BRIGHT_BLACK,
    BRIGHT_RED,
    BRIGHT_GREEN,
    BRIGHT_YELLOW,
    BRIGHT_BLUE,
    BRIGHT_MAGENTA,
    BRIGHT_CYAN,
    BRIGHT_WHITE;

    /**
     * Returns true if this is one of the bright color variants.
     */
    val isBright: Boolean
        get() = ordinal >= BRIGHT_BLACK.ordinal

    /**
     * Returns true if this is the default (unset) color.
     */
    val isDefault: Boolean
        get() = this == DEFAULT
}