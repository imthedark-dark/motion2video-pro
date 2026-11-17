package dev.motion2video.app;
import android.content.Context;import android.graphics.Bitmap;import java.io.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;

/** Two reusable frame buffers overlap rendering with one serialized encoder.
 * Frame timestamps and all output settings are identical to the requested job.
 */
final class RenderEngine {
 interface Progress{void frame(int done,int total);}
 private final Context context;private final RenderRequest request;private volatile OffscreenStage stage;
 RenderEngine(Context c,RenderRequest r){context=c;request=r;}
 void cancel(){OffscreenStage s=stage;if(s!=null)s.cancelPending();}
 private static void active(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean())throw new IOException("رندر لغو شد.");}
 File render(BooleanSupplier cancelled,Progress progress)throws Exception{
  File output=new File(context.getCacheDir(),"render-"+request.id+"."+request.ext);boolean success=false;
  ExecutorService encoding=Executors.newSingleThreadExecutor(r->new Thread(r,"motion-encoder"));Bitmap[] buffers=new Bitmap[2];Mp4Encoder[] encoder=new Mp4Encoder[1];
  try{
   CodecSupport.encoder(request.mime,request.width,request.height,request.fps,request.bitrate);
   active(cancelled);stage=new OffscreenStage(context,request);stage.awaitReady(cancelled);active(cancelled);
   for(int i=0;i<buffers.length;i++)buffers[i]=Bitmap.createBitmap(request.width,request.height,Bitmap.Config.ARGB_8888);
   encoding.submit(()->{try{encoder[0]=new Mp4Encoder(output,request.width,request.height,request.fps,request.mime,request.bitrate);}catch(Exception e){throw new CompletionException(e);}}).get(45,TimeUnit.SECONDS);
   Future<?>[] fences={CompletableFuture.completedFuture(null),CompletableFuture.completedFuture(null)};
   for(int i=0;i<request.frames;i++){
    int slot=i%buffers.length;fences[slot].get(45,TimeUnit.SECONDS);active(cancelled);
    stage.capture((double)i/request.fps,i,buffers[slot]);active(cancelled);
    final int frame=i;final Bitmap bitmap=buffers[slot];
    fences[slot]=encoding.submit(()->{try{active(cancelled);encoder[0].frame(bitmap,(long)frame*1000000/request.fps);progress.frame(frame+1,request.frames);}catch(Exception e){throw new CompletionException(e);}});
   }
   for(Future<?> fence:fences)fence.get(45,TimeUnit.SECONDS);active(cancelled);
   encoding.submit(()->{try{encoder[0].finish((long)request.frames*1000000/request.fps);}catch(Exception e){throw new CompletionException(e);}}).get(45,TimeUnit.SECONDS);
   encoding.submit(()->{encoder[0].close();encoder[0]=null;}).get(45,TimeUnit.SECONDS);
   active(cancelled);if(!CodecSupport.webm(request.mime))FastStart.rewrite(output);Mp4Encoder.validate(output,request.mime);active(cancelled);success=true;return output;
  }finally{
   OffscreenStage s=stage;stage=null;if(s!=null)s.close();
   try{encoding.submit(()->{if(encoder[0]!=null){encoder[0].close();encoder[0]=null;}});}catch(RejectedExecutionException ignored){}
   encoding.shutdown();boolean stopped=false;try{stopped=encoding.awaitTermination(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   if(stopped)for(Bitmap b:buffers)if(b!=null&&!b.isRecycled())b.recycle();
   if(!success)output.delete();
  }
 }
}
