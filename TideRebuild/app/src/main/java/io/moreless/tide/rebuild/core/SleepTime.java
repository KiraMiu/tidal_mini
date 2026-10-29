package io.moreless.tide.rebuild.core;
import java.util.Calendar;
import java.util.TimeZone;

public final class SleepTime {
    public static long next(long now,int hour,int minute,TimeZone zone) {
        if(hour<0 || hour>23 || minute<0 || minute>59)throw new IllegalArgumentException("invalid clock time");
        Calendar c=Calendar.getInstance(zone);c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()<=now){c.add(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);}
        return c.getTimeInMillis();
    }
    public static boolean shouldFire(String received,String expected,long trigger,long now){
        return received!=null && !received.isEmpty() && received.equals(expected) && now>=trigger && now-trigger<=900000;
    }
}
