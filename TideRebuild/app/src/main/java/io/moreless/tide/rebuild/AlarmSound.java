package io.moreless.tide.rebuild;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.util.UUID;

/** Imported files live with alarm state and remain readable before first unlock. */
final class AlarmSound {
    static String title(Context c){
        SleepStore s=new SleepStore(c);
        return s.data.getString("ringtoneName",s.data.getString("ringtone","alarm_1.m4a").equals("alarm_1.m4a")?"光":"挪威的森林");
    }
    static void source(Context c,MediaPlayer player) throws IOException {
        SleepStore s=new SleepStore(c);
        String kind=s.data.getString("ringtoneKind","asset");
        if(kind.equals("system")){
            player.setDataSource(c,Uri.parse(s.data.getString("ringtoneUri",Settings.System.DEFAULT_ALARM_ALERT_URI.toString())));
        }else if(kind.equals("custom")){
            String name=s.data.getString("ringtoneFile","");
            if(!name.matches("alarm-[a-f0-9\\-]+\\.audio"))throw new IOException("invalid imported sound");
            player.setDataSource(new File(c.createDeviceProtectedStorageContext().getFilesDir(),name).getAbsolutePath());
        }else{
            String name=s.data.getString("ringtone","alarm_1.m4a");
            if(name.contains("/") || name.contains(".."))throw new IOException("invalid asset");
            try(AssetFileDescriptor fd=c.getAssets().openFd("sounds/"+name)){
                player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            }
        }
    }
    static String importFile(Context c,Uri uri) throws Exception {
        // Stream into a unique candidate; selection changes only after validation succeeds.
        String name="alarm-"+UUID.randomUUID()+".audio";
        File candidate=new File(c.createDeviceProtectedStorageContext().getFilesDir(),name);
        long total=0;
        try {
        try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(candidate)){
            if(in==null)throw new IOException("无法读取所选文件");
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){total+=n;if(total>50L*1024*1024)throw new IOException("请选择小于 50 MB 的音频");out.write(buffer,0,n);}
            out.getFD().sync();
        }
        if(total==0)throw new IOException("音频文件为空");
        android.media.MediaMetadataRetriever probe=new android.media.MediaMetadataRetriever();
        try{probe.setDataSource(candidate.getAbsolutePath());String duration=probe.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null || Long.parseLong(duration)<=0)throw new IOException("无法识别音频，请选择 MP3、M4A、WAV 或 OGG 文件");}finally{probe.release();}
        return name;
        } catch(Exception error){
            // Only this newly created candidate is discarded; the previous selection and source stay intact.
            if(candidate.exists() && !candidate.delete())android.util.Log.w("TideAlarm","Could not remove failed import candidate");
            throw error;
        }
    }
}
