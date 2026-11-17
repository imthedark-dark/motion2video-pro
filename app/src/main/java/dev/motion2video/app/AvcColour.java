package dev.motion2video.app;
import java.io.*;import java.util.*;
/** Normalize AVC SPS VUI to the BT.709 limited-range samples we produce. */
public final class AvcColour {
 private static class Bits{String s;int p;Bits(String s){this.s=s;}int n(int n)throws IOException{if(n<0||n>31||p+n>s.length())throw new IOException("Invalid SPS");int v=0;for(int i=0;i<n;i++)v=(v<<1)|(s.charAt(p++)-'0');return v;}int ue()throws IOException{int zeros=0;while(n(1)==0){if(++zeros>24)throw new IOException("Invalid SPS integer");}return ((1<<zeros)-1)+n(zeros);}int se()throws IOException{int v=ue();return (v&1)==1?(v+1)/2:-v/2;}}
 private static String bits(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)for(int i=7;i>=0;i--)s.append(((v>>i)&1)==0?'0':'1');return s.toString();}
 private static byte[] packed(String s){while(s.length()%8!=0)s+='0';byte[] b=new byte[s.length()/8];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*8,i*8+8),2);return b;}
 public static byte[] nal(byte[] data)throws IOException{
  if(data.length==0||(data[0]&31)!=7)return data;ByteArrayOutputStream rb=new ByteArrayOutputStream();int zeros=0;for(int i=1;i<data.length;i++){int v=data[i]&255;if(zeros>=2&&v==3){zeros=0;continue;}rb.write(v);zeros=v==0?zeros+1:0;}
  String source=bits(rb.toByteArray());int last=source.lastIndexOf('1');if(last<0)throw new IOException("Missing SPS stop bit");source=source.substring(0,last+1);Bits b=new Bits(source);int profile=b.n(8);b.n(8);b.n(8);b.ue();
  if(Arrays.asList(100,110,122,244,44,83,86,118,128,138,139,134,135).contains(profile)){int chroma=b.ue();if(chroma==3)b.n(1);b.ue();b.ue();b.n(1);if(b.n(1)==1){int count=chroma==3?12:8;for(int i=0;i<count;i++)if(b.n(1)==1){int next=8,prev=8;for(int j=0;j<(i<6?16:64);j++){if(next!=0)next=(prev+b.se()+256)%256;prev=next==0?prev:next;}}}}
  b.ue();int order=b.ue();if(order==0)b.ue();else if(order==1){b.n(1);b.se();b.se();int count=b.ue();if(count>256)throw new IOException("SPS cycle too large");for(int i=0;i<count;i++)b.se();}
  b.ue();b.n(1);b.ue();b.ue();if(b.n(1)==0)b.n(1);b.n(1);if(b.n(1)==1)for(int i=0;i<4;i++)b.ue();if(b.n(1)==0)return data;
  if(b.n(1)==1&&b.n(8)==255){b.n(16);b.n(16);}if(b.n(1)==1)b.n(1);
  int signal=b.p;int present=b.n(1);String fixed;
  String colour="000000010000000100000001";
  if(present==0)fixed=source.substring(0,signal)+"1"+"101"+"0"+"1"+colour+source.substring(b.p);
  else{b.n(3);int range=b.p;b.n(1);int description=b.p;int described=b.n(1);int start=b.p;if(described==1)b.n(24);fixed=source.substring(0,range)+"0"+source.substring(range+1,description)+"1"+colour+source.substring(b.p);}
  byte[] raw=packed(fixed);ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data[0]);zeros=0;for(byte value:raw){int v=value&255;if(zeros>=2&&v<=3){out.write(3);zeros=0;}out.write(v);zeros=v==0?zeros+1:0;}return out.toByteArray();
 }
 static int start(byte[] b,int from){for(int i=from;i+3<b.length;i++)if(b[i]==0&&b[i+1]==0&&(b[i+2]==1||b[i+2]==0&&b[i+3]==1))return i;return -1;}
 public static byte[] annex(byte[] data)throws IOException{int first=start(data,0);if(first<0)return nal(data);ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data,0,first);int p=first;while(p>=0){int code=data[p+2]==1?3:4,next=start(data,p+code);int end=next<0?data.length:next;out.write(data,p,code);out.write(nal(Arrays.copyOfRange(data,p+code,end)));p=next;}return out.toByteArray();}
 public static void main(String[] a)throws Exception{java.nio.file.Files.write(new File(a[1]).toPath(),annex(java.nio.file.Files.readAllBytes(new File(a[0]).toPath())));}
}
