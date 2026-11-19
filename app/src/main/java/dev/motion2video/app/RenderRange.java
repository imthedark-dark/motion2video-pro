package dev.motion2video.app;
import java.io.IOException;
/** Source range affects capture time only. Output always starts at PTS zero. */
public final class RenderRange {
 public static int frames(double duration,int fps,double start,double end)throws IOException{
  if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end>duration||end<=start||end-start<1.0/fps)throw new IOException("بازهٔ رندر معتبر نیست؛ شروع و پایان را در مدت موشن انتخاب کن.");
  return (int)Math.round((end-start)*fps);
 }
 private RenderRange(){}
}
