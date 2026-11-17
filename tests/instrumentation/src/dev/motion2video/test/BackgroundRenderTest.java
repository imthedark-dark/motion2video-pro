package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.*;

/** Starts the service without a StudioActivity and presses Home during the job. */
public final class BackgroundRenderTest extends Instrumentation {
 private Class<?> service;private Context target;
 private JSONObject state()throws Exception{return (JSONObject)service.getMethod("latest",Context.class).invoke(null,target);}
 private void note(String message){Bundle b=new Bundle();b.putString("progress",message);sendStatus(1,b);}
 private void shell(String cmd)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(cmd);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[8192];while(in.read(b)!=-1){}}}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  target=getTargetContext();ClassLoader loader=target.getClassLoader();Class<?> request=loader.loadClass("dev.motion2video.app.RenderRequest");service=loader.loadClass("dev.motion2video.app.RenderService");
  String html="<!doctype html><html lang='fa' dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;background:#111;color:#f3efea;font-family:Vazirmatn;text-align:center}h1{font-size:34px;margin:28px 0 10px}.ar{font-family:Amiri;font-weight:700;font-size:26px}p{font-family:Inter;font-size:16px}#box{position:absolute;left:20px;bottom:15px;width:25px;height:25px;background:#ff3b00;animation:move 1s linear both}@keyframes move{from{transform:translateX(0)}to{transform:translateX(100px)}}</style></head><body><h1>سلام فارسی</h1><div class='ar'>إِنَّ مَعَ الْعُسْرِ يُسْرًا</div><p dir='ltr'>Dark — Background Render</p><div id='box'></div></body></html>";
  JSONObject options=new JSONObject().put("duration",1).put("fps",30).put("width",640).put("height",360).put("bitrate",12000000).put("mime","video/avc").put("gpu",false);
  Object job=request.getMethod("create",String.class,JSONObject.class).invoke(null,html,options);String id=(String)request.getField("id").get(job);
  runOnMainSync(()->{try{service.getMethod("start",Context.class,request).invoke(null,target,job);}catch(Exception e){throw new RuntimeException(e);}});
  boolean home=false;long limit=System.currentTimeMillis()+300000;JSONObject s;
  while(true){s=state();note(s.toString());if(id.equals(s.optString("id"))){String phase=s.optString("phase");if(!home&&phase.equals("rendering")){shell("input keyevent KEYCODE_HOME");home=true;note("Home pressed; Activity UI is not required by the renderer.");}if(phase.equals("complete"))break;if(phase.equals("error")||phase.equals("cancelled"))throw new Exception(s.toString());}if(System.currentTimeMillis()>limit)throw new Exception("Render timed out: "+s);Thread.sleep(1000);}
  if(!home)throw new Exception("Did not test Home transition");File f=new File(s.getString("file"));if(!f.isFile())throw new Exception("Missing output");
  File copy=new File(target.getFilesDir(),"background-render.mp4");try(InputStream in=new FileInputStream(f);OutputStream out=new FileOutputStream(copy)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  JSONObject download=s.optJSONObject("download");if(download==null)throw new Exception("Downloads save failed: "+s);
  android.net.Uri uri=android.net.Uri.parse(download.getString("uri"));long bytes=0;try(InputStream in=target.getContentResolver().openInputStream(uri)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)bytes+=n;}if(bytes!=f.length())throw new Exception("Downloads content size differs");
  if(!download.getString("path").startsWith("Download/Motion2Video/"))throw new Exception("Wrong download path");
  result.putString("result","PASS");result.putString("state",s.toString());result.putString("file",copy.getAbsolutePath());result.putBoolean("homeDuringRender",home);result.putLong("downloadBytes",bytes);finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("result","FAIL");result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
