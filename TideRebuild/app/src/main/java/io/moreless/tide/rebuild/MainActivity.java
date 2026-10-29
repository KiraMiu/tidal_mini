package io.moreless.tide.rebuild;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import io.moreless.tide.rebuild.core.*;

/** Phase-one offline client. Cloud/sleep tracking are explicitly outside this release. */
public final class MainActivity extends Activity {
    private static final int INK=0xffe9ede7, MUTED=0xffa5b7ae, BG=0xff152721;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView countdown, instruction;
    private Button toggle;
    private SessionTimer timer;
    private BreathTimeline breathing;
    private String tab="声音", sessionName="专注";
    private boolean completionSaved;
    private SharedPreferences preferences;
    private final Runnable ticker=new Runnable(){public void run(){refresh();handler.postDelayed(this,100);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=getSharedPreferences("offline",MODE_PRIVATE);
        if(state!=null)tab=state.getString("tab","声音");
        show();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(8),0,dp(8));content.addView(v);return v;}
    private Button button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextColor(INK);b.setAllCaps(false);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff294239);bg.setCornerRadius(dp(14));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(6),0,dp(6));content.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void show(){
        countdown=null;instruction=null;toggle=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(36));scroll.addView(content);setContentView(scroll);
        text("T I D E   /   O F F L I N E",12,MUTED);
        TextView heading=text(tab,32,INK);heading.setTypeface(null,Typeface.BOLD);
        LinearLayout navigation=new LinearLayout(this);content.addView(navigation);
        for(String name:new String[]{"声音","专注","呼吸","记录"}){
            Button b=new Button(this);b.setText(name);b.setAllCaps(false);navigation.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));
            b.setOnClickListener(v->{tab=name;show();});
        }
        try{
            switch(tab){case "专注":focus();break;case "呼吸":breath();break;case "记录":history();break;default:sounds();}
        }catch(Exception e){text("内容加载失败："+e.getMessage(),16,INK);}
        text("离线重构 0.1 · 仅包含已恢复的部分功能",12,MUTED);
    }
    private void sounds() throws Exception {
        text("来自原 APK 的本地声音，可锁屏播放。",15,MUTED);
        JSONArray scenes=Catalog.scenes(this);
        for(int i=0;i<scenes.length();i++){
            JSONObject scene=scenes.getJSONObject(i);
            String name=scene.getJSONObject("name").getString("zh-Hans");
            String file=scene.getJSONObject("resource").getString("bundle");
            button(name,()->play(name,file));
        }
        button("暂停声音",()->command(AudioService.PAUSE));
        button("继续声音",()->command(AudioService.RESUME));
        button("停止声音",()->stopService(new Intent(this,AudioService.class)));
        text("海边篝火是混音场景，本阶段尚未恢复。云端声音不在本地列表中。",13,MUTED);
    }
    private void play(String name,String file){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        Intent intent=new Intent(this,AudioService.class).setAction(AudioService.PLAY).putExtra("file",file).putExtra("title",name);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
    }
    private void command(String action){startService(new Intent(this,AudioService.class).setAction(action));}
    private void focus(){
        text("选择时长，开始一段专注。计时支持暂停和继续，结束后保存本地记录。",15,MUTED);
        for(int minutes:new int[]{5,15,25,45})button(minutes+" 分钟",()->begin("专注",minutes*60000L,null));
        text("这些时长是重构版的选项，尚未证明与原版默认值一致。",12,MUTED);
        session();
    }
    private void breath() throws Exception {
        text("按原 APK 当前配置执行动作和默认频率，每次约 1 分钟，结束时完成整个呼吸周期。",15,MUTED);
        JSONArray modes=Catalog.breathModes(this);
        for(int i=0;i<modes.length();i++){
            JSONObject mode=modes.getJSONObject(i);String title=mode.getJSONObject("title").getString("zh-Hans");
            button(title,()->{try{BreathTimeline timeline=Catalog.breath(mode,1);begin(title,timeline.duration(),timeline);}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}});
        }
        session();
    }
    private void begin(String name,long duration,BreathTimeline timeline){
        if(timer!=null && !timer.isFinished(now())) {
            new AlertDialog.Builder(this).setMessage("结束当前计时，开始新的练习？").setNegativeButton("保留",null).setPositiveButton("开始新的练习",(d,w)->{record(false);start(name,duration,timeline);}).show();
        }else start(name,duration,timeline);
    }
    private void start(String name,long duration,BreathTimeline timeline){
        sessionName=name;breathing=timeline;timer=new SessionTimer(duration);completionSaved=false;timer.start(now());show();
    }
    private void session(){
        if(timer==null){text("尚未开始",20,MUTED);return;}
        text(sessionName,20,INK);countdown=text("",48,INK);countdown.setTypeface(Typeface.MONOSPACE);
        instruction=text("",24,INK);
        toggle=button("暂停",()->{if(timer.isRunning(now()))timer.pause(now());else timer.resume(now());refresh();});
        button("结束并记录",()->{record(timer.isFinished(now()));timer=null;breathing=null;show();});
        text("本阶段切到后台会自动暂停计时；声音播放继续。再次回到应用需手动继续。",13,MUTED);
        refresh();
    }
    private long now(){return SystemClock.elapsedRealtime();}
    private String clock(long millis){long seconds=(millis+999)/1000;return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private void refresh(){
        if(timer==null)return;
        long remaining=timer.remaining(now());
        if(countdown!=null)countdown.setText(clock(remaining));
        if(toggle!=null){toggle.setEnabled(remaining>0);toggle.setText(timer.isRunning(now())?"暂停":"继续");}
        if(instruction!=null){
            String action=remaining==0?"已完成":timer.isRunning(now())?"保持专注":"已暂停";
            if(breathing!=null && timer.isRunning(now())){
                String key=breathing.actionAt(timer.duration-remaining);
                switch(key){case "inhale":action="吸气";break;case "exhale":action="呼气";break;case "hold":action="屏气";break;default:action="过渡";}
            }
            instruction.setText(action);
        }
        if(remaining==0 && !completionSaved){record(true);Toast.makeText(this,"本次练习已完成",Toast.LENGTH_SHORT).show();}
    }
    private void record(boolean completed){
        if(timer==null || completionSaved)return;
        try{
            JSONArray list=new JSONArray(preferences.getString("records","[]"));
            JSONObject item=new JSONObject().put("name",sessionName).put("date",System.currentTimeMillis()).put("elapsed",timer.duration-timer.remaining(now())).put("completed",completed);
            list.put(item);preferences.edit().putString("records",list.toString()).apply();completionSaved=true;
        }catch(JSONException e){Toast.makeText(this,"记录保存失败",1).show();}
    }
    private void history() throws Exception {
        JSONArray list=new JSONArray(preferences.getString("records","[]"));
        text("仅显示此离线工程产生的记录，不读取原应用数据。",14,MUTED);
        if(list.length()==0)text("还没有练习记录",20,INK);
        SimpleDateFormat date=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);
        for(int i=list.length()-1;i>=0;i--){JSONObject item=list.getJSONObject(i);text(item.getString("name")+" · "+clock(item.getLong("elapsed"))+" · "+(item.getBoolean("completed")?"完成":"提前结束")+"\n"+date.format(new Date(item.getLong("date"))),16,INK);}
    }
    @Override protected void onStart(){super.onStart();handler.post(ticker);}
    @Override protected void onStop(){handler.removeCallbacks(ticker);if(timer!=null){timer.pause(now());}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);out.putString("tab",tab);
        if(timer!=null){out.putLong("duration",timer.duration);out.putLong("remaining",timer.remaining(now()));out.putString("name",sessionName);out.putBoolean("saved",completionSaved);out.putBoolean("breathing",breathing!=null);}
    }
    @Override protected void onRestoreInstanceState(Bundle state){
        super.onRestoreInstanceState(state);
        if(state.containsKey("duration")){
            timer=SessionTimer.restore(state.getLong("duration"),state.getLong("remaining"),false,now());sessionName=state.getString("name","专注");completionSaved=state.getBoolean("saved");
            if(state.getBoolean("breathing"))try{JSONArray modes=Catalog.breathModes(this);for(int i=0;i<modes.length();i++){JSONObject mode=modes.getJSONObject(i);if(mode.getJSONObject("title").getString("zh-Hans").equals(sessionName))breathing=Catalog.breath(mode,1);}}catch(Exception e){timer=null;Toast.makeText(this,"呼吸练习无法恢复，请重新开始",1).show();}
            show();
        }
    }
}
