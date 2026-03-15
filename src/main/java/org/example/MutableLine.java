package org.example;

import java.util.ArrayList;
import java.util.List;

public class MutableLine implements Line {
    private final List<Cell> cells;
    private boolean wrapped;

    public MutableLine() {
        this.cells = new ArrayList<>();
    }

    public MutableLine(List<Cell> cells) {
        this(cells, false);
    }

    public MutableLine(List<Cell> cells, boolean wrapped) {
        this.cells = new ArrayList<>(cells);
        this.wrapped = wrapped;
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
        int length = 0;
        for (Cell cell : cells) {
            length += cell.getColSpan();
        }
        return length;
    }

    @Override
    public String getString() {
        StringBuilder builder = new StringBuilder(cells.size());
        for (Cell cell : cells) {
            builder.append(cell.getChar());
        }
        return builder.toString();
    }

    @Override
    public boolean isWrapped() {
        return wrapped;
    }

    public int physicalLineCount(int screenWidth) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        return Math.max(1, (visualLength() + screenWidth - 1) / screenWidth);
    }

    public List<Cell> getCells() {
        return cells;
    }

    public void clear() {
        cells.clear();
    }

    public void addCell(Cell cell) {
        cells.add(cell);
    }

    public void addCell(int index, Cell cell) {
        cells.add(index, cell);
    }

    public Cell removeCell(int index) {
        return cells.remove(index);
    }

    public Cell getCellByIndex(int index) {
        return cells.get(index);
    }

    public void setCell(int index, Cell cell) {
        cells.set(index, cell);
    }

    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

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

    public MutableLine copy() {
        return new MutableLine(cells, wrapped);
    }
}
