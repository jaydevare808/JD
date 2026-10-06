package com.jd.hsc.testseries;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity {
    private static final int BG = Color.rgb(11, 16, 32);
    private static final int SURFACE = Color.rgb(21, 28, 50);
    private static final int SURFACE2 = Color.rgb(29, 39, 69);
    private static final int PRIMARY = Color.rgb(79, 124, 255);
    private static final int TEXT = Color.rgb(244, 247, 255);
    private static final int MUTED = Color.rgb(170, 180, 208);
    private static final int SUCCESS = Color.rgb(57, 217, 138);
    private static final int DANGER = Color.rgb(255, 107, 122);
    private static final int WARNING = Color.rgb(255, 200, 87);

    private LinearLayout root;
    private String screen = "home";
    private String selectedSubject = "";
    private Test selectedTest;
    private int currentQuestion = 0;
    private int[] answers;
    private boolean[] review;
    private CountDownTimer timer;
    private TextView timerText;
    private TextView questionCounter;
    private LinearLayout palette;
    private SharedPreferences prefs;

    private final ArrayList<Test> tests = buildTests();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("hsc_test_series", MODE_PRIVATE);
        showHome();
    }

    @Override public void onBackPressed() {
        if ("exam".equals(screen)) {
            new AlertDialog.Builder(this).setTitle("Exit test?")
                    .setMessage("Your current answers will be lost in this demo attempt.")
                    .setNegativeButton("Stay", null)
                    .setPositiveButton("Exit", (d,w) -> { stopTimer(); showTestList(selectedSubject); }).show();
        } else if ("tests".equals(screen) || "history".equals(screen)) showHome();
        else if ("result".equals(screen)) showTestList(selectedSubject);
        else super.onBackPressed();
    }

    private void base(String title, String subtitle) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(20),dp(18),dp(20),dp(10));
        header.addView(text(title,25,TEXT,true));
        if (subtitle != null && !subtitle.isEmpty()) { TextView s=text(subtitle,13,MUTED,false); LinearLayout.LayoutParams sp=lp(-1,-2); sp.topMargin=dp(4); header.addView(s,sp); }
        root.addView(header,lp(-1,-2)); setContentView(root);
    }

    private void showHome() {
        stopTimer(); screen="home"; base("HSC TEST SERIES 2027","12th Maharashtra HSC • Practice smarter, score higher");
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(10));
        ScrollView scroll=new ScrollView(this); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout hero=card(); hero.addView(pill("DEMO BUILD • OFFLINE"),lpWrap());
        addTop(hero,text("Your board-style practice hub",22,TEXT,true),10);
        addTop(hero,text("Choose a subject, start a timed test, mark questions for review and get an instant score with answer analysis.",14,MUTED,false),7);
        Button start=primaryButton("Browse Tests"); addTop(hero,start,15); start.setOnClickListener(v->showSubjects()); content.addView(hero,lp(-1,-2));
        addTop(content,text("Subjects",18,TEXT,true),18);

        String[][] subjects={{"Physics","Concepts • numericals","⚛"},{"Chemistry","Reactions • concepts","⚗"},{"Mathematics & Statistics","Problem solving","∑"},{"Biology","Theory • diagrams","BIO"},{"English","Language • literature","A"},{"Information Technology","Emerging tech • practical","IT"},{"Computer Science","Java • programming","</>"}};
        for (String[] s:subjects) {
            LinearLayout c=card(); LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(centeredBox(s[2],46,PRIMARY,20,true),lp(46,46));
            LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.addView(text(s[0],16,TEXT,true)); addTop(mid,text(s[1],12,MUTED,false),3);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1); mp.leftMargin=dp(12); row.addView(mid,mp); row.addView(text("›",28,MUTED,false),lpWrap()); c.addView(row);
            c.setOnClickListener(v->showTestList(s[0])); LinearLayout.LayoutParams cp=lp(-1,-2); cp.topMargin=dp(9); content.addView(c,cp);
        }
        bottomNav("home");
    }

    private void showSubjects() {
        screen="tests"; base("Choose a subject","All demo papers are local to this test build");
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(12));
        ScrollView scroll=new ScrollView(this); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String[] names={"Physics","Chemistry","Mathematics & Statistics","Biology","English","Information Technology","Computer Science"};
        for(String n:names){ Button b=outlineButton(n); b.setOnClickListener(v->showTestList(n)); LinearLayout.LayoutParams bp=lp(-1,dp(55)); bp.bottomMargin=dp(10); content.addView(b,bp); }
        bottomNav("tests");
    }

    private void showTestList(String subject) {
        stopTimer(); screen="tests"; selectedSubject=subject; base(subject,"Available practice papers");
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(12));
        ScrollView scroll=new ScrollView(this); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(Test t:tests) if(t.subject.equals(subject)){
            LinearLayout c=card(); c.addView(text(t.title,17,TEXT,true)); addTop(c,text(t.description,13,MUTED,false),5);
            LinearLayout meta=new LinearLayout(this); meta.setGravity(Gravity.CENTER_VERTICAL); meta.addView(pill(t.questions.size()+" questions"));
            meta.addView(text("  •  "+t.durationMinutes+" min  •  "+t.questions.size()+" marks",12,MUTED,false)); addTop(c,meta,11);
            Button start=primaryButton("Start test"); addTop(c,start,12); start.setOnClickListener(v->startTest(t));
            LinearLayout.LayoutParams cp=lp(-1,-2); cp.bottomMargin=dp(10); content.addView(c,cp);
        }
        Button back=outlineButton("← All subjects"); back.setOnClickListener(v->showSubjects()); content.addView(back,lp(-1,dp(50))); bottomNav("tests");
    }

    private void startTest(Test t) {
        selectedTest=t; currentQuestion=0; answers=new int[t.questions.size()]; review=new boolean[t.questions.size()];
        for(int i=0;i<answers.length;i++) answers[i]=-1;
        screen="exam"; renderExam();
        timer=new CountDownTimer(t.durationMinutes*60000L,1000){
            @Override public void onTick(long ms){ updateTimer(ms); }
            @Override public void onFinish(){ updateTimer(0); Toast.makeText(MainActivity.this,"Time is up. Submitting test.",Toast.LENGTH_LONG).show(); submitTest(); }
        }.start();
    }

    private void renderExam() {
        base(selectedTest.title,selectedTest.subject+" • Timed test");
        LinearLayout wrapper=new LinearLayout(this); wrapper.setOrientation(LinearLayout.VERTICAL); wrapper.setPadding(dp(12),dp(4),dp(12),dp(10)); root.addView(wrapper,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout top=card(); LinearLayout topRow=new LinearLayout(this); topRow.setGravity(Gravity.CENTER_VERTICAL);
        questionCounter=text("Question "+(currentQuestion+1)+" / "+selectedTest.questions.size(),14,TEXT,true); topRow.addView(questionCounter,new LinearLayout.LayoutParams(0,-2,1));
        timerText=centeredBox("--:--",92,DANGER,14,true); topRow.addView(timerText,lp(92,42)); top.addView(topRow);
        addTop(top,text("Tap a number to jump • ★ marks for review",11,MUTED,false),7); wrapper.addView(top,lp(-1,-2));

        HorizontalScrollView h=new HorizontalScrollView(this); h.setHorizontalScrollBarEnabled(false); palette=new LinearLayout(this); palette.setPadding(dp(2),dp(8),dp(2),dp(4)); h.addView(palette,new ViewGroup.LayoutParams(-2,dp(48))); wrapper.addView(h,lp(-1,dp(56))); updatePalette();

        ScrollView scroll=new ScrollView(this); LinearLayout qbox=new LinearLayout(this); qbox.setOrientation(LinearLayout.VERTICAL); qbox.setPadding(dp(4),dp(4),dp(4),dp(4)); scroll.addView(qbox); wrapper.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Question q=selectedTest.questions.get(currentQuestion); LinearLayout questionCard=card();
        LinearLayout qr=new LinearLayout(this); qr.setGravity(Gravity.CENTER_VERTICAL); qr.addView(text("Q"+(currentQuestion+1),13,PRIMARY,true),lpWrap()); qr.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));
        Button reviewBtn=smallButton(review[currentQuestion]?"★ Reviewed":"☆ Review"); reviewBtn.setOnClickListener(v->{review[currentQuestion]=!review[currentQuestion];renderExam();}); qr.addView(reviewBtn,lpWrap()); questionCard.addView(qr);
        addTop(questionCard,text(q.text,20,TEXT,true),14); if(q.note!=null&&!q.note.isEmpty()) addTop(questionCard,text(q.note,12,MUTED,false),8); qbox.addView(questionCard,lp(-1,-2));

        RadioGroup options=new RadioGroup(this); options.setOrientation(RadioGroup.VERTICAL); options.setPadding(0,dp(8),0,0);
        for(int i=0;i<q.options.length;i++){ RadioButton rb=new RadioButton(this); rb.setText(q.options[i]); rb.setTextColor(TEXT); rb.setTextSize(15); rb.setGravity(Gravity.CENTER_VERTICAL); rb.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY)); rb.setPadding(dp(12),dp(2),dp(10),dp(2)); if(answers[currentQuestion]==i) rb.setChecked(true); final int idx=i; rb.setOnClickListener(v->{answers[currentQuestion]=idx;updatePalette();}); LinearLayout.LayoutParams rp=lp(-1,dp(54)); rp.bottomMargin=dp(8); options.addView(rb,rp); }
        qbox.addView(options);

        LinearLayout action=new LinearLayout(this); action.setGravity(Gravity.CENTER_VERTICAL); Button prev=outlineButton("Previous"); prev.setEnabled(currentQuestion>0); prev.setOnClickListener(v->{currentQuestion--;renderExam();}); action.addView(prev,new LinearLayout.LayoutParams(0,dp(52),1));
        action.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1)); Button next=primaryButton(currentQuestion==selectedTest.questions.size()-1?"Review & Submit":"Next"); next.setOnClickListener(v->{if(currentQuestion==selectedTest.questions.size()-1) confirmSubmit(); else{currentQuestion++;renderExam();}}); action.addView(next,new LinearLayout.LayoutParams(0,dp(52),1)); qbox.addView(action,lp(-1,dp(58)));
        Button finish=outlineButton("Submit test now"); finish.setOnClickListener(v->confirmSubmit()); LinearLayout.LayoutParams fp=lp(-1,dp(48)); fp.topMargin=dp(8); qbox.addView(finish,fp);
    }

    private void updatePalette() {
        if(palette==null||selectedTest==null)return; palette.removeAllViews();
        for(int i=0;i<selectedTest.questions.size();i++){ Button b=smallButton((i+1)+(review[i]?"★":"")); final int idx=i; b.setTextColor(i==currentQuestion?Color.WHITE:TEXT); b.setBackground(round(i==currentQuestion?PRIMARY:(review[i]?WARNING:(answers[i]!=-1?SURFACE2:SURFACE)),12)); b.setOnClickListener(v->{currentQuestion=idx;renderExam();}); LinearLayout.LayoutParams bp=lp(58,40); bp.rightMargin=dp(6); palette.addView(b,bp); }
    }

    private void confirmSubmit(){
        int attempted=0; for(int a:answers)if(a!=-1)attempted++;
        new AlertDialog.Builder(this).setTitle("Submit test?").setMessage("Attempted: "+attempted+"/"+answers.length+"\nUnattempted questions are marked unattempted in the result.")
                .setNegativeButton("Continue",null).setPositiveButton("Submit",(d,w)->submitTest()).show();
    }

    private void submitTest(){
        stopTimer(); int correct=0,wrong=0,attempted=0;
        for(int i=0;i<answers.length;i++){if(answers[i]==-1)continue;attempted++;if(answers[i]==selectedTest.questions.get(i).correct)correct++;else wrong++;}
        saveHistory(selectedTest.subject,selectedTest.title,correct,answers.length); showResult(correct,wrong,answers.length-attempted,correct,answers.length);
    }

    private void showResult(int correct,int wrong,int unattempted,int score,int total){
        screen="result"; base("Test submitted",selectedTest.subject+" • "+selectedTest.title);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(12)); ScrollView scroll=new ScrollView(this); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout scoreCard=card(); TextView sc=centeredBox(score+"/"+total,92,score>=Math.ceil(total*.6)?SUCCESS:WARNING,27,true); scoreCard.addView(sc,lp(-1,92)); TextView pct=text(String.format(Locale.US,"%.0f%%",(score*100.0)/total),22,TEXT,true); pct.setGravity(Gravity.CENTER); scoreCard.addView(pct,lp(-1,dp(32))); scoreCard.addView(text("Demo score • no negative marking",12,MUTED,false)); content.addView(scoreCard,lp(-1,-2));
        LinearLayout stats=new LinearLayout(this); stats.setGravity(Gravity.CENTER); stats.addView(stat("Correct",String.valueOf(correct),SUCCESS),new LinearLayout.LayoutParams(0,dp(88),1)); stats.addView(stat("Wrong",String.valueOf(wrong),DANGER),new LinearLayout.LayoutParams(0,dp(88),1)); stats.addView(stat("Skipped",String.valueOf(unattempted),WARNING),new LinearLayout.LayoutParams(0,dp(88),1)); LinearLayout.LayoutParams stp=lp(-1,dp(96)); stp.topMargin=dp(10); content.addView(stats,stp);
        addTop(content,text("Answer review",18,TEXT,true),16);
        for(int i=0;i<selectedTest.questions.size();i++){ Question q=selectedTest.questions.get(i); int a=answers[i]; boolean ok=a==q.correct; LinearLayout c=card(); c.addView(text("Q"+(i+1)+"  "+(ok?"✓ Correct":"✗ Review"),14,ok?SUCCESS:DANGER,true)); addTop(c,text(q.text,15,TEXT,false),6); addTop(c,text("Your answer: "+(a==-1?"Not attempted":q.options[a]),12,MUTED,false),8); if(!ok)addTop(c,text("Correct answer: "+q.options[q.correct],12,SUCCESS,true),3); LinearLayout.LayoutParams cp=lp(-1,-2); cp.bottomMargin=dp(8); content.addView(c,cp); }
        Button retest=primaryButton("Retake test"); retest.setOnClickListener(v->startTest(selectedTest)); content.addView(retest,lp(-1,dp(52))); Button back=outlineButton("Back to subject tests"); back.setOnClickListener(v->showTestList(selectedSubject)); LinearLayout.LayoutParams bp=lp(-1,dp(50));bp.topMargin=dp(8);content.addView(back,bp); bottomNav("tests");
    }

    private LinearLayout stat(String label,String value,int color){ LinearLayout c=card(); c.setGravity(Gravity.CENTER); TextView v=text(value,24,color,true);v.setGravity(Gravity.CENTER);c.addView(v,lp(-1,dp(35)));TextView l=text(label,11,MUTED,false);l.setGravity(Gravity.CENTER);c.addView(l,lp(-1,dp(28)));return c; }

    private void showHistory(){
        screen="history"; base("History","Recent test attempts stored on this device"); LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String data=prefs.getString("history",""); if(data.isEmpty()){LinearLayout empty=card();empty.addView(centeredBox("⌁",58,SURFACE2,22,true),lp(-1,70));TextView h=text("No attempts yet",18,TEXT,true);h.setGravity(Gravity.CENTER);empty.addView(h,lp(-1,dp(30)));TextView p=text("Complete a test and your result will appear here.",13,MUTED,false);p.setGravity(Gravity.CENTER);addTop(empty,p,3);content.addView(empty,lp(-1,-2));}
        else{for(String e:data.split("\\n")){String[] f=e.split("\\|",-1);if(f.length<5)continue;LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);mid.addView(text(f[0],12,PRIMARY,true));addTop(mid,text(f[1],16,TEXT,true),3);addTop(mid,text(f[4],11,MUTED,false),4);row.addView(mid,new LinearLayout.LayoutParams(0,-2,1));TextView sc=text(f[2]+"/"+f[3],20,TEXT,true);sc.setGravity(Gravity.CENTER);row.addView(sc,lp(84,dp(52)));c.addView(row);LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(8);content.addView(c,cp);}Button clear=outlineButton("Clear history");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear history?").setMessage("This deletes saved local attempt history.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{prefs.edit().remove("history").apply();showHistory();}).show());content.addView(clear,lp(-1,dp(50)));}
        bottomNav("history");
    }

    private void bottomNav(String active){LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),dp(6),dp(10),dp(10));nav.setGravity(Gravity.CENTER);Button home=navButton("⌂  Home","home".equals(active));Button testsB=navButton("▦  Tests","tests".equals(active));Button hist=navButton("◷  History","history".equals(active));nav.addView(home,new LinearLayout.LayoutParams(0,dp(46),1));nav.addView(testsB,new LinearLayout.LayoutParams(0,dp(46),1));nav.addView(hist,new LinearLayout.LayoutParams(0,dp(46),1));home.setOnClickListener(v->showHome());testsB.setOnClickListener(v->showSubjects());hist.setOnClickListener(v->showHistory());root.addView(nav,lp(-1,dp(60)));}

    private Button navButton(String label,boolean active){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(active?Color.WHITE:MUTED);b.setGravity(Gravity.CENTER);b.setBackground(round(active?SURFACE2:BG,14));return b;}
    private TextView text(String v,float s,int c,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(c);t.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);t.setIncludeFontPadding(true);return t;}
    private TextView pill(String v){TextView t=text(v,11,PRIMARY,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),dp(4),dp(10),dp(4));t.setBackground(round(Color.rgb(33,52,104),20));return t;}
    private TextView centeredBox(String v,int ignored,int bg,float size,boolean bold){TextView t=text(v,size,TEXT,bold);t.setGravity(Gravity.CENTER);t.setBackground(round(bg,14));return t;}
    private Button primaryButton(String l){Button b=new Button(this);b.setText(l);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(PRIMARY,14));return b;}
    private Button outlineButton(String l){Button b=new Button(this);b.setText(l);b.setTextColor(TEXT);b.setTextSize(14);b.setAllCaps(false);b.setBackground(stroke(SURFACE2,PRIMARY,14));return b;}
    private Button smallButton(String l){Button b=new Button(this);b.setText(l);b.setTextColor(TEXT);b.setTextSize(11);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinWidth(0);b.setBackground(round(SURFACE2,10));return b;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(round(SURFACE,18));return c;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable stroke(int fill,int line,int radius){GradientDrawable g=round(fill,radius);g.setStroke(dp(1),line);return g;}
    private void addTop(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=lp(-1,-2);x.topMargin=dp(top);p.addView(v,x);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams lpWrap(){return new LinearLayout.LayoutParams(-2,-2);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void updateTimer(long ms){if(timerText==null)return;long s=ms/1000,m=s/60,r=s%60;timerText.setText(String.format(Locale.US,"%02d:%02d",m,r));timerText.setTextColor(s<=60?DANGER:(s<=180?WARNING:TEXT));}
    private void stopTimer(){if(timer!=null){timer.cancel();timer=null;}}
    private void saveHistory(String subject,String test,int score,int total){String old=prefs.getString("history","");String date=new SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.US).format(new Date());String all=subject+"|"+test+"|"+score+"|"+total+"|"+date+(old.isEmpty()?"":"\n"+old);String[] lines=all.split("\\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(12,lines.length);i++){if(i>0)b.append('\\n');b.append(lines[i]);}prefs.edit().putString("history",b.toString()).apply();}

    private static Question q(String t,String... r){String[] o=new String[4];System.arraycopy(r,1,o,0,4);return new Question(t,o,Integer.parseInt(r[0]),"");}
    private static Question qn(String t,String n,int c,String a,String b,String d,String e){return new Question(t,new String[]{a,b,d,e},c,n);}
    private static Test test(String s,String t,String d,int m,Question... q){return new Test(s,t,d,m,q);}

    private static ArrayList<Test> buildTests(){
        ArrayList<Test> l=new ArrayList<>();
        l.add(test("Physics","Rotational Dynamics • Chapter Demo","HSC-style concept check",5,
                qn("The SI unit of angular momentum is:","Use dimensions of L = r × p.",1,"N","kg m² s⁻¹","J s⁻¹","kg m s⁻¹"),
                qn("For a rigid body rotating about a fixed axis, angular speed is measured in:","",2,"m/s","rad/s²","rad/s","N m"),
                qn("The moment of inertia depends on the distribution of mass about the:","",0,"Axis of rotation","Centre of mass only","Surface area","Temperature"),
                qn("Torque is equal to the rate of change of:","",2,"Linear momentum","Kinetic energy","Angular momentum","Power"),
                qn("If angular velocity is constant, angular acceleration is:","",1,"Infinite","Zero","Maximum","Variable")));
        l.add(test("Physics","Physics • Board Pattern Mini Test","Mixed mechanics fundamentals",5,
                qn("Work done by centripetal force in uniform circular motion is:","",1,"Maximum","Zero","Negative","Variable"),
                qn("The dimensional formula of force is:","",0,"MLT⁻²","ML²T⁻²","ML⁻¹T⁻²","M⁰LT⁻¹"),
                qn("A scalar quantity has:","",2,"Direction only","No magnitude","Magnitude only","Both direction and magnitude"),
                qn("Power is defined as:","",3,"Force × time","Work × time","Energy / distance","Work / time"),
                qn("Momentum is conserved when net external:","",0,"Force is zero","Mass is zero","Velocity is zero","Energy is zero")));
        l.add(test("Chemistry","Solid State • Chapter Demo","Core definitions and properties",5,
                qn("The number of atoms present in a body-centred cubic unit cell is:","",2,"1","2","3","4"),
                qn("The coordination number of an FCC unit cell is:","",2,"4","6","8","12"),
                qn("A crystal defect caused by missing ions is called:","",1,"Interstitial defect","Vacancy defect","Frenkel defect","Schottky pair"),
                qn("The particles in an ideal crystalline solid are arranged:","",0,"Periodically","Randomly","Only in layers","Without symmetry"),
                qn("The density of a unit cell depends on its:","",3,"Colour","Smell","Melting point only","Mass, volume and number of particles")));
        l.add(test("Chemistry","Chemistry • Concepts Mini Test","Solutions and electrochemistry fundamentals",5,
                qn("Molarity is expressed as moles of solute per:","",1,"kg of solvent","litre of solution","gram of solution","litre of solvent"),
                qn("An oxidation process involves:","",0,"Loss of electrons","Gain of electrons","No electron change","Gain of protons only"),
                qn("The electrode at which reduction occurs is the:","",2,"Anode","Salt bridge","Cathode","Electrolyte"),
                qn("pH of a neutral aqueous solution at 25°C is approximately:","",3,"0","5","14","7"),
                qn("A catalyst primarily changes the:","",2,"Equilibrium constant","Heat of reaction","Activation energy","Final products")));
        l.add(test("Mathematics & Statistics","Trigonometric Functions • Mini Test","Formula and identity practice",5,
                qn("sin²θ + cos²θ is equal to:","Fundamental identity",2,"0","2","1","sin θ cos θ"),
                qn("tan 45° is:","",1,"0","1","√3","1/√3"),
                qn("The period of sin x is:","",3,"π/2","π","2π/3","2π"),
                qn("sec²θ − tan²θ is:","",0,"1","0","sec θ","tan θ"),
                qn("The value of cos 0° is:","",2,"0","−1","1","∞")));
        l.add(test("Mathematics & Statistics","Vectors • Mini Test","Core vector concepts",5,
                qn("A unit vector has magnitude:","",1,"0","1","2","−1"),
                qn("Dot product of two perpendicular vectors is:","",0,"0","1","−1","Depends on length"),
                qn("Cross product produces a:","",2,"Scalar","Number only","Vector","Matrix"),
                qn("The zero vector has magnitude:","",1,"1","0","−1","Undefined"),
                qn("a · a equals:","",3,"a","0","|a|","|a|²")));
        l.add(test("Biology","Biology • HSC Concepts Demo","Fast recall from senior-secondary biology",5,
                qn("DNA is primarily located in the cell:","For eukaryotic cells",1,"Wall","Nucleus","Vacuole","Ribosome"),
                qn("The basic structural and functional unit of life is:","",2,"Tissue","Organ","Cell","System"),
                qn("Photosynthesis primarily converts light energy into:","",0,"Chemical energy","Sound energy","Mechanical energy","Nuclear energy"),
                qn("The male gamete in flowering plants is carried by the:","",3,"Sepal","Ovary wall","Stigma","Pollen grain"),
                qn("Insulin is secreted by the:","",2,"Thyroid","Adrenal gland","Pancreas","Pituitary")));
        l.add(test("Biology","Biology • Genetics Mini Test","Inheritance and molecular biology",5,
                qn("A gene is a segment of:","",0,"DNA","Protein","Lipid","Starch"),
                qn("The genetic material in most organisms is:","",1,"RNA only","DNA","Protein","ATP"),
                qn("The observable expression of a trait is the:","",3,"Genotype","Allele","Codon","Phenotype"),
                qn("A cross involving one pair of contrasting traits is a:","",2,"Dihybrid cross","Test cross","Monohybrid cross","Back mutation"),
                qn("The sequence of three nucleotides coding for an amino acid is a:","",1,"Gene","Codon","Genome","Chromatid")));
        l.add(test("English","English • Language Skills","Grammar and comprehension style MCQs",5,
                qn("Choose the correct form: He ____ to school every day:","",2,"go","going","goes","gone"),
                qn("A word that replaces a noun is a:","",1,"Adjective","Pronoun","Adverb","Conjunction"),
                qn("The opposite of 'expand' is:","",3,"Explain","Express","Expose","Contract"),
                qn("'Quickly' is generally used as a/an:","",0,"Adverb","Noun","Pronoun","Preposition"),
                qn("A sentence that asks a question is:","",2,"Assertive","Imperative","Interrogative","Exclamatory")));
        l.add(test("English","English • Board Pattern Mini Test","Vocabulary and usage",5,
                qn("Choose the correct spelling:","",1,"Definately","Definitely","Definetely","Definatly"),
                qn("A person who writes a dictionary is a:","",0,"Lexicographer","Biographer","Cartographer","Typographer"),
                qn("'Although' is a:","",2,"Preposition","Interjection","Conjunction","Pronoun"),
                qn("Past tense of 'write' is:","",3,"writed","write","written","wrote"),
                qn("'Honesty is the best policy' is a:","",1,"Question","Proverb","Command","Greeting")));
        l.add(test("Information Technology","Emerging Technologies • Demo","IT concept check for practice",5,
                qn("AI stands for:","",0,"Artificial Intelligence","Automated Internet","Applied Information","Artificial Integration"),
                qn("IoT stands for:","",2,"Internet of Technology","Integration of Things","Internet of Things","Intelligent Online Tools"),
                qn("Cloud computing primarily provides computing resources:","",1,"Only offline","Over a network","Only on paper","Only through Bluetooth"),
                qn("A blockchain is best described as a:","",3,"Single editable file","Printer driver","Video format","Distributed digital ledger"),
                qn("AR means:","",2,"Automatic Rendering","Advanced Routing","Augmented Reality","Applied Robotics")));
        l.add(test("Information Technology","IT • Cyber Safety Mini Test","Digital safety fundamentals",5,
                qn("A strong password should generally be:","",0,"Long and unique","Your birth date","12345678","Your first name"),
                qn("Phishing is an attempt to:","",3,"Improve Wi‑Fi speed","Compress files","Repair a phone","Trick users into revealing information"),
                qn("Two-factor authentication adds:","",1,"A second verification step","A second username","A second SIM only","A second browser"),
                qn("Malware means:","",2,"Mail hardware","Manual software","Malicious software","Managed hardware"),
                qn("A backup is primarily used to:","",3,"Increase screen brightness","Change font","Overclock a CPU","Recover data after loss")));
        l.add(test("Computer Science","Java • Programming Basics","Core OOP and Java syntax",5,
                qn("Java source files normally use the extension:","",1,".class",".java",".javac",".jarx"),
                qn("Which keyword creates an object in Java?","",0,"new","make","object","create"),
                qn("A class is primarily a:","",2,"Loop","Package only","Blueprint for objects","Database"),
                qn("Which type stores true/false values?","",3,"int","char","double","boolean"),
                qn("The entry point of a standard Java application is:","",1,"start()","main()","run()","init()")));
        l.add(test("Computer Science","Computer Science • Logic Mini Test","Algorithms and programming logic",5,
                qn("A loop that repeats while a condition remains true is a:","",2,"switch","class","while loop","package"),
                qn("An array stores elements of:","",0,"A declared type","Only strings","Only numbers","No type"),
                qn("A compiler translates source code into:","",3,"Only images","A spreadsheet","A web page","Machine/byte code as required"),
                qn("In Java, String is a:","",1,"Primitive type","Class","Operator","Keyword"),
                qn("The logical AND operator in Java is:","",2,"||","!","&&","==")));
        return l;
    }

    private static class Test{String subject,title,description;int durationMinutes;List<Question> questions;Test(String s,String t,String d,int m,Question...q){subject=s;title=t;description=d;durationMinutes=m;questions=new ArrayList<>();for(Question x:q)questions.add(x);}}
    private static class Question{String text,note;String[] options;int correct;Question(String t,String[]o,int c,String n){text=t;options=o;correct=c;note=n;}}
}
