package dev.motion2video.app;
import android.media.*;import android.os.Build;import java.util.*;
public final class CodecSupport {
 private static final java.util.concurrent.ConcurrentHashMap<String,String> MATCHES=new java.util.concurrent.ConcurrentHashMap<>();
 public static final String[] MIME={"video/avc","video/hevc","video/av01","video/x-vnd.on2.vp9","video/x-vnd.on2.vp8"};
 public static final String[] LABEL={"H.264 • MP4 (پیشنهادی)","HEVC • MP4","AV1 • MP4 (اندروید ۱۴+)","VP9 • WebM","VP8 • WebM"};
 public static boolean webm(String mime){return mime.contains("vnd.on2");}
 public static String encoder(String mime,int w,int h,int fps,int bitrate)throws Exception {
  String key=mime+":"+w+":"+h+":"+fps+":"+bitrate;String cached=MATCHES.get(key);if(cached!=null)return cached;
  if(mime.equals("video/av01")&&Build.VERSION.SDK_INT<34)throw new Exception("کانتینر MP4 با AV1 در این نسخهٔ اندروید فعال نیست.");
  for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(info.isEncoder())for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase(mime))try{
   MediaCodecInfo.CodecCapabilities caps=info.getCapabilitiesForType(type);boolean yuv=false;for(int color:caps.colorFormats)yuv|=color==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
   MediaCodecInfo.VideoCapabilities vc=caps.getVideoCapabilities();
   if(yuv&&vc.areSizeAndRateSupported(w,h,fps)&&vc.getBitrateRange().contains(bitrate)){MATCHES.put(key,info.getName());return info.getName();}
  }catch(Exception ignored){}
  throw new Exception("کدگذار سازگار برای این کدک، اندازه، نرخ فریم و بیت‌ریت پیدا نشد؛ H.264 با اندازه یا کیفیت کمتر را امتحان کن.");
 }
 public static org.json.JSONObject suggestion(RenderRequest r)throws Exception {
  org.json.JSONObject best=null;double score=Double.POSITIVE_INFINITY;
  for(String mime:MIME)for(int[] size:new int[][]{{640,360},{1280,720},{1920,1080},{1080,1920},{1080,1080}})for(int fps:new int[]{30,60})for(int bits:new int[]{12000000,16000000,20000000}){
   double rank=CapabilityPolicy.score(r.mime,mime,r.width,r.height,r.fps,r.bitrate,size[0],size[1],fps,bits);if(rank>=score)continue;
   try{encoder(mime,size[0],size[1],fps,bits);best=r.options().put("mime",mime).put("width",size[0]).put("height",size[1]).put("fps",fps).put("bitrate",bits);score=rank;}catch(Exception ignored){}
  }return best;
 }
 public static String report(){StringBuilder out=new StringBuilder("این فهرست وجود موتور را نشان می‌دهد، نه موفقیت رندر روی دستگاه.\n\n");for(String mime:new String[]{"video/avc","video/hevc","video/av01","video/x-vnd.on2.vp9","video/x-vnd.on2.vp8","audio/mp4a-latm","audio/opus","audio/flac"}){boolean enc=false,dec=false;for(MediaCodecInfo c:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())for(String t:c.getSupportedTypes())if(t.equalsIgnoreCase(mime)){if(c.isEncoder())enc=true;else dec=true;}out.append(mime).append("\nEncode: ").append(enc).append(" / Decode: ").append(dec).append("\n\n");}return out+"خروجی صوت در این نسخه متصل نیست. ProRes/MOV/Alpha به موتور جدا نیاز دارند. WebGL فقط با مسیر ضبط GPU آزمایشی است.";}
}
