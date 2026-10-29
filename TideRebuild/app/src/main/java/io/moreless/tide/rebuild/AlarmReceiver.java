package io.moreless.tide.rebuild;
import android.content.*;
import android.os.*;
import io.moreless.tide.rebuild.core.SleepTime;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        SleepStore s=new SleepStore(c);String action=intent.getAction(),token=intent.getStringExtra("token");
        try{
            if(SleepAlarm.TIMEOUT.equals(action)){SleepAlarm.timeout(c,token);return;}
            if(SleepAlarm.DISMISS.equals(action)){SleepAlarm.dismiss(c,token);return;}
            if(SleepAlarm.SNOOZE.equals(action)){SleepAlarm.snooze(c,token);return;}
            if(SleepAlarm.FIRE.equals(action)){
                long now=System.currentTimeMillis();
                if(!s.pending() || !SleepTime.shouldFire(token,s.token(),s.trigger(),now))return;
                long until=s.data.getLong("ringUntil",0);
                if(until!=0 && until<=now){SleepAlarm.timeout(c,token);return;}
                if(until==0)until=io.moreless.tide.rebuild.core.AlarmCycle.ringEnd(now);
                s.write(s.data.edit().putString("phase","ringing").putLong("ringUntil",until));
                SleepAlarm.armTimeout(c,token,until);
                PowerManager.WakeLock lock=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Tide:alarm-delivery");lock.acquire(10000);
                Intent service=new Intent(c,AlarmService.class).putExtra("token",token);
                if(Build.VERSION.SDK_INT>=26)c.startForegroundService(service);else c.startService(service);
            }else SleepAlarm.restore(c,action);
        }catch(RuntimeException e){s.data.edit().putString("error","闹钟未能启动："+e.getClass().getSimpleName()).commit();AlarmService.failureNotification(c);}
    }
}
