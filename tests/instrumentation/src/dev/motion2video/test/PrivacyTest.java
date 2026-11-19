package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.*;import android.webkit.*;import java.lang.reflect.*;
/** Native callback contract fixture; pair with manual logcat/packet checks on a working device. */
public final class PrivacyTest extends Instrumentation {
 private Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new Exception("Missing test field");}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  Context c=getTargetContext();ApplicationInfo info=c.getPackageManager().getApplicationInfo(c.getPackageName(),PackageManager.GET_META_DATA);
  if(!info.metaData.getBoolean("android.webkit.WebView.MetricsOptOut",false))throw new Exception("Missing metrics opt-out");
  Activity activity=startActivitySync(new Intent().setClassName(c,"dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  WebView stage=(WebView)field(activity,"stage"),studio=(WebView)field(activity,"studio");
  runOnMainSync(()->{for(WebView web:new WebView[]{stage,studio}){
   WebChromeClient client=web.getWebChromeClient();if(client==null||!client.onConsoleMessage(new ConsoleMessage("PRIVACY_FIXTURE_ONLY <html>synthetic</html>","file:///private-fixture-only",1,ConsoleMessage.MessageLevel.ERROR)))throw new AssertionError("Console message was not handled");
   client.onConsoleMessage("PRIVACY_FIXTURE_ONLY",1,"file:///private-fixture-only");
  }});
  out.putString("result","PASS");out.putBoolean("consoleCallbacksHandled",true);out.putBoolean("metricsOptOutDeclared",true);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error","Native privacy fixture setup or callback failed");finish(Activity.RESULT_CANCELED,out);}}
}
