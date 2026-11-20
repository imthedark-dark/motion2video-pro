package dev.motion2video.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

final class StudioWindow {
 static WebView await(Instrumentation test,Activity activity) {
  WebView[] result=new WebView[1];
  long deadline=SystemClock.elapsedRealtime()+120000;
  do {
   test.runOnMainSync(()->result[0]=find(activity.getWindow().getDecorView()));
   if(result[0]!=null)return result[0];
   SystemClock.sleep(100);
  }while(SystemClock.elapsedRealtime()<deadline);
  throw new AssertionError("Trusted studio did not load after the native loading screen");
 }
 private static WebView find(View view){
  if(view instanceof WebView && "https://studio.motion.local/index.html".equals(((WebView)view).getUrl()))return (WebView)view;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){WebView web=find(group.getChildAt(i));if(web!=null)return web;}
  }
  return null;
 }
}
