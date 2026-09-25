package com.jd.studynote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class NotebookStore {
    public static final class PageMeta {
        public String id;
        public String title;
        public String paperType;

        public PageMeta(String id, String title, String paperType) {
            this.id = id;
            this.title = title;
            this.paperType = paperType;
        }
    }

    public static final class NotebookMeta {
        public String id;
        public String title;
        public String subject;
        public long updatedAt;
        public final ArrayList<PageMeta> pages = new ArrayList<>();
    }

    private final File notebooksDir;
    private final File pagesDir;

    public NotebookStore(Context context) {
        File root = new File(context.getFilesDir(), "studynote");
        notebooksDir = new File(root, "notebooks");
        pagesDir = new File(root, "pages");
        if (!notebooksDir.exists()) notebooksDir.mkdirs();
        if (!pagesDir.exists()) pagesDir.mkdirs();
    }

    public List<NotebookMeta> list() {
        ArrayList<NotebookMeta> result = new ArrayList<>();
        File[] files = notebooksDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try {
                result.add(parseNotebook(readText(file)));
            } catch (Exception ignored) {
            }
        }

        result.sort(Comparator.comparingLong((NotebookMeta n) -> n.updatedAt).reversed());
        return result;
    }

    public NotebookMeta get(String id) throws Exception {
        File file = metadataFile(id);
        if (!file.exists()) throw new Exception("Notebook not found");
        return parseNotebook(readText(file));
    }

    public NotebookMeta create(String title, String subject, String paperType) throws Exception {
        NotebookMeta meta = new NotebookMeta();
        meta.id = UUID.randomUUID().toString();
        meta.title = clean(title, "New Notebook");
        meta.subject = clean(subject, "General Study");
        meta.updatedAt = System.currentTimeMillis();
        meta.pages.add(new PageMeta(
                UUID.randomUUID().toString(),
                "Page 1",
                normalizePaper(paperType)
        ));
        saveNotebook(meta);
        return meta;
    }

    public PageMeta addPage(NotebookMeta notebook, String title, String paperType) {
        PageMeta page = new PageMeta(
                UUID.randomUUID().toString(),
                clean(title, "Page " + (notebook.pages.size() + 1)),
                normalizePaper(paperType)
        );
        notebook.pages.add(page);
        notebook.updatedAt = System.currentTimeMillis();
        return page;
    }

    public void renameNotebook(NotebookMeta notebook, String title) throws Exception {
        String value = title == null ? "" : title.trim();
        if (!value.isEmpty()) notebook.title = value;
        notebook.updatedAt = System.currentTimeMillis();
        saveNotebook(notebook);
    }

    public void renamePage(NotebookMeta notebook, int index, String title) throws Exception {
        if (index < 0 || index >= notebook.pages.size()) return;
        String value = title == null ? "" : title.trim();
        if (!value.isEmpty()) {
            notebook.pages.get(index).title = value;
            notebook.updatedAt = System.currentTimeMillis();
            saveNotebook(notebook);
        }
    }

    public void saveNotebook(NotebookMeta notebook) throws Exception {
        notebook.updatedAt = System.currentTimeMillis();
        File target = metadataFile(notebook.id);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        writeText(temp, serializeNotebook(notebook));

        if (target.exists() && !target.delete()) {
            throw new Exception("Unable to replace notebook metadata");
        }
        if (!temp.renameTo(target)) {
            throw new Exception("Unable to commit notebook metadata");
        }
    }

    public Bitmap loadPageBitmap(String pageId, int width, int height) {
        File file = pageFile(pageId);
        if (!file.exists()) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
            return bitmap;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        if (scaled != bitmap && !bitmap.isRecycled()) bitmap.recycle();
        return scaled;
    }

    public void savePageBitmap(String pageId, Bitmap bitmap) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new Exception("Invalid page bitmap");
        }

        File target = pageFile(pageId);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");

        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new Exception("Could not encode page");
            }
            out.flush();
        }

        if (target.exists() && !target.delete()) {
            throw new Exception("Unable to replace page");
        }
        if (!temp.renameTo(target)) {
            throw new Exception("Unable to commit page");
        }
    }

    public static NotebookMeta copyOf(NotebookMeta source) {
        NotebookMeta copy = new NotebookMeta();
        copy.id = source.id;
        copy.title = source.title;
        copy.subject = source.subject;
        copy.updatedAt = source.updatedAt;

        for (PageMeta page : source.pages) {
            copy.pages.add(new PageMeta(page.id, page.title, page.paperType));
        }
        return copy;
    }

    private File metadataFile(String id) {
        return new File(notebooksDir, id + ".json");
    }

    private File pageFile(String id) {
        return new File(pagesDir, id + ".png");
    }

    private static String serializeNotebook(NotebookMeta notebook) throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", notebook.id);
        root.put("title", notebook.title);
        root.put("subject", notebook.subject);
        root.put("updatedAt", notebook.updatedAt);

        JSONArray pages = new JSONArray();
        for (PageMeta page : notebook.pages) {
            JSONObject item = new JSONObject();
            item.put("id", page.id);
            item.put("title", page.title);
            item.put("paperType", page.paperType);
            pages.put(item);
        }
        root.put("pages", pages);
        return root.toString();
    }

    private static NotebookMeta parseNotebook(String json) throws Exception {
        JSONObject root = new JSONObject(json);

        NotebookMeta notebook = new NotebookMeta();
        notebook.id = root.getString("id");
        notebook.title = clean(root.optString("title"), "Notebook");
        notebook.subject = clean(root.optString("subject"), "General Study");
        notebook.updatedAt = root.optLong("updatedAt", 0L);

        JSONArray pages = root.optJSONArray("pages");
        if (pages != null) {
            for (int i = 0; i < pages.length(); i++) {
                JSONObject item = pages.getJSONObject(i);
                notebook.pages.add(new PageMeta(
                        item.getString("id"),
                        clean(item.optString("title"), "Page " + (i + 1)),
                        normalizePaper(item.optString("paperType", NoteCanvasView.PAPER_RULED))
                ));
            }
        }

        if (notebook.pages.isEmpty()) {
            notebook.pages.add(new PageMeta(
                    UUID.randomUUID().toString(),
                    "Page 1",
                    NoteCanvasView.PAPER_RULED
            ));
        }

        return notebook;
    }

    private static String normalizePaper(String value) {
        if (NoteCanvasView.PAPER_BLANK.equals(value)
                || NoteCanvasView.PAPER_RULED.equals(value)
                || NoteCanvasView.PAPER_GRAPH.equals(value)) {
            return value;
        }
        return NoteCanvasView.PAPER_RULED;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static void writeText(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
