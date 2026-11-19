package dev.motion2video.app;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.regex.*;
import javax.net.ssl.HttpsURLConnection;

/** Native-only transport and validation. The caller supplies approved family names, never URLs. */
final class FontDownload {
 static final int FILE_LIMIT = 3*1024*1024, FAMILY_LIMIT = 12*1024*1024, FACE_LIMIT = 16, CSS_LIMIT = 128*1024;
 static final int CONNECT_MS = 5000, READ_MS = 6000, OVERALL_MS = 45000;
 static final String UA = "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
 private static final Pattern COMMENT=Pattern.compile("/\\*.*?\\*/",Pattern.DOTALL);
 private static final Pattern FACE=Pattern.compile("@font-face\\s*\\{([^{}]*)}",Pattern.CASE_INSENSITIVE);
 private static final Pattern SOURCE=Pattern.compile("url\\(\\s*(['\"]?)(https://[^\\s'\"()]+)\\1\\s*\\)\\s*(?:format\\(\\s*(['\"]?)(woff2|woff|truetype)\\3\\s*\\))?",Pattern.CASE_INSENSITIVE);
 private static final Pattern RANGE=Pattern.compile("(?i)U\\+[0-9a-f?]{1,6}(?:-[0-9a-f]{1,6})?(?:\\s*,\\s*U\\+[0-9a-f?]{1,6}(?:-[0-9a-f]{1,6})?)*");

