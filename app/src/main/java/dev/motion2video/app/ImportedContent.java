package dev.motion2video.app;

/** Policy is inserted before any imported markup/scripts. Later CSPs can only tighten it. */
public final class ImportedContent {
 private static final String LOCAL="https://motion.local";
 public static String csp(boolean online){
  String resources=online?"https: "+LOCAL:LOCAL+" https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://unpkg.com https://cdn.tailwindcss.com https://fonts.googleapis.com";
  return "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' data: blob: "+resources+"; style-src 'unsafe-inline' "+resources+"; img-src data: blob: "+(online?"https: ":"")+LOCAL+"; font-src data: "+(online?"https: ":"")+LOCAL+"; media-src data: blob: "+(online?"https: ":"")+LOCAL+"; connect-src "+(online?"https: wss: "+LOCAL:"'none'")+"; frame-src 'none'; child-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
 }
 public static String secure(String html,boolean online){
  html=html.replace("file:///android_asset/","https://motion.local/");
  String prefix="<meta http-equiv=\"Content-Security-Policy\" content=\""+csp(online)+"\">";
  if(!online)prefix+="<script>['RTCPeerConnection','webkitRTCPeerConnection'].forEach(function(k){try{Object.defineProperty(window,k,{value:undefined,writable:false,configurable:false})}catch(_){}});</script>";
  java.util.regex.Matcher doctype=java.util.regex.Pattern.compile("(?is)\\A(?:\\uFEFF)?\\s*<!doctype[^>]*>").matcher(html);
  int at=doctype.find()?doctype.end():0;return html.substring(0,at)+prefix+html.substring(at);
 }
}
