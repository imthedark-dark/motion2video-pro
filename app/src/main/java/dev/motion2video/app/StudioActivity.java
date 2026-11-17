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
 private static final int STORAGE_REQUEST=41;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  downloads=new DownloadRepository(this);
  ((ViewGroup)stage.getParent()).removeView(stage);
  studioRoot=new FrameLayout(this);studioRoot.setBackgroundColor(Color.rgb(7,7,9));
  studio=new WebView(this);studio.setBackgroundColor(Color.rgb(7,7,9));
  WebSettings settings=studio.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  studio.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
    Uri u=request.getUrl();String host=u.getHost(),path=u.getPath()==null?"":u.getPath();
    try{
     if("studio.motion.local".equals(host)){
      if(path.equals("/index.html")||path.equals("/"))return assetResponse("text/html","studio.html");
      if(path.equals(exportPath)&&result!=null&&result.isFile())return exportResponse(request,result);
      return missing();
     }
     if("fonts.googleapis.com".equals(host))return assetResponse("text/css","fonts.css");
     if("motion.local".equals(host)&&path.startsWith("/fonts/")&&!path.contains(".."))return assetResponse("font/ttf",path.substring(1));
     if("cdnjs.cloudflare.com".equals(host)&&path.equals("/ajax/libs/gsap/3.12.5/gsap.min.js"))return assetResponse("application/javascript","studio-gsap-3.12.5.min.js");
     if("cdn.tailwindcss.com".equals(host))return assetResponse("application/javascript","studio-tailwind.js");
    }catch(Exception e){return missing();}
    return super.shouldInterceptRequest(v,request);
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public void onPageFinished(WebView view,String url){
    if(!url.equals(ORIGIN+"/index.html"))return;
    if(port!=null)port.close();
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
  Map<String,String> headers=new HashMap<>();headers.put("Access-Control-Allow-Origin","*");r.setResponseHeaders(headers);return r;
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
  if(closed||raw==null||raw.length()>3*1024*1024)return;
  try{
   JSONObject m=new JSONObject(raw);String type=m.getString("type");
   if(type.equals("hello")){emit("connected");emitDownloads();return;}
   if(type.equals("downloads")){emitDownloads();return;}
   if(type.equals("layout")){layout(m);return;}
   if(type.equals("cancel")){cancelled=true;return;}
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
  }catch(Exception e){emit("error","message",e.getMessage()==null?"درخواست معتبر نیست.":e.getMessage());}
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
  double scale=m.getDouble("dpr"),x=m.getDouble("x"),y=m.getDouble("y"),w=m.getDouble("width"),h=m.getDouble("height"),clip=m.optDouble("clipTop",0);
  if(!Double.isFinite(scale)||scale<=0||scale>10||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||!Double.isFinite(clip)||w<1||h<1||w*scale>10000||h*scale>10000)return;
  FrameLayout.LayoutParams p=new FrameLayout.LayoutParams((int)Math.round(w*scale),(int)Math.round(h*scale));p.leftMargin=(int)Math.round(x*scale);p.topMargin=(int)Math.round(y*scale);stage.setLayoutParams(p);
  stage.setClipBounds(new Rect(0,(int)Math.min(p.height,Math.max(0,clip*scale)),p.width,p.height));stage.setVisibility(m.optBoolean("visible",true)?View.VISIBLE:View.INVISIBLE);
 }
 @Override protected void onStagePageFinished(){
  if(studio==null)return;final int token=++readyPoll;pollReady(token,0);
 }
 private void pollReady(int token,int attempts){
  if(closed||token!=readyPoll||attempts>150)return;
  stage.evaluateJavascript("window.__m2vReady&&window.__m2vDuration?window.__m2vDuration():null",v->{
   if(closed||token!=readyPoll)return;
   if(v!=null&&!v.equals("null")){try{emit("ready","duration",Double.parseDouble(v));}catch(Exception ignored){}}else stage.postDelayed(()->pollReady(token,attempts+1),200);
  });
 }
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
  if(Build.VERSION.SDK_INT<29&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_REQUEST);return;}
  final String mime=resultMime,ext=resultExt;
  new Thread(()->{try{
   JSONObject item=downloads.save(source,mime,ext);
   runOnUiThread(()->{saving=false;savedSource=source.getAbsolutePath();savedDownload=item;emit("saved","item",item);emitDownloads();});
  }catch(Exception e){runOnUiThread(()->{saving=false;emit("downloadError","message","ذخیره در دانلودها انجام نشد؛ فضای خالی و دسترسی حافظه را بررسی کن. "+(e.getMessage()==null?"":e.getMessage()));});}},"save-download").start();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==STORAGE_REQUEST){saving=false;if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)saveCurrent();else emit("downloadError","message","در اندروید ۸ و ۹ برای ذخیره در دانلودها، دسترسی حافظه لازم است. ویدیو هنوز برای ذخیرهٔ مجدد در دسترس است.");}}
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
 @Override protected void onDestroy(){closed=true;readyPoll++;if(port!=null)port.close();if(studio!=null)studio.destroy();super.onDestroy();}
}
