package dev.motion2video.app;

import java.nio.ByteBuffer;

/** Reusable BT.709 limited converter; identical integer math to the original path.
 * Row writes avoid millions of Image.Plane/ByteBuffer accessor calls per frame.
 */
public final class Yuv420Converter {
 private final int width,height;
 private final byte[] y0,y1,u,v;
 public Yuv420Converter(int width,int height){
  if(width<=0||height<=0||(width&1)!=0||(height&1)!=0)throw new IllegalArgumentException("Even dimensions required");
  this.width=width;this.height=height;y0=new byte[width];y1=new byte[width];u=new byte[width/2];v=new byte[width/2];
 }
 private static int clamp(int n){return Math.max(0,Math.min(255,n));}
 private static byte luma(int p){return (byte)clamp(((47*((p>>16)&255)+157*((p>>8)&255)+16*(p&255)+128)>>8)+16);}
 private static void row(ByteBuffer out,int base,int stride,int pixelStride,int index,byte[] data){
  int offset=base+index*stride;
  if(pixelStride==1){out.position(offset);out.put(data);}else for(int x=0;x<data.length;x++)out.put(offset+x*pixelStride,data[x]);
 }
 public void convert(int[] pixels,ByteBuffer yy,int ys,int yp,ByteBuffer uu,int us,int up,ByteBuffer vv,int vs,int vp){
  if(pixels.length<width*height||ys<=0||us<=0||vs<=0||yp<=0||up<=0||vp<=0)throw new IllegalArgumentException("Invalid planes");
  ByteBuffer by=yy.duplicate(),bu=uu.duplicate(),bv=vv.duplicate();int yBase=by.position(),uBase=bu.position(),vBase=bv.position();
  for(int y=0;y<height;y+=2){int top=y*width,bottom=top+width;
   for(int x=0;x<width;x+=2){int a=pixels[top+x],b=pixels[top+x+1],c=pixels[bottom+x],d=pixels[bottom+x+1];
    y0[x]=luma(a);y0[x+1]=luma(b);y1[x]=luma(c);y1[x+1]=luma(d);
    int r=(((a>>16)&255)+((b>>16)&255)+((c>>16)&255)+((d>>16)&255))/4;
    int g=(((a>>8)&255)+((b>>8)&255)+((c>>8)&255)+((d>>8)&255))/4;
    int blue=((a&255)+(b&255)+(c&255)+(d&255))/4;
    u[x/2]=(byte)clamp(((-26*r-86*g+112*blue+128)>>8)+128);
    v[x/2]=(byte)clamp(((112*r-102*g-10*blue+128)>>8)+128);
   }
   row(by,yBase,ys,yp,y,y0);row(by,yBase,ys,yp,y+1,y1);row(bu,uBase,us,up,y/2,u);row(bv,vBase,vs,vp,y/2,v);
  }
 }
}
