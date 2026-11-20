package dev.motion2video.app;

import android.app.AlertDialog;
import android.content.*;
import android.view.ViewGroup;
import android.widget.*;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Trusted Android UI for consent. No bridge command downloads a font or grants approval. */
final class FontWorkflow implements AutoCloseable {
 private final StudioActivity owner;
 private final SharedPreferences settings;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ExecutorService network=Executors.newSingleThreadExecutor();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 private final Set<String> sessionFallback=new HashSet<>();
 private final BooleanSupplier rendering;
 private boolean active,closed,networkBusy;
 private int epoch;
 private FontDownload.Control control;
 private AlertDialog dialog;
 private Runnable pendingCancel,abortDownload;

 FontWorkflow(StudioActivity owner,BooleanSupplier rendering){
  this.owner=owner;this.rendering=rendering;settings=AppFonts.settings(owner);
 }

 boolean busy(){return active;}
 void newImport(){sessionFallback.clear();}

 void resolve(Supplier<FontDiscovery> scan,boolean explicit,Consumer<String> summary,
              Consumer<FontCache.Scope> proceed,Runnable cancel){
  if(active||closed){cancel.run();return;}
  active=true;
  int request=++epoch;
  pendingCancel=cancel;
  worker.execute(()->{
   try{
    FontDiscovery discovery=scan.get();
    FontCache.Scope scope=AppFonts.scope(owner,discovery,false);
    Set<String> cached=scope==null?Collections.emptySet():scope.families();
    Set<String> needed=AppFonts.needed(discovery);
    Set<String> unknown=new LinkedHashSet<>();
    for(String name:needed)if(!FontDiscovery.bundled(name)&&!cached.contains(FontCache.key(name)))unknown.add(FontCache.key(name));
    Set<String> installed=InstalledFonts.matching(unknown);
    Set<String> missing=new HashSet<>(unknown);
    Set<String> eligible=FontConsent.eligible(discovery,missing);
    String text=summary(needed,cached,installed);
    owner.runOnUiThread(()->{
     if(closed||epoch!=request)return;
     summary.accept(text);
     eligible.removeIf(name->sessionFallback.contains(FontCache.key(name)));
     if(!AppFonts.enabled(owner)||eligible.isEmpty()){finish(()->proceed.accept(scope));return;}
     Set<String> remembered=settings.getStringSet("remembered",Collections.emptySet());
     if(FontConsent.mayReuse(eligible,remembered,true,explicit)){
      download(eligible,false,discovery,installed,summary,proceed,scope);
     }else showConsent(eligible,discovery,installed,summary,proceed,cancel,scope);
    });
   }catch(RuntimeException|OutOfMemoryError unavailable){
    owner.runOnUiThread(()->{if(!closed&&epoch==request){summary.accept("بررسی فونت‌ها کامل نشد؛ از فونت‌های محلی استفاده می‌شود.");finish(()->proceed.accept(null));}});
   }
  });
 }

 private static String summary(Set<String> needed,Set<String> cached,Set<String> installed){
  if(needed.isEmpty())return "فونت جداگانه‌ای در کد شناسایی نشد.";
  List<String> lines=new ArrayList<>();
  for(String family:needed){
   String key=FontCache.key(family);
   String status=FontDiscovery.bundled(family)?"همراه برنامه":cached.contains(key)?"ذخیره‌شده":
    installed.contains(key)?"روی دستگاه (نمایش به WebView بستگی دارد)":"ناموجود یا تأییدنشده";
   lines.add(family+" — "+status);
  }
  return String.join("\n",lines);
 }

 private void showConsent(Set<String> families,FontDiscovery discovery,Set<String> installed,
                          Consumer<String> summary,Consumer<FontCache.Scope> proceed,Runnable cancel,FontCache.Scope previousScope){
  CheckBox remember=new CheckBox(owner);
  remember.setText("اجازهٔ دانلود دوباره فقط برای همین خانواده‌ها");
  remember.setPadding(owner.dp(20),0,owner.dp(20),0);
  LinearLayout content=fontPanel();
  TextView notice=new TextView(owner);
  notice.setText(String.join("، ",families)+"\nسقف تقریبی حجم: "+(12*families.size())+" مگابایت\n\n"+
    "فقط فهرست و فایل فونت از گوگل درخواست می‌شود. گوگل نام خانواده و نشانی IP دستگاه را می‌بیند. فونت روی همین گوشی ذخیره می‌شود. این اجازه اینترنت موشن را فعال نمی‌کند. هر فونت مجوز خودش را دارد.");
  content.addView(notice);
  remember.setMinHeight(owner.dp(48));
  content.addView(remember);
  addAction(content,"دانلود و ذخیره",()->download(families,remember.isChecked(),discovery,installed,summary,proceed,previousScope));
  addAction(content,"فونت جایگزین",()->{
   for(String family:families)sessionFallback.add(FontCache.key(family));
   resume(discovery,installed,summary,proceed,"");
  });
  dialog=new AlertDialog.Builder(owner).setTitle("دانلود فونت از Google Fonts")
   .setView(scrollable(content)).setNegativeButton("لغو",(d,w)->finish(cancel))
   .setOnCancelListener(d->finish(cancel)).show();
 }

