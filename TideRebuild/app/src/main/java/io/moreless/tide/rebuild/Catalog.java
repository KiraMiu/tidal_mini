package io.moreless.tide.rebuild;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import io.moreless.tide.rebuild.core.BreathTimeline;
import io.moreless.tide.rebuild.core.BreathScript;

public final class Catalog {
    public static String read(Context context, String name) throws IOException {
        try (InputStream stream=context.getAssets().open(name); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192]; int n;
            while ((n=stream.read(bytes))!=-1) out.write(bytes,0,n);
            return out.toString("UTF-8");
        }
    }
    public static JSONArray scenes(Context context) throws Exception {
        JSONArray source=new JSONArray(read(context,"presets/scenes.json")), result=new JSONArray();
        JSONArray recovered=new JSONArray(read(context,"presets/recovered_scenes.json"));
        for(int i=0;i<recovered.length();i++)source.put(recovered.getJSONObject(i));
        Set<String> local=new HashSet<>(Arrays.asList(context.getAssets().list("sounds")));
        for (int i=0;i<source.length();i++) {
            JSONObject scene=source.getJSONObject(i), resource=scene.optJSONObject("resource");
            if(!scene.optBoolean("rebuild_demo") && resource!=null && local.contains(resource.optString("bundle"))) result.put(scene);
        }
        return result;
    }
    public static JSONArray breathModes(Context context) throws Exception {
        return new JSONObject(read(context,"presets/breath/config.json")).getJSONArray("breath_modes");
    }
    public static BreathTimeline breath(JSONObject mode,int minutes) throws Exception {
        JSONObject script=mode.getJSONArray("actions").getJSONObject(0);
        JSONArray steps=script.getJSONArray("cycling");
        String[] names=new String[steps.length()];double[] seconds=new double[steps.length()];
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);names[i]=step.getString("type");seconds[i]=step.getDouble("duration");}
        return BreathScript.standard(mode.getString("mode"),names,seconds,script.getDouble("frequency_def"),minutes);
    }
}
