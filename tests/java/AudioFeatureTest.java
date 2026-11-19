import dev.motion2video.app.*;import java.io.*;import java.nio.*;
public final class AudioFeatureTest {
 public static void main(String[] args)throws Exception{
  for(String name:new String[]{"voice.mp3","صدا.WAV","a.m4a","a.aac"})AudioPolicy.metadata(name,"application/octet-stream",1);
  for(String name:new String[]{"../a.mp3","a.exe","a.mp3.zip"})try{AudioPolicy.metadata(name,"audio/mpeg",1);throw new AssertionError();}catch(IOException expected){}
  try{AudioPolicy.metadata("a.mp3","audio/wav",1);throw new AssertionError();}catch(IOException expected){}
  try{AudioPolicy.metadata("a.mp3","audio/mpeg",AudioPolicy.MAX_BYTES+1);throw new AssertionError();}catch(IOException expected){}
  if(AudioPolicy.id("../../x")||!AudioPolicy.id("12345678-1234-1234-1234-123456789abc"))throw new AssertionError();
  if(PcmTimeline.gain(30000,.5,1,2,0,0)!=32767||PcmTimeline.gain(-30000,.5,1,2,0,0)!=-32768||PcmTimeline.gain(10000,0,1,1,.2,0)!=0||PcmTimeline.gain(10000,.9,1,1,0,.2)!=5000)throw new AssertionError("gain/fade/clamp");
  ByteArrayOutputStream out=new ByteArrayOutputStream();PcmTimeline timeline=new PcmTimeline(out,0,2/48000.0,4/48000.0,1,0,0);timeline.accept(0,(short)1000,(short)-1000);timeline.accept(2/48000.0,(short)3000,(short)-3000);timeline.finish();
  ByteBuffer pcm=ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);short[] expected={1000,-1000,2000,-2000,0,0,0,0};for(short value:expected)if(pcm.getShort()!=value)throw new AssertionError("interpolation/padding/channel order");if(pcm.hasRemaining())throw new AssertionError();
  out.reset();timeline=new PcmTimeline(out,1,1.001,.001,1,0,0);timeline.accept(.9999,(short)1234,(short)1234);timeline.accept(1.001,(short)1234,(short)1234);timeline.finish();if(out.size()!=48*4||ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getShort()!=1234)throw new AssertionError("trim starts at output zero");
  final int[] checkpoints={0};out.reset();timeline=new PcmTimeline(out,0,1,1,1,0,0).checked(()->{if(++checkpoints[0]==2)throw new IOException("cancel");});try{timeline.finish();throw new AssertionError();}catch(IOException expectedFailure){}if(out.size()>4096*4)throw new AssertionError("cancellation delayed");
  System.out.println("PASS: audio size/type/UUID policies, PCM trim, stereo interpolation, padding, gain/clamp/fades and bounded cancellation");
 }
}
