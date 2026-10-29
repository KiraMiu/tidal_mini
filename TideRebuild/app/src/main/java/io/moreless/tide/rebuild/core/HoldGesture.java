package io.moreless.tide.rebuild.core;
/** Monotonic press duration, independent of system animation speed. */
public final class HoldGesture {
    private long started=-1;
    public void begin(long now){started=now;}
    public void cancel(){started=-1;}
    public boolean active(){return started>=0;}
    public float progress(long now){return started<0?0:Math.max(0,Math.min(1,(now-started)/1500f));}
    public boolean complete(long now){if(started>=0 && now-started>=1500){cancel();return true;}return false;}
}
