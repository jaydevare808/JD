package com.jd.codestudio;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int OPEN_FILE = 1001;
    private static final int SAVE_FILE = 1002;

    private LinearLayout root;
    private LinearLayout editorRow;
    private LinearLayout outputPanel;
    private EditText editor;
    private TextView lineNumbers;
    private TextView output;
    private TextView fileNameView;
    private TextView languageView;
    private TextView statusView;
    private EditText stdinEditor;
    private WebView preview;
    private Spinner languageSpinner;

    private boolean internalChange;
    private boolean suppressLanguageEvent;
    private File currentFile;

    private final String[] languages = {
            "PHP", "Python", "JavaScript", "HTML", "CSS",
            "C", "C++", "Java", "SQL", "Bash"
    };

    private final Map<String, String> samples = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 15, 20));
        getWindow().setNavigationBarColor(Color.rgb(11, 15, 20));

        initSamples();
        buildUi();

        File dir = new File(getFilesDir(), "projects/default");
        if (!dir.exists() && !dir.mkdirs()) {
            toast("Could not create project folder");
        }

        currentFile = new File(dir, "main.php");
        if (!currentFile.exists()) {
            writeText(currentFile, samples.get("PHP"));
        }
        loadFile(currentFile);
    }

    private void initSamples() {
        samples.put("PHP",
                "<?php\n\n" +
                "$name = \"JD\";\n" +
                "$marks = 85;\n\n" +
                "echo \"Name: \" . $name . \"<br>\";\n" +
                "echo \"Marks: \" . $marks;\n\n" +
                "?>");

        samples.put("Python",
                "name = \"JD\"\n" +
                "marks = 85\n\n" +
                "print(\"Name:\", name)\n" +
                "print(\"Marks:\", marks)");

        samples.put("JavaScript",
                "const name = \"JD\";\n" +
                "const marks = 85;\n\n" +
                "console.log(\"Name:\", name);\n" +
                "console.log(\"Marks:\", marks);");

        samples.put("HTML",
                "<!doctype html>\n" +
                "<html>\n" +
                "<body>\n" +
                "  <h1>JD Code Studio</h1>\n" +
                "  <p>Hello from HTML.</p>\n" +
                "</body>\n" +
                "</html>");

        samples.put("CSS",
                "body { font-family: sans-serif; padding: 24px; }\n" +
                "h1 { font-size: 28px; }\n" +
                ".note { padding: 12px; border: 1px solid #ccc; }");

        samples.put("C",
                "#include <stdio.h>\n\n" +
                "int main() {\n" +
                "    printf(\"Hello from C\\n\");\n" +
                "    return 0;\n" +
                "}");

        samples.put("C++",
                "#include <iostream>\n\n" +
                "int main() {\n" +
                "    std::cout << \"Hello from C++\\n\";\n" +
                "    return 0;\n" +
                "}");

        samples.put("Java",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from Java\");\n" +
                "    }\n" +
                "}");

        samples.put("SQL",
                "CREATE TABLE students(id INTEGER, name TEXT, marks INTEGER);\n" +
                "INSERT INTO students VALUES(1, 'JD', 85);\n" +
                "INSERT INTO students VALUES(2, 'Riya', 92);\n" +
                "SELECT * FROM students;");

        samples.put("Bash",
                "echo \"Hello from Bash\"\n" +
                "name=JD\n" +
                "echo \"Name: $name\"");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 15, 20));
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = makeText("JD Code Studio", 21, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        statusView = makeText("READY", 11, Color.rgb(83, 212, 154));
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(statusView, new LinearLayout.LayoutParams(dp(130), dp(48)));

        root.addView(header);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(2), 0, dp(6));

        languageSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                languages
        );
        languageSpinner.setAdapter(adapter);
        toolbar.addView(languageSpinner, new LinearLayout.LayoutParams(dp(112), dp(44)));

        Button newButton = makeButton("New");
        Button openButton = makeButton("Open");
        Button saveButton = makeButton("Save");
        Button runButton = makeButton("▶ Run");
        Button settingsButton = makeButton("⚙");

        toolbar.addView(newButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        toolbar.addView(openButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        toolbar.addView(saveButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        toolbar.addView(runButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(52), dp(44)));

        root.addView(toolbar);

        LinearLayout fileBar = new LinearLayout(this);
        fileBar.setOrientation(LinearLayout.HORIZONTAL);
        fileBar.setGravity(Gravity.CENTER_VERTICAL);
        fileBar.setBackgroundColor(Color.rgb(18, 24, 33));
        fileBar.setPadding(dp(10), 0, dp(10), 0);

        fileNameView = makeText("main.php", 13, Color.rgb(170, 183, 202));
        fileNameView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        fileBar.addView(fileNameView, new LinearLayout.LayoutParams(0, dp(38), 1));

        languageView = makeText("PHP", 12, Color.rgb(120, 169, 255));
        languageView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        fileBar.addView(languageView, new LinearLayout.LayoutParams(dp(70), dp(38)));

        root.addView(fileBar);

        editorRow = new LinearLayout(this);
        editorRow.setOrientation(LinearLayout.HORIZONTAL);
        editorRow.setBackgroundColor(Color.rgb(18, 24, 33));

        lineNumbers = makeText("1", 13, Color.rgb(80, 96, 117));
        lineNumbers.setTypeface(Typeface.MONOSPACE);
        lineNumbers.setGravity(Gravity.TOP | Gravity.RIGHT);
        lineNumbers.setPadding(dp(8), dp(12), dp(8), 0);
        editorRow.addView(lineNumbers, new LinearLayout.LayoutParams(dp(44), -1));

        editor = new EditText(this);
        editor.setTextColor(Color.rgb(234, 240, 246));
        editor.setHintTextColor(Color.rgb(82, 98, 120));
        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(8), dp(10), dp(8), dp(16));
        editor.setBackgroundColor(Color.rgb(18, 24, 33));
        editor.setHint("Type your code here…");
        editor.setSingleLine(false);
        editor.setCursorVisible(true);
        editor.setHorizontallyScrolling(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setHorizontalScrollBarEnabled(true);
        editor.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        editorRow.addView(editor, new LinearLayout.LayoutParams(0, -1, 1));

        root.addView(editorRow, new LinearLayout.LayoutParams(-1, 0, 0.92f));

        LinearLayout inputPanel = new LinearLayout(this);
        inputPanel.setOrientation(LinearLayout.VERTICAL);
        inputPanel.setBackgroundColor(Color.rgb(16, 22, 30));
        TextView inputHeader = makeText("STDIN / INPUT (optional)", 10, Color.rgb(141, 154, 175));
        inputHeader.setTypeface(Typeface.DEFAULT_BOLD);
        inputHeader.setPadding(dp(10), 0, 0, 0);
        inputPanel.addView(inputHeader, new LinearLayout.LayoutParams(-1, dp(28)));
        stdinEditor = new EditText(this);
        stdinEditor.setSingleLine(false);
        stdinEditor.setGravity(Gravity.TOP | Gravity.START);
        stdinEditor.setTextColor(Color.rgb(234, 240, 246));
        stdinEditor.setHintTextColor(Color.rgb(82, 98, 120));
        stdinEditor.setHint("Enter program input here (one value per line)…");
        stdinEditor.setTextSize(12);
        stdinEditor.setTypeface(Typeface.MONOSPACE);
        stdinEditor.setPadding(dp(10), dp(4), dp(10), dp(6));
        stdinEditor.setBackgroundColor(Color.rgb(16, 22, 30));
        inputPanel.addView(stdinEditor, new LinearLayout.LayoutParams(-1, dp(64)));
        root.addView(inputPanel, new LinearLayout.LayoutParams(-1, dp(96)));

        outputPanel = new LinearLayout(this);
        outputPanel.setOrientation(LinearLayout.VERTICAL);
        outputPanel.setBackgroundColor(Color.rgb(13, 18, 25));

        TextView outputHeader = makeText("OUTPUT", 11, Color.rgb(141, 154, 175));
        outputHeader.setTypeface(Typeface.DEFAULT_BOLD);
        outputHeader.setPadding(dp(10), 0, 0, 0);
        outputPanel.addView(outputHeader, new LinearLayout.LayoutParams(-1, dp(32)));

        output = makeText("Press ▶ Run to execute or preview this program.", 13, Color.rgb(234, 240, 246));
        output.setTypeface(Typeface.MONOSPACE);
        output.setGravity(Gravity.TOP | Gravity.LEFT);
        output.setPadding(dp(10), dp(4), dp(10), dp(10));

        ScrollView outputScroll = new ScrollView(this);
        outputScroll.addView(output);
        outputPanel.addView(outputScroll, new LinearLayout.LayoutParams(-1, dp(128)));

        root.addView(outputPanel, new LinearLayout.LayoutParams(-1, dp(160)));

        preview = new WebView(this);
        preview.setBackgroundColor(Color.WHITE);
        preview.getSettings().setJavaScriptEnabled(true);
        preview.getSettings().setDomStorageEnabled(true);
        preview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                output.setText("[JavaScript] " + consoleMessage.message());
                return true;
            }
        });
        preview.setVisibility(View.GONE);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0));

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!suppressLanguageEvent) {
                    changeLanguage(languages[position]);
                }
            }
        });

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!internalChange) {
                    updateLineNumbers();
                    statusView.setText("EDITING");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        newButton.setOnClickListener(v -> newFile());
        openButton.setOnClickListener(v -> openFile());
        saveButton.setOnClickListener(v -> saveFile());
        runButton.setOnClickListener(v -> runCurrent());
        settingsButton.setOnClickListener(v -> showExecutionSettings());
    }

    private void updateLineNumbers() {
        int count = Math.max(1, editor.getLineCount());
        StringBuilder numbers = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            numbers.append(i);
            if (i < count) {
                numbers.append('\n');
            }
        }
        lineNumbers.setText(numbers.toString());
    }

    private void changeLanguage(String language) {
        hidePreview();
        languageView.setText(language);

        String extension = extension(language);
        File dir = currentFile == null ? new File(getFilesDir(), "projects/default") : currentFile.getParentFile();
        if (dir == null) {
            dir = new File(getFilesDir(), "projects/default");
        }
        currentFile = new File(dir, "main." + extension);

        if (currentFile.exists()) {
            loadFile(currentFile);
        } else {
            setEditorText(samples.get(language));
            fileNameView.setText(currentFile.getName());
            statusView.setText("NEW " + language.toUpperCase(Locale.US));
        }
    }

    private void setEditorText(String text) {
        internalChange = true;
        editor.setText(text == null ? "" : text);
        editor.setSelection(editor.length());
        internalChange = false;
        updateLineNumbers();
    }

    private void newFile() {
        String language = (String) languageSpinner.getSelectedItem();
        currentFile = new File(getFilesDir(), "projects/default/main." + extension(language));
        writeText(currentFile, samples.get(language));
        loadFile(currentFile);
        statusView.setText("NEW FILE");
    }

    private void loadFile(File file) {
        currentFile = file;

        String content = readText(file);
        setEditorText(content);

        fileNameView.setText(file.getName());

        String language = languageForExtension(file.getName());
        int index = Arrays.asList(languages).indexOf(language);
        if (index >= 0) {
            suppressLanguageEvent = true;
            languageSpinner.setSelection(index, false);
            suppressLanguageEvent = false;
            languageView.setText(language);
        }

        hidePreview();
        statusView.setText("READY");
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, OPEN_FILE);
    }

    private void saveFile() {
        if (currentFile == null) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "main.txt");
            startActivityForResult(intent, SAVE_FILE);
            return;
        }

        writeText(currentFile, editor.getText().toString());
        statusView.setText("SAVED");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == SAVE_FILE) {
            try {
                String content = editor.getText().toString();
                getContentResolver().openOutputStream(uri).write(content.getBytes(StandardCharsets.UTF_8));
                statusView.setText("SAVED");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
            return;
        }

        if (requestCode == OPEN_FILE) {
            try {
                String content = readUri(uri);
                String name = extractFileName(uri);
                String language = languageForExtension(name);

                currentFile = new File(getFilesDir(), "projects/default/" + name);
                writeText(currentFile, content);
                loadFile(currentFile);

                if (Arrays.asList(languages).contains(language)) {
                    languageView.setText(language);
                }
            } catch (Exception e) {
                toast("Could not open file: " + e.getMessage());
            }
        }
    }

    private String extractFileName(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.trim().isEmpty()) {
            return "main.txt";
        }

        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.contains(":")) {
            name = name.substring(name.lastIndexOf(':') + 1);
        }
        return name.isEmpty() ? "main.txt" : name;
    }

    private String readUri(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalStateException("No readable stream");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[8192];
        int read;
        while ((read = input.read(bytes)) > 0) {
            buffer.write(bytes, 0, read);
        }
        input.close();
        return buffer.toString("UTF-8");
    }

    private void runCurrent() {
        saveFile();

        String language = (String) languageSpinner.getSelectedItem();
        String code = editor.getText().toString();

        hidePreview();
        output.setText("");
        statusView.setText("RUNNING");

        switch (language) {
            case "HTML":
                runHtml(code);
                break;
            case "CSS":
                runCss(code);
                break;
            case "SQL":
                runSql(code);
                break;
            case "JavaScript":
            case "PHP":
            case "Python":
            case "C":
            case "C++":
            case "Java":
            case "Bash":
                runThroughOneCompiler(language, code, stdinEditor.getText().toString());
                break;
            default:
                runtimeNotice(language);
                break;
        }
    }

    private String oneCompilerLanguage(String language) {
        switch (language) {
            case "PHP": return "php";
            case "Python": return "python";
            case "JavaScript": return "javascript";
            case "C": return "c";
            case "C++": return "cpp";
            case "Java": return "java";
            case "Bash": return "bash";
            default: return language.toLowerCase(Locale.US);
        }
    }

    private String oneCompilerFileName(String language) {
        return "main." + extension(language);
    }

    private String getOneCompilerKey() {
        return getSharedPreferences("execution", MODE_PRIVATE).getString("onecompiler_key", "");
    }

    private void showExecutionSettings() {
        final EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(0x81);
        key.setHint("OneCompiler API key");
        key.setText(getOneCompilerKey());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        box.addView(key, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView info = makeText(
                "Real PHP/Python/C/C++/Java/JavaScript/Bash execution uses OneCompiler's secure execution API. " +
                "Get an API key from OneCompiler, paste it here, and it is stored only on this device.",
                12, Color.rgb(141, 154, 175));
        info.setPadding(dp(8), dp(4), dp(8), dp(4));
        box.addView(info, new LinearLayout.LayoutParams(-1, dp(90)));

        new AlertDialog.Builder(this)
                .setTitle("Code Execution")
                .setView(box)
                .setPositiveButton("Save", (dialog, which) -> {
                    getSharedPreferences("execution", MODE_PRIVATE)
                            .edit()
                            .putString("onecompiler_key", key.getText().toString().trim())
                            .apply();
                    statusView.setText(key.getText().length() > 0 ? "API READY" : "READY");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runThroughOneCompiler(String language, String code, String stdin) {
        String apiKey = getOneCompilerKey();
        if (apiKey.isEmpty()) {
            statusView.setText("API KEY NEEDED");
            output.setText(
                    "Real " + language + " execution is enabled through OneCompiler.\n\n" +
                    "Tap ⚙ → enter your OneCompiler API key → Save → Run."
            );
            showExecutionSettings();
            return;
        }

        final String apiLanguage = oneCompilerLanguage(language);
        final String fileName = oneCompilerFileName(language);

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.onecompiler.org/v1/run");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-API-Key", apiKey);

                org.json.JSONObject request = new org.json.JSONObject();
                request.put("language", apiLanguage);
                request.put("stdin", stdin == null ? "" : stdin);

                org.json.JSONArray files = new org.json.JSONArray();
                org.json.JSONObject source = new org.json.JSONObject();
                source.put("name", fileName);
                source.put("content", code);
                files.put(source);
                request.put("files", files);

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body);
                }

                int responseCode = connection.getResponseCode();
                InputStreamReader reader = new InputStreamReader(
                        responseCode >= 200 && responseCode < 400
                                ? connection.getInputStream()
                                : connection.getErrorStream(),
                        StandardCharsets.UTF_8);
                BufferedReader buffered = new BufferedReader(reader);
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = buffered.readLine()) != null) {
                    response.append(line);
                }

                org.json.JSONObject result = new org.json.JSONObject(response.toString());
                StringBuilder shown = new StringBuilder();
                shown.append("Language: ").append(language).append("\n");
                shown.append("Status: ").append(result.optString("status", "unknown")).append("\n");

                String exception = result.optString("exception", "");
                String stderr = result.optString("stderr", "");
                String stdout = result.optString("stdout", "");

                if (!stdout.isEmpty()) {
                    shown.append("\nOUTPUT\n").append(stdout);
                }
                if (!stderr.isEmpty()) {
                    shown.append("\n\nERROR\n").append(stderr);
                }
                if (!exception.isEmpty()) {
                    shown.append("\n\nEXCEPTION\n").append(exception);
                }

                if (result.has("compilationTime")) {
                    shown.append("\n\nCompile: ").append(result.optInt("compilationTime")).append(" ms");
                }
                if (result.has("executionTime")) {
                    shown.append("\nRun: ").append(result.optInt("executionTime")).append(" ms");
                }
                if (result.has("memoryUsed")) {
                    shown.append("\nMemory: ").append(result.optInt("memoryUsed")).append(" KB");
                }
                if (result.has("error")) {
                    shown.append("\n\nAPI ERROR\n").append(result.optString("error"));
                }

                final String text = shown.toString();
                runOnUiThread(() -> {
                    output.setText(text);
                    statusView.setText(responseCode >= 200 && responseCode < 300 ? "DONE" : "API ERROR");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    output.setText("Execution connection error:\n" + e.getMessage());
                    statusView.setText("NETWORK ERROR");
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

        saveFile();

        String language = (String) languageSpinner.getSelectedItem();
        String code = editor.getText().toString();

        hidePreview();
        output.setText("");
        statusView.setText("RUNNING");

        switch (language) {
            case "HTML":
                runHtml(code);
                break;
            case "CSS":
                runCss(code);
                break;
            case "JavaScript":
                runJavaScript(code);
                break;
            case "SQL":
                runSql(code);
                break;
            default:
                runtimeNotice(language);
                break;
        }
    }

    private void runHtml(String html) {
        showPreview(210, 110);
        preview.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
        output.setText("HTML preview loaded.");
        statusView.setText("PREVIEW");
    }

    private void runCss(String css) {
        String html = "<!doctype html><html><head><style>" +
                escapeHtml(css) +
                "</style></head><body><h2>CSS Preview</h2><div class='note'>" +
                "Styles loaded successfully.</div></body></html>";

        showPreview(210, 110);
        preview.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
        output.setText("CSS preview loaded.");
        statusView.setText("PREVIEW");
    }

    private void runJavaScript(String code) {
        String html = "<!doctype html><html><body><pre id='out'></pre><script>" +
                "const output=[];" +
                "const originalLog=console.log;" +
                "console.log=function(){const v=Array.from(arguments).join(' ');output.push(v);originalLog.apply(console,arguments);};" +
                "try{eval(" + javascriptQuote(code) + ");document.getElementById('out').textContent=output.join('\\n');}" +
                "catch(e){document.getElementById('out').textContent='Error: '+e.message;}" +
                "</script></body></html>";

        showPreview(140, 180);
        preview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        statusView.setText("RUNNING");
    }

    private void runSql(String code) {
        SQLiteDatabase database = null;

        try {
            database = SQLiteDatabase.create(null);
            StringBuilder result = new StringBuilder();

            for (String raw : code.split(";")) {
                String statement = raw.trim();
                if (statement.isEmpty()) {
                    continue;
                }

                String upper = statement.toUpperCase(Locale.US);

                if (upper.startsWith("SELECT") ||
                        upper.startsWith("PRAGMA") ||
                        upper.startsWith("WITH")) {

                    Cursor cursor = database.rawQuery(statement, null);
                    while (cursor.moveToNext()) {
                        for (int i = 0; i < cursor.getColumnCount(); i++) {
                            if (i > 0) {
                                result.append(" | ");
                            }
                            result.append(cursor.getString(i));
                        }
                        result.append('\n');
                    }
                    cursor.close();
                } else {
                    database.execSQL(statement);
                    result.append("OK: ")
                            .append(statement.split("\\s+")[0])
                            .append('\n');
                }
            }

            output.setText(result.length() == 0
                    ? "SQL executed successfully."
                    : result.toString());
            statusView.setText("DONE");
        } catch (Exception e) {
            output.setText("SQL Error: " + e.getMessage());
            statusView.setText("ERROR");
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    private void runtimeNotice(String language) {
        output.setText(
                language + " support is installed in the IDE, but this build does not bundle a native " +
                language + " compiler/interpreter yet.\n\n" +
                "The editor, project files and language-aware UI are ready.\n\n" +
                "A real on-device runtime pack is required for true local execution."
        );
        statusView.setText("RUNTIME NEEDED");
    }

    private void showPreview(int previewHeight, int outputHeight) {
        preview.setVisibility(View.VISIBLE);
        preview.getLayoutParams().height = dp(previewHeight);
        outputPanel.getLayoutParams().height = dp(outputHeight);
        preview.requestLayout();
        outputPanel.requestLayout();
    }

    private void hidePreview() {
        if (preview != null) {
            preview.setVisibility(View.GONE);
            preview.getLayoutParams().height = 0;
        }
        if (outputPanel != null) {
            outputPanel.getLayoutParams().height = dp(160);
            outputPanel.requestLayout();
        }
    }

    private String extension(String language) {
        switch (language) {
            case "PHP": return "php";
            case "Python": return "py";
            case "JavaScript": return "js";
            case "HTML": return "html";
            case "CSS": return "css";
            case "C": return "c";
            case "C++": return "cpp";
            case "Java": return "java";
            case "SQL": return "sql";
            default: return "sh";
        }
    }

    private String languageForExtension(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".js")) return "JavaScript";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
        if (lower.endsWith(".css")) return "CSS";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "C++";
        if (lower.endsWith(".c")) return "C";
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".sql")) return "SQL";
        return "Bash";
    }

    private String javascriptQuote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (ch == 0x2028) {
                        out.append("\\u2028");
                    } else if (ch == 0x2029) {
                        out.append("\\u2029");
                    } else {
                        out.append(ch);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String readText(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void writeText(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
