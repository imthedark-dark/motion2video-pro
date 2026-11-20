package dev.motion2video.test;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.lang.reflect.*;
/** Debug-only serial service acceptance. Run on a fresh test install; never a production install. */
public final class QueueExportTest extends Instrumentation {
 private Class<?> cls(String name)throws Exception{return getTargetContext().getClassLoader().loadClass("dev.motion2video.app."+name);}
 private Object invoke(String owner,String name,Class<?>[] types,Object... args)throws Exception{Method m=cls(owner).getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
 private JSONObject option(boolean badAudio)throws Exception{JSONObject o=new JSONObject().put("duration",1).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);if(badAudio)o.put("audio",new JSONObject().put("id","00000000-0000-0000-0000-000000000000").put("start",0).put("end",1).put("volume",1).put("fadeIn",0).put("fadeOut",0));return o;}
 private String phase(JSONArray items,String id){for(int i=0;i<items.length();i++)if(id.equals(items.optJSONObject(i).optString("id")))return items.optJSONObject(i).optString("phase");return "";}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();String[] ids=new String[3];try{
  if(Build.VERSION.SDK_INT<29)throw new Exception("Use manual API26-28 permission flow checklist");Context c=getTargetContext();Activity a=startActivitySync(new Intent().setClassName(c,"dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONArray entries=new JSONArray();Class<?> request=cls("RenderRequest");String html="<!doctype html><body style='margin:0;background:#0033ff'>صف رندر</body>";
  for(int i=0;i<3;i++){JSONObject options=option(i==0);Object job=request.getMethod("create",String.class,JSONObject.class).invoke(null,html,options);ids[i]=(String)request.getField("id").get(job);request.getMethod("write",Context.class).invoke(job,c);entries.put(new JSONObject().put("id",ids[i]).put("name","queue acceptance "+i).put("bytes",html.getBytes("UTF-8").length).put("total",30).put("options",options));}
  invoke("RenderQueue","enqueue",new Class[]{Context.class,JSONArray.class},c,entries);invoke("RenderService","cancelQueued",new Class[]{Context.class,String.class},c,ids[2]);invoke("RenderService","startQueue",new Class[]{Context.class},c);runOnMainSync(()->a.moveTaskToBack(true));
  long deadline=SystemClock.elapsedRealtime()+360000;JSONArray snapshot=null;while(SystemClock.elapsedRealtime()<deadline){snapshot=(JSONArray)invoke("RenderQueue","snapshot",new Class[]{Context.class},c);if(phase(snapshot,ids[1]).equals("complete")||phase(snapshot,ids[1]).equals("error"))break;Thread.sleep(500);}
  if(snapshot==null||!phase(snapshot,ids[0]).equals("error")||!phase(snapshot,ids[1]).equals("complete")||!phase(snapshot,ids[2]).equals("cancelled"))throw new Exception("FIFO failure/cancel continuation failed: "+snapshot);
  out.putString("result","PASS: failed first job, successful next background job, waiting cancellation");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}finally{for(String id:ids)if(id!=null)try{invoke("RenderService","cancelQueued",new Class[]{Context.class,String.class},getTargetContext(),id);}catch(Exception ignored){}}}
}
