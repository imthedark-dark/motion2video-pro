package dev.motion2video.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;

/** Fixed milestones and main-queue lateness. No page, file, or device identifiers. */
final class StartupProfile {
 enum Event { ACTIVITY, FIRST_DRAW, PROVIDER_BEGIN, PROVIDER_END, INPUTS_READY,
              STUDIO_BEGIN, STUDIO_CONSTRUCTED, STAGE_BEGIN, STAGE_READY, LOAD_QUEUED, BRIDGE, READY }
 private final boolean enabled;
 private final Handler main=new Handler(Looper.getMainLooper());
 private long due,maxDelay;
 private boolean stopped;
 private final Runnable sample=new Runnable(){
  @Override public void run(){
   measureDelay();
   scheduleSample();
  }
 };
 StartupProfile(Context context){
  enabled=(context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
  if(enabled)scheduleSample();
 }
 private void scheduleSample(){
  due=SystemClock.uptimeMillis()+50;
  main.postAtTime(sample,due);
 }
 private void measureDelay(){maxDelay=Math.max(maxDelay,SystemClock.uptimeMillis()-due);}
 void mark(Event event){
  if(!enabled)return;
  long elapsed=SystemClock.elapsedRealtime()-Process.getStartElapsedRealtime();
  boolean ui=Looper.myLooper()==Looper.getMainLooper();
  Log.d("M2VStartup",event.name()+" elapsedMs="+elapsed+" main="+ui+" cpuMs="+SystemClock.currentThreadTimeMillis()
   +(event==Event.READY?" maxQueueDelayMs="+maxDelay:""));
 }
 void awaitControls(WebView studio){
  if(!enabled||stopped)return;
  studio.evaluateJavascript("document.documentElement.dataset.nativeReady==='true'",value->{
   if(stopped)return;
   if(!"true".equals(value)){
    main.postDelayed(()->awaitControls(studio),100);
    return;
   }
   measureDelay();
   mark(Event.READY);
   stop();
  });
 }
 void stop(){stopped=true;if(enabled)main.removeCallbacksAndMessages(null);}
}
