package dev.motion2video.test;
import android.app.*;import android.os.*;import android.widget.*;import android.content.*;import java.io.*;import java.lang.reflect.*;
public class RenderTest extends Instrumentation {
 Object field(Object a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  Activity a=startActivitySync(new Intent().setClassName("dev.motion2video.app","dev.motion2video.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  runOnMainSync(()->{try{((EditText)field(a,"editor")).setText("<!doctype html><html lang='fa' dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;background:#111;color:#f3efea;font-family:Vazirmatn;text-align:center}h1{font-size:34px;margin:25px 0 10px}.ar{font-family:Amiri;font-size:26px;margin:5px}p{font-family:Inter;font-size:16px}#box{position:absolute;left:20px;bottom:15px;width:25px;height:25px;background:#ff3b00;animation:move 1s linear both}@keyframes move{from{transform:translateX(0)}to{transform:translateX(100px)}}</style></head><body><h1>سلام فارسی</h1><div class='ar'>إِنَّ مَعَ الْعُسْرِ يُسْرًا</div><p dir='ltr'>English 123 — Motion2Video</p><div id='box'></div></body></html>");((EditText)field(a,"duration")).setText("1");((Spinner)field(a,"sizeChoice")).setSelection(0);((Spinner)field(a,"fps")).setSelection(0);((Spinner)field(a,"quality")).setSelection(0);((TextView)field(a,"render")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
  long deadline=System.currentTimeMillis()+180000;boolean working=true;while(working&&System.currentTimeMillis()<deadline){Thread.sleep(1000);working=(Boolean)field(a,"working");Bundle progress=new Bundle();progress.putString("progress","rendering="+working);sendStatus(1,progress);}
  File result=(File)field(a,"result");String status=((TextView)field(a,"status")).getText().toString();if(working||result==null||!result.isFile())throw new Exception("No output: "+status);
  File dest=new File(getTargetContext().getFilesDir(),"instrumentation-render.mp4");try(InputStream in=new FileInputStream(result);OutputStream stream=new FileOutputStream(dest)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)stream.write(bytes,0,n);}
  out.putString("result","PASS");out.putString("file",dest.getAbsolutePath());out.putString("status",status);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