 static final class Control {
  private final long deadline;
  Control(){this(OVERALL_MS);}
  Control(long millis){deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);}
  private final AtomicReference<Runnable> disconnect = new AtomicReference<>();
  volatile boolean cancelled;
  void cancel() {
   cancelled = true;
   Runnable action = disconnect.getAndSet(null);
   if (action!=null) action.run();
  }
  void check() throws IOException {
   if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline) throw failure();
  }
  void attach(Runnable action) throws IOException {
   disconnect.set(action);
   if (cancelled) cancel();
   check();
  }
  void detach() { disconnect.set(null); }
 }

 interface Transport { Reply get(URI uri, int limit, Control control) throws IOException; }

 static final class Reply {
  final int status;
  final String mime;
  final byte[] bytes;
  Reply(int status,String mime,byte[] bytes){this.status=status;this.mime=mime;this.bytes=bytes;}
 }

 static final class Face {
  final String family,weight,style,range;
  final URI url;
  Face(String family,String weight,String style,String range,URI url){
   this.family=family;this.weight=weight;this.style=style;this.range=range;this.url=url;
  }
 }

 static final class FileData {
  final Face face;
  final String mime;
  final byte[] bytes;
  FileData(Face face,String mime,byte[] bytes){this.face=face;this.mime=mime;this.bytes=bytes;}
 }

 private final Transport transport;
 FontDownload(Transport transport){this.transport=transport;}

 static URI allowed(String raw, boolean css) throws IOException {
  try {
   URI uri=URI.create(raw);
   String host=css?"fonts.googleapis.com":"fonts.gstatic.com";
   String path=uri.getRawPath();
   if (!"https".equals(uri.getScheme()) || !host.equals(uri.getRawAuthority()) || uri.getRawFragment()!=null ||
       path==null || raw.length()>4096 || path.contains("..") || path.contains("%")) throw failure();
   if (css ? !path.equals("/css2") : !path.matches("/s/[A-Za-z0-9_./-]+\\.(woff2|woff|ttf)")) throw failure();
   if (!css && uri.getRawQuery()!=null) throw failure();
   return uri;
  } catch (IllegalArgumentException error) { throw failure(); }
 }

 List<FileData> download(String family, Control control, IntConsumer progress) throws IOException {
  if (!family.equals(FontDiscovery.clean(family)) || FontDiscovery.bundled(family)) throw failure();
  URI request=allowed("https://fonts.googleapis.com/css2?family="+family.replace(" ","+")+
   ":ital,wght@0,100..900;1,100..900&display=swap",true);
  Reply css=fetch(request,CSS_LIMIT,control);
  // Static families often reject the full variable/italic request. Retry the same approved family at its default face.
  if(css.status==400) css=fetch(allowed("https://fonts.googleapis.com/css2?family="+family.replace(" ","+")+"&display=swap",true),CSS_LIMIT,control);
  if(css.status!=200 || !type(css.mime).equals("text/css")) throw failure();
  String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(css.bytes)).toString();
  List<Face> faces=parse(source,family);
  Map<URI,Reply> downloaded=new HashMap<>();
  List<FileData> files=new ArrayList<>();
  int total=0;
  for (Face face:faces) {
   control.check();
   Reply reply=downloaded.get(face.url);
   if(reply==null){
    reply=fetch(face.url,Math.min(FILE_LIMIT,FAMILY_LIMIT-total),control);
    if(reply.status!=200 || !validFont(face.url,reply)) throw failure();
    downloaded.put(face.url,reply);
   }
   total+=reply.bytes.length;
   if(total>FAMILY_LIMIT) throw failure();
   files.add(new FileData(face,canonicalMime(face.url),reply.bytes));
   progress.accept(total);
  }
  control.check();
  return files;
 }

 private Reply fetch(URI uri,int limit,Control control) throws IOException {
  control.check();
  Reply reply=transport.get(uri,limit,control);
  control.check();
  if(reply.bytes.length>limit || (reply.status>=300&&reply.status<400)) throw failure();
  return reply;
 }

 static List<Face> parse(String source,String family) throws IOException {
  String css=COMMENT.matcher(source).replaceAll("");
  List<Face> result=new ArrayList<>();
  Matcher match=FACE.matcher(css);
  while(match.find()){
   Map<String,String> declarations=new HashMap<>();
   for(String item:match.group(1).split(";")){
    int colon=item.indexOf(':');
    if(colon>0) declarations.put(item.substring(0,colon).trim().toLowerCase(Locale.ROOT),item.substring(colon+1).trim());
   }
   String name=FontDiscovery.clean(declarations.getOrDefault("font-family",""));
   if(!family.equalsIgnoreCase(name)) continue;
   String style=declarations.getOrDefault("font-style","normal");
   String weight=declarations.getOrDefault("font-weight","400").replaceAll("\\s+"," ");
   String range=declarations.getOrDefault("unicode-range","");
   Matcher src=SOURCE.matcher(declarations.getOrDefault("src",""));
   if(!src.matches() || !validDescriptors(weight,style,range)) throw failure();
   if(result.size()>=FACE_LIMIT) throw failure();
   result.add(new Face(family,weight,style,range,allowed(src.group(2),false)));
  }
  if(result.isEmpty()) throw failure();
  return result;
 }

 static boolean validDescriptors(String weight,String style,String range){
  return ("normal".equals(style)||"italic".equals(style)) && weight.matches("[1-9]00(?: [1-9]00)?")
   && (weight.length()==3||weight.charAt(0)<=weight.charAt(4))
   && (range.isEmpty()||(range.length()<=4096&&RANGE.matcher(range).matches()));
 }

 static String canonicalMime(URI uri) {
  String path=uri.getPath();
  return path.endsWith(".woff2")?"font/woff2":path.endsWith(".woff")?"font/woff":"font/ttf";
 }
 static String type(String mime){return mime==null?"":mime.split(";",2)[0].trim().toLowerCase(Locale.ROOT);}

 private static boolean validFont(URI uri,Reply reply){
  String canonical=canonicalMime(uri), type=type(reply.mime);
  if(!type.equals(canonical) && !(canonical.equals("font/woff")&&type.equals("application/font-woff"))
      && !(canonical.equals("font/ttf")&&type.equals("application/x-font-ttf"))) return false;
  return validBytes(canonical,reply.bytes);
 }

 static boolean validBytes(String mime,byte[] bytes){
  if(bytes.length<12 || bytes.length>FILE_LIMIT) return false;
  ByteBuffer data=ByteBuffer.wrap(bytes);
  int magic=data.getInt(0);
  if(mime.equals("font/ttf")) return magic==0x00010000 && data.getShort(4)>0 && 12+16L*data.getShort(4)<=bytes.length;
  boolean woff2=mime.equals("font/woff2");
  if(!woff2&&!mime.equals("font/woff"))return false;
  return bytes.length>=(woff2?48:44) && magic==(woff2?0x774f4632:0x774f4646) &&
    data.getInt(8)==bytes.length && data.getShort(12)>0 && data.getInt(16)>0 && data.getInt(16)<=FAMILY_LIMIT;
 }

 static IOException failure(){return new IOException("Font download rejected");}

 static Reply https(URI uri,int limit,Control control) throws IOException {
  allowed(uri.toASCIIString(),"fonts.googleapis.com".equals(uri.getHost()));
  // The browser's CookieManager is separate. Fail closed if a process-wide Java cookie hook exists.
  if(CookieHandler.getDefault()!=null) throw failure();
  HttpsURLConnection connection=(HttpsURLConnection)uri.toURL().openConnection(Proxy.NO_PROXY);
  try {
   control.attach(connection::disconnect);
   connection.setInstanceFollowRedirects(false);
   connection.setUseCaches(false);
   connection.setConnectTimeout(CONNECT_MS);
   connection.setReadTimeout(READ_MS);
   connection.setRequestProperty("User-Agent",UA);
   int status=connection.getResponseCode();
   if(status!=200) return new Reply(status,"",new byte[0]);
   if(connection.getContentLength()>limit) throw failure();
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];
    for(int read;(read=in.read(buffer))!=-1;){
     control.check();
     if(read>limit-out.size()) throw failure();
     out.write(buffer,0,read);
    }
    return new Reply(status,connection.getContentType(),out.toByteArray());
   }
  } finally {
   connection.disconnect();
   control.detach();
  }
 }
}
