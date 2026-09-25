# StudyNote

StudyNote is a small, offline-first Android education notebook focused on reliable, precise handwriting.

## StudyNote 1.2.0

The 1.2.0 editor keeps the same core study features and adds:
- Strict stylus-only palm rejection by default
- Finger/palm pointers ignored while an active stylus continues writing
- Low-latency stylus event delivery
- Safer cancellation handling for stylus strokes
- First-run Welcome Screen
- Refined tablet-friendly editor and notebook UI
- Pen colour and pen size controls remain available in the editor menu

## Core features

- Subject notebooks
- Handwriting-first page editor
- Pen, highlighter and eraser
- Undo and redo
- Undo/redo history
- Blank, ruled and graph paper
- Multiple pages
- Autosave after writing
- PNG page export
- Local storage with no account and no network dependency

## Stability design

The editor uses a single activity instead of opening multiple screens while writing. It does not replace the active canvas during autosave. Pages are checkpointed to private app storage using background I/O.

Palm rejection is implemented at the Android MotionEvent layer. In strict mode, only TOOL_TYPE_STYLUS and TOOL_TYPE_ERASER pointers are accepted as writing input. Finger and palm contacts are consumed but never become the active drawing pointer, so a palm can rest on the display while a stylus continues the stroke.

The app intentionally does not include cloud sync, bundled AI, PDF import, image insertion, shapes, or other secondary features.

CI verification build: StudyNote 1.2.0.


CI recheck after restoring the StudyNote pull-request build trigger.
