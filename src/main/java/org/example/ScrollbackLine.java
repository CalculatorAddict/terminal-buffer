package org.example;

import java.util.ArrayList;
import java.util.List;

public final class ScrollbackLine implements Line {
    private final List<Cell> cells;
    private final int visualLength;
    private final String stringValue;
    private final boolean wrapped;

    public ScrollbackLine(MutableLine line) {
        this.cells = List.copyOf(new ArrayList<>(line.getCells()));
        this.visualLength = line.visualLength();
        this.stringValue = line.getString();
        this.wrapped = line.isWrapped();
    }

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

    @Override
    public int cellLength() {
        return cells.size();
    }

    @Override
    public int visualLength() {
        return visualLength;
    }

    @Override
    public String getString() {
        return stringValue;
    }

    @Override
    public boolean isWrapped() {
        return wrapped;
    }

    public List<Cell> getCells() {
        return cells;
    }
}
