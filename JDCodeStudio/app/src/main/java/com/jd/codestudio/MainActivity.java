package com.jd.codestudio;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int OPEN_FILE = 1001;
    private static final int SAVE_FILE = 1002;

    private LinearLayout root, editorRow, outputPanel;
    private EditText editor;
    private TextView lineNumbers, output, fileNameView, languageView, statusView;
    private WebView preview;
    private Spinner languageSpinner;
    private boolean internalChange = false;
    private boolean suppressLanguageEvent = false;
    private File currentFile;
    private final Map<String, String> samples = new LinkedHashMap<>();
    private final String[] languages = {"PHP","Python","JavaScript","HTML","CSS","C","C++","Java","SQL","Bash"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(11,15,20));
        getWindow().setNavigationBarColor(Color.rgb(11,15,20));
        initSamples();
        buildUi();
        File dir = new File(getFilesDir(), "projects/default");
        if (!dir.exists()) dir.mkdirs();
        currentFile = new File(dir, "main.php");
        if (!currentFile.exists()) writeText(currentFile, samples.get("PHP"));
        loadFile(currentFile);
    }

    private void initSamples() {
        samples.put("PHP", "<?php\n\n$name = \"JD\";\n$marks = 85;\n\necho \"Name: \" . $name . \"<br>\";\necho \"Marks: \" . $marks;\n\n?>");
        samples.put("Python", "name = \"JD\"\nmarks = 85\nprint(\"Name:\", name)\nprint(\"Marks:\", marks)");
        samples.put("JavaScript", "const name = \"JD\";\nconst marks = 85;\nconsole.log(\"Name:\", name);\nconsole.log(\"Marks:\", marks);");
        samples.put("HTML", "<!doctype html>\n<html>\n<body>\n<h1>JD Code Studio</h1>\n<p>Hello from HTML.</p>\n</body>\n</html>");
        samples.put("CSS", "body { font-family: sans-serif; padding: 24px; }\nh1 { font-size: 28px; }\n.note { padding: 12px; border: 1px solid #ccc; }");
        samples.put("C", "#include <stdio.h>\nint main(){\n  printf(\"Hello from C\\n\");\n  return 0;\n}");
        samples.put("C++", "#include <iostream>\nint main(){\n  std::cout << \"Hello from C++\\n\";\n  return 0;\n}");
        samples.put("Java", "public class Main {\n  public static void main(String[] args) {\n    System.out.println(\"Hello from Java\");\n  }\n}");
        samples.put("SQL", "CREATE TABLE students(id INTEGER, name TEXT, marks INTEGER);\nINSERT INTO students VALUES(1,'JD',85);\nINSERT INTO students VALUES(2,'Riya',92);\nSELECT * FROM students;");
        samples.put("Bash", "echo \"Hello from Bash\"\nname=JD\necho \"Name: $name\"");
    }

    private int dp(float v){ return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView tv(String text, float size, int color){
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private Button btn(String text){
        Button b=new Button(this); b.setText(text); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setMinHeight(dp(42)); b.setPadding(dp(10),0,dp(10),0); return b;
    }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(11,15,20));
        root.setPadding(dp(12),dp(8),dp(12),dp(8)); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(dp(12), dp(8)+insets.getSystemWindowInsetTop()/2, dp(12), dp(8)+insets.getSystemWindowInsetBottom()/2); return insets; });

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=tv("JD Code Studio",21,Color.WHITE); brand.setTypeface(Typeface.DEFAULT_BOLD); top.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
        statusView=tv("READY",11,Color.rgb(83,212,154)); statusView.setTypeface(Typeface.DEFAULT_BOLD); top.addView(statusView,new LinearLayout.LayoutParams(dp(90),dp(48))); root.addView(top);

        LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(0,dp(3),0,dp(7));
        languageSpinner=new Spinner(this); ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,languages); languageSpinner.setAdapter(ad);
        controls.addView(languageSpinner,new LinearLayout.LayoutParams(dp(118),dp(44)));
        Button newBtn=btn("New"); Button openBtn=btn("Open"); Button saveBtn=btn("Save"); Button runBtn=btn("▶ Run");
        controls.addView(newBtn,new LinearLayout.LayoutParams(0,dp(44),1)); controls.addView(openBtn,new LinearLayout.LayoutParams(0,dp(44),1)); controls.addView(saveBtn,new LinearLayout.LayoutParams(0,dp(44),1)); controls.addView(runBtn,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(controls);

        LinearLayout nameRow=new LinearLayout(this); nameRow.setOrientation(LinearLayout.HORIZONTAL); nameRow.setBackgroundColor(Color.rgb(18,24,33)); nameRow.setPadding(dp(10),0,dp(10),0);
        fileNameView=tv("main.php",13,Color.rgb(141,154,175)); nameRow.addView(fileNameView,new LinearLayout.LayoutParams(0,dp(40),1));
        languageView=tv("PHP",12,Color.rgb(120,169,255)); nameRow.addView(languageView,new LinearLayout.LayoutParams(dp(82),dp(40))); root.addView(nameRow);

        editorRow=new LinearLayout(this); editorRow.setOrientation(LinearLayout.HORIZONTAL); editorRow.setBackgroundColor(Color.rgb(18,24,33));
        lineNumbers=tv("1",12,Color.rgb(76,92,113)); lineNumbers.setTypeface(Typeface.MONOSPACE); lineNumbers.setGravity(Gravity.TOP|Gravity.RIGHT); lineNumbers.setPadding(dp(8),dp(12),dp(8),0);
        editorRow.addView(lineNumbers,new LinearLayout.LayoutParams(dp(42),-1));
        editor=new EditText(this); editor.setTextColor(Color.rgb(234,240,246)); editor.setHintTextColor(Color.rgb(90,105,125)); editor.setTextSize(14); editor.setTypeface(Typeface.MONOSPACE); editor.setGravity(Gravity.TOP|Gravity.START); editor.setPadding(dp(8),dp(10),dp(8),dp(20)); editor.setBackgroundColor(Color.rgb(18,24,33)); editor.setHint("Type your code here…"); editor.setHintTextColor(Color.rgb(82,98,120)); editor.setSingleLine(false); editor.setCursorVisible(true); editor.setHorizontallyScrolling(true); editor.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        editorRow.addView(editor,new LinearLayout.LayoutParams(0,-1,1)); root.addView(editorRow,new LinearLayout.LayoutParams(-1,0,1.0f));

        outputPanel=new LinearLayout(this); outputPanel.setOrientation(LinearLayout.VERTICAL); outputPanel.setBackgroundColor(Color.rgb(13,18,25));
        TextView outHead=tv("OUTPUT",11,Color.rgb(141,154,175)); outHead.setTypeface(Typeface.DEFAULT_BOLD); outHead.setPadding(dp(10),0,0,0); outputPanel.addView(outHead,new LinearLayout.LayoutParams(-1,dp(34)));
        output=tv("Run a program to see output.",13,Color.rgb(234,240,246)); output.setTypeface(Typeface.MONOSPACE); output.setGravity(Gravity.TOP|Gravity.LEFT); output.setPadding(dp(10),dp(4),dp(10),dp(10));
        ScrollView outScroll=new ScrollView(this); outScroll.addView(output); outputPanel.addView(outScroll,new LinearLayout.LayoutParams(-1,dp(150)));
        root.addView(outputPanel,new LinearLayout.LayoutParams(-1,dp(184)));

        preview=new WebView(this); preview.setBackgroundColor(Color.WHITE); preview.getSettings().setJavaScriptEnabled(true); preview.getSettings().setDomStorageEnabled(true);
        preview.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage cm){ output.setText("[JS] " + cm.message() + "\\n" + output.getText()); return true; }
        });
        preview.setVisibility(View.GONE); root.addView(preview,new LinearLayout.LayoutParams(-1,0,0));

        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ if(!suppressLanguageEvent) changeLanguage(languages[pos]); }
        });
        editor.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){ if(!internalChange){ updateLineNumbers(); highlight(); statusView.setText("EDITING"); }}
            public void afterTextChanged(Editable e){}
        });
        newBtn.setOnClickListener(v->newFile()); openBtn.setOnClickListener(v->openFile()); saveBtn.setOnClickListener(v->saveFile(false)); runBtn.setOnClickListener(v->runCurrent());
    }

    private void updateLineNumbers(){ int n=Math.max(1, editor.getLineCount()); StringBuilder sb=new StringBuilder(); for(int i=1;i<=n;i++){ sb.append(i); if(i<n) sb.append('\n'); } lineNumbers.setText(sb.toString()); }
    private void highlight(){
        if(internalChange) return;
        int colorText=Color.rgb(234,240,246); String code=editor.getText().toString(); SpannableStringBuilder s=new SpannableStringBuilder(code); s.setSpan(new ForegroundColorSpan(colorText),0,s.length(),0);
        String lang=languageView.getText().toString();
        String kws = lang.equals("Python") ? "\\b(and|as|assert|break|class|continue|def|elif|else|for|from|if|import|in|is|lambda|not|or|pass|print|return|True|False|while|with|yield)\\b" :
                lang.equals("JavaScript") ? "\\b(const|let|var|function|return|if|else|for|while|class|new|true|false|null|undefined|console)\\b" :
                lang.equals("PHP") ? "\\b(echo|if|else|elseif|while|for|foreach|function|return|true|false|null|array)\\b" :
                lang.equals("Java") ? "\\b(public|private|protected|class|static|void|int|double|String|new|return|if|else|for|while|true|false|null)\\b" :
                "\\b(int|char|float|double|void|return|if|else|for|while|class|public|private|static|include|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|TABLE|echo)\\b";
        applyRegex(s,kws,Color.rgb(120,169,255)); applyRegex(s,"(\\\"(?:\\\\\\.|[^\\\"])*\\\"|'(?:\\\\\\.|[^'])*')",Color.rgb(83,212,154));
        if(lang.equals("PHP")) applyRegex(s,"\\$[A-Za-z_][A-Za-z0-9_]*",Color.rgb(255,190,92));
        if(lang.equals("Python")||lang.equals("Bash")||lang.equals("C")||lang.equals("C++")) applyRegex(s,"#[^\\n]*|//[^\\n]*",Color.rgb(120,132,151));
        else applyRegex(s,"//[^\\n]*|/\\*[\\s\\S]*?\\*/",Color.rgb(120,132,151));
        internalChange=true; int pos=editor.getSelectionStart(); editor.setText(s); editor.setSelection(Math.min(pos,s.length())); internalChange=false;
    }
    private void applyRegex(SpannableStringBuilder s,String regex,int c){ try{ Matcher m=Pattern.compile(regex).matcher(s); while(m.find()) s.setSpan(new ForegroundColorSpan(c),m.start(),m.end(),0);}catch(Exception ignored){} }

    private void changeLanguage(String lang){
        languageView.setText(lang); String ext=extension(lang);
        if(currentFile!=null){ currentFile=new File(currentFile.getParentFile(), baseName(currentFile.getName(), ext)); }
        fileNameView.setText(currentFile==null ? "main."+ext : currentFile.getName());
        internalChange=true; editor.setText(samples.get(lang)); internalChange=false; updateLineNumbers(); highlight(); statusView.setText("READY");
    }
    private String extension(String l){ switch(l){case"PHP":return"php";case"Python":return"py";case"JavaScript":return"js";case"HTML":return"html";case"CSS":return"css";case"C":return"c";case"C++":return"cpp";case"Java":return"java";case"SQL":return"sql";default:return"sh";} }
    private String baseName(String name,String ext){ int p=name.lastIndexOf('.'); return (p>0?name.substring(0,p):name)+"."+ext; }

    private void newFile(){
        String lang=(String)languageSpinner.getSelectedItem(); String ext=extension(lang); currentFile=new File(getFilesDir(),"projects/default/main."+ext); writeText(currentFile,samples.get(lang)); loadFile(currentFile); statusView.setText("NEW FILE");
    }
    private void loadFile(File f){
        currentFile=f; String c=readText(f); internalChange=true; editor.setText(c); internalChange=false; fileNameView.setText(f.getName()); updateLineNumbers();
        String lang=languageForExt(f.getName()); int idx=Arrays.asList(languages).indexOf(lang);
        if(idx>=0){ suppressLanguageEvent=true; languageSpinner.setSelection(idx,false); suppressLanguageEvent=false; languageView.setText(lang); }
        highlight(); statusView.setText("READY");
    }
    private String languageForExt(String n){ String x=n.toLowerCase(Locale.US); if(x.endsWith(".php"))return"PHP"; if(x.endsWith(".py"))return"Python"; if(x.endsWith(".js"))return"JavaScript"; if(x.endsWith(".html")||x.endsWith(".htm"))return"HTML"; if(x.endsWith(".css"))return"CSS"; if(x.endsWith(".cpp")||x.endsWith(".cc")||x.endsWith(".cxx"))return"C++"; if(x.endsWith(".c"))return"C"; if(x.endsWith(".java"))return"Java"; if(x.endsWith(".sql"))return"SQL"; return"Bash"; }

    private void openFile(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("text/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,OPEN_FILE); }
    private void saveFile(boolean choose){ if(currentFile==null || choose){ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE,fileNameView.getText().toString()); startActivityForResult(i,SAVE_FILE); return;} writeText(currentFile,editor.getText().toString()); statusView.setText("SAVED"); }
    @Override protected void onActivityResult(int req,int res,Intent data){ super.onActivityResult(req,res,data); if(res!=RESULT_OK||data==null)return; Uri u=data.getData(); try{ String c=readUri(u); String name="main."+extension(languageForUri(u)); currentFile=new File(getFilesDir(),"projects/default/"+name); writeText(currentFile,c); loadFile(currentFile);}catch(Exception e){toast("Could not open file: "+e.getMessage());} }
    private String languageForUri(Uri u){ String p=u.getPath()==null?"":u.getPath(); return languageForExt(p); }
    private String readUri(Uri u)throws Exception{ InputStream in=getContentResolver().openInputStream(u); ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0)b.write(buf,0,n); in.close(); return b.toString("UTF-8"); }

    private void runCurrent(){
        saveFile(false); String lang=(String)languageSpinner.getSelectedItem(); String code=editor.getText().toString(); statusView.setText("RUNNING"); output.setText("");
        switch(lang){ case"HTML": runWeb(buildHtml(code)); break; case"CSS": runWeb("<html><head><style>"+escapeHtml(code)+"</style></head><body><h2 class='title'>CSS Preview</h2><div class='note'>Styles loaded successfully.</div></body></html>"); break; case"JavaScript": runJavaScript(code); break; case"SQL": runSql(code); break; default: runtimeNotice(lang); }
    }

    private void runWeb(String html){ preview.setVisibility(View.VISIBLE); preview.getLayoutParams().height=dp(210); outputPanel.getLayoutParams().height=dp(140); preview.requestLayout(); preview.loadDataWithBaseURL("https://localhost/",html,"text/html","UTF-8",null); output.setText("Web preview loaded."); statusView.setText("PREVIEW"); }
    private String buildHtml(String h){ return h; }
    private void runJavaScript(String code){ preview.setVisibility(View.VISIBLE); preview.getLayoutParams().height=dp(120); outputPanel.getLayoutParams().height=dp(230); preview.requestLayout(); String q=JSONObject.quote(code); String html="<html><body><pre id='out'></pre><script>const o=[]; const old=console.log; console.log=function(){o.push(Array.from(arguments).join(' ')); old.apply(console,arguments);}; try{eval("+q+");}catch(e){console.log('Error:',e.message)} document.getElementById('out').textContent=o.join('\\n');</script></body></html>"; preview.loadDataWithBaseURL(null,html,"text/html","UTF-8",null); statusView.setText("RUNNING"); }
    private void runSql(String code){ try{ SQLiteDatabase db=SQLiteDatabase.create(null); StringBuilder out=new StringBuilder(); for(String raw:code.split(";")){ String stmt=raw.trim(); if(stmt.isEmpty())continue; String upper=stmt.toUpperCase(Locale.US); if(upper.startsWith("SELECT")||upper.startsWith("PRAGMA")||upper.startsWith("WITH")){ Cursor c=db.rawQuery(stmt,null); while(c.moveToNext()){ for(int i=0;i<c.getColumnCount();i++){ if(i>0)out.append(" | "); out.append(c.getString(i)); } out.append('\n'); } c.close(); } else { db.execSQL(stmt); out.append("OK: ").append(stmt.split("\\s+")[0]).append('\n'); } } db.close(); output.setText(out.length()==0?"SQL executed successfully.":out.toString()); statusView.setText("DONE"); } catch(Exception e){ output.setText("SQL Error: "+e.getMessage()); statusView.setText("ERROR"); } }
    private void hidePreview(){ preview.setVisibility(View.GONE); preview.getLayoutParams().height=0; root.requestLayout(); }\n    private void runtimeNotice(String lang){ StringBuilder b=new StringBuilder(); b.append(lang).append(" runtime adapter is ready, but this APK's v1 build does not bundle the native ").append(lang).append(" toolchain.\n\n"); b.append("The IDE/editor, files, syntax highlighting and project workflow are available.\n\n"); b.append("For true offline execution, a signed runtime pack for ").append(lang).append(" must be installed on-device. This build deliberately does not pretend a simulator is the real language runtime."); output.setText(b.toString()); statusView.setText("RUNTIME NEEDED"); }

    private String escapeHtml(String s){ return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    private String readText(File f){ try{ return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}catch(Exception e){return "";} }
    private void writeText(File f,String s){ try{ File p=f.getParentFile(); if(p!=null)p.mkdirs(); java.nio.file.Files.write(f.toPath(),s.getBytes(StandardCharsets.UTF_8)); }catch(Exception e){ toast("Save failed: "+e.getMessage()); } }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
