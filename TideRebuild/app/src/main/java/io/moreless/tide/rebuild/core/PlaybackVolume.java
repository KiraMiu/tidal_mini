package io.moreless.tide.rebuild.core;

/** Background-player gain, independent of system media and alarm volume. */
public final class PlaybackVolume {
    public static final int MAX_PROGRESS=10000;
    public static final float DEFAULT_GAIN=1f;
    private PlaybackVolume(){}
    public static float normalize(float gain){return Float.isNaN(gain)?DEFAULT_GAIN:Math.max(0f,Math.min(1f,gain));}
    public static float gain(int progress){return normalize(progress/(float)MAX_PROGRESS);}
    public static int progress(float gain){return Math.round(normalize(gain)*MAX_PROGRESS);}
    public static float legacyGain(int step){return normalize(step/10f);}
    public static String percent(float gain){
        return Math.round(normalize(gain)*100f)+"%";
    }
}
