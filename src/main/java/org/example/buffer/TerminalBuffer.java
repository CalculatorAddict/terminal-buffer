package org.example.buffer;

import java.util.ArrayList;
import java.util.List;

import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.Line;
import org.example.buffer.model.MutableLine;
import org.example.buffer.model.ScrollbackLine;
import org.example.buffer.reflow.ReflowEngine;

/**
 * Terminal text buffer with a visible screen and append-only scrollback history.
 *
 * <p>The buffer stores visible content as mutable physical rows and stores scrolled-off rows as immutable
 * {@link ScrollbackLine} instances. Each physical row carries a {@code wrapped} flag that indicates whether the next
 * physical row is a continuation of the same logical line, which allows resize reflow to reconstruct logical lines
 * exactly instead of guessing from row width alone.</p>
 *
 * <p>The cursor is tracked in physical screen coordinates. Columns use the inclusive range {@code [0, width]}:
 * {@code col == width} is the pending-wrap state produced by writing exactly to the end of a row. The next printable
 * write wraps before storing a cell.</p>
 *
 * <p>Control characters are handled directly by text operations. {@code '\r'} moves the cursor to column {@code 0} on
 * the current row, and {@code '\n'} moves to the next physical row or scrolls the buffer when already on the last
 * screen row. Neither control character stores a cell in the buffer.</p>
 */
public class TerminalBuffer {
    private final List<MutableLine> screen;
    private final List<ScrollbackLine> scrollback;
    private int cursorCol;
    private int cursorRow;
    private CellAttributes currentAttributes;
    private int width;
    private int height;
    private final int maxScrollbackSize;

