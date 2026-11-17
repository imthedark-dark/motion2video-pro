package dev.motion2video.app;
import android.content.Context;import android.net.Uri;import android.webkit.*;import java.io.*;import java.util.*;
final class StageAssets {
 static String text(Context c,String name)throws IOException{try(InputStream in=c.getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 static WebResourceResponse response(Context c,Uri uri){try{String path=uri.getPath()==null?"":uri.getPath(),name=path.substring(path.lastIndexOf('/')+1),asset=null,mime="application/javascript";
  if("fonts.googleapis.com".equals(uri.getHost())){boolean known=true;List<String> families=uri.getQueryParameters("family");if(families.isEmpty())known=false;for(String family:families)for(String f:family.split("\\|"))if(!Arrays.asList("Vazirmatn","Amiri","Noto Naskh Arabic","Scheherazade New","Anton","Inter").contains(f.split(":")[0].replace('+',' ').trim()))known=false;if(known){asset="fonts.css";mime="text/css";}}
  if("motion.local".equals(uri.getHost())&&path.startsWith("/fonts/")&&!path.contains("..")){asset=path.substring(1);mime="font/ttf";}
  if(name.equals("gsap.min.js"))asset="gsap.min.js";
  if(Arrays.asList("MorphSVGPlugin.min.js","DrawSVGPlugin.min.js","SplitText.min.js","MotionPathPlugin.min.js","Flip.min.js","lottie.min.js","three.min.js").contains(name))asset="vendor/"+name;
  if(asset!=null){WebResourceResponse r=new WebResourceResponse(mime,mime.equals("font/ttf")?null:"UTF-8",c.getAssets().open(asset));r.setResponseHeaders(Collections.singletonMap("Access-Control-Allow-Origin","*"));return r;}
 }catch(Exception ignored){}return null;}
}
