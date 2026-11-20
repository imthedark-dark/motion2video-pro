import dev.motion2video.app.ImportedContent;
import java.nio.file.*;
public final class ImportedContentTest {
 public static void main(String[] args)throws Exception{
  String html=Files.readString(Path.of(args[0]));
  for(boolean online:new boolean[]{false,true}){
   String safe=ImportedContent.secure(html,online);
   if(!safe.startsWith("<!doctype html><meta http-equiv=\"Content-Security-Policy\""))throw new AssertionError("CSP must follow doctype and precede all imported markup");
   if(!safe.contains("frame-src 'none'")||!safe.contains("worker-src 'none'")||!safe.contains("form-action 'none'"))throw new AssertionError("Missing restrictions");
   if(!online&&!safe.contains("connect-src data: blob:;"))throw new AssertionError("Offline connections permitted");
   Files.writeString(Path.of(args[1],online?"online.html":"offline.html"),safe);
  }
  if(args.length==4){
   String prepared=ImportedContent.prepare(html,Files.readString(Path.of(args[2])),Files.readString(Path.of(args[3])),"",false);
   Files.writeString(Path.of(args[1],"offline.html"),prepared);
  }
  String headless=ImportedContent.prepare("<!doctype html><canvas></canvas>","","","",false);
  if(!headless.startsWith("<!doctype html><meta"))throw new AssertionError("Headless HTML lost standards mode");
  String fragment="<script>window.early=true;</script>";
  if(ImportedContent.secure(fragment,false).indexOf("Content-Security-Policy")>ImportedContent.secure(fragment,false).indexOf("window.early"))throw new AssertionError("Fragment executes first");
  System.out.println("PASS: CSP precedes scripts/markup, preserves doctype, and blocks frames/workers/forms.");
 }
}
