package com.jd.hsc_test_series_2027;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int PURPLE = Color.rgb(91, 63, 212);
    private final int BG = Color.rgb(246, 247, 251);
    private final int TEXT = Color.rgb(23, 23, 37);
    private final int MUTED = Color.rgb(106, 106, 122);
    private final int BORDER = Color.rgb(228, 228, 236);
    private final int GREEN = Color.rgb(31, 157, 98);
    private final int RED = Color.rgb(214, 74, 74);
    private final int SOFT = Color.rgb(238, 234, 254);

    private LinearLayout root;
    private CountDownTimer timer;
    private TextView timerView;
    private Test currentTest;
    private String currentSubject = "Physics";
    private int currentQuestion = 0;
    private long remainingMillis = 0L;
    private final List<Integer> answers = new ArrayList<>();
    private final List<Boolean> marked = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private GradientDrawable shape(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (strokeWidth > 0) g.setStroke(dp(strokeWidth), stroke);
        return g;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private Button primary(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setBackground(shape(PURPLE, 16, 0, 0));
        return b;
    }

    private Button outline(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(PURPLE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setBackground(shape(Color.WHITE, 16, BORDER, 1));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(shape(Color.WHITE, 18, BORDER, 1));
        return c;
    }

    private void base(String title, String subtitle, boolean back) {
        root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(18), dp(20), 0);

        LinearLayout top = row();
        if (back) {
            TextView b = text("‹", 34, TEXT, false);
            b.setGravity(Gravity.CENTER);
            top.addView(b, new LinearLayout.LayoutParams(dp(40), dp(44)));
            b.setOnClickListener(v -> showHome());
        }
        LinearLayout titles = column();
        TextView t = text(title, 25, TEXT, true);
        titles.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(subtitle, 12.5f, MUTED, false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(6);
            titles.addView(s, p);
        }
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private ScrollView scroll(LinearLayout content) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(content);
        return s;
    }

    private void bottomBar(String selected) {
        LinearLayout bar = row();
        bar.setPadding(dp(4), dp(4), dp(4), dp(6));
        bar.setBackground(shape(Color.WHITE, 18, BORDER, 1));
        String[] items = {"Home", "Tests", "Results"};
        for (String item : items) {
            TextView v = text(item, 11, item.equals(selected) ? PURPLE : MUTED, item.equals(selected));
            v.setGravity(Gravity.CENTER);
            v.setPadding(0, dp(9), 0, dp(9));
            if (item.equals("Home")) v.setOnClickListener(x -> showHome());
            if (item.equals("Tests")) v.setOnClickListener(x -> showTests("Physics"));
            if (item.equals("Results")) v.setOnClickListener(x -> showResults());
            bar.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void showHome() {
        stopTimer();
        base("HSC TEST SERIES 2027", "Maharashtra HSC • CBT Practice", false);

        LinearLayout c = column();
        c.addView(space(14));

        LinearLayout hero = card();
        LinearLayout r = row();
        LinearLayout l = column();
        l.addView(text("Your board prep,", 22, TEXT, true));
        l.addView(text("one test at a time.", 22, PURPLE, true));
        TextView sub = text("Practice • Analyse • Improve", 12, MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        l.addView(sub, sp);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));

        TextView score = text("70%", 26, PURPLE, true);
        score.setGravity(Gravity.CENTER);
        score.setBackground(shape(SOFT, 40, 0, 0));
        r.addView(score, new LinearLayout.LayoutParams(dp(76), dp(76)));
        hero.addView(r);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(70);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(7));
        pp.topMargin = dp(16);
        hero.addView(pb, pp);
        c.addView(hero);

        c.addView(space(20));
        c.addView(text("Subjects", 17, TEXT, true));
        c.addView(space(8));

        String[] subjects = {"Physics","Chemistry","Mathematics 1","Mathematics 2","Biology","English","IT"};
        for (int i = 0; i < subjects.length; i += 2) {
            LinearLayout rr = row();
            rr.setGravity(Gravity.TOP);
            addSubject(rr, subjects[i]);
            if (i + 1 < subjects.length) addSubject(rr, subjects[i + 1]);
            else rr.addView(new View(this), new LinearLayout.LayoutParams(0, dp(92), 1));
            c.addView(rr, new LinearLayout.LayoutParams(-1, dp(106)));
        }

        c.addView(space(10));
        c.addView(text("Continue test", 17, TEXT, true));
        c.addView(space(8));

        LinearLayout t = card();
        LinearLayout tr = row();
        TextView badge = text("PH", 12, PURPLE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(SOFT, 12, 0, 0));
        tr.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout td = column();
        td.setPadding(dp(12), 0, dp(6), 0);
        td.addView(text("Physics • Rotational Dynamics", 14, TEXT, true));
        TextView meta = text("10 MCQs • 20 min • HSC pattern", 11, MUTED, false);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(5);
        td.addView(meta, mp);
        tr.addView(td, new LinearLayout.LayoutParams(0, -2, 1));
        tr.addView(text("Start  ›", 13, PURPLE, true));
        t.addView(tr);
        t.setOnClickListener(v -> openTest("Physics"));
        c.addView(t);

        c.addView(space(16));
        TextView note = text("Offline-ready demo • Full HSC question bank can be added", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        c.addView(note, new LinearLayout.LayoutParams(-1, dp(26)));

        root.addView(scroll(c), new LinearLayout.LayoutParams(-1, 0, 1));
        bottomBar("Home");
    }

    private void addSubject(LinearLayout row, String subject) {
        LinearLayout c = card();
        c.setPadding(dp(12), dp(11), dp(10), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(92), 1);
        p.setMargins(0, 0, dp(8), 0);

        TextView b = text(subject.substring(0, Math.min(2, subject.length())).toUpperCase(Locale.US), 10, PURPLE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(SOFT, 11, 0, 0));
        c.addView(b, new LinearLayout.LayoutParams(dp(36), dp(30)));

        TextView n = text(subject, 12.5f, TEXT, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(7);
        c.addView(n, np);

        TextView ct = text(subject.equals("Physics") ? "3 tests" : "2 tests", 10, MUTED, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(3);
        c.addView(ct, tp);

        c.setOnClickListener(v -> showTests(subject));
        row.addView(c, p);
    }

    private void showTests(String subject) {
        currentSubject = subject;
        base(subject, "Available practice tests", true);
        LinearLayout c = column();
        c.addView(space(14));

        for (int i = 1; i <= 3; i++) {
            LinearLayout t = card();
            LinearLayout r = row();

            TextView n = text(String.valueOf(i), 16, Color.WHITE, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(shape(PURPLE, 40, 0, 0));
            r.addView(n, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout d = column();
            d.setPadding(dp(12), 0, 0, 0);
            d.addView(text(testName(subject, i), 14, TEXT, true));
            TextView m = text(i == 1 ? "10 MCQs • 20 min • 10 marks" : "Coming next • 15 MCQs", 11, MUTED, false);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(5);
            d.addView(m, mp);
            r.addView(d, new LinearLayout.LayoutParams(0, -2, 1));

            TextView status = text(i == 1 ? "LIVE" : "UPCOMING", 10.5f, PURPLE, true);
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(9), dp(5), dp(9), dp(5));
            status.setBackground(shape(SOFT, 12, 0, 0));
            r.addView(status);
            t.addView(r);

            if (i == 1) t.setOnClickListener(v -> openTest(subject));
            c.addView(t, new LinearLayout.LayoutParams(-1, dp(84)));
            if (i < 3) c.addView(space(10));
        }

        c.addView(space(10));
        TextView info = text("Only Test 01 is enabled in this preview build.", 11, MUTED, false);
        info.setGravity(Gravity.CENTER);
        c.addView(info);
        root.addView(scroll(c), new LinearLayout.LayoutParams(-1, 0, 1));
        bottomBar("Tests");
    }

    private String testName(String subject, int i) {
        if (i == 1) return subject + " Test 01 • Board Basics";
        if (i == 2) return subject + " Test 02 • Chapter Mix";
        return subject + " Test 03 • Full-Length Practice";
    }

    private void openTest(String subject) {
        stopTimer();
        currentSubject = subject;
        currentTest = sample(subject);
        currentQuestion = 0;
        remainingMillis = currentTest.minutes * 60_000L;
        answers.clear();
        marked.clear();
        for (int i = 0; i < currentTest.questions.size(); i++) {
            answers.add(-1);
            marked.add(false);
        }
        startTimer();
        showQuestion();
    }

    private void showQuestion() {
        base("Test 01", currentSubject + " • Question " + (currentQuestion + 1) + " of " + currentTest.questions.size(), false);
        LinearLayout c = column();
        c.addView(space(10));

        LinearLayout top = row();
        TextView qtag = text("Q" + (currentQuestion + 1), 12, PURPLE, true);
        qtag.setGravity(Gravity.CENTER);
        qtag.setBackground(shape(SOFT, 12, 0, 0));
        top.addView(qtag, new LinearLayout.LayoutParams(dp(48), dp(34)));

        TextView marks = text(" +1 mark", 11, MUTED, false);
        top.addView(marks, new LinearLayout.LayoutParams(0, dp(34), 1));

        timerView = text(formatMillis(remainingMillis), 15, RED, true);
        timerView.setGravity(Gravity.CENTER);
        timerView.setBackground(shape(Color.WHITE, 12, Color.rgb(242, 204, 204), 1));
        top.addView(timerView, new LinearLayout.LayoutParams(dp(92), dp(34)));
        c.addView(top);

        c.addView(space(14));

        Question q = currentTest.questions.get(currentQuestion);
        LinearLayout qb = card();
        qb.addView(text(q.text, 17, TEXT, true));
        TextView helper = text("Choose one correct answer.", 11, MUTED, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(8);
        qb.addView(helper, hp);
        c.addView(qb);

        c.addView(space(12));
        for (int i = 0; i < q.options.length; i++) {
            final int idx = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(q.options[i]);
            rb.setTextSize(14);
            rb.setTextColor(TEXT);
            rb.setPadding(dp(10), dp(7), dp(8), dp(7));
            rb.setGravity(Gravity.CENTER_VERTICAL);
            rb.setButtonTintList(new ColorStateList(
                    new int[][] { new int[]{android.R.attr.state_checked}, new int[]{} },
                    new int[]{PURPLE, MUTED}
            ));
            rb.setBackground(shape(answers.get(currentQuestion) == i ? SOFT : Color.WHITE, 14, BORDER, 1));
            rb.setChecked(answers.get(currentQuestion) == i);
            rb.setOnClickListener(v -> {
                answers.set(currentQuestion, idx);
                showQuestion();
            });
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(56));
            op.bottomMargin = dp(8);
            c.addView(rb, op);
        }

        LinearLayout paletteHead = row();
        TextView ph = text("Question palette", 12, TEXT, true);
        paletteHead.addView(ph, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView flag = text(marked.get(currentQuestion) ? "Unflag" : "Flag for review", 11, PURPLE, true);
        flag.setGravity(Gravity.CENTER);
        flag.setPadding(dp(9), 0, dp(9), 0);
        flag.setBackground(shape(SOFT, 12, 0, 0));
        flag.setOnClickListener(v -> {
            marked.set(currentQuestion, !marked.get(currentQuestion));
            showQuestion();
        });
        paletteHead.addView(flag, new LinearLayout.LayoutParams(-2, dp(34)));
        c.addView(paletteHead);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout nums = row();
        for (int i = 0; i < currentTest.questions.size(); i++) {
            final int idx = i;
            int bg = i == currentQuestion ? PURPLE : (answers.get(i) >= 0 ? Color.rgb(223,245,235) : Color.WHITE);
            int fg = i == currentQuestion ? Color.WHITE : TEXT;
            TextView n = text(String.valueOf(i + 1), 11, fg, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(shape(bg, 11, i == currentQuestion ? 0 : BORDER, i == currentQuestion ? 0 : 1));
            n.setOnClickListener(v -> { currentQuestion = idx; showQuestion(); });
            nums.addView(n, new LinearLayout.LayoutParams(dp(38), dp(34)));
            if (i < currentTest.questions.size() - 1) nums.addView(new View(this), new LinearLayout.LayoutParams(dp(6), 1));
        }
        hsv.addView(nums);
        c.addView(hsv);

        c.addView(space(14));
        LinearLayout actions = row();
        Button prev = outline("Previous");
        prev.setEnabled(currentQuestion > 0);
        prev.setAlpha(currentQuestion > 0 ? 1f : 0.45f);
        prev.setOnClickListener(v -> { currentQuestion--; showQuestion(); });
        actions.addView(prev, new LinearLayout.LayoutParams(0, dp(50), 1));

        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button next = primary(currentQuestion == currentTest.questions.size() - 1 ? "Submit Test" : "Next");
        next.setOnClickListener(v -> {
            if (currentQuestion == currentTest.questions.size() - 1) finishTest();
            else { currentQuestion++; showQuestion(); }
        });
        actions.addView(next, new LinearLayout.LayoutParams(0, dp(50), 1));
        c.addView(actions);

        c.addView(space(18));
        root.addView(scroll(c), new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void startTimer() {
        final long startFrom = remainingMillis;
        timer = new CountDownTimer(startFrom, 1000) {
            @Override public void onTick(long left) {
                remainingMillis = left;
                if (timerView != null) timerView.setText(formatMillis(left));
            }
            @Override public void onFinish() {
                remainingMillis = 0;
                finishTest();
            }
        };
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private String formatMillis(long millis) {
        long total = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private void finishTest() {
        if (currentTest == null) return;
        stopTimer();
        int correct = 0;
        for (int i = 0; i < currentTest.questions.size(); i++) {
            if (answers.get(i) == currentTest.questions.get(i).correct) correct++;
        }
        getSharedPreferences("hsc_test_series", MODE_PRIVATE)
                .edit()
                .putInt("score", correct)
                .putInt("total", currentTest.questions.size())
                .putString("subject", currentSubject)
                .apply();
        showResult(correct, currentTest.questions.size());
    }

    private void showResult(int correct, int total) {
        base("Test Result", currentSubject + " • Test 01", false);
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(space(16));

        TextView score = text(correct + "/" + total, 42, PURPLE, true);
        score.setGravity(Gravity.CENTER);
        c.addView(score, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView pct = text(Math.round(correct * 100f / total) + "% score", 14, MUTED, false);
        pct.setGravity(Gravity.CENTER);
        c.addView(pct);

        c.addView(space(16));
        LinearLayout stats = card();
        addStat(stats, "Correct", String.valueOf(correct), GREEN);
        addStat(stats, "Wrong", String.valueOf(total - correct), RED);
        addStat(stats, "Accuracy", Math.round(correct * 100f / total) + "%", PURPLE);
        c.addView(stats, new LinearLayout.LayoutParams(-1, dp(76)));

        c.addView(space(18));
        TextView msg = text(correct == total ? "Excellent. Full marks." : "Review the incorrect answers and retry.", 15, TEXT, true);
        msg.setGravity(Gravity.CENTER);
        c.addView(msg, new LinearLayout.LayoutParams(-1, dp(46)));

        c.addView(space(8));
        Button retry = primary("Retry Test");
        retry.setOnClickListener(v -> openTest(currentSubject));
        c.addView(retry, new LinearLayout.LayoutParams(-1, dp(50)));

        c.addView(space(8));
        Button home = outline("Back to Dashboard");
        home.setOnClickListener(v -> showHome());
        c.addView(home, new LinearLayout.LayoutParams(-1, dp(50)));

        c.addView(space(16));
        root.addView(scroll(c), new LinearLayout.LayoutParams(-1, 0, 1));
        bottomBar("Results");
    }

    private void addStat(LinearLayout parent, String label, String value, int color) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.addView(text(value, 18, color, true));
        TextView l = text(label, 10, MUTED, false);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(4);
        item.addView(l, p);
        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showResults() {
        base("Results", "Your recent test performance", true);
        LinearLayout c = column();
        c.addView(space(14));

        SharedPreferences p = getSharedPreferences("hsc_test_series", MODE_PRIVATE);
        int score = p.getInt("score", 7);
        int total = p.getInt("total", 10);
        String subject = p.getString("subject", "Physics");

        LinearLayout r = card();
        LinearLayout rr = row();
        rr.addView(text(score + "/" + total, 25, PURPLE, true), new LinearLayout.LayoutParams(dp(88), dp(50)));

        LinearLayout d = column();
        d.addView(text(subject + " • Test 01", 14, TEXT, true));
        TextView latest = text("Latest saved attempt", 11, MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        d.addView(latest, lp);
        rr.addView(d, new LinearLayout.LayoutParams(0, -2, 1));

        rr.addView(text(Math.round(score * 100f / total) + "%", 16, GREEN, true));
        r.addView(rr);
        c.addView(r);

        c.addView(space(12));
        TextView tip = text("Next upgrade: chapter-wise tests, HSC PYQs and detailed mistake analysis.", 13, TEXT, false);
        tip.setPadding(dp(14), dp(14), dp(14), dp(14));
        tip.setBackground(shape(Color.WHITE, 16, BORDER, 1));
        c.addView(tip);

        root.addView(scroll(c), new LinearLayout.LayoutParams(-1, 0, 1));
        bottomBar("Results");
    }

    private Test sample(String subject) {
        if (subject.equals("Physics")) {
            return new Test(20, Arrays.asList(
                    new Question("The SI unit of angular velocity is:", new String[]{"rad s⁻¹","m s⁻¹","N m","J s"}, 0),
                    new Question("For a rigid body rotating with angular speed ω, linear speed at radius r is:", new String[]{"ω/r","ωr","r/ω","ω+r"}, 1),
                    new Question("Moment of inertia depends on:", new String[]{"Mass only","Shape only","Mass distribution about axis","Angular speed"}, 2),
                    new Question("Torque is equal to:", new String[]{"I/α","Iα","I+α","α/I"}, 1),
                    new Question("One complete revolution is equal to:", new String[]{"π rad","2π rad","180 rad","360 rad"}, 1),
                    new Question("Angular momentum of a rigid body is:", new String[]{"L = Iω","L = I/ω","L = ω/I","L = I+ω"}, 0),
                    new Question("Rotational kinetic energy is:", new String[]{"½Iω²","Iω²","2Iω","½I/ω²"}, 0),
                    new Question("If angular speed doubles, rotational kinetic energy becomes:", new String[]{"2 times","4 times","8 times","Half"}, 1),
                    new Question("A couple produces:", new String[]{"Pure translation","Pure rotation","No effect","Linear acceleration only"}, 1),
                    new Question("Radius of gyration k is related to I by:", new String[]{"I = Mk²","I = M/k²","I = k/M","I = M+k"}, 0)
            ));
        }
        List<Question> q = new ArrayList<>();
        q.add(new Question("This is a demo HSC-style question for " + subject + ".", new String[]{"Option A","Option B","Option C","Option D"}, 0));
        q.add(new Question("Which approach is best for board preparation?", new String[]{"Only guessing","Only reading","Timed practice + analysis","Skipping revision"}, 2));
        q.add(new Question("A strong test attempt should include:", new String[]{"Unplanned answers","Time management","No review","Random selection"}, 1));
        q.add(new Question("After a test, the most useful next step is:", new String[]{"Ignore mistakes","Review errors","Delete result","Repeat without analysis"}, 1));
        q.add(new Question("What does CBT mean?", new String[]{"Computer Based Test","Chapter Based Theory","Class Book Test","Central Board Timing"}, 0));
        q.add(new Question("Which metric shows correct answers among attempted questions?", new String[]{"Accuracy","Brightness","Latency","Volume"}, 0));
        q.add(new Question("Unanswered questions are tracked in a:", new String[]{"Question palette","Wallpaper","Gallery","Browser"}, 0));
        q.add(new Question("Which habit helps identify weak chapters?", new String[]{"Result analysis","Skipping tests","Random revision","No practice"}, 0));
        q.add(new Question("A full-length mock checks:", new String[]{"Only memory","Endurance and pacing","Only handwriting","Only attendance"}, 1));
        q.add(new Question("A board-oriented question bank should be based on:", new String[]{"Syllabus + textbook + PYQs","Random posts only","Guesses only","One example only"}, 0));
        return new Test(20, q);
    }

    private static class Test {
        final int minutes;
        final List<Question> questions;
        Test(int minutes, List<Question> questions) {
            this.minutes = minutes;
            this.questions = questions;
        }
    }

    private static class Question {
        final String text;
        final String[] options;
        final int correct;
        Question(String text, String[] options, int correct) {
            this.text = text;
            this.options = options;
            this.correct = correct;
        }
    }
}
