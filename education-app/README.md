# StudyNote

StudyNote is a small, offline-first Android education notebook focused on reliable handwriting.

## Core features

- Subject notebooks
- Handwriting-first page editor
- Pen, highlighter and eraser
- Undo and redo
- Blank, ruled and graph paper
- Multiple pages
- Autosave after writing
- Local storage with no account and no network dependency

## Stability design

The editor uses a single activity instead of opening multiple screens while writing. It never calls finish because of ordinary lifecycle changes, and it does not discard the canvas when the activity temporarily loses focus. Pages are autosaved to private app storage so Android process recreation can restore the latest checkpoint.

The app intentionally does not include cloud sync, bundled AI, PDF import, image insertion, shapes, or other secondary features in this first stable build.
