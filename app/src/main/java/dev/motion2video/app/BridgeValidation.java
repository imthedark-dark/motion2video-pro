package dev.motion2video.app;
import org.json.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.util.*;
/** Closed command schema for the trusted MessagePort. No reflection/Intent/path dispatch. */
final class BridgeValidation {
 private static void invalid()throws IOException{throw new IOException("ساختار درخواست معتبر نیست.");}
 static String string(JSONObject o,String key,int max)throws Exception{Object v=o.get(key);if(!(v instanceof String)||((String)v).length()>max||((String)v).getBytes(StandardCharsets.UTF_8).length>max)invalid();return (String)v;}
 static boolean bool(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof Boolean))invalid();return (Boolean)v;}
 private static void number(JSONObject o,String key,double min,double max,boolean integer)throws Exception{Object v=o.get(key);if(!(v instanceof Number))invalid();double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<min||n>max||(integer&&n!=Math.rint(n)))invalid();}
 private static void keys(JSONObject o,String... names)throws Exception{Set<String> allowed=new HashSet<>(Arrays.asList(names));Iterator<String> i=o.keys();while(i.hasNext())if(!allowed.contains(i.next()))invalid();}
 static void options(JSONObject v)throws Exception{
    keys(v,"duration","fps","width","height","bitrate","mime","gpu","rangeStart","rangeEnd","audio");number(v,"duration",1,300,false);number(v,"fps",30,60,true);number(v,"width",1,1920,true);number(v,"height",1,1920,true);number(v,"bitrate",12000000,20000000,true);string(v,"mime",64);if(v.has("gpu"))bool(v,"gpu");if(v.has("rangeStart")!=v.has("rangeEnd"))invalid();if(v.has("rangeStart")){number(v,"rangeStart",0,300,false);number(v,"rangeEnd",0,300,false);RenderRange.frames(v.getDouble("duration"),v.getInt("fps"),v.getDouble("rangeStart"),v.getDouble("rangeEnd"));}
    if(v.has("audio")){if(!(v.get("audio") instanceof JSONObject))invalid();new AudioOptions(v.getJSONObject("audio"),Math.round((v.optDouble("rangeEnd",v.getDouble("duration"))-v.optDouble("rangeStart",0))*v.getInt("fps"))/(double)v.getInt("fps"));}
    int fps=v.getInt("fps"),w=v.getInt("width"),h=v.getInt("height"),bits=v.getInt("bitrate");if((fps!=30&&fps!=60)||(bits!=12000000&&bits!=16000000&&bits!=20000000)||!Arrays.asList(CodecSupport.MIME).contains(v.getString("mime"))||!((w==640&&h==360)||(w==1280&&h==720)||(w==1920&&h==1080)||(w==1080&&h==1920)||(w==1080&&h==1080)))invalid();
 }
 static String command(JSONObject o)throws Exception{
  String type=string(o,"type",32);
  switch(type){
   case "preview":case "render":
    keys(o,"type","html","options");ImportPolicy.validate(string(o,"html",ImportPolicy.MAX_BYTES));Object options=o.get("options");if(!(options instanceof JSONObject))invalid();JSONObject v=(JSONObject)options;
    options(v);break;
   case "queueFiles":keys(o,"type","options");if(!(o.get("options") instanceof JSONObject))invalid();options(o.getJSONObject("options"));break;
   case "cancelQueued":case "openQueueResult":keys(o,"type","id");if(!AudioPolicy.id(string(o,"id",36)))invalid();break;
   case "layout":
    keys(o,"type","dpr","x","y","width","height","clipTop","visible");number(o,"dpr",.01,10,false);number(o,"x",-100000,100000,false);number(o,"y",-100000,100000,false);number(o,"width",1,10000,false);number(o,"height",1,10000,false);if(o.has("clipTop"))number(o,"clipTop",0,100000,false);if(o.has("visible"))bool(o,"visible");break;
   case "shareDownload":case "openDownload":keys(o,"type","uri");String uri=string(o,"uri",2048);if(!uri.startsWith("content://"))invalid();break;
   case "onlineResources":keys(o,"type","enabled");bool(o,"enabled");break;
   case "about":case "troubleshooting":case "compatibility":case "queueState":case "resumeQueue":case "chooseAudio":case "clearAudioAssets":case "hello":case "renderState":case "downloads":case "cancel":case "capabilities":case "openDownloads":case "save":case "chooseFile":keys(o,"type");break;
   default:invalid();
  }return type;
 }
}
