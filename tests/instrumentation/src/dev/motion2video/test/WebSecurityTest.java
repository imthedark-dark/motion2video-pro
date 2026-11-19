package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.webkit.*;import org.json.*;import java.lang.reflect.*;import java.util.concurrent.*;

/** Device-only checks of actual settings, asset interception and native command schema. */
public final class WebSecurityTest extends Instrumentation {
 private Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  Context target=getTargetContext();ClassLoader loader=target.getClassLoader();Class<?> validator=loader.loadClass("dev.motion2video.app.BridgeValidation");Method command=validator.getDeclaredMethod("command",JSONObject.class);command.setAccessible(true);
  require("hello".equals(command.invoke(null,new JSONObject("{\"type\":\"hello\"}"))),"Valid command rejected");
  for(String raw:new String[]{"{\"type\":\"undeclaredMethod\"}","{\"type\":\"hello\",\"extra\":1}","{\"type\":\"openDownload\",\"uri\":\"file:///data/private\"}","{\"type\":\"onlineResources\",\"enabled\":\"true\"}","{\"type\":7}"}){
   try{command.invoke(null,new JSONObject(raw));throw new AssertionError("Invalid bridge command accepted");}catch(InvocationTargetException expected){require(expected.getCause() instanceof Exception,"Unexpected validation failure");}
  }
  Class<?> assets=loader.loadClass("dev.motion2video.app.StageAssets");Method response=assets.getDeclaredMethod("response",Context.class,Uri.class,boolean.class);response.setAccessible(true);
  for(String url:new String[]{"https://security-test.invalid/fetch","https://studio.motion.local/index.html","file:///data/data/private","content://media/external/downloads/1","https://motion.local/fonts/../studio.html","https://motion.local/fonts/unknown.ttf"}){
   WebResourceResponse r=(WebResourceResponse)response.invoke(null,target,Uri.parse(url),false);require(r!=null&&r.getStatusCode()==403,"Offline resource escaped interception");
  }
  WebResourceResponse bundled=(WebResourceResponse)response.invoke(null,target,Uri.parse("https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.5/gsap.min.js"),false);require(bundled.getData().read()!=-1,"Bundled engine unavailable offline");bundled.getData().close();
  require(response.invoke(null,target,Uri.parse("https://security-test.invalid/fetch"),true)==null,"Explicit HTTPS opt-in unavailable");
  Activity a=startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));WebView stage=(WebView)field(a,"stage"),studio=(WebView)field(a,"studio");
  runOnMainSync(()->{for(WebView web:new WebView[]{stage,studio}){WebSettings s=web.getSettings();require(!s.getAllowFileAccess()&&!s.getAllowContentAccess()&&!s.getAllowFileAccessFromFileURLs()&&!s.getAllowUniversalAccessFromFileURLs(),"Unsafe file settings");require(s.getSafeBrowsingEnabled()&&s.getBlockNetworkLoads(),"Safety/network defaults missing");}});
  ServiceWorkerWebSettings sw=ServiceWorkerController.getInstance().getServiceWorkerWebSettings();require(sw.getBlockNetworkLoads()&&!sw.getAllowFileAccess()&&!sw.getAllowContentAccess(),"Service-worker network escape available");
  out.putString("result","PASS");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,out);}}
}
