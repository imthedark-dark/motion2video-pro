package dev.motion2video.app;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small Intent + private request file avoids Binder's transaction-size limit. */
public final class RenderRequest {
 public final String id,html,mime,ext;public final int width,height,fps,bitrate,frames;public final double duration;public final boolean gpu,onlineResources;
 private RenderRequest(String id,String html,JSONObject o)throws Exception{
  if(!id.matches("[a-f0-9-]{36}"))throw new IOException("شناسهٔ رندر معتبر نیست.");
  ImportPolicy.validate(html);
  for(String key:new String[]{"duration","fps","width","height","bitrate"}){Object value=o.get(key);if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||(!key.equals("duration")&&(((Number)value).doubleValue()!=Math.rint(((Number)value).doubleValue())||((Number)value).doubleValue()<1||((Number)value).doubleValue()>20000000)))throw new IOException("تنظیمات عددی معتبر نیست.");}
  if(!(o.get("mime") instanceof String)||(o.has("gpu")&&!(o.get("gpu") instanceof Boolean))||(o.has("onlineResources")&&!(o.get("onlineResources") instanceof Boolean)))throw new IOException("نوع تنظیمات معتبر نیست.");
  this.id=id;this.html=html;duration=o.getDouble("duration");fps=o.getInt("fps");width=o.getInt("width");height=o.getInt("height");bitrate=o.getInt("bitrate");mime=o.getString("mime");gpu=o.optBoolean("gpu",false);onlineResources=o.optBoolean("onlineResources",false);
  if(html.trim().isEmpty()||html.getBytes(StandardCharsets.UTF_8).length>ImportPolicy.MAX_BYTES||!Double.isFinite(duration)||duration<1||duration>300||(fps!=30&&fps!=60)||!Arrays.asList(CodecSupport.MIME).contains(mime)||(bitrate!=12000000&&bitrate!=16000000&&bitrate!=20000000))throw new IOException("کد یا تنظیمات رندر معتبر نیست.");
  if(!((width==640&&height==360)||(width==1280&&height==720)||(width==1920&&height==1080)||(width==1080&&height==1920)))throw new IOException("اندازهٔ خروجی معتبر نیست.");
  frames=(int)Math.round(duration*fps);ext=CodecSupport.webm(mime)?"webm":"mp4";
 }
 public static RenderRequest create(String html,JSONObject o)throws Exception{return new RenderRequest(UUID.randomUUID().toString(),html,o);}
 public static File file(Context c,String id)throws IOException{if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid job ID");File dir=new File(c.getCacheDir(),"render-jobs");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create job directory");return new File(dir,id+".json");}
 public void write(Context c)throws Exception{JSONObject o=options();o.put("id",id);o.put("html",html);try(OutputStream out=new FileOutputStream(file(c,id))){out.write(o.toString().getBytes(StandardCharsets.UTF_8));}}
 public static RenderRequest read(Context c,String id)throws Exception{try(InputStream in=new FileInputStream(file(c,id))){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(b.size()+n>ImportPolicy.MAX_JSON_BYTES)throw new IOException("Request too large");b.write(buf,0,n);}JSONObject o=new JSONObject(b.toString("UTF-8"));return new RenderRequest(id,o.getString("html"),o);}}
 public JSONObject options()throws JSONException{return new JSONObject().put("duration",duration).put("fps",fps).put("width",width).put("height",height).put("bitrate",bitrate).put("mime",mime).put("gpu",gpu).put("onlineResources",onlineResources);}
}
