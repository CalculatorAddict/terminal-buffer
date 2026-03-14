package org.example;

public record CellAttributes(
        TerminalColor foreground,
        TerminalColor background,
        boolean bold,
        boolean italic,
        boolean underline
) {
    public static final CellAttributes DEFAULT =
            new CellAttributes(TerminalColor.DEFAULT, TerminalColor.DEFAULT, false, false, false);
}
