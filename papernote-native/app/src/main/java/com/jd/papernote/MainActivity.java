package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements PaperCanvasView.Listener {
    private static final int REQUEST_IMAGE = 501;
    private static final int REQUEST_EXPORT = 502;
    private static final int REQUEST_BACKUP = 503;
    private static final int REQUEST_RESTORE = 504;

    private static final int EXPORT_PDF = 1;
    private static final int EXPORT_PNG = 2;
    private static final int EXPORT_BACKUP = 3;

    private NotebookStore store;
    private SoundEngine soundEngine;
    private ExecutorService saveExecutor;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSave;

    private NotebookStore.NotebookMeta currentNotebook;
    private int currentPageIndex = 0;
    private PaperCanvasView canvasView;
    private TextView pageLabel;
    private TextView saveLabel;
    private TextView titleLabel;
    private Button writeModeButton;
    private Button palmButton;
    private Button soundButton;
    private int pendingExport = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        soundEngine = new SoundEngine(this);
        saveExecutor = Executors.newSingleThreadExecutor();

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(23, 32, 51));
        window.setNavigationBarColor(Color.rgb(23, 32, 51));

        if (store.list().isEmpty()) {
            try {
                store.create("Math Practice", "Mathematics", PaperCanvasView.PAPER_GRAPH);
            } catch (Exception ignored) {
            }
        }

        GoogleAuthManager authManager = GoogleAuthManager.get(this);
        boolean offlineMode = getPreferences(MODE_PRIVATE).getBoolean("papernote_offline_mode", false);
        if (authManager.getCurrentUser() != null || offlineMode) {
            showHome();
        } else {
            showWelcome();
        }
        UpdateManager.check(this, false);
    }

    @Override
    protected void onDestroy() {
        saveHandler.removeCallbacksAndMessages(null);
        saveCurrentPageNow();
        if (saveExecutor != null) saveExecutor.shutdown();
        if (soundEngine != null) soundEngine.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentNotebook != null) {
            saveCurrentPageNow();
            currentNotebook = null;
            showHome();
        } else {
            super.onBackPressed();
        }
    }


    private void showWelcome() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFFF6F8FC);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(30), dp(26), dp(24));

        TextView topBadge = text("PAPERNOTE 1.6  •  STUDY EDITION", 10, 0xFF5D6B86, true);
        topBadge.setGravity(Gravity.CENTER);
        topBadge.setBackground(rounded(0xFFE9EEFF, 22));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-1, dp(34));
        root.addView(topBadge, badgeParams);

        Space top = new Space(this);
        root.addView(top, new LinearLayout.LayoutParams(1, 0, 0.7f));

        TextView logo = text("P", 46, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(0xFF536DFE, 26));
        logo.setElevation(dp(10));
        root.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView title = text("PaperNote", 34, 0xFF182339, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, dp(18), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text(
                "Your focused digital notebook for handwritten study.\\nWrite, revise, organize and export without paper.",
                15, 0xFF667085, false
        );
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, dp(7), 0, 0);
        root.addView(subtitle, subtitleParams);

        LinearLayout featureCard = new LinearLayout(this);
        featureCard.setOrientation(LinearLayout.VERTICAL);
        featureCard.setPadding(dp(18), dp(16), dp(18), dp(14));
        featureCard.setBackground(rounded(Color.WHITE, 20));
        featureCard.setElevation(dp(3));

        TextView cardTitle = text("Built around your study flow", 16, 0xFF182339, true);
        featureCard.addView(cardTitle);

        LinearLayout featureRow = new LinearLayout(this);
        featureRow.setGravity(Gravity.CENTER_VERTICAL);
        featureRow.setPadding(0, dp(11), 0, 0);
        addFeature(featureRow, "✍", "Fast handwriting");
        addFeature(featureRow, "✓", "Auto-save");
        addFeature(featureRow, "⇩", "PDF + images");
        featureCard.addView(featureRow);

        LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(-1, -2);
        featureParams.setMargins(0, dp(22), 0, 0);
        root.addView(featureCard, featureParams);

        Button google = styledButton("Continue with Google", true);
        google.setTextSize(15);
        google.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams googleParams = new LinearLayout.LayoutParams(-1, dp(54));
        googleParams.setMargins(0, dp(18), 0, dp(10));
        root.addView(google, googleParams);

        TextView status = text("", 12, 0xFF667085, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        Button offline = styledButton("Continue offline", false);
        offline.setTextSize(14);
        LinearLayout.LayoutParams offlineParams = new LinearLayout.LayoutParams(-1, dp(50));
        offlineParams.setMargins(0, dp(10), 0, 0);
        root.addView(offline, offlineParams);

        TextView footer = text(
                "Google sign-in syncs account identity. Your current notebook data stays on this device.",
                11, 0xFF8A93A3, false
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.setMargins(0, dp(16), 0, 0);
        root.addView(footer, footerParams);

        Space bottom = new Space(this);
        root.addView(bottom, new LinearLayout.LayoutParams(1, 0, 1f));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        GoogleAuthManager authManager = GoogleAuthManager.get(this);
        if (!authManager.isConfigured()) {
            google.setEnabled(false);
            status.setText(authManager.getConfigurationMessage());
        }

        google.setOnClickListener(v -> {
            google.setEnabled(false);
            google.setText("Signing in…");
            status.setText("Waiting for Google account…");

            authManager.signIn(this, new GoogleAuthManager.Callback() {
                @Override public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                    getPreferences(MODE_PRIVATE)
                            .edit()
                            .putBoolean("papernote_offline_mode", false)
                            .apply();

                    runOnUiThread(() -> {
                        status.setText("Signed in");
                        google.setText("Continue with Google");
                        google.setEnabled(true);
                        showHome();
                    });
                }

                @Override public void onError(String message) {
                    runOnUiThread(() -> {
                        google.setText("Continue with Google");
                        google.setEnabled(true);
                        status.setText(message);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Google sign-in")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            });
        });

        offline.setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE)
                    .edit()
                    .putBoolean("papernote_offline_mode", true)
                    .apply();
            showHome();
        });

        // Staggered entrance animation gives the launch screen a polished, calm feel.
        View[] animated = {topBadge, logo, title, subtitle, featureCard, google, status, offline, footer};
        for (int i = 0; i < animated.length; i++) {
            View view = animated[i];
            view.setAlpha(0f);
            view.setTranslationY(dp(18));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(90L + i * 55L)
                    .setDuration(360L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        logo.setScaleX(0.72f);
        logo.setScaleY(0.72f);
        logo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(100L)
                .setDuration(520L)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();
    }

    private void addFeature(LinearLayout row, String icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView iconView = text(icon, 18, 0xFF536DFE, true);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView, new LinearLayout.LayoutParams(-1, dp(26)));
        TextView labelView = text(label, 10, 0xFF667085, false);
        labelView.setGravity(Gravity.CENTER);
        item.addView(labelView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(item, params);
    }

    private void showHome() {
        currentNotebook = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF6F8FC);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(22), dp(22), dp(20));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(5));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("PaperNote", 29, Color.WHITE, true);
        TextView tagline = text("Your study desk, wherever you are.", 12, 0xFFC9D2E1, false);
        brandBox.addView(brand);
        brandBox.addView(tagline);
        headerRow.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1f));

        Button account = toolbarButton("ACCOUNT");
        account.setTextColor(Color.WHITE);
        account.setBackground(rounded(0xFF2A3854, 14));
        account.setOnClickListener(v -> showAccountDialog());
        headerRow.addView(account);

        header.addView(headerRow);

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(0, dp(14), 0, 0);

        List<NotebookStore.NotebookMeta> notebooks = store.list();
        int totalPages = 0;
        for (NotebookStore.NotebookMeta n : notebooks) totalPages += n.pages.size();

        TextView notebookStat = text(notebooks.size() + " notebooks", 12, 0xFFE6EBF4, true);
        TextView pageStat = text(totalPages + " saved pages", 12, 0xFFE6EBF4, true);
        stats.addView(notebookStat, new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(pageStat, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(stats);

        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(15), dp(16), dp(7));

        Button newButton = styledButton("+ New notebook", true);
        newButton.setTextSize(14);
        newButton.setOnClickListener(v -> showNewNotebookDialog(null, null, PaperCanvasView.PAPER_RULED));
        actions.addView(newButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button restoreButton = styledButton("Restore", false);
        restoreButton.setOnClickListener(v -> chooseRestoreFile());
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(dp(100), dp(48));
        restoreParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(restoreButton, restoreParams);
        root.addView(actions);

        EditText search = new EditText(this);
        search.setHint("Search by notebook or subject");
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(rounded(Color.WHITE, 16));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.setMargins(dp(16), dp(5), dp(16), dp(10));
        root.addView(search, searchParams);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(16), 0, dp(16), dp(12));
        addChip(chips, "Math", () -> showNewNotebookDialog("Math Practice", "Mathematics", PaperCanvasView.PAPER_GRAPH));
        addChip(chips, "Physics", () -> showNewNotebookDialog("Physics Notes", "Physics", PaperCanvasView.PAPER_RULED));
        addChip(chips, "Chemistry", () -> showNewNotebookDialog("Chemistry Notes", "Chemistry", PaperCanvasView.PAPER_RULED));
        addChip(chips, "Quick Blank", () -> showNewNotebookDialog("New Notebook", "General Study", PaperCanvasView.PAPER_BLANK));
        chipsScroll.addView(chips);
        root.addView(chipsScroll);

        TextView section = text("MY NOTEBOOKS", 11, 0xFF7A8495, true);
        section.setPadding(dp(17), dp(2), dp(17), dp(7));
        root.addView(section);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(18));

        for (NotebookStore.NotebookMeta notebook : notebooks) {
            addNotebookCard(list, notebook);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase();
                list.removeAllViews();
                for (NotebookStore.NotebookMeta notebook : store.list()) {
                    if (q.isEmpty()
                            || notebook.title.toLowerCase().contains(q)
                            || notebook.subject.toLowerCase().contains(q)) {
                        addNotebookCard(list, notebook);
                    }
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        header.setAlpha(0f);
        header.setTranslationY(-dp(12));
        header.animate().alpha(1f).translationY(0f)
                .setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void showAccountDialog() {
        GoogleAuthManager authManager = GoogleAuthManager.get(this);
        com.google.firebase.auth.FirebaseUser user = authManager.getCurrentUser();

        if (user == null) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("PaperNote account")
                    .setMessage("You are using PaperNote offline. Sign in with Google to connect this app session to your Firebase account.")
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Sign in with Google", null)
                    .create();

            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Button signIn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                signIn.setEnabled(false);
                signIn.setText("Signing in…");

                authManager.signIn(this, new GoogleAuthManager.Callback() {
                    @Override public void onSuccess(com.google.firebase.auth.FirebaseUser signedInUser) {
                        getPreferences(MODE_PRIVATE)
                                .edit()
                                .putBoolean("papernote_offline_mode", false)
                                .apply();
                        dialog.dismiss();
                        showHome();
                    }

                    @Override public void onError(String message) {
                        signIn.setEnabled(true);
                        signIn.setText("Sign in with Google");
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Google sign-in")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            }));
            dialog.show();
            return;
        }

        String identity = "Signed in with Google\\n";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            identity += user.getDisplayName() + "\\n";
        }
        if (user.getEmail() != null) {
            identity += user.getEmail();
        }

        new AlertDialog.Builder(this)
                .setTitle("PaperNote account")
                .setMessage(identity)
                .setNegativeButton("Sign out", (dialog, which) -> {
                    authManager.signOut(this);
                    getPreferences(MODE_PRIVATE)
                            .edit()
                            .putBoolean("papernote_offline_mode", false)
                            .apply();
                    showWelcome();
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void addNotebookCard(LinearLayout parent, NotebookStore.NotebookMeta notebook) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(14), dp(14), dp(14));
        card.setBackground(rounded(0xFFFFFFFF, 18));
        card.setElevation(dp(2));
        card.setOnClickListener(v -> openNotebook(notebook.id));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(notebook.title, 19, Color.rgb(23, 32, 51), true);
        TextView info = text(notebook.subject + "  •  " + notebook.pages.size() + " page" + (notebook.pages.size() == 1 ? "" : "s"), 13, 0xFF6E7788, false);
        labels.addView(title);
        labels.addView(info);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = styledButton("Open", false);
        open.setOnClickListener(v -> openNotebook(notebook.id));
        row.addView(open, new LinearLayout.LayoutParams(dp(80), dp(44)));

        card.addView(row);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        parent.addView(card, cp);
    }

    private void showNewNotebookDialog(String presetTitle, String presetSubject, String presetPaper) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        EditText title = new EditText(this);
        title.setHint("Notebook title");
        title.setSingleLine(true);
        title.setText(presetTitle == null ? "" : presetTitle);

        EditText subject = new EditText(this);
        subject.setHint("Subject");
        subject.setSingleLine(true);
        subject.setText(presetSubject == null ? "" : presetSubject);

        TextView helper = text("Choose a paper style after creating the notebook.", 13, 0xFF6E7788, false);
        helper.setPadding(0, dp(6), 0, 0);
        box.addView(title);
        box.addView(subject);
        box.addView(helper);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                NotebookStore.NotebookMeta created = store.create(
                        title.getText().toString(),
                        subject.getText().toString(),
                        presetPaper == null ? PaperCanvasView.PAPER_RULED : presetPaper
                );
                dialog.dismiss();
                openNotebook(created.id);
            } catch (Exception e) {
                toast("Could not create notebook");
            }
        }));
        dialog.show();
    }

    private void openNotebook(String id) {
        try {
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra("notebook_id", id);
            intent.putExtra("page_index", 0);
            startActivity(intent);
        } catch (Exception e) {
            toast("Could not open notebook");
        }
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(222, 226, 232));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(6), dp(10), dp(6));
        header.setBackgroundColor(Color.rgb(23, 32, 51));

        Button back = toolbarButton("←");
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleLabel = text(currentNotebook.title, 17, Color.WHITE, true);
        TextView subject = text(currentNotebook.subject, 12, 0xFFC8D0DD, false);
        titleBox.addView(titleLabel);
        titleBox.addView(subject);
        LinearLayout.LayoutParams tb = new LinearLayout.LayoutParams(0, -2, 1f);
        tb.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, tb);

        saveLabel = text("Ready", 12, 0xFFC8D0DD, false);
        saveLabel.setGravity(Gravity.CENTER);
        header.addView(saveLabel, new LinearLayout.LayoutParams(dp(72), -1));

        Button more = toolbarButton("⋮");
        more.setOnClickListener(v -> showMoreMenu(more));
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header);

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(dp(8), dp(7), dp(8), dp(7));
        tools.setGravity(Gravity.CENTER_VERTICAL);

        writeModeButton = toolbarButton("WRITE");
        writeModeButton.setOnClickListener(v -> {
            boolean newMode = !canvasView.isWriteMode();
            canvasView.setWriteMode(newMode);
            updateWriteModeButton();
        });
        tools.addView(writeModeButton);

        Button pen = toolbarButton("PEN");
        pen.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_PEN));
        tools.addView(pen);

        Button marker = toolbarButton("MARK");
        marker.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_HIGHLIGHTER));
        tools.addView(marker);

        Button eraser = toolbarButton("ERASER");
        eraser.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_ERASER));
        tools.addView(eraser);

        Button line = toolbarButton("LINE");
        line.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_LINE));
        tools.addView(line);

        Button rect = toolbarButton("RECT");
        rect.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_RECT));
        tools.addView(rect);

        Button oval = toolbarButton("OVAL");
        oval.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_OVAL));
        tools.addView(oval);

        Button text = toolbarButton("TEXT");
        text.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_TEXT));
        tools.addView(text);

        Button undo = toolbarButton("UNDO");
        undo.setOnClickListener(v -> canvasView.undo());
        tools.addView(undo);

        Button redo = toolbarButton("REDO");
        redo.setOnClickListener(v -> canvasView.redo());
        tools.addView(redo);

        Button image = toolbarButton("IMAGE");
        image.setOnClickListener(v -> chooseImage());
        tools.addView(image);

        Button study = toolbarButton("STUDY");
        study.setOnClickListener(v -> showStudyTools(study));
        tools.addView(study);

        toolsScroll.addView(tools);
        root.addView(toolsScroll);

        HorizontalScrollView controlScroll = new HorizontalScrollView(this);
        controlScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(10), dp(5), dp(10), dp(5));
        controlBar.setBackgroundColor(Color.WHITE);

        TextView sizeLabel = text("Size", 12, 0xFF596273, true);
        controlBar.addView(sizeLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(45);
        size.setProgress(8);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setPenSize(1.5f + progress * 0.58f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlBar.addView(size, new LinearLayout.LayoutParams(dp(170), dp(42)));

        TextView smoothLabel = text("Smooth", 12, 0xFF596273, true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, dp(42));
        slp.setMargins(dp(12), 0, 0, 0);
        controlBar.addView(smoothLabel, slp);

        SeekBar smooth = new SeekBar(this);
        smooth.setMax(25);
        smooth.setProgress(6);
        smooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setStabilizer(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlBar.addView(smooth, new LinearLayout.LayoutParams(dp(145), dp(42)));

        Button color = toolbarButton("INK");
        color.setOnClickListener(v -> showColorDialog());
        controlBar.addView(color);

        palmButton = toolbarButton("PALM");
        palmButton.setOnClickListener(v -> {
            canvasView.setPalmShield(!canvasView.isPalmShield());
            updatePalmButton();
        });
        controlBar.addView(palmButton);

        soundButton = toolbarButton("SOUND");
        soundButton.setOnClickListener(v -> {
            boolean enabled = !soundEngine.isEnabled();
            soundEngine.setEnabled(enabled);
            updateSoundButton();
        });
        controlBar.addView(soundButton);

        controlScroll.addView(controlBar);
        root.addView(controlScroll, new LinearLayout.LayoutParams(-1, dp(54)));

        FrameLayout canvasFrame = new FrameLayout(this);
        canvasView = new PaperCanvasView(this);
        canvasView.setListener(this);
        canvasView.setSoundEngine(soundEngine);
        canvasFrame.addView(canvasView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(canvasFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(7), dp(6), dp(7), dp(6));
        bottom.setBackgroundColor(Color.WHITE);

        Button prev = toolbarButton("‹");
        prev.setOnClickListener(v -> movePage(-1));
        bottom.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(44)));

        pageLabel = text("Page 1 / 1", 13, Color.rgb(23, 32, 51), true);
        pageLabel.setGravity(Gravity.CENTER);
        bottom.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button next = toolbarButton("›");
        next.setOnClickListener(v -> movePage(1));
        bottom.addView(next, new LinearLayout.LayoutParams(dp(48), dp(44)));

        Button add = toolbarButton("+ PAGE");
        add.setOnClickListener(v -> addPage());
        bottom.addView(add);

        Button paper = toolbarButton("PAPER");
        paper.setOnClickListener(v -> showPaperDialog());
        bottom.addView(paper);

        Button pdf = toolbarButton("PDF");
        pdf.setOnClickListener(v -> requestExport(EXPORT_PDF));
        bottom.addView(pdf);

        Button save = toolbarButton("PNG");
        save.setOnClickListener(v -> requestExport(EXPORT_PNG));
        bottom.addView(save);

        root.addView(bottom);

        setContentView(root);
        loadCurrentPage();
        updateWriteModeButton();
        updatePalmButton();
        updateSoundButton();
    }

    private void loadCurrentPage() {
        if (currentNotebook == null || currentNotebook.pages.isEmpty()) return;
        NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
        Bitmap bitmap = store.loadPageBitmap(page.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
        canvasView.setPaperType(page.paperType);
        canvasView.loadBitmap(bitmap);
        pageLabel.setText("Page " + (currentPageIndex + 1) + " / " + currentNotebook.pages.size());
        saveLabel.setText("Saved");
    }

    private void saveCurrentPageNow() {
        if (currentNotebook == null || canvasView == null) return;
        try {
            NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
            if (canvasView.getInkBitmap() != null) {
                store.savePageBitmap(page.id, canvasView.getInkBitmap());
            }
            store.save(currentNotebook);
            if (saveLabel != null) saveLabel.setText("Saved");
        } catch (Exception e) {
            toast("Save failed");
        }
    }

    private void saveCurrentPage() {
        if (currentNotebook == null || canvasView == null) return;

        if (pendingSave != null) {
            saveHandler.removeCallbacks(pendingSave);
        }

        pendingSave = () -> {
            saveCurrentPageSnapshotAsync();
            pendingSave = null;
        };

        saveHandler.postDelayed(pendingSave, 650L);
    }

    private void saveCurrentPageSnapshotAsync() {
        if (currentNotebook == null || canvasView == null) return;

        try {
            NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
            Bitmap source = canvasView.getInkBitmap();
            if (source == null) return;

            // Only make the expensive page copy after the user has paused writing.
            // This removes the visible pause that used to happen after every stroke.
            Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
            store.save(currentNotebook);
            saveExecutor.submit(() -> {
                try {
                    store.savePageBitmap(page.id, copy);
                    copy.recycle();
                    runOnUiThread(() -> {
                        if (saveLabel != null) saveLabel.setText("Saved");
                    });
                } catch (Exception ignored) {
                    copy.recycle();
                    runOnUiThread(() -> {
                        if (saveLabel != null) saveLabel.setText("Save error");
                    });
                }
            });
        } catch (Exception e) {
            toast("Save failed");
        }
    }

    @Override
    public void onCanvasDirty() {
        saveLabel.setText("Editing");
        saveCurrentPage();
    }

    @Override
    public void onRequestText(float pageX, float pageY) {
        final EditText input = new EditText(this);
        input.setHint("Type text to place on the page");
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(8), dp(4), dp(8), dp(4));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add text")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                canvasView.addText(value, pageX, pageY);
                dialog.dismiss();
            }
        }));
        dialog.show();
        input.requestFocus();
    }

    private void addPage() {
        saveCurrentPageNow();
        NotebookStore.PageMeta page = store.addPage(
                currentNotebook,
                "Page " + (currentNotebook.pages.size() + 1),
                currentNotebook.pages.isEmpty() ? PaperCanvasView.PAPER_RULED : currentNotebook.pages.get(currentPageIndex).paperType
        );
        currentPageIndex = currentNotebook.pages.size() - 1;
        try {
            store.save(currentNotebook);
        } catch (Exception ignored) {
        }
        loadCurrentPage();
    }

    private void movePage(int delta) {
        saveCurrentPageNow();
        int next = currentPageIndex + delta;
        if (next < 0 || next >= currentNotebook.pages.size()) return;
        currentPageIndex = next;
        loadCurrentPage();
    }

    private void showPaperDialog() {
        String[] items = {"Blank", "Ruled", "Graph", "Dot Grid", "Math Practice"};
        String[] values = {
                PaperCanvasView.PAPER_BLANK,
                PaperCanvasView.PAPER_RULED,
                PaperCanvasView.PAPER_GRAPH,
                PaperCanvasView.PAPER_DOT,
                PaperCanvasView.PAPER_MATH
        };
        int checked = 1;
        String current = currentNotebook.pages.get(currentPageIndex).paperType;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;

        final int defaultChecked = checked;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Paper template")
                .setSingleChoiceItems(items, checked, null)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ListViewCompat selection = new ListViewCompat(dialog);
            int which = selection.getCheckedItemPosition();
            if (which < 0) which = defaultChecked;
            currentNotebook.pages.get(currentPageIndex).paperType = values[which];
            canvasView.setPaperType(values[which]);
            saveCurrentPageNow();
            try { store.save(currentNotebook); } catch (Exception ignored) {}
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showColorDialog() {
        final int[] colors = {
                Color.rgb(24, 35, 51),
                Color.rgb(25, 74, 160),
                Color.rgb(125, 40, 72),
                Color.rgb(15, 105, 80),
                Color.rgb(102, 62, 28),
                Color.rgb(92, 49, 150)
        };
        String[] labels = {"Navy", "Blue", "Burgundy", "Green", "Brown", "Purple"};
        new AlertDialog.Builder(this)
                .setTitle("Ink color")
                .setItems(labels, (dialog, which) -> canvasView.setInkColor(colors[which]))
                .show();
    }


    private void showStudyTools(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Scientific calculator");
        menu.getMenu().add("Focus timer");
        menu.getMenu().add("Page checklist");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Scientific calculator":
                    showCalculator();
                    return true;
                case "Focus timer":
                    showFocusTimer();
                    return true;
                case "Page checklist":
                    showStudyChecklist();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void showCalculator() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText expression = new EditText(this);
        expression.setHint("Example: (12+8)*3/4");
        expression.setSingleLine(true);
        expression.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_CLASS_PHONE
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(expression);

        TextView result = text("Result: ", 18, Color.rgb(23, 32, 51), true);
        result.setPadding(0, dp(14), 0, dp(8));
        box.addView(result);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scientific calculator")
                .setView(box)
                .setNeutralButton("Clear", null)
                .setNegativeButton("Close", null)
                .setPositiveButton("Calculate", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    double value = ExpressionParser.evaluate(expression.getText().toString());
                    result.setText("Result: " + formatNumber(value));
                } catch (Exception e) {
                    result.setText("Result: Invalid expression");
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                expression.setText("");
                result.setText("Result: ");
            });
        });
        dialog.show();
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-10) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.10f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\\\.$", "");
    }

    private void showFocusTimer() {
        final Handler timerHandler = new Handler(Looper.getMainLooper());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView time = text("25:00", 42, Color.rgb(23, 32, 51), true);
        time.setGravity(Gravity.CENTER);
        box.addView(time);

        TextView hint = text("Focus on one chapter or problem set. You can stop at any time.", 13, 0xFF667085, false);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);

        final long[] endAt = {0L};
        final boolean[] running = {false};
        final Runnable[] tick = new Runnable[1];

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Focus timer")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Start", null)
                .create();

        dialog.setOnDismissListener(d -> timerHandler.removeCallbacks(tick[0]));

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!running[0]) {
                    endAt[0] = System.currentTimeMillis() + 25L * 60L * 1000L;
                    running[0] = true;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Pause");
                    tick[0] = new Runnable() {
                        @Override
                        public void run() {
                            long remaining = Math.max(0L, endAt[0] - System.currentTimeMillis());
                            long minutes = remaining / 60000L;
                            long seconds = (remaining / 1000L) % 60L;
                            time.setText(String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds));
                            if (remaining <= 0L) {
                                running[0] = false;
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                                android.media.ToneGenerator tone =
                                        new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90);
                                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 700);
                                tone.release();
                                return;
                            }
                            timerHandler.postDelayed(this, 250L);
                        }
                    };
                    timerHandler.post(tick[0]);
                } else {
                    long remaining = Math.max(0L, endAt[0] - System.currentTimeMillis());
                    endAt[0] = System.currentTimeMillis() + remaining;
                    running[0] = false;
                    timerHandler.removeCallbacks(tick[0]);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                }
            });
        });

        dialog.show();
    }

    private void showStudyChecklist() {
        final String[] tasks = {
                "Review formulas",
                "Solve 10 practice questions",
                "Solve 10 MCQs",
                "Mark difficult questions",
                "Revise mistakes",
                "Write a short summary"
        };
        boolean[] checked = new boolean[tasks.length];

        new AlertDialog.Builder(this)
                .setTitle("Study checklist")
                .setMultiChoiceItems(tasks, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Close", null)
                .setPositiveButton("Save as page note", (dialog, which) -> {
                    StringBuilder note = new StringBuilder("Study Checklist\\n");
                    for (int i = 0; i < tasks.length; i++) {
                        note.append(checked[i] ? "☑ " : "☐ ").append(tasks[i]).append("\\n");
                    }
                    canvasView.addText(
                            note.toString().trim(),
                            150f,
                            180f
                    );
                })
                .show();
    }

    private static final class ExpressionParser {
        private final String source;
        private int index;

        private ExpressionParser(String source) {
            this.source = source.replace("×", "*")
                    .replace("÷", "/")
                    .replace("π", String.valueOf(Math.PI))
                    .replaceAll("\\\\s+", "");
        }

        static double evaluate(String source) {
            if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException();
            ExpressionParser parser = new ExpressionParser(source);
            double value = parser.parseExpression();
            if (parser.index != parser.source.length()) throw new IllegalArgumentException();
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '+' && op != '-') break;
                index++;
                double rhs = parseTerm();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private double parseTerm() {
            double value = parsePower();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '*' && op != '/') break;
                index++;
                double rhs = parsePower();
                if (op == '/' && Math.abs(rhs) < 1e-15) throw new ArithmeticException();
                value = op == '*' ? value * rhs : value / rhs;
            }
            return value;
        }

        private double parsePower() {
            double base = parseUnary();
            if (index < source.length() && source.charAt(index) == '^') {
                index++;
                base = Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() {
            if (index < source.length() && source.charAt(index) == '+') {
                index++;
                return parseUnary();
            }
            if (index < source.length() && source.charAt(index) == '-') {
                index++;
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (index >= source.length()) throw new IllegalArgumentException();

            if (source.charAt(index) == '(') {
                index++;
                double value = parseExpression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                return value;
            }

            if (source.startsWith("sqrt(", index)) {
                index += 5;
                double value = parseExpression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                if (value < 0) throw new ArithmeticException();
                return Math.sqrt(value);
            }

            int start = index;
            boolean dot = false;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (Character.isDigit(ch)) {
                    index++;
                } else if (ch == '.' && !dot) {
                    dot = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) throw new IllegalArgumentException();
            return Double.parseDouble(source.substring(start, index));
        }
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename notebook");
        menu.getMenu().add("Rename page");
        menu.getMenu().add("Duplicate page");
        menu.getMenu().add("Delete page");
        menu.getMenu().add("Clear current page");
        menu.getMenu().add("Backup notebook");
        menu.getMenu().add("Restore backup");
        menu.getMenu().add("About passive stylus");
        menu.getMenu().add("Check for update");
        menu.getMenu().add("Delete notebook");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Rename notebook":
                    renameNotebook();
                    return true;
                case "Rename page":
                    renamePage();
                    return true;
                case "Duplicate page":
                    duplicateCurrentPage();
                    return true;
                case "Delete page":
                    confirmDeleteCurrentPage();
                    return true;
                case "Clear current page":
                    new AlertDialog.Builder(this)
                            .setTitle("Clear this page?")
                            .setMessage("All handwriting and inserted content on this page will be removed.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear", (d, w) -> canvasView.clearPage())
                            .show();
                    return true;
                case "Backup notebook":
                    requestExport(EXPORT_BACKUP);
                    return true;
                case "Restore backup":
                    chooseRestoreFile();
                    return true;
                case "About passive stylus":
                    new AlertDialog.Builder(this)
                            .setTitle("Passive stylus mode")
                            .setMessage(
                                    "Your non-electric capacitive stylus does not send a separate pen identity to Android. " +
                                    "PaperNote therefore accepts normal touch input for writing, locks the first contact as the active stroke, " +
                                    "and can ignore unusually large contact areas as a palm shield. True electronic palm rejection requires an active pen digitizer."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                    return true;
                case "Check for update":
                    UpdateManager.check(this, true);
                    return true;
                case "Delete notebook":
                    confirmDeleteNotebook();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void renameNotebook() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setText(currentNotebook.title);
        EditText subject = new EditText(this);
        subject.setSingleLine(true);
        subject.setText(currentNotebook.subject);
        box.addView(title);
        box.addView(subject);

        new AlertDialog.Builder(this)
                .setTitle("Rename notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renameNotebook(currentNotebook, title.getText().toString(), subject.getText().toString());
                        titleLabel.setText(currentNotebook.title);
                    } catch (Exception e) {
                        toast("Rename failed");
                    }
                }).show();
    }


    private void duplicateCurrentPage() {
        saveCurrentPageNow();
        NotebookStore.PageMeta original = currentNotebook.pages.get(currentPageIndex);
        NotebookStore.PageMeta copy = store.addPage(
                currentNotebook,
                original.title + " Copy",
                original.paperType
        );

        Bitmap image = canvasView.getInkBitmap();
        if (image != null) {
            Bitmap bitmapCopy = image.copy(Bitmap.Config.ARGB_8888, false);
            try {
                store.savePageBitmap(copy.id, bitmapCopy);
                bitmapCopy.recycle();
            } catch (Exception e) {
                bitmapCopy.recycle();
                toast("Could not duplicate page");
                return;
            }
        }

        currentPageIndex = currentNotebook.pages.size() - 1;
        try { store.save(currentNotebook); } catch (Exception ignored) {}
        loadCurrentPage();
    }

    private void confirmDeleteCurrentPage() {
        if (currentNotebook.pages.size() <= 1) {
            toast("A notebook must keep at least one page");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete current page?")
                .setMessage("This permanently removes the current page from this notebook.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    saveCurrentPageNow();
                    store.removePage(currentNotebook, currentPageIndex);
                    currentPageIndex = Math.min(currentPageIndex, currentNotebook.pages.size() - 1);
                    try { store.save(currentNotebook); } catch (Exception ignored) {}
                    loadCurrentPage();
                })
                .show();
    }

    private void renamePage() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentNotebook.pages.get(currentPageIndex).title);
        new AlertDialog.Builder(this)
                .setTitle("Rename page")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renamePage(currentNotebook, currentPageIndex, input.getText().toString());
                        pageLabel.setText("Page " + (currentPageIndex + 1) + " / " + currentNotebook.pages.size());
                    } catch (Exception e) {
                        toast("Rename failed");
                    }
                }).show();
    }

    private void confirmDeleteNotebook() {
        new AlertDialog.Builder(this)
                .setTitle("Delete notebook?")
                .setMessage("This deletes the notebook and its page images from PaperNote.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    store.deleteNotebook(currentNotebook);
                    currentNotebook = null;
                    showHome();
                }).show();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    private void requestExport(int type) {
        saveCurrentPageNow();
        pendingExport = type;
        String name;
        String mime;
        if (type == EXPORT_PDF) {
            name = safeFileName(currentNotebook.title) + ".pdf";
            mime = "application/pdf";
        } else if (type == EXPORT_PNG) {
            name = safeFileName(currentNotebook.pages.get(currentPageIndex).title) + ".png";
            mime = "image/png";
        } else {
            name = safeFileName(currentNotebook.title) + ".papernote";
            mime = "application/json";
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, type == EXPORT_BACKUP ? REQUEST_BACKUP : REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        try {
            if (requestCode == REQUEST_IMAGE) {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    Bitmap decoded = android.graphics.BitmapFactory.decodeStream(in);
                    if (decoded == null) throw new Exception();
                    int max = 1800;
                    float scale = Math.min(1f, max / (float) Math.max(decoded.getWidth(), decoded.getHeight()));
                    if (scale < 1f) {
                        Bitmap scaled = Bitmap.createScaledBitmap(
                                decoded,
                                Math.max(1, Math.round(decoded.getWidth() * scale)),
                                Math.max(1, Math.round(decoded.getHeight() * scale)),
                                true
                        );
                        decoded.recycle();
                        decoded = scaled;
                    }
                    canvasView.addImage(decoded);
                    decoded.recycle();
                }
                return;
            }

            if (requestCode == REQUEST_EXPORT) {
                if (pendingExport == EXPORT_PDF) {
                    exportPdf(uri);
                } else if (pendingExport == EXPORT_PNG) {
                    saveCurrentPageNow();
                    Bitmap page = canvasView.renderPageBitmap();
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        page.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    page.recycle();
                    toast("PNG exported");
                }
                return;
            }

            if (requestCode == REQUEST_BACKUP) {
                saveCurrentPageNow();
                String backup = store.exportBackup(currentNotebook, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(backup.getBytes(StandardCharsets.UTF_8));
                }
                toast("Backup exported");
                return;
            }

            if (requestCode == REQUEST_RESTORE) {
                StringBuilder builder = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }
                NotebookStore.NotebookMeta restored = store.importBackup(builder.toString());
                toast("Backup restored");
                currentNotebook = restored;
                currentPageIndex = 0;
                buildEditor();
            }
        } catch (Exception e) {
            toast("Operation failed");
        }
    }

    private void exportPdf(Uri uri) throws Exception {
        saveCurrentPageNow();

        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        try {
            for (int i = 0; i < currentNotebook.pages.size(); i++) {
                NotebookStore.PageMeta pageMeta = currentNotebook.pages.get(i);
                Bitmap ink = store.loadPageBitmap(pageMeta.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                Bitmap rendered = PaperCanvasView.renderPage(ink, pageMeta.paperType, true);
                android.graphics.pdf.PdfDocument.PageInfo info =
                        new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, i + 1).create();
                android.graphics.pdf.PdfDocument.Page page = document.startPage(info);
                page.getCanvas().drawBitmap(rendered, null, new android.graphics.Rect(0, 0, 595, 842), null);
                document.finishPage(page);
                ink.recycle();
                rendered.recycle();
            }

            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                document.writeTo(out);
            }
            toast("PDF exported");
        } finally {
            document.close();
        }
    }

    private void updateWriteModeButton() {
        if (writeModeButton == null || canvasView == null) return;
        writeModeButton.setText(canvasView.isWriteMode() ? "WRITE" : "PAN");
    }

    private void updatePalmButton() {
        if (palmButton != null && canvasView != null) {
            palmButton.setText(canvasView.isPalmShield() ? "PALM✓" : "PALM");
        }
    }

    private void updateSoundButton() {
        if (soundButton != null) {
            soundButton.setText(soundEngine.isEnabled() ? "SOUND✓" : "SOUND");
        }
    }

    private Button styledButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(23, 32, 51));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(rounded(primary ? Color.rgb(64, 93, 230) : Color.WHITE, 14));
        return b;
    }

    private Button toolbarButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setTextColor(Color.rgb(23, 32, 51));
        b.setBackground(rounded(Color.WHITE, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addChip(LinearLayout parent, String label, Runnable action) {
        Button chip = styledButton(label, false);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(0, 0, dp(8), 0);
        parent.addView(chip, lp);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeFileName(String value) {
        String cleaned = value == null ? "PaperNote" : value.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        return cleaned.isEmpty() ? "PaperNote" : cleaned;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Small adapter that lets the dialog read the selected radio item without exposing
    // Android's internal AlertController implementation details.
    private static final class ListViewCompat {
        private final AlertDialog dialog;
        ListViewCompat(AlertDialog dialog) { this.dialog = dialog; }
        int getCheckedItemPosition() {
            android.widget.ListView list = dialog.getListView();
            return list == null ? -1 : list.getCheckedItemPosition();
        }
    }
}
