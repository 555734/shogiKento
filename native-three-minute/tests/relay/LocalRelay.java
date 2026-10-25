package com.presentsoft.shogi3minutes;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.*;
/** Strict in-process Nostr relay for tests: WebSocket upgrade, NIP-01 REQ/EVENT/CLOSE, id and BIP-340 checks, ephemeral fan-out. */
public final class LocalRelay implements Closeable {
 final ServerSocket server;final List<Conn> conns=new CopyOnWriteArrayList<>();final Random random=new Random(7);
 volatile int dropPercent,delayMillis;volatile int accepted,rejected,forwarded,refused;volatile boolean closed;
 /** When set, every valid event is answered OK=false with this message and not forwarded, like a public relay's policy. */
 volatile String refuse;
 /** Events whose content contains one of these strings are accepted but not forwarded, once per entry. */
 final List<String> dropOnce=new CopyOnWriteArrayList<>();
 final ScheduledExecutorService delays=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"relay-delay");t.setDaemon(true);return t;});
 public LocalRelay()throws IOException{server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));Thread t=new Thread(this::acceptLoop,"local-relay");t.setDaemon(true);t.start();}
 public String url(){return "ws://127.0.0.1:"+server.getLocalPort()+"/";}
 /** Standalone relay for cross-platform tests (e.g. the iOS simulator): prints its URL, optionally writes it to a file, runs until killed. */
 public static void main(String[] args)throws Exception{LocalRelay relay=new LocalRelay();System.out.println(relay.url());System.out.flush();
  if(args.length>0)java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),relay.url().getBytes(StandardCharsets.UTF_8));Thread.currentThread().join();}
 final class Conn {
  final Socket s;final InputStream in;final OutputStream out;final Map<String,List<Map<String,Object>>> subs=new ConcurrentHashMap<>();
  Conn(Socket s)throws IOException{this.s=s;in=new BufferedInputStream(s.getInputStream());out=new BufferedOutputStream(s.getOutputStream());}
  synchronized void send(String text){try{byte[] p=text.getBytes(StandardCharsets.UTF_8);out.write(0x81);if(p.length<126)out.write(p.length);else{out.write(126);out.write(p.length>>>8);out.write(p.length);}out.write(p);out.flush();}catch(IOException e){close();}}
  void close(){conns.remove(this);try{s.close();}catch(IOException ignored){}}
 }
 void acceptLoop(){while(!closed)try{Socket s=server.accept();Thread t=new Thread(()->serve(s),"relay-conn");t.setDaemon(true);t.start();}catch(IOException e){return;}}
 static String line(InputStream in)throws IOException{StringBuilder b=new StringBuilder();int c;while((c=in.read())!=-1&&c!='\n')if(c!='\r')b.append((char)c);if(c==-1)throw new EOFException();return b.toString();}
 void serve(Socket socket){Conn c=null;
  try{c=new Conn(socket);String request=line(c.in);if(!request.startsWith("GET "))throw new IOException("bad request");String key=null;
   for(String h=line(c.in);!h.isEmpty();h=line(c.in)){int i=h.indexOf(':');if(i>0&&h.substring(0,i).trim().equalsIgnoreCase("Sec-WebSocket-Key"))key=h.substring(i+1).trim();}
   String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
   java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);f.setTimeZone(TimeZone.getTimeZone("GMT"));
   c.out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\nDate: "+f.format(new Date())+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));c.out.flush();conns.add(c);
   ByteArrayOutputStream msg=new ByteArrayOutputStream();
   while(!closed){int b0=c.in.read(),b1=c.in.read();if(b0<0||b1<0)break;if((b1&128)==0)throw new IOException("client frames must be masked");long len=b1&127;
    if(len==126)len=(c.in.read()<<8)|c.in.read();else if(len==127){len=0;for(int i=0;i<8;i++)len=(len<<8)|c.in.read();}
    byte[] mask=new byte[4],p=new byte[(int)len];readFully(c.in,mask);readFully(c.in,p);for(int i=0;i<p.length;i++)p[i]^=mask[i&3];int op=b0&15;
    if(op==8)break;if(op==9){synchronized(c){c.out.write(0x8A);c.out.write(p.length);c.out.write(p);c.out.flush();}continue;}if(op==10)continue;
    msg.write(p);if((b0&128)!=0){handle(c,new String(msg.toByteArray(),StandardCharsets.UTF_8));msg.reset();}
   }
  }catch(Exception e){}finally{if(c!=null)c.close();else try{socket.close();}catch(IOException ignored){}}
 }
 static void readFully(InputStream in,byte[] b)throws IOException{int o=0;while(o<b.length){int r=in.read(b,o,b.length-o);if(r<0)throw new EOFException();o+=r;}}
 void handle(Conn c,String text){
  List<Object> m=NetJson.array(NetJson.parse(text));String type=(String)m.get(0);
  if(type.equals("REQ")){ArrayList<Map<String,Object>> filters=new ArrayList<>();for(Object f:m.subList(2,m.size()))filters.add(NetJson.object(f));c.subs.put((String)m.get(1),filters);c.send(NetJson.write(Arrays.asList("EOSE",m.get(1))));}
  else if(type.equals("CLOSE"))c.subs.remove((String)m.get(1));
  else if(type.equals("EVENT")){Map<String,Object> raw=NetJson.object(m.get(1));NostrRelayPool.Event e=NostrRelayPool.parseEvent(raw,"local");
   boolean ok=e!=null&&NostrRelayPool.verified(e,NetJson.string(raw,"sig"))&&Math.abs(e.createdAt-System.currentTimeMillis()/1000)<60;
   String refusal=refuse;if(ok&&refusal!=null){refused++;c.send(NetJson.write(Arrays.asList("OK",NetJson.string(raw,"id"),false,refusal)));return;}
   c.send(NetJson.write(Arrays.asList("OK",NetJson.string(raw,"id"),ok,ok?"":"invalid: id, signature or created_at")));if(!ok){rejected++;return;}accepted++;
   for(String d:dropOnce)if(e.content.contains(d)&&dropOnce.remove(d))return;
   String out=null;for(Conn other:conns)for(Map.Entry<String,List<Map<String,Object>>> sub:other.subs.entrySet())if(matches(sub.getValue(),e)){
    if(dropPercent>0&&random.nextInt(100)<dropPercent)continue;String frame=NetJson.write(Arrays.asList("EVENT",sub.getKey(),raw));forwarded++;
    if(delayMillis>0)delays.schedule(()->other.send(frame),delayMillis,TimeUnit.MILLISECONDS);else other.send(frame);}
  }
 }
 static boolean matches(List<Map<String,Object>> filters,NostrRelayPool.Event e){
  for(Map<String,Object> f:filters){boolean ok=true;
   for(Map.Entry<String,Object> x:f.entrySet()){String k=x.getKey();
    if(k.equals("kinds"))ok&=NetJson.array(x.getValue()).contains((long)e.kind);
    else if(k.equals("since"))ok&=e.createdAt>=((Number)x.getValue()).longValue();
    else if(k.startsWith("#")){String v=e.tag(k.substring(1));ok&=v!=null&&NetJson.array(x.getValue()).contains(v);}
   }
   if(ok)return true;}
  return false;
 }
 int connections(){return conns.size();}
 /** Drops every client connection (simulates a relay restart); clients must reconnect and resubscribe. */
 void dropConnections(){for(Conn c:conns)c.close();}
 @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(Conn c:conns)c.close();delays.shutdownNow();}
}
