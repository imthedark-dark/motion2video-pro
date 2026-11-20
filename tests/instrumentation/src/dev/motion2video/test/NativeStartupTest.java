package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import java.lang.reflect.*;import java.util.concurrent.*;
/** Native precompiled CSS and bridge smoke test; no successful-job prerequisite. */
public final class NativeStartupTest extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  long began=System.currentTimeMillis();Activity a=startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));WebView web=StudioWindow.await(this,a);
  while(true){CompletableFuture<String> value=new CompletableFuture<>();runOnMainSync(()->web.evaluateJavascript("!!(window.MotionStudio&&MotionStudio.state().native&&Array.from(document.querySelectorAll('style')).some(s=>s.textContent.includes('tailwindcss v3.4.17'))&&document.documentElement.scrollWidth<=innerWidth)",value::complete));if("true".equals(value.get(90,TimeUnit.SECONDS)))break;if(System.currentTimeMillis()-began>240000)throw new Exception("Native UI/CSS startup timeout");Thread.sleep(500);}
  out.putString("result","PASS");out.putLong("startupMs",System.currentTimeMillis()-began);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
