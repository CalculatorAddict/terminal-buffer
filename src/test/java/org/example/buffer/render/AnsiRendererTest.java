package org.example.buffer.render;

import org.example.buffer.TerminalBuffer;
import org.example.buffer.model.CellAttributes;
import org.example.buffer.model.TerminalColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnsiRendererTest {
    private final AnsiRenderer renderer = new AnsiRenderer();

    @Test
    void defaultCellsRenderWithoutEscapeCodes() {
        TerminalBuffer buffer = new TerminalBuffer(4, 1, 10);

        buffer.writeText("ab");

        assertEquals("ab  ", renderer.renderLine(buffer.getLine(0), buffer.getWidth()));
    }

    @Test
    void styledCellsRenderWithSgrCodesAndResetBeforeDefaultPadding() {
        TerminalBuffer buffer = new TerminalBuffer(4, 1, 10);
        buffer.writeText("ab", new CellAttributes(
                TerminalColor.RED,
                TerminalColor.BLUE,
                true,
                true,
                true
        ));

        assertEquals(
                "\u001B[0;1;3;4;31;44mab\u001B[0m  ",
                renderer.renderLine(buffer.getLine(0), buffer.getWidth())
        );
    }

    @Test
    void wideCharactersRenderOnceWhileStillRespectingVisualWidth() {
        TerminalBuffer buffer = new TerminalBuffer(4, 1, 10);
        buffer.writeText("界a", new CellAttributes(
                TerminalColor.GREEN,
                TerminalColor.DEFAULT,
                false,
                false,
                false
        ));

        assertEquals(
                "\u001B[0;32;49m界a\u001B[0m ",
                renderer.renderLine(buffer.getLine(0), buffer.getWidth())
        );
    }
}
