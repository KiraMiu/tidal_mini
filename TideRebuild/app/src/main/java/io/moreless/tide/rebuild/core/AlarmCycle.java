package io.moreless.tide.rebuild.core;
/** User-requested wake policy, independent of Android scheduling. */
public final class AlarmCycle {
    public static long ringEnd(long start){return start+60000L;}
    public static long retryAt(int round,long ended){return round>=3?0:ended+240000L;}
    public static long snoozeAt(long now){return now+1800000L;}
}
