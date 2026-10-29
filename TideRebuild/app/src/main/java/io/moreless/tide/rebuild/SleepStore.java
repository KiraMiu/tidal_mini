package io.moreless.tide.rebuild;
import android.content.*;

/** Only alarm state lives in device-protected storage so it works before first unlock. */
public final class SleepStore {
    public final SharedPreferences data;
    public SleepStore(Context c){
        Context device=c.createDeviceProtectedStorageContext();
        data=device.getSharedPreferences("sleep_alarm",Context.MODE_PRIVATE);
        migrate(data,"sceneFile","sceneName",false);
        if("asset".equals(data.getString("ringtoneKind","asset")))migrate(data,"ringtone","ringtoneName",true);
        migrate(device.getSharedPreferences("sleep_audio",Context.MODE_PRIVATE),"file","title",false);
    }
    static boolean isPreview(String file){return file!=null && file.startsWith("original_demo_");}
    static String fullSound(String file,boolean alarm){
        if(!isPreview(file))return file;
        if(file.equals("original_demo_FkYQAAnlUP9GK4glJFppQX0joAGv.mp3"))return "lnSNpbE0TI_HLFPpdeSkoeIvGjvh.m4a";
        return alarm?"alarm_1.m4a":"lqVsojtUa0PGHanLKJwjWHMPyhP3.m4a";
    }
    static String fullName(String file){return file.equals("lnSNpbE0TI_HLFPpdeSkoeIvGjvh.m4a")?"绵雨":file.equals("alarm_1.m4a")?"光":"海洋";}
    private void migrate(SharedPreferences prefs,String fileKey,String titleKey,boolean alarm){
        String old=prefs.getString(fileKey,"");if(!isPreview(old))return;
        String replacement=fullSound(old,alarm);
        write(prefs.edit().putString(fileKey,replacement).putString(titleKey,fullName(replacement)));
    }
    public String phase(){return data.getString("phase","off");}
    public String token(){return data.getString("token","");}
    public long trigger(){return data.getLong("trigger",0);}
    public int hour(){return data.getInt("hour",7);}
    public int minute(){return data.getInt("minute",30);}
    public boolean pending(){return phase().equals("armed") || phase().equals("snoozed") || phase().equals("retry") || phase().equals("resuming");}
    public void write(SharedPreferences.Editor editor){if(!editor.commit())throw new IllegalStateException("闹钟状态保存失败，请重试");}
}
