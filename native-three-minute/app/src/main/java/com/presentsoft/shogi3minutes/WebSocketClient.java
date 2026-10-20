package com.presentsoft.shogi3minutes;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import javax.net.ssl.*;
/** RFC 6455 text-message client over TLS (wss) or plain TCP (ws, local tests only). One reader thread per connection. */
final class WebSocketClient implements Closeable {
 interface Listener {void message(String text);void closed(String reason);}
 static final int MAX_MESSAGE=256*1024;
 final URI uri;private final Socket socket;private final InputStream in;private final OutputStream out;private final Listener listener;
 private final SecureRandom random=new SecureRandom();private volatile boolean closed;private final java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
 /** Server Date header minus the local clock, or Long.MIN_VALUE when the relay sent none. */
 volatile long serverClockOffsetSeconds=Long.MIN_VALUE;
 private WebSocketClient(URI uri,Socket socket,Listener listener)throws IOException{this.uri=uri;this.socket=socket;this.listener=listener;in=new BufferedInputStream(socket.getInputStream());out=new BufferedOutputStream(socket.getOutputStream());}
 /** Connects and completes the HTTP upgrade before returning. TLS verifies the certificate chain and hostname. */
 static WebSocketClient connect(String url,int timeoutMillis,HostnameVerifier verifier,Listener listener)throws IOException{
  URI uri=URI.create(url);boolean tls="wss".equals(uri.getScheme());if(!tls&&!"ws".equals(uri.getScheme()))throw new IOException("Unsupported scheme "+url);
  String host=uri.getHost();int port=uri.getPort()>0?uri.getPort():tls?443:80;Socket raw=new Socket();
  try{raw.connect(new InetSocketAddress(host,port),timeoutMillis);raw.setTcpNoDelay(true);raw.setSoTimeout(timeoutMillis);Socket s=raw;
   if(tls){SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,host,port,true);
    SSLParameters params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(params);ssl.startHandshake();
    if(verifier!=null&&!verifier.verify(host,ssl.getSession()))throw new SSLPeerUnverifiedException("Hostname mismatch: "+host);s=ssl;}
   WebSocketClient c=new WebSocketClient(uri,s,listener);c.handshake(host,port,tls);s.setSoTimeout(0);
   Thread t=new Thread(c::readLoop,"ws-"+host);t.setDaemon(true);t.start();return c;
  }catch(IOException|RuntimeException e){try{raw.close();}catch(IOException ignored){}throw e;}
 }
 private void handshake(String host,int port,boolean tls)throws IOException{
  byte[] nonce=new byte[16];random.nextBytes(nonce);String key=NetJson.base64(nonce);
  String path=uri.getRawPath()==null||uri.getRawPath().isEmpty()?"/":uri.getRawPath();if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
  String hostHeader=(port==(tls?443:80))?host:host+":"+port;
  out.write(("GET "+path+" HTTP/1.1\r\nHost: "+hostHeader+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\nUser-Agent: ShogiKento/1.9.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
  String status=line();if(!status.startsWith("HTTP/1.1 101"))throw new IOException("WebSocket upgrade refused: "+status);
  String accept=null;for(String h=line();!h.isEmpty();h=line()){int colon=h.indexOf(':');if(colon<=0)continue;String name=h.substring(0,colon).trim(),value=h.substring(colon+1).trim();
   if(name.equalsIgnoreCase("Sec-WebSocket-Accept"))accept=value;else if(name.equalsIgnoreCase("Date"))serverClockOffsetSeconds=dateOffset(value);}
  String expected;try{expected=NetJson.base64(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException e){throw new IOException(e);}
  if(!expected.equals(accept))throw new IOException("Bad Sec-WebSocket-Accept");
 }
 static long dateOffset(String value){try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);f.setTimeZone(TimeZone.getTimeZone("GMT"));return f.parse(value).getTime()/1000-System.currentTimeMillis()/1000;}catch(java.text.ParseException|RuntimeException e){return Long.MIN_VALUE;}}
 private String line()throws IOException{StringBuilder b=new StringBuilder();int c;while((c=in.read())!=-1){if(c=='\n')break;if(c!='\r')b.append((char)c);if(b.length()>8192)throw new IOException("Header too long");}if(c==-1)throw new EOFException("Connection closed during upgrade");return b.toString();}
 boolean isOpen(){return !closed;}
 void send(String text)throws IOException{frame(0x1,text.getBytes(StandardCharsets.UTF_8));}
 void ping()throws IOException{frame(0x9,new byte[0]);}
 private synchronized void frame(int opcode,byte[] payload)throws IOException{
  if(closed)throw new IOException("WebSocket closed");byte[] mask=new byte[4];random.nextBytes(mask);int n=payload.length;
  ByteArrayOutputStream f=new ByteArrayOutputStream(n+14);f.write(0x80|opcode);
  if(n<126)f.write(0x80|n);else if(n<65536){f.write(0x80|126);f.write(n>>>8);f.write(n);}else{f.write(0x80|127);for(int i=7;i>=0;i--)f.write(i>3?0:(n>>>(8*i)));}
  f.write(mask);for(int i=0;i<n;i++)f.write(payload[i]^mask[i&3]);
  try{out.write(f.toByteArray());out.flush();}catch(IOException e){shutdown("write: "+e.getMessage());throw e;}
 }
 private void readFully(byte[] b)throws IOException{int off=0;while(off<b.length){int r=in.read(b,off,b.length-off);if(r<0)throw new EOFException("Relay closed connection");off+=r;}}
 private void readLoop(){
  ByteArrayOutputStream message=new ByteArrayOutputStream();String reason="closed";int kind=0x1;
  try{while(!closed){
    int b0=in.read();if(b0<0)throw new EOFException("Relay closed connection");int b1=in.read();if(b1<0)throw new EOFException("Relay closed connection");
    int opcode=b0&15;long len=b1&127;
    if(len==126){byte[] e=new byte[2];readFully(e);len=((e[0]&255)<<8)|(e[1]&255);}else if(len==127){byte[] e=new byte[8];readFully(e);len=0;for(byte x:e)len=(len<<8)|(x&255);}
    if(len<0||len>MAX_MESSAGE||message.size()+len>MAX_MESSAGE)throw new IOException("Frame too large");
    byte[] mask=null;if((b1&128)!=0){mask=new byte[4];readFully(mask);}
    byte[] payload=new byte[(int)len];readFully(payload);if(mask!=null)for(int i=0;i<payload.length;i++)payload[i]^=mask[i&3];
    if(opcode==0x8){reason="closed by relay";try{frame(0x8,new byte[0]);}catch(IOException ignored){}break;}
    if(opcode==0x9){frame(0xA,payload);continue;}if(opcode==0xA)continue;
    if(opcode==0x1||opcode==0x0||opcode==0x2){if(opcode!=0x0)kind=opcode;message.write(payload);if((b0&0x80)!=0){byte[] all=message.toByteArray();message.reset();if(kind==0x1)listener.message(new String(all,StandardCharsets.UTF_8));}}
    else throw new IOException("Unknown opcode "+opcode);
   }}catch(IOException|RuntimeException e){reason=e.getMessage()==null?e.toString():e.getMessage();}
  shutdown(reason);
 }
 private void shutdown(String reason){closed=true;try{socket.close();}catch(IOException ignored){}if(finished.compareAndSet(false,true))listener.closed(reason);}
 @Override public void close(){if(!closed){try{frame(0x8,new byte[0]);}catch(IOException ignored){}}shutdown("closed locally");}
}
