package org.example.buffer;

import java.util.ArrayList;
import java.util.List;

import org.example.buffer.cursor.CursorState;
import org.example.buffer.model.Cell;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.Line;
import org.example.buffer.model.MutableLine;
import org.example.buffer.model.ScrollbackLine;
import org.example.buffer.reflow.ReflowEngine;
import org.example.buffer.write.CellFactory;
import org.example.buffer.write.TextWriter;

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
    private final CursorState cursorState;
    private final TextWriter textWriter;
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
        this.cursorState = new CursorState(width, height);
        this.textWriter = new TextWriter(screen, scrollback, cursorState, maxScrollbackSize, new CellFactory());
        this.currentAttributes = CellAttributes.DEFAULT;
    }

    public void setAttributes(CellAttributes attributes) {
        this.currentAttributes = attributes == null ? CellAttributes.DEFAULT : attributes;
    }

    public int getCursorCol() {
        return cursorState.getCursorCol();
    }

    public int getCursorRow() {
        return cursorState.getCursorRow();
    }

    /**
     * Sets the cursor position.
     *
     * <p>The column is clamped to the inclusive range {@code [0, width]}. The value {@code width} is valid and
     * represents the pending-wrap state produced by writing exactly to the end of a row.</p>
     */
    public void setCursor(int col, int row) {
        cursorState.setCursor(col, row);
    }

    public void moveCursor(int dcol, int drow) {
        cursorState.moveCursor(dcol, drow);
    }

    /**
     * Writes text at the current cursor using the current attributes.
     *
     * <p>{@code '\r'} resets the cursor column to {@code 0}. {@code '\n'} moves to the next physical row and scrolls
     * when already on the last visible row. Control characters update cursor state but do not create cells.</p>
     */
    public void writeText(String text) {
        textWriter.writeText(text, currentAttributes);
    }

    /**
     * Inserts text at the current cursor using the current attributes.
     *
     * <p>Control characters follow the same cursor-only semantics as {@link #writeText(String)}. Printable characters
     * are inserted into the current row and may cascade overflow into following rows.</p>
     */
    public void insertText(String text) {
        textWriter.insertText(text, currentAttributes);
    }

    public void fillLine(int row, char c) {
        int clampedRow = cursorState.clampRow(row);
        MutableLine line = new MutableLine();
        for (int col = 0; col < width; col++) {
            line.addCell(new Cell(c, currentAttributes, 1));
        }
        line.setWrapped(false);
        screen.set(clampedRow, line);
    }

    public void insertEmptyLineAtBottom() {
        textWriter.insertEmptyLineAtBottom();
    }

    public void clearScreen() {
        screen.clear();
        for (int i = 0; i < height; i++) {
            screen.add(new MutableLine());
        }
        cursorState.setCursor(cursorState.getCursorCol(), cursorState.getCursorRow());
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
                cursorState.getCursorRow(),
                cursorState.getCursorCol(),
                newWidth,
                newHeight
        );

        width = newWidth;
        height = newHeight;
        cursorState.setDimensions(newWidth, newHeight);
        scrollback.clear();
        scrollback.addAll(result.scrollback());
        screen.clear();
        screen.addAll(result.screen());
        cursorState.setCursor(result.cursorCol(), result.cursorRow());
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

    public CellAttributes getScrollbackAttributes(int col, int scrollbackRow) {
        Cell cell = getScrollbackCell(col, scrollbackRow);
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
}
