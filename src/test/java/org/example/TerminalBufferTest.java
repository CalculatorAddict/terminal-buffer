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
        assertEquals(3, buffer.getCursorCol());
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
        assertEquals(CellAttributes.DEFAULT, buffer.getAttributes(0, 0));
        assertNull(buffer.getScrollbackCell(0, 0));
        assertNull(buffer.getScrollbackLine(0));
        assertNotNull(buffer.getLine(0));
    }
}
