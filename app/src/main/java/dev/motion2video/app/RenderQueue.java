package dev.motion2video.app;
import android.content.Context;import org.json.*;import java.io.*;
/** Private bounded queue metadata; large HTML remains in validated UUID request files. */
final class RenderQueue {
 private static QueueLedger ledger;
 private static QueueLedger load(Context c)throws Exception{
  if(ledger!=null)return ledger;String raw=c.getSharedPreferences("render_queue",0).getString("items","[]");
  if(raw.length()>256000)raw="[]";try{ledger=new QueueLedger(new JSONArray(raw));}catch(Exception bad){ledger=new QueueLedger(new JSONArray());}
  JSONArray old=ledger.snapshot();ledger.recover();persist(c);
  for(int i=0;i<old.length();i++){JSONObject job=old.getJSONObject(i);String phase=job.optString("phase");if(phase.equals("preparing")||phase.equals("rendering")||phase.equals("saving")||phase.equals("cancelling"))cleanup(c,job.getString("id"));}
  return ledger;
 }
 private static void persist(Context c)throws Exception{if(!c.getSharedPreferences("render_queue",0).edit().putString("items",ledger.snapshot().toString()).commit())throw new IOException(QueueLedger.ERROR);}
 private static void persistAsync(Context c)throws Exception{c.getSharedPreferences("render_queue",0).edit().putString("items",ledger.snapshot().toString()).apply();}
 static void cleanup(Context c,String id){if(!AudioPolicy.id(id))return;try{RenderRequest.file(c,id).delete();}catch(Exception ignored){}for(String ext:new String[]{"mp4","webm","mp4.faststart"})new File(c.getCacheDir(),"render-"+id+"."+ext).delete();for(String ext:new String[]{"pcm","m4a","mp4","mp4.faststart"})new File(c.getCacheDir(),"audio-"+id+"."+ext).delete();}
 static synchronized JSONArray snapshot(Context c){try{JSONArray jobs=load(c).snapshot();for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);if(job.optString("phase").equals("error"))job.put("message",PublicErrors.sanitize(job.optString("message")));}return jobs;}catch(Exception e){return new JSONArray();}}
 static synchronized boolean waiting(Context c){try{return load(c).waiting();}catch(Exception e){return false;}}
 static synchronized boolean pending(Context c){JSONArray jobs=snapshot(c);for(int i=0;i<jobs.length();i++){String p=jobs.optJSONObject(i).optString("phase");if(!p.equals("complete")&&!p.equals("cancelled")&&!p.equals("error"))return true;}return false;}
 static synchronized String claim(Context c)throws Exception{QueueLedger previous=load(c),next=new QueueLedger(previous.snapshot());String id=next.claim();ledger=next;try{persist(c);}catch(Exception failure){ledger=previous;throw failure;}return id;}
 static synchronized boolean contains(Context c,String id){try{return load(c).find(id)!=null;}catch(Exception e){return false;}}
 static synchronized void enqueue(Context c,JSONArray prepared)throws Exception{
  QueueLedger next=new QueueLedger(load(c).snapshot());for(int i=0;i<prepared.length();i++){JSONObject p=prepared.getJSONObject(i);next.add(p.getString("id"),p.getString("name"),p.getLong("bytes"),p.getInt("total"),p.getJSONObject("options"));}
  JSONArray old=ledger.snapshot();QueueLedger previous=ledger;ledger=next;try{persist(c);}catch(Exception e){ledger=previous;throw e;}for(int i=0;i<old.length();i++){String id=old.getJSONObject(i).getString("id");if(next.find(id)==null)cleanup(c,id);}
 }
 static synchronized void update(Context c,String id,String phase,int done,String message,boolean save){try{load(c).update(id,phase,done,phase.equals("error")?PublicErrors.sanitize(message):"");if(save)persistAsync(c);}catch(Exception ignored){}}
 static synchronized JSONObject item(Context c,String id)throws Exception{JSONObject item=load(c).find(id);if(item==null)throw new IOException(QueueLedger.ERROR);return new JSONObject(item.toString());}
 static synchronized void saved(Context c,String id,boolean saved){try{load(c).saved(id,saved);persistAsync(c);}catch(Exception ignored){}}
 static synchronized void trimCache(Context c){try{JSONArray jobs=load(c).snapshot();long total=0;for(int i=0;i<jobs.length();i++){String id=jobs.getJSONObject(i).getString("id");for(String ext:new String[]{"mp4","webm"})total+=new File(c.getCacheDir(),"render-"+id+"."+ext).length();}for(int i=0;i<jobs.length()-1&&total>1024L*1024*1024;i++){JSONObject job=jobs.getJSONObject(i);if(!job.optString("phase").equals("complete")||!job.optBoolean("saved",false))continue;String id=job.getString("id");for(String ext:new String[]{"mp4","webm"}){File f=new File(c.getCacheDir(),"render-"+id+"."+ext);long size=f.length();if(f.delete())total-=size;}}}catch(Exception ignored){}}
 static synchronized boolean cancelWaiting(Context c,String id)throws Exception{boolean cancelled=load(c).cancelWaiting(id);if(cancelled){persist(c);cleanup(c,id);}return cancelled;}
 static synchronized void pause(Context c){if(ledger==null)return;try{ledger.pause();persistAsync(c);}catch(Exception ignored){}}
 static synchronized void resume(Context c,boolean online)throws Exception{
  JSONArray jobs=load(c).snapshot();for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(!j.optString("phase").equals("paused"))continue;String id=j.getString("id");try{RenderRequest previous=RenderRequest.read(c,id);RenderRequest updated=previous.withOnlineResources(previous.onlineResources&&online);updated.write(c);ledger.update(id,"waiting",0,"");ledger.options(id,updated.options());}catch(Exception e){ledger.update(id,"error",0,PublicErrors.message(e));cleanup(c,id);}}
  persist(c);
 }
}
