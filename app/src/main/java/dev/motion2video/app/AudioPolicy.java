package dev.motion2video.app;
import java.io.IOException;import java.util.Locale;
/** Bounded import metadata, independent of providers and decoder implementations. */
public final class AudioPolicy {
 public static final long MAX_BYTES=100L*1024*1024,MAX_TOTAL=160L*1024*1024;public static final int MAX_ASSETS=8;
 public static final String ERROR="فایل صوتی معتبر نیست؛ MP3، AAC، WAV یا M4A تا ۱۰۰ مگابایت و ۳۰ دقیقه انتخاب کن.";
 public static void metadata(String name,String mime,long bytes)throws IOException{
  if(name==null||name.length()>240||name.contains("/")||name.contains("\\")||bytes>MAX_BYTES||bytes< -1||name.indexOf(0)>=0)throw new IOException(ERROR);
  String n=name.toLowerCase(Locale.ROOT),m=mime==null?"":mime.toLowerCase(Locale.ROOT).split(";",2)[0];
  boolean generic=m.isEmpty()||m.equals("application/octet-stream"),ok=false;
  if(n.endsWith(".mp3"))ok=generic||m.equals("audio/mpeg")||m.equals("audio/mp3");
  if(n.endsWith(".aac"))ok=generic||m.equals("audio/aac")||m.equals("audio/aac-adts")||m.equals("audio/x-aac");
  if(n.endsWith(".wav"))ok=generic||m.equals("audio/wav")||m.equals("audio/x-wav")||m.equals("audio/wave");
  if(n.endsWith(".m4a"))ok=generic||m.equals("audio/mp4")||m.equals("audio/x-m4a");
  if(!ok||bytes==0)throw new IOException(ERROR);
 }
 public static boolean id(String id){return id!=null&&id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
 private AudioPolicy(){}
}
