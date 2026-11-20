package dev.motion2video.app;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Trusted studio UI and untrusted animation run in distinct WebViews.
 * Only the studio receives a transferred message port; no JavascriptInterface is exposed.
 * MainActivity retains the tested frame capture/MediaCodec path and legacy regression target.
 */
public final class StudioActivity extends MainActivity {
 private static final String ORIGIN="https://studio.motion.local";
 private WebView studio;
 private FrameLayout studioRoot;
 private WebMessagePort port;
 private String exportPath="";
 private boolean closed;
 private int readyPoll;
 private FontWorkflow fontWorkflow;
 private String nextPickedFontHtml;
 private boolean fontRenderPreparing;
 private DownloadRepository downloads;
 private volatile boolean saving;
 private String savedSource="";
 private JSONObject savedDownload;
 private static final int STORAGE_REQUEST=41,RENDER_PERMISSIONS=42;
 private boolean bridgeReady,studioFailed,onlinePrompt;
 private String presentedJob="";
 private RenderRequest pendingRender;
 private JSONObject studioOptions;
 private TextView loading;
 private StartupProfile startup;
 private final java.util.concurrent.ExecutorService startupWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private String restoringRender;
 private boolean permissionReturned,reportPending;
 private String pickedBeforeReady;
 private String fontCss,clockScript;
 private JSONObject pendingQueueOptions;private boolean pendingQueueStart;
 private final RenderService.Listener renderListener=this::renderState;

