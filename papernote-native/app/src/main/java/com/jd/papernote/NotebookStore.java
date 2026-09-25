package com.jd.papernote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
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
        File root = new File(context.getFilesDir(), "papernote");
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
        meta.title = cleanOrDefault(title, "New Notebook");
        meta.subject = cleanOrDefault(subject, "General Study");
        meta.updatedAt = System.currentTimeMillis();
        meta.pages.add(new PageMeta(
                UUID.randomUUID().toString(),
                "Page 1",
                normalizePaperType(paperType)
        ));
        save(meta);
        return meta;
    }

    public PageMeta addPage(NotebookMeta notebook, String title, String paperType) {
        PageMeta page = new PageMeta(
                UUID.randomUUID().toString(),
                cleanOrDefault(title, "Page " + (notebook.pages.size() + 1)),
                normalizePaperType(paperType)
        );
        notebook.pages.add(page);
        notebook.updatedAt = System.currentTimeMillis();
        return page;
    }

    public void removePage(NotebookMeta notebook, int index) {
        if (notebook == null || notebook.pages.size() <= 1
                || index < 0 || index >= notebook.pages.size()) return;

        String pageId = notebook.pages.get(index).id;
        notebook.pages.remove(index);
        deletePageBitmap(pageId);
        deleteOldFeatureFiles(pageId);
        notebook.updatedAt = System.currentTimeMillis();
    }

    public void deleteNotebook(NotebookMeta notebook) {
        if (notebook == null) return;

        for (PageMeta page : notebook.pages) {
            deletePageBitmap(page.id);
            deleteOldFeatureFiles(page.id);
        }

        File meta = metadataFile(notebook.id);
        if (meta.exists()) meta.delete();
    }

    public void renameNotebook(NotebookMeta notebook, String title, String subject) throws Exception {
        if (title != null && !title.trim().isEmpty()) notebook.title = title.trim();
        if (subject != null && !subject.trim().isEmpty()) notebook.subject = subject.trim();
        notebook.updatedAt = System.currentTimeMillis();
        save(notebook);
    }

    public void renamePage(NotebookMeta notebook, int index, String title) throws Exception {
        if (notebook == null || index < 0 || index >= notebook.pages.size()) return;
        if (title != null && !title.trim().isEmpty()) {
            notebook.pages.get(index).title = title.trim();
            notebook.updatedAt = System.currentTimeMillis();
            save(notebook);
        }
    }

    public void save(NotebookMeta meta) throws Exception {
        if (meta == null || meta.id == null || meta.id.trim().isEmpty()) {
            throw new Exception("Invalid notebook");
        }

        meta.updatedAt = System.currentTimeMillis();
        File target = metadataFile(meta.id);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        writeText(tmp, serializeNotebook(meta));

        if (target.exists() && !target.delete()) {
            throw new Exception("Unable to replace notebook metadata");
        }
        if (!tmp.renameTo(target)) {
            throw new Exception("Unable to commit notebook metadata");
        }
    }

    public Bitmap loadPageBitmap(String pageId, int width, int height) {
        File file = pageFile(pageId);
        if (!file.exists()) return newTransparentBitmap(width, height);

        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded == null) return newTransparentBitmap(width, height);

        if (decoded.getWidth() == width && decoded.getHeight() == height) {
            return decoded;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    public void savePageBitmap(String pageId, Bitmap bitmap) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) throw new Exception("Invalid page image");

        File target = pageFile(pageId);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new Exception("PNG compression failed");
            }
            out.flush();
        }

        if (target.exists() && !target.delete()) {
            throw new Exception("Unable to replace page");
        }
        if (!tmp.renameTo(target)) {
            throw new Exception("Unable to commit page");
        }
    }

    public void deletePageBitmap(String pageId) {
        File file = pageFile(pageId);
        if (file.exists()) file.delete();
    }

    /**
     * Backup remains intentionally simple and backward compatible.
     * Unknown fields from older PaperNote builds are ignored on import.
     */
    public String exportBackup(NotebookMeta meta, int width, int height) throws Exception {
        if (meta == null) throw new Exception("No notebook selected");

        JSONObject root = new JSONObject();
        root.put("format", "PaperNoteBackup");
        root.put("version", 2);
        root.put("id", meta.id);
        root.put("title", meta.title);
        root.put("subject", meta.subject);

        JSONArray pages = new JSONArray();
        for (PageMeta page : meta.pages) {
            JSONObject item = new JSONObject();
            item.put("id", page.id);
            item.put("title", page.title);
            item.put("paperType", normalizePaperType(page.paperType));

            Bitmap bitmap = loadPageBitmap(page.id, width, height);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                    throw new Exception("Could not encode " + page.title);
                }
                item.put("pngBase64", android.util.Base64.encodeToString(
                        png.toByteArray(), android.util.Base64.NO_WRAP));
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            pages.put(item);
        }

        root.put("pages", pages);
        return root.toString();
    }

    public NotebookMeta importBackup(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"PaperNoteBackup".equals(root.optString("format"))) {
            throw new Exception("Not a PaperNote backup");
        }

        JSONArray pages = root.getJSONArray("pages");
        if (pages.length() == 0) throw new Exception("Backup contains no pages.");

        NotebookMeta meta = new NotebookMeta();
        meta.id = UUID.randomUUID().toString();
        meta.title = cleanOrDefault(root.optString("title"), "Restored Notebook") + " (Restored)";
        meta.subject = cleanOrDefault(root.optString("subject"), "General Study");
        meta.updatedAt = System.currentTimeMillis();

        for (int i = 0; i < pages.length(); i++) {
            JSONObject item = pages.getJSONObject(i);
            String pageId = UUID.randomUUID().toString();
            PageMeta page = new PageMeta(
                    pageId,
                    cleanOrDefault(item.optString("title"), "Page " + (i + 1)),
                    normalizePaperType(item.optString("paperType", PaperCanvasView.PAPER_RULED))
            );
            meta.pages.add(page);

            byte[] png = android.util.Base64.decode(
                    item.getString("pngBase64"), android.util.Base64.NO_WRAP);
            try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) throw new Exception("Invalid page image in backup.");
                try {
                    savePageBitmap(page.id, bitmap);
                } finally {
                    bitmap.recycle();
                }
            }
        }

        save(meta);
        return meta;
    }

    private String normalizePaperType(String type) {
        if (PaperCanvasView.PAPER_BLANK.equals(type)
                || PaperCanvasView.PAPER_RULED.equals(type)
                || PaperCanvasView.PAPER_GRAPH.equals(type)
                || PaperCanvasView.PAPER_DOT.equals(type)
                || PaperCanvasView.PAPER_MATH.equals(type)) {
            return type;
        }
        return PaperCanvasView.PAPER_RULED;
    }

    private void deleteOldFeatureFiles(String pageId) {
        File pageDir = pagesDir;
        String[] oldSuffixes = {".ghost.png"};
        for (String suffix : oldSuffixes) {
            File file = new File(pageDir, pageId + suffix);
            if (file.exists()) file.delete();
        }
    }

    private File metadataFile(String id) {
        return new File(notebooksDir, id + ".json");
    }

    private File pageFile(String id) {
        return new File(pagesDir, id + ".png");
    }

    private static Bitmap newTransparentBitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private static String writeTempAndRead(String ignored) {
        return ignored;
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
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String serializeNotebook(NotebookMeta meta) throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", meta.id);
        root.put("title", meta.title);
        root.put("subject", meta.subject);
        root.put("updatedAt", meta.updatedAt);

        JSONArray pages = new JSONArray();
        for (PageMeta page : meta.pages) {
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

        NotebookMeta meta = new NotebookMeta();
        meta.id = root.getString("id");
        meta.title = cleanOrDefault(root.optString("title"), "Notebook");
        meta.subject = cleanOrDefault(root.optString("subject"), "General Study");
        meta.updatedAt = root.optLong("updatedAt", 0L);

        JSONArray pages = root.optJSONArray("pages");
        if (pages != null) {
            for (int i = 0; i < pages.length(); i++) {
                JSONObject item = pages.getJSONObject(i);
                meta.pages.add(new PageMeta(
                        item.getString("id"),
                        cleanOrDefault(item.optString("title"), "Page " + (i + 1)),
                        normalizePaperTypeStatic(item.optString("paperType", PaperCanvasView.PAPER_RULED))
                ));
            }
        }

        if (meta.pages.isEmpty()) {
            meta.pages.add(new PageMeta(
                    UUID.randomUUID().toString(),
                    "Page 1",
                    PaperCanvasView.PAPER_RULED
            ));
        }
        return meta;
    }

    private static String normalizePaperTypeStatic(String type) {
        if (PaperCanvasView.PAPER_BLANK.equals(type)
                || PaperCanvasView.PAPER_RULED.equals(type)
                || PaperCanvasView.PAPER_GRAPH.equals(type)
                || PaperCanvasView.PAPER_DOT.equals(type)
                || PaperCanvasView.PAPER_MATH.equals(type)) {
            return type;
        }
        return PaperCanvasView.PAPER_RULED;
    }

    private static String cleanOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}
