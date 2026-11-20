package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import android.graphics.Bitmap;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.*;

/** Checks Activity removal/recreation and cancellation using the actual studio bridge. */
public final class BackgroundUiTest extends Instrumentation {
 private Context target;private Class<?> service;
 private Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private String js(WebView web,String script)throws Exception{CompletableFuture<String> f=new CompletableFuture<>();runOnMainSync(()->web.evaluateJavascript(script,f::complete));return f.get(120,TimeUnit.SECONDS);}
 private JSONObject state()throws Exception{return (JSONObject)service.getMethod("latest",Context.class).invoke(null,target);}
 private void note(String text){Bundle b=new Bundle();b.putString("progress",text);sendStatus(1,b);}
 private Activity open(){return startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 private WebView ready(Activity a)throws Exception{WebView web=StudioWindow.await(this,a);long limit=System.currentTimeMillis()+300000;while(!"true".equals(js(web,"!!(window.MotionStudio&&MotionStudio.state().native)"))){if(System.currentTimeMillis()>limit)throw new Exception("Studio bridge timeout");note("Waiting for studio bridge");Thread.sleep(1000);}js(web,"document.getElementById('introCurtain').hidden=true;document.querySelectorAll('.orb,.grain').forEach(e=>e.style.display='none');if(window.gsap)gsap.killTweensOf('.orb-a,.orb-b,.keyframe-dot');window.dispatchEvent(new Event('resize'));true");return web;}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  target=getTargetContext();service=target.getClassLoader().loadClass("dev.motion2video.app.RenderService");Activity first=open();WebView web=ready(first);String old=state().optString("id");
  String html="<!doctype html><html lang='fa' dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#111;color:#f3efea;text-align:center;font-family:Vazirmatn}h1{font-size:34px}#ball{position:absolute;left:20px;bottom:15px;width:25px;height:25px;background:#ff3b00;animation:move 1s linear both}@keyframes move{to{transform:translateX(100px)}}</style></head><body><h1>رندر پس‌زمینه</h1><p dir='ltr'>Motion2Video / Dark</p><div id='ball'></div></body></html>";
  js(web,"document.getElementById('code').value="+JSONObject.quote(html)+";document.getElementById('duration').value='1';document.getElementById('fps').value='30';document.getElementById('size').value='640x360';document.getElementById('bitrate').value='12000000';MotionStudio.render();true");
  long limit=System.currentTimeMillis()+180000;JSONObject s;while(true){s=state();if(!old.equals(s.optString("id"))&&s.optString("phase").equals("rendering"))break;if(s.optString("phase").equals("error")&&!old.equals(s.optString("id")))throw new Exception(s.toString());if(System.currentTimeMillis()>limit)throw new Exception("UI render did not start: "+s);Thread.sleep(1000);}
  String job=s.getString("id");runOnMainSync(first::finishAndRemoveTask);note("Studio Activity removed; service job "+job+" remains active");
  limit=System.currentTimeMillis()+300000;while(true){s=state();note("Background phase="+s.optString("phase")+" frames="+s.optInt("done"));if(s.optString("phase").equals("complete"))break;if(s.optString("phase").equals("error")||s.optString("phase").equals("cancelled"))throw new Exception(s.toString());if(System.currentTimeMillis()>limit)throw new Exception("Background completion timeout");Thread.sleep(1000);}
  if(!job.equals(s.optString("id"))||s.optJSONObject("download")==null)throw new Exception("Background result missing");String path=s.getJSONObject("download").getString("path");
  Activity second=open();web=ready(second);limit=System.currentTimeMillis()+120000;
  while(!"true".equals(js(web,"!document.getElementById('resultCard').hidden&&document.getElementById('outputVideo').readyState>=2&&!document.getElementById('savedDetails').hidden"))){if(System.currentTimeMillis()>limit)throw new Exception("Restored result did not load");Thread.sleep(1000);}
  String restored=js(web,"document.getElementById('savedPath').textContent");if(!path.equals(new JSONTokener(restored).nextValue()))throw new Exception("Restored download path differs: "+restored);note("Restored completed video and download path after Activity recreation");
  Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(OutputStream f=new FileOutputStream(new File(target.getFilesDir(),"background-ui.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,f);}screenshot.recycle();}
  int history=((JSONArray)target.getClassLoader().loadClass("dev.motion2video.app.DownloadRepository").getMethod("list").invoke(target.getClassLoader().loadClass("dev.motion2video.app.DownloadRepository").getConstructor(Context.class).newInstance(target))).length();
  js(web,"document.getElementById('duration').value='60';MotionStudio.render();true");limit=System.currentTimeMillis()+120000;
  while(job.equals(state().optString("id"))){if(System.currentTimeMillis()>limit)throw new Exception("Second job did not start");Thread.sleep(500);}
  String cancelled=state().getString("id");js(web,"document.getElementById('recordButton').click();true");limit=System.currentTimeMillis()+120000;
  while(!state().optString("phase").equals("cancelled")){if(System.currentTimeMillis()>limit)throw new Exception("Cancellation failed: "+state());Thread.sleep(1000);}
  if(new File(target.getCacheDir(),"render-"+cancelled+".mp4").exists())throw new Exception("Partial output was not deleted");
  limit=System.currentTimeMillis()+15000;while(!"true".equals(js(web,"!MotionStudio.state().busy&&!document.getElementById('recordButton').disabled"))){if(System.currentTimeMillis()>limit)throw new Exception("UI remained locked after cancel");Thread.sleep(300);}
  int after=((JSONArray)target.getClassLoader().loadClass("dev.motion2video.app.DownloadRepository").getMethod("list").invoke(target.getClassLoader().loadClass("dev.motion2video.app.DownloadRepository").getConstructor(Context.class).newInstance(target))).length();if(after!=history)throw new Exception("Cancelled job was added to Downloads");
  out.putString("result","PASS");out.putString("restoredDownloadPath",path);out.putBoolean("activityRemovedDuringRender",true);out.putBoolean("restoredVideoReady",true);out.putBoolean("cancelDeletesPartial",true);out.putBoolean("cancelUnlocksUI",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
