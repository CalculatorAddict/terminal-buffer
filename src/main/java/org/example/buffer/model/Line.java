package org.example.buffer.model;

/**
 * Read-only view of a logical or physical line of cells.
 */
public interface Line {
    /**
     * Returns the cell covering the given visual column.
     *
     * @param visualCol 0-based visual column within the line
     * @return the covering cell, or {@code null} when the column is outside the line
     */
    Cell getCell(int visualCol);

    /**
     * Returns the number of stored cells in this line.
     *
     * @return the number of cells
     */
    int cellLength();

    /**
     * Returns the visual width of the line after accounting for wide characters.
     *
     * @return the total number of occupied columns
     */
    int visualLength();

    /**
     * Returns the characters in this line without padding trailing empty columns.
     *
     * @return the line contents as plain text
     */
    String getString();

    /**
     * Reports whether this physical row continues onto the next physical row.
     *
     * @return {@code true} when the next physical row belongs to the same logical line
     */
    boolean isWrapped();
}
