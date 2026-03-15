package org.example.buffer;

import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.Line;
import org.example.buffer.model.ScrollbackLine;
import org.example.buffer.model.TerminalColor;

public class Demo {
    public static void main(String[] args) {
        TerminalBuffer buffer = new TerminalBuffer(10, 4, 20);

        buffer.writeText("Hello World! This is a long line that wraps.");
        printScreen("=== Screen after wrap ===", buffer);
        printScrollback("=== Scrollback after wrap ===", buffer);

        buffer.setAttributes(new CellAttributes(
                TerminalColor.RED,
                TerminalColor.DEFAULT,
                true,
                false,
                false
        ));
        buffer.writeText("Bold red text");
        printScreen("=== Screen after bold red text ===", buffer);

        buffer.writeText("\u754Ca\u6F22\u5B57");
        printScreen("=== Screen after wide chars ===", buffer);

        buffer.resize(6, 4);
        printScreen("=== Screen after resize reflow ===", buffer);
    }

    private static void printScreen(String label, TerminalBuffer buffer) {
        System.out.println(label);
        for (int row = 0; row < buffer.getHeight(); row++) {
            System.out.println(formatLine(buffer.getLine(row), buffer.getWidth()));
        }
    }

    private static void printScrollback(String label, TerminalBuffer buffer) {
        System.out.println(label);
        if (buffer.getScrollback().isEmpty()) {
            System.out.println("(empty)");
            return;
        }
        for (ScrollbackLine line : buffer.getScrollback()) {
            System.out.println(formatLine(line, buffer.getWidth()));
        }
    }

    private static String formatLine(Line line, int width) {
        StringBuilder builder = new StringBuilder(width);
        int col = 0;
        while (col < width) {
            Cell cell = line.getCell(col);
            if (cell == null) {
                builder.append(' ');
                col++;
                continue;
            }
            builder.append(cell.getChar());
            col += cell.getColSpan();
            if (cell.getColSpan() == 2 && col <= width) {
                builder.append(' ');
            }
        }
        if (builder.length() > width) {
            return builder.substring(0, width);
        }
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }
}
