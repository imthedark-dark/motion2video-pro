package dev.motion2video.app;
import java.io.IOException;import java.util.*;import org.json.*;
public final class AudioOptions {
 public static final String ERROR="تنظیمات صدا معتبر نیست؛ برش، بلندی و محوشدن را بررسی کن.";
 public final String id;public final double start,end,volume,fadeIn,fadeOut;
 public AudioOptions(JSONObject o,double videoDuration)throws Exception{
  Set<String> keys=new HashSet<>(Arrays.asList("id","start","end","volume","fadeIn","fadeOut"));Iterator<String> i=o.keys();while(i.hasNext())if(!keys.contains(i.next()))throw new IOException(ERROR);
  if(!(o.get("id") instanceof String)||!AudioPolicy.id(o.getString("id")))throw new IOException(ERROR);id=o.getString("id");
  for(String key:new String[]{"start","end","volume","fadeIn","fadeOut"})if(!(o.get(key) instanceof Number)||!Double.isFinite(o.getDouble(key)))throw new IOException(ERROR);
  start=o.getDouble("start");end=o.getDouble("end");volume=o.getDouble("volume");fadeIn=o.getDouble("fadeIn");fadeOut=o.getDouble("fadeOut");
  if(start<0||end<=start||end>1800||volume<0||volume>2||fadeIn<0||fadeOut<0||fadeIn+fadeOut>Math.min(end-start,videoDuration))throw new IOException(ERROR);
 }
 public JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("start",start).put("end",end).put("volume",volume).put("fadeIn",fadeIn).put("fadeOut",fadeOut);}
}
