package dev.motion2video.app;
import org.json.*;import java.io.*;
public final class QueueLedgerTest {
 private static String id(int i){return String.format("%08x-1234-1234-1234-123456789abc",i);}
 public static void main(String[] args)throws Exception{
  QueueLedger q=new QueueLedger(new JSONArray());q.add(id(1),"اول",1024,30,new JSONObject());q.add(id(2),"<img>",1024,60,new JSONObject());if(!id(1).equals(q.claim()))throw new AssertionError("FIFO");q.update(id(1),"error",0,"fixture");if(!id(2).equals(q.claim()))throw new AssertionError("Failure stopped queue");q.update(id(2),"complete",60,"");q.saved(id(2),false);if(q.find(id(2)).getBoolean("saved"))throw new AssertionError("Unsaved export evictable");
  q.add(id(3),"cancel",1024,30,new JSONObject());if(!q.cancelWaiting(id(3))||q.waiting()||q.claim()!=null)throw new AssertionError("Waiting cancellation");
  q.add(id(4),"active",1024,30,new JSONObject());q.add(id(5),"waiting",1024,30,new JSONObject());q.claim();q.recover();if(!q.find(id(4)).getString("phase").equals("error")||!q.find(id(5)).getString("phase").equals("paused")||q.waiting())throw new AssertionError("Unsafe recovery");
  q=new QueueLedger(new JSONArray());for(int n=0;n<8;n++)q.add(id(n),"bounded",1,30,new JSONObject());try{q.add(id(9),"overflow",1,30,new JSONObject());throw new AssertionError();}catch(IOException expected){}if(q.snapshot().length()!=8)throw new AssertionError("Non-atomic limit");
  q=new QueueLedger(new JSONArray());for(int n=0;n<3;n++)q.add(id(n),"large",20L*1024*1024,30,new JSONObject());try{q.add(id(4),"overflow",1,30,new JSONObject());throw new AssertionError();}catch(IOException expected){}
  try{q.add("../file","bad",1,30,new JSONObject());throw new AssertionError();}catch(IOException expected){}
  q=new QueueLedger(new JSONArray());for(int n=0;n<40;n++){q.add(id(n),"history",1,30,new JSONObject());q.update(id(n),"complete",30,"");}if(q.snapshot().length()!=24)throw new AssertionError("History unbounded");
  System.out.println("PASS: production queue FIFO, failure continuation, cancellation, paused recovery, count/byte/UUID limits and bounded history");
 }
}
