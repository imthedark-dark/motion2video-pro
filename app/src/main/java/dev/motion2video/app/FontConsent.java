package dev.motion2video.app;

import java.util.*;

/** Computes exactly which static Google references may reach the native worker. */
final class FontConsent {
 static final int MAX_BATCH=8;
 static Set<String> eligible(FontDiscovery discovery,Set<String> missing) {
  Set<String> result=new LinkedHashSet<>();
  for(String family:discovery.google)if(result.size()<MAX_BATCH&&missing.contains(FontCache.key(family))&&!FontDiscovery.bundled(family))result.add(family);
  return result;
 }

 static boolean mayReuse(Set<String> families,Set<String> remembered,boolean enabled,boolean userAction){
  if(!enabled||!userAction||families.isEmpty())return false;
  for(String family:families)if(!remembered.contains(FontCache.key(family)))return false;
  return true;
 }
}
