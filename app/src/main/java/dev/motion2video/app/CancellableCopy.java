package dev.motion2video.app;
import java.io.*;import java.util.function.BooleanSupplier;
/** Stop a Downloads write promptly when the OS stops the render service. */
public final class CancellableCopy {
 private static void active(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean())throw new IOException("ذخیره متوقف شد.");}
 public static void copy(InputStream in,OutputStream out,BooleanSupplier cancelled)throws IOException{
  byte[] buffer=new byte[65536];int n;
  active(cancelled);
  while((n=in.read(buffer))!=-1){active(cancelled);out.write(buffer,0,n);}
  active(cancelled);
 }
}
