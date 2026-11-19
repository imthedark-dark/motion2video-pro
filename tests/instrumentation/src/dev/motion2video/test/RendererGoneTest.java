package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import org.json.*;import java.io.*;import java.lang.reflect.*;
/** API29+: terminate a real service WebView renderer while encoding is active. */
public final class RendererGoneTest extends Instrumentation {
 private Context target;private Class<?> service;
 private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private JSONObject state()throws Exception{return (JSONObject)service.getMethod("latest",Context.class).invoke(null,target);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  if(Build.VERSION.SDK_INT<29)throw new Exception("Requires API29+");target=getTargetContext();ClassLoader loader=target.getClassLoader();service=loader.loadClass("dev.motion2video.app.RenderService");Class<?> request=loader.loadClass("dev.motion2video.app.RenderRequest");
  String html="<!doctype html><html><head><style>body{background:#111}div{width:20px;height:20px;background:#ff3b00;animation:move 5s both}@keyframes move{to{transform:translateX(100px)}}</style></head><body><div></div></body></html>";
  JSONObject options=new JSONObject().put("duration",5).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);
  Object job=request.getMethod("create",String.class,JSONObject.class).invoke(null,html,options);String id=(String)request.getField("id").get(job);runOnMainSync(()->{try{service.getMethod("start",Context.class,request).invoke(null,target,job);}catch(Exception e){throw new RuntimeException(e);}});
  long until=System.currentTimeMillis()+240000;while(true){JSONObject s=state();if(id.equals(s.optString("id"))&&s.optInt("done")>0&&s.optString("phase").equals("rendering"))break;if(id.equals(s.optString("id"))&&s.optString("phase").equals("error"))throw new Exception("Render preparation failed");if(System.currentTimeMillis()>until)throw new Exception("No active frames");Thread.sleep(1000);}
  Field singleton=service.getDeclaredField("instance");singleton.setAccessible(true);Object engine=field(singleton.get(null),"engine"),stage=field(engine,"stage");WebView web=(WebView)field(stage,"web");boolean[] killed={false};runOnMainSync(()->{WebViewRenderProcess process=web.getWebViewRenderProcess();if(process!=null)killed[0]=process.terminate();});if(!killed[0])throw new Exception("Renderer termination unavailable");
  until=System.currentTimeMillis()+120000;while(!state().optString("phase").equals("error")){if(System.currentTimeMillis()>until)throw new Exception("Renderer loss did not fail job");Thread.sleep(1000);}
  if(!state().optString("message").contains("پردازشگر"))throw new Exception("Missing Persian renderer error");if(new File(target.getCacheDir(),"render-"+id+".mp4").exists())throw new Exception("Partial output retained");
  out.putString("result","PASS");out.putBoolean("realRendererTerminated",true);out.putBoolean("partialOutputRemoved",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.getMessage());finish(Activity.RESULT_CANCELED,out);}}
}
