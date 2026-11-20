package dev.motion2video.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** A bounded static scan. Only stylesheet links and CSS imports authorize Google candidates. */
final class FontDiscovery {
 static final Set<String> BUNDLED = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
  "Vazirmatn", "Orbitron", "Amiri", "Noto Naskh Arabic", "Scheherazade New", "Anton", "Inter")));
 static final int MAX_FAMILIES = 24;
 private static final int MAX_HTML = 20 * 1024 * 1024, MAX_CSS = 256 * 1024, MAX_TAG = 8192;
 private static final Pattern TAG = Pattern.compile("^</?([a-z][a-z0-9:-]*)", Pattern.CASE_INSENSITIVE);
 private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z][a-zA-Z0-9:_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
 private static final Pattern IMPORT = Pattern.compile("(?i)(?:^|[;}])\\s*@import\\s+(?:url\\(\\s*)?(?:\"([^\"\\r\\n]{1,4096})\"|'([^'\\r\\n]{1,4096})'|([^\\s);]{1,4096}))");
 private static final Set<String> RAW_TAGS = new HashSet<>(Arrays.asList(
  "style","script","textarea","title","xmp","iframe","noscript"));
 private static final Pattern FAMILY = Pattern.compile("(?i)(?:^|[;{])\\s*font-family\\s*:\\s*([^;{}]{1,1024})");
 private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
  "serif","sans-serif","monospace","system-ui","cursive","fantasy","emoji","math","fangsong","inherit","initial","unset","revert"));
 final Set<String> declared = new LinkedHashSet<>();
 final Set<String> google = new LinkedHashSet<>();
 private int cssLeft = MAX_CSS;

 static FontDiscovery scan(String html) {
  FontDiscovery result = new FontDiscovery();
  if (html.length() > MAX_HTML) return result;
  int templates=0;
  for (int start = html.indexOf('<'); start >= 0;) {
   if (html.startsWith("<!--", start)) {
    int end = html.indexOf("-->", start + 4);
    if (end < 0) break;
    start = html.indexOf('<', end + 3);
    continue;
   }
   int end = tagEnd(html, start);
   if (end < 0) break;
   String tag = html.substring(start, end + 1);
   Matcher name = TAG.matcher(tag);
   if (name.find()) {
    String type = name.group(1).toLowerCase(Locale.ROOT);
    boolean closingTag=tag.startsWith("</");
    if(type.equals("template"))templates=closingTag?Math.max(0,templates-1):templates+1;
    if(closingTag){start=html.indexOf('<',end+1);continue;}
    if (RAW_TAGS.contains(type)) {
     int close = closing(html, end + 1, type);
     if (close < 0) break;
     if (templates==0&&type.equals("style")) result.css(html.substring(end + 1, Math.min(close, end + 1 + result.cssLeft)), true);
     start = close;
     continue;
    }
    if(templates>0){start=html.indexOf('<',end+1);continue;}
    Map<String,String> attrs = new HashMap<>();
    Matcher attr = ATTRIBUTE.matcher(tag);
    attr.region(name.end(), tag.length());
    while (attr.find()) attrs.putIfAbsent(attr.group(1).toLowerCase(Locale.ROOT), value(attr));
    if (type.equals("link") && Arrays.asList(attrs.getOrDefault("rel","").toLowerCase(Locale.ROOT).split("\\s+")).contains("stylesheet")) {
     result.reference(attrs.getOrDefault("href","").replace("&amp;", "&").replace("&#38;", "&").replace("&#x26;", "&"));
    }
    if (attrs.containsKey("style")) result.css(attrs.get("style"), false);
   }
   start = html.indexOf('<', end + 1);
  }
  return result;
 }

 private static int tagEnd(String html, int start) {
  char quote = 0;
  for (int i = start+1; i < Math.min(html.length(),start+MAX_TAG); i++) {
   char c = html.charAt(i);
   if (quote != 0) { if (c == quote) quote = 0; }
   else if (c == '\'' || c == '"') quote = c;
   else if (c == '>') return i;
  }
  return -1;
 }

 private static int closing(String html, int start, String name) {
  String needle = "</" + name;
  for (int i = html.indexOf('<',start); i >= 0; i = html.indexOf('<',i+1)) {
   int boundary = i + needle.length();
   if (html.regionMatches(true,i,needle,0,needle.length()) && boundary < html.length()
       && (html.charAt(boundary)=='>' || Character.isWhitespace(html.charAt(boundary)))) return i;
  }
  return -1;
 }

 private static String value(Matcher match) {
  for (int i = match.groupCount()-2; i <= match.groupCount(); i++) if (match.group(i)!=null) return match.group(i);
  return "";
 }

 private void css(String value, boolean allowImports) {
  int length = Math.min(value.length(), cssLeft);
  if (length == 0) return;
  cssLeft -= length;
  String css = withoutComments(value.substring(0,length));
  if (allowImports) {
   int depth=0;
   boolean statement=true;
   char quote=0;
   for(int i=0;i<css.length();i++){
    char c=css.charAt(i);
    if(quote!=0){
     if(c=='\\') i++;
     else if(c==quote) quote=0;
     continue;
    }
    if(c=='\''||c=='"'){quote=c;statement=false;continue;}
    if(c=='{'){depth++;statement=true;continue;}
    if(c=='}'){depth=Math.max(0,depth-1);statement=true;continue;}
    if(c==';'){statement=true;continue;}
    if(Character.isWhitespace(c))continue;
    if(statement&&depth==0&&c=='@'){
     Matcher m=IMPORT.matcher(css);
     m.region(i,Math.min(css.length(),i+4200));
     if(m.lookingAt())reference(value(m));
    }
    statement=false;
   }
  }
  Matcher m = FAMILY.matcher(css);
  while (m.find()) for (String item : m.group(1).split(",")) {
   String name = clean(item.replaceFirst("(?i)\\s*!important\\s*$", ""));
   if (name != null && !GENERIC.contains(name.toLowerCase(Locale.ROOT)) && declared.size()<MAX_FAMILIES) declared.add(name);
  }
 }

 private static String withoutComments(String text){
  StringBuilder out=new StringBuilder(text.length());
  char quote=0;
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);
   if(quote==0&&c=='/'&&i+1<text.length()&&text.charAt(i+1)=='*'){
    int end=text.indexOf("*/",i+2);
    if(end<0)break;
    i=end+1;
    out.append(' ');
    continue;
   }
   out.append(c);
   if(quote!=0&&c=='\\'&&i+1<text.length())out.append(text.charAt(++i));
   else if(c==quote)quote=0;
   else if(quote==0&&(c=='\''||c=='"'))quote=c;
  }
  return out.toString();
 }

 void merge(FontDiscovery other){
  for(String family:other.google)if(google.size()<MAX_FAMILIES)google.add(family);
  for(String family:other.declared)if(declared.size()<MAX_FAMILIES)declared.add(family);
 }

 private void reference(String url) {
  google.addAll(families(url, MAX_FAMILIES-google.size()));
 }

 static Set<String> families(String raw, int limit) {
  Set<String> found = new LinkedHashSet<>();
  if (raw.length()>4096 || limit<=0) return found;
  try {
   URI uri = URI.create(raw);
   if (!"https".equals(uri.getScheme()) || !"fonts.googleapis.com".equals(uri.getRawAuthority())
       || !("/css".equals(uri.getRawPath()) || "/css2".equals(uri.getRawPath())) || uri.getRawQuery()==null) return found;
   for (String param : uri.getRawQuery().split("&")) if (param.startsWith("family=")) {
    String decoded = URLDecoder.decode(param.substring(7),StandardCharsets.UTF_8.name());
    for (String entry : decoded.split("\\|")) {
     String name = clean(entry.split(":",2)[0]);
     if (name != null && found.size()<limit) found.add(name);
    }
   }
  } catch (Exception invalid) { found.clear(); }
  return found;
 }

 static String clean(String text) {
  String name = text.trim();
  if (name.length()>=2 && ((name.startsWith("\"")&&name.endsWith("\""))||(name.startsWith("'")&&name.endsWith("'")))) {
   name = name.substring(1,name.length()-1).trim();
  }
  if (!name.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{0,63}")) return null;
  return name.replaceAll(" +"," ");
 }

 static boolean bundled(String family) {
  for (String name : BUNDLED) if (name.equalsIgnoreCase(family)) return true;
  return false;
 }
}