 private void download(Set<String> families,boolean remember,FontDiscovery discovery,Set<String> installed,
                       Consumer<String> summary,Consumer<FontCache.Scope> proceed,FontCache.Scope previousScope){
  if(networkBusy){
   useFallback(discovery,installed,summary,proceed,previousScope,"دانلود قبلی هنوز در حال توقف است.");
   return;
  }
  networkBusy=true;
  FontDownload.Control task=new FontDownload.Control();
  control=task;
  LinearLayout panel=new LinearLayout(owner);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(owner.dp(24),owner.dp(8),owner.dp(24),owner.dp(12));
  TextView label=new TextView(owner);
  label.setText("در حال دریافت فونت…");
  panel.addView(label);
  ProgressBar bar=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);
  int maximum=FontDownload.FAMILY_LIMIT*families.size();
  bar.setContentDescription("پیشرفت دانلود فونت");
  bar.setMax(maximum);
  panel.addView(bar,new LinearLayout.LayoutParams(-1,-2));
  dialog=new AlertDialog.Builder(owner).setTitle("دانلود فونت").setView(panel)
   .setNegativeButton("لغو",null).setCancelable(false).show();
  abortDownload=()->{
   if(control!=task)return;
   stopSocket(task);
   control=null;
   for(String family:families)sessionFallback.add(FontCache.key(family));
   dialog.dismiss();
   useFallback(discovery,installed,summary,proceed,previousScope,"دانلود متوقف شد.");
  };
  dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->abortDownload.run());
  ScheduledFuture<?> expiry=timer.schedule(()->owner.runOnUiThread(()->{
   if(!closed&&control==task)abortDownload.run();
  }),FontDownload.OVERALL_MS,TimeUnit.MILLISECONDS);
  network.execute(()->{
   boolean failed=false;
   Set<String> stored=new HashSet<>();
   int offset=0;
   try{
    FontDownload downloader=new FontDownload(FontDownload::https);
    for(String family:families){
     final int previous=offset;
     List<FontDownload.FileData> files=downloader.download(family,task,received->
      owner.runOnUiThread(()->{
       if(!closed&&control==task&&!task.cancelled){
        bar.setProgress(previous+received);
        label.setText(family+" • "+(previous+received)/1024+" KB");
       }
      }));
     AppFonts.cache(owner).save(files,task);
     stored.add(FontCache.key(family));
     for(FontDownload.FileData file:files)offset+=file.bytes.length;
    }
   }catch(IOException|RuntimeException|OutOfMemoryError unavailable){failed=true;}
   finally{expiry.cancel(false);}
   final String message=failed||task.cancelled?"دانلود کامل نشد؛ فونت‌های ناموجود با فونت جایگزین نمایش داده می‌شوند.":"فونت‌ها روی دستگاه ذخیره شدند.";
   owner.runOnUiThread(()->{
    networkBusy=false;
    if(closed||control!=task)return;
    control=null;
    abortDownload=null;
    for(String family:families)if(!stored.contains(FontCache.key(family)))sessionFallback.add(FontCache.key(family));
    if(remember&&!stored.isEmpty())appendRemembered(stored);
    if(dialog!=null)dialog.dismiss();
    resume(discovery,installed,summary,proceed,message);
   });
  });
 }

 private static void stopSocket(FontDownload.Control task){
  task.cancelled=true;
  new Thread(task::cancel,"font-disconnect").start();
 }

 private void useFallback(FontDiscovery discovery,Set<String> installed,Consumer<String> summary,
                          Consumer<FontCache.Scope> proceed,FontCache.Scope scope,String reason){
  Set<String> cached=scope==null?Collections.emptySet():scope.families();
  summary.accept(reason+" فونت‌های ناموجود با جایگزین نمایش داده می‌شوند.\n"+summary(AppFonts.needed(discovery),cached,installed));
  finish(()->proceed.accept(scope));
 }

 private void appendRemembered(Set<String> stored){
  Set<String> previous=new HashSet<>(settings.getStringSet("remembered",Collections.emptySet()));
  previous.addAll(stored);
  settings.edit().putStringSet("remembered",previous).apply();
 }

 private void resume(FontDiscovery discovery,Set<String> installed,Consumer<String> summary,
                     Consumer<FontCache.Scope> proceed,String message){
  int request=epoch;
  worker.execute(()->{
   FontCache.Scope resolved=null;
   try{resolved=AppFonts.scope(owner,discovery,false);}catch(RuntimeException|OutOfMemoryError unavailable){}
   FontCache.Scope scope=resolved;
   String text=summary(AppFonts.needed(discovery),scope==null?Collections.emptySet():scope.families(),installed);
   owner.runOnUiThread(()->{if(!closed&&epoch==request){summary.accept(message.isEmpty()?text:message+"\n"+text);finish(()->proceed.accept(scope));}});
  });
 }

 private void finish(Runnable action){
  active=false;
  pendingCancel=null;
  abortDownload=null;
  dialog=null;
  action.run();
 }

 void cancelPending(){
  if(!active||pendingCancel==null)return;
  if(abortDownload!=null)abortDownload.run();
  else if(dialog!=null)dialog.cancel();
  else{epoch++;finish(pendingCancel);}
 }

 void openSettings(){
  if(active||closed)return;
  active=true;
  worker.execute(()->{
   List<FontCache.Entry> entries;
   try{entries=AppFonts.cache(owner).list();}catch(IOException unavailable){entries=Collections.emptyList();}
   List<FontCache.Entry> snapshot=entries;
   owner.runOnUiThread(()->{if(!closed){active=false;showSettings(snapshot);}});
  });
 }

 private void showSettings(List<FontCache.Entry> entries){
  Map<String,Long> totals=new TreeMap<>(String.CASE_INSENSITIVE_ORDER), dates=new HashMap<>();
  for(FontCache.Entry e:entries){totals.merge(e.family,(long)e.bytes,Long::sum);dates.merge(e.family,e.date,Math::max);}
  LinearLayout panel=fontPanel();
  Switch enabled=new Switch(owner);
  enabled.setText("دانلود و استفاده از فونت ذخیره‌شده");
  enabled.setMinHeight(owner.dp(48));
  enabled.setChecked(AppFonts.enabled(owner));
  enabled.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("enabled",checked).apply());
  panel.addView(enabled);
  TextView total=new TextView(owner);
  total.setText(String.format(Locale.US,"حجم کل: %.2f از ۱۰۰ مگابایت",FontCache.bytes(entries)/1048576.0));
  panel.addView(total);
  for(String family:totals.keySet()){
   Button row=new Button(owner);
   row.setMinHeight(owner.dp(48));
   row.setSingleLine(false);
   row.setText(family+" • "+(totals.get(family)+1023)/1024+" KB • "+
    DateFormat.getDateInstance().format(new Date(dates.get(family)))+" • حذف");
   row.setOnClickListener(v->confirmDelete(family));
   panel.addView(row);
  }
  if(entries.isEmpty()){
   TextView empty=new TextView(owner);
   empty.setText("هنوز فونتی ذخیره نشده است.");
   panel.addView(empty);
  }
  addAction(panel,"پاک‌کردن اجازه‌ها",()->{settings.edit().remove("remembered").apply();toast("اجازه‌های ذخیره‌شده پاک شدند.");});
  addAction(panel,"پاک‌کردن همه",()->confirmDelete(null));
  dialog=new AlertDialog.Builder(owner).setTitle("فونت‌ها").setView(scrollable(panel))
   .setPositiveButton("بستن",null).show();
 }

 private void confirmDelete(String family){
  if(rendering.getAsBoolean()){toast("پاک‌کردن فونت پس از پایان رندر ممکن است.");return;}
  new AlertDialog.Builder(owner).setTitle(family==null?"پاک‌کردن همهٔ فونت‌های ذخیره‌شده؟":"پاک‌کردن فونت "+family+"؟")
   .setNegativeButton("لغو",null).setPositiveButton("پاک کن",(d,w)->erase(family)).show();
 }

 private void erase(String family){
  if(closed||active)return;
  active=true;
  worker.execute(()->{
   boolean deleted;
   try{deleted=AppFonts.cache(owner).delete(family);}catch(IOException unavailable){deleted=false;}
   boolean done=deleted;
   owner.runOnUiThread(()->{
    if(closed)return;
    active=false;
    if(done){
     Set<String> remembered=new HashSet<>(settings.getStringSet("remembered",Collections.emptySet()));
     if(family==null)remembered.clear();else remembered.remove(FontCache.key(family));
     settings.edit().putStringSet("remembered",remembered).apply();
     if(dialog!=null)dialog.dismiss();
     openSettings();
    }else toast("پاک‌کردن انجام نشد؛ پس از پایان رندر دوباره امتحان کن.");
   });
  });
 }

 private LinearLayout fontPanel(){
  LinearLayout panel=new LinearLayout(owner);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setLayoutDirection(ViewGroup.LAYOUT_DIRECTION_RTL);
  panel.setPadding(owner.dp(18),0,owner.dp(18),0);
  return panel;
 }
 private ScrollView scrollable(LinearLayout content){
  ScrollView scroll=new ScrollView(owner);
  scroll.addView(content);
  return scroll;
 }
 private void addAction(LinearLayout panel,String title,Runnable action){
  Button button=new Button(owner);
  button.setText(title);
  button.setSingleLine(false);
  button.setMinHeight(owner.dp(48));
  button.setOnClickListener(v->{dialog.dismiss();action.run();});
  panel.addView(button);
 }
 private void toast(String message){Toast.makeText(owner,message,Toast.LENGTH_SHORT).show();}

 @Override public void close(){
  closed=true;
  if(control!=null)stopSocket(control);
  if(dialog!=null)dialog.dismiss();
  Runnable cancel=pendingCancel;
  pendingCancel=null;
  if(cancel!=null)cancel.run();
  worker.shutdownNow();
  network.shutdownNow();
  timer.shutdownNow();
 }
}
