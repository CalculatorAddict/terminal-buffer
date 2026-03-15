package org.example;

import java.util.ArrayList;
import java.util.List;

public class TerminalBuffer {
    private static final class CursorVisualPosition {
        private final int logicalLineIndex;
        private final int visualCol;

        private CursorVisualPosition(int logicalLineIndex, int visualCol) {
            this.logicalLineIndex = logicalLineIndex;
            this.visualCol = visualCol;
        }
    }

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

    public void setCursor(int col, int row) {
        cursorCol = clamp(col, 0, width);
        cursorRow = clamp(row, 0, height - 1);
    }

    public void moveCursor(int dcol, int drow) {
        setCursor(cursorCol + dcol, cursorRow + drow);
    }

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

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) {
            throw new IllegalArgumentException("newWidth must be positive");
        }
        if (newHeight <= 0) {
            throw new IllegalArgumentException("newHeight must be positive");
        }

        CursorVisualPosition targetVisualPosition = computeCursorVisualPosition();
        List<MutableLine> reflowedRows = new ArrayList<>();
        for (MutableLine logicalLine : collectLogicalLines()) {
            reflowedRows.addAll(rewrapLine(logicalLine, newWidth));
        }

        width = newWidth;
        height = newHeight;

        int screenStart = Math.max(0, reflowedRows.size() - newHeight);
        int scrollbackStart = Math.max(0, screenStart - maxScrollbackSize);

        scrollback.clear();
        for (int i = scrollbackStart; i < screenStart; i++) {
            scrollback.add(new ScrollbackLine(reflowedRows.get(i)));
        }

        screen.clear();
        for (int i = screenStart; i < reflowedRows.size(); i++) {
            screen.add(reflowedRows.get(i).copy());
        }
        while (screen.size() < newHeight) {
            screen.add(new MutableLine());
        }

        setCursorFromVisualPosition(targetVisualPosition);
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
        overflowLine(row);
    }

    private void padLineToColumn(MutableLine line, int visualCol) {
        while (line.visualLength() < visualCol) {
            line.addCell(new Cell(' ', currentAttributes, 1));
        }
    }

    private void advanceCursor(int span) {
        cursorCol += span;
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
            screen.get(cursorRow).setWrapped(true);
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

        Cell overflow = line.removeCell(line.cellLength() - 1);
        line.setWrapped(true);
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

    private List<MutableLine> rewrapLine(Line line, int targetWidth) {
        List<MutableLine> rows = new ArrayList<>();
        MutableLine currentRow = new MutableLine();
        rows.add(currentRow);

        int visualCol = 0;
        while (visualCol < line.visualLength()) {
            Cell cell = line.getCell(visualCol);
            if (cell == null) {
                break;
            }
            if (currentRow.visualLength() > 0
                    && currentRow.visualLength() + cell.getColSpan() > targetWidth) {
                currentRow.setWrapped(true);
                currentRow = new MutableLine();
                rows.add(currentRow);
            }
            currentRow.addCell(cell);
            visualCol += cell.getColSpan();
        }

        return rows;
    }

    private List<MutableLine> collectLogicalLines() {
        List<MutableLine> logicalLines = new ArrayList<>();
        MutableLine currentLine = null;

        for (Line line : getPhysicalLines()) {
            if (currentLine == null) {
                currentLine = new MutableLine();
            }
            appendLineCells(currentLine, line);
            if (!line.isWrapped()) {
                logicalLines.add(currentLine);
                currentLine = null;
            }
        }

        if (currentLine != null) {
            logicalLines.add(currentLine);
        }
        if (logicalLines.isEmpty()) {
            logicalLines.add(new MutableLine());
        }
        return logicalLines;
    }

    private void appendLineCells(MutableLine target, Line source) {
        int visualCol = 0;
        while (visualCol < source.visualLength()) {
            Cell cell = source.getCell(visualCol);
            if (cell == null) {
                break;
            }
            target.addCell(cell);
            visualCol += cell.getColSpan();
        }
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

    private List<Line> getPhysicalLines() {
        List<Line> lines = new ArrayList<>(scrollback.size() + screen.size());
        lines.addAll(scrollback);
        lines.addAll(screen);
        return lines;
    }

    private CursorVisualPosition computeCursorVisualPosition() {
        List<Line> physicalLines = getPhysicalLines();
        if (physicalLines.isEmpty()) {
            return new CursorVisualPosition(0, 0);
        }

        int targetPhysicalRow = scrollback.size() + cursorRow;
        int logicalLineIndex = 0;
        int logicalLineVisualBase = 0;
        for (int physicalRow = 0; physicalRow < physicalLines.size(); physicalRow++) {
            Line line = physicalLines.get(physicalRow);
            if (physicalRow == targetPhysicalRow) {
                return new CursorVisualPosition(logicalLineIndex, logicalLineVisualBase + cursorCol);
            }
            if (line.isWrapped()) {
                logicalLineVisualBase += line.visualLength();
            } else {
                logicalLineIndex++;
                logicalLineVisualBase = 0;
            }
        }
        return new CursorVisualPosition(logicalLineIndex, cursorCol);
    }

    private void setCursorFromVisualPosition(CursorVisualPosition targetVisualPosition) {
        List<Line> physicalLines = getPhysicalLines();
        if (physicalLines.isEmpty()) {
            cursorRow = 0;
            cursorCol = 0;
            return;
        }

        int totalLogicalLines = 0;
        for (Line line : physicalLines) {
            if (!line.isWrapped()) {
                totalLogicalLines++;
            }
        }
        if (totalLogicalLines == 0) {
            totalLogicalLines = 1;
        }
        int clampedLogicalLineIndex = clamp(targetVisualPosition.logicalLineIndex, 0, totalLogicalLines - 1);

        int logicalLineIndex = 0;
        int logicalLineVisualBase = 0;
        int resolvedPhysicalRow = 0;
        int resolvedCol = 0;
        for (int physicalRow = 0; physicalRow < physicalLines.size(); physicalRow++) {
            Line line = physicalLines.get(physicalRow);
            int rowStart = logicalLineVisualBase;
            int rowEnd = rowStart + line.visualLength();

            if (logicalLineIndex == clampedLogicalLineIndex) {
                resolvedPhysicalRow = physicalRow;
                resolvedCol = clamp(targetVisualPosition.visualCol - rowStart, 0, Math.min(width, line.visualLength()));
                if (targetVisualPosition.visualCol <= rowEnd || !line.isWrapped()) {
                    break;
                }
            }

            if (line.isWrapped()) {
                logicalLineVisualBase = rowEnd;
            } else {
                logicalLineIndex++;
                logicalLineVisualBase = 0;
            }
        }

        int firstScreenRow = scrollback.size();
        cursorRow = clamp(resolvedPhysicalRow - firstScreenRow, 0, height - 1);
        Line line = screen.get(cursorRow);
        cursorCol = clamp(resolvedCol, 0, Math.min(width, line.visualLength()));
    }
}
