import io.moreless.tide.rebuild.core.SleepTime;
import io.moreless.tide.rebuild.core.AlarmCycle;
import io.moreless.tide.rebuild.core.HoldGesture;
import java.util.*;
public class SleepTest {
 static long at(TimeZone z,int y,int month,int d,int h,int m){Calendar c=Calendar.getInstance(z);c.clear();c.set(y,month-1,d,h,m);return c.getTimeInMillis();}
 static void eq(long x,long y){if(x!=y)throw new AssertionError(x+" != "+y);}
 public static void main(String[] a){
  TimeZone z=TimeZone.getTimeZone("Asia/Shanghai");
  eq(at(z,2026,10,29,7,30),SleepTime.next(at(z,2026,10,28,22,0),7,30,z));
  eq(at(z,2026,10,28,7,30),SleepTime.next(at(z,2026,10,28,6,0),7,30,z));
  eq(at(z,2027,1,1,7,0),SleepTime.next(at(z,2026,12,31,23,0),7,0,z));
  TimeZone ny=TimeZone.getTimeZone("America/New_York");
  eq(at(ny,2026,3,8,7,0),SleepTime.next(at(ny,2026,3,7,22,0),7,0,ny));
  eq(at(z,2026,10,29,7,30),SleepTime.next(at(z,2026,10,28,7,30),7,30,z));
  if(SleepTime.shouldFire("old","new",1000,1000))throw new AssertionError("stale alarm");
  if(SleepTime.shouldFire("new","new",1000,500))throw new AssertionError("early alarm");
  if(!SleepTime.shouldFire("new","new",1000,1100))throw new AssertionError("valid alarm");
  if(SleepTime.shouldFire("new","new",1000,901001))throw new AssertionError("expired alarm");
  // These cases catch wrong retry delays, an extra fourth ring, and a short snooze.
  eq(61000L,AlarmCycle.ringEnd(1000L));
  eq(301000L,AlarmCycle.retryAt(1,61000L));
  eq(601000L,AlarmCycle.retryAt(2,361000L));
  eq(0L,AlarmCycle.retryAt(3,661000L));
  eq(1801000L,AlarmCycle.snoozeAt(1000L));
  HoldGesture hold=new HoldGesture();hold.begin(1000L);
  if(hold.complete(2499L))throw new AssertionError("short press completed");
  hold.cancel();if(hold.complete(2600L))throw new AssertionError("canceled hold completed");
  hold.begin(3000L);if(hold.progress(3750L)!=.5f)throw new AssertionError("wrong progress");
  if(!hold.complete(4500L))throw new AssertionError("full hold did not complete");
  if(hold.complete(4600L))throw new AssertionError("hold completed twice");
  System.out.println("PASS sleep: same/next day, year boundary, DST, stale/early/expired trigger; three 1-minute rounds, 4-minute gaps, 30-minute snooze; hold duration/cancel/progress/single completion");
 }
}
