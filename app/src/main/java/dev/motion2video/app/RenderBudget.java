package dev.motion2video.app;
import java.io.IOException;
/** Monotonic deadlines and a bounded Java-heap budget, independent of video timestamps. */
public final class RenderBudget {
 public static final long STALL_MS=45000,READY_MS=120000,MAX_JOB_MS=2*60*60*1000L;
 private final long start,ceiling;private volatile long activity;
 public RenderBudget(long now,long maxHeap){start=activity=now;ceiling=Math.min(256L*1024*1024,maxHeap*85/100);}
 public void progress(long now){activity=now;}
 public void check(long now,long used,long reserve)throws IOException{
  if(now-start>=MAX_JOB_MS)throw new IOException("مهلت دو ساعتهٔ رندر تمام شد؛ بخش کوتاه‌تری را اجرا کن.");
  if(now-activity>=READY_MS)throw new IOException("اجرای موشن بیش از حد متوقف مانده است؛ کد یا منابع آن را بررسی کن.");
  if(used<0||reserve<0||used>ceiling||reserve>ceiling-used)throw new IOException("حافظهٔ کافی برای رندر امن نیست؛ اندازه یا حجم HTML را کاهش بده.");
 }
 public static long frameReserve(int width,int height){return (long)width*height*24+16L*1024*1024;}
}
