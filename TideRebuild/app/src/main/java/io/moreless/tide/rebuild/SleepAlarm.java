package io.moreless.tide.rebuild;
import android.app.*;
import android.content.*;
import android.os.Build;
import io.moreless.tide.rebuild.core.SleepTime;
import io.moreless.tide.rebuild.core.AlarmCycle;
import java.util.*;

/** One outstanding AlarmClockInfo; token protects replacements from stale broadcasts. */
public final class SleepAlarm {
    public static final String TIMEOUT="io.moreless.tide.rebuild.TIMEOUT";
    public static final String FIRE="io.moreless.tide.rebuild.ALARM", DISMISS="io.moreless.tide.rebuild.DISMISS", SNOOZE="io.moreless.tide.rebuild.SNOOZE";
    public static AlarmManager manager(Context c){return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);}
    public static boolean allowed(Context c){return Build.VERSION.SDK_INT<31 || manager(c).canScheduleExactAlarms();}
    private static PendingIntent event(Context c,String token){return PendingIntent.getBroadcast(c,701,new Intent(c,AlarmReceiver.class).setAction(FIRE).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static PendingIntent home(Context c){return PendingIntent.getActivity(c,702,new Intent(c,SleepActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static PendingIntent command(Context c,String action,String token){return PendingIntent.getBroadcast(c,action.hashCode(),new Intent(c,AlarmReceiver.class).setAction(action).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void setClock(Context c,int hour,int minute){
        long when=SleepTime.next(System.currentTimeMillis(),hour,minute,TimeZone.getDefault());
        schedule(c,when,"armed",hour,minute,false);
    }
    public static void test(Context c){if(!AlarmService.silentChannelSafe(c))throw new IllegalStateException("请先在系统通知设置中关闭“静音闹钟测试”频道的声音与震动");SleepStore s=new SleepStore(c);schedule(c,System.currentTimeMillis()+30000,"armed",s.hour(),s.minute(),true);}
    private static void schedule(Context c,long when,String phase,int hour,int minute,boolean silentTest){
        schedule(c,when,phase,hour,minute,silentTest,1);
    }
    private static void schedule(Context c,long when,String phase,int hour,int minute,boolean silentTest,int round){
        if(!allowed(c))throw new IllegalStateException("请先允许“闹钟和提醒”权限");
        SleepStore store=new SleepStore(c);String token=UUID.randomUUID().toString();
        Map<String,?> previous=store.data.getAll();
        store.write(store.data.edit().putString("token",token).putString("phase",phase).putLong("trigger",when).putLong("ringUntil",0).putInt("round",round).putInt("hour",hour).putInt("minute",minute).putBoolean("silentTest",silentTest).remove("playingRingtone").remove("ringPlayback").remove("ringSource").remove("error"));
        try{manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(when,home(c)),event(c,token));manager(c).cancel(command(c,TIMEOUT,token));c.stopService(new Intent(c,AlarmService.class));}
        catch(RuntimeException e){
            // Do not show success if scheduling fails; retain the old persisted record for recovery.
            android.content.SharedPreferences.Editor back=store.data.edit().clear();
            for(Map.Entry<String,?> item:previous.entrySet()){Object v=item.getValue();if(v instanceof String)back.putString(item.getKey(),(String)v);else if(v instanceof Long)back.putLong(item.getKey(),(Long)v);else if(v instanceof Integer)back.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)back.putBoolean(item.getKey(),(Boolean)v);}
            back.commit();
            if(store.pending())try{manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(store.trigger(),home(c)),event(c,store.token()));}catch(RuntimeException restoreError){store.data.edit().putString("error","闹钟未能恢复，请重新设置").commit();}
            throw e;
        }
    }
    public static void cancel(Context c){
        SleepStore s=new SleepStore(c);s.write(s.data.edit().putString("phase","off").putString("token","").putLong("trigger",0).putLong("ringUntil",0).putBoolean("silentTest",false).remove("error"));
        manager(c).cancel(event(c,""));manager(c).cancel(command(c,TIMEOUT,""));c.stopService(new Intent(c,AlarmService.class));
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(201);
    }
    public static void dismiss(Context c,String token){SleepStore s=new SleepStore(c);if(!s.token().equals(token))return;cancel(c);AudioService.stopPlayback(c);}
    public static void snooze(Context c,String token){
        SleepStore s=new SleepStore(c);if(!s.token().equals(token) || !s.phase().equals("ringing"))return;
        schedule(c,AlarmCycle.snoozeAt(System.currentTimeMillis()),"snoozed",s.hour(),s.minute(),s.data.getBoolean("silentTest",false));
        c.stopService(new Intent(c,AlarmService.class));
    }
    public static void restore(Context c,String action){
        SleepStore s=new SleepStore(c);
        if(s.pending()){
            if(!allowed(c)){s.data.edit().putString("error","精确闹钟权限已关闭，请重新允许").commit();return;}
            long when=s.trigger();
            if(Intent.ACTION_TIMEZONE_CHANGED.equals(action) && s.phase().equals("armed"))when=SleepTime.next(System.currentTimeMillis(),s.hour(),s.minute(),TimeZone.getDefault());
            long now=System.currentTimeMillis();
            if(when<now-900000){s.data.edit().putString("phase","missed").putString("error","设备未能按时唤醒，闹钟已错过，请重新设置").commit();return;}
            when=Math.max(when,now+2000);s.write(s.data.edit().putLong("trigger",when));
            manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(when,home(c)),event(c,s.token()));
        } else if(s.phase().equals("ringing")){
            long now=System.currentTimeMillis();
            if(s.data.getLong("ringUntil",0)<=now){timeout(c,s.token());return;}
            if(AlarmService.running)return;
            if(allowed(c)){
                // Re-deliver the same round, preserving its original end time.
                s.write(s.data.edit().putString("phase","resuming").putLong("trigger",now+100));
                manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(now+100,home(c)),event(c,s.token()));
            }
        }
    }
    public static void armTimeout(Context c,String token,long until){
        // Alarm-clock delivery avoids idle-mode frequency limits between rounds.
        manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(until,home(c)),command(c,TIMEOUT,token));
    }
    public static void timeout(Context c,String token){
        SleepStore s=new SleepStore(c);long until=s.data.getLong("ringUntil",0),now=System.currentTimeMillis();
        if(!s.token().equals(token) || !(s.phase().equals("ringing") || s.phase().equals("resuming")) || now<until)return;
        long next=AlarmCycle.retryAt(s.data.getInt("round",1),until);
        if(next==0 || now-next>900000){cancel(c);return;}
        try{schedule(c,Math.max(next,now+1000),"retry",s.hour(),s.minute(),s.data.getBoolean("silentTest",false),s.data.getInt("round",1)+1);}
        catch(RuntimeException e){cancel(c);s.data.edit().putString("error","下一轮闹钟未能调度，请检查精确闹钟权限").commit();AlarmService.failureNotification(c);}
    }
}
