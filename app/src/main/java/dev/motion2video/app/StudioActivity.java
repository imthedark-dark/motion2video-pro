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
 private DownloadRepository downloads;
 private volatile boolean saving;
 private String savedSource="";
 private JSONObject savedDownload;
 private static final int STORAGE_REQUEST=41,RENDER_PERMISSIONS=42;
 private boolean bridgeReady,studioFailed,onlinePrompt;
 private String presentedJob="";
 private RenderRequest pendingRender;
 private final RenderService.Listener renderListener=this::renderState;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  downloads=new DownloadRepository(this);
  if(state!=null&&state.getString("pendingRender")!=null)try{pendingRender=RenderRequest.read(this,state.getString("pendingRender"));}catch(Exception ignored){}
  ((ViewGroup)stage.getParent()).removeView(stage);
  studioRoot=new FrameLayout(this);studioRoot.setBackgroundColor(Color.rgb(7,7,9));
  studio=new WebView(this);studio.setBackgroundColor(Color.rgb(7,7,9));
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
   @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){studioFailed=true;bridgeReady=false;if(pendingRender!=null){try{RenderRequest.file(StudioActivity.this,pendingRender.id).delete();}catch(Exception ignored){}pendingRender=null;}if(port!=null){port.close();port=null;}RenderService.rendererGone(StudioActivity.this);if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);v.destroy();if(!isFinishing())new android.app.AlertDialog.Builder(StudioActivity.this).setTitle("خطای نمایش برنامه").setMessage(WebViewSecurity.RENDERER_ERROR).setPositiveButton("بستن صفحه",(d,w)->finish()).setCancelable(false).show();return true;}
   @Override public void onPageStarted(WebView v,String url,Bitmap icon){if(url.equals(ORIGIN+"/index.html")){bridgeReady=false;if(port!=null){port.close();port=null;}}}
   @Override public void onPageFinished(WebView view,String url){
    if(!url.equals(ORIGIN+"/index.html"))return;
    if(port!=null)return;
    WebMessagePort[] pair=view.createWebMessageChannel();port=pair[0];
    port.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort p,WebMessage m){command(m.getData());}});
    view.postWebMessage(new WebMessage("motion-native-port-v1",new WebMessagePort[]{pair[1]}),Uri.parse(ORIGIN));
   }
  });
  studio.setWebChromeClient(new WebChromeClient());
  studioRoot.addView(studio,new FrameLayout.LayoutParams(-1,-1));
  stage.setVisibility(View.INVISIBLE);studioRoot.addView(stage,new FrameLayout.LayoutParams(1,1));
  setContentView(studioRoot);
  if(Build.VERSION.SDK_INT>=30){
   getWindow().setDecorFitsSystemWindows(false);
   studioRoot.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets a=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());studioRoot.setPadding(a.left,a.top,a.right,a.bottom);return i;});
   studioRoot.requestApplyInsets();
  }
  studio.loadUrl(ORIGIN+"/index.html");
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
  try{JSONObject o=new JSONObject();o.put("type",type);for(int i=0;i<entries.length;i+=2)o.put((String)entries[i],entries[i+1]);port.postMessage(new WebMessage(o.toString()));}catch(Exception ignored){}
 }
 private void command(String raw){
  if(closed||studioFailed||raw==null||raw.length()>3*1024*1024)return;
  try{
   JSONObject m=new JSONObject(raw);String type=BridgeValidation.command(m);
   if(type.equals("hello")){bridgeReady=true;JSONObject state=RenderService.latest(this);working=RenderService.busy(state);emit("connected","rendering",working,"onlineResources",onlineResourcesAllowed);emitDownloads();renderState(state);return;}
   if(type.equals("onlineResources")){if(working||saving)throw new IOException("هنگام رندر نمی‌توان دسترسی اینترنت را تغییر داد.");requestOnlineResources(m.getBoolean("enabled"));return;}
   if(type.equals("renderState")){renderState(RenderService.latest(this));return;}
   if(type.equals("downloads")){emitDownloads();return;}
   if(type.equals("layout")){layout(m);return;}
   if(type.equals("cancel")){if(pendingRender!=null){try{RenderRequest.file(this,pendingRender.id).delete();}catch(Exception ignored){}pendingRender=null;working=false;emit("cancelled");}else RenderService.cancel(this);return;}
   if(type.equals("capabilities")){stage.setVisibility(View.INVISIBLE);emit("capabilities","text",CodecSupport.report());return;}
   if(type.equals("openDownloads")||type.equals("openDownload")||type.equals("save")){
    if(working||saving){emit("downloadError","message","رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");return;}
    try{if(type.equals("openDownloads"))openDownloads();else if(type.equals("openDownload"))openDownload(m.getString("uri"));else saveCurrent();}catch(Exception e){emit("downloadError","message",e.getMessage()==null?"بازکردن دانلود انجام نشد.":e.getMessage());}return;
   }
   if(working||saving)throw new IOException("رندر یا ذخیره در حال اجراست؛ کمی صبر کن.");
   if(type.equals("preview")||type.equals("render")){
    String html=m.getString("html");if(html.isEmpty()||html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("اندازهٔ HTML باید کمتر از دو مگابایت باشد.");
    configure(m.getJSONObject("options"));editor.setText(html);
    if(type.equals("render"))export();else preview();return;
   }
   if(type.equals("chooseFile")){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);return;}
  }catch(Exception e){emit(working?"commandError":"error","message","درخواست معتبر نیست؛ نوع و تنظیمات ورودی را بررسی کن.");}
 }
 private void configure(JSONObject o)throws Exception{
  double sec=o.getDouble("duration");int rate=o.getInt("fps"),w=o.getInt("width"),h=o.getInt("height"),bits=o.getInt("bitrate");String mime=o.getString("mime");
  if(!Double.isFinite(sec)||sec<1||sec>300||(rate!=30&&rate!=60)||(bits!=12000000&&bits!=16000000&&bits!=20000000))throw new IOException("تنظیمات رندر معتبر نیست.");
  int size=-1;int[][] sizes={{640,360},{1280,720},{1920,1080},{1080,1920}};
  for(int i=0;i<sizes.length;i++)if(w==sizes[i][0]&&h==sizes[i][1])size=i;
  int codec=Arrays.asList(CodecSupport.MIME).indexOf(mime);if(size<0||codec<0)throw new IOException("اندازه یا کدک پشتیبانی نمی‌شود.");
  duration.setText(String.valueOf(sec));fps.setSelection(rate==60?1:0);sizeChoice.setSelection(size);codecChoice.setSelection(codec);quality.setSelection((bits/1000000-12)/4);gpu.setChecked(o.optBoolean("gpu",false));exportWidth=w;exportHeight=h;
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
 @Override protected void onStageRendererGone(){RenderService.rendererGone(this);emit("error","message",WebViewSecurity.RENDERER_ERROR);}
 @Override protected void onRenderProgress(int done,int total){emit("progress","done",done,"total",total);}
 @Override protected void onRenderResult(File file){exportPath="/exports/"+UUID.randomUUID()+"."+resultExt;emit("result","url",ORIGIN+exportPath,"ext",resultExt,"size",file.length());saveCurrent();}
 private void emitDownloads(){emit("downloads","items",downloads.list(),"folder",DownloadRepository.RELATIVE);}
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
  }catch(Exception e){runOnUiThread(()->{saving=false;emit("downloadError","message","ذخیره در دانلودها انجام نشد؛ فضای خالی و دسترسی حافظه را بررسی کن. "+(e.getMessage()==null?"":e.getMessage()));});}},"save-download").start();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==RENDER_PERMISSIONS){
   if(PermissionPolicy.notifications(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)emit("permissionNotice","message","اعلان‌ها مجاز نیستند؛ رندر ادامه دارد. برای دیدن پیشرفت یا توقف، به برنامه برگرد.");
   if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)emit("permissionNotice","message","دسترسی دانلودها مجاز نیست؛ ویدیو در برنامه می‌ماند و بعداً می‌توانی دوباره ذخیره کنی.");
   if(pendingRender!=null)beginRender();return;
  }
  if(request==STORAGE_REQUEST){saving=false;if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)saveCurrent();else emit("downloadError","message","در اندروید ۸ و ۹ برای ذخیره در دانلودها، دسترسی حافظه لازم است. ویدیو هنوز برای ذخیرهٔ مجدد در دسترس است.");}
 }
 @Override protected void export(){
  try{
   if(stageFailed||studioFailed)throw new IOException(WebViewSecurity.RENDERER_ERROR);
   if(RenderService.busy(RenderService.latest(this)))throw new IOException("یک رندر دیگر در حال اجراست.");
   int[][] sizes={{640,360},{1280,720},{1920,1080},{1080,1920}};int[] size=sizes[sizeChoice.getSelectedItemPosition()];
   JSONObject options=new JSONObject().put("duration",Double.parseDouble(duration.getText().toString())).put("fps",fps.getSelectedItemPosition()==0?30:60).put("width",size[0]).put("height",size[1]).put("bitrate",(12+quality.getSelectedItemPosition()*4)*1000000).put("mime",CodecSupport.MIME[codecChoice.getSelectedItemPosition()]).put("gpu",gpu.isChecked()).put("onlineResources",onlineResourcesAllowed);
   pendingRender=RenderRequest.create(editor.getText().toString(),options);getPreferences(0).edit().putString("code",pendingRender.html).apply();
   ArrayList<String> permissions=new ArrayList<>();
   if(PermissionPolicy.notifications(android.os.Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
   if(PermissionPolicy.legacyStorage(Build.VERSION.SDK_INT)&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
   if(!permissions.isEmpty()){pendingRender.write(this);working=true;requestPermissions(permissions.toArray(new String[0]),RENDER_PERMISSIONS);}else beginRender();
  }catch(Exception e){working=false;pendingRender=null;emit("error","message",e.getMessage()==null?"شروع رندر انجام نشد.":e.getMessage());}
 }
 private void beginRender(){
  RenderRequest request=pendingRender;pendingRender=null;if(request==null){working=false;emit("error","message","درخواست رندر موجود نیست؛ دوباره اجرا کن.");return;}
  try{RenderService.start(this,request);working=true;stage.onPause();emit("renderState","state",new JSONObject().put("phase","preparing").put("id",request.id).put("done",0).put("total",request.frames).put("options",request.options()).put("message","آماده‌سازی رندر مستقل…"));}
  catch(Exception e){working=false;emit("error","message","شروع سرویس رندر انجام نشد: "+e.getMessage());}
 }
 private void renderState(JSONObject s){
  if(closed||!bridgeReady||s.optString("phase").isEmpty())return;
  working=RenderService.busy(s);
  if(working){if(!stageFailed)stage.onPause();emit("renderState","state",s);return;}
  if(!stageFailed)stage.onResume();String id=s.optString("id"),phase=s.optString("phase");
  if((id+":"+phase).equals(presentedJob))return;presentedJob=id+":"+phase;
  if(phase.equals("complete")){
   File file=new File(s.optString("file"));if(!file.isFile()){emit("error","message","فایل موقت خروجی دیگر موجود نیست؛ دانلودهای من را بررسی کن.");return;}
   result=file;resultExt=s.optString("ext","mp4");resultMime=s.optString("mime","video/mp4");exportPath="/exports/"+id+"."+resultExt;
   emit("result","url",ORIGIN+exportPath,"ext",resultExt,"size",file.length(),"options",s.optJSONObject("options"));
   JSONObject item=s.optJSONObject("download");if(item!=null){savedSource=file.getAbsolutePath();savedDownload=item;emit("saved","item",item);}else if(s.has("warning"))emit("downloadError","message",s.optString("warning"));
   emitDownloads();
  }else if(phase.equals("cancelled"))emit("cancelled");else emit("error","message",s.optString("message","رندر متوقف شد."));
 }
 @Override protected void onStart(){super.onStart();RenderService.addListener(renderListener);if(bridgeReady)renderState(RenderService.latest(this));}
 @Override protected void onStop(){RenderService.removeListener(renderListener);super.onStop();}
 @Override protected void onSaveInstanceState(Bundle out){if(pendingRender!=null)out.putString("pendingRender",pendingRender.id);super.onSaveInstanceState(out);}
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
 @Override protected void onActivityResult(int r,int code,Intent data){
  if(r==1){if(code==RESULT_OK&&data!=null&&data.getData()!=null){try(InputStream in=getContentResolver().openInputStream(data.getData())){emit("file","code",read(in,2*1024*1024));}catch(Exception e){emit("error","message","فایل HTML خوانده نشد.");}}return;}
  super.onActivityResult(r,code,data);
 }
 @Override protected void onDestroy(){closed=true;readyPoll++;if(port!=null)port.close();if(studio!=null&&!studioFailed)studio.destroy();super.onDestroy();}
}
