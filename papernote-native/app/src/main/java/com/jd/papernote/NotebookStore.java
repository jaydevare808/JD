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

    public static final class StudyMark {
        public static final String DOUBT = "DOUBT";
        public static final String MISTAKE = "MISTAKE";
        public static final String IMPORTANT = "IMPORTANT";
        public static final String REVISE = "REVISE";

        public String id;
        public String pageId;
        public String type;
        public float x;
        public float y;
        public String note;
        public boolean resolved;
        public long createdAt;

        public StudyMark(String id, String pageId, String type, float x, float y, String note,
                         boolean resolved, long createdAt) {
            this.id = id;
            this.pageId = pageId;
            this.type = type;
            this.x = x;
            this.y = y;
            this.note = note;
            this.resolved = resolved;
            this.createdAt = createdAt;
        }
    }

    public static final class StudyLink {
        public String id;
        public String sourcePageId;
        public String targetPageId;
        public String label;
        public long createdAt;

        public StudyLink(String id, String sourcePageId, String targetPageId, String label, long createdAt) {
            this.id = id;
            this.sourcePageId = sourcePageId;
            this.targetPageId = targetPageId;
            this.label = label;
            this.createdAt = createdAt;
        }
    }

    public static final class PageStats {
        public int strokes;
        public long activeMs;
        public long lastActivityAt;
        public final int[] heatmap = new int[48];

        public PageStats copy() {
            PageStats copy = new PageStats();
            copy.strokes = strokes;
            copy.activeMs = activeMs;
            copy.lastActivityAt = lastActivityAt;
            System.arraycopy(heatmap, 0, copy.heatmap, 0, heatmap.length);
            return copy;
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
    private final File studyFile;
    private JSONObject studyRoot;

    public NotebookStore(Context context) {
        File root = new File(context.getFilesDir(), "papernote");
        notebooksDir = new File(root, "notebooks");
        pagesDir = new File(root, "pages");
        studyFile = new File(root, "study_features.json");
        if (!notebooksDir.exists()) notebooksDir.mkdirs();
        if (!pagesDir.exists()) pagesDir.mkdirs();
        try {
            studyRoot = studyFile.exists() ? new JSONObject(readText(studyFile)) : new JSONObject();
        } catch (Exception ignored) {
            studyRoot = new JSONObject();
        }
    }

    public List<NotebookMeta> list() {
        ArrayList<NotebookMeta> result = new ArrayList<>();
        File[] files = notebooksDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try {
                NotebookMeta meta = parseNotebook(readText(file));
                result.add(meta);
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
        meta.title = title == null || title.trim().isEmpty() ? "New Notebook" : title.trim();
        meta.subject = subject == null ? "General Study" : subject.trim();
        meta.updatedAt = System.currentTimeMillis();
        meta.pages.add(new PageMeta(UUID.randomUUID().toString(), "Page 1", paperType));
        save(meta);
        return meta;
    }

    public PageMeta addPage(NotebookMeta notebook, String title, String paperType) {
        PageMeta page = new PageMeta(
                UUID.randomUUID().toString(),
                title == null || title.trim().isEmpty() ? "Page " + (notebook.pages.size() + 1) : title.trim(),
                paperType
        );
        notebook.pages.add(page);
        notebook.updatedAt = System.currentTimeMillis();
        return page;
    }

    public void removePage(NotebookMeta notebook, int index) {
        if (notebook.pages.size() <= 1 || index < 0 || index >= notebook.pages.size()) return;
        String pageId = notebook.pages.get(index).id;
        notebook.pages.remove(index);
        deletePageBitmap(pageId);
        deletePageGhostSnapshot(pageId);
        notebook.updatedAt = System.currentTimeMillis();
    }

    public void deleteNotebook(NotebookMeta notebook) {
        if (notebook == null) return;
        for (PageMeta page : notebook.pages) deletePageBitmap(page.id);
        File meta = metadataFile(notebook.id);
        if (meta.exists()) meta.delete();
        removeStudyFeaturesForNotebook(notebook);
    }

    public void renameNotebook(NotebookMeta notebook, String title, String subject) throws Exception {
        if (title != null && !title.trim().isEmpty()) notebook.title = title.trim();
        if (subject != null && !subject.trim().isEmpty()) notebook.subject = subject.trim();
        notebook.updatedAt = System.currentTimeMillis();
        save(notebook);
    }

    public void renamePage(NotebookMeta notebook, int index, String title) throws Exception {
        if (index < 0 || index >= notebook.pages.size()) return;
        if (title != null && !title.trim().isEmpty()) {
            notebook.pages.get(index).title = title.trim();
            notebook.updatedAt = System.currentTimeMillis();
            save(notebook);
        }
    }

    public void save(NotebookMeta meta) throws Exception {
        meta.updatedAt = System.currentTimeMillis();
        File target = metadataFile(meta.id);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        writeText(tmp, serializeNotebook(meta));
        if (target.exists() && !target.delete()) throw new Exception("Unable to replace notebook metadata");
        if (!tmp.renameTo(target)) throw new Exception("Unable to commit notebook metadata");
    }

    public Bitmap loadPageBitmap(String pageId, int width, int height) {
        File file = pageFile(pageId);
        if (!file.exists()) return newTransparentBitmap(width, height);

        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded == null) return newTransparentBitmap(width, height);

        if (decoded.getWidth() == width && decoded.getHeight() == height) return decoded;
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    public void savePageBitmap(String pageId, Bitmap bitmap) throws Exception {
        File target = pageFile(pageId);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new Exception("PNG compression failed");
        }
        if (target.exists() && !target.delete()) throw new Exception("Unable to replace page");
        if (!tmp.renameTo(target)) throw new Exception("Unable to commit page");
    }

    public void deletePageBitmap(String pageId) {
        File file = pageFile(pageId);
        if (file.exists()) file.delete();
    }

    public String exportBackup(NotebookMeta meta, int width, int height) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "PaperNoteBackup");
        root.put("version", 1);
        root.put("id", meta.id);
        root.put("title", meta.title);
        root.put("subject", meta.subject);
        JSONArray pages = new JSONArray();

        for (PageMeta page : meta.pages) {
            JSONObject item = new JSONObject();
            item.put("id", page.id);
            item.put("title", page.title);
            item.put("paperType", page.paperType);
            Bitmap bitmap = loadPageBitmap(page.id, width, height);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            item.put("pngBase64", android.util.Base64.encodeToString(png.toByteArray(), android.util.Base64.NO_WRAP));
            pages.put(item);
            bitmap.recycle();
        }

        root.put("pages", pages);
        root.put("studyFeatures", new JSONObject(exportStudyFeatures(meta)));
        return root.toString();
    }

    public NotebookMeta importBackup(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"PaperNoteBackup".equals(root.optString("format"))) throw new Exception("Not a PaperNote backup");

        NotebookMeta meta = new NotebookMeta();
        meta.id = UUID.randomUUID().toString();
        meta.title = root.optString("title", "Restored Notebook") + " (Restored)";
        meta.subject = root.optString("subject", "General Study");
        meta.updatedAt = System.currentTimeMillis();

        JSONArray pages = root.getJSONArray("pages");
        for (int i = 0; i < pages.length(); i++) {
            JSONObject item = pages.getJSONObject(i);
            PageMeta page = new PageMeta(
                    UUID.randomUUID().toString(),
                    item.optString("title", "Page " + (i + 1)),
                    item.optString("paperType", PaperCanvasView.PAPER_RULED)
            );
            meta.pages.add(page);

            byte[] png = android.util.Base64.decode(item.getString("pngBase64"), android.util.Base64.NO_WRAP);
            try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) throw new Exception("Invalid page image");
                savePageBitmap(page.id, bitmap);
                bitmap.recycle();
            }
        }

        importStudyFeatures(meta, root.optJSONObject("studyFeatures"));
        save(meta);
        return meta;
    }

    public synchronized List<StudyMark> getStudyMarks(String pageId) {
        ArrayList<StudyMark> result = new ArrayList<>();
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) return result;
        for (int i = 0; i < marks.length(); i++) {
            JSONObject item = marks.optJSONObject(i);
            if (item == null || !pageId.equals(item.optString("pageId"))) continue;
            result.add(parseStudyMark(item));
        }
        result.sort(Comparator.comparingLong(m -> m.createdAt));
        return result;
    }

    public synchronized StudyMark addStudyMark(String pageId, String type, float x, float y, String note) throws Exception {
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) {
            marks = new JSONArray();
            studyRoot.put("marks", marks);
        }
        StudyMark mark = new StudyMark(
                UUID.randomUUID().toString(),
                pageId,
                type,
                x,
                y,
                note == null ? "" : note.trim(),
                false,
                System.currentTimeMillis()
        );
        marks.put(studyMarkJson(mark));
        saveStudyRoot();
        return mark;
    }

    public synchronized void setStudyMarkResolved(String pageId, String markId, boolean resolved) throws Exception {
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) return;
        for (int i = 0; i < marks.length(); i++) {
            JSONObject item = marks.optJSONObject(i);
            if (item != null && markId.equals(item.optString("id"))
                    && pageId.equals(item.optString("pageId"))) {
                item.put("resolved", resolved);
                saveStudyRoot();
                return;
            }
        }
    }

    public synchronized void deleteStudyMark(String pageId, String markId) throws Exception {
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) return;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < marks.length(); i++) {
            JSONObject item = marks.optJSONObject(i);
            if (item == null) continue;
            if (!(markId.equals(item.optString("id")) && pageId.equals(item.optString("pageId")))) {
                kept.put(item);
            }
        }
        studyRoot.put("marks", kept);
        saveStudyRoot();
    }

    public synchronized PageStats getPageStats(String pageId) {
        JSONObject stats = studyRoot.optJSONObject("stats");
        if (stats == null) return new PageStats();
        return parsePageStats(stats.optJSONObject(pageId));
    }

    public synchronized void startPageSession(String pageId) {
        try {
            JSONObject stats = ensureStatsObject(pageId);
            PageStats value = parsePageStats(stats);
            value.lastActivityAt = System.currentTimeMillis();
            writePageStats(stats, value);
            saveStudyRoot();
        } catch (Exception ignored) {
        }
    }

    public synchronized void recordPageActivity(String pageId) {
        try {
            JSONObject statsObject = ensureStatsObject(pageId);
            PageStats value = parsePageStats(statsObject);
            long now = System.currentTimeMillis();
            if (value.lastActivityAt > 0L) {
                long delta = Math.max(0L, now - value.lastActivityAt);
                value.activeMs += Math.min(delta, 15000L);
            }
            value.lastActivityAt = now;
            writePageStats(statsObject, value);
            saveStudyRoot();
        } catch (Exception ignored) {
        }
    }

    public synchronized void flushPageActivity(String pageId) {
        try {
            JSONObject statsObject = ensureStatsObject(pageId);
            PageStats value = parsePageStats(statsObject);
            long now = System.currentTimeMillis();
            if (value.lastActivityAt > 0L) {
                long delta = Math.max(0L, now - value.lastActivityAt);
                value.activeMs += Math.min(delta, 15000L);
            }
            value.lastActivityAt = 0L;
            writePageStats(statsObject, value);
            saveStudyRoot();
        } catch (Exception ignored) {
        }
    }

    public synchronized void recordStroke(String pageId, float pageX, float pageY) {
        try {
            JSONObject statsObject = ensureStatsObject(pageId);
            PageStats value = parsePageStats(statsObject);
            value.strokes++;
            int gx = Math.max(0, Math.min(5, (int) (pageX / PaperCanvasView.PAGE_WIDTH * 6f)));
            int gy = Math.max(0, Math.min(7, (int) (pageY / PaperCanvasView.PAGE_HEIGHT * 8f)));
            value.heatmap[gy * 6 + gx]++;
            writePageStats(statsObject, value);
            saveStudyRoot();
        } catch (Exception ignored) {
        }
    }

    public synchronized List<StudyLink> getStudyLinksForPage(String pageId) {
        ArrayList<StudyLink> result = new ArrayList<>();
        JSONArray links = studyRoot.optJSONArray("links");
        if (links == null) return result;
        for (int i = 0; i < links.length(); i++) {
            JSONObject item = links.optJSONObject(i);
            if (item == null) continue;
            if (pageId.equals(item.optString("sourcePageId"))
                    || pageId.equals(item.optString("targetPageId"))) {
                result.add(parseStudyLink(item));
            }
        }
        result.sort(Comparator.comparingLong(l -> l.createdAt));
        return result;
    }

    public synchronized StudyLink addStudyLink(String sourcePageId, String targetPageId, String label) throws Exception {
        JSONArray links = studyRoot.optJSONArray("links");
        if (links == null) {
            links = new JSONArray();
            studyRoot.put("links", links);
        }
        StudyLink link = new StudyLink(
                UUID.randomUUID().toString(),
                sourcePageId,
                targetPageId,
                label == null || label.trim().isEmpty() ? "Concept connection" : label.trim(),
                System.currentTimeMillis()
        );
        links.put(studyLinkJson(link));
        saveStudyRoot();
        return link;
    }

    public synchronized int countStudyMarks(NotebookMeta notebook, String type, boolean unresolvedOnly) {
        if (notebook == null) return 0;
        java.util.HashSet<String> pageIds = new java.util.HashSet<>();
        for (PageMeta page : notebook.pages) pageIds.add(page.id);
        int count = 0;
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) return 0;
        for (int i = 0; i < marks.length(); i++) {
            JSONObject item = marks.optJSONObject(i);
            if (item == null || !pageIds.contains(item.optString("pageId"))) continue;
            if (type != null && !type.equals(item.optString("type"))) continue;
            if (unresolvedOnly && item.optBoolean("resolved", false)) continue;
            count++;
        }
        return count;
    }

    public synchronized long getNotebookActiveMs(NotebookMeta notebook) {
        if (notebook == null) return 0L;
        long total = 0L;
        for (PageMeta page : notebook.pages) total += getPageStats(page.id).activeMs;
        return total;
    }

    public synchronized int getNotebookStrokeCount(NotebookMeta notebook) {
        if (notebook == null) return 0;
        int total = 0;
        for (PageMeta page : notebook.pages) total += getPageStats(page.id).strokes;
        return total;
    }

    public synchronized String exportStudyFeatures(NotebookMeta notebook) throws Exception {
        JSONObject root = new JSONObject();
        if (notebook == null) return root.toString();

        java.util.HashMap<String, Integer> pageIndex = new java.util.HashMap<>();
        for (int i = 0; i < notebook.pages.size(); i++) pageIndex.put(notebook.pages.get(i).id, i);

        JSONArray marksOut = new JSONArray();
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks != null) {
            for (int i = 0; i < marks.length(); i++) {
                JSONObject item = marks.optJSONObject(i);
                if (item == null || !pageIndex.containsKey(item.optString("pageId"))) continue;
                JSONObject copy = new JSONObject(item.toString());
                copy.remove("id");
                copy.put("pageIndex", pageIndex.get(item.optString("pageId")));
                marksOut.put(copy);
            }
        }
        root.put("marks", marksOut);

        JSONArray statsOut = new JSONArray();
        JSONObject stats = studyRoot.optJSONObject("stats");
        if (stats != null) {
            for (PageMeta page : notebook.pages) {
                JSONObject item = stats.optJSONObject(page.id);
                if (item == null) continue;
                JSONObject copy = new JSONObject(item.toString());
                copy.put("pageIndex", pageIndex.get(page.id));
                statsOut.put(copy);
            }
        }
        root.put("stats", statsOut);

        JSONArray linksOut = new JSONArray();
        JSONArray links = studyRoot.optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) continue;
                String from = item.optString("sourcePageId");
                String to = item.optString("targetPageId");
                if (!pageIndex.containsKey(from) || !pageIndex.containsKey(to)) continue;
                JSONObject copy = new JSONObject(item.toString());
                copy.remove("id");
                copy.remove("sourcePageId");
                copy.remove("targetPageId");
                copy.put("sourcePageIndex", pageIndex.get(from));
                copy.put("targetPageIndex", pageIndex.get(to));
                linksOut.put(copy);
            }
        }
        root.put("links", linksOut);
        return root.toString();
    }

    public synchronized void importStudyFeatures(NotebookMeta notebook, JSONObject data) throws Exception {
        if (notebook == null || data == null) return;
        JSONArray marks = studyRoot.optJSONArray("marks");
        if (marks == null) {
            marks = new JSONArray();
            studyRoot.put("marks", marks);
        }
        JSONArray marksIn = data.optJSONArray("marks");
        if (marksIn != null) {
            for (int i = 0; i < marksIn.length(); i++) {
                JSONObject item = marksIn.optJSONObject(i);
                if (item == null) continue;
                int index = item.optInt("pageIndex", -1);
                if (index < 0 || index >= notebook.pages.size()) continue;
                JSONObject out = new JSONObject();
                out.put("id", UUID.randomUUID().toString());
                out.put("pageId", notebook.pages.get(index).id);
                out.put("type", item.optString("type", StudyMark.REVISE));
                out.put("x", item.optDouble("x", 100));
                out.put("y", item.optDouble("y", 100));
                out.put("note", item.optString("note", ""));
                out.put("resolved", item.optBoolean("resolved", false));
                out.put("createdAt", item.optLong("createdAt", System.currentTimeMillis()));
                marks.put(out);
            }
        }

        JSONObject statsRoot = studyRoot.optJSONObject("stats");
        if (statsRoot == null) {
            statsRoot = new JSONObject();
            studyRoot.put("stats", statsRoot);
        }
        JSONArray statsIn = data.optJSONArray("stats");
        if (statsIn != null) {
            for (int i = 0; i < statsIn.length(); i++) {
                JSONObject item = statsIn.optJSONObject(i);
                if (item == null) continue;
                int index = item.optInt("pageIndex", -1);
                if (index < 0 || index >= notebook.pages.size()) continue;
                JSONObject copy = new JSONObject(item.toString());
                copy.remove("pageIndex");
                copy.remove("lastActivityAt");
                statsRoot.put(notebook.pages.get(index).id, copy);
            }
        }

        JSONArray links = studyRoot.optJSONArray("links");
        if (links == null) {
            links = new JSONArray();
            studyRoot.put("links", links);
        }
        JSONArray linksIn = data.optJSONArray("links");
        if (linksIn != null) {
            for (int i = 0; i < linksIn.length(); i++) {
                JSONObject item = linksIn.optJSONObject(i);
                if (item == null) continue;
                int from = item.optInt("sourcePageIndex", -1);
                int to = item.optInt("targetPageIndex", -1);
                if (from < 0 || from >= notebook.pages.size() || to < 0 || to >= notebook.pages.size()) continue;
                JSONObject out = new JSONObject();
                out.put("id", UUID.randomUUID().toString());
                out.put("sourcePageId", notebook.pages.get(from).id);
                out.put("targetPageId", notebook.pages.get(to).id);
                out.put("label", item.optString("label", "Concept connection"));
                out.put("createdAt", item.optLong("createdAt", System.currentTimeMillis()));
                links.put(out);
            }
        }
        saveStudyRoot();
    }

    public synchronized void removeStudyFeaturesForNotebook(NotebookMeta notebook) {
        try {
            if (notebook == null) return;
            java.util.HashSet<String> pageIds = new java.util.HashSet<>();
            for (PageMeta page : notebook.pages) pageIds.add(page.id);

            JSONArray marksOut = new JSONArray();
            JSONArray marks = studyRoot.optJSONArray("marks");
            if (marks != null) {
                for (int i = 0; i < marks.length(); i++) {
                    JSONObject item = marks.optJSONObject(i);
                    if (item != null && !pageIds.contains(item.optString("pageId"))) marksOut.put(item);
                }
            }
            studyRoot.put("marks", marksOut);

            JSONObject statsOut = new JSONObject();
            JSONObject stats = studyRoot.optJSONObject("stats");
            if (stats != null) {
                for (String key : stats.keySet()) {
                    if (!pageIds.contains(key)) statsOut.put(key, stats.optJSONObject(key));
                }
            }
            studyRoot.put("stats", statsOut);

            JSONArray linksOut = new JSONArray();
            JSONArray links = studyRoot.optJSONArray("links");
            if (links != null) {
                for (int i = 0; i < links.length(); i++) {
                    JSONObject item = links.optJSONObject(i);
                    if (item == null) continue;
                    if (!pageIds.contains(item.optString("sourcePageId"))
                            && !pageIds.contains(item.optString("targetPageId"))) {
                        linksOut.put(item);
                    }
                }
            }
            studyRoot.put("links", linksOut);
            saveStudyRoot();
        } catch (Exception ignored) {
        }
    }

    public synchronized void savePageGhostSnapshot(String pageId, Bitmap bitmap) throws Exception {
        if (bitmap == null) return;
        File target = ghostFile(pageId);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new Exception("Ghost snapshot compression failed");
            }
        }
        if (target.exists() && !target.delete()) throw new Exception("Unable to replace ghost snapshot");
        if (!tmp.renameTo(target)) throw new Exception("Unable to commit ghost snapshot");
    }

    public Bitmap loadPageGhostSnapshot(String pageId, int width, int height) {
        File file = ghostFile(pageId);
        if (!file.exists()) return null;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded == null) return null;
        if (decoded.getWidth() == width && decoded.getHeight() == height) return decoded;
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    public void deletePageGhostSnapshot(String pageId) {
        File file = ghostFile(pageId);
        if (file.exists()) file.delete();
    }

    private JSONObject ensureStatsObject(String pageId) throws Exception {
        JSONObject stats = studyRoot.optJSONObject("stats");
        if (stats == null) {
            stats = new JSONObject();
            studyRoot.put("stats", stats);
        }
        JSONObject item = stats.optJSONObject(pageId);
        if (item == null) {
            item = new JSONObject();
            item.put("strokes", 0);
            item.put("activeMs", 0L);
            item.put("lastActivityAt", 0L);
            JSONArray heat = new JSONArray();
            for (int i = 0; i < 48; i++) heat.put(0);
            item.put("heatmap", heat);
            stats.put(pageId, item);
        }
        return item;
    }

    private PageStats parsePageStats(JSONObject item) {
        PageStats result = new PageStats();
        if (item == null) return result;
        result.strokes = item.optInt("strokes", 0);
        result.activeMs = item.optLong("activeMs", 0L);
        result.lastActivityAt = item.optLong("lastActivityAt", 0L);
        JSONArray heat = item.optJSONArray("heatmap");
        if (heat != null) {
            for (int i = 0; i < Math.min(48, heat.length()); i++) result.heatmap[i] = heat.optInt(i, 0);
        }
        return result;
    }

    private void writePageStats(JSONObject item, PageStats value) throws Exception {
        item.put("strokes", value.strokes);
        item.put("activeMs", value.activeMs);
        item.put("lastActivityAt", value.lastActivityAt);
        JSONArray heat = new JSONArray();
        for (int v : value.heatmap) heat.put(v);
        item.put("heatmap", heat);
    }

    private StudyMark parseStudyMark(JSONObject item) {
        return new StudyMark(
                item.optString("id", UUID.randomUUID().toString()),
                item.optString("pageId"),
                item.optString("type", StudyMark.REVISE),
                (float) item.optDouble("x", 100),
                (float) item.optDouble("y", 100),
                item.optString("note", ""),
                item.optBoolean("resolved", false),
                item.optLong("createdAt", System.currentTimeMillis())
        );
    }

    private JSONObject studyMarkJson(StudyMark mark) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", mark.id);
        item.put("pageId", mark.pageId);
        item.put("type", mark.type);
        item.put("x", mark.x);
        item.put("y", mark.y);
        item.put("note", mark.note);
        item.put("resolved", mark.resolved);
        item.put("createdAt", mark.createdAt);
        return item;
    }

    private StudyLink parseStudyLink(JSONObject item) {
        return new StudyLink(
                item.optString("id", UUID.randomUUID().toString()),
                item.optString("sourcePageId"),
                item.optString("targetPageId"),
                item.optString("label", "Concept connection"),
                item.optLong("createdAt", System.currentTimeMillis())
        );
    }

    private JSONObject studyLinkJson(StudyLink link) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", link.id);
        item.put("sourcePageId", link.sourcePageId);
        item.put("targetPageId", link.targetPageId);
        item.put("label", link.label);
        item.put("createdAt", link.createdAt);
        return item;
    }

    private void saveStudyRoot() throws Exception {
        File tmp = new File(studyFile.getParentFile(), studyFile.getName() + ".tmp");
        writeText(tmp, studyRoot.toString());
        if (studyFile.exists() && !studyFile.delete()) throw new Exception("Unable to replace study data");
        if (!tmp.renameTo(studyFile)) throw new Exception("Unable to commit study data");
    }

    private File ghostFile(String id) {
        return new File(pagesDir, id + ".ghost.png");
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
        meta.title = root.optString("title", "Notebook");
        meta.subject = root.optString("subject", "General Study");
        meta.updatedAt = root.optLong("updatedAt", 0L);

        JSONArray pages = root.optJSONArray("pages");
        if (pages != null) {
            for (int i = 0; i < pages.length(); i++) {
                JSONObject item = pages.getJSONObject(i);
                meta.pages.add(new PageMeta(
                        item.getString("id"),
                        item.optString("title", "Page " + (i + 1)),
                        item.optString("paperType", PaperCanvasView.PAPER_RULED)
                ));
            }
        }

        if (meta.pages.isEmpty()) {
            meta.pages.add(new PageMeta(UUID.randomUUID().toString(), "Page 1", PaperCanvasView.PAPER_RULED));
        }

        return meta;
    }
}
