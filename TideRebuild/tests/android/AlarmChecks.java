package io.moreless.tide.rebuild;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;

/** Separate instrumentation APK: never included in the user application. All playback is muted. */
public final class AlarmChecks extends Instrumentation {
    private String command;
    @Override public void onCreate(Bundle args){super.onCreate(args);command=args.getString("command","assets");start();}
    private void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private int prepare(Context c) throws Exception {
        MediaPlayer p=new MediaPlayer();try{p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());p.setVolume(0,0);AlarmSound.source(c,p);p.prepare();require(p.getDuration()>0,"empty or undecodable sound");return p.getDuration();}finally{p.release();}
    }
    private void restoreStrings(android.content.SharedPreferences prefs,java.util.Map<String,?> saved,String... keys){
        android.content.SharedPreferences.Editor edit=prefs.edit();
        for(String key:keys){if(saved.containsKey(key))edit.putString(key,(String)saved.get(key));else edit.remove(key);}edit.commit();
    }
    private android.view.View findText(android.view.View view,String label){
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()) || (view instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView)view).getText())))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=findText(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    @Override public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SleepStore store=new SleepStore(c);
        try{
            if(command.equals("lock-check")){
                require(!store.pending()&&!store.phase().equals("ringing"),"existing alarm must not be replaced");
                NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);KeyguardManager keyguard=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
                result.putBoolean("notifications_enabled",nm.areNotificationsEnabled());result.putBoolean("exact_alarm_allowed",SleepAlarm.allowed(c));
                if(Build.VERSION.SDK_INT>=34)result.putBoolean("fullscreen_allowed",nm.canUseFullScreenIntent());
                AlarmService.channel(c);AlarmService.silentChannelSafe(c);
                for(String channel:new String[]{"wake_alarm","wake_silent_test"}){NotificationChannel info=nm.getNotificationChannel(channel);result.putInt(channel+"_importance",info.getImportance());result.putString(channel+"_sound",String.valueOf(info.getSound()));}
                ActivityMonitor monitor=addMonitor(WakeActivity.class.getName(),null,false);
                try{
                    runOnMainSync(()->{SleepAlarm.test(c);AudioService.stopPlayback(c);});long trigger=store.trigger();result.putLong("trigger",trigger);
                    try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("input keyevent 223")){try(FileInputStream input=new FileInputStream(fd.getFileDescriptor())){while(input.read()!=-1){}}}
                    SystemClock.sleep(2500);result.putBoolean("interactive_before",power.isInteractive());result.putBoolean("keyguard_before",keyguard.isKeyguardLocked());require(!power.isInteractive(),"device did not sleep");
                    while(System.currentTimeMillis()<trigger+7000)SystemClock.sleep(100);
                    result.putString("alarm_phase",store.phase());result.putString("playback",store.data.getString("ringPlayback",""));result.putInt("wake_activity_hits",monitor.getHits());result.putBoolean("interactive_after",power.isInteractive());result.putBoolean("keyguard_after",keyguard.isKeyguardLocked());
                    android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();File png=new File(c.getExternalFilesDir(null),"lock-check.png");try(FileOutputStream out=new FileOutputStream(png)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();result.putString("screenshot",png.getAbsolutePath());
                    require(store.phase().equals("ringing"),"alarm did not ring");
                    android.view.accessibility.AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();
                    boolean visible=active!=null && c.getPackageName().contentEquals(active.getPackageName()==null?"":active.getPackageName()) && !active.findAccessibilityNodeInfosByText("正在响铃").isEmpty() && !active.findAccessibilityNodeInfosByText("起床").isEmpty();
                    result.putBoolean("ringing_window_visible",visible);require(visible&&power.isInteractive()&&keyguard.isKeyguardLocked(),"ringing UI is not visible over locked screen");
                }finally{removeMonitor(monitor);runOnMainSync(()->{SleepAlarm.cancel(c);AudioService.stopPlayback(c);});}
            }else if(command.equals("buttons")){
                try{
                    for(String label:new String[]{"再睡30分钟","起床"}){
                        runOnMainSync(()->{SleepAlarm.test(c);AudioService.stopPlayback(c);store.data.edit().putLong("trigger",System.currentTimeMillis()).commit();new AlarmReceiver().onReceive(c,new Intent(SleepAlarm.FIRE).putExtra("token",store.token()));});
                        SystemClock.sleep(500);
                        Activity activity=startActivitySync(new Intent(c,WakeActivity.class).putExtra("token",store.token()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                        android.view.View button=findText(activity.findViewById(android.R.id.content),label);require(button!=null,"missing button "+label);
                        int[] point=new int[2];runOnMainSync(()->button.getLocationOnScreen(point));float x=point[0]+button.getWidth()/2f,y=point[1]+button.getHeight()/2f;
                        long now=System.currentTimeMillis(),down=SystemClock.uptimeMillis();sendPointerSync(android.view.MotionEvent.obtain(down,down,0,x,y,0));sendPointerSync(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0));waitForIdleSync();
                        if(label.equals("再睡30分钟")){require(store.phase().equals("snoozed"),"UI snooze failed");require(store.trigger()-now>=1800000 && store.trigger()-now<1802000,"UI snooze wrong delay");}
                        else require(store.phase().equals("off")&&store.trigger()==0,"UI dismiss failed");
                    }
                    result.putString("buttons","real touch: snooze scheduled 30min; wake canceled alarm");
                }finally{runOnMainSync(()->{SleepAlarm.cancel(c);AudioService.stopPlayback(c);});}
            }else if(command.equals("icon")){
                android.content.pm.PackageManager pm=c.getPackageManager();android.content.pm.ApplicationInfo app=pm.getApplicationInfo(c.getPackageName(),0);
                android.content.pm.ActivityInfo entry=pm.getActivityInfo(new ComponentName(c,SleepActivity.class),0);
                require(app.icon!=0,"no application icon");require("潮汐Mini".contentEquals(pm.getApplicationLabel(app)),"wrong label");
                result.putInt("application_icon_resource",app.icon);result.putInt("activity_icon_resource",entry.getIconResource());
                android.graphics.drawable.Drawable icon=pm.getActivityIcon(new ComponentName(c,SleepActivity.class));
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);icon.setBounds(0,0,256,256);icon.draw(canvas);
                File png=new File(c.getExternalFilesDir(null),"resolved-launcher-icon.png");try(FileOutputStream out=new FileOutputStream(png)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();result.putString("screenshot",png.getAbsolutePath());
            }else if(command.equals("cycle")){
                try{
                    runOnMainSync(()->{
                        SleepAlarm.test(c);AudioService.stopPlayback(c);
                        store.data.edit().putLong("trigger",System.currentTimeMillis()).commit();
                        new AlarmReceiver().onReceive(c,new Intent(SleepAlarm.FIRE).putExtra("token",store.token()));
                    });
                    long began=System.currentTimeMillis(),until=store.data.getLong("ringUntil",0);
                    require(until-began>58000 && until-began<=60000,"first ring not one minute");
                    String first=store.token();
                    long deadline=SystemClock.elapsedRealtime()+70000;
                    while(store.phase().equals("ringing") && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
                    require(store.phase().equals("retry"),"real one-minute timeout did not enter retry");
                    require(store.data.getInt("round",0)==2,"wrong next round");
                    require(store.trigger()==until+240000,"retry not four minutes from end");
                    result.putLong("real_first_round_elapsed_ms",System.currentTimeMillis()-began);
                    runOnMainSync(()->SleepAlarm.dismiss(c,first));require(store.phase().equals("retry"),"stale dismiss canceled new round");
                    for(int round=2;round<=3;round++){
                        final int expected=round;
                        runOnMainSync(()->{
                            store.data.edit().putLong("trigger",System.currentTimeMillis()).commit();
                            new AlarmReceiver().onReceive(c,new Intent(SleepAlarm.FIRE).putExtra("token",store.token()));
                        });
                        require(store.phase().equals("ringing")&&store.data.getInt("round",0)==expected,"round not delivered");
                        SystemClock.sleep(300);
                        long end=store.data.getLong("ringUntil",0);require(end-System.currentTimeMillis()>58000,"round duration incorrect");
                        runOnMainSync(()->{store.data.edit().putLong("ringUntil",System.currentTimeMillis()).commit();SleepAlarm.timeout(c,store.token());});
                        require(store.phase().equals(round==3?"off":"retry"),"wrong timeout transition");
                        if(round==2)require(store.trigger()==store.data.getLong("ringUntil",0)+240000 || Math.abs(store.trigger()-System.currentTimeMillis()-240000)<1000,"second retry delay wrong");
                    }
                    require(store.trigger()==0 && store.token().isEmpty(),"third round left an alarm");
                    runOnMainSync(()->{SleepAlarm.test(c);store.data.edit().putLong("trigger",System.currentTimeMillis()).commit();new AlarmReceiver().onReceive(c,new Intent(SleepAlarm.FIRE).putExtra("token",store.token()));});
                    SystemClock.sleep(300);String before=store.token();long now=System.currentTimeMillis();
                    runOnMainSync(()->SleepAlarm.snooze(c,before));
                    require(store.phase().equals("snoozed")&&store.trigger()-now>=1800000&&store.trigger()-now<1801000,"30-minute snooze not scheduled");
                    require(store.data.getInt("round",0)==1,"snooze did not reset rounds");
                    runOnMainSync(()->SleepAlarm.timeout(c,before));require(store.phase().equals("snoozed"),"old timeout affected snooze");
                    AlarmManager.AlarmClockInfo next=SleepAlarm.manager(c).getNextAlarmClock();require(next!=null&&next.getTriggerTime()==store.trigger(),"system alarm missing for snooze");
                    result.putString("cycle","real first 60s; accelerated round 2/3; 4-minute gaps, final stop, stale token, 30-minute system schedule passed");
                }finally{runOnMainSync(()->{SleepAlarm.cancel(c);AudioService.stopPlayback(c);});}
            }else if(command.equals("hold")){
                try{
                    runOnMainSync(()->{SleepAlarm.test(c);AudioService.stopPlayback(c);});
                    Activity activity=startActivitySync(new Intent(c,SleepActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                    android.view.View button=findText(activity.findViewById(android.R.id.content),"长按结束");require(button!=null,"hold button missing");
                    int[] point=new int[2];runOnMainSync(()->button.getLocationOnScreen(point));float x=point[0]+button.getWidth()/2f,y=point[1]+button.getHeight()/2f;
                    long down=SystemClock.uptimeMillis();sendPointerSync(android.view.MotionEvent.obtain(down,down,0,x,y,0));SystemClock.sleep(100);sendPointerSync(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0));SystemClock.sleep(100);require(store.pending(),"short tap canceled alarm");SystemClock.sleep(2500);
                    down=SystemClock.uptimeMillis();sendPointerSync(android.view.MotionEvent.obtain(down,down,0,x,y,0));SystemClock.sleep(750);
                    android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();File png=new File(c.getExternalFilesDir(null),"hold-progress.png");try(FileOutputStream out=new FileOutputStream(png)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();result.putString("screenshot",png.getAbsolutePath());
                    SystemClock.sleep(900);sendPointerSync(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0));waitForIdleSync();require(store.phase().equals("off"),"full hold did not cancel");require(!AudioService.state(c).getBoolean("wanted",false),"audio wanted after end");
                    result.putString("hold","short tap preserved alarm; full hold stopped alarm and audio");
                }finally{runOnMainSync(()->{SleepAlarm.cancel(c);AudioService.stopPlayback(c);});}
            }else if(command.equals("full-only")){
                JSONArray scenes=Catalog.scenes(c);require(scenes.length()==8,"expected 8 full sounds");
                for(int i=0;i<scenes.length();i++)require(!scenes.getJSONObject(i).optBoolean("rebuild_demo"),"preview in catalogue");
                for(String name:c.getAssets().list("sounds"))require(!name.startsWith("original_demo_"),"preview packed in APK");
                android.content.SharedPreferences audio=AudioService.state(c);
                java.util.Map<String,?> saved=store.data.getAll(),savedAudio=audio.getAll();String phase=store.phase(),token=store.token();boolean wanted=audio.getBoolean("wanted",false);
                try{
                    String drizzle="original_demo_FkYQAAnlUP9GK4glJFppQX0joAGv.mp3";
                    store.data.edit().putString("sceneFile",drizzle).putString("sceneName","绵雨").putString("ringtoneKind","asset").putString("ringtone",drizzle).putString("ringtoneName","绵雨 · 原版试听").commit();
                    audio.edit().putString("file",drizzle).putString("title","绵雨").commit();new SleepStore(c);
                    String full="lnSNpbE0TI_HLFPpdeSkoeIvGjvh.m4a";
                    require(full.equals(store.data.getString("sceneFile",""))&&full.equals(store.data.getString("ringtone",""))&&full.equals(audio.getString("file","")),"old Drizzle not migrated");
                    require("绵雨".equals(store.data.getString("ringtoneName","")),"stale preview label");
                    store.data.edit().putString("sceneFile","original_demo_removed.mp3").putString("ringtone","original_demo_removed.mp3").commit();
                    audio.edit().putString("file","original_demo_removed.mp3").commit();new SleepStore(c);
                    require("lqVsojtUa0PGHanLKJwjWHMPyhP3.m4a".equals(store.data.getString("sceneFile","")),"background fallback missing");
                    require("alarm_1.m4a".equals(store.data.getString("ringtone","")),"alarm fallback missing");
                    for(String kind:new String[]{"system","custom"}){
                        store.data.edit().putString("ringtoneKind",kind).putString("ringtone","original_demo_unused.mp3").putString("ringtoneName","用户选择").commit();new SleepStore(c);
                        require(kind.equals(store.data.getString("ringtoneKind",""))&&"用户选择".equals(store.data.getString("ringtoneName","")),"user sound was replaced");
                    }
                    require(phase.equals(store.phase())&&token.equals(store.token()),"alarm schedule state changed");require(wanted==audio.getBoolean("wanted",false),"playback intention changed");
                }finally{
                    restoreStrings(store.data,saved,"sceneFile","sceneName","ringtoneKind","ringtone","ringtoneName");
                    restoreStrings(audio,savedAudio,"file","title");
                }
                result.putString("catalogue_and_migration","8 full sounds; no packaged previews; old selection migration and custom/system preservation passed");
            }else if(command.equals("arm")){
                runOnMainSync(()->{SleepAlarm.test(c);AudioService.stopPlayback(c);store.data.edit().putLong("sleepStarted",System.currentTimeMillis()).commit();});
                require(store.pending()&&store.data.getBoolean("silentTest",false),"silent schedule failed");
                result.putString("token",store.token());result.putLong("trigger",store.trigger());
            }else if(command.equals("cancel")){
                runOnMainSync(()->{SleepAlarm.cancel(c);AudioService.stopPlayback(c);});require(store.phase().equals("off"),"cancel failed");
            }else if(command.equals("fallback")){
                String kind=store.data.getString("ringtoneKind","asset"),file=store.data.getString("ringtoneFile","");
                try{
                    File broken=new File(c.createDeviceProtectedStorageContext().getFilesDir(),"alarm-00000000-0000-0000-0000-000000000000.audio");
                    try(FileOutputStream out=new FileOutputStream(broken)){out.write(new byte[1024]);}
                    runOnMainSync(()->{
                        SleepAlarm.test(c);AudioService.stopPlayback(c);
                        store.data.edit().putString("ringtoneKind","custom").putString("ringtoneFile",broken.getName()).putLong("trigger",System.currentTimeMillis()).commit();
                        new AlarmReceiver().onReceive(c,new Intent(SleepAlarm.FIRE).putExtra("token",store.token()));
                    });
                    long end=SystemClock.elapsedRealtime()+8000;
                    while(!"playing".equals(store.data.getString("ringPlayback",""))&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
                    require("playing".equals(store.data.getString("ringPlayback","")),"fallback did not play");
                    require("system-default".equals(store.data.getString("ringSource","")),"corrupt custom did not fall back");
                    require(store.data.getBoolean("silentTest",false),"fallback test not muted");
                }finally{runOnMainSync(()->SleepAlarm.cancel(c));store.data.edit().putString("ringtoneKind",kind).putString("ringtoneFile",file).commit();}
            }else if(command.equals("notification")){
                require(store.phase().equals("ringing"),"need ringing session");
                // Instrumentation restarts the process; explicitly restart the same silent ringing service.
                runOnMainSync(()->c.startForegroundService(new Intent(c,AlarmService.class).putExtra("token",store.token())));
                Notification notification=null;long until=SystemClock.elapsedRealtime()+5000;
                while(SystemClock.elapsedRealtime()<until){
                    for(android.service.notification.StatusBarNotification n:((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications())if(n.getId()==200)notification=n.getNotification();
                    if(notification!=null)break;SystemClock.sleep(100);
                }
                require(notification!=null,"alarm notification absent");require(notification.fullScreenIntent!=null,"no fullscreen intent");
                require(notification.visibility==Notification.VISIBILITY_PUBLIC,"private notification");
                require("起床时间到，正在响铃…".equals(notification.extras.getString(Notification.EXTRA_TITLE)),"wrong title");
                require(notification.actions.length==2,"missing actions");
                notification.actions[0].actionIntent.send();
                until=SystemClock.elapsedRealtime()+3000;while(!store.phase().equals("snoozed") && SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);
                require(store.phase().equals("snoozed"),"notification snooze did not schedule");
                require(store.trigger()>System.currentTimeMillis()+1795000 && store.trigger()<=System.currentTimeMillis()+1800000,"wrong snooze time");
                require(store.data.getBoolean("silentTest",false),"snooze lost mute flag");
            }else if(command.equals("route")){
                require(store.phase().equals("ringing"),"schedule silent alarm and wait first");
                ActivityMonitor wake=addMonitor(WakeActivity.class.getName(),null,false);
                startActivitySync(new Intent(c,SleepActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                require(waitForMonitorWithTimeout(wake,6000)!=null,"launcher did not route to ringing activity");removeMonitor(wake);
            }else{
                String kind=store.data.getString("ringtoneKind","asset"),name=store.data.getString("ringtoneName","光"),asset=store.data.getString("ringtone","alarm_1.m4a"),uri=store.data.getString("ringtoneUri",android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString()),file=store.data.getString("ringtoneFile","");
                try{
                    JSONArray scenes=Catalog.scenes(c);boolean drizzle=false;
                    for(int i=0;i<scenes.length();i++){
                        JSONObject scene=scenes.getJSONObject(i);String sound=scene.getJSONObject("resource").getString("bundle");
                        if(scene.getJSONObject("name").getString("zh-Hans").equals("绵雨"))drizzle=true;
                        store.data.edit().putString("ringtoneKind","asset").putString("ringtone",sound).commit();int duration=prepare(c);
                        if(scene.getJSONObject("name").getString("zh-Hans").equals("绵雨")){require(duration>306000 && duration<308000,"Drizzle is not the full original");result.putInt("drizzle_duration_ms",duration);}
                    }
                    require(drizzle,"original Drizzle missing");result.putInt("decodable_sounds",scenes.length());
                    store.data.edit().putString("ringtoneKind","system").putString("ringtoneUri",android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString()).commit();prepare(c);
                    File input=new File(c.getCacheDir(),"import-test.m4a");
                    try(InputStream in=c.getAssets().open("sounds/alarm_1.m4a");FileOutputStream out=new FileOutputStream(input)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
                    String imported=AlarmSound.importFile(c,Uri.fromFile(input));
                    store.data.edit().putString("ringtoneKind","custom").putString("ringtoneFile",imported).commit();prepare(c);
                    require(new File(c.createDeviceProtectedStorageContext().getFilesDir(),imported).isFile(),"import not device protected");
                    result.putString("custom_and_system","prepared successfully without starting playback");
                    File oversized=new File(c.getCacheDir(),"import-oversized.audio");
                    try(java.io.RandomAccessFile large=new java.io.RandomAccessFile(oversized,"rw")){large.setLength(51L*1024*1024);}
                    File directory=c.createDeviceProtectedStorageContext().getFilesDir();int before=directory.list().length;boolean rejected=false;
                    try{AlarmSound.importFile(c,Uri.fromFile(oversized));}catch(IOException expected){rejected=true;}
                    require(rejected,"oversized import accepted");require(directory.list().length==before,"failed import leaked candidate");
                    File invalid=new File(c.getCacheDir(),"import-invalid.audio");try(FileOutputStream bad=new FileOutputStream(invalid)){bad.write(new byte[1024]);}
                    rejected=false;try{AlarmSound.importFile(c,Uri.fromFile(invalid));}catch(Exception expected){rejected=true;}
                    require(rejected,"invalid import accepted");require(directory.list().length==before,"invalid import leaked candidate");
                    result.putString("invalid_and_oversized","rejected without changing selected sound or retaining candidates");
                }finally{store.data.edit().putString("ringtoneKind",kind).putString("ringtoneName",name).putString("ringtone",asset).putString("ringtoneUri",uri).putString("ringtoneFile",file).commit();}
            }
            result.putString("result","PASS "+command);finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("failure",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
