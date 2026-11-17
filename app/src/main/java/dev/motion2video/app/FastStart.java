package dev.motion2video.app;
import java.io.*;import java.util.*;
/** Relocates a non-fragmented ISO BMFF moov atom and fixes stco/co64 offsets. */
public final class FastStart {
 static final class Box{long at,size;String type;Box(long a,long s,String t){at=a;size=s;type=t;}}
 public static void rewrite(File input)throws Exception{
  ArrayList<Box> boxes=new ArrayList<>();Box moov=null;long insert=0;
  try(RandomAccessFile in=new RandomAccessFile(input,"r")){long pos=0;while(pos<in.length()){in.seek(pos);long size=Integer.toUnsignedLong(in.readInt());String type;in.seek(pos+4);byte[] tag=new byte[4];in.readFully(tag);type=new String(tag,"US-ASCII");long head=8;if(size==1){size=in.readLong();head=16;}if(size==0)size=in.length()-pos;if(size<head||size>in.length()-pos)throw new IOException("Invalid MP4 box");Box b=new Box(pos,size,type);boxes.add(b);if(type.equals("moof"))throw new IOException("Fragmented MP4 unsupported");if(type.equals("moov"))moov=b;if(type.equals("ftyp"))insert=pos+size;pos+=size;}
   if(moov==null||insert==0)throw new IOException("MP4 metadata missing");if(moov.at==insert)return;if(moov.size>64*1024*1024)throw new IOException("Metadata too large");byte[] data=new byte[(int)moov.size];in.seek(moov.at);in.readFully(data);patch(data,0,data.length,insert,moov.at,moov.size);
   File temp=new File(input.getParentFile(),input.getName()+".faststart");try(RandomAccessFile out=new RandomAccessFile(temp,"rw")){out.setLength(0);for(Box b:boxes){if(b==moov)continue;copy(in,out,b.at,b.size);if(b.at+b.size==insert)out.write(data);}}catch(Exception e){temp.delete();throw e;}
   if(!temp.renameTo(input)){temp.delete();throw new IOException("Cannot replace MP4");}
  }
 }
 static long u32(byte[] b,int p){return ((b[p]&255L)<<24)|((b[p+1]&255L)<<16)|((b[p+2]&255L)<<8)|(b[p+3]&255L);}
 static long u64(byte[] b,int p){return(u32(b,p)<<32)|u32(b,p+4);}
 static void put(byte[] b,int p,long value,int n){for(int i=n-1;i>=0;i--){b[p+i]=(byte)value;value>>>=8;}}
 static void patch(byte[] b,int from,int to,long insert,long old,long delta)throws Exception{int p=from;while(p+8<=to){long size=u32(b,p);int head=8;if(size==1){if(p+16>to)throw new IOException();size=u64(b,p+8);head=16;}if(size==0)size=to-p;if(size<head||size>to-p)throw new IOException("Invalid metadata box");String type=new String(b,p+4,4,"US-ASCII");int start=p+head,end=p+(int)size;
  if(type.equals("stco")||type.equals("co64")){if(start+8>end)throw new IOException();long count=u32(b,start+4);int bytes=type.equals("stco")?4:8;if(count>(end-start-8)/bytes)throw new IOException();for(int i=0;i<count;i++){int at=start+8+i*bytes;long offset=bytes==4?u32(b,at):u64(b,at);if(offset>=insert&&offset<old)offset+=delta;if(bytes==4&&offset>0xffffffffL)throw new IOException("stco overflow");put(b,at,offset,bytes);}}
  else if(Arrays.asList("moov","trak","mdia","minf","stbl").contains(type))patch(b,start,end,insert,old,delta);p=end;}}
 static void copy(RandomAccessFile in,RandomAccessFile out,long offset,long count)throws Exception{in.seek(offset);byte[] buffer=new byte[65536];while(count>0){int n=in.read(buffer,0,(int)Math.min(count,buffer.length));if(n<0)throw new EOFException();out.write(buffer,0,n);count-=n;}}
 public static void main(String[] args)throws Exception{rewrite(new File(args[0]));}
}
