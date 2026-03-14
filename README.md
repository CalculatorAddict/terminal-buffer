# TerminalBuffer

A terminal text buffer implementation in Java — the core data structure used by terminal emulators to store and manipulate displayed text.

This was built as part of a JetBrains internship application task.

## Overview

When a shell sends output, a terminal emulator updates a buffer, and the UI renders it. This implementation covers the buffer layer only — no escape sequence parsing or rendering.

The buffer maintains a **screen** (the visible grid, `width × height` physical rows) and a **scrollback** (lines that have scrolled off the top, preserved as history).

## Design Decisions

### Logical vs Physical Lines

The core abstraction is a `Line` — an unbounded logical line with no fixed width. Physical layout (how many screen rows a line occupies) is computed on demand via `physicalLineCount(screenWidth)`. This cleanly separates content from presentation, and makes resize/reflow straightforward.

A `Row` is a lightweight view into a `Line` at a given visual offset — it is never stored, only computed when needed for rendering or content access.

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

Resize performs a full reflow. All content (scrollback + screen) is collected as logical lines, re-wrapped at the new width, then redistributed — oldest lines fill scrollback up to `maxScrollbackSize`, most recent `height` physical rows fill the new screen. The cursor is clamped to the new bounds.

## Operations

- **Cursor** — get/set/move with bounds clamping. Valid column range is `[0, width]` inclusive.
- **Attributes** — set current foreground color, background color, and style flags (bold, italic, underline). Used by all subsequent editing operations.
- **Editing** — `writeText`, `insertText`, `fillLine` — all use the current cursor position and current attributes.
- **Screen** — `insertEmptyLineAtBottom`, `clearScreen`, `clearAll`.
- **Content access** — `getCell`, `getAttributes`, `getLine`, `getScrollbackLine`, `getScreenString`, `getFullString`.

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

Tests cover cursor clamping, wrap behaviour, scrollback push and trimming, wide characters, resize reflow, and edge cases throughout.

## AI Usage

This project was developed with AI assistance as encouraged in the task requirements.

Architecture and design decisions were worked out collaboratively with Claude (claude.ai) — including the logical/physical line separation, the `Line`/`MutableLine`/`ScrollbackLine` type hierarchy, cursor pending-wrap behaviour, scrollback push granularity, and wide character handling.

Implementation was carried out by OpenAI Codex guided by a detailed prompt derived from the design session, with the test suite used as a spec to validate correctness.