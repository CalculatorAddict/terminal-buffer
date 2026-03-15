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

    public void setAttributes(CellAttributes attributes) {
        this.currentAttributes = attributes == null ? CellAttributes.DEFAULT : attributes;
    }

    public int getCursorCol() {
        return cursorCol;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    /**
     * Sets the cursor position.
     *
     * <p>The column is clamped to the inclusive range {@code [0, width]}. The value {@code width} is valid and
     * represents the pending-wrap state produced by writing exactly to the end of a row.</p>
     */
    public void setCursor(int col, int row) {
        cursorCol = clamp(col, 0, width);
        cursorRow = clamp(row, 0, height - 1);
    }

    public void moveCursor(int dcol, int drow) {
        setCursor(cursorCol + dcol, cursorRow + drow);
    }

    /**
     * Writes text at the current cursor using the current attributes.
     *
     * <p>{@code '\r'} resets the cursor column to {@code 0}. {@code '\n'} moves to the next physical row and scrolls
     * when already on the last visible row. Control characters update cursor state but do not create cells.</p>
     */
    public void writeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Control characters mutate cursor state directly and never materialize as cells.
            if (handleControlCharacter(c)) {
                continue;
            }
            writeCell(createCell(c));
        }
    }

    /**
     * Inserts text at the current cursor using the current attributes.
     *
     * <p>Control characters follow the same cursor-only semantics as {@link #writeText(String)}. Printable characters
     * are inserted into the current row and may cascade overflow into following rows.</p>
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Insert shares the same CR/LF semantics as write, then resumes cell insertion at the new cursor.
            if (handleControlCharacter(c)) {
                continue;
            }
            Cell cell = createCell(c);
            normalizeCursorForSpan(cell.getColSpan());
            insertCell(cursorRow, cursorCol, cell);
            advanceCursor(cell.getColSpan());
        }
    }

    public void fillLine(int row, char c) {
        int clampedRow = clamp(row, 0, height - 1);
        MutableLine line = new MutableLine();
        for (int col = 0; col < width; col++) {
            line.addCell(new Cell(c, currentAttributes, 1));
        }
        line.setWrapped(false);
        screen.set(clampedRow, line);
    }

    public void insertEmptyLineAtBottom() {
        scrollUp();
    }

    public void clearScreen() {
        screen.clear();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        setCursor(cursorCol, cursorRow);
    }

    public void clearAll() {
        clearScreen();
        scrollback.clear();
    }

    /**
     * Resizes the screen and reflows the full buffer contents.
     *
     * <p>Resize reconstructs logical lines from scrollback plus screen, re-wraps them to the new width, trims
     * scrollback to capacity, and remaps the cursor using visual-position semantics.</p>
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) {
            throw new IllegalArgumentException("newWidth must be positive");
        }
        if (newHeight <= 0) {
            throw new IllegalArgumentException("newHeight must be positive");
        }

        // Reflow computes a complete replacement screen/scrollback snapshot plus the remapped cursor.
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

    public Cell getCell(int col, int row) {
        if (row < 0 || row >= height || col < 0 || col > width) {
            return null;
        }
        return screen.get(row).getCell(col);
    }

    public Cell getScrollbackCell(int col, int scrollbackRow) {
        if (scrollbackRow < 0 || scrollbackRow >= scrollback.size() || col < 0) {
            return null;
        }
        return scrollback.get(scrollbackRow).getCell(col);
    }

    public CellAttributes getAttributes(int col, int row) {
        Cell cell = getCell(col, row);
        return cell == null ? CellAttributes.DEFAULT : cell.getAttributes();
    }

    public Line getLine(int row) {
        if (row < 0 || row >= height) {
            return null;
        }
        return screen.get(row);
    }

    public ScrollbackLine getScrollbackLine(int scrollbackRow) {
        if (scrollbackRow < 0 || scrollbackRow >= scrollback.size()) {
            return null;
        }
        return scrollback.get(scrollbackRow);
    }

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

    public List<MutableLine> getScreen() {
        return screen;
    }

    public List<ScrollbackLine> getScrollback() {
        return scrollback;
    }

    public CellAttributes getCurrentAttributes() {
        return currentAttributes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxScrollbackSize() {
        return maxScrollbackSize;
    }

    private void writeCell(Cell cell) {
        normalizeCursorForSpan(cell.getColSpan());
        MutableLine line = screen.get(cursorRow);
        padLineToColumn(line, cursorCol);
        int cellIndex = line.visualColToCellIndex(cursorCol);
        // Writes replace the existing cell when the cursor is inside populated content,
        // otherwise they extend the row.
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
        // Inserts operate on cell indices, not raw visual columns, so wide cells stay intact.
        int cellIndex = line.visualColToCellIndex(visualCol);
        line.addCell(cellIndex, cell);
        if (line.visualLength() > width) {
            markWrapped(line);
        }
        overflowLine(row);
    }

    private void padLineToColumn(MutableLine line, int visualCol) {
        // Writing past the current content implicitly fills the gap with blank cells carrying current attributes.
        while (line.visualLength() < visualCol) {
            line.addCell(new Cell(' ', currentAttributes, 1));
        }
    }

    private void advanceCursor(int span) {
        cursorCol += span;
        // The pending-wrap state uses cursorCol == width, so only values strictly past width
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
            // Mark the current physical row as continuing before moving the cursor so resize can rejoin it later.
            markWrapped(screen.get(cursorRow));
            cursorCol = 0;
            if (cursorRow == height - 1) {
                scrollUp();
            } else {
                cursorRow++;
            }
        }
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

    private void overflowLine(int row) {
        MutableLine line = screen.get(row);
        if (line.visualLength() <= width) {
            return;
        }

        // Insert can push the last cell off the row, so the source row must be marked
        // as continuing into the next physical row before the overflow cascades.
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
            // This buffer treats '\n' as "move to next row and reset column".
            // A full terminal emulator would usually distinguish line feed from carriage return
            // and let escape-sequence handling decide how those controls interact.
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
