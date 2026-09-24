package com.jd.papernote;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class StudyHubActivity extends Activity {
    private NotebookStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        build();
    }

    private void build() {
        int bg = 0xFFF5F7FB;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackgroundColor(0xFF182339);

        Button back = button("‹", true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Study Hub", 21, Color.WHITE, true);
        TextView sub = text("Your local PaperNote learning dashboard", 12, 0xFFC9D2E1, false);
        titleBox.addView(title);
        titleBox.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, tp);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));

        List<NotebookStore.NotebookMeta> notebooks = store.list();
        int pages = 0;
        int strokes = 0;
        long activeMs = 0L;
        int doubts = 0;
        int mistakes = 0;
        int important = 0;
        int revise = 0;
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            pages += notebook.pages.size();
            strokes += store.getNotebookStrokeCount(notebook);
            activeMs += store.getNotebookActiveMs(notebook);
            doubts += store.countStudyMarks(notebook, NotebookStore.StudyMark.DOUBT, true);
            mistakes += store.countStudyMarks(notebook, NotebookStore.StudyMark.MISTAKE, true);
            important += store.countStudyMarks(notebook, NotebookStore.StudyMark.IMPORTANT, true);
            revise += store.countStudyMarks(notebook, NotebookStore.StudyMark.REVISE, true);
        }

        LinearLayout summary = card();
        summary.addView(text("MY STUDY SIGNALS", 12, 0xFF667085, true));
        summary.addView(text(formatStats(pages, strokes, activeMs), 18, 0xFF182339, true),
                new LinearLayout.LayoutParams(-1, dp(46)));
        summary.addView(text(
                doubts + " unresolved doubts  •  " + mistakes + " mistakes  •  " +
                        important + " important  •  " + revise + " revise marks",
                12, 0xFF5B6473, false
        ));
        content.addView(summary, marginParams(dp(10)));

        TextView rhythmTitle = text("14-DAY STUDY RHYTHM", 12, 0xFF667085, true);
        content.addView(rhythmTitle, marginParams(dp(8)));
        LinearLayout rhythmCard = card();
        List<NotebookStore.DailyStudy> daily = store.getDailyStudy(14);
        long maxDailyMs = 1L;
        int totalDailyStrokes = 0;
        for (NotebookStore.DailyStudy day : daily) {
            maxDailyMs = Math.max(maxDailyMs, day.activeMs);
            totalDailyStrokes += day.strokes;
        }
        DailyRhythmView rhythmView = new DailyRhythmView(this, daily, maxDailyMs);
        rhythmCard.addView(rhythmView, new LinearLayout.LayoutParams(-1, dp(150)));
        rhythmCard.addView(text(
                "Recent 14 days  •  " + formatDuration(daily.stream().mapToLong(d -> d.activeMs).sum()) +
                        " active  •  " + totalDailyStrokes + " strokes",
                12, 0xFF5B6473, false
        ));
        rhythmCard.addView(text(
                "This is a local activity history, not a measure of marks, intelligence, or academic performance.",
                10, 0xFF7A8495, false
        ));
        content.addView(rhythmCard, marginParams(dp(10)));

        TextView nextTitle = text("NEXT ACTIONS", 12, 0xFF667085, true);
        content.addView(nextTitle, marginParams(dp(8)));

        ArrayList<MarkRow> marks = collectMarks(notebooks);
        if (marks.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text(
                    "No study marks yet. In a notebook, use STUDY → Study marks to pin doubts, mistakes, important ideas and revision targets directly on the page.",
                    13, 0xFF5B6473, false
            ));
            content.addView(empty, marginParams(dp(8)));
        } else {
            int shown = 0;
            for (MarkRow row : marks) {
                if (row.mark.resolved) continue;
                LinearLayout item = card();
                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = text(markLabel(row.mark.type), 15, 0xFF182339, true);
                top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
                Button open = button("Open", false);
                open.setOnClickListener(v -> {
                    IntentBuilder.openNotebook(this, row.notebook.id, row.pageIndex);
                });
                top.addView(open, new LinearLayout.LayoutParams(dp(76), dp(40)));
                item.addView(top);
                item.addView(text(
                        row.notebook.title + "  •  " + row.page.title,
                        12, 0xFF6B7280, false
                ));
                if (row.mark.note != null && !row.mark.note.isEmpty()) {
                    item.addView(text(row.mark.note, 13, 0xFF364152, false));
                }
                shown++;
                content.addView(item, marginParams(dp(8)));
                if (shown >= 8) break;
            }
        }

        TextView pagesTitle = text("PAGE ACTIVITY", 12, 0xFF667085, true);
        content.addView(pagesTitle, marginParams(dp(12)));

        int pageShown = 0;
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            for (int i = 0; i < notebook.pages.size(); i++) {
                NotebookStore.PageMeta page = notebook.pages.get(i);
                NotebookStore.PageStats stats = store.getPageStats(page.id);
                if (stats.strokes == 0 && stats.activeMs == 0L) continue;

                LinearLayout item = card();
                item.addView(text(notebook.title + "  •  " + page.title, 14, 0xFF182339, true));
                item.addView(text(
                        stats.strokes + " strokes  •  " + formatDuration(stats.activeMs),
                        12, 0xFF6B7280, false
                ));
                HeatmapView heat = new HeatmapView(this, stats.heatmap);
                item.addView(heat, new LinearLayout.LayoutParams(-1, dp(92)));
                content.addView(item, marginParams(dp(8)));
                pageShown++;
                if (pageShown >= 5) break;
            }
            if (pageShown >= 5) break;
        }

        if (pageShown == 0) {
            LinearLayout empty = card();
            empty.addView(text("Page analytics appear after you write in PaperNote.", 13, 0xFF5B6473, false));
            content.addView(empty, marginParams(dp(8)));
        }

        TextView recentTitle = text("RECENT STUDY TIMELINE", 12, 0xFF667085, true);
        content.addView(recentTitle, marginParams(dp(12)));

        ArrayList<ActivityRow> recent = collectRecentActivity(notebooks);
        if (recent.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Your recent study timeline will appear here after you open and work on pages.", 13, 0xFF5B6473, false));
            content.addView(empty, marginParams(dp(8)));
        } else {
            int shownRecent = 0;
            for (ActivityRow row : recent) {
                LinearLayout item = card();
                item.addView(text(row.notebook.title + "  •  " + row.page.title, 14, 0xFF182339, true));
                item.addView(text(formatDate(row.lastStudiedAt), 12, 0xFF6B7280, false));
                Button open = button("Open", false);
                open.setOnClickListener(v -> IntentBuilder.openNotebook(this, row.notebook.id, row.pageIndex));
                item.addView(open);
                content.addView(item, marginParams(dp(8)));
                if (++shownRecent >= 8) break;
            }
        }

        TextView linksTitle = text("CONCEPT THREADS", 12, 0xFF667085, true);
        content.addView(linksTitle, marginParams(dp(12)));

        ArrayList<LinkRow> links = collectLinks(notebooks);
        if (links.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Connect pages from STUDY → Concept thread to build your handwritten concept map.", 13, 0xFF5B6473, false));
            content.addView(empty, marginParams(dp(8)));
        } else {
            int shownLinks = 0;
            for (LinkRow row : links) {
                LinearLayout item = card();
                item.addView(text(row.sourcePage.title + "  ↔  " + row.targetPage.title, 14, 0xFF182339, true));
                item.addView(text(row.link.label, 12, 0xFF6B7280, false));
                Button open = button("Open source", false);
                open.setOnClickListener(v -> IntentBuilder.openNotebook(this, row.notebook.id, row.sourceIndex));
                item.addView(open);
                content.addView(item, marginParams(dp(8)));
                if (++shownLinks >= 8) break;
            }
        }

        TextView privacy = text(
                "All Study Hub metrics are kept locally on this device. No account or cloud sync is used.",
                11, 0xFF7A8495, false
        );
        privacy.setPadding(dp(4), dp(14), dp(4), 0);
        content.addView(privacy);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private ArrayList<MarkRow> collectMarks(List<NotebookStore.NotebookMeta> notebooks) {
        ArrayList<MarkRow> rows = new ArrayList<>();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            for (int i = 0; i < notebook.pages.size(); i++) {
                NotebookStore.PageMeta page = notebook.pages.get(i);
                for (NotebookStore.StudyMark mark : store.getStudyMarks(page.id)) {
                    rows.add(new MarkRow(notebook, page, i, mark));
                }
            }
        }
        rows.sort((a, b) -> {
            int unresolved = Boolean.compare(a.mark.resolved, b.mark.resolved);
            if (unresolved != 0) return unresolved;
            return Long.compare(b.mark.createdAt, a.mark.createdAt);
        });
        return rows;
    }

    private String markLabel(String type) {
        if (NotebookStore.StudyMark.DOUBT.equals(type)) return "Doubt";
        if (NotebookStore.StudyMark.MISTAKE.equals(type)) return "Mistake";
        if (NotebookStore.StudyMark.IMPORTANT.equals(type)) return "Important";
        return "Revise";
    }

    private String formatStats(int pages, int strokes, long activeMs) {
        return pages + " pages  •  " + strokes + " strokes  •  " + formatDuration(activeMs);
    }

    private String formatDuration(long ms) {
        long totalMinutes = Math.max(0L, ms / 60000L);
        if (totalMinutes < 60) return totalMinutes + " min";
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format(Locale.US, "%dh %02dm", hours, minutes);
    }

    private String formatDate(long timestamp) {
        if (timestamp <= 0L) return "Not studied yet";
        SimpleDateFormat format = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(timestamp));
    }

    private ArrayList<ActivityRow> collectRecentActivity(List<NotebookStore.NotebookMeta> notebooks) {
        ArrayList<ActivityRow> rows = new ArrayList<>();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            for (int i = 0; i < notebook.pages.size(); i++) {
                NotebookStore.PageMeta page = notebook.pages.get(i);
                long last = store.getPageStats(page.id).lastStudiedAt;
                if (last > 0L) rows.add(new ActivityRow(notebook, page, i, last));
            }
        }
        rows.sort((a, b) -> Long.compare(b.lastStudiedAt, a.lastStudiedAt));
        return rows;
    }

    private ArrayList<LinkRow> collectLinks(List<NotebookStore.NotebookMeta> notebooks) {
        ArrayList<LinkRow> rows = new ArrayList<>();
        Map<String, NotebookPageRef> refs = new HashMap<>();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            for (int i = 0; i < notebook.pages.size(); i++) {
                refs.put(notebook.pages.get(i).id, new NotebookPageRef(notebook, notebook.pages.get(i), i));
            }
        }
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            for (NotebookStore.PageMeta page : notebook.pages) {
                for (NotebookStore.StudyLink link : store.getStudyLinksForPage(page.id)) {
                    String key = link.id;
                    if (!seen.add(key)) continue;
                    NotebookPageRef source = refs.get(link.sourcePageId);
                    NotebookPageRef target = refs.get(link.targetPageId);
                    if (source != null && target != null) {
                        rows.add(new LinkRow(notebook, source.page, target.page, source.pageIndex, link));
                    }
                }
            }
        }
        rows.sort((a, b) -> Long.compare(b.link.createdAt, a.link.createdAt));
        return rows;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(rounded(0xFFFFFFFF, 18));
        return box;
    }

    private LinearLayout.LayoutParams marginParams(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, bottom);
        return lp;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return v;
    }

    private Button button(String label, boolean dark) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextColor(dark ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(dark ? 0xFF2A3854 : Color.WHITE, 12));
        return b;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class MarkRow {
        final NotebookStore.NotebookMeta notebook;
        final NotebookStore.PageMeta page;
        final int pageIndex;
        final NotebookStore.StudyMark mark;
        MarkRow(NotebookStore.NotebookMeta notebook, NotebookStore.PageMeta page, int pageIndex,
                NotebookStore.StudyMark mark) {
            this.notebook = notebook;
            this.page = page;
            this.pageIndex = pageIndex;
            this.mark = mark;
        }
    }

    private static final class ActivityRow {
        final NotebookStore.NotebookMeta notebook;
        final NotebookStore.PageMeta page;
        final int pageIndex;
        final long lastStudiedAt;
        ActivityRow(NotebookStore.NotebookMeta notebook, NotebookStore.PageMeta page, int pageIndex, long lastStudiedAt) {
            this.notebook = notebook;
            this.page = page;
            this.pageIndex = pageIndex;
            this.lastStudiedAt = lastStudiedAt;
        }
    }

    private static final class NotebookPageRef {
        final NotebookStore.NotebookMeta notebook;
        final NotebookStore.PageMeta page;
        final int pageIndex;
        NotebookPageRef(NotebookStore.NotebookMeta notebook, NotebookStore.PageMeta page, int pageIndex) {
            this.notebook = notebook;
            this.page = page;
            this.pageIndex = pageIndex;
        }
    }

    private static final class LinkRow {
        final NotebookStore.NotebookMeta notebook;
        final NotebookStore.PageMeta sourcePage;
        final NotebookStore.PageMeta targetPage;
        final int sourceIndex;
        final NotebookStore.StudyLink link;
        LinkRow(NotebookStore.NotebookMeta notebook, NotebookStore.PageMeta sourcePage,
                NotebookStore.PageMeta targetPage, int sourceIndex, NotebookStore.StudyLink link) {
            this.notebook = notebook;
            this.sourcePage = sourcePage;
            this.targetPage = targetPage;
            this.sourceIndex = sourceIndex;
            this.link = link;
        }
    }

    private static final class IntentBuilder {
        static void openNotebook(Activity activity, String notebookId, int pageIndex) {
            android.content.Intent intent = new android.content.Intent(activity, EditorActivity.class);
            intent.putExtra("notebook_id", notebookId);
            intent.putExtra("page_index", pageIndex);
            activity.startActivity(intent);
        }
    }

    private static final class DailyRhythmView extends View {
        private final List<NotebookStore.DailyStudy> days;
        private final long maxMs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DailyRhythmView(Activity context, List<NotebookStore.DailyStudy> days, long maxMs) {
            super(context);
            this.days = new ArrayList<>(days);
            this.maxMs = Math.max(1L, maxMs);
            setContentDescription("Fourteen day local study activity chart");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float gap = dp(5f);
            float usableW = Math.max(1f, w - gap * Math.max(0, days.size() - 1));
            float barW = Math.max(dp(6f), usableW / Math.max(1, days.size()));
            float baseline = h - dp(25f);
            Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
            guide.setColor(0xFFE5E8EE);
            guide.setStrokeWidth(dp(1f));
            canvas.drawLine(0, baseline, w, baseline, guide);
            for (int i = 0; i < days.size(); i++) {
                NotebookStore.DailyStudy day = days.get(i);
                float x = i * (barW + gap);
                float ratio = Math.min(1f, day.activeMs / (float) maxMs);
                float barH = ratio * (baseline - dp(20f));
                paint.setColor(0xFF486CC8);
                canvas.drawRoundRect(
                        x, baseline - barH, x + barW, baseline,
                        dp(5f), dp(5f), paint
                );
                if (i == 0 || i == days.size() - 1 || i % 3 == 0) {
                    Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
                    label.setColor(0xFF7A8495);
                    label.setTextSize(dp(9f));
                    String dayLabel = day.date.length() >= 10
                            ? day.date.substring(8) : day.date;
                    canvas.drawText(dayLabel, x, h - dp(6f), label);
                }
            }
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    private static final class HeatmapView extends View {
        private final int[] values;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeatmapView(Activity context, int[] values) {
            super(context);
            this.values = values.clone();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int max = 1;
            for (int v : values) max = Math.max(max, v);
            int width = getWidth();
            int height = getHeight();
            float cellW = width / 6f;
            float cellH = height / 8f;
            for (int i = 0; i < 48; i++) {
                float intensity = values[i] / (float) max;
                int alpha = 18 + Math.round(150f * intensity);
                paint.setColor(Color.argb(alpha, 61, 111, 232));
                float left = i % 6 * cellW + 2;
                float top = i / 6 * cellH + 2;
                canvas.drawRoundRect(left, top, left + cellW - 4, top + cellH - 4, 7, 7, paint);
            }
        }
    }
}