    /**
     * Creates a new terminal buffer.
     *
     * @param width screen width in columns
     * @param height screen height in physical rows
     * @param maxScrollbackSize maximum number of physical rows retained in scrollback
     */
    public TerminalBuffer(int width, int height, int maxScrollbackSize) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        if (maxScrollbackSize < 0) {
            throw new IllegalArgumentException("maxScrollbackSize must be non-negative");
        }
        this.width = width;
        this.height = height;
        this.maxScrollbackSize = maxScrollbackSize;
        this.screen = new ArrayList<>(height);
        this.scrollback = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        this.currentAttributes = CellAttributes.DEFAULT;
    }

    /**
     * Sets the attributes used by subsequent editing operations.
     *
     * @param attributes attributes to apply; {@code null} resets to {@link CellAttributes#DEFAULT}
     */
    public void setAttributes(CellAttributes attributes) {
        this.currentAttributes = attributes == null ? CellAttributes.DEFAULT : attributes;
    }

    /**
     * Returns the current cursor column.
     *
     * @return 0-based cursor column, or {@code width} for the pending-wrap state
     */
    public int getCursorCol() {
        return cursorCol;
    }

    /**
     * Returns the current cursor row.
     *
     * @return 0-based physical screen row
     */
    public int getCursorRow() {
        return cursorRow;
    }

    /**
     * Sets the cursor position.
     *
     * @param col target column, clamped to {@code [0, width]}
     * @param row target row, clamped to {@code [0, height - 1]}
     */
    public void setCursor(int col, int row) {
        cursorCol = clamp(col, 0, width);
        cursorRow = clamp(row, 0, height - 1);
    }

    /**
     * Moves the cursor relative to its current position.
     *
     * @param dcol column delta
     * @param drow row delta
     */
    public void moveCursor(int dcol, int drow) {
        setCursor(cursorCol + dcol, cursorRow + drow);
    }

    /**
     * Writes text at the current cursor using the current attributes.
     *
     * @param text text to write; {@code '\r'} resets the column and {@code '\n'} advances to the next physical row
     */
    public void writeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (handleControlCharacter(c)) {
                continue;
            }
            writeCell(createCell(c));
        }
    }

    /**
     * Inserts text at the current cursor using the current attributes.
     *
     * @param text text to insert; {@code '\r'} and {@code '\n'} adjust the cursor without inserting cells
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (handleControlCharacter(c)) {
                continue;
            }
            Cell cell = createCell(c);
            normalizeCursorForSpan(cell.getColSpan());
            insertCell(cursorRow, cursorCol, cell);
            advanceCursor(cell.getColSpan());
        }
    }

    /**
     * Replaces a physical screen row with the given character repeated across the full width.
     *
     * @param row target physical row, clamped to the visible screen
     * @param c character used to fill the row
     */
    public void fillLine(int row, char c) {
        int clampedRow = clamp(row, 0, height - 1);
        MutableLine line = new MutableLine();
        for (int col = 0; col < width; col++) {
            line.addCell(new Cell(c, currentAttributes, 1));
        }
        line.setWrapped(false);
        screen.set(clampedRow, line);
    }

    /**
     * Scrolls the visible screen up by one physical row and appends a blank row at the bottom.
     */
    public void insertEmptyLineAtBottom() {
        scrollUp();
    }

    /**
     * Clears the visible screen while preserving scrollback history.
     */
    public void clearScreen() {
        screen.clear();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        setCursor(cursorCol, cursorRow);
    }

    /**
     * Clears both the visible screen and the scrollback history.
     */
    public void clearAll() {
        clearScreen();
        scrollback.clear();
    }

    /**
     * Resizes the screen and reflows the full buffer contents.
     *
     * @param newWidth new screen width in columns
     * @param newHeight new screen height in physical rows
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) {
            throw new IllegalArgumentException("newWidth must be positive");
        }
        if (newHeight <= 0) {
            throw new IllegalArgumentException("newHeight must be positive");
        }

        ReflowEngine.ReflowResult result = ReflowEngine.reflow(
                screen,
                scrollback,
                width,
                maxScrollbackSize,
                cursorRow,
                cursorCol,
                newWidth,
                newHeight
        );

        width = newWidth;
        height = newHeight;
        scrollback.clear();
        scrollback.addAll(result.scrollback());
        screen.clear();
        screen.addAll(result.screen());
        cursorRow = result.cursorRow();
        cursorCol = result.cursorCol();
    }

    /**
     * Returns the visible cell covering the given position.
     *
     * @param col 0-based visual column
     * @param row 0-based physical screen row
     * @return the covering cell, or {@code null} for out-of-bounds or empty positions
     */
    public Cell getCell(int col, int row) {
        if (row < 0 || row >= height || col < 0 || col > width) {
            return null;
        }
        return screen.get(row).getCell(col);
    }

    /**
     * Returns a scrollback cell covering the given position.
     *
     * @param col 0-based visual column
     * @param scrollbackRow 0-based physical scrollback row
     * @return the covering cell, or {@code null} for out-of-bounds or empty positions
     */
    public Cell getScrollbackCell(int col, int scrollbackRow) {
        if (scrollbackRow < 0 || scrollbackRow >= scrollback.size() || col < 0) {
            return null;
        }
        return scrollback.get(scrollbackRow).getCell(col);
    }

    /**
     * Returns the attributes at the given visible position.
     *
     * @param col 0-based visual column
     * @param row 0-based physical screen row
     * @return the cell attributes at that position, or {@link CellAttributes#DEFAULT} when no cell exists
     */
    public CellAttributes getAttributes(int col, int row) {
        Cell cell = getCell(col, row);
        return cell == null ? CellAttributes.DEFAULT : cell.getAttributes();
    }

    /**
     * Returns a visible physical row.
     *
     * @param row 0-based physical screen row
     * @return the row, or {@code null} when out of bounds
     */
    public Line getLine(int row) {
        if (row < 0 || row >= height) {
            return null;
        }
        return screen.get(row);
    }

    /**
     * Returns a physical row from scrollback.
     *
     * @param scrollbackRow 0-based physical scrollback row
     * @return the immutable row, or {@code null} when out of bounds
     */
    public ScrollbackLine getScrollbackLine(int scrollbackRow) {
        if (scrollbackRow < 0 || scrollbackRow >= scrollback.size()) {
            return null;
        }
        return scrollback.get(scrollbackRow);
    }

    /**
     * Returns the visible screen as newline-separated row strings.
     *
     * @return the screen contents from top to bottom
     */
    public String getScreenString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < screen.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(screen.get(i).getString());
        }
        return builder.toString();
    }

    /**
     * Returns scrollback followed by screen as newline-separated row strings.
     *
     * @return the full buffer contents from oldest scrollback row to newest visible row
     */
    public String getFullString() {
        StringBuilder builder = new StringBuilder();
        for (ScrollbackLine scrollbackLine : scrollback) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(scrollbackLine.getString());
        }
        for (MutableLine line : screen) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(line.getString());
        }
        return builder.toString();
    }

    /**
     * Returns the live list backing the visible screen.
     *
     * @return mutable screen rows in top-to-bottom order
     */
    public List<MutableLine> getScreen() {
        return screen;
    }

    /**
     * Returns the live scrollback list.
     *
     * @return immutable scrollback rows in oldest-to-newest order
     */
    public List<ScrollbackLine> getScrollback() {
        return scrollback;
    }

    /**
     * Returns the attributes used for future writes.
     *
     * @return the current write attributes
     */
    public CellAttributes getCurrentAttributes() {
        return currentAttributes;
    }

    /**
     * Returns the current screen width.
     *
     * @return width in columns
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the current screen height.
     *
     * @return height in physical rows
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the configured scrollback capacity.
     *
     * @return maximum number of physical rows retained in scrollback
     */
    public int getMaxScrollbackSize() {
        return maxScrollbackSize;
    }

    private void writeCell(Cell cell) {
        normalizeCursorForSpan(cell.getColSpan());
        MutableLine line = screen.get(cursorRow);
        padLineToColumn(line, cursorCol);
        int cellIndex = line.visualColToCellIndex(cursorCol);
        if (cellIndex < line.cellLength()) {
            line.setCell(cellIndex, cell);
        } else {
            line.addCell(cell);
        }
        overflowLine(cursorRow);
        advanceCursor(cell.getColSpan());
    }

    private void insertCell(int row, int visualCol, Cell cell) {
        MutableLine line = screen.get(row);
        padLineToColumn(line, visualCol);
        int cellIndex = line.visualColToCellIndex(visualCol);
        line.addCell(cellIndex, cell);
        if (line.visualLength() > width) {
            markWrapped(line);
        }
        overflowLine(row);
    }

    private void padLineToColumn(MutableLine line, int visualCol) {
        while (line.visualLength() < visualCol) {
            line.addCell(new Cell(' ', currentAttributes, 1));
        }
    }

    private void advanceCursor(int span) {
        cursorCol += span;
        // The pending-wrap state is represented by cursorCol == width, so only values strictly past width
        // should advance to the next physical row.
        while (cursorCol > width) {
            cursorCol -= width;
            if (cursorRow == height - 1) {
                scrollUp();
            } else {
                cursorRow++;
            }
        }
    }

    private void normalizeCursorForSpan(int span) {
        if (cursorCol >= width || cursorCol + span > width) {
            // Mark the current physical row as continuing before moving the cursor so resize can later rejoin it.
            markWrapped(screen.get(cursorRow));
            cursorCol = 0;
            if (cursorRow == height - 1) {
                scrollUp();
            } else {
                cursorRow++;
            }
        }
    }

    private void scrollUp() {
        if (!screen.isEmpty()) {
            // Scrollback stores immutable snapshots of physical rows, including whether the row wrapped forward.
            scrollback.add(new ScrollbackLine(screen.remove(0)));
            if (scrollback.size() > maxScrollbackSize) {
                scrollback.remove(0);
            }
            screen.add(new MutableLine());
        }
    }

    private void overflowLine(int row) {
        MutableLine line = screen.get(row);
        if (line.visualLength() <= width) {
            return;
        }

        // Insert can push the last cell off the row; that row is now explicitly marked as wrapping into the next one.
        Cell overflow = line.removeCell(line.cellLength() - 1);
        markWrapped(line);
        int nextRow = row + 1;
        if (nextRow >= height) {
            scrollUp();
            nextRow = height - 1;
        }
        insertCell(nextRow, 0, overflow);
    }

    private Cell createCell(char c) {
        return new Cell(c, currentAttributes, charWidth(c));
    }

    private void markWrapped(MutableLine line) {
        if (!line.isWrapped()) {
            line.setWrapped(true);
        }
    }

    private boolean handleControlCharacter(char c) {
        if (c == '\r') {
            cursorCol = 0;
            return true;
        }
        if (c == '\n') {
            cursorCol = 0;
            if (cursorRow == height - 1) {
                scrollUp();
            } else {
                cursorRow++;
            }
            return true;
        }
        return false;
    }

    private int charWidth(char c) {
        return isWide(c) ? 2 : 1;
    }

    private boolean isWide(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
