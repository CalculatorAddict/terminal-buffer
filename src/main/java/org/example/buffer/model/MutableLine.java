package org.example.buffer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable physical row stored on the screen.
 */
public class MutableLine implements Line {
    private final List<Cell> cells;
    private boolean wrapped;

    /**
     * Creates an empty line with no wrap continuation.
     */
    public MutableLine() {
        this.cells = new ArrayList<>();
    }

    /**
     * Creates a line from the provided cells without marking it as wrapped.
     *
     * @param cells cells to copy into the line
     */
    public MutableLine(List<Cell> cells) {
        this(cells, false);
    }

    /**
     * Creates a line from the provided cells.
     *
     * @param cells cells to copy into the line
     * @param wrapped whether this row continues onto the next physical row
     */
    public MutableLine(List<Cell> cells, boolean wrapped) {
        this.cells = new ArrayList<>(cells);
        this.wrapped = wrapped;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public Cell getCell(int visualCol) {
        if (visualCol < 0) {
            return null;
        }
        int currentCol = 0;
        for (Cell cell : cells) {
            int nextCol = currentCol + cell.getColSpan();
            if (visualCol < nextCol) {
                return cell;
            }
            currentCol = nextCol;
        }
        return null;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public int cellLength() {
        return cells.size();
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public int visualLength() {
        int length = 0;
        for (Cell cell : cells) {
            length += cell.getColSpan();
        }
        return length;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public String getString() {
        StringBuilder builder = new StringBuilder(cells.size());
        for (Cell cell : cells) {
            builder.append(cell.getChar());
        }
        return builder.toString();
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public boolean isWrapped() {
        return wrapped;
    }

    /**
     * Returns how many physical rows this line would occupy at the given width.
     *
     * @param screenWidth screen width used for wrapping
     * @return the number of physical rows required, always at least {@code 1}
     */
    public int physicalLineCount(int screenWidth) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        return Math.max(1, (visualLength() + screenWidth - 1) / screenWidth);
    }

    /**
     * Exposes the mutable backing cell list.
     *
     * @return the mutable list of cells
     */
    public List<Cell> getCells() {
        return cells;
    }

    /**
     * Removes all cells from the line.
     */
    public void clear() {
        cells.clear();
    }

    /**
     * Appends a cell to the end of the line.
     *
     * @param cell cell to append
     */
    public void addCell(Cell cell) {
        cells.add(cell);
    }

    /**
     * Inserts a cell at the given cell index.
     *
     * @param index insertion point in cell coordinates
     * @param cell cell to insert
     */
    public void addCell(int index, Cell cell) {
        cells.add(index, cell);
    }

    /**
     * Removes and returns the cell at the given cell index.
     *
     * @param index cell index to remove
     * @return the removed cell
     */
    public Cell removeCell(int index) {
        return cells.remove(index);
    }

    /**
     * Returns a cell by stored cell index rather than visual column.
     *
     * @param index 0-based cell index
     * @return the cell at that index
     */
    public Cell getCellByIndex(int index) {
        return cells.get(index);
    }

    /**
     * Replaces the cell at the given cell index.
     *
     * @param index cell index to replace
     * @param cell replacement cell
     */
    public void setCell(int index, Cell cell) {
        cells.set(index, cell);
    }

    /**
     * Marks whether this physical row continues onto the next physical row.
     *
     * @param wrapped {@code true} when the next row is a continuation of this one
     */
    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Converts a visual column into a cell-list insertion index.
     *
     * @param visualCol 0-based visual column
     * @return the cell index at which a write or insert should occur
     */
    public int visualColToCellIndex(int visualCol) {
        if (visualCol <= 0) {
            return 0;
        }
        int currentCol = 0;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (visualCol <= currentCol) {
                return i;
            }
            int nextCol = currentCol + cell.getColSpan();
            if (visualCol < nextCol) {
                return i;
            }
            if (visualCol == nextCol) {
                return i + 1;
            }
            currentCol = nextCol;
        }
        return cells.size();
    }

    /**
     * Returns a shallow copy of this line, preserving its wrap flag.
     *
     * @return copied line
     */
    public MutableLine copy() {
        return new MutableLine(cells, wrapped);
    }
}
