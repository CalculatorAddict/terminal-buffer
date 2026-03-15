package org.example.buffer.reflow;

import java.util.ArrayList;
import java.util.List;

import org.example.buffer.model.Cell;
import org.example.buffer.model.Line;
import org.example.buffer.model.MutableLine;
import org.example.buffer.model.ScrollbackLine;

/**
 * Rebuilds screen and scrollback state when the terminal is resized.
 */
public final class ReflowEngine {
    private ReflowEngine() {
    }

    /**
     * Result of reflowing the buffer to a new size.
     *
     * @param screen visible screen rows after resize
     * @param scrollback scrollback rows after resize
     * @param cursorRow cursor row on the resized screen
     * @param cursorCol cursor column on the resized screen
     */
    public record ReflowResult(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback,
            int cursorRow,
            int cursorCol
    ) {
    }

    private record CursorVisualPosition(int logicalLineIndex, int visualCol) {
    }

    /**
     * Reflows the current buffer contents to a new size.
     *
     * @param screen current visible screen rows
     * @param scrollback current scrollback rows
     * @param oldWidth current screen width
     * @param maxScrollbackSize maximum retained scrollback size
     * @param cursorRow current cursor row
     * @param cursorCol current cursor column
     * @param newWidth target screen width
     * @param newHeight target screen height
     * @return resized screen, scrollback, and cursor coordinates
     */
    public static ReflowResult reflow(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback,
            int oldWidth,
            int maxScrollbackSize,
            int cursorRow,
            int cursorCol,
            int newWidth,
            int newHeight
    ) {
        // Capture the cursor as a logical-line position before rewriting physical rows at the new width.
        CursorVisualPosition targetVisualPosition = computeCursorVisualPosition(screen, scrollback, cursorRow, cursorCol);
        List<MutableLine> reflowedRows = new ArrayList<>();
        for (MutableLine logicalLine : collectLogicalLines(screen, scrollback)) {
            reflowedRows.addAll(rewrapLine(logicalLine, newWidth));
        }

        int screenStart = Math.max(0, reflowedRows.size() - newHeight);
        int scrollbackStart = Math.max(0, screenStart - maxScrollbackSize);

        List<ScrollbackLine> resizedScrollback = new ArrayList<>(Math.max(0, screenStart - scrollbackStart));
        for (int i = scrollbackStart; i < screenStart; i++) {
            resizedScrollback.add(new ScrollbackLine(reflowedRows.get(i)));
        }

        List<MutableLine> resizedScreen = new ArrayList<>(newHeight);
        for (int i = screenStart; i < reflowedRows.size(); i++) {
            resizedScreen.add(reflowedRows.get(i).copy());
        }
        while (resizedScreen.size() < newHeight) {
            resizedScreen.add(new MutableLine());
        }

        int[] cursor = setCursorFromVisualPosition(
                resizedScreen,
                resizedScrollback,
                newWidth,
                newHeight,
                targetVisualPosition
        );
        return new ReflowResult(resizedScreen, resizedScrollback, cursor[0], cursor[1]);
    }

    private static List<MutableLine> rewrapLine(Line line, int targetWidth) {
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
                // The current physical row is full; the next row continues the same logical line.
                currentRow.setWrapped(true);
                currentRow = new MutableLine();
                rows.add(currentRow);
            }
            currentRow.addCell(cell);
            visualCol += cell.getColSpan();
        }

        return rows;
    }

    private static List<MutableLine> collectLogicalLines(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback
    ) {
        List<MutableLine> logicalLines = new ArrayList<>();
        MutableLine currentLine = null;

        for (Line line : getPhysicalLines(screen, scrollback)) {
            if (currentLine == null) {
                currentLine = new MutableLine();
            }
            // Reconstruct each logical line by concatenating wrapped physical rows until a row terminates it.
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

    private static void appendLineCells(MutableLine target, Line source) {
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

    private static List<Line> getPhysicalLines(List<MutableLine> screen, List<ScrollbackLine> scrollback) {
        List<Line> lines = new ArrayList<>(scrollback.size() + screen.size());
        lines.addAll(scrollback);
        lines.addAll(screen);
        return lines;
    }

    private static CursorVisualPosition computeCursorVisualPosition(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback,
            int cursorRow,
            int cursorCol
    ) {
        List<Line> physicalLines = getPhysicalLines(screen, scrollback);
        if (physicalLines.isEmpty()) {
            return new CursorVisualPosition(0, 0);
        }

        int targetPhysicalRow = scrollback.size() + cursorRow;
        int logicalLineIndex = 0;
        int logicalLineVisualBase = 0;
        for (int physicalRow = 0; physicalRow < physicalLines.size(); physicalRow++) {
            Line line = physicalLines.get(physicalRow);
            if (physicalRow == targetPhysicalRow) {
                // Store the cursor relative to the reconstructed logical line, not the current physical row.
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

    private static int[] setCursorFromVisualPosition(
            List<MutableLine> screen,
            List<ScrollbackLine> scrollback,
            int width,
            int height,
            CursorVisualPosition targetVisualPosition
    ) {
        List<Line> physicalLines = getPhysicalLines(screen, scrollback);
        if (physicalLines.isEmpty()) {
            return new int[]{0, 0};
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
        int clampedLogicalLineIndex = clamp(targetVisualPosition.logicalLineIndex(), 0, totalLogicalLines - 1);

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
                // If the original visual column no longer exists after reflow, land on the nearest valid column.
                resolvedCol = clamp(targetVisualPosition.visualCol() - rowStart, 0, Math.min(width, line.visualLength()));
                if (targetVisualPosition.visualCol() <= rowEnd || !line.isWrapped()) {
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
        int resolvedScreenRow = clamp(resolvedPhysicalRow - firstScreenRow, 0, height - 1);
        Line line = screen.get(resolvedScreenRow);
        int resolvedScreenCol = clamp(resolvedCol, 0, Math.min(width, line.visualLength()));
        return new int[]{resolvedScreenRow, resolvedScreenCol};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
