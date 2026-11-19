package dev.motion2video.app;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
/** Handle console output without forwarding message, source URL, line or HTML to Android logs. */
final class PrivateWebChromeClient extends WebChromeClient {
 @Override public boolean onConsoleMessage(ConsoleMessage message){return true;}
 @SuppressWarnings("deprecation")
 @Override public void onConsoleMessage(String message,int lineNumber,String sourceId){}
}
