package io.moreless.tide.rebuild;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The notification and launcher converge here while the current session is ringing. */
public final class WakeActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String token;
    private TextView clock,ringtone;
    private final Runnable check=new Runnable(){public void run(){
        SleepStore s=new SleepStore(WakeActivity.this);
        if(!s.phase().equals("ringing") || !s.token().equals(token)){finish();return;}
        if(s.data.getLong("ringUntil",0)<=System.currentTimeMillis()){SleepAlarm.timeout(WakeActivity.this,token);finish();return;}
        clock.setText(SleepUi.clock(WakeActivity.this));ringtone.setText(s.data.getString("playingRingtone",AlarmSound.title(WakeActivity.this)));handler.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        token=getIntent().getStringExtra("token");SleepStore s=new SleepStore(this);
        if(token==null || !s.token().equals(token) || !s.phase().equals("ringing")){finish();return;}
        FrameLayout root=SleepUi.backdrop(this,"art/sleep_dimmed.jpg");LinearLayout col=SleepUi.column(this,root);
        SleepUi.space(this,col,.71f);
        FrameLayout dial=new FrameLayout(this);int size=SleepUi.dp(this,300);col.addView(dial,new LinearLayout.LayoutParams(size,size));
        ImageView ring=new ImageView(this);ring.setImageBitmap(SleepUi.bitmap(this,"art/alarm_ring.webp"));dial.addView(ring,new FrameLayout.LayoutParams(-1,-1));
        clock=new TextView(this);clock.setTextSize(72);clock.setTextColor(0xfff0eff3);clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin",0));SleepUi.styleClock(this,clock);clock.setGravity(Gravity.CENTER);dial.addView(clock,new FrameLayout.LayoutParams(-1,-1));
        SleepUi.space(this,col,.62f);
        SleepUi.text(this,col,"正在响铃…",20,-1);ringtone=SleepUi.text(this,col,AlarmSound.title(this),13,0xffaaaeba);
        SleepUi.space(this,col,.93f);
        TextView snooze=SleepUi.button(this,col,"再睡30分钟",false,()->{try{SleepAlarm.snooze(this,token);finish();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("知道了",null).show();}});
        snooze.setContentDescription("再睡30分钟");snooze.setLayoutParams(new LinearLayout.LayoutParams(SleepUi.dp(this,220),SleepUi.dp(this,56)));
        TextView dismiss=SleepUi.button(this,col,"起床",false,()->{SleepAlarm.dismiss(this,token);finish();});
        LinearLayout.LayoutParams stop=new LinearLayout.LayoutParams(-1,SleepUi.dp(this,84));stop.topMargin=SleepUi.dp(this,18);dismiss.setLayoutParams(stop);dismiss.setTextSize(28);dismiss.setTextColor(0xff073d29);dismiss.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);dismiss.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33003d25),SleepUi.surface(this,0xff66e3a4,0),null));
        SleepUi.space(this,col,.58f);
    }
    @Override protected void onStart(){super.onStart();handler.post(check);}
    @Override protected void onStop(){handler.removeCallbacks(check);super.onStop();}
    @Override public void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);recreate();}
}
