import dev.motion2video.app.ImportPolicy;
import dev.motion2video.app.RenderBudget;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class InputProtectionTest {
 interface Checked{void run()throws Exception;}
 static int checks;
 static void reject(Checked c)throws Exception{try{c.run();throw new AssertionError("Accepted invalid input");}catch(IOException expected){checks++;}}
 static void ok(boolean value){if(!value)throw new AssertionError();checks++;}
 public static void main(String[] args)throws Exception{
  for(String mime:new String[]{null,"","text/html","text/plain","application/octet-stream","text/html; charset=UTF-8"})ImportPolicy.metadata("موشن.HTML",mime,20*1024*1024);
  reject(()->ImportPolicy.metadata("file.html.zip","text/html",1));reject(()->ImportPolicy.metadata("file.svg","text/html",1));reject(()->ImportPolicy.metadata("file.html","application/zip",1));reject(()->ImportPolicy.metadata(null,"text/html",1));reject(()->ImportPolicy.metadata("file.htm","text/html",ImportPolicy.MAX_BYTES+1L));
  ok(ImportPolicy.read(new ByteArrayInputStream("\ufeff<html>سلام العربية</html>".getBytes(StandardCharsets.UTF_8))).equals("<html>سلام العربية</html>"));
  reject(()->ImportPolicy.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28})));reject(()->ImportPolicy.read(new ByteArrayInputStream(new byte[]{(byte)0xff,(byte)0xfe,0,0})));reject(()->ImportPolicy.validate("\ud800"));reject(()->ImportPolicy.validate("\udc00"));reject(()->ImportPolicy.validate("<html>\0</html>"));reject(()->ImportPolicy.validate("  "));
  reject(()->ImportPolicy.validate("س".repeat(ImportPolicy.MAX_BYTES/2+1)));
  byte[] exact=new byte[ImportPolicy.MAX_BYTES];java.util.Arrays.fill(exact,(byte)'a');ok(ImportPolicy.read(new ByteArrayInputStream(exact)).length()==exact.length);
  reject(()->ImportPolicy.read(new InputStream(){long left=ImportPolicy.MAX_BYTES+1L;public int read(){return left-->0?'a':-1;}public int read(byte[] b,int o,int n){if(left<=0)return -1;int count=(int)Math.min(n,left);java.util.Arrays.fill(b,o,o+count,(byte)'a');left-=count;return count;}}));
  RenderBudget b=new RenderBudget(1000,512L*1024*1024);b.check(1001,32L*1024*1024,RenderBudget.frameReserve(1920,1080));checks++;
  reject(()->b.check(1001,256L*1024*1024,1));reject(()->b.check(1001,1,Long.MAX_VALUE));reject(()->b.check(1000+RenderBudget.READY_MS,0,0));b.progress(1000+RenderBudget.READY_MS);b.check(1001+RenderBudget.READY_MS,0,0);checks++;
  b.progress(1000+RenderBudget.MAX_JOB_MS);reject(()->b.check(1000+RenderBudget.MAX_JOB_MS,0,0));reject(()->new RenderBudget(0,64L*1024*1024).check(0,0,RenderBudget.frameReserve(1920,1080)));
  ok(RenderBudget.frameReserve(1080,1920)==RenderBudget.frameReserve(1920,1080));
  System.out.println("PASS: "+checks+" import/encoding/boundary/memory/deadline checks");
 }
}
