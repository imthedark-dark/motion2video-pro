package dev.motion2video.app;
/** Deterministic proposal ranking; never changes a running request. */
public final class CapabilityPolicy {
 private CapabilityPolicy(){}
 public static double score(String requested,String candidate,int w,int h,int fps,int bits,int cw,int ch,int cfps,int cbits){
  return (requested.equals(candidate)?0:1e12)+(candidate.equals("video/avc")?0:requested.equals(candidate)?0:1e11)
   +((w>h)==(cw>ch)?0:1e10)+((long)cw*ch>(long)w*h?1e9:0)
   +Math.abs(Math.log((double)cw*ch/((double)w*h)))*1e6+Math.abs(fps-cfps)*1000+Math.abs(bits-cbits)/1000000.;
 }
}
