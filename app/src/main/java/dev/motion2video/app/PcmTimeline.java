package dev.motion2video.app;
import java.io.*;
/** Streaming linear interpolation into 48kHz stereo PCM16. No whole-audio allocation. */
public final class PcmTimeline {
 public interface Checkpoint{void check()throws IOException;}
 private Checkpoint checkpoint=()->{};
 public PcmTimeline checked(Checkpoint c){checkpoint=c;return this;}
 public static final int RATE=48000;private final OutputStream output;private final double start,end,audible,volume,fadeIn,fadeOut;private final long frames;private long written;private boolean previous;private double time;private short left,right;
 public PcmTimeline(OutputStream out,double start,double end,double duration,double volume,double fadeIn,double fadeOut){output=out;this.start=start;this.end=end;audible=Math.min(end-start,duration);this.volume=volume;this.fadeIn=fadeIn;this.fadeOut=fadeOut;frames=Math.round(duration*RATE);}
 public static short gain(double value,double time,double audible,double volume,double in,double out){double g=volume;if(in>0)g*=Math.min(1,Math.max(0,time/in));if(out>0)g*=Math.min(1,Math.max(0,(audible-time)/out));return (short)Math.max(-32768,Math.min(32767,Math.round(value*g)));}
 private void sample(double l,double r)throws IOException{if((written&4095)==0)checkpoint.check();double t=written/(double)RATE;short a=gain(l,t,audible,volume,fadeIn,fadeOut),b=gain(r,t,audible,volume,fadeIn,fadeOut);output.write(a&255);output.write((a>>8)&255);output.write(b&255);output.write((b>>8)&255);written++;}
 public void accept(double at,short l,short r)throws IOException{
  if(!Double.isFinite(at)||(previous&&at<=time))return;
  while(written<frames){double target=start+written/(double)RATE;if(target>at)break;
   if(target>=end||(!previous&&target<at)||previous&&(target<time||at-time>.1)){sample(0,0);continue;}
   double fraction=previous?Math.max(0,Math.min(1,(target-time)/(at-time))):1;
   sample(previous?left+(l-left)*fraction:l,previous?right+(r-right)*fraction:r);
  }
  previous=true;time=at;left=l;right=r;
 }
 public boolean full(){return written>=frames;}
 public void finish()throws IOException{while(written<frames)sample(0,0);}
}
