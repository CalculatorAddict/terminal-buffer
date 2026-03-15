package org.example.buffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.Line;
import org.example.buffer.model.ScrollbackLine;
import org.example.buffer.model.TerminalColor;
import org.example.buffer.render.AnsiRenderer;

/**
 * Small manual demo for exercising wrapping, attributes, cursor-relative line edits, wide characters,
 * and resize reflow.
 */
public class Demo {
    private static final AnsiRenderer ANSI_RENDERER = new AnsiRenderer();
    private static final String WRAPPING_TEXT = "Hello World! This is a long line that wraps.";
    private static final String CJK_TEXT = "\u754Ca\u6F22\u5B57";
    private static final CellAttributes BOLD_RED = new CellAttributes(
            TerminalColor.RED,
            TerminalColor.DEFAULT,
            true,
            false,
            false
    );
    private static final CellAttributes UNDERLINED_CYAN = new CellAttributes(
            TerminalColor.CYAN,
            TerminalColor.DEFAULT,
            false,
            false,
            true
    );

    public static void main(String[] args) {
        boolean ansi = Arrays.asList(args).contains("--ansi");
        TerminalBuffer buffer = new TerminalBuffer(10, 4, 20);
        printIntro(ansi);

        buffer.writeText(WRAPPING_TEXT);
        printSnapshot(
                1,
                "Wrap a long plain-text write",
                "writeText(\"" + WRAPPING_TEXT + "\")",
                "The earliest physical row moves into scrollback once the screen fills.",
                buffer,
                ansi
        );

        buffer.writeText("Bold red text", BOLD_RED);
        printSnapshot(
                2,
                "Append a one-off bold red segment",
                "writeText(\"Bold red text\", BOLD_RED)",
                "The per-call overload applies styling without changing the buffer's current attributes.",
                buffer,
                ansi
        );

        buffer.writeText(CJK_TEXT, UNDERLINED_CYAN);
        printSnapshot(
                3,
                "Append underlined cyan CJK text",
                "writeText(\"\\u754Ca\\u6F22\\u5B57\", UNDERLINED_CYAN)",
                "Plain mode renders wide cells as ASCII placeholders and prints the escaped code points below.",
                buffer,
                ansi
        );

        buffer.resize(6, 4);
        printSnapshot(
                4,
                "Resize to 6 columns and reflow",
                "resize(6, 4)",
                "Text and stored attributes are rewrapped together so styled spans still line up.",
                buffer,
                ansi
        );

        buffer.moveCursorUp(1);
        buffer.moveCursorLeft(4);
        buffer.fillLine('=');
        printSnapshot(
                5,
                "Fill the current cursor row",
                "moveCursorUp(1); moveCursorLeft(4); fillLine('=')",
                "This is the cursor-relative line fill operation described by the internship spec.",
                buffer,
                ansi
        );

        buffer.clearLine();
        printSnapshot(
                6,
                "Clear the current cursor row",
                "clearLine()",
                "This is the explicit fill-with-empty operation. The cursor stays on the cleared row.",
                buffer,
                ansi
        );
    }

    private static void printIntro(boolean ansi) {
        System.out.println("TerminalBuffer demo");
        if (ansi) {
            System.out.println("mode: ANSI rendering enabled; colors and text modifiers are shown directly.");
        } else {
            System.out.println("mode: plain text; use --ansi in a UTF-8 terminal to see colors and modifiers directly.");
        }
        System.out.println("legend: rows are wrapped in |...| so trailing spaces remain visible.");
        System.out.println();
    }

    private static void printSnapshot(
            int step,
            String title,
            String action,
            String note,
            TerminalBuffer buffer,
            boolean ansi
    ) {
        System.out.println(step + ". " + title);
        System.out.println("   action: " + action);
        System.out.println("   note: " + note);
        System.out.println("   state: size=" + buffer.getWidth() + "x" + buffer.getHeight()
                + " cursor=(" + buffer.getCursorCol() + "," + buffer.getCursorRow() + ")"
                + " current-attrs=" + describeAttributes(buffer.getCurrentAttributes())
                + " scrollback=" + buffer.getScrollback().size());
        printScreen(buffer, ansi);
        printScrollback(buffer, ansi);
        printStyledSpans(buffer);
        printWideCells(buffer);
        System.out.println();
    }

    private static void printScreen(TerminalBuffer buffer, boolean ansi) {
        System.out.println("   screen:");
        for (int row = 0; row < buffer.getHeight(); row++) {
            printIndexedLine(row, buffer.getHeight(), buffer.getLine(row), buffer.getWidth(), ansi);
        }
    }

    private static void printScrollback(TerminalBuffer buffer, boolean ansi) {
        System.out.println("   scrollback:");
        if (buffer.getScrollback().isEmpty()) {
            System.out.println("     (empty)");
            return;
        }
        for (int row = 0; row < buffer.getScrollback().size(); row++) {
            printIndexedLine(row, buffer.getScrollback().size(), buffer.getScrollbackLine(row), buffer.getWidth(), ansi);
        }
    }

    private static void printIndexedLine(int row, int totalRows, Line line, int width, boolean ansi) {
        int rowDigits = digitsForCount(totalRows);
        System.out.println("     " + padNumber(row, rowDigits) + " |" + formatLine(line, width, ansi) + "|");
    }

