package dev.motion2video.app;
import android.content.Context;import android.net.Uri;import android.webkit.*;import java.io.*;import java.util.*;import java.util.function.IntConsumer;
final class StageAssets {
 static final int FONT_REPLACED=1,FONT_BLOCKED=2;
 private static final Set<String> FONT_FAMILIES=new HashSet<>(Arrays.asList(
  "Vazirmatn","Orbitron","Amiri","Noto Naskh Arabic","Scheherazade New","Anton","Inter"));
 private static final Set<String> LIBRARIES=new HashSet<>(Arrays.asList("MorphSVGPlugin.min.js","DrawSVGPlugin.min.js","SplitText.min.js","MotionPathPlugin.min.js","Flip.min.js","lottie.min.js","three.min.js"));
 private static final Set<String> FONTS=new HashSet<>(Arrays.asList("vazirmatn-Vazirmatn[wght].ttf","amiri-Amiri-Regular.ttf","amiri-Amiri-Bold.ttf","notonaskharabic-NotoNaskhArabic[wght].ttf","scheherazadenew-ScheherazadeNew-Regular.ttf","anton-Anton-Regular.ttf","inter-Inter[opsz,wght].ttf","orbitron-Orbitron[wght].ttf"));
 static String text(Context c,String name)throws IOException{try(InputStream in=c.getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));}
 static WebResourceResponse response(Context c,Uri uri,boolean online){
  return response(c,uri,online,null);
 }
 static WebResourceResponse response(Context c,Uri uri,boolean online,IntConsumer fontNotice){
  String host=uri.getHost(),scheme=uri.getScheme(),path=uri.getPath()==null?"":uri.getPath(),asset=null,mime="application/javascript";
  // Inline bytes are decoded by WebView. Chromium does not dispatch blob: here.
  if("data".equals(scheme))return null;
  if(uri.getUserInfo()!=null||uri.getPort()!=-1||path.contains("..")||path.contains("\\"))return blocked();
  boolean local="https".equals(scheme)&&"motion.local".equals(host);
  boolean packaged="file".equals(scheme)&&(host==null||host.isEmpty())&&path.startsWith("/android_asset/");
  if(packaged){path=path.substring("/android_asset".length());local=true;}
  boolean cdn="https".equals(scheme)&&Arrays.asList("cdnjs.cloudflare.com","cdn.jsdelivr.net","unpkg.com").contains(host);
  String name=path.substring(path.lastIndexOf('/')+1);
  if(local||cdn){if(name.equals("gsap.min.js"))asset="gsap.min.js";else if(LIBRARIES.contains(name))asset="vendor/"+name;}
  if(local&&path.equals("/fonts.css")){asset="fonts.css";mime="text/css";}
  if(local&&path.startsWith("/fonts/")&&FONTS.contains(path.substring(7))){asset=path.substring(1);mime="font/ttf";}
  if(!online&&"https".equals(scheme)&&"fonts.googleapis.com".equals(host)
    &&(path.equals("/css2")||path.equals("/css"))){
   return googleFonts(c,uri,fontNotice);
  }
  if(!online&&"fonts.gstatic.com".equals(host)&&fontNotice!=null)fontNotice.accept(FONT_BLOCKED);
  if("https".equals(scheme)&&"cdn.tailwindcss.com".equals(host)&&(path.isEmpty()||path.equals("/")))asset="studio-tailwind.js";
  if(asset!=null)try{WebResourceResponse r=new WebResourceResponse(mime,mime.equals("font/ttf")?null:"UTF-8",c.getAssets().open(asset));r.setResponseHeaders(Collections.singletonMap("Access-Control-Allow-Origin","*"));return r;}catch(IOException e){return blocked();}
  // Synthetic app origins never fall through to DNS or expose trusted UI/export routes.
  if("motion.local".equals(host)||"studio.motion.local".equals(host)||!online||!"https".equals(scheme))return blocked();
  return null;
 }
 private static WebResourceResponse googleFonts(Context context,Uri uri,IntConsumer notice){
  int result=0;
  String query=uri.getEncodedQuery();
  List<String> families=query!=null&&query.length()<=4096?uri.getQueryParameters("family"):Collections.emptyList();
  for(String group:families){
   for(String item:group.split("\\|")){
    String family=item.split(":",2)[0].trim();
    result|=FONT_FAMILIES.contains(family)?FONT_REPLACED:FONT_BLOCKED;
   }
  }
  if((result&FONT_REPLACED)!=0){
   try{
    WebResourceResponse response=new WebResourceResponse("text/css","UTF-8",context.getAssets().open("fonts.css"));
    Map<String,String> headers=new HashMap<>();
    headers.put("Access-Control-Allow-Origin","*");
    headers.put("Cache-Control","no-store");
    response.setResponseHeaders(headers);
    if(notice!=null)notice.accept(result);
    return response;
   }catch(IOException unavailable){/* Fail closed if the bundled stylesheet is unavailable. */}
  }
  if(notice!=null)notice.accept(FONT_BLOCKED);
  return blocked();
 }
}
