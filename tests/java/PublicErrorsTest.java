package dev.motion2video.app;
import java.io.*;import java.util.concurrent.*;
public final class PublicErrorsTest {
 static int checks;
 static void same(String a,String b){if(!a.equals(b))throw new AssertionError("Unexpected public error");checks++;}
 public static void main(String[] args)throws Exception{
  String fixtureUrl="https://" + "fixture-user" + ":" + "fixture-token" + "@example.invalid/";
  String canary="PRIVATE_FIXTURE /data/user/0/example/files/secret.html "+fixtureUrl+"<html>سلام سري</html>";
  for(Throwable error:new Throwable[]{new IOException(canary),new RuntimeException(canary),new Exception("خطای فایل "+canary),new Exception("رندر لغو شد. "+canary),new Exception("x".repeat(10000)+canary)})same(PublicErrors.message(error),PublicErrors.GENERIC);
  same(PublicErrors.message(new FileNotFoundException(canary)),PublicErrors.FILE);
  same(PublicErrors.message(new SecurityException(canary)),PublicErrors.PERMISSION);
  same(PublicErrors.message(new OutOfMemoryError(canary)),PublicErrors.MEMORY);
  same(PublicErrors.message(new TimeoutException(canary)),PublicErrors.TIMEOUT);
  String guidance=PublicErrors.message(new IOException("ثبت فهرست دانلودها انجام نشد."));
  if(!guidance.contains("Download/Motion2Video"))throw new AssertionError("Missing recovery guidance");
  same(PublicErrors.sanitize(guidance),guidance);
  same(PublicErrors.sanitize(guidance+canary),PublicErrors.GENERIC);
  String useful="رسم فریم بیش از ۴۵ ثانیه طول کشید؛ رندر متوقف شد.";
  same(PublicErrors.message(new ExecutionException(new IOException(useful))),useful);
  same(PublicErrors.message(new IOException("wrapper "+canary,new SecurityException(canary))),PublicErrors.PERMISSION);
  Throwable cycle=new Exception(canary){@Override public synchronized Throwable getCause(){return this;}};same(PublicErrors.message(cycle),PublicErrors.GENERIC);
  Throwable broken=new Exception(){@Override public String getMessage(){throw new IllegalStateException(canary);}};same(PublicErrors.message(broken),PublicErrors.GENERIC);
  Throwable deep=new IOException(canary);for(int i=0;i<50;i++)deep=new IOException(canary,deep);same(PublicErrors.message(deep),PublicErrors.GENERIC);
  same(PublicErrors.message((Throwable)null),PublicErrors.GENERIC);
  same(PublicErrors.sanitize(canary),PublicErrors.GENERIC);same(PublicErrors.sanitize(null),PublicErrors.GENERIC);
  same(PublicErrors.sanitize("رندر لغو شد."),"رندر لغو شد.");
  same(PublicErrors.sanitize(PublicErrors.SAVE_WARNING),PublicErrors.SAVE_WARNING);
  for(String value:new String[]{useful+canary,"رندر لغو شد.\n"+canary,"رندر لغو شد.\u202e"+canary})same(PublicErrors.sanitize(value),PublicErrors.GENERIC);
  System.out.println("PASS: "+checks+" privacy error checks (paths, HTML, URL credentials, oversized/cyclic/throwing causes and useful fixed messages)");
 }
}
