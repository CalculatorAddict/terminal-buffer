package org.example.buffer.write;

import java.util.List;

import org.example.buffer.cursor.CursorState;
import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.MutableLine;
import org.example.buffer.model.ScrollbackLine;

/**
 * Mutating write pipeline for terminal buffer text operations.
 */
public final class TextWriter {
    private final List<MutableLine> screen;
    private final List<ScrollbackLine> scrollback;
    private final CursorState cursorState;
    private final int maxScrollbackSize;
    private final CellFactory cellFactory;

    public TextWriter(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback,
            CursorState cursorState,
            int maxScrollbackSize,
            CellFactory cellFactory
    ) {
        this.screen = screen;
        this.scrollback = scrollback;
        this.cursorState = cursorState;
        this.maxScrollbackSize = maxScrollbackSize;
        this.cellFactory = cellFactory;
    }

    /**
     * Writes text at the current cursor using overwrite semantics.
     *
     * <p>{@code '\r'} resets the cursor column to {@code 0}. {@code '\n'} moves to the next physical row and scrolls
     * when already on the last visible row. Control characters update cursor state but do not create cells.</p>
     */
    public void writeText(String text, CellAttributes currentAttributes) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Control characters mutate cursor state directly and never materialize as cells.
            if (handleControlCharacter(c)) {
                continue;
            }
            writeCell(cellFactory.createCell(c, currentAttributes), currentAttributes);
        }
    }

    /**
     * Inserts text at the current cursor using insert semantics.
     *
     * <p>Control characters follow the same cursor-only semantics as {@link #writeText(String, CellAttributes)}.
     * Printable characters are inserted into the current row and may cascade overflow into following rows.</p>
     */
    public void insertText(String text, CellAttributes currentAttributes) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Insert shares the same CR/LF semantics as write, then resumes cell insertion at the new cursor.
            if (handleControlCharacter(c)) {
                continue;
            }
            Cell cell = cellFactory.createCell(c, currentAttributes);
            normalizeCursorForSpan(cell.getColSpan());
            insertCell(cursorState.getCursorRow(), cursorState.getCursorCol(), cell, currentAttributes);
            advanceCursor(cell.getColSpan());
        }
    }

    public void insertEmptyLineAtBottom() {
        scrollUp();
    }

    /**
     * Scrolls the visible content up by one physical row.
     *
     * <p>The top screen row is copied into scrollback, a blank row is appended at the bottom, and the cursor position
     * is left untouched so callers can decide how scrolling affects cursor movement.</p>
     */
    private void scrollUp() {
        if (!screen.isEmpty()) {
            // Scrollback stores immutable snapshots of physical rows, including whether each row wrapped forward.
            scrollback.add(new ScrollbackLine(screen.remove(0)));
            if (scrollback.size() > maxScrollbackSize) {
                scrollback.remove(0);
            }
            screen.add(new MutableLine());
        }
    }

    private void writeCell(Cell cell, CellAttributes currentAttributes) {
        normalizeCursorForSpan(cell.getColSpan());
        MutableLine line = screen.get(cursorState.getCursorRow());
        padLineToColumn(line, cursorState.getCursorCol(), currentAttributes);
        int cellIndex = line.visualColToCellIndex(cursorState.getCursorCol());
        // Writes replace the existing cell when the cursor is inside populated content,
        // otherwise they extend the row.
        if (cellIndex < line.cellLength()) {
            line.setCell(cellIndex, cell);
        } else {
            line.addCell(cell);
        }
        overflowLine(cursorState.getCursorRow(), currentAttributes);
        advanceCursor(cell.getColSpan());
    }

    private void insertCell(int row, int visualCol, Cell cell, CellAttributes currentAttributes) {
        MutableLine line = screen.get(row);
        padLineToColumn(line, visualCol, currentAttributes);
        // Inserts operate on cell indices, not raw visual columns, so wide cells stay intact.
        int cellIndex = line.visualColToCellIndex(visualCol);
        line.addCell(cellIndex, cell);
        if (line.visualLength() > cursorState.getWidth()) {
            markWrapped(line);
        }
        overflowLine(row, currentAttributes);
    }

    private void overflowLine(int row, CellAttributes currentAttributes) {
        MutableLine line = screen.get(row);
        if (line.visualLength() <= cursorState.getWidth()) {
            return;
        }

        // Insert can push the last cell off the row, so the source row must be marked
        // as continuing into the next physical row before the overflow cascades.
        Cell overflow = line.removeCell(line.cellLength() - 1);
        markWrapped(line);
        int nextRow = row + 1;
        if (nextRow >= cursorState.getHeight()) {
            scrollUp();
            nextRow = cursorState.getHeight() - 1;
        }
        insertCell(nextRow, 0, overflow, currentAttributes);
    }

    private void padLineToColumn(MutableLine line, int visualCol, CellAttributes currentAttributes) {
        // Writing past the current content implicitly fills the gap with blank cells carrying current attributes.
        while (line.visualLength() < visualCol) {
            line.addCell(new Cell(' ', currentAttributes, 1));
        }
    }

    private void advanceCursor(int span) {
        cursorState.setCursorColUnchecked(cursorState.getCursorCol() + span);
        // The pending-wrap state uses cursorCol == width, so only values strictly past width
        // should advance to the next physical row.
        while (cursorState.getCursorCol() > cursorState.getWidth()) {
            cursorState.setCursorColUnchecked(cursorState.getCursorCol() - cursorState.getWidth());
            if (cursorState.getCursorRow() == cursorState.getHeight() - 1) {
                scrollUp();
            } else {
                cursorState.setCursorRowUnchecked(cursorState.getCursorRow() + 1);
            }
        }
    }

    private void normalizeCursorForSpan(int span) {
        if (cursorState.getCursorCol() >= cursorState.getWidth()
                || cursorState.getCursorCol() + span > cursorState.getWidth()) {
            // Mark the current physical row as continuing before moving the cursor so resize can rejoin it later.
            markWrapped(screen.get(cursorState.getCursorRow()));
            cursorState.setCursorColUnchecked(0);
            if (cursorState.getCursorRow() == cursorState.getHeight() - 1) {
                scrollUp();
            } else {
                cursorState.setCursorRowUnchecked(cursorState.getCursorRow() + 1);
            }
        }
    }

    private boolean handleControlCharacter(char c) {
        if (c == '\r') {
            cursorState.setCursorColUnchecked(0);
            return true;
        }
        if (c == '\n') {
            // This buffer treats '\n' as "move to next row and reset column".
            // A full terminal emulator would usually distinguish line feed from carriage return
            // and let escape-sequence handling decide how those controls interact.
            cursorState.setCursorColUnchecked(0);
            if (cursorState.getCursorRow() == cursorState.getHeight() - 1) {
                scrollUp();
            } else {
                cursorState.setCursorRowUnchecked(cursorState.getCursorRow() + 1);
            }
            return true;
        }
        return false;
    }

    private void markWrapped(MutableLine line) {
        if (!line.isWrapped()) {
            line.setWrapped(true);
        }
    }
}
