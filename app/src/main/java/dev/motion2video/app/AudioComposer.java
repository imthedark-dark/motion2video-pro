package dev.motion2video.app;
import android.content.Context;import android.media.*;import android.os.*;import java.io.*;import java.nio.*;import java.nio.file.*;import java.util.function.BooleanSupplier;
/** Optional post-pass. Silent video encoder and its YUV/timestamps are untouched. */
final class AudioComposer {
 static final String UNSUPPORTED="پردازش این صدا روی دستگاه پشتیبانی نمی‌شود؛ WAV یا AAC دیگری امتحان کن.";
 private static final int RATE=48000,CHANNELS=2,BITS=192000,WAIT=10000;
 static void validate(Context c,RenderRequest job)throws Exception{
  if(job.audio==null)return;if(!job.ext.equals("mp4"))throw new IOException("صدا فعلاً فقط با خروجی MP4 قابل استفاده است.");
  org.json.JSONObject info=AudioAssets.inspect(AudioAssets.file(c,job.audio.id));if(job.audio.end>info.getDouble("duration")+.001)throw new IOException(AudioOptions.ERROR);
  MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",RATE,CHANNELS);f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);f.setInteger(MediaFormat.KEY_BIT_RATE,BITS);
  if(new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(f)==null)throw new IOException(UNSUPPORTED);
 }
 private static final class Guard {final Context context;final RenderBudget budget;long memoryAt;final BooleanSupplier cancelled;final long began=SystemClock.elapsedRealtime();long progress=began;Guard(Context context,BooleanSupplier c){this.context=context;cancelled=c;budget=new RenderBudget(began,Runtime.getRuntime().maxMemory());}void check()throws IOException{long now=SystemClock.elapsedRealtime();budget.check(now,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),16L*1024*1024);if(now-memoryAt>1000){memoryAt=now;android.app.ActivityManager.MemoryInfo m=new android.app.ActivityManager.MemoryInfo();((android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(m);if(m.lowMemory)throw new IOException(PublicErrors.MEMORY);}if(cancelled.getAsBoolean())throw new IOException("رندر لغو شد.");if(now-progress>45000||now-began>15*60*1000L)throw new IOException("مهلت پردازش صدا تمام شد؛ بخش کوتاه‌تری انتخاب کن.");}void progress(){progress=SystemClock.elapsedRealtime();budget.progress(progress);}}
 static void attach(Context c,RenderRequest job,File video,BooleanSupplier cancelled)throws Exception{
  if(job.audio==null)return;validate(c,job);Guard guard=new Guard(c,cancelled);File base=c.getCacheDir(),pcm=new File(base,"audio-"+job.id+".pcm"),aac=new File(base,"audio-"+job.id+".m4a"),mux=new File(base,"audio-"+job.id+".mp4");
  try{
   long reserve=Math.round(job.outputDuration*RATE*4)+video.length()*2+Math.round(job.outputDuration*BITS/8)+32L*1024*1024;
   if(new StatFs(base.getAbsolutePath()).getAvailableBytes()<reserve)throw new IOException("فضای خالی برای پردازش صدا کافی نیست.");
   decode(AudioAssets.file(c,job.audio.id),pcm,job,guard);encode(pcm,aac,guard);remux(video,aac,mux,(long)job.frames*1000000/job.fps,guard);guard.check();FastStart.rewrite(mux);Mp4Encoder.validate(mux,job.mime);guard.check();
   Files.move(mux.toPath(),video.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
  }finally{pcm.delete();aac.delete();mux.delete();new File(mux.getAbsolutePath()+".faststart").delete();}
 }
 private static int track(MediaExtractor e,String prefix)throws IOException{for(int i=0;i<e.getTrackCount();i++)if(e.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith(prefix))return i;throw new IOException(UNSUPPORTED);}
 private static void pcm(ByteBuffer data,MediaCodec.BufferInfo info,MediaFormat format,PcmTimeline timeline,Guard guard)throws Exception{
  int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),encoding=format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):2;
  if(channels<1||channels>2||rate<8000||rate>192000||encoding!=2||info.size<0||info.size>2*1024*1024||info.size%(channels*2)!=0)throw new IOException(UNSUPPORTED);
  data.order(ByteOrder.LITTLE_ENDIAN);data.position(info.offset);data.limit(info.offset+info.size);int frames=info.size/(channels*2);
  for(int n=0;n<frames;n++){if((n&1023)==0)guard.check();short l=data.getShort(),r=channels==2?data.getShort():l;timeline.accept(info.presentationTimeUs/1000000.0+n/(double)rate,l,r);}
 }
 private static void decode(File source,File destination,RenderRequest job,Guard guard)throws Exception{
  MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
  try(BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(destination),32768)){
   extractor.setDataSource(source.getAbsolutePath());int index=track(extractor,"audio/");MediaFormat input=extractor.getTrackFormat(index);extractor.selectTrack(index);extractor.seekTo((long)(job.audio.start*1000000),MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
   PcmTimeline timeline=new PcmTimeline(out,job.audio.start,job.audio.end,job.outputDuration,job.audio.volume,job.audio.fadeIn,job.audio.fadeOut).checked(guard::check);
   if("audio/raw".equals(input.getString(MediaFormat.KEY_MIME))){ByteBuffer raw=ByteBuffer.allocateDirect(2*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    while(!timeline.full()){guard.check();raw.clear();int count=extractor.readSampleData(raw,0);if(count<0)break;info.set(0,count,extractor.getSampleTime(),0);pcm(raw,info,input,timeline,guard);guard.progress();if(extractor.getSampleTime()>=job.audio.end*1000000)break;extractor.advance();}
   }else{
    input.setInteger(MediaFormat.KEY_PCM_ENCODING,2);decoder=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));decoder.configure(input,null,null,0);decoder.start();MediaFormat decoded=input;boolean inputEnded=false,outputEnded=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    while(!outputEnded&&!timeline.full()){guard.check();if(!inputEnded){int n=decoder.dequeueInputBuffer(WAIT);if(n>=0){ByteBuffer buffer=decoder.getInputBuffer(n);buffer.clear();int count=extractor.readSampleData(buffer,0);long pts=extractor.getSampleTime();if(count<0||pts>job.audio.end*1000000+1000000){decoder.queueInputBuffer(n,0,0,Math.max(0,pts),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}else{decoder.queueInputBuffer(n,0,count,pts,0);extractor.advance();}}}
     int n=decoder.dequeueOutputBuffer(info,WAIT);if(n==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)decoded=decoder.getOutputFormat();else if(n>=0){try{if(info.size>0){pcm(decoder.getOutputBuffer(n),info,decoded,timeline,guard);guard.progress();}outputEnded=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0||info.presentationTimeUs>job.audio.end*1000000;}finally{decoder.releaseOutputBuffer(n,false);}}
    }
   }
   timeline.finish();guard.check();
  }finally{if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}extractor.release();}
 }
 private static void encode(File pcm,File output,Guard guard)throws Exception{
  MediaCodec encoder=null;MediaMuxer muxer=null;boolean started=false,success=false;
  try(InputStream in=new BufferedInputStream(new FileInputStream(pcm),32768)){
   MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",RATE,CHANNELS);f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);f.setInteger(MediaFormat.KEY_BIT_RATE,BITS);f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
   encoder=MediaCodec.createEncoderByType("audio/mp4a-latm");encoder.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
   byte[] bytes=new byte[16384];long fed=0,last=-1;boolean inputEnded=false,ended=false;int track=-1;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   while(!ended){guard.check();if(!inputEnded){int index=encoder.dequeueInputBuffer(WAIT);if(index>=0){ByteBuffer buffer=encoder.getInputBuffer(index);buffer.clear();int count=in.read(bytes,0,Math.min(bytes.length,buffer.remaining()/4*4));if(count<0){encoder.queueInputBuffer(index,0,0,fed*1000000/RATE,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}else{if(count==0||count%4!=0)throw new IOException(UNSUPPORTED);buffer.put(bytes,0,count);encoder.queueInputBuffer(index,0,count,fed*1000000/RATE,0);fed+=count/4;}}}
    int index=encoder.dequeueOutputBuffer(info,WAIT);if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IOException(UNSUPPORTED);track=muxer.addTrack(encoder.getOutputFormat());muxer.start();started=true;}else if(index>=0){try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.presentationTimeUs>=0){if(!started||info.presentationTimeUs<=last)throw new IOException(UNSUPPORTED);muxer.writeSampleData(track,encoder.getOutputBuffer(index),info);last=info.presentationTimeUs;guard.progress();}ended=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;}finally{encoder.releaseOutputBuffer(index,false);}}
   }
   if(!started||last<0)throw new IOException(UNSUPPORTED);muxer.stop();started=false;success=true;
  }finally{if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}encoder.release();}if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}if(!success)output.delete();}
 }
 private static void remux(File video,File audio,File output,long durationUs,Guard guard)throws Exception{
  MediaExtractor v=new MediaExtractor(),a=new MediaExtractor();MediaMuxer muxer=null;boolean started=false,success=false;
  try{
   v.setDataSource(video.getAbsolutePath());a.setDataSource(audio.getAbsolutePath());int vi=track(v,"video/"),ai=track(a,"audio/");v.selectTrack(vi);a.selectTrack(ai);muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);int vo=muxer.addTrack(v.getTrackFormat(vi)),ao=muxer.addTrack(a.getTrackFormat(ai));muxer.start();started=true;
   ByteBuffer buffer=ByteBuffer.allocateDirect(8*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long lastA=-1;
   while(true){guard.check();long vt=v.getSampleTime(),at=a.getSampleTime();if(at>=durationUs)at=-1;if(vt<0&&at<0)break;boolean videoNext=vt>=0&&(at<0||vt<=at);MediaExtractor source=videoNext?v:a;long pts=videoNext?vt:at;buffer.clear();int n=source.readSampleData(buffer,0);if(n<0)throw new IOException(UNSUPPORTED);
    if(!videoNext&&pts<=lastA)throw new IOException(UNSUPPORTED);if(!videoNext)lastA=pts;info.set(0,n,pts,source.getSampleFlags()&MediaCodec.BUFFER_FLAG_KEY_FRAME);muxer.writeSampleData(videoNext?vo:ao,buffer,info);source.advance();guard.progress();
   }
   muxer.stop();started=false;success=true;
  }finally{v.release();a.release();if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}if(!success)output.delete();}
 }
}
