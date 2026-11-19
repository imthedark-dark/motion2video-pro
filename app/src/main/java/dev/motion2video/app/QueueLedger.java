package dev.motion2video.app;
import org.json.*;import java.io.IOException;import java.util.*;
/** Small deterministic FIFO state machine; no Android, files, threads or rendering. */
public final class QueueLedger {
 public static final String ERROR="صف پر یا نامعتبر است؛ حداکثر ۸ کار و مجموع ۶۰ مگابایت HTML مجاز است.";
 private JSONArray jobs;
 private static final Set<String> PHASES=new HashSet<>(Arrays.asList("waiting","paused","preparing","rendering","saving","cancelling","complete","error","cancelled"));
 public QueueLedger(JSONArray stored)throws Exception{if(stored.length()>24)throw new IOException(ERROR);jobs=new JSONArray(stored.toString());Set<String> ids=new HashSet<>();for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(!AudioPolicy.id(j.getString("id"))||!ids.add(j.getString("id"))||!PHASES.contains(j.getString("phase"))||j.getString("name").length()>240||j.getLong("bytes")<1||j.getLong("bytes")>ImportPolicy.MAX_BYTES||j.getInt("total")<1||j.getInt("total")>18000)throw new IOException(ERROR);}checkCapacity(0,0);}
 private static boolean terminal(String p){return p.equals("complete")||p.equals("error")||p.equals("cancelled");}
 private void checkCapacity(int count,long bytes)throws Exception{for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(!terminal(j.getString("phase"))){count++;bytes+=j.getLong("bytes");}}if(count>8||bytes>60L*1024*1024)throw new IOException(ERROR);}
 public void add(String id,String name,long bytes,int total,JSONObject options)throws Exception{
  if(!AudioPolicy.id(id)||name==null||name.length()>240||bytes<1||bytes>ImportPolicy.MAX_BYTES||total<1||total>18000||find(id)!=null)throw new IOException(ERROR);checkCapacity(1,bytes);
  jobs.put(new JSONObject().put("id",id).put("name",name).put("bytes",bytes).put("total",total).put("done",0).put("phase","waiting").put("options",options));
  while(jobs.length()>24){boolean removed=false;for(int i=0;i<jobs.length();i++)if(terminal(jobs.getJSONObject(i).getString("phase"))){jobs.remove(i);removed=true;break;}if(!removed)throw new IOException(ERROR);}
 }
 public JSONObject find(String id){for(int i=0;i<jobs.length();i++){JSONObject j=jobs.optJSONObject(i);if(j!=null&&id.equals(j.optString("id")))return j;}return null;}
 public void saved(String id,boolean saved)throws Exception{JSONObject j=find(id);if(j!=null)j.put("saved",saved);}
 public void options(String id,JSONObject options)throws Exception{JSONObject j=find(id);if(j!=null)j.put("options",options);}
 public boolean waiting(){for(int i=0;i<jobs.length();i++)if(jobs.optJSONObject(i).optString("phase").equals("waiting"))return true;return false;}
 public String claim()throws Exception{for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.getString("phase").equals("waiting")){j.put("phase","preparing");return j.getString("id");}}return null;}
 public void update(String id,String phase,int done,String message)throws Exception{JSONObject j=find(id);if(j==null)return;if(!PHASES.contains(phase))throw new IOException(ERROR);j.put("phase",phase).put("done",Math.max(0,Math.min(j.getInt("total"),done))).put("message",message==null?"":message);}
 public boolean cancelWaiting(String id)throws Exception{JSONObject j=find(id);if(j==null||!(j.optString("phase").equals("waiting")||j.optString("phase").equals("paused")))return false;update(id,"cancelled",0,"");return true;}
 public void pause()throws Exception{for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.optString("phase").equals("waiting"))j.put("phase","paused");}}
 public void recover()throws Exception{for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);String p=j.getString("phase");if(p.equals("waiting"))j.put("phase","paused");else if(!terminal(p)&&!p.equals("paused"))update(j.getString("id"),"error",j.optInt("done"),"رندر قبلی با توقف فرایند برنامه قطع شد؛ دوباره اجرا کن.");}}
 public JSONArray snapshot()throws JSONException{return new JSONArray(jobs.toString());}
}
