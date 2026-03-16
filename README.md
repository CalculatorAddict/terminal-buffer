# TerminalBuffer

This repository contains a Java implementation of a terminal text buffer built for a JetBrains internship task.

The goal of the task is to implement the core data structure a terminal emulator uses to store displayed text, cursor state, cell attributes, visible screen contents, and scrollback history.

Repository: `https://github.com/CalculatorAddict/terminal-buffer`

## Task Context

The task asks for a terminal buffer with:

- configurable width, height, and scrollback size
- per-cell character and styling data
- cursor movement and clamping
- overwrite and insert editing operations
- editable visible screen plus immutable scrollback history
- content and attribute accessors
- comprehensive tests

Bonus points were available for wide-character support and resize handling. This implementation includes both.

## Solution Overview

The public entry point is [`TerminalBuffer`](src/main/java/org/example/buffer/TerminalBuffer.java).

The buffer models two user-visible regions:

- **Screen**: the visible `width x height` area, stored as mutable rows
- **Scrollback**: immutable rows that have scrolled off the top, capped by `maxScrollbackSize`

Each stored cell contains:

- a character
- foreground color
- background color
- style flags: `bold`, `italic`, `underline`
- a visual width (`colSpan`) so wide characters can occupy two terminal columns

The buffer also tracks:

- current cursor position
- current attributes used by normal writes and inserts
- read-only scrollback views for inspection

Rendering is intentionally separate from the core buffer. The demo's ANSI mode goes through [`AnsiRenderer`](src/main/java/org/example/buffer/render/AnsiRenderer.java), which converts stored `CellAttributes` into ANSI SGR escape sequences for terminal display.

## Requirements Coverage

### Setup

- configurable initial width and height
- configurable scrollback maximum size

### Attributes

- `setAttributes(CellAttributes)` updates the current foreground, background, and style flags for further edits
- `writeText(String, CellAttributes)` is an extra convenience overload for one-off styled writes without mutating the current attribute state

### Cursor

- get cursor: `getCursorCol()`, `getCursorRow()`
- set cursor: `setCursor(int col, int row)`
- move cursor: `moveCursor(int dcol, int drow)`
- directional helpers: `moveCursorUp(int)`, `moveCursorDown(int)`, `moveCursorLeft(int)`, `moveCursorRight(int)`
- cursor coordinates are clamped to screen bounds

### Editing

Cursor- and attribute-aware operations:

- `writeText(String)` overwrites at the cursor and moves the cursor
- `writeText(String, CellAttributes)` does the same with explicit per-call attributes
- `insertText(String)` inserts at the cursor, shifts content right, and wraps overflow
- `fillLine(char)` fills the current cursor row using the current attributes
- `fillLine(int row, char c)` is a row-addressed convenience overload
- `clearLine()` clears the current cursor row to empty cells
- `clearLine(int row)` is a row-addressed convenience overload

Operations that do not depend on cursor position:

- `insertEmptyLineAtBottom()`
- `clearScreen()`
- `clearAll()`

### Content Access

- screen character access: `getCell(int col, int row)`
- scrollback character access: `getScrollbackCell(int col, int scrollbackRow)`
- screen character convenience access: `getChar(int col, int row)`
- scrollback character convenience access: `getScrollbackChar(int col, int scrollbackRow)`
- screen attributes: `getAttributes(int col, int row)`
- scrollback attributes: `getScrollbackAttributes(int col, int scrollbackRow)`
- screen line access: `getLine(int row)`
- scrollback line access: `getScrollbackLine(int scrollbackRow)`
- visible screen as string: `getScreenString()`
- scrollback + screen as string: `getFullString()`

### Bonus Features Implemented

- wide-character handling for common BMP CJK cases
- screen resize with full content reflow and cursor remapping

### Technical Constraints

- language: Java
- build tool: Gradle
- external libraries: none for production code, JUnit 5 for tests

## Design Decisions And Trade-Offs

### 1. Physical Rows First, Reconstructed Logical Lines On Demand

The task talks about screen lines and scrollback lines. Internally, this implementation stores **physical rows** because that keeps ordinary terminal editing and scrolling simple.

- screen storage is `List<MutableLine>`
- scrollback storage is `List<ScrollbackLine>`
- each row has a `wrapped` flag indicating whether the following physical row continues the same logical line

