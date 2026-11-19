import dev.motion2video.app.CancellableCopy;
import dev.motion2video.app.PermissionPolicy;
import java.io.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
public final class PlatformProtectionTest {
 public static void main(String[] args)throws Exception{
  int[] sdks={21,25,26,27,28,29,32,33,34,35,36};
  boolean[] storage={false,false,true,true,true,false,false,false,false,false,false};
  boolean[] notifications={false,false,false,false,false,false,false,true,true,true,true};
  for(int i=0;i<sdks.length;i++){
   if(PermissionPolicy.legacyStorage(sdks[i])!=storage[i])throw new AssertionError("Storage permission API boundary");
   if(PermissionPolicy.notifications(sdks[i])!=notifications[i])throw new AssertionError("Notification permission API boundary");
  }
  byte[] input=new byte[200000];new Random(8724).nextBytes(input);ByteArrayOutputStream out=new ByteArrayOutputStream();
  CancellableCopy.copy(new ByteArrayInputStream(input),out,()->false);if(!Arrays.equals(input,out.toByteArray()))throw new AssertionError("Copy altered export bytes");
  try{CancellableCopy.copy(new ByteArrayInputStream(input),new ByteArrayOutputStream(),()->true);throw new AssertionError("Cancelled copy started");}catch(IOException expected){}
  AtomicBoolean stop=new AtomicBoolean();ByteArrayOutputStream cancelled=new ByteArrayOutputStream(){@Override public synchronized void write(byte[] b,int off,int len){super.write(b,off,len);stop.set(true);}};
  try{CancellableCopy.copy(new ByteArrayInputStream(input),cancelled,stop::get);throw new AssertionError("Cancelled copy continued");}catch(IOException expected){}
  if(cancelled.size()!=65536)throw new AssertionError("Cancellation failed at chunk boundary");
  System.out.println("PASS: storage/notification API boundaries, byte-identical copy and prompt cancellation.");
 }
}
