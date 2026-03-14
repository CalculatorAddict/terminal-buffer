package org.example;

public class Cell {
    private final char c;
    private final CellAttributes attributes;
    private final int colSpan;

    public Cell(char c, CellAttributes attributes, int colSpan) {
        if (colSpan != 1 && colSpan != 2) {
            throw new IllegalArgumentException("colSpan must be 1 or 2");
        }
        this.c = c;
        this.attributes = attributes;
        this.colSpan = colSpan;
    }

    public char getChar() {
        return c;
    }

    public CellAttributes getAttributes() {
        return attributes;
    }

    public int getColSpan() {
        return colSpan;
    }
}
