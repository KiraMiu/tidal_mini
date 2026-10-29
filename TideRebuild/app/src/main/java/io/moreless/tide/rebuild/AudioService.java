package io.moreless.tide.rebuild;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import io.moreless.tide.rebuild.core.PlaybackIntent;

/** Local-only foreground player. No accounts, network endpoints or vendor SDKs. */
public final class AudioService extends Service {
    public static boolean running;
    public static SharedPreferences state(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("sleep_audio",MODE_PRIVATE);}
    public static void stopPlayback(Context c){state(c).edit().putBoolean("wanted",false).putString("status","stopped").commit();c.stopService(new Intent(c,AudioService.class));}
    public static void play(Context c,String title,String file){Intent i=new Intent(c,AudioService.class).setAction(PLAY).putExtra("title",title).putExtra("file",file);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    private int boot(){return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private void persist(String status){state(this).edit().putBoolean("wanted",playback.wantsPlayback()).putString("status",status).commit();}
    public static final String PLAY="play", PAUSE="pause", RESUME="resume", STOP="stop";
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private String title="声音场景";
    private boolean ready;
    private final PlaybackIntent playback=new PlaybackIntent();
    private final BroadcastReceiver noisy=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){pauseByUser();}
    };
    private final AudioManager.OnAudioFocusChangeListener listener=change -> {
        if (change==AudioManager.AUDIOFOCUS_GAIN) { if(playback.regainFocus())resume(); }
        else if(change==AudioManager.AUDIOFOCUS_LOSS) { pauseByUser(); }
        else { playback.interrupt(player!=null && ready && player.isPlaying());pause(); }
    };
    @Override public void onCreate() {
        super.onCreate();running=true;audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(Build.VERSION.SDK_INT>=26) {
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("audio","声音播放",NotificationManager.IMPORTANCE_LOW));
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(listener).build();
        }
        session=new MediaSession(this,"Tide offline");
        session.setCallback(new MediaSession.Callback(){public void onPause(){pauseByUser();}public void onPlay(){resume();}public void onStop(){stopSelf();}});
        session.setActive(true);
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy,filter);
    }
    private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private boolean requestFocus(){return (Build.VERSION.SDK_INT>=26?audio.requestAudioFocus(focus):audio.requestAudioFocus(listener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN))==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        new SleepStore(this); // Upgrade persisted preview choices before any sticky/media resume.
        if(intent==null){
            SharedPreferences s=state(this);
            if(!s.getBoolean("wanted",false) || s.getInt("boot",-2)!=boot()){stopSelf();return START_NOT_STICKY;}
            intent=new Intent().setAction(PLAY).putExtra("title",s.getString("title","声音")).putExtra("file",s.getString("file",""));
        }
        String action=intent.getAction();
        if(STOP.equals(action)){playback.pause();persist("stopped");stopSelf();return START_NOT_STICKY;}
        if(PAUSE.equals(action)){if(player==null){stopSelf();return START_NOT_STICKY;}pauseByUser();return START_STICKY;}
        if(RESUME.equals(action)){
            if(player!=null){resume();return START_STICKY;}
            SharedPreferences s=state(this);intent=new Intent().setAction(PLAY).putExtra("title",s.getString("title","声音")).putExtra("file",s.getString("file",""));action=PLAY;
        }
        if(!PLAY.equals(action))return START_NOT_STICKY;
        String selected=intent.getStringExtra("file");
        if(SleepStore.isPreview(selected)){String replacement=SleepStore.fullSound(selected,false);intent.putExtra("file",replacement).putExtra("title",SleepStore.fullName(replacement));}
        title=intent.getStringExtra("title");startForeground(1,notification(false));
        if(new SleepStore(this).phase().equals("ringing")){stopSelf();return START_NOT_STICKY;}
        try {
            String file=intent.getStringExtra("file");
            if(file==null || file.contains("/") || !(file.endsWith(".m4a") || file.endsWith(".mp3")))throw new IllegalArgumentException("invalid sound");
            releasePlayer();playback.play();player=new MediaPlayer();player.setAudioAttributes(attributes());
            state(this).edit().putString("file",file).putString("title",title).putInt("boot",boot()).commit();persist("preparing");
            player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
            try(AssetFileDescriptor fd=getAssets().openFd("sounds/"+file)){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
            player.setLooping(true);
            player.setOnPreparedListener(p->{if(p!=player)return;ready=true;if(!playback.wantsPlayback()){update(false);return;}if(requestFocus()){p.start();update(true);}else{update(false);android.widget.Toast.makeText(this,"暂时无法获得音频焦点，请稍后继续播放",1).show();}});
            player.setOnErrorListener((p,what,extra)->{playback.pause();persist("error");android.widget.Toast.makeText(this,"声音播放失败："+what,1).show();stopSelf();return true;});
            player.prepareAsync();
        } catch(Exception e){playback.pause();persist("error");android.widget.Toast.makeText(this,"无法播放："+e.getMessage(),1).show();stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private PendingIntent command(String action){return PendingIntent.getService(this,action.hashCode(),new Intent(this,AudioService.class).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private Notification notification(boolean playing){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"audio"):new Notification.Builder(this);
        SleepStore sleep=new SleepStore(this);
        String description=sleep.pending()?new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(sleep.trigger()))+" 起床 · "+title:title;
        try(java.io.InputStream cover=getAssets().open("art/sleep_dimmed.jpg")){
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(cover);
            b.setLargeIcon(bitmap);
            session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,sleep.pending()?"睡眠":title).putString(MediaMetadata.METADATA_KEY_ARTIST,description).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,bitmap).build());
        }catch(java.io.IOException ignored){}
        return b.setSmallIcon(SleepUi.notificationIcon(this)).setContentTitle(sleep.pending()?"睡眠":title).setContentText((playing?"":"已暂停 · ")+description).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,SleepActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"暂停":"继续",command(playing?PAUSE:RESUME))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",command(STOP))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).setOngoing(playing).build();
    }
    private void update(boolean playing){
        persist(playing?"playing":"paused");
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,playing?1:0).build());
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification(playing));
    }
    private void pause(){if(player!=null && ready){player.pause();update(false);}}
    private void pauseByUser(){playback.pause();persist("paused");pause();}
    private void resume(){playback.play();if(player!=null && ready && requestFocus()){player.start();update(true);}}
    private void releasePlayer(){ready=false;if(player!=null){player.release();player=null;}}
    @Override public void onDestroy(){running=false;unregisterReceiver(noisy);playback.pause();releasePlayer();if(Build.VERSION.SDK_INT>=26)audio.abandonAudioFocusRequest(focus);else audio.abandonAudioFocus(listener);session.release();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
