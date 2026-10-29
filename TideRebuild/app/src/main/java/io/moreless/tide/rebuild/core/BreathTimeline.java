package io.moreless.tide.rebuild.core;

/** Executes the APK's ordered action durations once, without inventing a cycle. */
public final class BreathTimeline {
    private final String[] actions;
    private final long[] ends;
    public BreathTimeline(String[] actions, long[] durations) {
        if (actions.length == 0 || actions.length != durations.length) throw new IllegalArgumentException();
        this.actions = actions.clone(); ends = new long[durations.length];
        long total = 0;
        for (int i=0; i<durations.length; i++) {
            if (durations[i] <= 0) throw new IllegalArgumentException();
            total = Math.addExact(total, durations[i]); ends[i] = total;
        }
    }
    public int indexAt(long elapsed) {
        for (int i=0;i<ends.length;i++) if (elapsed < ends[i]) return i;
        return -1;
    }
    public String actionAt(long elapsed) { int i=indexAt(elapsed); return i < 0 ? "done" : actions[i]; }
    public long duration() { return ends[ends.length-1]; }
}
