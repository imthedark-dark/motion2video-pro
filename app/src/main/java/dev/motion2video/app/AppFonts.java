package dev.motion2video.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

final class AppFonts {
 private static FontCache cache;

 static synchronized FontCache cache(Context context) {
  if(cache==null)cache=new FontCache(context.getFilesDir().toPath().resolve("google-fonts"));
  return cache;
 }

 static SharedPreferences settings(Context context){return context.getSharedPreferences("native_fonts",Context.MODE_PRIVATE);}
 static boolean enabled(Context context){return settings(context).getBoolean("enabled",true);}

 static Set<String> needed(FontDiscovery discovery){
  Set<String> names=new LinkedHashSet<>(discovery.google);
  names.addAll(discovery.declared);
  return names;
 }

 static FontCache.Scope scope(Context context,FontDiscovery discovery,boolean pin){
  if(!enabled(context))return null;
  Set<String> wanted=needed(discovery);
  wanted.removeIf(FontDiscovery::bundled);
  // Avoid cache I/O on the ordinary render path when no downloadable font is used.
  if(wanted.isEmpty())return null;
  try{return cache(context).scope(wanted,pin);}catch(java.io.IOException unavailable){return null;}
 }

 private AppFonts(){}
}
