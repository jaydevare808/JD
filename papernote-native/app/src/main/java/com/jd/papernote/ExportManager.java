package com.jd.papernote;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports every notebook page to the user's public Downloads/PaperNote folder.
 *
 * Image exports are intentionally page-based:
 *   Notebook - Page 01.png
 *   Notebook - Page 02.png
 *   ...
 *
 * PDF remains a single multi-page document.
 */
public final class ExportManager {

    public enum Format {
        PNG("PNG", "image/png", ".png"),
        JPG("JPG", "image/jpeg", ".jpg"),
        WEBP("WebP", "image/webp", ".webp"),
        PDF("PDF", "application/pdf", ".pdf"),
        ZIP("ZIP (PNG pages)", "application/zip", ".zip");

        final String label;
        final String mime;
        final String extension;

        Format(String label, String mime, String extension) {
            this.label = label;
            this.mime = mime;
            this.extension = extension;
        }
    }

    public interface Callback {
        void onComplete(String message);
        void onError(String message);
    }

    private ExportManager() {}

    public static void export(Context context,
                              NotebookStore store,
                              NotebookStore.NotebookMeta notebook,
                              Format format,
                              Callback callback) {
        if (notebook == null || notebook.pages == null || notebook.pages.isEmpty()) {
            callback.onError("There are no pages to export.");
            return;
        }

        try {
            switch (format) {
                case PDF:
                    exportPdf(context, store, notebook);
                    callback.onComplete("PDF saved to Downloads/PaperNote");
                    break;
                case ZIP:
                    exportZip(context, store, notebook);
                    callback.onComplete("ZIP with all pages saved to Downloads/PaperNote");
                    break;
                default:
                    int count = exportImages(context, store, notebook, format);
                    callback.onComplete(count + " " + format.label + " page" + (count == 1 ? "" : "s")
                            + " saved to Downloads/PaperNote");
                    break;
            }
        } catch (Exception e) {
            callback.onError("Export failed: " + safeMessage(e));
        }
    }

    private static int exportImages(Context context,
                                    NotebookStore store,
                                    NotebookStore.NotebookMeta notebook,
                                    Format format) throws Exception {
        int exported = 0;

        for (int i = 0; i < notebook.pages.size(); i++) {
            NotebookStore.PageMeta meta = notebook.pages.get(i);
            Bitmap ink = store.loadPageBitmap(meta.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
            Bitmap rendered = null;
            try {
                rendered = PaperCanvasView.renderPage(ink, meta.paperType, true);
                String fileName = pageFileName(notebook.title, meta.title, i + 1, format.extension);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, format.mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/PaperNote");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    ContentResolver resolver = context.getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create download file.");

                    try (OutputStream out = resolver.openOutputStream(uri)) {
                        if (out == null) throw new Exception("Could not open download file.");
                        compress(rendered, format, out);
                    } catch (Exception e) {
                        resolver.delete(uri, null, null);
                        throw e;
                    }

                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, done, null, null);
                } else {
                    File dir = new File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "PaperNote");
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new Exception("Could not create Downloads/PaperNote.");
                    }

