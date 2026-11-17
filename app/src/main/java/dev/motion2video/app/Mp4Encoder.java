package dev.motion2video.app;
import android.media.*;import android.graphics.Bitmap;import java.io.*;import java.nio.*;
/** Actual AVC encoder and ISO MP4 muxer; no extension renaming or WebM fallback. */
public final class Mp4Encoder implements AutoCloseable {
 private MediaCodec codec;private MediaMuxer muxer;private boolean started;private int track=-1;private final int width,height;private final int[] pixels;private boolean ended;private final String mime;
 public Mp4Encoder(File file,int width,int height,int fps,String mime,int bitrate)throws Exception {
  this.mime=mime;this.width=width;this.height=height;pixels=new int[width*height];
  try{MediaFormat format=MediaFormat.createVideoFormat(mime,width,height);format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);format.setInteger(MediaFormat.KEY_BIT_RATE,bitrate);format.setInteger(MediaFormat.KEY_FRAME_RATE,fps);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
   format.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);format.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED);format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
   codec=MediaCodec.createByCodecName(CodecSupport.encoder(mime,width,height,fps,bitrate));codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();muxer=new MediaMuxer(file.getAbsolutePath(),CodecSupport.webm(mime)?MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM:MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
  }catch(Exception e){close();throw e;}
 }
 public void frame(Bitmap bitmap,long pts)throws Exception {
  int index;long deadline=System.nanoTime()+10_000_000_000L;
  while((index=codec.dequeueInputBuffer(10000))<0){drain(false);if(System.nanoTime()>deadline)throw new IOException("Encoder input timeout");}
  int inputSize=codec.getInputBuffer(index).capacity();
  android.media.Image image=codec.getInputImage(index);if(image==null)throw new IOException("YUV input images unavailable on this encoder");
  bitmap.getPixels(pixels,0,width,0,0,width,height);android.media.Image.Plane[] planes=image.getPlanes();
  for(int y=0;y<height;y++)for(int x=0;x<width;x++){
   int p=pixels[y*width+x],r=(p>>16)&255,g=(p>>8)&255,b=p&255;
   put(planes[0],x,y,clamp(((47*r+157*g+16*b+128)>>8)+16));
   if((x&1)==0&&(y&1)==0){int rr=0,gg=0,bb=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int q=pixels[(y+dy)*width+x+dx];rr+=(q>>16)&255;gg+=(q>>8)&255;bb+=q&255;}rr/=4;gg/=4;bb/=4;put(planes[1],x/2,y/2,clamp(((-26*rr-86*gg+112*bb+128)>>8)+128));put(planes[2],x/2,y/2,clamp(((112*rr-102*gg-10*bb+128)>>8)+128));}
  }
  codec.queueInputBuffer(index,0,inputSize,pts,0);drain(false);
 }
 private static int clamp(int n){return Math.max(0,Math.min(255,n));}
 private static void put(android.media.Image.Plane p,int x,int y,int value){p.getBuffer().put(y*p.getRowStride()+x*p.getPixelStride(),(byte)value);}
 public void finish(long pts)throws Exception {
  int i;long deadline=System.nanoTime()+10_000_000_000L;while((i=codec.dequeueInputBuffer(10000))<0){drain(false);if(System.nanoTime()>deadline)throw new IOException("Encoder EOS timeout");}codec.queueInputBuffer(i,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drain(true);
 }
 private void drain(boolean finish)throws Exception {
  MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=System.nanoTime()+20_000_000_000L;
  while(true){int index=codec.dequeueOutputBuffer(info,10000);
   if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IOException("Duplicate encoder format");MediaFormat actual=codec.getOutputFormat();actual.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);actual.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);actual.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED);if(mime.equals("video/avc")&&actual.containsKey("csd-0")){ByteBuffer csd=actual.getByteBuffer("csd-0").duplicate();byte[] bytes=new byte[csd.remaining()];csd.get(bytes);actual.setByteBuffer("csd-0",ByteBuffer.wrap(AvcColour.annex(bytes)));}track=muxer.addTrack(actual);muxer.start();started=true;}
   else if(index>=0){ByteBuffer buffer=codec.getOutputBuffer(index);if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;if(info.size>0){if(!started||buffer==null)throw new IOException("Encoder muxer state");buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info);}boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);if(eos){ended=true;return;}}
   else if(!finish)return;
   if(System.nanoTime()>deadline)throw new IOException("Encoder drain timeout");
  }
 }
 @Override public void close(){if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();codec=null;}if(muxer!=null){try{if(started)muxer.stop();}catch(Exception ignored){}muxer.release();muxer=null;}}
 public static void validate(File file,String mime)throws Exception{MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(file.getAbsolutePath());boolean avc=false;for(int i=0;i<extractor.getTrackCount();i++)avc|=mime.equals(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));if(!avc||file.length()<100)throw new IOException("MP4 validation failed");}finally{extractor.release();}}
}
