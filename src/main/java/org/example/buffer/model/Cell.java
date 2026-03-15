package org.example.buffer.model;

/**
 * Single rendered cell within the terminal buffer.
 */
public class Cell {
    private final char c;
    private final CellAttributes attributes;
    private final int colSpan;

    /**
     * Creates a cell.
     *
     * @param c the character stored in the cell
     * @param attributes the styling attributes applied to the cell
     * @param colSpan the visual width of the cell; must be {@code 1} or {@code 2}
     */
    public Cell(char c, CellAttributes attributes, int colSpan) {
        if (colSpan != 1 && colSpan != 2) {
            throw new IllegalArgumentException("colSpan must be 1 or 2");
        }
        this.c = c;
        this.attributes = attributes;
        this.colSpan = colSpan;
    }

    /**
     * Returns the character stored in this cell.
     *
     * @return the cell character
     */
    public char getChar() {
        return c;
    }

    /**
     * Returns the styling attributes for this cell.
     *
     * @return the cell attributes
     */
    public CellAttributes getAttributes() {
        return attributes;
    }

    /**
     * Returns the number of visual columns occupied by this cell.
     *
     * @return {@code 1} for normal cells or {@code 2} for wide cells
     */
    public int getColSpan() {
        return colSpan;
    }
}
