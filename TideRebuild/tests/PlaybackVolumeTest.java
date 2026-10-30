import io.moreless.tide.rebuild.core.PlaybackVolume;

/** Catches coarse snapping, endpoint errors and lost legacy volume settings. */
public class PlaybackVolumeTest {
    private static void eq(float expected,float actual){
        if(!Float.isFinite(actual) || Math.abs(expected-actual)>.000001f)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args){
        eq(0f,PlaybackVolume.gain(0));eq(1f,PlaybackVolume.gain(10000));
        eq(.3754f,PlaybackVolume.gain(3754));eq(.0037f,PlaybackVolume.gain(37));
        eq(3754,PlaybackVolume.progress(.3754f));
        eq(1f,PlaybackVolume.DEFAULT_GAIN);
        eq(0f,PlaybackVolume.normalize(-1f));eq(1f,PlaybackVolume.normalize(2f));
        eq(1f,PlaybackVolume.normalize(Float.NaN));
        eq(0f,PlaybackVolume.legacyGain(0));eq(.3f,PlaybackVolume.legacyGain(3));eq(1f,PlaybackVolume.legacyGain(10));
        if(!"38%".equals(PlaybackVolume.percent(.3754f)))throw new AssertionError("integer percent rounding");
        if(!"37%".equals(PlaybackVolume.percent(.3749f)))throw new AssertionError("integer percent rounding below midpoint");
        if(!"100%".equals(PlaybackVolume.percent(1f)))throw new AssertionError("full volume label");
        if(!"0%".equals(PlaybackVolume.percent(0f)))throw new AssertionError("mute label");
        System.out.println("PASS volume: fine-grained gain, integer percentage rounding, defaults/bounds and legacy conversion");
    }
}
