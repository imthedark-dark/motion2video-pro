package dev.motion2video.app;
import android.content.Context;import android.graphics.Bitmap;import java.io.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;

/** Two reusable frame buffers overlap rendering with one serialized encoder.
 * Frame timestamps and all output settings are identical to the requested job.
 */
final class RenderEngine {
 interface Progress{void frame(int done,int total);}
 private final Context context;private final RenderRequest request;private volatile OffscreenStage stage;private volatile IOException failure;private RenderBudget budget;
 RenderEngine(Context c,RenderRequest r){context=c;request=r;}
 void cancel(){OffscreenStage s=stage;if(s!=null)s.cancelPending();}
 private void active(BooleanSupplier cancelled)throws IOException{if(failure!=null)throw failure;if(cancelled.getAsBoolean())throw new IOException("رندر لغو شد.");}
 File render(BooleanSupplier cancelled,Progress progress)throws Exception{
  File output=new File(context.getCacheDir(),"render-"+request.id+"."+request.ext);boolean success=false;
  budget=new RenderBudget(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().maxMemory());
  budget.check(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),RenderBudget.frameReserve(request.width,request.height));
  ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"motion-watchdog"));
  watchdog.scheduleAtFixedRate(()->{try{
   budget.check(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),0);
   android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();((android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
   if(memory.lowMemory)throw new IOException("حافظهٔ دستگاه کم است؛ رندر متوقف شد. برنامه‌های دیگر را ببند یا اندازه را کاهش بده.");
  }catch(IOException e){if(failure==null)failure=e;OffscreenStage s=stage;if(s!=null)s.abort(failure,true);}},1,1,TimeUnit.SECONDS);
  ExecutorService encoding=Executors.newSingleThreadExecutor(r->new Thread(r,"motion-encoder"));Bitmap[] buffers=new Bitmap[2];Mp4Encoder[] encoder=new Mp4Encoder[1];
  try{
   CodecSupport.encoder(request.mime,request.width,request.height,request.fps,request.bitrate);
   active(cancelled);stage=new OffscreenStage(context,request);stage.awaitReady(cancelled);active(cancelled);budget.progress(android.os.SystemClock.elapsedRealtime());
   for(int i=0;i<buffers.length;i++)buffers[i]=Bitmap.createBitmap(request.width,request.height,Bitmap.Config.ARGB_8888);
   encoding.submit(()->{try{encoder[0]=new Mp4Encoder(output,request.width,request.height,request.fps,request.mime,request.bitrate);}catch(Exception e){throw new CompletionException(e);}}).get(45,TimeUnit.SECONDS);
   Future<?>[] fences={CompletableFuture.completedFuture(null),CompletableFuture.completedFuture(null)};
   for(int i=0;i<request.frames;i++){
    budget.progress(android.os.SystemClock.elapsedRealtime());int slot=i%buffers.length;fences[slot].get(45,TimeUnit.SECONDS);active(cancelled);
    stage.capture(request.rangeStart+(double)i/request.fps,i,buffers[slot]);active(cancelled);
    final int frame=i;final Bitmap bitmap=buffers[slot];
    fences[slot]=encoding.submit(()->{try{active(cancelled);encoder[0].frame(bitmap,(long)frame*1000000/request.fps);progress.frame(frame+1,request.frames);}catch(Exception e){throw new CompletionException(e);}});
   }
   for(Future<?> fence:fences)fence.get(45,TimeUnit.SECONDS);active(cancelled);
   encoding.submit(()->{try{encoder[0].finish((long)request.frames*1000000/request.fps);}catch(Exception e){throw new CompletionException(e);}}).get(45,TimeUnit.SECONDS);
   encoding.submit(()->{encoder[0].close();encoder[0]=null;}).get(45,TimeUnit.SECONDS);
   active(cancelled);if(!CodecSupport.webm(request.mime))FastStart.rewrite(output);Mp4Encoder.validate(output,request.mime);active(cancelled);success=true;return output;
  }catch(Exception e){if(failure!=null)throw failure;if(e instanceof TimeoutException){IOException timeout=new IOException("مهلت پردازش فریم تمام شد؛ کد موشن را بررسی کن.",e);OffscreenStage s=stage;if(s!=null)s.abort(timeout,true);throw timeout;}throw e;
  }finally{
   watchdog.shutdownNow();
   OffscreenStage s=stage;stage=null;if(s!=null)s.close();
   try{encoding.submit(()->{if(encoder[0]!=null){encoder[0].close();encoder[0]=null;}});}catch(RejectedExecutionException ignored){}
   encoding.shutdown();boolean stopped=false;try{stopped=encoding.awaitTermination(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   if(stopped)for(Bitmap b:buffers)if(b!=null&&!b.isRecycled()&&(s==null||s.canRecycle(b)))b.recycle();
   if(!success)output.delete();
  }
 }
}
