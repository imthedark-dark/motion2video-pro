package dev.motion2video.app;

import android.net.Uri;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import java.util.Locale;

/** Receives content errors but retains only a fixed, non-content category in debug builds. */
final class PrivateWebChromeClient extends WebChromeClient {
 private final boolean debug;
 String first="none";
 String directive="none";
 volatile String blockedRequest="none";
 void blocked(Uri uri){
  if(!debug||!blockedRequest.equals("none"))return;
  String host=uri.getHost();
  if("fonts.googleapis.com".equals(host))blockedRequest="google-font-stylesheet";
  else if("fonts.gstatic.com".equals(host))blockedRequest="google-font-binary";
  else if("motion.local".equals(host))blockedRequest="bundled-resource";
  else blockedRequest="other-resource";
 }

 PrivateWebChromeClient(boolean debug){this.debug=debug;}

 @Override public boolean onConsoleMessage(ConsoleMessage entry){
  if(!debug||entry.messageLevel()!=ConsoleMessage.MessageLevel.ERROR)return true;
  String message=entry.message().toLowerCase(Locale.ROOT);
  String category="script-or-resource";
  if(message.contains("content security policy")||message.contains("content-security-policy")){
   category="csp";
   if(directive.equals("none"))for(String name:new String[]{"connect-src","script-src","style-src","img-src","font-src","media-src","worker-src","frame-src","default-src"}){
    if(message.contains(name)){directive=name;break;}
   }
  }else if(message.contains("webgl")||message.contains("context lost"))category="graphics";
  else if(message.contains("failed to load")||message.contains("failed to fetch"))category="resource";
  if(first.equals("none"))first=category;
  return true;
 }

 @SuppressWarnings("deprecation")
 @Override public void onConsoleMessage(String message,int lineNumber,String sourceId){}
}
