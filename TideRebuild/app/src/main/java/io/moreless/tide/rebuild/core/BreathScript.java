package io.moreless.tide.rebuild.core;

/** Port of defpackage.cx.l(Standard): scale eligible actions, repeat whole cycles. */
public final class BreathScript {
    private static boolean scalable(String mode,String action) {
        return (mode.equals("4-0-4-0") || mode.equals("4-7-8-0")) &&
            (action.equals("inhale") || action.equals("exhale") || action.equals("hold"));
    }
    public static BreathTimeline standard(String mode,String[] actions,double[] seconds,double bpm,int minutes) {
        if(actions.length==0 || actions.length!=seconds.length || !Double.isFinite(bpm) || bpm<=0 || minutes<1 || minutes>60) throw new IllegalArgumentException();
        double fixed=0,variable=0;
        for(int i=0;i<actions.length;i++) {
            if(!Double.isFinite(seconds[i]) || seconds[i]<=0)throw new IllegalArgumentException();
            if(scalable(mode,actions[i]))variable+=seconds[i];else fixed+=seconds[i];
        }
        double scale=variable>0 && 60/bpm>fixed ? (60/bpm-fixed)/variable : 1;
        long[] cycle=new long[actions.length];long cycleMillis=0;
        for(int i=0;i<actions.length;i++) {
            cycle[i]=Math.round(seconds[i]*(scalable(mode,actions[i])?scale:1)*1000);
            if(cycle[i]<=0)throw new IllegalArgumentException();cycleMillis+=cycle[i];
        }
        int loops=(int)Math.ceil(minutes*60000.0/cycleMillis);
        String[] expanded=new String[actions.length*loops];long[] durations=new long[expanded.length];
        for(int i=0;i<expanded.length;i++){expanded[i]=actions[i%actions.length];durations[i]=cycle[i%cycle.length];}
        return new BreathTimeline(expanded,durations);
    }
}
