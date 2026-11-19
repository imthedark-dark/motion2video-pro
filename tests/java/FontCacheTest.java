package dev.motion2video.app;

import java.net.URI;
import java.nio.file.*;
import java.util.*;

public final class FontCacheTest {
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("font-cache-test");
  try{
   FontCache cache=new FontCache(root);
   FontDownload.Face face=new FontDownload.Face("Cairo","400","normal","U+0600-06FF",URI.create("https://fonts.gstatic.com/s/cairo/face.woff2"));
   byte[] bytes=FontDownloadTest.font();
   cache.save(List.of(new FontDownload.FileData(face,"font/woff2",bytes)),new FontDownload.Control());
   cache=new FontCache(root);
   FontCache.Entry entry=cache.list().get(0);
   try(FontCache.Scope pinned=cache.scope(Set.of("Cairo"),true)){
    if(cache.delete(null)||pinned.entry(entry.id)==null||!Arrays.equals(bytes,pinned.read(entry)))throw new AssertionError("Pinned cache read");
    if(!pinned.css(Set.of("cairo")).contains("/cached-fonts/"+entry.id)||pinned.css(Set.of("other")).length()!=0)
      throw new AssertionError("Scope or stylesheet invalid");
    if(cache.scope(Set.of("Never approved"),false).entry(entry.id)!=null)throw new AssertionError("Cache open to unrelated scope");
   }
   Path file=root.resolve(entry.id+".font");
   byte[] corrupt=bytes.clone();
   corrupt[corrupt.length-1]=1;
   Files.write(file,corrupt);
   if(cache.read(entry)!=null||Files.exists(file)||!new FontCache(root).list().isEmpty())throw new AssertionError("Corruption retained");
   cache.save(List.of(new FontDownload.FileData(face,"font/woff2",bytes)),new FontDownload.Control());
   if(!cache.delete("Cairo")||Files.exists(file)||!cache.list().isEmpty())throw new AssertionError("Cache delete failed");
   System.out.println("PASS: persistent offline cache, scoped lookup, SHA-256 removal, selected delete and active-render protection");
  }finally{
   try(java.util.stream.Stream<Path> files=Files.walk(root)){
    for(Path file:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(file);
   }
  }
 }
}
