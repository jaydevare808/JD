# GraphPaper Plotter

A static web app that plots X/Y experimental readings directly on a clean, printable graph-paper template modeled on the supplied sheet.

## Core features
- X/Y readings as comma-separated, space-separated, or line-separated input.
- Independent X and Y scale (value per major square).
- Points, joined points, or a least-squares best-fit straight line.
- Automatic integer grid-line origin placement or manual axis placement.
- Homography-based four-corner calibration.
- PNG export and browser Print / Save PDF.
- No backend and no API key required.
- Vector graph paper for crisp, repeatable plotting and printing.

## Accuracy
The logical plotting area is 17 × 27 major squares. Coordinates are transformed through a projective homography so calibration can compensate for a photographed or scanned sheet.