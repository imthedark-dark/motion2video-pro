package dev.motion2video.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.core.content.FileProvider;
import java.io.FileNotFoundException;

/** Standard canonical-path handling, with the old read-only export contract retained. */
public final class ExportFileProvider extends FileProvider {
 private void requireExport(Uri uri){
  String path=uri.getPath();
  if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".downloads").equals(uri.getAuthority())
    ||uri.getEncodedQuery()!=null||uri.getFragment()!=null||path==null
    ||!path.matches("/file/Motion2Video_[A-Za-z0-9_]+\\.(mp4|webm)"))
   throw new IllegalArgumentException("نشانی فایل خروجی معتبر نیست.");
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new FileNotFoundException("فقط خواندن فایل خروجی مجاز است.");
  try{requireExport(uri);}catch(IllegalArgumentException e){throw new FileNotFoundException("فایل خروجی پیدا نشد.");}
  return super.openFile(uri,mode);
 }
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  requireExport(uri);return super.query(uri,projection,selection,args,sort);
 }
 @Override public String getType(Uri uri){requireExport(uri);return super.getType(uri);}
 @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