    private static void printStyledSpans(TerminalBuffer buffer) {
        List<String> spans = new ArrayList<>();
        for (int row = 0; row < buffer.getScrollback().size(); row++) {
            addStyledSpans(spans, "scrollback", row, buffer.getScrollbackLine(row), buffer.getWidth());
        }
        for (int row = 0; row < buffer.getHeight(); row++) {
            addStyledSpans(spans, "screen", row, buffer.getLine(row), buffer.getWidth());
        }

        System.out.println("   styled spans:");
        if (spans.isEmpty()) {
            System.out.println("     none");
            return;
        }
        for (String span : spans) {
            System.out.println("     " + span);
        }
    }

    private static void printWideCells(TerminalBuffer buffer) {
        List<String> entries = new ArrayList<>();
        for (int row = 0; row < buffer.getScrollback().size(); row++) {
            addWideCells(entries, "scrollback", row, buffer.getScrollbackLine(row), buffer.getWidth());
        }
        for (int row = 0; row < buffer.getHeight(); row++) {
            addWideCells(entries, "screen", row, buffer.getLine(row), buffer.getWidth());
        }

        System.out.println("   wide cells:");
        if (entries.isEmpty()) {
            System.out.println("     none");
            return;
        }
        for (String entry : entries) {
            System.out.println("     " + entry);
        }
    }

    private static void addStyledSpans(List<String> spans, String area, int row, Line line, int width) {
        for (AttributeRun run : collectAttributeRuns(line, width)) {
            spans.add(area + " row " + row + " " + formatColumnRange(run.startCol(), run.endColExclusive())
                    + " -> " + describeAttributes(run.attributes()));
        }
    }

    private static List<AttributeRun> collectAttributeRuns(Line line, int width) {
        List<AttributeRun> runs = new ArrayList<>();
        CellAttributes activeAttributes = CellAttributes.DEFAULT;
        int runStart = -1;
        int col = 0;

        while (col < width) {
            Cell cell = line.getCell(col);
            CellAttributes cellAttributes = cell == null ? CellAttributes.DEFAULT : cell.getAttributes();
            int span = cell == null ? 1 : cell.getColSpan();

            if (!activeAttributes.equals(cellAttributes)) {
                if (!CellAttributes.DEFAULT.equals(activeAttributes)) {
                    runs.add(new AttributeRun(runStart, col, activeAttributes));
                }
                if (!CellAttributes.DEFAULT.equals(cellAttributes)) {
                    runStart = col;
                }
                activeAttributes = cellAttributes;
            }

            col += span;
        }

        if (!CellAttributes.DEFAULT.equals(activeAttributes)) {
            runs.add(new AttributeRun(runStart, width, activeAttributes));
        }
        return runs;
    }

    private static void addWideCells(List<String> entries, String area, int row, Line line, int width) {
        int col = 0;
        while (col < width) {
            Cell cell = line.getCell(col);
            if (cell == null) {
                col++;
                continue;
            }
            if (cell.getColSpan() == 2) {
                entries.add(area + " row " + row + " cols " + col + "-" + (col + 1)
                        + " -> " + unicodeCodePoint(cell.getChar()) + " (" + unicodeLiteral(cell.getChar()) + ")");
            }
            col += cell.getColSpan();
        }
    }

    private static String formatColumnRange(int startCol, int endColExclusive) {
        int endColInclusive = endColExclusive - 1;
        if (startCol == endColInclusive) {
            return "col " + startCol;
        }
        return "cols " + startCol + "-" + endColInclusive;
    }

    private static String describeAttributes(CellAttributes attributes) {
        if (attributes == null || CellAttributes.DEFAULT.equals(attributes)) {
            return "default";
        }

        List<String> parts = new ArrayList<>();
        if (attributes.foreground() != TerminalColor.DEFAULT) {
            parts.add("fg=" + colorName(attributes.foreground()));
        }
        if (attributes.background() != TerminalColor.DEFAULT) {
            parts.add("bg=" + colorName(attributes.background()));
        }
        if (attributes.bold()) {
            parts.add("bold");
        }
        if (attributes.italic()) {
            parts.add("italic");
        }
        if (attributes.underline()) {
            parts.add("underline");
        }
        return String.join(", ", parts);
    }

    private static String colorName(TerminalColor color) {
        return color.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String unicodeCodePoint(char c) {
        return String.format(Locale.ROOT, "U+%04X", (int) c);
    }

    private static String unicodeLiteral(char c) {
        return String.format(Locale.ROOT, "\\u%04X", (int) c);
    }

    private static int digitsForCount(int count) {
        return Integer.toString(Math.max(count - 1, 0)).length();
    }

    private static String padNumber(int value, int width) {
        String text = Integer.toString(value);
        if (text.length() >= width) {
            return text;
        }
        StringBuilder builder = new StringBuilder(width);
        while (builder.length() < width - text.length()) {
            builder.append(' ');
        }
        builder.append(text);
        return builder.toString();
    }

    private static String formatLine(Line line, int width, boolean ansi) {
        if (ansi) {
            return ANSI_RENDERER.renderLine(line, width);
        }

        return formatPlainLine(line, width);
    }

    private static String formatPlainLine(Line line, int width) {
        StringBuilder builder = new StringBuilder(width);
        int col = 0;
        while (col < width) {
            Cell cell = line.getCell(col);
            if (cell == null) {
                builder.append(' ');
                col++;
                continue;
            }
            if (cell.getColSpan() == 2) {
                builder.append('[');
                col += 2;
                if (col <= width) {
                    builder.append(']');
                }
                continue;
            }
            builder.append(cell.getChar());
            col += cell.getColSpan();
        }
        if (builder.length() > width) {
            return builder.substring(0, width);
        }
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private record AttributeRun(int startCol, int endColExclusive, CellAttributes attributes) {
    }
}
