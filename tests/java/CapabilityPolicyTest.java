import dev.motion2video.app.CapabilityPolicy;
public final class CapabilityPolicyTest {
 static double rank(String mime,int w,int h,int fps,int bits){return CapabilityPolicy.score("video/hevc",mime,1280,720,60,16000000,w,h,fps,bits);}
 public static void main(String[] args){
  if(rank("video/hevc",1280,720,60,16000000)!=0)throw new AssertionError();
  if(!(rank("video/hevc",640,360,30,12000000)<rank("video/avc",1280,720,60,16000000)))throw new AssertionError();
  if(!(rank("video/hevc",640,360,60,16000000)<rank("video/hevc",1080,1920,60,16000000)))throw new AssertionError();
  if(!(rank("video/hevc",640,360,60,16000000)<rank("video/hevc",1920,1080,60,16000000)))throw new AssertionError();
  if(!(rank("video/avc",1280,720,30,16000000)<rank("video/av01",1280,720,30,16000000)))throw new AssertionError();
  System.out.println("PASS: capability ranking preserves codec/orientation, avoids upscale and prefers compatible AVC fallback");
 }
}
