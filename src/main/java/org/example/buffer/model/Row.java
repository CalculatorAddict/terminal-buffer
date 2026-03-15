package org.example.buffer.model;

public final class Row {
    private final Line line;
    private final int visualOffset;
    private final int screenWidth;

    public Row(Line line, int visualOffset, int screenWidth) {
        if (visualOffset < 0) {
            throw new IllegalArgumentException("visualOffset must be non-negative");
        }
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        this.line = line;
        this.visualOffset = visualOffset;
        this.screenWidth = screenWidth;
    }

    public Cell getCell(int visualCol) {
        if (visualCol < 0 || visualCol >= screenWidth) {
            return null;
        }
        return line.getCell(visualOffset + visualCol);
    }

    public int visualLength() {
        return Math.max(0, Math.min(screenWidth, line.visualLength() - visualOffset));
    }

    public String getString() {
        StringBuilder builder = new StringBuilder();
        int rowEnd = visualOffset + screenWidth;
        int currentCol = 0;
        for (int col = 0; col < line.visualLength() && currentCol < rowEnd; col++) {
            Cell cell = line.getCell(col);
            if (cell == null) {
                break;
            }
            int spanEnd = currentCol + cell.getColSpan();
            if (currentCol >= visualOffset && currentCol < rowEnd) {
                builder.append(cell.getChar());
            }
            col = spanEnd - 1;
            currentCol = spanEnd;
        }
        return builder.toString();
    }

    public int getVisualOffset() {
        return visualOffset;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public Line getLine() {
        return line;
    }
}
