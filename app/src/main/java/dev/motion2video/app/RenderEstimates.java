package dev.motion2video.app;
/** Estimates only: never affects encoder settings or scheduling. */
public final class RenderEstimates {
 public static long bytes(int frames,int fps,int bitrate,boolean audio){if(frames<1||fps<1||bitrate<1)return -1;return (long)Math.ceil((frames/(double)fps)*(bitrate+(audio?192000:0))/8*1.03);}
 public static long remainingMillis(int done,int total,long elapsed){if(done<1||done>total||elapsed<1)return -1;return (long)Math.ceil((total-done)*(elapsed/(double)done));}
 private RenderEstimates(){}
}
