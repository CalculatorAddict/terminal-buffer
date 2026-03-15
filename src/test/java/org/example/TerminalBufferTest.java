package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TerminalBufferTest {

    @Test
    void cursorClampsAtBufferBoundaries() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.setCursor(99, -4);
        assertEquals(4, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());

        buffer.moveCursor(-10, 10);
        assertEquals(0, buffer.getCursorCol());
        assertEquals(2, buffer.getCursorRow());
    }

    @Test
    void writeTextWrapsAcrossRows() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcdef");

        assertEquals("abcd\nef", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void insertTextWrapsOverflowIntoNextRows() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcd");
        buffer.setCursor(1, 0);
        buffer.insertText("XY");

        assertEquals("aXYb\ncd", buffer.getScreenString());
        assertEquals(3, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
    }

    @Test
    void writingExactlyToLineEndKeepsCursorAtWidthWithoutWrappingYet() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcd");

        assertEquals("abcd\n", buffer.getScreenString());
        assertEquals(4, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
        assertEquals(0, buffer.getScrollback().size());
    }

    @Test
    void writingOneCharPastLineEndWrapsToNextLine() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcd");
        buffer.writeText("e");

        assertEquals("abcd\ne", buffer.getScreenString());
        assertEquals(1, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void insertAtLineEndAppendsAndWrapsWithoutShiftingExistingCells() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcd");
        buffer.insertText("XY");

        assertEquals("abcd\nXY", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void newlineInWriteTextMovesCursorToNextRow() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.writeText("ab\ncd");

        assertEquals("ab\ncd\n", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void carriageReturnInWriteTextMovesCursorToColumnZero() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("ab\rc");

        assertEquals("cb\n", buffer.getScreenString());
        assertEquals(1, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
    }

    @Test
    void carriageReturnNewlineMovesCursorToStartOfNextRow() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.writeText("ab\r\ncd");

        assertEquals("ab\ncd\n", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void newlineOnLastRowTriggersScrollUp() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("ab\ncd\n");

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("ab", buffer.getScrollbackLine(0).getString());
        assertEquals("cd\n", buffer.getScreenString());
        assertEquals(0, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void insertAtColumnZeroShiftsContentRightAndWrapsOverflow() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcd");
        buffer.setCursor(0, 0);
        buffer.insertText("XY");

        assertEquals("XYab\ncd", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
    }

    @Test
    void insertTextHandlesCarriageReturnAndNewlineLikeWriteText() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.writeText("ab");
        buffer.insertText("\r\ncd");

        assertEquals("ab\ncd\n", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void writingExactlyFillsScreenWithoutCreatingScrollback() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcdefgh");

        assertEquals("abcd\nefgh", buffer.getScreenString());
        assertEquals(4, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
        assertEquals(0, buffer.getScrollback().size());
    }

    @Test
    void writingOneCharPastFullScreenPushesFirstLineToScrollback() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcdefghi");

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("abcd", buffer.getScrollbackLine(0).getString());
        assertEquals("efgh\ni", buffer.getScreenString());
        assertEquals(1, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void wrappingOnLastScreenRowTriggersScrollUp() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcdefgh");
        buffer.writeText("ij");

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("abcd", buffer.getScrollbackLine(0).getString());
        assertEquals("efgh\nij", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void writingWhenScreenIsFullPushesTopLineToScrollback() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abcdefgh");
        buffer.writeText("ij");

        assertEquals("abcd", buffer.getScrollbackLine(0).getString());
        assertEquals("efgh\nij", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void scrollbackTrimsToMaximumSize() {
        TerminalBuffer buffer = new TerminalBuffer(2, 2, 2);

        buffer.writeText("abcdefghi");

        assertEquals(2, buffer.getScrollback().size());
        assertEquals("cd", buffer.getScrollbackLine(0).getString());
        assertEquals("ef", buffer.getScrollbackLine(1).getString());
        assertEquals("gh\ni", buffer.getScreenString());
    }

    @Test
    void clearScreenPreservesScrollbackButClearAllRemovesIt() {
        TerminalBuffer buffer = new TerminalBuffer(2, 2, 10);

        buffer.writeText("abcde");
        buffer.clearScreen();

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("\n", buffer.getScreenString());

        buffer.clearAll();

        assertEquals(0, buffer.getScrollback().size());
        assertEquals("\n", buffer.getScreenString());
    }

    @Test
    void fillLineUsesCurrentAttributesAcrossEntireRow() {
        TerminalBuffer buffer = new TerminalBuffer(3, 2, 10);
        CellAttributes attributes = new CellAttributes(
                TerminalColor.RED,
                TerminalColor.BLUE,
                true,
                false,
                true
        );

        buffer.setAttributes(attributes);
        buffer.fillLine(1, '#');

        assertEquals("###", buffer.getLine(1).getString());
        for (int col = 0; col < 3; col++) {
            assertEquals('#', buffer.getCell(col, 1).getChar());
            assertEquals(attributes, buffer.getAttributes(col, 1));
        }
    }

    @Test
    void getScreenAndFullStringReflectScreenAndScrollbackOrder() {
        TerminalBuffer buffer = new TerminalBuffer(3, 2, 10);

        buffer.writeText("abcdefg");

        assertEquals("abc", buffer.getScrollbackLine(0).getString());
        assertEquals("def\ng", buffer.getScreenString());
        assertEquals("abc\ndef\ng", buffer.getFullString());
    }

    @Test
    void cellAndAttributeAccessUseDefaultsForEmptyCells() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        assertNull(buffer.getCell(0, 0));
        assertNull(buffer.getCell(0, -1));
        assertNull(buffer.getCell(5, 0));
        assertEquals(CellAttributes.DEFAULT, buffer.getAttributes(0, 0));
        assertNull(buffer.getScrollbackCell(0, 0));
        assertNull(buffer.getScrollbackLine(0));
        assertNotNull(buffer.getLine(0));
        assertNull(buffer.getLine(-1));
        assertNull(buffer.getLine(2));
    }

    @Test
    void wideCharactersAdvanceCursorByTwoAndAreAddressableFromBothColumns() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("界a");

        assertEquals(3, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
        assertEquals(3, buffer.getLine(0).visualLength());
        assertEquals("界a", buffer.getLine(0).getString());
        assertEquals(2, buffer.getCell(0, 0).getColSpan());
        assertEquals('界', buffer.getCell(0, 0).getChar());
        assertEquals('界', buffer.getCell(1, 0).getChar());
        assertEquals('a', buffer.getCell(2, 0).getChar());
    }

    @Test
    void wideCharactersWrapBeforeWritingWhenOnlyOneColumnRemains() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.writeText("abc界");

        assertEquals("abc\n界", buffer.getScreenString());
        assertEquals(2, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }

    @Test
    void shrinkingWidthRewrapsContentIntoMorePhysicalRows() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.writeText("abcdef");
        buffer.resize(2, 3);

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("ab", buffer.getScrollbackLine(0).getString());
        assertEquals("cd\nef\n", buffer.getScreenString());
        assertEquals("ab\ncd\nef\n", buffer.getFullString());
    }

    @Test
    void growingWidthMergesPreviouslyWrappedRowsBackTogether() {
        TerminalBuffer buffer = new TerminalBuffer(2, 4, 10);

        buffer.writeText("abcdef");
        buffer.resize(6, 4);

        assertEquals(0, buffer.getScrollback().size());
        assertEquals("abcdef\n\n\n", buffer.getScreenString());
    }

    @Test
    void resizeReflowRespectsScrollbackMaximumSize() {
        TerminalBuffer buffer = new TerminalBuffer(5, 2, 1);

        buffer.writeText("abcdefghij");
        buffer.resize(2, 2);

        assertEquals(1, buffer.getScrollback().size());
        assertEquals("ef", buffer.getScrollbackLine(0).getString());
        assertEquals("gh\nij", buffer.getScreenString());
    }

    @Test
    void resizeKeepsCursorVisualPositionWhenPossible() {
        TerminalBuffer buffer = new TerminalBuffer(4, 3, 10);

        buffer.writeText("abcdef");
        buffer.setCursor(3, 0);
        buffer.resize(2, 3);

        assertEquals(1, buffer.getCursorCol());
        assertEquals(0, buffer.getCursorRow());
    }

    @Test
    void resizePreservesExactWidthLogicalLineBoundaries() {
        TerminalBuffer buffer = new TerminalBuffer(4, 2, 10);

        buffer.fillLine(0, 'a');
        buffer.fillLine(1, 'b');
        buffer.resize(8, 2);

        assertEquals("aaaa\nbbbb", buffer.getScreenString());
    }

    @Test
    void resizeClampsCursorToNearestValidPositionWhenTargetColumnNoLongerExists() {
        TerminalBuffer buffer = new TerminalBuffer(5, 4, 10);

        buffer.setCursor(4, 3);
        buffer.resize(2, 2);

        assertEquals(0, buffer.getCursorCol());
        assertEquals(1, buffer.getCursorRow());
    }
}
