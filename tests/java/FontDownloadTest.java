package dev.motion2video.app;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FontDownloadTest {
 private static final String FONT="https://fonts.gstatic.com/s/cairo/v1/test.woff2";
 static void reject(Checked action)throws Exception{
  try{action.run();throw new AssertionError("accepted unsafe input");}catch(IOException expected){}
 }
 interface Checked{void run()throws Exception;}

 private static final String CSS="@font-face {font-family:'Cairo';font-style:normal;font-weight:100 900;unicode-range:U+0600-06FF;src:url("+FONT+") format('woff2');}";
 static FontDownload.Reply text(int code,String css){return new FontDownload.Reply(code,"text/css",css.getBytes(StandardCharsets.UTF_8));}
 static byte[] font(){
  return ByteBuffer.allocate(52).putInt(0x774f4632).putInt(0x10000).putInt(52).putShort((short)1)
   .putShort((short)0).putInt(100).putInt(1).array();
 }
 static void require(boolean value){if(!value)throw new AssertionError();}

 public static void main(String[] args)throws Exception{
  FontDiscovery found=FontDiscovery.scan("<link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Cairo&amp;display=swap'>"
   +"<style>/* @import 'https://fonts.googleapis.com/css?family=Hidden'; */"
   +"@import url(\"https://fonts.googleapis.com/css?family=Lalezar\");div {font-family: 'Roboto',sans-serif;}</style>"
   +"<script>const x=\"<link rel=stylesheet href='https://fonts.googleapis.com/css2?family=Secret'>\";</script>"
   +"<div style='font-family: Installed'></div><link rel=preconnect href='https://fonts.googleapis.com/css?family=Ignored'>");
  require(found.google.equals(new java.util.LinkedHashSet<>(List.of("Cairo","Lalezar"))));
  require(found.declared.containsAll(List.of("Roboto","Installed")));
  require(FontDiscovery.scan("<style>x{content:\"; @import 'https://fonts.googleapis.com/css?family=Fake';\"}</style>").google.isEmpty());
  require(FontDiscovery.scan("<a href='https://fonts.googleapis.com/css?family=Fake'>font</a>").google.isEmpty());
  require(FontDiscovery.scan("<div style='font-family:Cairo'></div>").google.isEmpty());
  require(FontDiscovery.scan("<template><style>@import 'https://fonts.googleapis.com/css2?family=Cairo';</style></template>").google.isEmpty());
  for(String url:List.of(FONT.replace("https:","http:"),FONT.replace("fonts.gstatic.com","127.0.0.1"),
      FONT.replace("fonts.gstatic.com","evil.invalid"),FONT.replace("fonts.gstatic.com","fonts.gstatic.com:443"),
      FONT.replace("fonts.gstatic.com","user@fonts.gstatic.com"),FONT+"?extra=1",FONT.replace("/s/","/s/../")))
   reject(()->FontDownload.allowed(url,false));
  String approvedImport="<style>@import url('https://fonts.googleapis.com/css2?family=Cairo');</style>";
  var eligible=FontConsent.eligible(FontDiscovery.scan(approvedImport),java.util.Set.of("cairo"));
  require(FontConsent.eligible(FontDiscovery.scan("<div style='font-family:Cairo'></div>"),java.util.Set.of("cairo")).isEmpty());
  require(eligible.equals(java.util.Set.of("Cairo")));
  require(!FontConsent.mayReuse(eligible,java.util.Set.of(),true,true));
  require(!FontConsent.mayReuse(eligible,java.util.Set.of("cairo"),true,false));
  require(!FontConsent.mayReuse(eligible,java.util.Set.of("cairo"),false,true));
  require(!FontConsent.mayReuse(java.util.Set.of("Cairo","Lalezar"),java.util.Set.of("cairo"),true,true));
  require(FontConsent.mayReuse(eligible,java.util.Set.of("cairo"),true,true));
  FontDownload.allowed(FONT,false);
  FontDownload.allowed("https://fonts.googleapis.com/css2?family=Cairo",true);
  FontDownload fake=new FontDownload((uri,limit,control)->uri.getHost().equals("fonts.googleapis.com")
   ?text(200,CSS):new FontDownload.Reply(200,"font/woff2",font()));
  require(fake.download("Cairo",new FontDownload.Control(),n->{}).size()==1);
  for(FontDownload.Reply bad:List.of(new FontDownload.Reply(200,"image/png",font()),
    new FontDownload.Reply(200,"font/woff2",new byte[48]),new FontDownload.Reply(200,"font/woff2",new byte[FontDownload.FILE_LIMIT+1]),
    new FontDownload.Reply(302,"font/woff2",font()))){
   FontDownload d=new FontDownload((uri,limit,control)->uri.getHost().equals("fonts.googleapis.com")?text(200,CSS):bad);
   reject(()->d.download("Cairo",new FontDownload.Control(),n->{}));
  }
  for(String css:List.of(CSS.replace(FONT,"https://evil.invalid/media.woff2"),"@import url("+FONT+");",
    CSS.replace("}", ""),CSS.replace("100 900","url(x)"),CSS.repeat(17)))
   reject(()->FontDownload.parse(css,"Cairo"));
  require(FontDownload.parse("@import url(https://evil.invalid); body {color:red}"+CSS,"Cairo").size()==1);
  reject(()->new FontDownload((u,l,c)->{throw new SocketTimeoutException();}).download("Cairo",new FontDownload.Control(),n->{}));
  reject(()->fake.download("Cairo",new FontDownload.Control(0),n->{}));
  FontDownload.Control cancelled=new FontDownload.Control();
  cancelled.cancel();
  reject(()->fake.download("Cairo",cancelled,n->{}));
  reject(()->new FontDownload((u,l,c)->{c.cancel();return text(200,CSS);}).download("Cairo",new FontDownload.Control(),n->{}));
  System.out.println("PASS: static Google reference discovery, HTTPS host validation, redirects, consent scope, CSS and binary limits, MIME, magic, deadline and cancellation");
 }
}
