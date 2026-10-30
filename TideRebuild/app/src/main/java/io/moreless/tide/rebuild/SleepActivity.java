package io.moreless.tide.rebuild;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import io.moreless.tide.rebuild.core.SleepTime;

/** Sleep-only entry. Original artwork and wheel/card layout, no microphone or tracking. */
public final class SleepActivity extends Activity {
    private SleepStore store;
    private TextView summary,sceneButton,playButton,startButton,warning,title;
    private NumberPicker hours,minutes;
    private String lastPhase="", openedRingToken="";
    private TextView sleepClock,sleepAlarm,sleepElapsed,endControl;
    private static final int PICK_SYSTEM=71,PICK_CUSTOM=72;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new SleepStore(this);if(store.pending() || store.phase().equals("ringing"))try{SleepAlarm.restore(this,null);}catch(RuntimeException e){store.data.edit().putString("error","请重新设置闹钟："+e.getMessage()).commit();}build();}
    private String sceneName(){return store.data.getString("sceneName","海洋");}
    private String sceneFile(){return store.data.getString("sceneFile","lqVsojtUa0PGHanLKJwjWHMPyhP3.m4a");}
    private void build(){
        lastPhase=store.phase();
        if(store.pending()){buildSleeping();return;}
        sleepClock=null;
        FrameLayout root=SleepUi.backdrop(this,store.pending()?"art/sleep_dimmed.jpg":"art/sleep.jpg");
        // Measure against the current content window, including split-screen sizing.
        FrameLayout content=new FrameLayout(this);content.setPadding(SleepUi.dp(this,16),SleepUi.dp(this,72),SleepUi.dp(this,16),SleepUi.dp(this,20));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int width=Math.min(View.MeasureSpec.getSize(widthSpec),SleepUi.dp(SleepActivity.this,480));
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),heightSpec);
            }
        };
        scroll.setFillViewport(false);scroll.setBackground(SleepUi.surface(this,0xd92a3245,0x44778299));scroll.setClipToOutline(true);
        content.addView(scroll,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);col.setPadding(SleepUi.dp(this,24),SleepUi.dp(this,20),SleepUi.dp(this,24),SleepUi.dp(this,18));scroll.addView(col);
        title=SleepUi.text(this,col,"睡眠",20,Color.WHITE);
        LinearLayout wheel=new LinearLayout(this);wheel.setGravity(Gravity.CENTER);col.addView(wheel,new LinearLayout.LayoutParams(-1,SleepUi.dp(this,164)));
        hours=picker(23,store.hour());minutes=picker(59,store.minute());
        wheel.addView(hours,new LinearLayout.LayoutParams(SleepUi.dp(this,100),-1));
        TextView colon=new TextView(this);colon.setText(":");colon.setTextColor(-1);colon.setTextSize(32);wheel.addView(colon);
        wheel.addView(minutes,new LinearLayout.LayoutParams(SleepUi.dp(this,100),-1));
        NumberPicker.OnValueChangeListener changed=(p,oldValue,newValue)->{if(!store.pending()){store.data.edit().putInt("hour",hours.getValue()).putInt("minute",minutes.getValue()).commit();refresh();}};hours.setOnValueChangedListener(changed);minutes.setOnValueChangedListener(changed);
        summary=SleepUi.text(this,col,"",13,0xffc1cada);
        sceneButton=SleepUi.button(this,col,"",false,this::sounds);
        playButton=SleepUi.button(this,col,"",false,()->{if("playing".equals(AudioService.state(this).getString("status","")) && AudioService.running)startService(new Intent(this,AudioService.class).setAction(AudioService.PAUSE));else AudioService.play(this,sceneName(),sceneFile());});
        startButton=SleepUi.button(this,col,"开始睡眠",true,()->{
            if(store.phase().equals("ringing")){startActivity(new Intent(this,WakeActivity.class).putExtra("token",store.token()));return;}
            if(store.pending()){new AlertDialog.Builder(this).setMessage("结束睡眠，并取消本次闹钟？").setNegativeButton("继续睡眠",null).setPositiveButton("结束睡眠",(d,w)->{SleepAlarm.cancel(this);AudioService.stopPlayback(this);build();}).show();}
            else startSleep();
        });
        warning=SleepUi.text(this,col,"",12,0xffffd9a7);
        SleepUi.settings(this,root,this::settings);refresh();
    }
    private NumberPicker picker(int max,int value){NumberPicker p=new NumberPicker(this);p.setMinValue(0);p.setMaxValue(max);p.setValue(value);p.setWrapSelectorWheel(true);p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);p.setFormatter(v->String.format(Locale.ROOT,"%02d",v));if(Build.VERSION.SDK_INT>=29){p.setTextColor(Color.WHITE);p.setTextSize(SleepUi.dp(this,30));}p.setContentDescription(max==23?"闹钟小时":"闹钟分钟");return p;}
    private boolean permissions(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);return false;}
        if(!((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()){new AlertDialog.Builder(this).setMessage("请允许通知，以便控制声音及关闭闹钟。").setPositiveButton("去设置",(d,w)->open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))).setNegativeButton("取消",null).show();return false;}
        if(!SleepAlarm.allowed(this)){new AlertDialog.Builder(this).setMessage("请允许“闹钟和提醒”，再返回开始睡眠。").setPositiveButton("去设置",(d,w)->open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))).setNegativeButton("取消",null).show();return false;}
        return true;
    }
    private void startSleep(){
        if(!permissions())return;
        try{SleepAlarm.setClock(this,hours.getValue(),minutes.getValue());store.data.edit().putLong("sleepStarted",System.currentTimeMillis()).commit();AudioService.play(this,sceneName(),sceneFile());build();}
        catch(RuntimeException e){new AlertDialog.Builder(this).setMessage("未能开始睡眠："+e.getMessage()).setPositiveButton("知道了",null).show();}
    }
    private void refresh(){
        if(routeRinging())return;
        if(!lastPhase.equals(store.phase())){build();return;}
        if(sleepClock!=null){
            sleepClock.setText(SleepUi.clock(this));
            sleepAlarm.setText("◷  "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(store.trigger()))+(store.phase().equals("snoozed")?"  稍后提醒":"  起床"));
            long elapsed=Math.max(0,(System.currentTimeMillis()-store.data.getLong("sleepStarted",System.currentTimeMillis()))/60000);
            sleepElapsed.setText("已入睡 "+elapsed/60+" 小时 "+elapsed%60+" 分钟");
            boolean playing=AudioService.running&&"playing".equals(AudioService.state(this).getString("status",""));
            sceneButton.setText(sceneName()+"  ›");SleepUi.icon(this,playButton,playing?"pause":"play",playing?"暂停声音":"播放声音");return;
        }
        if(summary==null)return;
        boolean active=store.pending(),ringing=store.phase().equals("ringing");
        hours.setEnabled(!active&&!ringing);minutes.setEnabled(!active&&!ringing);
        title.setText(ringing?"早安":active?"睡眠中":"睡眠");
        long now=System.currentTimeMillis();long when=active?store.trigger():SleepTime.next(now,store.hour(),store.minute(),TimeZone.getDefault());
        long diff=Math.max(0,(when-now+59999)/60000);
        summary.setText(ringing?"闹钟正在响铃":(active?"闹钟已设为 "+new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(when))+"\n":"预计睡眠：")+diff/60+" 小时 "+diff%60+" 分钟");
        sceneButton.setText(sceneName()+"   ·   睡眠声音  ›");
        boolean playing=AudioService.running&&"playing".equals(AudioService.state(this).getString("status",""));SleepUi.icon(this,playButton,playing?"pause":"play",playing?"暂停声音":"播放声音");
        playButton.setEnabled(!ringing);sceneButton.setEnabled(!ringing);
        startButton.setText(ringing?"查看闹钟":active?"结束睡眠":"开始睡眠");
        String problem=store.data.getString("error","");
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(!SleepAlarm.allowed(this))problem="尚未允许精确闹钟，请到设置中开启";
        else if(audio.getStreamVolume(AudioManager.STREAM_ALARM)==0)problem="系统闹钟音量为零，请先调高";
        else if(Build.VERSION.SDK_INT>=34 && !((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).canUseFullScreenIntent())problem="锁屏全屏提醒未开启，闹钟仍通过声音和通知提醒";
        warning.setVisibility(problem.isEmpty()?View.GONE:View.VISIBLE);warning.setText(problem);lastPhase=store.phase();
    }
    private void sounds(){
        try{
            JSONArray scenes=Catalog.scenes(this);Dialog dialog=new Dialog(this);
            FrameLayout panel=new FrameLayout(this);panel.setBackground(SleepUi.glass(this));
            panel.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,android.graphics.Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),SleepUi.dp(SleepActivity.this,30));}});panel.setClipToOutline(true);
            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(SleepUi.dp(this,16),SleepUi.dp(this,12),SleepUi.dp(this,16),SleepUi.dp(this,20));panel.addView(body,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout header=new FrameLayout(this);body.addView(header,new LinearLayout.LayoutParams(-1,SleepUi.dp(this,56)));
            TextView heading=new TextView(this);heading.setText("睡眠声音");heading.setTextSize(20);heading.setTextColor(-1);heading.setGravity(Gravity.CENTER);header.addView(heading,new FrameLayout.LayoutParams(-1,-1));
            ImageButton back=new ImageButton(this);back.setImageDrawable(new SleepIcons("back",SleepUi.dp(this,24),-1));back.setContentDescription("返回");back.setBackground(SleepUi.surface(this,0x225f7898,0));header.addView(back,new FrameLayout.LayoutParams(SleepUi.dp(this,48),SleepUi.dp(this,48),Gravity.START|Gravity.CENTER_VERTICAL));back.setOnClickListener(v->dialog.dismiss());
            ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(0,SleepUi.dp(this,12),0,0);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
            for(int i=0;i<scenes.length();i++){
                JSONObject scene=scenes.getJSONObject(i);String name=scene.getJSONObject("name").getString("zh-Hans"),file=scene.getJSONObject("resource").getString("bundle");boolean selected=name.equals(sceneName());
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(SleepUi.dp(this,10),SleepUi.dp(this,8),SleepUi.dp(this,16),SleepUi.dp(this,8));
                LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,SleepUi.dp(this,80));rowSize.bottomMargin=SleepUi.dp(this,8);list.addView(row,rowSize);
                ImageView cover=new ImageView(this);String art=file.equals("alarm_1.m4a")?"art/light_original.jpg":file.equals("preset_wakeup_forest.m4a")?"art/preset_wakeup_forest.webp":"art/scene_"+scene.optString("id")+".png";
                cover.setImageBitmap(SleepUi.thumbnail(this,art,SleepUi.dp(this,60)));cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(SleepUi.surface(this,0xff28384d,0));cover.setClipToOutline(true);row.addView(cover,new LinearLayout.LayoutParams(SleepUi.dp(this,60),SleepUi.dp(this,60)));
                TextView nameView=new TextView(this);nameView.setText(name);nameView.setPadding(SleepUi.dp(this,16),0,0,0);nameView.setTextColor(-1);nameView.setTextSize(16);row.addView(nameView,new LinearLayout.LayoutParams(0,-2,1));
                TextView mark=new TextView(this);mark.setText(selected?"✓":"");mark.setTextSize(20);mark.setTextColor(0xff83e5ba);row.addView(mark);row.setContentDescription("选择"+name+(selected?"，已选择":""));row.setSelected(selected);
                row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337c9bb5),SleepUi.surface(this,selected?0x555f8093:0x22293950,selected?0x777dc3b0:0x225c7189),null));
                row.setOnClickListener(v->{store.data.edit().putString("sceneName",name).putString("sceneFile",file).commit();AudioService.play(this,name,file);dialog.dismiss();refresh();});
            }
            dialog.setContentView(panel);Window window=dialog.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.25f);
            dialog.show();int width=Math.min(getResources().getDisplayMetrics().widthPixels-SleepUi.dp(this,32),SleepUi.dp(this,460));int height=Math.min(SleepUi.dp(this,680),getWindow().getDecorView().getHeight()-SleepUi.dp(this,48));window.setLayout(width,height);
        }catch(Exception e){Toast.makeText(this,"声音加载失败："+e.getMessage(),1).show();}
    }
    private void settings(){
        String[] options={"闹钟铃声","调整系统闹钟音量","精确闹钟权限","锁屏全屏提醒","通知权限","后台省电与自启动","厂商锁屏与后台弹出权限","背景音乐音量 · "+io.moreless.tide.rebuild.core.PlaybackVolume.percent(AudioService.volumeGain(this))};
        SleepDialogs.choices(this,"设置",options,index->{
            switch(index){
                case 0:ringtoneSettings();break;
                case 1:setVolumeControlStream(AudioManager.STREAM_ALARM);((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_ALARM,AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);break;
                case 2:if(Build.VERSION.SDK_INT>=31)open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"当前系统无需单独授权",0).show();break;
                case 3:if(Build.VERSION.SDK_INT>=34)open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"当前系统由通知设置控制",0).show();break;
                case 4:open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));break;
                case 5:backgroundSettings();break;
                case 6:vendorSettings();break;
                case 7:SleepDialogs.backgroundVolume(this);break;
            }
        });
    }
    private boolean routeRinging(){
        if(!store.phase().equals("ringing")){openedRingToken="";return false;}
        if(store.data.getLong("ringUntil",0)<=System.currentTimeMillis()){SleepAlarm.timeout(this,store.token());return false;}
        if(!openedRingToken.equals(store.token())){
            openedRingToken=store.token();
            startActivity(new Intent(this,WakeActivity.class).putExtra("token",store.token()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        return true;
    }
    private void buildSleeping(){
        FrameLayout root=SleepUi.backdrop(this,"art/sleep_dimmed.jpg");
        LinearLayout col=SleepUi.column(this,root);SleepUi.space(this,col,1.2f);
        sleepClock=SleepUi.text(this,col,"",76,0xffd5d6dd);SleepUi.styleClock(this,sleepClock);
        sceneButton=SleepUi.text(this,col,sceneName()+"  ›",16,0xff9a9ba6);sceneButton.setOnClickListener(v->sounds());sceneButton.setPadding(0,SleepUi.dp(this,12),0,SleepUi.dp(this,12));
        sleepAlarm=SleepUi.text(this,col,"",14,0xffb2b3bf);
        sleepElapsed=SleepUi.text(this,col,"",13,0xff777b8c);
        SleepUi.space(this,col,1.4f);
        playButton=SleepUi.button(this,col,"Ⅱ",false,()->{
            if(AudioService.running&&"playing".equals(AudioService.state(this).getString("status","")))startService(new Intent(this,AudioService.class).setAction(AudioService.PAUSE));
            else if(!store.data.getBoolean("silentTest",false))AudioService.play(this,sceneName(),sceneFile());
        });
        LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(SleepUi.dp(this,220),SleepUi.dp(this,56));playSize.bottomMargin=SleepUi.dp(this,18);playButton.setLayoutParams(playSize);
        TextView end=SleepUi.holdEnd(this,col,()->{SleepAlarm.cancel(this);AudioService.stopPlayback(this);build();});
        endControl=end;end.setLayoutParams(new LinearLayout.LayoutParams(SleepUi.dp(this,220),SleepUi.dp(this,64)));
        SleepUi.settings(this,root,this::settings);
        SleepUi.space(this,col,.22f);refresh();
    }
    private void ringtoneSettings(){
        SleepDialogs.choices(this,"闹钟铃声",new String[]{"原版声音 · "+AlarmSound.title(this),"系统闹钟铃声","自定义音频文件"},which->{
            if(which==0){
                try{JSONArray scenes=Catalog.scenes(this);String[] labels=new String[scenes.length()];
                    for(int i=0;i<labels.length;i++){JSONObject scene=scenes.getJSONObject(i);labels[i]=scene.getJSONObject("name").getString("zh-Hans");}
                    SleepDialogs.choices(this,"原版声音",labels,index->{try{JSONObject scene=scenes.getJSONObject(index);store.write(store.data.edit().putString("ringtoneKind","asset").putString("ringtone",scene.getJSONObject("resource").getString("bundle")).putString("ringtoneName",labels[index]));}catch(Exception e){Toast.makeText(this,"无法保存铃声",1).show();}});
                }catch(Exception e){Toast.makeText(this,"无法加载原版声音",1).show();}
            }else if(which==1){
                Intent picker=new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,android.media.RingtoneManager.TYPE_ALARM).putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true).putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,Settings.System.DEFAULT_ALARM_ALERT_URI).putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(store.data.getString("ringtoneUri",Settings.System.DEFAULT_ALARM_ALERT_URI.toString())));
                try{startActivityForResult(picker,PICK_SYSTEM);}catch(ActivityNotFoundException e){Toast.makeText(this,"此设备没有铃声选择器，已选择系统默认闹钟铃声",1).show();saveSystemSound(Settings.System.DEFAULT_ALARM_ALERT_URI);}
            }else{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),PICK_CUSTOM);}catch(ActivityNotFoundException e){Toast.makeText(this,"没有可用的文件选择器",1).show();}}
        });
    }
    private void saveSystemSound(Uri uri){
        String name="系统默认闹钟铃声";
        try{android.media.Ringtone ringtone=android.media.RingtoneManager.getRingtone(this,uri);if(ringtone!=null)name=ringtone.getTitle(this);}catch(Exception ignored){}
        store.write(store.data.edit().putString("ringtoneKind","system").putString("ringtoneUri",uri.toString()).putString("ringtoneName",name));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        if(request==PICK_SYSTEM){Uri uri=data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);if(uri!=null)saveSystemSound(uri);}
        else if(request==PICK_CUSTOM && data.getData()!=null){
            Uri uri=data.getData();Toast.makeText(this,"正在导入音频…",0).show();
            new Thread(()->{try{
                String name="自定义铃声";
                try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
                String file=AlarmSound.importFile(this,uri);store.write(store.data.edit().putString("ringtoneKind","custom").putString("ringtoneFile",file).putString("ringtoneName",name));
                runOnUiThread(()->Toast.makeText(this,"已保存自定义铃声",0).show());
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"导入失败："+e.getMessage(),1).show());}},"alarm-import").start();
        }
    }
    private void vendorSettings(){
        SleepDialogs.message(this,"锁屏与后台弹出","部分设备还要求单独允许锁屏显示和后台弹出界面。请在应用的其他权限中检查这两项，同时允许锁屏通知与悬浮通知。系统全屏提醒权限不能代替厂商权限。",new String[]{"打开应用权限"},new Runnable[]{()->{
            Intent vendor=new Intent().setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity")).putExtra("extra_pkgname",getPackageName());
            try{startActivity(vendor);}catch(ActivityNotFoundException|SecurityException e){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
        }});
    }
    private void backgroundSettings(){
        boolean exempt=((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        SleepDialogs.message(this,"后台播放与闹钟","电池优化豁免："+(exempt?"已开启":"尚未开启")+"\n\n建议在系统中允许自启动、后台无限制，并在最近任务中锁定此应用。系统闹钟独立调度，无需保持屏幕常亮。\n\n强行停止应用、关机或厂商彻底限制后台仍可能使闹钟失效；重新打开后请检查闹钟状态。",new String[]{"应用后台设置","电池优化"},new Runnable[]{()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))),()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))});
    }
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException e){Toast.makeText(this,"请在系统设置中搜索此应用并调整权限",1).show();}}
    @Override protected void onResume(){super.onResume();openedRingToken="";
        if(store.phase().equals("ringing")&&!AlarmService.running)try{SleepAlarm.restore(this,null);}catch(RuntimeException e){store.data.edit().putString("error","闹钟未能恢复，请检查设置").commit();}
        handler.post(tick);
    }
    @Override protected void onPause(){handler.removeCallbacks(tick);if(endControl!=null && endControl.getTag() instanceof Runnable)((Runnable)endControl.getTag()).run();super.onPause();}
}
