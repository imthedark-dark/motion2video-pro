package dev.motion2video.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.webkit.*;

/** Explicit settings for every native WebView; debug access follows the APK flag. */
final class WebViewSecurity {
 static final String RENDERER_ERROR="پردازشگر نمایش موشن متوقف شد؛ رندر لغو شد. برنامه را دوباره باز کن.";
 static final String RECOVERY_HINT="پردازشگر نمایش متوقف شد. فشار حافظه، کد سنگین یا خطای گرافیکی WebView ممکن است باعث این توقف شده باشد.";
 static String provider(){
  PackageInfo info=WebView.getCurrentWebViewPackage();
  return info==null?"نامشخص":info.packageName+" / "+info.versionName;
 }
 private static boolean debug(Context context){return (context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
 static void resetDiagnostics(WebView view){
  if(view.getWebChromeClient() instanceof PrivateWebChromeClient){
   PrivateWebChromeClient client=(PrivateWebChromeClient)view.getWebChromeClient();
   client.first="none";
   client.directive="none";
   client.blockedRequest="none";
  }
 }
 static void rendererExit(WebView view,Context context,RenderProcessGoneDetail detail,boolean online,boolean trusted){
  diagnostic(view,context,"renderer-exit",detail,online,trusted);
 }
 static void watchdog(WebView view,Context context,boolean online){
  diagnostic(view,context,"preview-timeout",null,online,false);
 }
 private static void diagnostic(WebView view,Context context,String event,RenderProcessGoneDetail detail,boolean online,boolean trusted){
  if(!debug(context))return;
  PrivateWebChromeClient client=(PrivateWebChromeClient)view.getWebChromeClient();
  android.util.Log.d("Motion2VideoWebView",event+" provider="+provider()
   +" didCrash="+(detail==null?"n/a":detail.didCrash())+" rendererPriorityAtExit="+(detail==null?"n/a":detail.rendererPriorityAtExit())
   +" policy="+(trusted?"studio":online?"import-online":"import-local-only")+" layer="+view.getLayerType()
   +" networkBlocked="+view.getSettings().getBlockNetworkLoads()+" fileAccess="+view.getSettings().getAllowFileAccess()
   +" contentAccess="+view.getSettings().getAllowContentAccess()+" safeBrowsing="+view.getSettings().getSafeBrowsingEnabled()
   +" firstConsoleCategory="+client.first+" cspDirective="+client.directive+" blockedRequest="+client.blockedRequest);
 }
 @SuppressWarnings("deprecation")
 static PrivateWebChromeClient configure(WebView view,Context context,boolean trusted){
  PrivateWebChromeClient client=new PrivateWebChromeClient(debug(context));
  view.setWebChromeClient(client);
  WebSettings s=view.getSettings();
  s.setAllowFileAccess(false);s.setAllowContentAccess(false);
  s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
  s.setDomStorageEnabled(trusted);s.setMediaPlaybackRequiresUserGesture(true);
  if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);
  // Service-worker fetches do not use a page's WebViewClient. Workers are unsupported here.
  ServiceWorkerController workers=ServiceWorkerController.getInstance();
  ServiceWorkerWebSettings workerSettings=workers.getServiceWorkerWebSettings();
  workerSettings.setAllowFileAccess(false);workerSettings.setAllowContentAccess(false);workerSettings.setBlockNetworkLoads(true);
  workers.setServiceWorkerClient(new ServiceWorkerClient(){@Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request){return StageAssets.blocked();}});
  CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
  WebView.setWebContentsDebuggingEnabled(debug(context));
  return client;
 }
 static void network(WebView view,boolean online){view.getSettings().setBlockNetworkLoads(!online);}
}
