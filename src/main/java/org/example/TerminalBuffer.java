package org.example;

import java.util.ArrayList;
import java.util.List;

public class TerminalBuffer {
    private final List<MutableLine> screen;
    private final List<ScrollbackLine> scrollback;
    private int cursorCol;
    private int cursorRow;
    private CellAttributes currentAttributes;
    private int width;
    private int height;
    private final int maxScrollbackSize;

    public TerminalBuffer(int width, int height, int maxScrollbackSize) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        if (maxScrollbackSize < 0) {
            throw new IllegalArgumentException("maxScrollbackSize must be non-negative");
        }
        this.width = width;
        this.height = height;
        this.maxScrollbackSize = maxScrollbackSize;
        this.screen = new ArrayList<>(height);
        this.scrollback = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        this.currentAttributes = CellAttributes.DEFAULT;
    }

    public void setAttributes(CellAttributes attributes) {
        this.currentAttributes = attributes == null ? CellAttributes.DEFAULT : attributes;
    }

    public int getCursorCol() {
        return cursorCol;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    public void setCursor(int col, int row) {
        cursorCol = clamp(col, 0, width - 1);
        cursorRow = clamp(row, 0, height - 1);
    }

    public void moveCursor(int dcol, int drow) {
        setCursor(cursorCol + dcol, cursorRow + drow);
    }

    public void writeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            writeCell(new Cell(text.charAt(i), currentAttributes, 1));
        }
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            insertCell(cursorRow, cursorCol, new Cell(text.charAt(i), currentAttributes, 1));
            advanceCursor(1);
        }
    }

    public void fillLine(int row, char c) {
        int clampedRow = clamp(row, 0, height - 1);
        MutableLine line = new MutableLine();
        for (int col = 0; col < width; col++) {
            line.addCell(new Cell(c, currentAttributes, 1));
        }
        screen.set(clampedRow, line);
    }

    public void insertEmptyLineAtBottom() {
        scrollUp();
    }

    public void clearScreen() {
        screen.clear();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        setCursor(cursorCol, cursorRow);
    }

    public void clearAll() {
        clearScreen();
        scrollback.clear();
    }

    public Cell getCell(int col, int row) {
        return screen.get(clamp(row, 0, height - 1)).getCell(clamp(col, 0, width - 1));
    }

    public Cell getScrollbackCell(int col, int scrollbackRow) {
        if (scrollback.isEmpty()) {
            return null;
        }
        return scrollback.get(clamp(scrollbackRow, 0, scrollback.size() - 1)).getCell(Math.max(0, col));
    }

    public CellAttributes getAttributes(int col, int row) {
        Cell cell = getCell(col, row);
        return cell == null ? CellAttributes.DEFAULT : cell.getAttributes();
    }

    public Line getLine(int row) {
        return screen.get(clamp(row, 0, height - 1));
    }

    public ScrollbackLine getScrollbackLine(int scrollbackRow) {
        if (scrollback.isEmpty()) {
            return null;
        }
        return scrollback.get(clamp(scrollbackRow, 0, scrollback.size() - 1));
    }

    public String getScreenString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < screen.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(screen.get(i).getString());
        }
        return builder.toString();
    }

    public String getFullString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < scrollback.size(); i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(scrollback.get(i).getString());
        }
        for (MutableLine line : screen) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line.getString());
        }
        return builder.toString();
    }

    public List<MutableLine> getScreen() {
        return screen;
    }

    public List<ScrollbackLine> getScrollback() {
        return scrollback;
    }

    public CellAttributes getCurrentAttributes() {
        return currentAttributes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxScrollbackSize() {
        return maxScrollbackSize;
    }

    private void writeCell(Cell cell) {
        normalizeCursorForWrite();
        MutableLine line = screen.get(cursorRow);
        padLineToColumn(line, cursorCol);
        int cellIndex = line.visualColToCellIndex(cursorCol);
        if (cellIndex < line.cellLength()) {
            line.setCell(cellIndex, cell);
        } else {
            line.addCell(cell);
        }
        advanceCursor(cell.getColSpan());
    }

    private void insertCell(int row, int visualCol, Cell cell) {
        MutableLine line = screen.get(row);
        padLineToColumn(line, visualCol);
        int cellIndex = line.visualColToCellIndex(visualCol);
        line.addCell(cellIndex, cell);
        if (line.visualLength() > width) {
            Cell overflow = line.removeCell(line.cellLength() - 1);
            int nextRow = row + 1;
            if (nextRow >= height) {
                scrollUp();
                nextRow = height - 1;
                row = row - 1;
            }
            insertCell(nextRow, 0, overflow);
        }
    }

    private void padLineToColumn(MutableLine line, int visualCol) {
        while (line.visualLength() < visualCol) {
            line.addCell(new Cell(' ', currentAttributes, 1));
        }
    }

    private void advanceCursor(int span) {
        cursorCol += span;
        while (cursorCol >= width) {
            cursorCol -= width;
            if (cursorRow == height - 1) {
                scrollUp();
            } else {
                cursorRow++;
            }
        }
    }

    private void normalizeCursorForWrite() {
        if (cursorCol >= width) {
            advanceCursor(0);
        }
    }

    private void scrollUp() {
        if (!screen.isEmpty()) {
            scrollback.add(new ScrollbackLine(screen.remove(0)));
            if (scrollback.size() > maxScrollbackSize) {
                scrollback.remove(0);
            }
            screen.add(new MutableLine());
        }
        cursorRow = height - 1;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
