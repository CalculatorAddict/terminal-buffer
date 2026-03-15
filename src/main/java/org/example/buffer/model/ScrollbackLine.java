package org.example.buffer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable physical row stored in scrollback.
 */
public final class ScrollbackLine implements Line {
    private final List<Cell> cells;
    private final int visualLength;
    private final String stringValue;
    private final boolean wrapped;

    /**
     * Freezes a mutable screen row into an immutable scrollback row.
     *
     * @param line screen row to copy
     */
    public ScrollbackLine(MutableLine line) {
        this.cells = List.copyOf(new ArrayList<>(line.getCells()));
        this.visualLength = line.visualLength();
        this.stringValue = line.getString();
        this.wrapped = line.isWrapped();
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int cellLength() {
        return cells.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int visualLength() {
        return visualLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString() {
        return stringValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWrapped() {
        return wrapped;
    }

    /**
     * Returns the immutable cell list for this row.
     *
     * @return immutable cells
     */
    public List<Cell> getCells() {
        return cells;
    }
}
