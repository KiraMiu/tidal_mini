package io.moreless.tide.rebuild.core;

/** Explicit user intent takes precedence over transient system interruptions. */
public final class PlaybackIntent {
    private boolean wanted, interrupted;
    public void play(){wanted=true;interrupted=false;}
    public void pause(){wanted=false;interrupted=false;}
    public void interrupt(boolean wasPlaying){interrupted |= wasPlaying && wanted;}
    public boolean regainFocus(){boolean resume=interrupted && wanted;interrupted=false;return resume;}
    public boolean wantsPlayback(){return wanted;}
}
