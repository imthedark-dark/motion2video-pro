package dev.motion2video.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Locale;

/** Shared bounded HTML import policy; no archive or directory extraction. */
public final class ImportPolicy {
 public static final int MAX_BYTES=20*1024*1024;
 // JSON can escape each ASCII byte as six characters. Content is checked separately.
 public static final int MAX_JSON_BYTES=6*MAX_BYTES+4096;
 private ImportPolicy(){}
 public static void metadata(String name,String mime,long size)throws IOException{
  String n=name==null?"":name.toLowerCase(Locale.ROOT);
  if(n.length()>1024||!(n.endsWith(".html")||n.endsWith(".htm")))throw new IOException("فقط فایل با پسوند HTML یا HTM وارد کن.");
  String type=mime==null?"":mime.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
  if(!(type.isEmpty()||type.equals("text/html")||type.equals("text/plain")||type.equals("application/octet-stream")))throw new IOException("نوع فایل HTML معتبر نیست.");
  if(size>MAX_BYTES)throw new IOException("حداکثر اندازهٔ HTML بیست مگابایت است.");
 }
 public static String read(InputStream in)throws IOException{
  if(in==null)throw new IOException("فایل قابل خواندن نیست.");
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;
  while((n=in.read(bytes))!=-1){if(n>MAX_BYTES-out.size())throw new IOException("حداکثر اندازهٔ HTML بیست مگابایت است.");out.write(bytes,0,n);}
  try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();if(text.startsWith("\ufeff"))text=text.substring(1);validate(text);return text;}
  catch(CharacterCodingException e){throw new IOException("کدگذاری فایل معتبر نیست؛ فایل را با UTF-8 ذخیره کن.");}
 }
 public static void validate(String text)throws IOException{
  if(text==null||text.trim().isEmpty()||text.length()>MAX_BYTES||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("کد HTML خالی است یا از بیست مگابایت بزرگ‌تر است.");
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c==0)throw new IOException("فایل متنی HTML معتبر نیست.");if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IOException("متن Unicode معتبر نیست.");}else if(Character.isLowSurrogate(c))throw new IOException("متن Unicode معتبر نیست.");}
 }
}
