import dev.motion2video.app.RenderEstimates;
public final class RenderEstimatesTest {public static void main(String[] x){
 if(RenderEstimates.remainingMillis(0,840,1000)!=-1||RenderEstimates.remainingMillis(840,840,1000)!=0||RenderEstimates.remainingMillis(10,100,2000)!=18000||RenderEstimates.remainingMillis(101,100,2000)!=-1)throw new AssertionError();
 long silent=RenderEstimates.bytes(18000,60,20000000,false),audio=RenderEstimates.bytes(18000,60,20000000,true);
 if(silent!=772500000||audio<=silent||RenderEstimates.bytes(1,60,16000000,false)<=0||RenderEstimates.bytes(0,60,16000000,false)!=-1)throw new AssertionError();
 System.out.println("PASS: estimate unknown/completion/rate/large-size/audio-overhead boundaries");}}