Logical lines are reconstructed only when needed, mainly during resize. This keeps the hot path simple while still making correct reflow possible.

Trade-off:

- simpler normal writes and scrolling
- more work during resize because logical lines must be rebuilt from wrapped physical rows

### 2. Immutable Scrollback By Type

Scrollback rows are copied into `ScrollbackLine`, which exposes only read operations. `getScrollback()` also returns a read-only view, so callers cannot mutate history through the public API.

Trade-off:

- stronger safety and clearer API
- extra allocation when a screen row scrolls into history

### 3. Pending-Wrap Cursor State

The valid cursor column range is `[0, width]`, not `[0, width - 1]`.

`cursorCol == width` represents the normal terminal state after writing exactly to the right edge. The next printable write wraps before storing a new cell.

Trade-off:

- matches real terminal behavior more closely
- requires callers and tests to treat `width` as a valid cursor column

### 4. Simplified Control Character Semantics

This buffer handles control characters directly in the write pipeline:

- `\r` moves to column `0`
- `\n` moves to the next physical row and resets the column to `0`

So newline behaves like "line feed plus carriage return".

Trade-off:

- simple, predictable behavior for this task
- not a full terminal-emulator control-sequence model

### 5. Resize Strategy

Resize performs a full reflow:

1. collect scrollback and screen rows
2. rebuild logical lines using `wrapped`
3. re-wrap at the new width
4. keep the newest rows on screen
5. retain older rows in scrollback up to the configured cap

Cursor preservation is based on visual position inside the reconstructed logical line, then clamped if the target column no longer exists after reflow.

Trade-off:

- preserves content and styling more naturally across width changes
- more expensive than a naive crop-or-pad strategy

### 6. Wide Characters

Wide characters are represented with `colSpan == 2`. Accessing either covered visual column returns the same cell, and writing wraps first if only one column remains on the row.

Trade-off:

- good support for the task's bonus requirement
- still limited to a `char`-based model rather than full grapheme-cluster handling

## Demo

Run the structured plain-text demo:

```bash
./gradlew run
```

Run the ANSI-rendered demo:

```bash
./gradlew run --args=--ansi
```

That mode explicitly uses [`AnsiRenderer`](src/main/java/org/example/buffer/render/AnsiRenderer.java). `TerminalBuffer` itself stores characters and attributes only; the renderer is the small adapter that turns those stored attributes into visible ANSI terminal styling.

The demo prints step-by-step snapshots showing:

- action performed
- buffer size and cursor position
- screen rows
- scrollback rows
- styled spans
- wide-cell positions and code points

The later snapshots also demonstrate the spec-aligned cursor helpers plus the explicit current-row `fillLine(char)` and `clearLine()` operations.

In plain mode, wide characters are rendered as ASCII `[]` placeholders so the output remains readable even if the terminal cannot render the glyph itself. This is a demo-only fallback: the buffer still stores and exposes the real wide characters, and the demo prints their exact code points underneath each snapshot.

## Build And Test

Build:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

The test suite covers:

- cursor clamping and movement
- overwrite and insert behavior
- screen-to-scrollback transitions
- scrollback trimming
- attribute persistence
- wide-character behavior
- resize and cursor remapping edge cases

## Future Improvements

If I had more time, the next improvements would be:

- full escape-sequence handling upstream of the buffer
- more complete Unicode width and grapheme-cluster support
- delete-line and delete-character operations
- tabs, alternate screen behavior, and richer terminal semantics
- performance tuning to reduce copying on scroll and resize hot paths

## Repository Contents

The repository contains:

- source code
- Gradle build files
- unit tests documenting expected behavior and edge cases
- incremental git history with feature additions separated from follow-up cleanup/refinement

## AI Usage

This project was developed with AI assistance as encouraged in the task requirements.

Architecture and design decisions were worked out collaboratively with Claude (claude.ai) — including the logical/physical line separation, the Line/MutableLine/ScrollbackLine type hierarchy, cursor pending-wrap semantics, scrollback push granularity, wide character handling, and the wrapped flag approach for reflow.

Implementation, iteration, and refactoring were carried out by OpenAI Codex guided by prompts derived from the design session and code review findings. The test suite was written alongside the implementation and used as a spec to validate correctness.