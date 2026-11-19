package dev.motion2video.app;
import org.json.*;
/** JVM schema tests using production sources; Android framework/codec calls are not invoked. */
public final class RequestValidationTest {
 private static int checks;
 private static JSONObject options()throws Exception{return new JSONObject().put("duration",14).put("fps",60).put("width",1920).put("height",1080).put("bitrate",16000000).put("mime","video/avc").put("gpu",false);}
 private static void reject(JSONObject bad,boolean bridge)throws Exception{
  try{if(bridge)BridgeValidation.command(new JSONObject().put("type","render").put("html","<html>سلام</html>").put("options",bad));else RenderRequest.create("<html>سلام</html>",bad);throw new AssertionError("Invalid settings accepted");}catch(java.io.IOException|JSONException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
  for(int[] size:new int[][]{{640,360},{1280,720},{1920,1080},{1080,1920},{1080,1080}})for(int fps:new int[]{30,60})for(int bitrate:new int[]{12000000,16000000,20000000})for(String mime:CodecSupport.MIME){
   JSONObject o=options().put("width",size[0]).put("height",size[1]).put("fps",fps).put("bitrate",bitrate).put("mime",mime);
   RenderRequest r=RenderRequest.create("<html>سلام</html>",o);if(r.width!=size[0]||r.height!=size[1]||r.fps!=fps||r.bitrate!=bitrate||r.frames!=14*fps||!BridgeValidation.command(new JSONObject().put("type","render").put("html",r.html).put("options",o)).equals("render"))throw new AssertionError("Valid output changed");checks++;
  }
  for(Object[] change:new Object[][]{{"fps",45},{"fps","60"},{"fps",30.5},{"fps",4294967326L},{"width",0},{"width",3840},{"height",1920},{"duration",0},{"duration",300.1},{"duration","14"},{"bitrate",15000000},{"bitrate","16000000"},{"mime","video/unknown"},{"mime",7},{"gpu","false"},{"gpu",1}}){JSONObject bad=options().put((String)change[0],change[1]);reject(bad,true);reject(bad,false);}
  for(double duration:new double[]{1,300}){RenderRequest r=RenderRequest.create("<html></html>",options().put("duration",duration));if(r.frames!=Math.round(duration*60))throw new AssertionError("Frame rounding changed");checks++;}
  JSONObject extra=options().put("arbitraryPath","/private");reject(extra,true);
  for(String type:new String[]{"undeclaredMethod","openFile","exec","fetch"}){try{BridgeValidation.command(new JSONObject().put("type",type));throw new AssertionError("Unknown command accepted");}catch(java.io.IOException expected){checks++;}}
  JSONObject range=options().put("rangeStart",2.25).put("rangeEnd",5.75);RenderRequest selected=RenderRequest.create("<html></html>",range);if(selected.frames!=210||selected.rangeStart!=2.25||selected.outputDuration!=3.5||selected.options().getDouble("rangeEnd")!=5.75)throw new AssertionError("Range/timestamp contract");checks++;
  for(Object[] pair:new Object[][]{{-1,5},{5,4},{0,15},{1,1.001},{"1",5}}){JSONObject o=options().put("rangeStart",pair[0]).put("rangeEnd",pair[1]);reject(o,true);reject(o,false);}reject(options().put("rangeStart",1),true);reject(options().put("rangeStart",1),false);
  for(int rate:new int[]{30,60})for(int duration:new int[]{1,14,300}){RenderRequest full=RenderRequest.create("<html></html>",options().put("duration",duration).put("fps",rate));if(full.frames!=duration*rate||full.rangeStart!=0||full.rangeEnd!=duration||full.options().has("rangeStart"))throw new AssertionError("Default path changed");checks++;}
  for(String command:new String[]{"openDownload","shareDownload"}){BridgeValidation.command(new JSONObject().put("type",command).put("uri","content://registered/export"));checks++;for(String uri:new String[]{"file:///private","https://outside.invalid/video"}){try{BridgeValidation.command(new JSONObject().put("type",command).put("uri",uri));throw new AssertionError("Non-content URI accepted");}catch(java.io.IOException expected){checks++;}}}
  System.out.println("PASS: "+checks+" production bridge/request schema and unchanged output-setting cases (host JSON runtime)");
 }
}
