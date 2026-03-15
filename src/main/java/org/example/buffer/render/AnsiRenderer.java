package org.example.buffer.render;

import java.util.ArrayList;
import java.util.List;

import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.Line;
import org.example.buffer.model.TerminalColor;

/**
 * Formats buffer lines as ANSI SGR sequences for display in a terminal.
 */
public final class AnsiRenderer {
    private static final String ESC = "\u001B[";
    private static final String RESET = ESC + "0m";

    public String renderLine(Line line, int width) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        if (width < 0) {
            throw new IllegalArgumentException("width must not be negative");
        }

        StringBuilder builder = new StringBuilder(width + 16);
        CellAttributes activeAttributes = CellAttributes.DEFAULT;
        int col = 0;

        while (col < width) {
            Cell cell = line.getCell(col);
            CellAttributes cellAttributes = cell == null ? CellAttributes.DEFAULT : normalize(cell.getAttributes());
            if (!activeAttributes.equals(cellAttributes)) {
                builder.append(toSgr(cellAttributes));
                activeAttributes = cellAttributes;
            }

            if (cell == null) {
                builder.append(' ');
                col++;
                continue;
            }

            builder.append(cell.getChar());
            col += cell.getColSpan();
        }

        if (!CellAttributes.DEFAULT.equals(activeAttributes)) {
            builder.append(RESET);
        }
        return builder.toString();
    }

    private static String toSgr(CellAttributes attributes) {
        CellAttributes normalized = normalize(attributes);
        if (CellAttributes.DEFAULT.equals(normalized)) {
            return RESET;
        }

        List<String> codes = new ArrayList<>();
        codes.add("0");
        if (normalized.bold()) {
            codes.add("1");
        }
        if (normalized.italic()) {
            codes.add("3");
        }
        if (normalized.underline()) {
            codes.add("4");
        }
        codes.add(foregroundCode(normalized.foreground()));
        codes.add(backgroundCode(normalized.background()));
        return ESC + String.join(";", codes) + "m";
    }

    private static CellAttributes normalize(CellAttributes attributes) {
        if (attributes == null) {
            return CellAttributes.DEFAULT;
        }
        TerminalColor foreground = attributes.foreground() == null ? TerminalColor.DEFAULT : attributes.foreground();
        TerminalColor background = attributes.background() == null ? TerminalColor.DEFAULT : attributes.background();
        if (foreground == attributes.foreground() && background == attributes.background()) {
            return attributes;
        }
        return new CellAttributes(
                foreground,
                background,
                attributes.bold(),
                attributes.italic(),
                attributes.underline()
        );
    }

    private static String foregroundCode(TerminalColor color) {
        return switch (color) {
            case DEFAULT -> "39";
            case BLACK -> "30";
            case RED -> "31";
            case GREEN -> "32";
            case YELLOW -> "33";
            case BLUE -> "34";
            case MAGENTA -> "35";
            case CYAN -> "36";
            case WHITE -> "37";
            case BRIGHT_BLACK -> "90";
            case BRIGHT_RED -> "91";
            case BRIGHT_GREEN -> "92";
            case BRIGHT_YELLOW -> "93";
            case BRIGHT_BLUE -> "94";
            case BRIGHT_MAGENTA -> "95";
            case BRIGHT_CYAN -> "96";
            case BRIGHT_WHITE -> "97";
        };
    }

    private static String backgroundCode(TerminalColor color) {
        return switch (color) {
            case DEFAULT -> "49";
            case BLACK -> "40";
            case RED -> "41";
            case GREEN -> "42";
            case YELLOW -> "43";
            case BLUE -> "44";
            case MAGENTA -> "45";
            case CYAN -> "46";
            case WHITE -> "47";
            case BRIGHT_BLACK -> "100";
            case BRIGHT_RED -> "101";
            case BRIGHT_GREEN -> "102";
            case BRIGHT_YELLOW -> "103";
            case BRIGHT_BLUE -> "104";
            case BRIGHT_MAGENTA -> "105";
            case BRIGHT_CYAN -> "106";
            case BRIGHT_WHITE -> "107";
        };
    }
}
