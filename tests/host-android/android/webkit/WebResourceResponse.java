package android.webkit;
import java.io.InputStream;
import java.util.Map;
public class WebResourceResponse {
 public final String mime;
 public final int status;
 public final InputStream data;
 public Map<String,String> headers=Map.of();
 public WebResourceResponse(String mime,String encoding,InputStream data){this(mime,encoding,200,"OK",Map.of(),data);}
 public WebResourceResponse(String mime,String encoding,int status,String reason,Map<String,String> headers,InputStream data){
  this.mime=mime;this.status=status;this.data=data;this.headers=headers;
 }
 public void setResponseHeaders(Map<String,String> headers){this.headers=headers;}
}
