package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import android.graphics.Bitmap;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.*;

/** Completes recovery/cancellation checks using the persisted successful job. */
public final class RestoreCancelTest extends Instrumentation {
 private Context context;private Class<?> service;private WebView web;
 Object field(Object a,String name)throws Exception{for(Class<?> c=a.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 String js(String s)throws Exception{CompletableFuture<String> f=new CompletableFuture<>();runOnMainSync(()->web.evaluateJavascript(s,f::complete));return f.get(120,TimeUnit.SECONDS);}
 JSONObject state()throws Exception{return (JSONObject)service.getMethod("latest",Context.class).invoke(null,context);}
 void note(String s){Bundle b=new Bundle();b.putString("progress",s);sendStatus(1,b);}
 int history()throws Exception{Class<?> repo=context.getClassLoader().loadClass("dev.motion2video.app.DownloadRepository");return ((JSONArray)repo.getMethod("list").invoke(repo.getConstructor(Context.class).newInstance(context))).length();}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  context=getTargetContext();service=context.getClassLoader().loadClass("dev.motion2video.app.RenderService");JSONObject completed=state();if(!completed.optString("phase").equals("complete"))throw new Exception("Run a successful background render before this test");
  Activity a=startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));web=StudioWindow.await(this,a);long limit=System.currentTimeMillis()+300000;
  while(true){String ready=js("document.querySelectorAll('.orb,.grain').forEach(e=>e.style.display='none');if(window.gsap)gsap.killTweensOf('.orb-a,.orb-b,.keyframe-dot');if(document.getElementById('introCurtain'))document.getElementById('introCurtain').hidden=true;!!(window.MotionStudio&&MotionStudio.state().native)");if("true".equals(ready))break;if(System.currentTimeMillis()>limit)throw new Exception("Native UI bridge timeout");note("Loading studio");Thread.sleep(1000);}
  limit=System.currentTimeMillis()+120000;while(!"true".equals(js("!document.getElementById('resultCard').hidden&&document.getElementById('outputVideo').readyState>=2&&!document.getElementById('savedDetails').hidden"))){if(System.currentTimeMillis()>limit)throw new Exception("Result recovery failed");Thread.sleep(500);}
  String path=completed.getJSONObject("download").getString("path");Object actual=new JSONTokener(js("document.getElementById('savedPath').textContent")).nextValue();if(!path.equals(actual))throw new Exception("Saved path differs");
  if(!"\"640x360\"".equals(js("document.getElementById('size').value"))||!"\"30\"".equals(js("document.getElementById('fps').value")))throw new Exception("Render options were not restored");
  note("Restored playable output, saved path and selected render settings");Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(OutputStream f=new FileOutputStream(new File(context.getFilesDir(),"background-ui.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,f);}screenshot.recycle();}
  int count=history();String previous=completed.getString("id");
  js("document.getElementById('duration').value='60';MotionStudio.render();document.getElementById('recordButton').click();true");
  limit=System.currentTimeMillis()+120000;JSONObject s;while(true){s=state();if(!previous.equals(s.optString("id"))&&s.optString("phase").equals("cancelled"))break;if(!previous.equals(s.optString("id"))&&s.optString("phase").equals("error"))throw new Exception(s.toString());if(System.currentTimeMillis()>limit)throw new Exception("Immediate cancellation failed: "+s);Thread.sleep(500);}
  String cancelled=s.getString("id");if(new File(context.getCacheDir(),"render-"+cancelled+".mp4").exists())throw new Exception("Immediate cancel left partial file");
  limit=System.currentTimeMillis()+30000;while(!"true".equals(js("!MotionStudio.state().busy&&!document.getElementById('recordButton').disabled"))){if(System.currentTimeMillis()>limit)throw new Exception("Cancel left UI locked");Thread.sleep(300);}
  note("Immediate cancel completed without a partial export");previous=cancelled;
  js("MotionStudio.render();true");limit=System.currentTimeMillis()+180000;while(true){s=state();if(!previous.equals(s.optString("id"))&&s.optString("phase").equals("rendering")&&s.optInt("done")>0)break;if(s.optString("phase").equals("error"))throw new Exception(s.toString());if(System.currentTimeMillis()>limit)throw new Exception("Second render did not produce frames: "+s);Thread.sleep(1000);}
  cancelled=s.getString("id");final Intent cancel=new Intent().setClassName(context,"dev.motion2video.app.RenderService").setAction("dev.motion2video.CANCEL_RENDER");runOnMainSync(()->context.startService(cancel));
  limit=System.currentTimeMillis()+120000;while(!state().optString("phase").equals("cancelled")){if(System.currentTimeMillis()>limit)throw new Exception("Notification cancel failed");Thread.sleep(1000);}
  if(new File(context.getCacheDir(),"render-"+cancelled+".mp4").exists())throw new Exception("Cancel left partial output");if(history()!=count)throw new Exception("Cancelled job was added to downloads");
  limit=System.currentTimeMillis()+30000;while(!"true".equals(js("!MotionStudio.state().busy&&!document.getElementById('recordButton').disabled"))){if(System.currentTimeMillis()>limit)throw new Exception("Final UI still locked");Thread.sleep(300);}
  out.putString("result","PASS");out.putBoolean("restoredVideoAndPath",true);out.putBoolean("restoredRenderSettings",true);out.putBoolean("immediateCancel",true);out.putBoolean("notificationCancel",true);out.putBoolean("partialOutputsDeleted",true);out.putBoolean("noCancelledDownload",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
