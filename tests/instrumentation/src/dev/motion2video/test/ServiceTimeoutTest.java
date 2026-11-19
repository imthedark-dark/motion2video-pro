package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import org.json.*;import java.io.*;import java.lang.reflect.*;
/** Debug-only: invoke the real service timeout handler while frames are active. */
public final class ServiceTimeoutTest extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  Context c=getTargetContext();ClassLoader loader=c.getClassLoader();Class<?> service=loader.loadClass("dev.motion2video.app.RenderService"),request=loader.loadClass("dev.motion2video.app.RenderRequest");
  JSONObject o=new JSONObject().put("duration",10).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);
  Object job=request.getMethod("create",String.class,JSONObject.class).invoke(null,"<!doctype html><html><body style='background:#111;color:white'>Timeout test</body></html>",o);String id=(String)request.getField("id").get(job);runOnMainSync(()->{try{service.getMethod("start",Context.class,request).invoke(null,c,job);}catch(Exception e){throw new RuntimeException(e);}});
  Method latest=service.getMethod("latest",Context.class);long limit=System.currentTimeMillis()+240000;while(true){JSONObject s=(JSONObject)latest.invoke(null,c);if(id.equals(s.optString("id"))&&s.optInt("done")>0)break;if(System.currentTimeMillis()>limit)throw new Exception("No frames before timeout");Thread.sleep(1000);}
  Field f=service.getDeclaredField("instance");f.setAccessible(true);Object active=f.get(null);Method timeout=service.getMethod("onTimeout",int.class,int.class);runOnMainSync(()->{try{timeout.invoke(active,1,8192);}catch(Exception e){throw new RuntimeException(e);}});
  limit=System.currentTimeMillis()+180000;while(true){JSONObject s=(JSONObject)latest.invoke(null,c);if(s.optString("phase").equals("error")&&s.optString("message").contains("مهلت")&&!new File(c.getCacheDir(),"render-"+id+".mp4").exists()&&f.get(null)==null)break;if(System.currentTimeMillis()>limit)throw new Exception("Timeout did not cleanly stop");Thread.sleep(1000);}
  Thread.sleep(1000);JSONObject terminal=(JSONObject)latest.invoke(null,c);if(!terminal.optString("phase").equals("error"))throw new Exception("Late callback overwrote timeout error");
  out.putString("result","PASS");out.putBoolean("timeoutStopsService",true);out.putBoolean("partialOutputRemoved",true);out.putBoolean("terminalStatePreserved",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.getMessage());finish(Activity.RESULT_CANCELED,out);}}
}
