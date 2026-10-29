package io.moreless.tide.rebuild;
import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import android.provider.Settings;

/** Ringing is independent of the screen and ambient audio; rings for one minute per round. */
public final class AlarmService extends Service {
    static boolean running;
    private MediaPlayer player;
    private String token;
    private boolean silentTest;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private AudioFocusRequest focus;
    private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
        if(player!=null){try{float gain=silentTest?0:change==AudioManager.AUDIOFOCUS_GAIN?1f:.25f;player.setVolume(gain,gain);}catch(IllegalStateException ignored){}}
    };
    static void channel(Context c){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel("wake_alarm","睡眠闹钟",NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);}
    }
    public static boolean silentChannelSafe(Context c){
        if(Build.VERSION.SDK_INT<26)return true;
        NotificationManager manager=(NotificationManager)c.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel test=new NotificationChannel("wake_silent_test","静音闹钟测试",NotificationManager.IMPORTANCE_HIGH);
        test.setSound(null,null);test.enableVibration(false);manager.createNotificationChannel(test);
        NotificationChannel actual=manager.getNotificationChannel("wake_silent_test");
        return actual.getSound()==null && !actual.shouldVibrate();
    }
    static Notification.Builder builder(Context c){
        boolean test=new SleepStore(c).data.getBoolean("silentTest",false);channel(c);if(test)silentChannelSafe(c);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,test?"wake_silent_test":"wake_alarm"):new Notification.Builder(c);
        if(test){b.setDefaults(0).setSound(null).setVibrate(null);}
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b;
    }
    public static void failureNotification(Context c){
        if(new SleepStore(c).data.getBoolean("silentTest",false) && !silentChannelSafe(c))return;
        ((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE)).notify(201,builder(c).setSmallIcon(SleepUi.notificationIcon(c)).setContentTitle("睡眠闹钟需要检查").setContentText(new SleepStore(c).data.getString("error","请打开应用检查后台设置")).setContentIntent(SleepAlarm.home(c)).setAutoCancel(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        SleepStore s=new SleepStore(this);String requested=intent==null?s.token():intent.getStringExtra("token");
        if(!s.phase().equals("ringing") || !s.token().equals(requested)){stopSelf();return START_NOT_STICKY;}
        if(s.data.getLong("ringUntil",0)<=System.currentTimeMillis()){SleepAlarm.timeout(this,requested);stopSelf();return START_NOT_STICKY;}
        token=requested;silentTest=s.data.getBoolean("silentTest",false);
        if(silentTest && !silentChannelSafe(this)){s.data.edit().putString("phase","off").putString("error","静音测试通知频道被设置为有声，已取消测试").commit();stopSelf();return START_NOT_STICKY;}
        Intent page=new Intent(this,WakeActivity.class).putExtra("token",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,703,page,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=builder(this).setSmallIcon(SleepUi.notificationIcon(this)).setContentTitle("起床时间到，正在响铃…").setContentText(AlarmSound.title(this))
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
            .setContentIntent(open).setFullScreenIntent(open,true)
            .addAction(android.R.drawable.ic_lock_idle_alarm,"再睡30分钟",SleepAlarm.command(this,SleepAlarm.SNOOZE,token))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"起床",SleepAlarm.command(this,SleepAlarm.DISMISS,token)).build();
        startForeground(200,n);running=true;
        AudioService.stopPlayback(this);
        if(player!=null)return START_STICKY;
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        if(Build.VERSION.SDK_INT>=26){focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).build();audio.requestAudioFocus(focus);}else audio.requestAudioFocus(focusListener,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        playSource(0);
        handler.postDelayed(()->SleepAlarm.timeout(this,token),Math.max(1,s.data.getLong("ringUntil",0)-System.currentTimeMillis()));
        return START_STICKY;
    }
    private void playSource(int stage){
        if(player!=null){player.release();player=null;}
        SleepStore store=new SleepStore(this);
        if(!store.phase().equals("ringing") || !store.token().equals(token))return;
        if(stage>2){
            store.data.edit().putString("ringPlayback","error").putString("error","铃声播放失败，请检查闹钟音量和输出设备").commit();failureNotification(this);return;
        }
        String source=stage==0?"selected":stage==1?"system-default":"bundled-fallback";
        store.data.edit().putString("ringPlayback","preparing").putString("ringSource",source).commit();
        try{
            MediaPlayer next=new MediaPlayer();player=next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);next.setLooping(true);if(silentTest)next.setVolume(0,0);
            next.setOnErrorListener((failed,what,extra)->{if(failed==player)playSource(stage+1);return true;});
            next.setOnPreparedListener(prepared->{
                if(prepared!=player)return;
                try{prepared.start();store.data.edit().putString("ringPlayback","playing").putString("playingRingtone",stage==0?AlarmSound.title(this):stage==1?"系统默认闹钟铃声（回退）":"光（回退）").commit();}
                catch(RuntimeException error){playSource(stage+1);}
            });
            if(stage==0)AlarmSound.source(this,next);
            else if(stage==1)next.setDataSource(this,Settings.System.DEFAULT_ALARM_ALERT_URI);
            else try(AssetFileDescriptor fd=getAssets().openFd("sounds/alarm_1.m4a")){next.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
            next.prepareAsync();
        }catch(Exception error){playSource(stage+1);}
    }
    @Override public void onDestroy(){running=false;handler.removeCallbacksAndMessages(null);if(player!=null){player.release();player=null;}if(audio!=null){if(Build.VERSION.SDK_INT>=26 && focus!=null)audio.abandonAudioFocusRequest(focus);else audio.abandonAudioFocus(focusListener);}stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
