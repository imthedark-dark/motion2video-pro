package dev.motion2video.app;

import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.os.*;
import org.json.*;import java.io.*;import java.util.concurrent.CopyOnWriteArraySet;

/** Owns render lifetime, progress, wake lock and final Downloads save. */
public final class RenderService extends Service {
 public interface Listener{void changed(JSONObject state);}
 private static final CopyOnWriteArraySet<Listener> listeners=new CopyOnWriteArraySet<>();
 private static volatile RenderService instance;private static volatile String stateJson;private static volatile boolean pendingCancel,pendingRendererError;
 private static final String CHANNEL="motion_render",CANCEL="dev.motion2video.CANCEL_RENDER";private static final int NOTIFICATION=241;
 private final Handler main=new Handler(Looper.getMainLooper());private volatile boolean cancelled;private volatile String rendererError;private volatile RenderEngine engine;
 private PowerManager.WakeLock wake;private long began,lastNotice,lastPersist;private RenderRequest request;private volatile boolean alive,timeoutStopping;private int lastStartId;private Intent queued;
 public static boolean busy(JSONObject s){String phase=s.optString("phase");return phase.equals("preparing")||phase.equals("rendering")||phase.equals("saving")||phase.equals("cancelling");}
 public static JSONObject latest(Context c){
  String raw=stateJson;if(raw==null)raw=c.getSharedPreferences("render_state",MODE_PRIVATE).getString("last","{}");
  try{JSONObject s=new JSONObject(raw);boolean interrupted=busy(s);if((interrupted||s.optBoolean("cleanupPending"))&&instance==null){String id=s.optString("id"),ext=s.optString("ext");if(id.matches("[a-f0-9-]{36}")){if(ext.equals("mp4")||ext.equals("webm")){new File(c.getCacheDir(),"render-"+id+"."+ext).delete();new File(c.getCacheDir(),"render-"+id+"."+ext+".faststart").delete();}try{RenderRequest.file(c,id).delete();}catch(Exception ignored){}}s.remove("cleanupPending");s.put("phase","error");if(!s.has("message")||interrupted)s.put("message","رندر قبلی با توقف فرایند برنامه قطع شد؛ دوباره اجرا کن.");stateJson=s.toString();c.getSharedPreferences("render_state",MODE_PRIVATE).edit().putString("last",stateJson).apply();}return s;}catch(JSONException e){return new JSONObject();}
 }
 public static synchronized void recordDownload(Context c,String file,JSONObject item){try{JSONObject s=latest(c);if(s.optString("phase").equals("complete")&&file.equals(s.optString("file"))){s.put("download",item);s.remove("warning");stateJson=s.toString();c.getSharedPreferences("render_state",MODE_PRIVATE).edit().putString("last",stateJson).apply();}}catch(JSONException ignored){}}
 public static void addListener(Listener l){listeners.add(l);}public static void removeListener(Listener l){listeners.remove(l);}
 public static void start(Context c,RenderRequest request)throws Exception{RenderService running=instance;if(running!=null&&running.timeoutStopping)throw new IOException("مهلت سرویس تمام شده است؛ پس از توقف کامل دوباره اجرا کن.");request.write(c);pendingCancel=false;pendingRendererError=false;try{c.startForegroundService(new Intent(c,RenderService.class).putExtra("job",request.id));}catch(Exception e){RenderRequest.file(c,request.id).delete();throw e;}}
 public static void rendererGone(Context c){RenderService s=instance;if(s!=null&&s.request!=null&&busy(latest(c))){s.rendererError=WebViewSecurity.RENDERER_ERROR;s.cancelJob();}else{pendingCancel=true;pendingRendererError=true;}}
 public static void cancel(Context c){RenderService s=instance;if(s!=null&&s.request!=null&&busy(latest(c)))s.cancelJob();else pendingCancel=true;}
 @Override public void onCreate(){super.onCreate();instance=this;alive=true;NotificationManager n=getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel(CHANNEL,"رندر ویدیو",NotificationManager.IMPORTANCE_LOW);channel.setDescription("پیشرفت رندر Motion2Video و کنترل لغو");n.createNotificationChannel(channel);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  lastStartId=startId;
  if(timeoutStopping){discard(intent);stopSelf();return START_NOT_STICKY;}
  if(intent!=null&&CANCEL.equals(intent.getAction())){cancelJob();if(request==null)stopSelf();return START_NOT_STICKY;}
  if(request!=null){if(!busy(latest(this)))queued=intent;return START_NOT_STICKY;}
  try{
   startForegroundNow(notification("آماده‌سازی رندر…",0,0,true));
   if(intent==null)throw new IOException("درخواست رندر موجود نیست.");request=RenderRequest.read(this,intent.getStringExtra("job"));
   began=System.currentTimeMillis();rendererError=pendingRendererError?WebViewSecurity.RENDERER_ERROR:null;cancelled=pendingCancel;pendingCancel=false;pendingRendererError=false;
   PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Motion2Video:Render");wake.setReferenceCounted(false);wake.acquire(6*60*60*1000L);
   publish("preparing",0,"آماده‌سازی HTML و قلم‌ها…",null,null,null);
   new Thread(this::runJob,"motion-render").start();
  }catch(Throwable e){publish("error",0,error(e),null,null,null);finishService();}
  return START_NOT_STICKY;
 }
 private void startForegroundNow(Notification notification){if(Build.VERSION.SDK_INT>=35)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);else if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification);}
 private void runJob(){
  final RenderRequest job=request;File result=null;
  try{
   engine=new RenderEngine(this,job);result=engine.render(()->cancelled,(done,total)->publish("rendering",done,"رندر فریم‌ها",null,null,null));
   if(cancelled)throw new IOException("رندر لغو شد.");publish("saving",job.frames,"ذخیره در دانلودها…",result,null,null);
   JSONObject download=null;String warning=null;
   try{download=new DownloadRepository(this).save(result,"video/"+job.ext,job.ext,()->cancelled);}catch(Exception e){warning="ویدیو ساخته شد، اما ذخیره در دانلودها انجام نشد؛ در برنامه دوباره ذخیره کن. "+error(e);}
   if(cancelled)throw new IOException("رندر متوقف شد.");
   publish("complete",job.frames,"ویدیو آماده است",result,download,warning);
  }catch(Throwable e){if(result!=null)result.delete();publish(rendererError!=null?"error":cancelled?"cancelled":"error",0,rendererError!=null?rendererError:cancelled?"رندر لغو شد؛ فایل ناقص حذف شد.":error(e),null,null,null);}
  finally{engine=null;try{RenderRequest.file(this,job.id).delete();}catch(Exception ignored){}main.post(this::finishService);}
 }
 private static String error(Throwable t){while(!(t instanceof IOException)&&t.getCause()!=null&&t.getCause()!=t)t=t.getCause();if(t instanceof OutOfMemoryError)return "حافظهٔ کافی برای این اندازه نیست؛ اندازهٔ کوچک‌تری انتخاب کن.";return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
 private synchronized void publish(String phase,int done,String message,File file,JSONObject download,String warning){
  if(!alive||instance!=this||(timeoutStopping&&!phase.equals("error")))return;
  try{
   JSONObject s=new JSONObject().put("phase",phase).put("done",done).put("message",message).put("elapsedMs",Math.max(0,System.currentTimeMillis()-began));
   if(request!=null)s.put("id",request.id).put("total",request.frames).put("ext",request.ext).put("mime",request.mime).put("options",request.options());
   if(file!=null)s.put("file",file.getAbsolutePath()).put("size",file.length());if(download!=null)s.put("download",download);if(warning!=null)s.put("warning",warning);if(timeoutStopping)s.put("cleanupPending",true);
   final String json=s.toString();stateJson=json;long now=System.currentTimeMillis();boolean active=busy(s);
   if(!active||now-lastPersist>1000){getSharedPreferences("render_state",MODE_PRIVATE).edit().putString("last",json).apply();lastPersist=now;}
   if(!active||now-lastNotice>500){getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(message,done,request==null?0:request.frames,active));lastNotice=now;}
   main.post(()->{if(!json.equals(stateJson))return;for(Listener l:listeners)try{l.changed(new JSONObject(json));}catch(Exception ignored){}});
  }catch(Exception ignored){}
 }
 private Notification notification(String text,int done,int total,boolean active){
  Intent open=new Intent(this,StudioActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent launch=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(dev.motion2video.app.R.drawable.ic_render).setContentTitle("Motion2Video Pro").setContentText(text).setContentIntent(launch).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active).setCategory(active?Notification.CATEGORY_PROGRESS:Notification.CATEGORY_STATUS);
  if(active){b.setProgress(total,done,total<=0);PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,RenderService.class).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.addAction(new Notification.Action.Builder(null,"لغو",cancel).build());}
  return b.build();
 }
 private void cancelJob(){if(request==null||!busy(latest(this)))return;cancelled=true;RenderEngine e=engine;if(e!=null)e.cancel();JSONObject s=latest(this);publish("cancelling",s.optInt("done"),"در حال لغو رندر…",null,null,null);}
 private void finishService(){if(wake!=null&&wake.isHeld())wake.release();request=null;if(queued!=null&&alive&&!timeoutStopping){Intent next=queued;queued=null;onStartCommand(next,0,lastStartId);return;}if(alive){stopForeground(STOP_FOREGROUND_DETACH);stopSelfResult(lastStartId);}}
 private void discard(Intent intent){if(intent==null)return;try{RenderRequest.file(this,intent.getStringExtra("job")).delete();}catch(Exception ignored){}}
 @Override public void onTimeout(int startId,int type){
  // Meet Android 15's short stop deadline; never clear the worker's request or join here.
  timeoutStopping=true;cancelled=true;rendererError="مهلت پردازش پس‌زمینهٔ اندروید تمام شد؛ رندر متوقف شد. دوباره در برنامه اجرا کن.";
  RenderEngine e=engine;if(e!=null)e.cancel();
  if(request!=null&&busy(latest(this)))publish("error",0,rendererError,null,null,null);
  discard(queued);queued=null;
  if(wake!=null&&wake.isHeld())wake.release();
  stopForeground(STOP_FOREGROUND_DETACH);stopSelf();
 }
 @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);}
 @Override public void onDestroy(){alive=false;cancelled=true;RenderEngine e=engine;if(e!=null)e.cancel();if(wake!=null&&wake.isHeld())wake.release();if(instance==this)instance=null;discard(queued);queued=null;super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
