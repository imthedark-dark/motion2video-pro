package dev.motion2video.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private face files with an atomic metadata index. All mutation and entry reads are serialized. */
final class FontCache {
 static final long LIMIT=100L*1024*1024;
 static final int ENTRY_LIMIT=256;
 private static final String LICENSE_NOTE="Upstream terms apply; license not verified";
 private final Path directory;
 private Map<String,Entry> entries;
 private int leases;

 static final class Entry {
  final String id,family,weight,style,range,mime,sha,license;
  final int bytes;
  final long date;
  Entry(String family,String weight,String style,String range,String mime,String sha,int bytes,long date){
   this.family=family;this.weight=weight;this.style=style;this.range=range;this.mime=mime;this.sha=sha;
   this.bytes=bytes;this.date=date;license=LICENSE_NOTE;
   id=digest((key(family)+"\n"+weight+"\n"+style+"\n"+range).getBytes(StandardCharsets.UTF_8));
  }
 }

 static final class Scope implements AutoCloseable {
  private final FontCache cache;
  private final Map<String,Entry> entries;
  private final boolean pinned;
  private boolean closed;
  Scope(FontCache cache,Map<String,Entry> entries,boolean pinned){this.cache=cache;this.entries=entries;this.pinned=pinned;}
  String css(Set<String> families){
   StringBuilder out=new StringBuilder();
   for(Entry face:entries.values())if(families.contains(key(face.family))) {
    out.append("@font-face{font-family:'").append(face.family).append("';font-weight:").append(face.weight)
      .append(";font-style:").append(face.style).append(";font-display:swap;src:url(https://motion.local/cached-fonts/").append(face.id).append(')');
    if(!face.range.isEmpty())out.append(";unicode-range:").append(face.range);
    out.append("}\n");
   }
   return out.toString();
  }
  Set<String> families(){
   Set<String> names=new LinkedHashSet<>();
   for(Entry face:entries.values())names.add(key(face.family));
   return names;
  }
  Entry entry(String id){return entries.get(id);}
  byte[] read(Entry e){return cache.read(e);}
  @Override public void close(){
   synchronized(cache){if(!closed&&pinned)cache.leases--;closed=true;}
  }
 }

 FontCache(Path directory){this.directory=directory;}

 private void open() throws IOException {
  if(entries!=null)return;
  Files.createDirectories(directory);
  entries=new LinkedHashMap<>();
  Path index=directory.resolve("index");
  try {
   if(Files.exists(index)){
    if(Files.size(index)>2*1024*1024)throw FontDownload.failure();
    try(DataInputStream in=new DataInputStream(Files.newInputStream(index))){
     int count=in.readInt();
     if(count<0||count>ENTRY_LIMIT)throw FontDownload.failure();
     long total=0;
     for(int i=0;i<count;i++){
      Entry e=new Entry(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readInt(),in.readLong());
      if(!e.family.equals(FontDiscovery.clean(e.family)) || FontDiscovery.bundled(e.family) ||
         !FontDownload.validDescriptors(e.weight,e.style,e.range) || !e.sha.matches("[0-9a-f]{64}") ||
         !Arrays.asList("font/woff2","font/woff","font/ttf").contains(e.mime) ||
         e.bytes<1 || e.bytes>FontDownload.FILE_LIMIT || (total+=e.bytes)>LIMIT || entries.put(e.id,e)!=null)
       throw FontDownload.failure();
     }
    }
   }
  }catch(IOException invalid){entries.clear();Files.deleteIfExists(index);}
  try(DirectoryStream<Path> files=Files.newDirectoryStream(directory)){
   for(Path file:files){
    String name=file.getFileName().toString();
    if(name.endsWith(".tmp")||(name.endsWith(".font")&&!entries.containsKey(name.substring(0,name.length()-5))))Files.deleteIfExists(file);
   }
  }
 }

 synchronized List<Entry> list() throws IOException {
  open();
  return new ArrayList<>(entries.values());
 }

