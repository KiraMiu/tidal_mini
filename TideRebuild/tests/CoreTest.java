import io.moreless.tide.rebuild.core.SessionTimer;
import io.moreless.tide.rebuild.core.BreathTimeline;
import io.moreless.tide.rebuild.core.BreathScript;
import io.moreless.tide.rebuild.core.PlaybackIntent;
public final class CoreTest {
 static void eq(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
 public static void main(String[] args){
  SessionTimer t=new SessionTimer(60000); t.start(1000);eq(30000,t.remaining(31000));
  t.pause(31000);eq(30000,t.remaining(99000));t.resume(100000);eq(20000,t.remaining(110000));
  eq(0,t.remaining(999999));if(!t.isFinished(999999))throw new AssertionError("must finish");
  t.pause(999999);t.resume(1000000);eq(0,t.remaining(1000001));
  SessionTimer restored=SessionTimer.restore(60000,20000,false,0);eq(20000,restored.remaining(5));
  restored.resume(10);eq(10000,restored.remaining(10010));
  BreathTimeline b=new BreathTimeline(new String[]{"inhale","hold","exhale"},new long[]{4000,7000,8000});
  eq(0,b.indexAt(0));eq(1,b.indexAt(4000));eq(2,b.indexAt(11000));eq(-1,b.indexAt(19000));eq(19000,b.duration());
  BreathTimeline balance=BreathScript.standard("4-0-4-0",new String[]{"break","inhale","break","exhale","break"},new double[]{0.4,4,0.5,4,0.4},6,1);
  eq(60000,balance.duration());eq(1,balance.indexAt(400));eq(2,balance.indexAt(4750));
  BreathTimeline box=BreathScript.standard("box",new String[]{"inhale","hold","exhale","hold"},new double[]{4,4,4,4},3.75,1);
  eq(64000,box.duration());
  PlaybackIntent play=new PlaybackIntent();play.play();play.interrupt(true);play.pause();
  if(play.regainFocus())throw new AssertionError("manual pause must cancel focus auto-resume");
  play.play();play.interrupt(true);play.interrupt(false);
  if(!play.regainFocus())throw new AssertionError("nested transient loss must retain resume intent");
  play.play();play.pause();if(play.wantsPlayback())throw new AssertionError("pause during preparation must prevent autoplay");
  boolean rejected=false;try{new SessionTimer(0);}catch(IllegalArgumentException e){rejected=true;}if(!rejected)throw new AssertionError("invalid duration");
  System.out.println("PASS: countdown, pause/resume, completion, restoration, breath boundaries, invalid duration");
 }
}
