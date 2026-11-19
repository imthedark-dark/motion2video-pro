package dev.motion2video.app;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.webkit.*;import android.widget.*;import android.net.Uri;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;
public class MainActivity extends Activity {
 protected volatile boolean onlineResourcesAllowed,stageFailed;
 private boolean contextReported,recoveryShown;
 private int fontNotices;
 private final Handler previewMonitor=new Handler(Looper.getMainLooper());
 protected Spinner codecChoice,sizeChoice,quality;protected CheckBox gpu;protected ScrollView workspace;protected int exportWidth=640,exportHeight=360;protected String resultMime="video/mp4",resultExt="mp4";protected long loadToken;
 protected WebView stage;protected EditText editor,duration;protected Spinner fps;protected TextView status,render;protected volatile boolean cancelled,working;protected File result;protected volatile int generation;protected String savedCode="";
 protected final int orange=Color.rgb(255,59,0);protected LinearLayout root;protected VideoView video;
 protected int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 protected TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(12),dp(10),dp(12),dp(10));return t;}
 protected TextView button(String title,Runnable action){TextView b=text(title,15);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));GradientDrawable g=new GradientDrawable();g.setColor(orange);g.setCornerRadius(dp(14));b.setBackground(g);b.setOnClickListener(v->action.run());return b;}
 protected void row(LinearLayout parent,TextView... buttons){LinearLayout r=new LinearLayout(this);for(TextView b:buttons){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));r.addView(b,p);}parent.addView(r);}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(Color.rgb(7,7,9));root.setPadding(dp(12),dp(12),dp(12),dp(8));setContentView(root);
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets a=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());root.setPadding(dp(12)+a.left,dp(8)+a.top,dp(12)+a.right,dp(8)+a.bottom);return i;});root.requestApplyInsets();}
  root.addView(text("MOTION2VIDEO PRO  ▶",24));root.addView(text("استودیو HTML → MP4 • خروجی H.264 بدون صدا",12));
  ScrollView scroll=new ScrollView(this);workspace=scroll;LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  stage=new WebView(this);stage.setBackgroundColor(Color.BLACK);applyPreviewLayer();stage.getSettings().setJavaScriptEnabled(true);stage.getSettings().setAllowFileAccess(false);stage.getSettings().setAllowContentAccess(false);stage.getSettings().setDomStorageEnabled(false);stage.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  PrivateWebChromeClient diagnostics=WebViewSecurity.configure(stage,this,false);WebViewSecurity.network(stage,false);
  stage.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    int token=generation;
    Uri url=request.getUrl();
    WebResourceResponse response=StageAssets.response(MainActivity.this,url,onlineResourcesAllowed,
     notice->{
      if((notice&StageAssets.FONT_BLOCKED)!=0)diagnostics.blocked(url);
      previewMonitor.post(()->{if(!isDestroyed()&&!stageFailed&&token==generation)reportFontNotice(notice);});
     });
    if(response!=null&&response.getStatusCode()==403)diagnostics.blocked(url);
    return response;
   }
   @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){if(stageFailed)return true;WebViewSecurity.rendererExit(v,MainActivity.this,detail,onlineResourcesAllowed,false);stageFailed=true;cancelled=true;if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);v.destroy();onStageRendererGone();return true;}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public void onPageFinished(WebView v,String url){if(stageFailed||isDestroyed())return;v.evaluateJavascript(ImportedContent.READY_SCRIPT,null);onStagePageFinished();}
  });
  body.addView(stage,new LinearLayout.LayoutParams(-1,dp(210)));stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int h=(r-l)*exportHeight/exportWidth;if(h>0&&stage.getLayoutParams().height!=h){stage.getLayoutParams().height=h;stage.requestLayout();}});
  row(body,button("آپلود HTML",()->{if(working)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);}),button("نمونهٔ ۱۴ ثانیه‌ای",()->{if(!working){editor.setText(asset("preset.html"));duration.setText("14");preview();}}));
  LinearLayout settings=new LinearLayout(this);duration=new EditText(this);duration.setText("14");duration.setInputType(8194);duration.setHint("ثانیه");settings.addView(duration,new LinearLayout.LayoutParams(0,dp(55),1));fps=new Spinner(this);fps.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"30 FPS","60 FPS"}));settings.addView(fps,new LinearLayout.LayoutParams(0,dp(55),1));body.addView(settings);body.addView(text("زمان: ۱ تا ۳۰۰ ثانیه • انتخاب کدک و اندازه",12));
  codecChoice=new Spinner(this);codecChoice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,CodecSupport.LABEL));body.addView(codecChoice);
  sizeChoice=new Spinner(this);sizeChoice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"640×360","1280×720","1920×1080","1080×1920 • عمودی","1080×1080 • مربع"}));sizeChoice.setSelection(1);body.addView(sizeChoice);
  quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"12 Mbps","16 Mbps","20 Mbps"}));quality.setSelection(1);body.addView(quality);
  gpu=new CheckBox(this);gpu.setText("ضبط GPU برای Canvas/WebGL (آزمایشی؛ پیش‌نمایش باید کاملاً دیده شود)");body.addView(gpu);
  CheckBox online=new CheckBox(this);online.setText("اجازهٔ منابع آنلاین");online.setOnCheckedChangeListener((b,checked)->{if(working){online.setChecked(onlineResourcesAllowed);return;}if(!checked){onlineResourcesAllowed=false;WebViewSecurity.network(stage,false);preview();}else new AlertDialog.Builder(this).setTitle("اجازهٔ منابع آنلاین؟").setMessage("کد واردشده می‌تواند به سرورهای اینترنتی درخواست بفرستد و محتوای شما را ارسال کند. فقط برای کد مورد اعتماد فعال کن.").setPositiveButton("اجازه می‌دهم",(d,w)->{onlineResourcesAllowed=true;WebViewSecurity.network(stage,true);preview();}).setNegativeButton("لغو",(d,w)->online.setChecked(false)).setOnCancelListener(d->online.setChecked(false)).show();});body.addView(online);
  body.addView(button("وضعیت کدک‌های همین گوشی",()->new AlertDialog.Builder(this).setTitle("کدک‌ها و محدودیت‌ها").setMessage(CodecSupport.report()).setPositiveButton("باشه",null).show()));
  row(body,button("تشخیص زمان",this::detectDuration),button("پاک کردن کد",()->{if(!working){editor.setText("");}}));
  editor=new EditText(this);editor.setTextSize(12);editor.setTypeface(Typeface.MONOSPACE);editor.setGravity(Gravity.TOP|Gravity.LEFT);editor.setTextDirection(View.TEXT_DIRECTION_LTR);editor.setInputType(131073);editor.setMinLines(6);editor.setMaxLines(12);editor.setText(state==null?getPreferences(0).getString("code",asset("preset.html")):state.getString("code",getPreferences(0).getString("code",asset("preset.html"))));body.addView(editor,new LinearLayout.LayoutParams(-1,dp(220)));
  render=button("تبدیل به MP4",()->{if(working){cancelled=true;status.setText("درخواست لغو ثبت شد…");}else export();});row(body,button("اجرای مجدد",()->{if(!working)preview();}),render);
  status=text("خروجی مستقیم MP4 است؛ کیفیت و سرعت به کدگذار گوشی بستگی دارد.",13);body.addView(status);
  video=new VideoView(this);body.addView(video,new LinearLayout.LayoutParams(-1,dp(190)));video.setVisibility(View.GONE);MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);
  row(body,button("ذخیرهٔ ویدیو",()->{if(result==null||!result.isFile()){status.setText("اول یک خروجی بساز.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(resultMime);i.putExtra(Intent.EXTRA_TITLE,"motion-"+System.currentTimeMillis()+"."+resultExt);startActivityForResult(i,2);}));
  body.addView(text("موشن باید مستقل باشد. GSAP و CSS قابل زمان‌بندی‌اند؛ صدا، WebGL و تایمرهای دلخواه تضمین نمی‌شوند. فایل‌های دارای CDN ممکن است اینترنت بخواهند. هنگام رندر برنامه را باز نگه دار.",12));if(!(this instanceof StudioActivity))preview();
 }
 protected String asset(String name){try(InputStream in=getAssets().open(name)){return read(in,4*1024*1024);}catch(Exception e){return "";}}
 protected volatile boolean importing;
 protected static String read(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("فایل بیش از حد بزرگ است");out.write(b,0,n);}return out.toString("UTF-8");}
 protected void preview(){if(stageFailed){onRenderError(WebViewSecurity.RENDERER_ERROR);return;}generation++;savedCode=editor.getText().toString();String code=savedCode.replaceAll("(?i)inset\\s*:\\s*0(?:px)?\\s*;", "top:0;right:0;bottom:0;left:0;inset:0;");contextReported=false;fontNotices=0;onFontNotice(0);WebViewSecurity.resetDiagnostics(stage);String prepared=ImportedContent.prepare(code,asset("motion-clock.js"),asset("fonts.css"),"window.__m2vToken="+generation+";",onlineResourcesAllowed);getPreferences(0).edit().putString("code",savedCode).apply();stage.loadDataWithBaseURL("https://motion.local/",prepared,"text/html","UTF-8",null);previewMonitor.removeCallbacksAndMessages(null);monitorPreview(generation);}
 private void monitorPreview(int token){
  if(isDestroyed()||stageFailed||token!=generation)return;
  if(working){previewMonitor.postDelayed(()->monitorPreview(token),RenderBudget.STALL_MS);return;}
  final boolean[] answered={false};
  Runnable timeout=()->{
   if(answered[0]||isDestroyed()||stageFailed||token!=generation)return;
   if(working){answered[0]=true;monitorPreview(token);return;}
   stopUnresponsivePreview();
  };
  try{stage.evaluateJavascript(ImportedContent.PREVIEW_CHECK,value->{
   if(answered[0]||stageFailed||token!=generation)return;
   answered[0]=true;previewMonitor.removeCallbacks(timeout);
   if(("1".equals(value)||"3".equals(value))&&!contextReported){contextReported=true;onStageContextLost();}
   if("2".equals(value)||"3".equals(value))reportFontNotice(StageAssets.FONT_BLOCKED);
   previewMonitor.postDelayed(()->monitorPreview(token),1500);
  });}catch(RuntimeException ignored){}
  previewMonitor.postDelayed(timeout,RenderBudget.STALL_MS);
 }
 private void stopUnresponsivePreview(){
  WebViewSecurity.watchdog(stage,this,onlineResourcesAllowed);
  stageFailed=true;cancelled=true;
  if(Build.VERSION.SDK_INT>=29)try{WebViewRenderProcess process=stage.getWebViewRenderProcess();if(process!=null)process.terminate();}catch(RuntimeException ignored){}
  if(stage.getParent() instanceof ViewGroup)((ViewGroup)stage.getParent()).removeView(stage);
  stage.destroy();
  showRendererRecovery("پیش‌نمایش در ۴۵ ثانیه پاسخ نداد. حلقه‌های کد، اندازهٔ بافت‌ها و تعداد ذرات را بررسی کن.");
 }
 protected boolean softwarePreview(){return getPreferences(0).getBoolean("software_preview",false);}
 protected void setSoftwarePreview(boolean enabled){
  getPreferences(0).edit().putBoolean("software_preview",enabled).apply();
  if(!stageFailed)applyPreviewLayer();
 }
 private void applyPreviewLayer(){stage.setLayerType(softwarePreview()?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_HARDWARE,null);}
 protected void showRendererRecovery(String reason){
  if(recoveryShown||isFinishing()||isDestroyed())return;
  recoveryShown=true;
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("نمایش موشن متوقف شد").setMessage(reason)
   .setPositiveButton("تلاش دوباره",(d,w)->recreate())
   .setNeutralButton("تلاش نرم‌افزاری",(d,w)->{setSoftwarePreview(true);recreate();})
   .setNegativeButton("عیب‌یابی",null).setCancelable(false).create();
  dialog.show();
  dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->showTroubleshooting());
 }
 protected void showTroubleshooting(){
  new AlertDialog.Builder(this).setTitle("عیب‌یابی موشن").setMessage(
   "۱. Android System WebView و Chrome را به‌روز کن.\n۲. موشن را با ذرات و بافت‌های کوچک‌تر امتحان کن.\n۳. حالت نرم‌افزاری برای Canvas دوبعدی است؛ بعضی موشن‌های WebGL در این حالت نمایش داده نمی‌شوند.\n۴. اگر فایل منبع بیرونی دارد، نسخهٔ مستقل آن را بگیر. Worker در فایل واردشده پشتیبانی نمی‌شود.\n\nWebView: "+WebViewSecurity.provider())
   .setPositiveButton("بازگشت",null).show();
 }
 protected void showAbout(){
  new AlertDialog.Builder(this).setTitle("دربارهٔ Motion2Video Pro").setMessage("Android System WebView\n"+WebViewSecurity.provider()).setPositiveButton("بازگشت",null).show();
 }
 private void reportFontNotice(int notice){
  if((fontNotices|notice)!=fontNotices){fontNotices|=notice;onFontNotice(fontNotices);}
 }
 protected void onFontNotice(int flags){}
 protected void onStageContextLost(){onRenderError(ImportedContent.CONTEXT_ERROR);}
 protected <T>T ui(Callable<T> call)throws Exception{FutureTask<T> task=new FutureTask<>(call);runOnUiThread(task);return task.get(15,TimeUnit.SECONDS);}
 protected String js(String script)throws Exception{CompletableFuture<String> f=new CompletableFuture<>();runOnUiThread(()->{if(isDestroyed()||stageFailed){f.completeExceptionally(new IOException("صفحه بسته شد"));return;}stage.evaluateJavascript(script,f::complete);});return f.get(15,TimeUnit.SECONDS);}
 protected Bitmap frame(double time)throws Exception{
  int frameToken=(int)Math.round(time*1000000);
  String seekResult=js("window.__m2vSeek("+time+","+frameToken+")");if(!"true".equals(seekResult))throw new IOException("اجرای کنترل زمان موشن خطا داد؛ کد و کتابخانه‌های لازم را بررسی کن.");
  long frameDeadline=System.currentTimeMillis()+15000;
  while(!"true".equals(js("window.__m2vFrameReady==="+frameToken))){if(cancelled||System.currentTimeMillis()>frameDeadline)throw new IOException("رسم فریم موشن کامل نشد.");Thread.sleep(20);}

  CompletableFuture<Bitmap> f=new CompletableFuture<>();runOnUiThread(()->stage.postOnAnimation(()->stage.postOnAnimation(()->{try{if(cancelled||isDestroyed())throw new IOException("لغو شد");Bitmap b=Bitmap.createBitmap(exportWidth,exportHeight,Bitmap.Config.ARGB_8888);
    if(gpu.isChecked()){Rect visible=new Rect();if(!stage.getGlobalVisibleRect(visible)||visible.width()!=stage.getWidth()||visible.height()!=stage.getHeight()){b.recycle();throw new IOException("برای ضبط GPU کل پیش‌نمایش باید دیده شود. اندازه را کمتر یا مسیر دوبعدی را انتخاب کن.");}int[] at=new int[2];stage.getLocationInWindow(at);Rect region=new Rect(at[0],at[1],at[0]+stage.getWidth(),at[1]+stage.getHeight());PixelCopy.request(getWindow(),region,b,result->{if(result==PixelCopy.SUCCESS)f.complete(b);else{b.recycle();f.completeExceptionally(new IOException("ضبط GPU انجام نشد: "+result));}},new Handler(Looper.getMainLooper()));}
    else{Canvas canvas=new Canvas(b);canvas.drawColor(Color.BLACK);canvas.scale((float)exportWidth/stage.getWidth(),(float)exportHeight/stage.getHeight());stage.draw(canvas);f.complete(b);}}catch(Exception e){f.completeExceptionally(e);}})));return f.get(15,TimeUnit.SECONDS);
 }
 protected void detectDuration(){if(stageFailed){onRenderError(WebViewSecurity.RENDERER_ERROR);return;}if(working)return;stage.evaluateJavascript("window.__m2vDuration?window.__m2vDuration():0",v->{try{double n=Double.parseDouble(v);if(n>0){duration.setText(String.valueOf(n));status.setText("زمان از اجرای فعلی تشخیص داده شد؛ حلقه‌ها و تایمرهای دلخواه را دستی بررسی کن.");}else status.setText("مدت معتبر پیدا نشد؛ زمان را دستی وارد کن.");}catch(Exception e){status.setText("زمان را دستی وارد کن.");}});}
 protected void export(){double seconds;try{seconds=Double.parseDouble(duration.getText().toString());if(!Double.isFinite(seconds)||seconds<1||seconds>300)throw new Exception();}catch(Exception e){duration.setError("عدد ۱ تا ۳۰۰ وارد کن");return;}int rate=fps.getSelectedItemPosition()==0?30:60;int frames=(int)Math.round(seconds*rate);int[][] sizes={{640,360},{1280,720},{1920,1080},{1080,1920},{1080,1080}};exportWidth=sizes[sizeChoice.getSelectedItemPosition()][0];exportHeight=sizes[sizeChoice.getSelectedItemPosition()][1];String selectedCodec=CodecSupport.MIME[codecChoice.getSelectedItemPosition()];int selectedBitrate=(12+quality.getSelectedItemPosition()*4)*1000000;
  try{ImportPolicy.validate(editor.getText().toString());new RenderBudget(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().maxMemory()).check(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),RenderBudget.frameReserve(exportWidth,exportHeight));CodecSupport.encoder(selectedCodec,exportWidth,exportHeight,rate,selectedBitrate);}catch(Exception e){status.setText(PublicErrors.message(e));onRenderError(PublicErrors.message(e));return;}
  resultExt=CodecSupport.webm(selectedCodec)?"webm":"mp4";resultMime="video/"+resultExt;codecChoice.setEnabled(false);sizeChoice.setEnabled(false);quality.setEnabled(false);gpu.setEnabled(false);stage.getLayoutParams().height=stage.getWidth()*exportHeight/exportWidth;stage.requestLayout();workspace.scrollTo(0,0);stage.setLayerType(gpu.isChecked()?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_SOFTWARE,null);working=true;cancelled=false;result=null;video.setVisibility(View.GONE);render.setText("لغو رندر");editor.setEnabled(false);duration.setEnabled(false);fps.setEnabled(false);preview();final int expectedGeneration=generation;status.setText("آماده‌سازی HTML و قلم‌ها…");
  new Thread(()->{File file=new File(getCacheDir(),"motion-"+System.currentTimeMillis()+"."+resultExt);try{
   RenderBudget budget=new RenderBudget(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().maxMemory());Thread.sleep(400);long deadline=System.currentTimeMillis()+30000;while(!"true".equals(js("window.__m2vReady===true&&window.__m2vToken==="+expectedGeneration+"&&(!window.lottie||!lottie.getRegisteredAnimations||lottie.getRegisteredAnimations().every(a=>a.isLoaded))"))){if(cancelled||System.currentTimeMillis()>deadline)throw new IOException("HTML یا دارایی‌های آن آماده نشدند.");Thread.sleep(200);}
   try(Mp4Encoder encoder=new Mp4Encoder(file,exportWidth,exportHeight,rate,selectedCodec,selectedBitrate)){for(int i=0;i<frames;i++){budget.progress(android.os.SystemClock.elapsedRealtime());budget.check(android.os.SystemClock.elapsedRealtime(),Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),0);if(cancelled)throw new IOException("رندر لغو شد.");Bitmap b=frame((double)i/rate);try{encoder.frame(b,(long)i*1000000/rate);}finally{b.recycle();}final int done=i+1;if(i%10==0||done==frames)runOnUiThread(()->{status.setText("رندر MP4: "+done+" / "+frames+" فریم");onRenderProgress(done,frames);});}encoder.finish((long)frames*1000000/rate);}
   if(cancelled)throw new IOException("رندر لغو شد.");if(!CodecSupport.webm(selectedCodec))FastStart.rewrite(file);Mp4Encoder.validate(file,selectedCodec);result=file;runOnUiThread(()->{onRenderResult(file);if(isDestroyed())return;video.setVisibility(View.VISIBLE);video.setVideoPath(file.getAbsolutePath());video.seekTo(1);status.setText(resultExt.toUpperCase(java.util.Locale.ROOT)+" آماده است • "+rate+" FPS • "+String.format(java.util.Locale.US,"%.2f",file.length()/1048576.0)+" MB");});
  }catch(Exception e){file.delete();runOnUiThread(()->{onRenderError(cancelled?"رندر لغو شد.":PublicErrors.message(e));if(!isDestroyed())status.setText(cancelled?"رندر لغو شد؛ فایل ناقص حذف شد.":"رندر انجام نشد: "+(PublicErrors.message(e)));});}finally{working=false;runOnUiThread(()->{if(!isDestroyed()){render.setText("تبدیل به MP4");editor.setEnabled(true);duration.setEnabled(true);fps.setEnabled(true);codecChoice.setEnabled(true);sizeChoice.setEnabled(true);quality.setEnabled(true);gpu.setEnabled(true);stage.setLayerType(View.LAYER_TYPE_HARDWARE,null);}});}} ,"mp4-render").start();
 }
 @Override protected void onActivityResult(int r,int resultCode,Intent data){super.onActivityResult(r,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(r==1){if(importing)return;importing=true;new Thread(()->{try{String html=HtmlImport.read(getContentResolver(),uri);runOnUiThread(()->{if(!working&&!isDestroyed()){try{editor.setText(html);preview();}catch(OutOfMemoryError memory){status.setText("حافظه کافی نیست؛ فایل کوچک‌تری وارد کن.");}}});}catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(!isDestroyed())status.setText(e instanceof OutOfMemoryError?"حافظه کافی نیست؛ فایل کوچک‌تری وارد کن.":PublicErrors.message(e));});}finally{importing=false;}},"html-import").start();}if(r==2){File src=result;if(src==null)return;new Thread(()->{try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->{status.setText("فایل ویدیو ذخیره شد.");onVideoSaved();});}catch(Exception e){runOnUiThread(()->status.setText("ذخیره انجام نشد؛ دوباره امتحان کن."));}}).start();}}
 @Override protected void onSaveInstanceState(Bundle out){cancelled=true;getPreferences(0).edit().putString("code",editor.getText().toString()).apply();super.onSaveInstanceState(out);}
 @Override protected void onStop(){if(working)cancelled=true;super.onStop();}
 @Override protected void onDestroy(){previewMonitor.removeCallbacksAndMessages(null);cancelled=true;video.stopPlayback();if(!stageFailed)stage.destroy();super.onDestroy();}
 protected void onStagePageFinished(){}
 protected void onStageRendererGone(){if(status!=null)status.setText(WebViewSecurity.RENDERER_ERROR);showRendererRecovery(WebViewSecurity.RECOVERY_HINT);}
 protected void onRenderProgress(int done,int total){}
 protected void onRenderResult(File file){}
 protected void onRenderError(String error){}
 protected void onVideoSaved(){}
}
