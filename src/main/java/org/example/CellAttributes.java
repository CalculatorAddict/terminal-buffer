package org.example;

/**
 * Styling attributes attached to a cell.
 *
 * @param foreground foreground color, or {@link TerminalColor#DEFAULT} to inherit
 * @param background background color, or {@link TerminalColor#DEFAULT} to inherit
 * @param bold whether bold styling is enabled
 * @param italic whether italic styling is enabled
 * @param underline whether underline styling is enabled
 */
public record CellAttributes(
        TerminalColor foreground,
        TerminalColor background,
        boolean bold,
        boolean italic,
        boolean underline
) {
    /**
     * Default terminal attributes with inherited colors and no style flags.
     */
    public static final CellAttributes DEFAULT =
            new CellAttributes(TerminalColor.DEFAULT, TerminalColor.DEFAULT, false, false, false);
}
