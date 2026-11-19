package dev.motion2video.app;

import android.app.Presentation;
import android.content.Context;
import android.graphics.*;
import android.hardware.display.*;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.*;

/** A private, app-owned virtual display keeps the render WebView attached and
 * visible independently of the Activity. No overlay or screen-capture permission.
 */
final class OffscreenStage implements AutoCloseable {
 private final Context context;private final RenderRequest request;private final Handler main=new Handler(Looper.getMainLooper());
 private VirtualDisplay display;private ImageReader sink;private Presentation window;private WebView web;private WebMessagePort clock;
 private volatile IOException rendererFailure;
 private volatile boolean closed,attached;private boolean pageFinished;private Capture pending;
 private static final class Capture{final int token;final Bitmap bitmap;final CompletableFuture<Void> done=new CompletableFuture<>();Capture(int t,Bitmap b){token=t;bitmap=b;}}
 OffscreenStage(Context context,RenderRequest request)throws Exception{this.context=context;this.request=request;onMain(()->{create();return null;});}
 private <T>T onMain(Callable<T> action)throws Exception{if(Looper.myLooper()==Looper.getMainLooper())return action.call();FutureTask<T> task=new FutureTask<>(action);main.post(task);return task.get(45,TimeUnit.SECONDS);}
 private void create()throws Exception{
  try{
   sink=ImageReader.newInstance(request.width,request.height,PixelFormat.RGBA_8888,2);
   sink.setOnImageAvailableListener(reader->{try(android.media.Image image=reader.acquireLatestImage()){}catch(Exception ignored){}},main);
   DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
   display=manager.createVirtualDisplay("Motion2VideoRender",request.width,request.height,160,sink.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
   if(display==null)throw new IOException("نمایشگر رندر ساخته نشد.");
   window=new Presentation(context,display.getDisplay(),dev.motion2video.app.R.style.AppTheme);
   window.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);
   window.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
   window.getWindow().setBackgroundDrawableResource(android.R.color.black);
   web=new WebView(window.getContext());web.setBackgroundColor(Color.BLACK);web.setLayerType(request.gpu?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_SOFTWARE,null);
   WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);
   WebViewSecurity.configure(web,context,false);WebViewSecurity.network(web,request.onlineResources);
   web.setWebViewClient(new WebViewClient(){
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return StageAssets.response(context,r.getUrl(),request.onlineResources);}
    @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){rendererFailure=new IOException(WebViewSecurity.RENDERER_ERROR);RenderService.rendererGone(context);closeNow();return true;}
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
    @Override public void onPageFinished(WebView v,String url){
     if(closed||pageFinished)return;pageFinished=true;
     WebMessagePort[] ports=web.createWebMessageChannel();clock=ports[0];
     clock.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort p,WebMessage m){
      if(closed)return;String text=m.getData();if(text==null||text.length()>32)return;if("attached".equals(text)){attached=true;return;}
      Capture capture=pending;if(capture==null||!String.valueOf(capture.token).equals(text))return;
      web.postVisualStateCallback(capture.token,new WebView.VisualStateCallback(){@Override public void onComplete(long token){if(closed||pending!=capture)return;draw(capture);}});
     }});
     web.postWebMessage(new WebMessage("motion-clock-port-v1",new WebMessagePort[]{ports[1]}),Uri.parse("https://motion.local"));
     web.evaluateJavascript("window.__m2vReady=false;Promise.all([document.fonts?document.fonts.ready:Promise.resolve(),...['Vazirmatn','Amiri','Noto Naskh Arabic','Scheherazade New','Anton','Inter'].map(f=>document.fonts.load('16px '+JSON.stringify(f))),...Array.from(document.images).map(i=>i.complete?Promise.resolve():new Promise(r=>{i.onload=i.onerror=r}))]).then(()=>{window.__m2vReady=true});",null);
    }
   });
   FrameLayout root=new FrameLayout(window.getContext());root.setBackgroundColor(Color.BLACK);root.addView(web,new FrameLayout.LayoutParams(-1,-1));window.setContentView(root);window.show();window.getWindow().setLayout(request.width,request.height);
   String code=request.html.replaceAll("(?i)inset\\s*:\\s*0(?:px)?\\s*;","top:0;right:0;bottom:0;left:0;inset:0;");
   String injection="<script>window.__m2vReady=false;</script><script>"+StageAssets.text(context,"motion-clock.js")+"</script><style>"+StageAssets.text(context,"fonts.css")+"</style>";
   int head=code.toLowerCase(java.util.Locale.ROOT).indexOf("</head>");code=head>=0?code.substring(0,head)+injection+code.substring(head):injection+code;
   web.loadDataWithBaseURL("https://motion.local/",ImportedContent.secure(code,request.onlineResources),"text/html","UTF-8",null);
  }catch(Throwable e){closeNow();throw new IOException("ساخت محیط مستقل رندر انجام نشد: "+e.getClass().getSimpleName(),e);}
 }
 private String js(String script)throws Exception{CompletableFuture<String> f=new CompletableFuture<>();main.post(()->{if(closed){f.completeExceptionally(rendererFailure!=null?rendererFailure:new IOException("رندر بسته شد."));return;}web.evaluateJavascript(script,f::complete);});return f.get(45,TimeUnit.SECONDS);}
 void awaitReady(java.util.function.BooleanSupplier cancelled)throws Exception{
  long end=System.currentTimeMillis()+120000;
  while(true){if(rendererFailure!=null)throw rendererFailure;if(cancelled.getAsBoolean())throw new IOException("رندر لغو شد.");if(System.currentTimeMillis()>end)throw new IOException("HTML، قلم‌ها یا اتصال ساعت رندر آماده نشد.");
   if(attached&&"true".equals(js("window.__m2vReady===true&&(!window.lottie||!lottie.getRegisteredAnimations||lottie.getRegisteredAnimations().every(a=>a.isLoaded))"))&&onMain(()->web.getWidth()==request.width&&web.getHeight()==request.height))return;
   Thread.sleep(100);
  }
 }
 void capture(double time,int token,Bitmap target)throws Exception{
  Capture c=new Capture(token,target);main.post(()->{
   if(closed){c.done.completeExceptionally(rendererFailure!=null?rendererFailure:new IOException("رندر بسته شد."));return;}
   pending=c;web.evaluateJavascript("window.__m2vSeek("+time+","+token+")",value->{if(!"true".equals(value))c.done.completeExceptionally(new IOException("کنترل زمان موشن خطا داد."));});
  });
  c.done.get(45,TimeUnit.SECONDS);
 }
 private void draw(Capture c){
  try{
   if(request.gpu){PixelCopy.request(window.getWindow(),new Rect(0,0,request.width,request.height),c.bitmap,status->{if(status==PixelCopy.SUCCESS)c.done.complete(null);else c.done.completeExceptionally(new IOException("ضبط GPU انجام نشد: "+status));if(pending==c)pending=null;},main);}
   else{Canvas canvas=new Canvas(c.bitmap);canvas.drawColor(Color.BLACK);web.draw(canvas);c.done.complete(null);if(pending==c)pending=null;}
  }catch(Throwable e){c.done.completeExceptionally(e);if(pending==c)pending=null;}
 }
 void cancelPending(){main.post(()->{Capture c=pending;if(c!=null)c.done.completeExceptionally(new IOException("رندر لغو شد."));pending=null;});}
 private void closeNow(){
  closed=true;Capture c=pending;pending=null;if(c!=null)c.done.completeExceptionally(rendererFailure!=null?rendererFailure:new IOException("رندر بسته شد."));
  if(clock!=null){try{clock.close();}catch(RuntimeException ignored){}clock=null;}
  if(web!=null){WebView old=web;web=null;try{if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);}catch(RuntimeException ignored){}try{if(rendererFailure==null)old.stopLoading();}catch(RuntimeException ignored){}try{old.destroy();}catch(RuntimeException ignored){}}
  if(window!=null){try{window.dismiss();}catch(RuntimeException ignored){}window=null;}
  if(display!=null){try{display.release();}catch(RuntimeException ignored){}display=null;}
  if(sink!=null){try{sink.close();}catch(RuntimeException ignored){}sink=null;}
 }
 @Override public void close(){try{onMain(()->{closeNow();return null;});}catch(Exception ignored){}}
}
