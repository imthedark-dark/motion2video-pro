package dev.motion2video.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.webkit.*;

/** Explicit settings for every native WebView; debug access follows the APK flag. */
final class WebViewSecurity {
 static final String RENDERER_ERROR="پردازشگر نمایش موشن متوقف شد؛ رندر لغو شد. برنامه را دوباره باز کن.";
 @SuppressWarnings("deprecation")
 static void configure(WebView view,Context context,boolean trusted){
  view.setWebChromeClient(new PrivateWebChromeClient());
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
  WebView.setWebContentsDebuggingEnabled((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0);
 }
 static void network(WebView view,boolean online){view.getSettings().setBlockNetworkLoads(!online);}
}
