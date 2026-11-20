package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import org.json.*;import java.io.*;import java.lang.reflect.*;
/** Debug-only native schema, hung script deadline and cancellation checks. */
public final class InputProtectionTest extends Instrumentation {
 private Class<?> service,request;private Method latest;private Context context;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private String startJob(String html)throws Exception{
  JSONObject options=new JSONObject().put("duration",1).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);
  Object job=request.getMethod("create",String.class,JSONObject.class).invoke(null,html,options);String id=(String)request.getField("id").get(job);
  runOnMainSync(()->{try{service.getMethod("start",Context.class,request).invoke(null,context,job);}catch(Exception e){throw new RuntimeException(e);}});return id;
 }
 private Object field(Object target,String name)throws Exception{if(target==null)return null;Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private void waitCapture()throws Exception{
  Field f=service.getDeclaredField("instance");f.setAccessible(true);long until=SystemClock.elapsedRealtime()+180000;
  while(SystemClock.elapsedRealtime()<until){Object engine=field(f.get(null),"engine"),stage=field(engine,"stage");if(field(stage,"pending")!=null)return;Thread.sleep(100);}
  throw new AssertionError("No pending frame before cancel");
 }
 private JSONObject waitTerminal(String id,long timeout)throws Exception{
  long until=SystemClock.elapsedRealtime()+timeout;
  while(SystemClock.elapsedRealtime()<until){JSONObject state=(JSONObject)latest.invoke(null,context);String phase=state.optString("phase");if(id.equals(state.optString("id"))&&(phase.equals("error")||phase.equals("cancelled")))return state;Thread.sleep(250);}
  throw new AssertionError("No terminal state before deadline");
 }
 private void cleaned(String id)throws Exception{
  Field instance=service.getDeclaredField("instance");instance.setAccessible(true);long until=SystemClock.elapsedRealtime()+90000;
  while(instance.get(null)!=null&&SystemClock.elapsedRealtime()<until)Thread.sleep(250);
  if(instance.get(null)!=null||new File(context.getCacheDir(),"render-"+id+".mp4").exists())throw new AssertionError("Job cleanup failed");
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  context=getTargetContext();ClassLoader loader=context.getClassLoader();service=loader.loadClass("dev.motion2video.app.RenderService");request=loader.loadClass("dev.motion2video.app.RenderRequest");latest=service.getMethod("latest",Context.class);
  Class<?> validator=loader.loadClass("dev.motion2video.app.BridgeValidation");Method command=validator.getDeclaredMethod("command",JSONObject.class);command.setAccessible(true);
  JSONObject options=new JSONObject().put("duration",1).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);
  for(Object[] change:new Object[][]{{"fps",45},{"fps","30"},{"duration",301},{"width",3840},{"bitrate",15000000},{"mime","video/unknown"},{"gpu","false"},{"fps",4294967326L}}){
   JSONObject bad=new JSONObject(options.toString()).put((String)change[0],change[1]);try{command.invoke(null,new JSONObject().put("type","render").put("html","<html></html>").put("options",bad));throw new AssertionError("Bridge accepted invalid settings");}catch(InvocationTargetException expected){}
   try{request.getMethod("create",String.class,JSONObject.class).invoke(null,"<html></html>",bad);throw new AssertionError("Request accepted invalid settings");}catch(InvocationTargetException expected){}
  }
  String stuck="<!doctype html><html><head></head><body><script>window.__m2vSeek=function(){while(true){}}</script></body></html>";
  String id=startJob(stuck);JSONObject terminal=waitTerminal(id,180000);if(!terminal.optString("phase").equals("error")||!terminal.optString("message").contains("۴۵"))throw new AssertionError("No visible deadline error");cleaned(id);
  String cancelId=startJob(stuck);waitCapture();runOnMainSync(()->{try{service.getMethod("cancel",Context.class).invoke(null,context);}catch(Exception e){throw new RuntimeException(e);}});waitTerminal(cancelId,30000);cleaned(cancelId);
  out.putString("result","PASS");out.putBoolean("invalidSettingsRejected",true);out.putBoolean("infiniteScriptStopped",true);out.putBoolean("cancelCleanedUp",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
