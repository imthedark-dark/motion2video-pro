package dev.motion2video.app;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import java.io.*;
/** Read-only URI grants for exports on Android 8/9; not an exposed file browser. */
public final class DownloadProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{
  String path=uri.getPath();if(path==null||!path.matches("/file/Motion2Video_[A-Za-z0-9_]+\\.(mp4|webm)"))throw new FileNotFoundException();
  try{File dir=DownloadRepository.legacyFolder().getCanonicalFile(),f=new File(dir,uri.getLastPathSegment()).getCanonicalFile();if(!dir.equals(f.getParentFile())||!f.isFile())throw new FileNotFoundException();return f;}catch(IOException e){throw new FileNotFoundException("فایل دانلود پیدا نشد.");}
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public String getType(Uri uri){return uri.toString().endsWith(".webm")?"video/webm":"video/mp4";}
 @Override public Cursor query(Uri uri,String[] columns,String selection,String[] args,String sort){try{File f=file(uri);String[] names=columns==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:columns;MatrixCursor c=new MatrixCursor(names);Object[] row=new Object[names.length];for(int i=0;i<names.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(names[i])?f.getName():OpenableColumns.SIZE.equals(names[i])?f.length():null;c.addRow(row);return c;}catch(IOException e){return null;}}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
