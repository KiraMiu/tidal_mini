package io.moreless.tide.rebuild.core;

/** Monotonic-clock countdown; callers supply elapsedRealtime, never wall clock. */
public final class SessionTimer {
    public final long duration;
    private long savedRemaining, startedAt;
    private boolean running;
    public SessionTimer(long duration) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        this.duration = duration; savedRemaining = duration;
    }
    public static SessionTimer restore(long duration, long remaining, boolean running, long now) {
        SessionTimer timer = new SessionTimer(duration);
        timer.savedRemaining = Math.max(0, Math.min(duration, remaining));
        timer.running = running && timer.savedRemaining > 0; timer.startedAt = now;
        return timer;
    }
    public void start(long now) { resume(now); }
    public void resume(long now) {
        if (!running && savedRemaining > 0) { startedAt = now; running = true; }
    }
    public void pause(long now) { savedRemaining = remaining(now); running = false; }
    public long remaining(long now) {
        return Math.max(0, savedRemaining - (running ? Math.max(0, now - startedAt) : 0));
    }
    public boolean isRunning(long now) { return running && remaining(now) > 0; }
    public boolean isFinished(long now) { return remaining(now) == 0; }
}
