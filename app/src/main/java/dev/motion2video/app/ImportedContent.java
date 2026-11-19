package dev.motion2video.app;

/** Policy is inserted before any imported markup/scripts. Later CSPs can only tighten it. */
public final class ImportedContent {
 private static final String LOCAL="https://motion.local";
 public static String csp(boolean online){
  String resources=online?"https: "+LOCAL:LOCAL+" https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://unpkg.com https://cdn.tailwindcss.com https://fonts.googleapis.com";
  return "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' data: blob: "+resources+"; style-src 'unsafe-inline' data: blob: "+resources+"; img-src data: blob: "+(online?"https: ":"")+LOCAL+"; font-src data: blob: "+(online?"https: ":"")+LOCAL+"; media-src data: blob: "+(online?"https: ":"")+LOCAL+"; connect-src data: blob:"+(online?" https: wss: "+LOCAL:"")+"; frame-src 'none'; child-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
 }
 public static final String CONTEXT_ERROR="زمینهٔ Canvas یا WebGL از دست رفت یا ساخته نشد؛ اندازهٔ بافت‌ها و تعداد ذرات را کاهش بده و دوباره امتحان کن.";
 public static final String CONTEXT_CHECK="window.__m2vContextFailed===true";
 public static final String READY_SCRIPT="window.__m2vReady=false;Promise.race([Promise.resolve(document.fonts?document.fonts.ready:null).catch(()=>null),new Promise(resolve=>setTimeout(resolve,3000))]).then(()=>{window.__m2vReady=true;});";
 public static final String PREVIEW_CHECK="("+CONTEXT_CHECK+"?1:0)|(window.__m2vFontBlocked===true?2:0)";

 /** Both native stages and the host fixture use the same document preparation. */
 public static String prepare(String html,String clock,String fonts,String token,boolean online){
  String injection="<script>window.__m2vReady=false;"+token+"</script><script>"+clock+"</script><style>"+fonts+"</style>";
  int head=html.toLowerCase(java.util.Locale.ROOT).indexOf("</head>");
  if(head>=0)html=html.substring(0,head)+injection+html.substring(head);
  else{int at=doctypeEnd(html);html=html.substring(0,at)+injection+html.substring(at);}
  return secure(html,online);
 }
 public static String secure(String html,boolean online){
  html=html.replace("file:///android_asset/","https://motion.local/");
  String prefix="<meta http-equiv=\"Content-Security-Policy\" content=\""+csp(online)+"\">";
  prefix+="<script>window.__m2vContextFailed=false; ['webglcontextlost','webglcontextcreationerror','contextlost'].forEach(function(type){addEventListener(type,function(){window.__m2vContextFailed=true},true)});</script>";
  if(!online)prefix+="<script>window.__m2vFontBlocked=false;addEventListener('securitypolicyviolation',function(e){if(e.effectiveDirective==='font-src'&&e.blockedURI.indexOf('https://fonts.gstatic.com/')===0)window.__m2vFontBlocked=true;});</script>";
  if(!online)prefix+="<script>['RTCPeerConnection','webkitRTCPeerConnection'].forEach(function(k){try{Object.defineProperty(window,k,{value:undefined,writable:false,configurable:false})}catch(_){}});</script>";
  int at=doctypeEnd(html);return html.substring(0,at)+prefix+html.substring(at);
 }
 private static int doctypeEnd(String html){
  java.util.regex.Matcher doctype=java.util.regex.Pattern.compile("(?is)\\A(?:\\uFEFF)?\\s*<!doctype[^>]*>").matcher(html);
  return doctype.find()?doctype.end():0;
 }
}
