# TerminalBuffer

A terminal text buffer implementation in Java — the core data structure used by terminal emulators to store and manipulate displayed text.

This was built as part of a JetBrains internship application task.

## Overview

When a shell sends output, a terminal emulator updates a buffer, and the UI renders it. This implementation covers the buffer layer only — no escape sequence parsing or rendering.

The buffer maintains a **screen** (the visible grid, `width × height` physical rows) and a **scrollback** (lines that have scrolled off the top, preserved as history).

The text editing operations handle carriage return and newline directly: `\r` moves the cursor to column `0` of the current row, `\n` moves to the next physical row or scrolls when already on the last row, and neither control character stores a visible cell.

This is a deliberate simplification: the buffer treats `\n` as "line feed plus carriage return". A full terminal emulator would usually distinguish those controls and let escape-sequence handling upstream decide how they interact.

The code is split into focused packages: `org.example.buffer` contains the public `TerminalBuffer` facade and demo, `org.example.buffer.model` contains the line/cell data model, `org.example.buffer.reflow` contains the resize/reflow logic, `org.example.buffer.write` contains the write pipeline and cell construction logic, and `org.example.buffer.cursor` contains cursor state and clamping.

## Design Decisions

### Logical vs Physical Lines

The core abstraction is a `Line` — an unbounded logical line with no fixed width. Physical layout (how many screen rows a line occupies) is computed on demand via `physicalLineCount(screenWidth)`. This cleanly separates content from presentation, and makes resize/reflow straightforward.

### Immutability at the Type Level

The `Line` interface is read-only. `MutableLine` extends it with editing operations for screen content. `ScrollbackLine` implements it with final fields set at construction — immutability is enforced by the type system, not by convention. When a line scrolls off the screen it is copied into a `ScrollbackLine`, severing any shared reference.

### Cursor

The cursor is physical and 0-indexed throughout. Valid column range is `[0, width]` inclusive — `col == width` represents a **pending wrap** state. No extra flag is needed; the next write checks `cursorCol == width` and wraps before writing. This matches standard terminal behaviour.

### Scrollback

Scrollback is append-only. A physical row is pushed to scrollback the moment it would be displaced above row 0 of the screen — this happens via a private `scrollUp()` called whenever a new physical row is needed at the bottom. Scrollback is trimmed to `maxScrollbackSize` on every push, oldest entries removed first.

Delete-line and delete-character operations were deliberately omitted — they would violate the append-only invariant of scrollback and significantly complicate history management. A real terminal emulator would handle these via escape sequence processing upstream of the buffer.

### Wide Characters

`Cell` carries a `colSpan` field (1 for normal, 2 for wide/double-width characters like CJK ideographs and emoji). `getCell(visualCol)` scans by summing colSpans, so accessing either visual column of a wide character returns the same `Cell`. When writing a wide character, if only one column remains on the current row, the buffer wraps before writing to avoid splitting it across rows.

### Resize

Resize performs a full reflow. All content (scrollback + screen) is collected as logical lines, re-wrapped at the new width, then redistributed — oldest lines fill scrollback up to `maxScrollbackSize`, most recent `height` physical rows fill the new screen. The reflow implementation now lives in a dedicated `ReflowEngine`, which keeps resize-specific logic out of `TerminalBuffer` itself.

Cursor placement after resize uses visual-position semantics: before reflow, the buffer records the cursor's physical screen row and column; after reflow, it places the cursor on the new screen row corresponding to that same visual row position within the full scrollback+screen stack, preserving the original column when possible and otherwise clamping to the nearest valid row/column in the rebuilt screen.

## Operations

- **Cursor** — get/set/move with bounds clamping. Valid column range is `[0, width]` inclusive.
- **Attributes** — set current foreground color, background color, and style flags (bold, italic, underline). Used by all subsequent editing operations.
- **Editing** — `writeText`, `insertText`, `fillLine` — all use the current cursor position and current attributes.
- **Screen** — `insertEmptyLineAtBottom`, `clearScreen`, `clearAll`.
- **Content access** — `getCell`, `getAttributes`, `getScrollbackCell`, `getScrollbackAttributes`, `getLine`, `getScrollbackLine`, `getScreenString`, `getFullString`.

## Demo

```bash
./gradlew run
```

Runs a small demo that writes text including wide characters, triggers scrollback, and resizes the buffer, printing the result at each stage.

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

Tests cover cursor clamping, wrap behaviour, scrollback push and trimming, wide characters, resize reflow, visual-position cursor preservation across resize, and edge cases throughout.

## Future Improvements

The current design uses a separate `ScrollbackLine` type to enforce immutability at the type level, matching the spec's requirement that scrollback is unmodifiable. A production implementation concerned with allocation pressure could instead use a single `Line` class with a `freeze()` method — trading compile-time safety for zero extra allocations on the scrollback hot path. Object pooling of `MutableLine` instances would further reduce GC pressure in high-throughput scenarios.

## AI Usage

This project was developed with AI assistance as encouraged in the task requirements.

Architecture and design decisions were worked out collaboratively with Claude (claude.ai) — including the logical/physical line separation, the `Line`/`MutableLine`/`ScrollbackLine` type hierarchy, cursor pending-wrap behaviour, scrollback push granularity, and wide character handling.

Implementation was carried out by OpenAI Codex guided by a detailed prompt derived from the design session, with the test suite used as a spec to validate correctness.
