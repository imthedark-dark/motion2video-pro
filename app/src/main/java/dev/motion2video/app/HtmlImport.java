package dev.motion2video.app;
import android.content.ContentResolver;import android.database.Cursor;import android.net.Uri;import android.provider.OpenableColumns;import java.io.*;
final class HtmlImport {
 static String read(ContentResolver resolver,Uri uri)throws IOException{
  if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("فایل را با انتخاب‌گر اسناد باز کن.");
  String name=null;long size=-1;
  try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
   if(c!=null&&c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=c.getString(n);if(s>=0&&!c.isNull(s))size=c.getLong(s);}
  }
  ImportPolicy.metadata(name,resolver.getType(uri),size);
  try(InputStream in=resolver.openInputStream(uri)){return ImportPolicy.read(in);}
 }
}
