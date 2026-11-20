package dev.motion2video.app;

import android.os.Build;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Match only requested family names. No system font path or inventory leaves this class. */
final class InstalledFonts {
 static Set<String> matching(Set<String> requested){
  if(requested.isEmpty()||Build.VERSION.SDK_INT<29)return Collections.emptySet();
  return compareRequested(requested);
 }

 @android.annotation.TargetApi(29)
 private static Set<String> compareRequested(Set<String> requested){
  Set<String> matches=new HashSet<>();
  try{
   for(Font font:SystemFonts.getAvailableFonts()){
    matchNameTable(font.getBuffer(),font.getTtcIndex(),requested,matches);
    if(matches.size()==requested.size())break;
   }
  }catch(RuntimeException unavailable){/* Unconfirmed names remain unknown. */}
  return matches;
 }

 private static void matchNameTable(ByteBuffer raw,int ttcIndex,Set<String> wanted,Set<String> matches){
  ByteBuffer data=raw.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
  try{
   int base=0;
   if(data.getInt(0)==0x74746366){
    if(ttcIndex<0||ttcIndex>=256||ttcIndex>=data.getInt(8))return;
    base=data.getInt(12+ttcIndex*4);
   }
   int tables=Short.toUnsignedInt(data.getShort(base+4));
   if(tables>256)return;
   for(int i=0;i<tables;i++){
    int row=base+12+i*16;
    if(data.getInt(row)!=0x6e616d65)continue;
    int table=data.getInt(row+8), size=data.getInt(row+12);
    if(table<0||size<6||table>data.limit()-size)return;
    int records=Short.toUnsignedInt(data.getShort(table+2));
    int strings=Short.toUnsignedInt(data.getShort(table+4));
    if(records>1024||6+12*records>size)return;
    for(int n=0;n<records;n++){
     int record=table+6+n*12;
     int name=Short.toUnsignedInt(data.getShort(record+6));
     int platform=Short.toUnsignedInt(data.getShort(record));
     if((name!=1&&name!=16)||(platform!=0&&platform!=3))continue;
     int length=Short.toUnsignedInt(data.getShort(record+8));
     int start=strings+Short.toUnsignedInt(data.getShort(record+10));
     if(length==0||length>128||(length&1)!=0||start>size-length)continue;
     byte[] text=new byte[length];
     ByteBuffer entry=data.duplicate();
     entry.position(table+start);
     entry.get(text);
     String value=FontCache.key(new String(text,StandardCharsets.UTF_16BE).trim());
     if(wanted.contains(value))matches.add(value);
    }
    return;
   }
  }catch(IndexOutOfBoundsException malformed){/* Ignore metadata we cannot prove. */}
 }

 private InstalledFonts(){}
}
