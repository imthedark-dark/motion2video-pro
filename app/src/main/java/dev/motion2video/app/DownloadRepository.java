package dev.motion2video.app;

import android.content.*;
import androidx.core.content.FileProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Public Downloads exports. MediaStore owns Q+ files; legacy writes require the
 * normal storage permission on Android 8/9. History contains only this app's exports. */
public final class DownloadRepository {
 public static final String FOLDER="Motion2Video";
 public static final String RELATIVE=Environment.DIRECTORY_DOWNLOADS+"/"+FOLDER+"/";
 private final Context context;
 private final android.content.SharedPreferences prefs;
 public DownloadRepository(Context context){this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("download_history",Context.MODE_PRIVATE);}
 public static File legacyFolder(){return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),FOLDER);}
 public static Uri folderUri(){return android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:"+Environment.DIRECTORY_DOWNLOADS+"/"+FOLDER);}
 public synchronized JSONArray list(){try{return new JSONArray(prefs.getString("items","[]"));}catch(JSONException e){return new JSONArray();}}
 public synchronized JSONObject find(String uri)throws IOException{JSONArray items=list();for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&uri.equals(item.optString("uri")))return item;}throw new IOException("این فایل در دانلودهای برنامه ثبت نشده است.");}
 public JSONObject save(File source,String mime,String extension)throws Exception{return save(source,mime,extension,()->false);}
 public JSONObject save(File source,String mime,String extension,java.util.function.BooleanSupplier cancelled)throws Exception{
  if(!source.isFile()||source.length()==0)throw new IOException("فایل خروجی در دسترس نیست.");
  if(!Arrays.asList("mp4","webm").contains(extension))throw new IOException("پسوند خروجی معتبر نیست.");
  String name="Motion2Video_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+"_"+UUID.randomUUID().toString().substring(0,6)+"."+extension;
  ContentResolver resolver=context.getContentResolver();Uri uri=null;File legacy=null;boolean legacyCreated=false;
  try{
   if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,RELATIVE);values.put(MediaStore.MediaColumns.IS_PENDING,1);uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("ساخت فایل در دانلودها انجام نشد.");}
   else{File dir=legacyFolder();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("پوشهٔ دانلود ساخته نشد.");legacy=new File(dir,name);if(!legacy.createNewFile())throw new IOException("نام فایل تکراری است.");legacyCreated=true;uri=FileProvider.getUriForFile(context,context.getPackageName()+".downloads",legacy);}
   try(InputStream in=new FileInputStream(source);OutputStream out=Build.VERSION.SDK_INT>=29?resolver.openOutputStream(uri,"w"):new FileOutputStream(legacy)){
    if(out==null)throw new IOException("فایل دانلود برای نوشتن باز نشد.");CancellableCopy.copy(in,out,cancelled);
   }
   if(cancelled.getAsBoolean())throw new IOException("ذخیره متوقف شد.");
   if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);if(resolver.update(uri,done,null,null)!=1)throw new IOException("نهایی‌کردن دانلود انجام نشد.");
    try(Cursor c=resolver.query(uri,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
   }
   JSONObject item=new JSONObject();item.put("uri",uri.toString());item.put("name",name);item.put("path",RELATIVE+name);item.put("folder",RELATIVE);item.put("mime",mime);item.put("size",source.length());item.put("createdAt",System.currentTimeMillis());
   remember(item);if(legacy!=null)android.media.MediaScannerConnection.scanFile(context,new String[]{legacy.getAbsolutePath()},new String[]{mime},null);
   return item;
  }catch(Exception e){if(Build.VERSION.SDK_INT>=29&&uri!=null){try{resolver.delete(uri,null,null);}catch(Exception ignored){}}if(legacyCreated)legacy.delete();throw e;}
 }
 private synchronized void remember(JSONObject item)throws IOException{JSONArray next=new JSONArray();next.put(item);JSONArray old=list();for(int i=0;i<old.length();i++)next.put(old.optJSONObject(i));if(!prefs.edit().putString("items",next.toString()).commit())throw new IOException("ثبت فهرست دانلودها انجام نشد.");}
 /** Shares only a registered, generated export through the existing narrow provider root. */
 public Intent shareIntent(String uri)throws Exception{
  JSONObject item=find(uri);String name=item.getString("name");
  if(!name.matches("Motion2Video_[0-9]{8}_[0-9]{6}_[0-9]{3}_[a-f0-9]{6}\\.(mp4|webm)"))throw new IOException("نشانی فایل خروجی معتبر نیست.");
  File folder=legacyFolder().getCanonicalFile(),file=new File(folder,name).getCanonicalFile();
  if(!folder.equals(file.getParentFile())||!file.isFile())throw new FileNotFoundException();
  Uri content=FileProvider.getUriForFile(context,context.getPackageName()+".downloads",file);
  try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(content,"r")){if(fd==null)throw new FileNotFoundException();}
  Intent send=new Intent(Intent.ACTION_SEND).setType(item.getString("mime")).putExtra(Intent.EXTRA_STREAM,content).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  send.setClipData(ClipData.newRawUri(name,content));return Intent.createChooser(send,"اشتراک ویدیو").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
 }
 public Intent openIntent(String uri)throws Exception{JSONObject item=find(uri);Uri content=Uri.parse(uri);try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(content,"r")){if(fd==null)throw new IOException("فایل پیدا نشد؛ ممکن است از دانلودها پاک یا جابه‌جا شده باشد.");}Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(content,item.getString("mime")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(item.getString("name"),content));return intent;}
}