 @Override protected void createContent(Bundle state){
  startup=new StartupProfile(this);
  startup.mark(StartupProfile.Event.ACTIVITY);
  studioRoot=new FrameLayout(this);
  studioRoot.setBackgroundColor(Color.rgb(7,7,9));
  loading=text("در حال آماده‌سازی استودیو…",18);
  loading.setGravity(Gravity.CENTER);
  loading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  studioRoot.addView(loading,new FrameLayout.LayoutParams(-1,-1));
  setContentView(studioRoot);
  loading.setBackgroundColor(Color.rgb(7,7,9));
  permissionReturned=state!=null&&state.getBoolean("permissionReturned");
  restoringRender=state==null?null:state.getString("pendingRender");
  studioRoot.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener(){
   private boolean drawn;
   @Override public void onDraw(){
    if(drawn)return;
    drawn=true;
    studioRoot.post(()->{
     studioRoot.getViewTreeObserver().removeOnDrawListener(this);
     if(closed||isFinishing())return;
     startup.mark(StartupProfile.Event.FIRST_DRAW);
     prepareStudio(restoringRender);
    });
   }
  });
 }
 private void prepareStudio(String id){
  startupWorker.execute(()->{
   startup.mark(StartupProfile.Event.PROVIDER_BEGIN);
   // Public API since P. Load the provider before the UI thread asks for it.
   try{
    if(Build.VERSION.SDK_INT>=28)WebView.getWebViewClassLoader();
   }catch(RuntimeException | LinkageError unavailable){
    runOnUiThread(this::showStartupError);
    return;
   }
   startup.mark(StartupProfile.Event.PROVIDER_END);
   softwarePreview(); // Await the preferences XML read off the main thread.
   String css=super.asset("fonts.css"),clock=super.asset("motion-clock.js");
   RenderRequest restored=null;
   if(id!=null)try{restored=RenderRequest.read(this,id);}catch(Exception ignored){}
   final RenderRequest request=restored;
   startup.mark(StartupProfile.Event.INPUTS_READY);
   runOnUiThread(()->{
    if(closed||isFinishing())return;
    fontCss=css;clockScript=clock;pendingRender=request;restoringRender=null;
    startupStep(this::initializeStudio);
   });
  });
 }
 @Override protected String asset(String name){
  if(name.equals("fonts.css")&&fontCss!=null)return fontCss;
  if(name.equals("motion-clock.js")&&clockScript!=null)return clockScript;
  return super.asset(name);
 }
 private void startupStep(Runnable work){
  if(closed||isFinishing()||studioFailed||stageFailed)return;
  try{work.run();}catch(RuntimeException | LinkageError unavailable){showStartupError();}
 }
 private void showStartupError(){
  if(closed||isFinishing())return;
  startup.stop();
  loading.setText("استودیو باز نشد. Android System WebView را به‌روز کن و برای تلاش دوباره اینجا بزن.");
  loading.bringToFront();
  loading.setOnClickListener(v->recreate());
 }
 private void initializeStudio(){
  fontWorkflow=new FontWorkflow(this,()->working||RenderService.busy(RenderService.latest(this)));
  downloads=new DownloadRepository(this);
  startup.mark(StartupProfile.Event.STUDIO_BEGIN);
  studio=new WebView(this);
  startup.mark(StartupProfile.Event.STUDIO_CONSTRUCTED);
  studioRoot.post(()->startupStep(this::configureStudio));
 }
 private void configureStudio(){
  studio.setBackgroundColor(Color.rgb(7,7,9));
  WebSettings settings=studio.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  WebViewSecurity.configure(studio,this,true);WebViewSecurity.network(studio,false);
  studio.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
    Uri u=request.getUrl();if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1)return StageAssets.blocked();String host=u.getHost(),path=u.getPath()==null?"":u.getPath();
    try{
     if("studio.motion.local".equals(host)){
      if(path.equals("/index.html")||path.equals("/"))return assetResponse("text/html","studio.html");
      if(path.equals(exportPath)&&result!=null&&result.isFile())return exportResponse(request,result);
      return missing();
     }
     if("fonts.googleapis.com".equals(host))return assetResponse("text/css","fonts.css");
     if("motion.local".equals(host)&&path.startsWith("/fonts/")&&!path.contains(".."))return assetResponse("font/ttf",path.substring(1));
     if("cdnjs.cloudflare.com".equals(host)&&path.equals("/ajax/libs/gsap/3.12.5/gsap.min.js"))return assetResponse("application/javascript","studio-gsap-3.12.5.min.js");
     if("cdn.tailwindcss.com".equals(host))return assetResponse("application/javascript","studio-tailwind-loader.js");
    }catch(Exception e){return missing();}
    return StageAssets.blocked();
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){WebViewSecurity.rendererExit(v,StudioActivity.this,detail,false,true);startup.stop();studioFailed=true;bridgeReady=false;if(pendingRender!=null){try{RenderRequest.file(StudioActivity.this,pendingRender.id).delete();}catch(Exception ignored){}pendingRender=null;}if(port!=null){port.close();port=null;}cancelRenderAfterRendererExit();if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);v.destroy();showRendererRecovery(WebViewSecurity.RECOVERY_HINT);return true;}
   @Override public void onPageStarted(WebView v,String url,Bitmap icon){if(url.equals(ORIGIN+"/index.html")){bridgeReady=false;if(port!=null){port.close();port=null;}}}
   @Override public void onPageFinished(WebView view,String url){
    if(!url.equals(ORIGIN+"/index.html"))return;
    syncStudioAccessibility();
    if(port!=null)return;
    WebMessagePort[] pair=view.createWebMessageChannel();port=pair[0];
    port.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort p,WebMessage m){command(m.getData());}});
    view.postWebMessage(new WebMessage("motion-native-port-v1",new WebMessagePort[]{pair[1]}),Uri.parse(ORIGIN));
   }
  });
  studioRoot.addView(studio,0,new FrameLayout.LayoutParams(-1,-1));
  studioRoot.post(()->startupStep(this::attachStage));
 }
 private void attachStage(){
  startup.mark(StartupProfile.Event.STAGE_BEGIN);
  createStage();
  startup.mark(StartupProfile.Event.STAGE_READY);
  stage.setVisibility(View.INVISIBLE);studioRoot.addView(stage,new FrameLayout.LayoutParams(1,1));
  loading.bringToFront();
  if(Build.VERSION.SDK_INT>=30){
   getWindow().setDecorFitsSystemWindows(false);
   studioRoot.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets a=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());studioRoot.setPadding(a.left,a.top,a.right,a.bottom);return i;});
   studioRoot.requestApplyInsets();
  }
  studio.loadUrl(ORIGIN+"/index.html");
  startup.mark(StartupProfile.Event.LOAD_QUEUED);
  if(permissionReturned&&pendingRender!=null)beginRender();
 }
 private WebResourceResponse assetResponse(String mime,String asset)throws IOException{
  WebResourceResponse r=new WebResourceResponse(mime,mime.startsWith("font/")?null:"UTF-8",getAssets().open(asset));
  Map<String,String> headers=new HashMap<>();headers.put("Access-Control-Allow-Origin","*");if(mime.equals("text/html"))headers.put("Content-Security-Policy","default-src 'none'; script-src 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.tailwindcss.com; style-src 'unsafe-inline' https://fonts.googleapis.com; font-src https://motion.local; img-src data:; media-src https://studio.motion.local blob:; connect-src https://studio.motion.local; frame-src about:; manifest-src blob:; object-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'");r.setResponseHeaders(headers);return r;
 }
 private WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private WebResourceResponse exportResponse(WebResourceRequest req,File f)throws IOException{
  long length=f.length(),start=0,end=length-1;String range=null;
  for(Map.Entry<String,String> h:req.getRequestHeaders().entrySet())if(h.getKey().equalsIgnoreCase("Range"))range=h.getValue();
  boolean partial=range!=null;
  if(partial){try{if(!range.matches("bytes=\\d*-\\d*"))throw new Exception();String[] p=range.substring(6).split("-",-1);if(p[0].isEmpty()){start=Math.max(0,length-Long.parseLong(p[1]));}else{start=Long.parseLong(p[0]);if(!p[1].isEmpty())end=Math.min(end,Long.parseLong(p[1]));}if(start>end||start>=length)throw new Exception();}catch(Exception e){return new WebResourceResponse(resultMime,null,416,"Range Not Satisfiable",Collections.singletonMap("Content-Range","bytes */"+length),new ByteArrayInputStream(new byte[0]));}}
  FileInputStream input=new FileInputStream(f);input.getChannel().position(start);
  final long count=end-start+1;
  InputStream limited=new FilterInputStream(input){long remaining=count;@Override public int read()throws IOException{if(remaining<=0)return -1;int v=super.read();if(v>=0)remaining--;return v;}@Override public int read(byte[] b,int off,int len)throws IOException{if(remaining<=0)return -1;int n=in.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}@Override public int available()throws IOException{return (int)Math.min(remaining,Integer.MAX_VALUE);}};
  Map<String,String> h=new HashMap<>();h.put("Accept-Ranges","bytes");h.put("Content-Length",Long.toString(count));h.put("Cache-Control","no-store");if(partial)h.put("Content-Range","bytes "+start+"-"+end+"/"+length);
  return new WebResourceResponse(resultMime,null,partial?206:200,partial?"Partial Content":"OK",h,limited);
 }
 private void emit(String type,Object... entries){
  if(closed||port==null)return;
  try{JSONObject o=new JSONObject();o.put("type",type);for(int i=0;i<entries.length;i+=2)o.put((String)entries[i],entries[i+1]);port.postMessage(new WebMessage(o.toString()));}catch(OutOfMemoryError memory){memoryError();}catch(Exception ignored){}
 }
 private void memoryError(){try{if(port!=null)port.postMessage(new WebMessage("{\"type\":\""+(working?"commandError":"error")+"\",\"message\":\"حافظه کافی نیست؛ حجم HTML یا اندازهٔ خروجی را کاهش بده.\"}"));}catch(Exception|OutOfMemoryError ignored){}}
 private void command(String raw){
  if(closed||studioFailed||raw==null||raw.length()>ImportPolicy.MAX_JSON_BYTES)return;
  try{
   JSONObject m=new JSONObject(raw);String type=BridgeValidation.command(m);
   if(type.equals("hello")){
    bridgeReady=true;
    boolean first=loading!=null;
    if(first){studioRoot.removeView(loading);loading=null;startup.mark(StartupProfile.Event.BRIDGE);}
    JSONObject state=RenderService.latest(this);
    working=RenderService.busy(state);
    emit("connected","rendering",working,"onlineResources",onlineResourcesAllowed,"software",softwarePreview());
    emitDownloads();renderState(state);
    if(pickedBeforeReady!=null){String html=pickedBeforeReady;pickedBeforeReady=null;acceptPickedHtml(html);}
    if(first)startup.awaitControls(studio);
    return;
   }
   if(type.equals("fontSettings")){fontWorkflow.openSettings();return;}
   if(type.equals("about")){showAbout();return;}
   if(type.equals("troubleshooting")){showTroubleshooting();return;}
   if(type.equals("compatibility")){
    if(working||saving||pendingRender!=null)throw new IOException("رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");
    new android.app.AlertDialog.Builder(this).setTitle("حالت سازگاری پیش‌نمایش")
     .setSingleChoiceItems(new String[]{"گرافیکی (پیش‌فرض)","نرم‌افزاری (Canvas دوبعدی)"},softwarePreview()?1:0,(dialog,choice)->{
      dialog.dismiss();
      setSoftwarePreview(choice==1);
      emit("compatibility","software",softwarePreview());
      if(stageFailed)recreate();
      else if(!savedCode.isEmpty()){
       String html=savedCode;
       int token=generation;
       startupWorker.execute(()->{
        FontCache.Scope scope=AppFonts.scope(this,FontDiscovery.scan(html),false);
        runOnUiThread(()->{
         if(!closed&&!working&&!stageFailed&&token==generation&&html.equals(savedCode))preview(scope,html);
        });
       });
      }
     }).setNegativeButton("لغو",null).show();
    return;
   }
   if(type.equals("queueState")){emit("queueState","items",RenderQueue.snapshot(this));return;}
   if(type.equals("cancelQueued")){RenderService.cancelQueued(this,m.getString("id"));emit("queueState","items",RenderQueue.snapshot(this));return;}
   if(type.equals("resumeQueue")){if(importing)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");final boolean online=onlineResourcesAllowed;new Thread(()->{try{RenderQueue.resume(this,online);runOnUiThread(this::startQueueWithPermissions);}catch(Exception e){runOnUiThread(()->emit("commandError","message",PublicErrors.message(e)));}},"queue-resume").start();return;}
   if(type.equals("queueFiles")){if(importing||pendingRender!=null)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");pendingQueueOptions=new JSONObject(m.getJSONObject("options").toString()).put("onlineResources",onlineResourcesAllowed);Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(picker,4);return;}
   if(type.equals("openQueueResult")){if(working||saving)throw new IOException("رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");openQueueResult(m.getString("id"));return;}
   if(type.equals("onlineResources")){if(working||saving)throw new IOException("هنگام رندر نمی‌توان دسترسی اینترنت را تغییر داد.");requestOnlineResources(m.getBoolean("enabled"));return;}
   if(type.equals("renderState")){renderState(RenderService.latest(this));return;}
   if(type.equals("downloads")){emitDownloads();return;}
   if(type.equals("layout")){layout(m);return;}
   if(type.equals("cancel")){if(fontRenderPreparing){fontWorkflow.cancelPending();return;}if(pendingRender!=null){try{RenderRequest.file(this,pendingRender.id).delete();}catch(Exception ignored){}pendingRender=null;working=false;emit("cancelled");}else RenderService.cancel(this);return;}
   if(type.equals("capabilities")){stage.setVisibility(View.INVISIBLE);reportCapabilities();return;}
   if(type.equals("openDownloads")||type.equals("openDownload")||type.equals("shareDownload")||type.equals("save")){
    if(working||saving){emit("downloadError","message","رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");return;}
    try{if(type.equals("openDownloads"))openDownloads();else if(type.equals("openDownload"))openDownload(m.getString("uri"));else if(type.equals("shareDownload"))startActivity(downloads.shareIntent(m.getString("uri")));else saveCurrent();}catch(Exception e){emit("downloadError","message",PublicErrors.message(e));}return;
   }
   if(working||saving)throw new IOException("رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");
   if(type.equals("preview")||type.equals("render")){
    String html=m.getString("html");ImportPolicy.validate(html);
    boolean exportRequested=type.equals("render");
    if(fontWorkflow.busy()){
     if(exportRequested&&!fontRenderPreparing)emit("error","message","ابتدا بررسی فونت یا تنظیمات را تمام کن.");
     return;
    }
    boolean explicit=exportRequested||html.equals(nextPickedFontHtml);
    nextPickedFontHtml=null;
    configure(m.getJSONObject("options"));
    savedCode=html;
    fontRenderPreparing=exportRequested;
    if(exportRequested){
     working=true;
     emit("renderState","state",new JSONObject().put("phase","preparing").put("message","در حال بررسی فونت‌ها…"));
    }
    fontWorkflow.resolve(()->FontDiscovery.scan(html),explicit,this::fontSummary,scope->{
     fontRenderPreparing=false;
     if(exportRequested){working=false;export();}
     else if(!working&&!stageFailed)preview(scope,html);
    },()->{
     fontRenderPreparing=false;
     if(exportRequested){working=false;emit("cancelled");}
     else emit("commandError","message","پیش‌نمایش لغو شد.");
    });
    return;
   }
   if(type.equals("clearAudioAssets")){if(RenderQueue.pending(this))throw new IOException("ابتدا کارهای صف را تمام یا لغو کن.");if(importing)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");AudioAssets.clear(this);emit("audioCleared");return;}
   if(type.equals("chooseAudio")){if(importing)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,3);return;}
   if(type.equals("chooseFile")){if(importing)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);return;}
  }catch(OutOfMemoryError memory){memoryError();}catch(Exception e){emit(working?"commandError":"error","message",PublicErrors.message(e));}
 }
 private void configure(JSONObject o)throws Exception{
  double sec=o.getDouble("duration");int rate=o.getInt("fps"),w=o.getInt("width"),h=o.getInt("height"),bits=o.getInt("bitrate");String mime=o.getString("mime");
  if(!Double.isFinite(sec)||sec<1||sec>300||(rate!=30&&rate!=60)||(bits!=12000000&&bits!=16000000&&bits!=20000000))throw new IOException("تنظیمات رندر معتبر نیست.");
  int size=-1;int[][] sizes={{640,360},{1280,720},{1920,1080},{1080,1920},{1080,1080}};
  for(int i=0;i<sizes.length;i++)if(w==sizes[i][0]&&h==sizes[i][1])size=i;
  int codec=Arrays.asList(CodecSupport.MIME).indexOf(mime);if(size<0||codec<0)throw new IOException("اندازه یا کدک پشتیبانی نمی‌شود.");
  studioOptions=new JSONObject().put("duration",sec).put("fps",rate).put("width",w).put("height",h)
   .put("bitrate",bits).put("mime",mime).put("gpu",o.optBoolean("gpu",false));
  for(String key:new String[]{"rangeStart","rangeEnd","audio"})if(o.has(key))studioOptions.put(key,o.get(key));
  exportWidth=w;exportHeight=h;
 }
 private void layout(JSONObject m)throws Exception{
  if(stageFailed)return;
  double scale=m.getDouble("dpr"),x=m.getDouble("x"),y=m.getDouble("y"),w=m.getDouble("width"),h=m.getDouble("height"),clip=m.optDouble("clipTop",0);
  if(!Double.isFinite(scale)||scale<=0||scale>10||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||!Double.isFinite(clip)||w<1||h<1||w*scale>10000||h*scale>10000)return;
  FrameLayout.LayoutParams p=new FrameLayout.LayoutParams((int)Math.round(w*scale),(int)Math.round(h*scale));p.leftMargin=(int)Math.round(x*scale);p.topMargin=(int)Math.round(y*scale);stage.setLayoutParams(p);
  stage.setClipBounds(new Rect(0,(int)Math.min(p.height,Math.max(0,clip*scale)),p.width,p.height));stage.setVisibility(m.optBoolean("visible",true)?View.VISIBLE:View.INVISIBLE);
 }
 @Override protected void onStagePageFinished(){
  if(studio==null||stageFailed||working)return;final int token=++readyPoll;pollReady(token,0);
 }
 private void pollReady(int token,int attempts){
  if(closed||stageFailed||working||token!=readyPoll||attempts>150)return;
  stage.evaluateJavascript("window.__m2vReady&&window.__m2vDuration?window.__m2vDuration():null",v->{
   if(closed||stageFailed||token!=readyPoll)return;
   if(v!=null&&!v.equals("null")){try{emit("ready","duration",Double.parseDouble(v));}catch(Exception ignored){}}else stage.postDelayed(()->pollReady(token,attempts+1),200);
  });
 }
 private void requestOnlineResources(boolean enable){
  if(onlinePrompt){emit("onlineResources","enabled",onlineResourcesAllowed);return;}
  if(!enable){onlineResourcesAllowed=false;WebViewSecurity.network(stage,false);emit("onlineResources","enabled",false);return;}
  onlinePrompt=true;
  new android.app.AlertDialog.Builder(this).setTitle("اجازهٔ منابع آنلاین؟").setMessage("کد واردشده می‌تواند به سرورهای اینترنتی درخواست بفرستد و محتوای شما را ارسال کند. این اجازه فقط برای همین بار بازبودن برنامه است. فقط برای کد مورد اعتماد فعال کن.").setPositiveButton("اجازه می‌دهم",(d,w)->{onlineResourcesAllowed=true;WebViewSecurity.network(stage,true);}).setNegativeButton("لغو",null).setOnDismissListener(d->{onlinePrompt=false;emit("onlineResources","enabled",onlineResourcesAllowed);}).show();
 }
 private void cancelRenderAfterRendererExit(){
  if(RenderService.busy(RenderService.latest(this)))RenderService.rendererGone(this);
 }
 @Override protected void onStageRendererGone(){
  cancelRenderAfterRendererExit();
  showRendererRecovery(WebViewSecurity.RECOVERY_HINT);
 }
 private void fontSummary(String text){emit("fontSummary","text",text);}
 @Override protected void onFontNotice(int flags){emit("fontNotice","replaced",(flags&StageAssets.FONT_REPLACED)!=0,"blocked",(flags&StageAssets.FONT_BLOCKED)!=0);}
 @Override protected void onStageContextLost(){emit("commandError","message",ImportedContent.CONTEXT_ERROR);}
 @Override protected void onRenderProgress(int done,int total){emit("progress","done",done,"total",total);}
 @Override protected void onRenderResult(File file){exportPath="/exports/"+UUID.randomUUID()+"."+resultExt;emit("result","url",ORIGIN+exportPath,"ext",resultExt,"size",file.length());saveCurrent();}
 private void reportCapabilities(){
  if(reportPending)return;
  reportPending=true;
  startupWorker.execute(()->{
   try{
    String report=CodecSupport.report();
    runOnUiThread(()->{reportPending=false;emit("capabilities","text",report);});
   }catch(RuntimeException unavailable){
    runOnUiThread(()->{reportPending=false;emit("commandError","message","فهرست کدک‌ها در دسترس نیست؛ دوباره امتحان کن.");});
   }
  });
 }
 private void emitDownloads(){
  if(closed)return;
  startupWorker.execute(()->{
   JSONArray items=downloads.list();
   runOnUiThread(()->emit("downloads","items",items,"folder",DownloadRepository.RELATIVE));
  });
 }
 private void saveCurrent(){
  if(saving)return;
  File source=result;
  if(source==null||!source.isFile()){emit("downloadError","message","ابتدا یک ویدیو بساز.");return;}
  if(source.getAbsolutePath().equals(savedSource)&&savedDownload!=null){
   try{downloads.openIntent(savedDownload.getString("uri"));emit("saved","item",savedDownload);emitDownloads();return;}catch(Exception ignored){savedSource="";savedDownload=null;}
  }
  saving=true;emit("saving");
  if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_REQUEST);return;}
  final String mime=resultMime,ext=resultExt;
  new Thread(()->{try{
   JSONObject item=downloads.save(source,mime,ext);
   runOnUiThread(()->{saving=false;savedSource=source.getAbsolutePath();savedDownload=item;RenderService.recordDownload(this,source.getAbsolutePath(),item);emit("saved","item",item);emitDownloads();});
  }catch(Exception e){runOnUiThread(()->{saving=false;emit("downloadError","message","ذخیره در دانلودها انجام نشد؛ فضای خالی و دسترسی حافظه را بررسی کن. "+(PublicErrors.message(e)));});}},"save-download").start();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==RENDER_PERMISSIONS){
   if(PermissionPolicy.notifications(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)emit("permissionNotice","message","اعلان‌ها مجاز نیستند؛ رندر ادامه دارد. برای دیدن پیشرفت یا توقف، به برنامه برگرد.");
   if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)emit("permissionNotice","message","دسترسی دانلودها مجاز نیست؛ ویدیو در برنامه می‌ماند و بعداً می‌توانی دوباره ذخیره کنی.");
   if(restoringRender!=null||(pendingRender!=null&&stage==null))permissionReturned=true;else if(pendingRender!=null)beginRender();else if(pendingQueueStart){pendingQueueStart=false;try{RenderService.startQueue(this);}catch(Exception e){emit("commandError","message",PublicErrors.message(e));}}return;
  }
  if(request==STORAGE_REQUEST){saving=false;if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)saveCurrent();else emit("downloadError","message","در اندروید ۸ و ۹ برای ذخیره در دانلودها، دسترسی حافظه لازم است. ویدیو هنوز برای ذخیرهٔ مجدد در دسترس است.");}
 }
 @Override protected void export(){
  try{
   if(stageFailed||studioFailed)throw new IOException(WebViewSecurity.RENDERER_ERROR);
   if(importing)throw new IOException("خواندن فایل قبلی هنوز تمام نشده است.");
   if(RenderService.busy(RenderService.latest(this)))throw new IOException("یک رندر دیگر در حال اجراست.");
   studioOptions.put("onlineResources",onlineResourcesAllowed);
   final RenderRequest candidate=RenderRequest.create(savedCode,studioOptions);pendingRender=candidate;working=true;
   new Thread(()->{boolean videoSupported=false;try{CodecSupport.encoder(candidate.mime,candidate.width,candidate.height,candidate.fps,candidate.bitrate);videoSupported=true;if(candidate.audio!=null)AudioComposer.validate(this,candidate);runOnUiThread(()->{if(closed||pendingRender!=candidate)return;try{authorizeRender(candidate);}catch(Exception e){working=false;pendingRender=null;emit("error","message",PublicErrors.message(e));}});}
    catch(Throwable unsupported){org.json.JSONObject proposed=null;if(!videoSupported&&!(unsupported instanceof OutOfMemoryError))try{proposed=CodecSupport.suggestion(candidate);}catch(Throwable ignored){}final org.json.JSONObject next=proposed;
     runOnUiThread(()->{if(closed||pendingRender!=candidate)return;pendingRender=null;working=false;emit("error","message",PublicErrors.message(unsupported));offerSuggestion(next);});}
   },"codec-capability").start();
  }catch(Exception e){working=false;pendingRender=null;emit("error","message",PublicErrors.message(e));}
 }
 private void offerSuggestion(JSONObject options){if(options==null||closed||isFinishing()||working)return;new android.app.AlertDialog.Builder(this).setTitle("پیشنهاد سازگار با دستگاه").setMessage(options.optInt("width")+" × "+options.optInt("height")+" / "+options.optInt("fps")+" FPS / "+options.optString("mime")+"\nاین پیشنهاد خودکار رندر نمی‌شود؛ پس از اعمال دوباره تبدیل یا افزودن به صف را بزن.").setPositiveButton("اعمال پیشنهاد",(d,w)->{try{if(working||saving)throw new IOException("رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");configure(options);emit("suggestedOptions","options",options);}catch(Exception e){emit("commandError","message",PublicErrors.message(e));}}).setNegativeButton("لغو",null).show();}
 private void authorizeRender(RenderRequest candidate)throws Exception {
  getPreferences(0).edit().putString("code",candidate.html).apply();
   ArrayList<String> permissions=new ArrayList<>();
   if(PermissionPolicy.notifications(android.os.Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
   if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
   if(!permissions.isEmpty()){candidate.write(this);working=true;requestPermissions(permissions.toArray(new String[0]),RENDER_PERMISSIONS);}else beginRender();
 }
 private void startQueueWithPermissions(){
  if(closed)return;try{ArrayList<String> needed=new ArrayList<>();if(PermissionPolicy.notifications(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)needed.add(android.Manifest.permission.POST_NOTIFICATIONS);if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)needed.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);if(!needed.isEmpty()){pendingQueueStart=true;requestPermissions(needed.toArray(new String[0]),RENDER_PERMISSIONS);}else RenderService.startQueue(this);}catch(Exception e){emit("commandError","message",PublicErrors.message(e));}
 }
 private void openQueueResult(String id)throws Exception{
  JSONObject item=RenderQueue.item(this,id);if(!item.getString("phase").equals("complete"))throw new IOException(QueueLedger.ERROR);JSONObject o=item.getJSONObject("options");String mime=o.getString("mime"),ext=CodecSupport.webm(mime)?"webm":"mp4";File f=new File(getCacheDir(),"render-"+id+"."+ext);if(!f.isFile())throw new FileNotFoundException();result=f;resultMime="video/"+ext;resultExt=ext;exportPath="/exports/"+id+"."+ext;savedSource="";savedDownload=null;emit("result","url",ORIGIN+exportPath,"ext",ext,"size",f.length(),"options",o);
 }
 private void importQueue(Intent data,JSONObject options){
  if(importing)return;importing=true;emit("queueImporting");new Thread(()->{JSONArray prepared=new JSONArray();boolean handedOff=false;FontDiscovery fonts=new FontDiscovery();try{
   ArrayList<Uri> uris=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null){if(clip.getItemCount()>8)throw new IOException(QueueLedger.ERROR);for(int i=0;i<clip.getItemCount();i++)uris.add(clip.getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())throw new IOException(QueueLedger.ERROR);
   long staged=0;File[] existing=new File(getCacheDir(),"render-jobs").listFiles();if(existing!=null)for(File f:existing)staged+=f.length();if(staged>256L*1024*1024)throw new IOException(QueueLedger.ERROR);
   long bytes=0;for(Uri uri:uris){String html=HtmlImport.read(getContentResolver(),uri);fonts.merge(FontDiscovery.scan(html));long count=html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;bytes+=count;if(bytes>60L*1024*1024)throw new IOException(QueueLedger.ERROR);if(new android.os.StatFs(getCacheDir().getAbsolutePath()).getAvailableBytes()<count*2+32L*1024*1024)throw new IOException("فضای خالی برای فایل‌های صف کافی نیست.");RenderRequest job=RenderRequest.create(html,options);if(prepared.length()==0){try{CodecSupport.encoder(job.mime,job.width,job.height,job.fps,job.bitrate);}catch(Exception unsupported){JSONObject proposed=CodecSupport.suggestion(job);runOnUiThread(()->offerSuggestion(proposed));throw unsupported;}if(job.audio!=null)AudioComposer.validate(this,job);}String name="موشن "+(prepared.length()+1);try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&n.length()<=240)name=n;}}
    JSONObject entry=new JSONObject().put("id",job.id).put("name",name).put("bytes",count).put("total",job.frames).put("options",job.options());prepared.put(entry);job.write(this);
   }
   runOnUiThread(()->{
    if(closed){endQueueImport(prepared,false);return;}
    fontWorkflow.resolve(()->fonts,true,this::fontSummary,scope->admitQueue(prepared),()->endQueueImport(prepared,false));
   });
   handedOff=true;
  }catch(Throwable e){runOnUiThread(()->emit("commandError","message",PublicErrors.message(e)));}
  finally{if(!handedOff)endQueueImport(prepared,false);}
  },"queue-import").start();
 }
 private void admitQueue(JSONArray prepared){
  new Thread(()->{
   boolean admitted=false;
   try{
    RenderQueue.enqueue(this,prepared);
    admitted=true;
    runOnUiThread(()->{emit("queueState","items",RenderQueue.snapshot(this));startQueueWithPermissions();});
   }catch(Exception error){runOnUiThread(()->emit("commandError","message",PublicErrors.message(error)));}
   finally{endQueueImport(prepared,admitted);}
  },"queue-admission").start();
 }
 private void endQueueImport(JSONArray prepared,boolean admitted){
  if(!admitted)for(int i=0;i<prepared.length();i++)RenderQueue.cleanup(this,prepared.optJSONObject(i).optString("id"));
  importing=false;
  runOnUiThread(()->emit("queueIdle"));
 }
 private void beginRender(){
  permissionReturned=false;RenderRequest request=pendingRender;pendingRender=null;if(request==null){working=false;emit("error","message","درخواست رندر موجود نیست؛ دوباره اجرا کن.");return;}
  try{RenderService.start(this,request);working=true;stage.onPause();emit("renderState","state",new JSONObject().put("phase","preparing").put("id",request.id).put("done",0).put("total",request.frames).put("options",request.options()).put("message","آماده‌سازی رندر مستقل…"));}
  catch(Exception e){working=false;emit("error","message","شروع سرویس رندر انجام نشد: "+PublicErrors.message(e));}
 }
 private void renderState(JSONObject s){
  if(closed||!bridgeReady||s.optString("phase").isEmpty())return;
  if(fontRenderPreparing&&!RenderService.busy(s))return;
  working=RenderService.busy(s);
  if(working){if(!stageFailed)stage.onPause();emit("renderState","state",s);return;}
  if(!stageFailed)stage.onResume();String id=s.optString("id"),phase=s.optString("phase");
  if((id+":"+phase).equals(presentedJob))return;presentedJob=id+":"+phase;
  if(phase.equals("complete")){
   File file=new File(s.optString("file"));if(!file.isFile()){emit("error","message","فایل موقت خروجی دیگر موجود نیست؛ دانلودهای من را بررسی کن.");return;}
   result=file;resultExt=s.optString("ext","mp4");resultMime=s.optString("mime","video/mp4");exportPath="/exports/"+id+"."+resultExt;
   emit("result","url",ORIGIN+exportPath,"ext",resultExt,"size",file.length(),"options",s.optJSONObject("options"),"elapsedMs",s.optLong("elapsedMs"),"frames",s.optInt("total"));
   JSONObject item=s.optJSONObject("download");if(item!=null){savedSource=file.getAbsolutePath();savedDownload=item;emit("saved","item",item);}else if(s.has("warning"))emit("downloadError","message",s.optString("warning"));
   emitDownloads();
  }else if(phase.equals("cancelled"))emit("cancelled");else{String message=s.optString("message","رندر متوقف شد.");emit("error","message",message);if(WebViewSecurity.RENDERER_ERROR.equals(message)&&!s.optString("id").equals(getPreferences(0).getString("last_renderer_notice",""))){getPreferences(0).edit().putString("last_renderer_notice",s.optString("id")).apply();showRendererRecovery(WebViewSecurity.RECOVERY_HINT);}}
 }
 private void syncStudioAccessibility(){
  if(studio==null||studioFailed)return;
  studio.getSettings().setTextZoom(Math.round(100*getResources().getConfiguration().fontScale));
  boolean reduced=!android.animation.ValueAnimator.areAnimatorsEnabled();
  studio.evaluateJavascript("window.MotionStudio && MotionStudio.systemMotion("+reduced+")",null);
 }
 @Override protected void onResume(){super.onResume();syncStudioAccessibility();}
 @Override protected void onStart(){super.onStart();RenderService.addListener(renderListener);if(bridgeReady)renderState(RenderService.latest(this));}
 @Override protected void onStop(){RenderService.removeListener(renderListener);super.onStop();}
 @Override protected void onSaveInstanceState(Bundle out){if(pendingRender!=null)out.putString("pendingRender",pendingRender.id);else if(restoringRender!=null)out.putString("pendingRender",restoringRender);out.putBoolean("permissionReturned",permissionReturned);super.onSaveInstanceState(out);}
 private void openDownload(String uri)throws Exception{
  try{startActivity(downloads.openIntent(uri));}catch(android.content.ActivityNotFoundException e){throw new IOException("پخش‌کنندهٔ سازگار روی گوشی پیدا نشد.");}catch(FileNotFoundException e){throw new IOException("فایل پیدا نشد؛ ممکن است از دانلودها پاک یا جابه‌جا شده باشد.");}
 }
 private void openDownloads()throws IOException{
  Intent folder=new Intent(Intent.ACTION_VIEW).setDataAndType(DownloadRepository.folderUri(),"vnd.android.document/directory");
  try{startActivity(folder);return;}catch(android.content.ActivityNotFoundException|SecurityException ignored){}
  try{startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(android.content.ActivityNotFoundException e){throw new IOException("برنامهٔ مدیریت دانلود روی گوشی پیدا نشد. مسیر فایل‌ها: "+DownloadRepository.RELATIVE);}
 }
 @Override protected void onRenderError(String error){emit(cancelled?"cancelled":"error","message",error==null?"رندر انجام نشد.":error);}
 @Override protected void onVideoSaved(){emit("saved");}
 private void acceptPickedHtml(String html){
  if(!bridgeReady){pickedBeforeReady=html;return;}
  fontWorkflow.newImport();nextPickedFontHtml=html.trim();emit("file","code",html);
 }
 @Override protected void onActivityResult(int r,int code,Intent data){
  if(r==4){JSONObject options=pendingQueueOptions;pendingQueueOptions=null;if(code==RESULT_OK&&data!=null&&options!=null)importQueue(data,options);return;}
  if(r==3){if(code==RESULT_OK&&data!=null&&data.getData()!=null&&!importing){importing=true;emit("audioImporting");new Thread(()->{try{JSONObject item=AudioAssets.importFile(this,data.getData());runOnUiThread(()->{if(!working&&!saving&&!closed)emit("audioSelected","item",item);});}catch(Throwable e){runOnUiThread(()->emit("commandError","message",PublicErrors.message(e)));}finally{importing=false;runOnUiThread(()->emit("audioIdle"));}},"audio-import").start();}return;}
  if(r==1){if(code==RESULT_OK&&data!=null&&data.getData()!=null){if(importing)return;importing=true;new Thread(()->{try{String html=HtmlImport.read(getContentResolver(),data.getData());runOnUiThread(()->{if(!working&&!saving&&!closed)acceptPickedHtml(html);});}catch(OutOfMemoryError memory){runOnUiThread(this::memoryError);}catch(Exception e){runOnUiThread(()->emit("commandError","message",PublicErrors.message(e)));}finally{importing=false;}},"html-import").start();}return;}
  super.onActivityResult(r,code,data);
 }
 @Override protected void onDestroy(){closed=true;startupWorker.shutdownNow();if(startup!=null)startup.stop();readyPoll++;if(fontWorkflow!=null)fontWorkflow.close();if(port!=null)port.close();if(studio!=null&&!studioFailed)studio.destroy();super.onDestroy();}
}
