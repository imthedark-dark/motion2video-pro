package android.net;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class Uri {
 private final URI uri;
 private Uri(String value){uri=URI.create(value);}
 public static Uri parse(String value){return new Uri(value);}
 public String getHost(){return uri.getHost();}
 public String getScheme(){return uri.getScheme();}
 public String getPath(){return uri.getPath();}
 public String getUserInfo(){return uri.getUserInfo();}
 public String getEncodedQuery(){return uri.getRawQuery();}
 public int getPort(){return uri.getPort();}
 public List<String> getQueryParameters(String name){
  List<String> values=new ArrayList<>();
  if(uri.getRawQuery()==null)return values;
  for(String pair:uri.getRawQuery().split("&")){
   String[] parts=pair.split("=",2);
   if(parts.length==2&&name.equals(parts[0]))values.add(URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
  }
  return values;
 }
}
