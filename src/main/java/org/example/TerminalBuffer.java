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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
