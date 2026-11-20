package dev.motion2video.test;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.*;
public class StudioTest extends Instrumentation {
 Object field(Object a,String name)throws Exception{Class<?> c=a.getClass();while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
 String js(WebView web,String script)throws Exception{CompletableFuture<String> f=new CompletableFuture<>();runOnMainSync(()->web.evaluateJavascript(script,f::complete));return f.get(90,TimeUnit.SECONDS);}
 void note(String value){Bundle b=new Bundle();b.putString("progress",value);sendStatus(1,b);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  Activity a=startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.StudioActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));StudioWindow.await(this,a);
  WebView studio=(WebView)field(a,"studio");long deadline=System.currentTimeMillis()+240000;
  while(!"true".equals(js(studio,"!!(window.MotionStudio&&MotionStudio.state().native)"))){if(System.currentTimeMillis()>deadline)throw new Exception("Native port failed to connect");note("waiting for studio bridge");Thread.sleep(1000);}
  note("native bridge connected");WebView stage=(WebView)field(a,"stage");note("engine="+js(stage,"JSON.stringify({ua:navigator.userAgent,documentAnimations:typeof document.getAnimations,elementAnimations:typeof document.body.getAnimations})"));
  js(studio,"document.getElementById('introCurtain').hidden=true;document.querySelectorAll('.orb,.grain').forEach(e=>e.style.display='none');if(window.gsap)gsap.killTweensOf('.orb-a,.orb-b,.keyframe-dot');window.dispatchEvent(new Event('resize'));true");
  String html="<!doctype html><html lang='fa' dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;background:#111;color:#f3efea;font-family:Vazirmatn;text-align:center}h1{font-size:34px;margin:25px 0 10px}.ar{font-family:Amiri;font-weight:700;font-size:26px;margin:5px}p{font-family:Inter;font-size:16px}#box{position:absolute;left:20px;bottom:15px;width:25px;height:25px;background:#ff3b00;animation:move 1s linear both}@keyframes move{from{transform:translateX(0)}to{transform:translateX(100px)}}</style></head><body><h1>سلام فارسی</h1><div class='ar'>إِنَّ مَعَ الْعُسْرِ يُسْرًا</div><p dir='ltr'>English 123 — Motion2Video</p><div id='box'></div></body></html>";
  js(studio,"document.getElementById('code').value="+org.json.JSONObject.quote(html)+";document.getElementById('size').value='640x360';document.getElementById('duration').value='1';document.getElementById('fps').value='30';document.getElementById('bitrate').value='12000000';MotionStudio.preview();true");
  Thread.sleep(2000);
  js(studio,"MotionStudio.render();true");
  deadline=System.currentTimeMillis()+240000;boolean started=false;
  while(System.currentTimeMillis()<deadline){boolean running=(Boolean)field(a,"working");started|=running;File f=(File)field(a,"result");if(started&&!running&&f!=null&&f.isFile())break;String state=js(studio,"JSON.stringify({s:MotionStudio.state(),message:document.getElementById('message').textContent})");note("working="+running+" "+state);Thread.sleep(1500);}
  File result=(File)field(a,"result");if(result==null||!result.isFile()||(Boolean)field(a,"working"))throw new Exception("No native result: "+js(studio,"document.getElementById('message').textContent"));
  File dest=new File(getTargetContext().getFilesDir(),"studio-render.mp4");try(InputStream in=new FileInputStream(result);OutputStream stream=new FileOutputStream(dest)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)stream.write(bytes,0,n);}
  note("lastCSS="+js(stage,"JSON.stringify({transform:getComputedStyle(document.getElementById(\"box\")).transform,clock:typeof window.__m2vSeek})"));Thread.sleep(1500);String visible=js(studio,"!document.getElementById('resultCard').hidden");if(!"true".equals(visible))throw new Exception("Native result not delivered to studio UI");
  js(studio,"window.__rangeTest=null;fetch(document.getElementById('outputVideo').src,{headers:{Range:'bytes=0-127'}}).then(async r=>window.__rangeTest={status:r.status,bytes:(await r.arrayBuffer()).byteLength});true");
  Thread.sleep(2000);String range=js(studio,"JSON.stringify(window.__rangeTest)");if(!range.contains("206")||!range.contains("128"))throw new Exception("Export range failed: "+range);
  out.putString("result","PASS");out.putString("range",range);out.putString("file",dest.getAbsolutePath());out.putString("playback",js(studio,"JSON.stringify({readyState:document.getElementById('outputVideo').readyState,width:document.getElementById('outputVideo').videoWidth})"));finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