                    File file = new File(dir, fileName);
                    try (OutputStream out = new FileOutputStream(file)) {
                        compress(rendered, format, out);
                    }
                    android.media.MediaScannerConnection.scanFile(
                            context,
                            new String[]{file.getAbsolutePath()},
                            new String[]{format.mime},
                            null
                    );
                }

                exported++;
            } finally {
                if (ink != null && !ink.isRecycled()) ink.recycle();
                if (rendered != null && !rendered.isRecycled()) rendered.recycle();
            }
        }

        return exported;
    }

    private static void exportPdf(Context context,
                                  NotebookStore store,
                                  NotebookStore.NotebookMeta notebook) throws Exception {
        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();

        Uri uri = null;
        File legacyFile = null;
        OutputStream out = null;

        try {
            String fileName = safeFileName(notebook.title) + ".pdf";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/PaperNote");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Could not create PDF download.");
                out = context.getContentResolver().openOutputStream(uri);
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "PaperNote");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create Downloads/PaperNote.");
                legacyFile = new File(dir, fileName);
                out = new FileOutputStream(legacyFile);
            }

            if (out == null) throw new Exception("Could not open PDF download.");

            for (int i = 0; i < notebook.pages.size(); i++) {
                NotebookStore.PageMeta meta = notebook.pages.get(i);
                Bitmap ink = store.loadPageBitmap(meta.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                Bitmap rendered = null;
                try {
                    rendered = PaperCanvasView.renderPage(ink, meta.paperType, true);

                    android.graphics.pdf.PdfDocument.PageInfo info =
                            new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, i + 1).create();
                    android.graphics.pdf.PdfDocument.Page page = document.startPage(info);
                    Canvas canvas = page.getCanvas();
                    canvas.drawBitmap(rendered, null, new Rect(0, 0, 595, 842), null);
                    document.finishPage(page);
                } finally {
                    if (ink != null && !ink.isRecycled()) ink.recycle();
                    if (rendered != null && !rendered.isRecycled()) rendered.recycle();
                }
            }

            document.writeTo(out);
            out.close();
            out = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(uri, done, null, null);
            } else if (legacyFile != null) {
                android.media.MediaScannerConnection.scanFile(
                        context,
                        new String[]{legacyFile.getAbsolutePath()},
                        new String[]{"application/pdf"},
                        null
                );
            }
        } catch (Exception e) {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                context.getContentResolver().delete(uri, null, null);
            } else if (legacyFile != null && legacyFile.exists()) {
                // Avoid leaving a partial PDF behind.
                //noinspection ResultOfMethodCallIgnored
                legacyFile.delete();
            }
            throw e;
        } finally {
            document.close();
        }
    }

    private static void exportZip(Context context,
                                  NotebookStore store,
                                  NotebookStore.NotebookMeta notebook) throws Exception {
        String fileName = safeFileName(notebook.title) + " - Pages.zip";

        Uri uri = null;
        File legacyFile = null;
        OutputStream rawOut = null;
        ZipOutputStream zipOut = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/PaperNote");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Could not create ZIP download.");
                rawOut = context.getContentResolver().openOutputStream(uri);
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "PaperNote");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create Downloads/PaperNote.");
                legacyFile = new File(dir, fileName);
                rawOut = new FileOutputStream(legacyFile);
            }

            if (rawOut == null) throw new Exception("Could not open ZIP download.");
            zipOut = new ZipOutputStream(rawOut);

            for (int i = 0; i < notebook.pages.size(); i++) {
                NotebookStore.PageMeta meta = notebook.pages.get(i);
                Bitmap ink = store.loadPageBitmap(meta.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                Bitmap rendered = null;
                try {
                    rendered = PaperCanvasView.renderPage(ink, meta.paperType, true);

                    zipOut.putNextEntry(new ZipEntry(pageFileName(
                            notebook.title, meta.title, i + 1, ".png")));
                    if (!rendered.compress(Bitmap.CompressFormat.PNG, 100, zipOut)) {
                        throw new Exception("Could not encode page " + (i + 1));
                    }
                    zipOut.closeEntry();
                } finally {
                    if (ink != null && !ink.isRecycled()) ink.recycle();
                    if (rendered != null && !rendered.isRecycled()) rendered.recycle();
                }
            }

            zipOut.finish();
            zipOut.close();
            zipOut = null;
            rawOut = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(uri, done, null, null);
            } else if (legacyFile != null) {
                android.media.MediaScannerConnection.scanFile(
                        context,
                        new String[]{legacyFile.getAbsolutePath()},
                        new String[]{"application/zip"},
                        null
                );
            }
        } catch (Exception e) {
            if (zipOut != null) try { zipOut.close(); } catch (Exception ignored) {}
            else if (rawOut != null) try { rawOut.close(); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                context.getContentResolver().delete(uri, null, null);
            } else if (legacyFile != null && legacyFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                legacyFile.delete();
            }
            throw e;
        }
    }

    private static void compress(Bitmap bitmap, Format format, OutputStream out) throws Exception {
        boolean ok;
        switch (format) {
            case PNG:
                ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                break;
            case JPG:
                ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                break;
            case WEBP:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ok = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 95, out);
                } else {
                    // WEBP is supported on the minimum SDK and maps to the legacy encoder.
                    ok = bitmap.compress(Bitmap.CompressFormat.WEBP, 95, out);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported image format");
        }
        if (!ok) throw new Exception("Could not encode " + format.label + " image.");
    }

    private static String pageFileName(String notebookTitle,
                                       String pageTitle,
                                       int pageNumber,
                                       String extension) {
        return safeFileName(notebookTitle)
                + " - Page "
                + String.format(java.util.Locale.US, "%02d", pageNumber)
                + " - "
                + safeFileName(pageTitle)
                + extension;
    }

    private static String safeFileName(String value) {
        String cleaned = value == null
                ? "PaperNote"
                : value.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        return cleaned.isEmpty() ? "PaperNote" : cleaned;
    }

    private static String safeMessage(Exception error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().isEmpty() ? "Please try again." : value.trim();
    }
}
