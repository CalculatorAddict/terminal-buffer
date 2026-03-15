package org.example.buffer.cursor;

/**
 * Mutable cursor state for a terminal buffer.
 */
public final class CursorState {
    private int cursorCol;
    private int cursorRow;
    private int width;
    private int height;

    public CursorState(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public int getCursorCol() {
        return cursorCol;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    /**
     * Sets the cursor position.
     *
     * <p>The column is clamped to the inclusive range {@code [0, width]}. The value {@code width} is valid and
     * represents the pending-wrap state produced by writing exactly to the end of a row.</p>
     */
    public void setCursor(int col, int row) {
        cursorCol = clamp(col, 0, width);
        cursorRow = clamp(row, 0, height - 1);
    }

    public void moveCursor(int dcol, int drow) {
        setCursor(cursorCol + dcol, cursorRow + drow);
    }

    public void setCursorColUnchecked(int cursorCol) {
        this.cursorCol = cursorCol;
    }

    public void setCursorRowUnchecked(int cursorRow) {
        this.cursorRow = cursorRow;
    }

    public int clampRow(int row) {
        return clamp(row, 0, height - 1);
    }

    public void setDimensions(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        this.width = width;
        this.height = height;
        setCursor(cursorCol, cursorRow);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