 synchronized Scope scope(Collection<String> families,boolean pin) throws IOException {
  open();
  Set<String> wanted=new HashSet<>();
  for(String family:families)wanted.add(key(family));
  Map<String,Entry> selected=new LinkedHashMap<>();
  for(Entry e:new ArrayList<>(entries.values()))if(wanted.contains(key(e.family))&&read(e)!=null)selected.put(e.id,e);
  if(pin)leases++;
  return new Scope(this,selected,pin);
 }

 synchronized byte[] read(Entry entry) {
  Entry current=entries.get(entry.id);
  if(current==null||!current.sha.equals(entry.sha))return null;
  try {
   Path path=file(entry);
   if(Files.size(path)!=entry.bytes)throw FontDownload.failure();
   byte[] bytes=new byte[entry.bytes];
   try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){
    in.readFully(bytes);
    if(in.read()!=-1)throw FontDownload.failure();
   }
   if(!entry.sha.equals(digest(bytes))||!FontDownload.validBytes(entry.mime,bytes))throw FontDownload.failure();
   return bytes;
  }catch(IOException corrupt){
   entries.remove(entry.id);
   try{Files.deleteIfExists(file(entry));persist(entries);}catch(IOException ignored){}
   return null;
  }
 }

 synchronized void save(List<FontDownload.FileData> font,FontDownload.Control control) throws IOException {
  open();
  Map<String,Entry> next=new LinkedHashMap<>(entries);
  List<Path> created=new ArrayList<>();
  boolean committed=false;
  try {
   for(FontDownload.FileData data:font){
    control.check();
    FontDownload.Face f=data.face;
    Entry e=new Entry(f.family,f.weight,f.style,f.range,data.mime,digest(data.bytes),data.bytes.length,System.currentTimeMillis());
    // Existing entries are immutable, including ones currently in use by a render.
    if(next.containsKey(e.id))continue;
    next.put(e.id,e);
    if(next.size()>ENTRY_LIMIT||bytes(next.values())>LIMIT)throw FontDownload.failure();
    Path target=file(e);
    created.add(target);
    atomicWrite(target,out->out.write(data.bytes));
   }
   control.check();
   persist(next);
   entries=next;
   committed=true;
  }finally{if(!committed)for(Path file:created)Files.deleteIfExists(file);}
 }

 synchronized boolean delete(String family) throws IOException {
  open();
  if(leases>0)return false;
  Map<String,Entry> next=new LinkedHashMap<>();
  for(Entry e:entries.values())if(family!=null&&!key(family).equals(key(e.family)))next.put(e.id,e);
  persist(next);
  Map<String,Entry> old=entries;
  entries=next;
  for(Entry e:old.values())if(!next.containsKey(e.id))Files.deleteIfExists(file(e));
  return true;
 }

 private void persist(Map<String,Entry> value)throws IOException{
  atomicWrite(directory.resolve("index"),out->{
   out.writeInt(value.size());
   for(Entry e:value.values()){
    out.writeUTF(e.family);out.writeUTF(e.weight);out.writeUTF(e.style);out.writeUTF(e.range);
    out.writeUTF(e.mime);out.writeUTF(e.sha);out.writeInt(e.bytes);out.writeLong(e.date);
   }
  });
 }

 private interface Writer {void write(DataOutputStream out)throws IOException;}
 private static void atomicWrite(Path target,Writer writer)throws IOException{
  Path temporary=target.resolveSibling(target.getFileName()+".tmp");
  try{
   try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(temporary))){writer.write(out);}
   Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(temporary);}
 }

 private Path file(Entry e){return directory.resolve(e.id+".font");}
 static String key(String family){return family.toLowerCase(Locale.ROOT);}
 static long bytes(Collection<Entry> entries){long total=0;for(Entry e:entries)total+=e.bytes;return total;}
 private static String digest(byte[] bytes){
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
   StringBuilder hex=new StringBuilder(64);
   for(byte b:hash)hex.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
   return hex.toString();
  }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
 }
}
