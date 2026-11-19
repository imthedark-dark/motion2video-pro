package dev.motion2video.app;
import android.content.*;import android.net.Uri;import android.database.Cursor;import android.provider.OpenableColumns;import android.media.*;import android.os.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Private immutable assets; bridge and render requests carry UUIDs, never provider URIs/paths. */
final class AudioAssets {
 private static File dir(Context c)throws IOException{File d=new File(c.getCacheDir(),"audio-assets");if(!d.isDirectory()&&!d.mkdirs())throw new IOException(AudioPolicy.ERROR);return d;}
 static File file(Context c,String id)throws IOException{if(!AudioPolicy.id(id))throw new IOException(AudioOptions.ERROR);return new File(dir(c),id+".media");}
 static synchronized JSONObject importFile(Context c,Uri uri)throws Exception{
  if(uri==null||!"content".equals(uri.getScheme()))throw new IOException(AudioPolicy.ERROR);
  ContentResolver r=c.getContentResolver();CancellationSignal signal=new CancellationSignal();java.util.concurrent.atomic.AtomicReference<InputStream> stream=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.ScheduledExecutorService timeout=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
  timeout.schedule(()->{signal.cancel();InputStream in=stream.get();if(in!=null)try{in.close();}catch(IOException ignored){}},60,java.util.concurrent.TimeUnit.SECONDS);
  try{String name=null;long size=-1;
  try(Cursor cursor=r.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null,signal)){if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);if(!cursor.isNull(1))size=cursor.getLong(1);}}
  AudioPolicy.metadata(name,r.getType(uri),size);File d=dir(c);long total=0;int count=0;File[] files=d.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".media")){total+=f.length();count++;}
  if(count>=AudioPolicy.MAX_ASSETS||total+(size<0?AudioPolicy.MAX_BYTES:size)>AudioPolicy.MAX_TOTAL)throw new IOException("حافظهٔ صوت‌های موقت پر است؛ ابتدا آن‌ها را پاک کن.");
  String id=UUID.randomUUID().toString();File file=file(c,id);boolean success=false;long began=SystemClock.elapsedRealtime();
  try{
   if(new StatFs(d.getAbsolutePath()).getAvailableBytes()<Math.max(size,AudioPolicy.MAX_BYTES)+32L*1024*1024)throw new IOException("فضای خالی برای پردازش صدا کافی نیست.");
   try(android.content.res.AssetFileDescriptor fd=r.openAssetFileDescriptor(uri,"r",signal);InputStream in=fd==null?null:fd.createInputStream();OutputStream out=new FileOutputStream(file)){stream.set(in);if(signal.isCanceled())throw new IOException(AudioPolicy.ERROR);if(in==null)throw new IOException(AudioPolicy.ERROR);byte[] buffer=new byte[32768];long copied=0;int n;while((n=in.read(buffer))!=-1){copied+=n;if(copied>AudioPolicy.MAX_BYTES||total+copied>AudioPolicy.MAX_TOTAL||SystemClock.elapsedRealtime()-began>60000)throw new IOException(AudioPolicy.ERROR);out.write(buffer,0,n);}}
   JSONObject info=inspect(file);info.put("id",id).put("name",name).put("size",file.length());try(OutputStream out=new FileOutputStream(new File(d,id+".json"))){out.write(info.toString().getBytes(StandardCharsets.UTF_8));}success=true;return info;
  }finally{if(!success){file.delete();new File(d,id+".json").delete();}}
  }finally{timeout.shutdownNow();}
 }
 private static final java.util.concurrent.ExecutorService PARSER=new java.util.concurrent.ThreadPoolExecutor(0,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>());
 static JSONObject inspect(File file)throws Exception{
  java.util.concurrent.Future<JSONObject> future;
  try{future=PARSER.submit(()->inspectLocal(file));}catch(java.util.concurrent.RejectedExecutionException busy){throw new IOException(AudioPolicy.ERROR);}
  try{return future.get(45,java.util.concurrent.TimeUnit.SECONDS);}finally{future.cancel(true);}
 }
 private static JSONObject inspectLocal(File file)throws Exception{
  if(!file.isFile()||file.length()<1||file.length()>AudioPolicy.MAX_BYTES)throw new IOException(AudioPolicy.ERROR);MediaExtractor e=new MediaExtractor();
  try{e.setDataSource(file.getAbsolutePath());for(int i=0;i<e.getTrackCount();i++){MediaFormat f=e.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){
    long us=f.containsKey(MediaFormat.KEY_DURATION)?f.getLong(MediaFormat.KEY_DURATION):-1;int rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    if(us<=0||us>1800L*1000000||rate<8000||rate>192000||channels<1||channels>2)throw new IOException(AudioPolicy.ERROR);
    return new JSONObject().put("duration",us/1000000.0).put("mime",mime).put("rate",rate).put("channels",channels);
   }}throw new IOException(AudioPolicy.ERROR);
  }finally{e.release();}
 }
 static synchronized void clear(Context c)throws IOException{File[] files=dir(c).listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[a-f0-9-]{36}\\.(media|json)")&&!f.delete())throw new IOException(AudioPolicy.ERROR);}
}
