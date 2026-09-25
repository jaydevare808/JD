package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReviewActivity extends Activity {
    private NotebookStore store;
    private NotebookStore.NotebookMeta notebook;
    private final ArrayList<NotebookStore.ReviewCard> due = new ArrayList<>();
    private int index = 0;
    private boolean showingAnswer = false;

    private TextView progress;
    private TextView front;
    private TextView answer;
    private TextView interval;
    private LinearLayout ratingRow;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        String notebookId = getIntent().getStringExtra("notebook_id");
        try {
            notebook = notebookId == null ? firstNotebook() : store.get(notebookId);
        } catch (Exception e) {
            notebook = firstNotebook();
        }
        loadDue();
        build();
    }

    private NotebookStore.NotebookMeta firstNotebook() {
        List<NotebookStore.NotebookMeta> list = store.list();
        return list.isEmpty() ? null : list.get(0);
    }

    private void loadDue() {
        due.clear();
        if (notebook != null) due.addAll(store.getDueReviewCards(notebook));
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackgroundColor(0xFF182339);

        Button back = button("‹", false, true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("Review Lab", 21, Color.WHITE, true));
        titleBox.addView(text("Spaced repetition for your own notes", 12, 0xFFC9D2E1, false));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, titleLp);

        progress = text("", 11, 0xFFE5EAF3, true);
        progress.setGravity(Gravity.CENTER);
        header.addView(progress, new LinearLayout.LayoutParams(dp(78), dp(38)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(24));

        LinearLayout intro = card();
        intro.addView(text("TODAY'S REVIEW", 11, 0xFF667085, true));
        intro.addView(text(
                "PaperNote only schedules cards you created or approved. Nothing is uploaded.",
                13, 0xFF5B6473, false));
        body.addView(intro, bottom(dp(10)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, 22));
        card.setElevation(dp(2));

        TextView qLabel = text("QUESTION", 10, 0xFF667085, true);
        card.addView(qLabel);
        front = text("", 20, 0xFF182339, true);
        front.setTextIsSelectable(true);
        front.setPadding(0, dp(14), 0, dp(14));
        card.addView(front);

        answer = text("", 15, 0xFF344054, false);
        answer.setTextIsSelectable(true);
        answer.setLineSpacing(0, 1.15f);
        answer.setVisibility(View.GONE);
        answer.setPadding(0, dp(6), 0, dp(12));
        card.addView(answer);

        Button reveal = button("Reveal answer", true, false);
        reveal.setOnClickListener(v -> {
            showingAnswer = !showingAnswer;
            answer.setVisibility(showingAnswer ? View.VISIBLE : View.GONE);
            reveal.setText(showingAnswer ? "Hide answer" : "Reveal answer");
        });
        card.addView(reveal, new LinearLayout.LayoutParams(-1, dp(46)));
        body.addView(card, bottom(dp(12)));

        interval = text("", 12, 0xFF667085, false);
        interval.setPadding(dp(4), 0, dp(4), dp(9));
        body.addView(interval);

        TextView rateTitle = text("HOW DID YOU RECALL IT?", 11, 0xFF667085, true);
        body.addView(rateTitle, bottom(dp(7)));

        ratingRow = new LinearLayout(this);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        addRating("Again", 0, 0xFFB54745);
        addRating("Hard", 1, 0xFFB76E00);
        addRating("Good", 2, 0xFF2F7D4A);
        addRating("Easy", 3, 0xFF405DE6);
        body.addView(ratingRow);

        TextView empty = text(
                "When there are no cards due, use PaperNote AI or create cards manually from the Review Lab.",
                12, 0xFF7A8495, false);
        empty.setPadding(dp(4), dp(16), dp(4), 0);
        body.addView(empty);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        if (due.isEmpty() || index >= due.size()) {
            progress.setText("0 due");
            front.setText("You're caught up.");
            answer.setText("No review cards are due for this notebook.");
            answer.setVisibility(View.VISIBLE);
            interval.setText("Create more cards from important formulas, definitions or mistakes.");
            ratingRow.setVisibility(View.GONE);
            return;
        }
        NotebookStore.ReviewCard card = due.get(index);
        progress.setText((index + 1) + "/" + due.size());
        front.setText(card.front);
        answer.setText(card.back);
        answer.setVisibility(View.GONE);
        showingAnswer = false;
        interval.setText(
                card.tag.isEmpty() ? "New card" :
                        card.tag + "  •  " + card.repetitions + " successful reviews  •  " +
                                card.intervalDays + " day interval");
    }

    private void addRating(String label, int rating, int color) {
        Button b = button(label, false, false);
        b.setTextColor(color);
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            if (!showingAnswer) {
                toast("Reveal the answer before rating the card.");
                return;
            }
            NotebookStore.ReviewCard card = due.get(index);
            try {
                store.reviewCard(card.id, rating);
                index++;
                render();
            } catch (Exception e) {
                toast("Could not save the review.");
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(0, 0, dp(6), 0);
        ratingRow.addView(b, lp);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(rounded(Color.WHITE, 18));
        return box;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private Button button(String label, boolean primary, boolean darkHeader) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : darkHeader ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(primary ? 0xFF405DE6 : darkHeader ? 0xFF2A3854 : Color.WHITE, 13));
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams bottom(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp);
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
