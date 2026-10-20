package com.presentsoft.shogi3minutes;
import java.io.*;import java.net.*;import java.nio.ByteBuffer;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.*;
import javax.crypto.*;import javax.crypto.spec.*;
/**
 * Encrypted, ordered and reliable messages between two matched players. Messages take the direct UDP path
 * once a hole-punched candidate answers; until then, and whenever a packet goes unacknowledged, the same
 * AES-GCM packet also travels through the Nostr relays. Duplicates from both paths are discarded.
 */
final class P2PLink implements Closeable {
 interface Listener {void message(String text);void path(boolean direct,int rttMillis,String route);void peerClosed();}
 interface Relay {void send(byte[] packet);}
 static final int PROBE=1,PROBE_ACK=2,PING=3,PONG=4,CANDIDATES=5,DATA=16,ACK=17,BYE=18;
 static final int HEADER=20,MAX_TEXT=900;
 static final class Outgoing {final int seq;final byte[] body;final long first;long lastSent,lastRelay;int attempts;Outgoing(int seq,byte[] body,long now){this.seq=seq;this.body=body;first=now;}}
 final UdpEndpoint.PacketHandler handler=(data,n,from)->receive(data,n,from,false);
 final UdpEndpoint endpoint;final Relay relay;final Listener listener;final int role;final byte[] tag;final SecretKeySpec key;
 final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"p2p-timer");t.setDaemon(true);return t;});
 final ExecutorService events=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"p2p-events");t.setDaemon(true);return t;});
 final List<InetSocketAddress> remote=new ArrayList<>();final Map<InetSocketAddress,Integer> pathRtt=new HashMap<>();
 final TreeMap<Integer,Outgoing> unacked=new TreeMap<>();final TreeMap<Integer,String> early=new TreeMap<>();
 final LinkedHashSet<Long> seenCounters=new LinkedHashSet<>();long highestCounter=-1;
 long counter,startedAt,probeBurst,lastProbe,lastPing,lastRelayPing,lastDirectReceive,lastPeerReceive,lastCandidates;
 int nextSeq=1,expected=1,srtt=-1,relayRtt=-1,probeId;InetSocketAddress active;boolean direct,closed,announcedDirect;
 volatile int udpSent,udpReceived,relaySent,relayReceived,rejected;
 P2PLink(UdpEndpoint endpoint,byte[] secret,byte[] sessionId,int role,Relay relay,Listener listener){
  this.endpoint=endpoint;this.relay=relay;this.listener=listener;this.role=role;tag=Arrays.copyOf(sessionId,8);key=new SecretKeySpec(secret,"AES");
  startedAt=probeBurst=now();lastPeerReceive=startedAt;
  endpoint.handler=handler;
  timer.scheduleWithFixedDelay(this::tick,20,20,TimeUnit.MILLISECONDS);
 }
 static long now(){return System.nanoTime()/1000000;}
 // ---- packet encryption ----
 byte[] seal(int type,byte[] body){
  byte[] header=new byte[HEADER];header[0]='S';header[1]='K';header[2]=1;header[3]=(byte)role;System.arraycopy(tag,0,header,4,8);long c;synchronized(this){c=++counter;}
  ByteBuffer.wrap(header,12,8).putLong(c);byte[] plain=new byte[body.length+1];plain[0]=(byte)type;System.arraycopy(body,0,plain,1,body.length);
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce(role,c)));cipher.updateAAD(header);
   byte[] sealed=cipher.doFinal(plain),out=new byte[HEADER+sealed.length];System.arraycopy(header,0,out,0,HEADER);System.arraycopy(sealed,0,out,HEADER,sealed.length);return out;}
  catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 static byte[] nonce(int role,long c){byte[] n=new byte[12];n[3]=(byte)role;ByteBuffer.wrap(n,4,8).putLong(c);return n;}
 /** Returns type+body, or null for foreign, forged or replayed packets. */
 byte[] open(byte[] p,int n){
  if(n<HEADER+17||p[0]!='S'||p[1]!='K'||p[2]!=1||p[3]!=(byte)(1-role))return null;for(int i=0;i<8;i++)if(p[4+i]!=tag[i])return null;
  long c=ByteBuffer.wrap(p,12,8).getLong();synchronized(this){if(c<=highestCounter-4096||seenCounters.contains(c))return null;}
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce(1-role,c)));cipher.updateAAD(p,0,HEADER);
   byte[] plain=cipher.doFinal(p,HEADER,n-HEADER);if(plain.length<1)return null;
   synchronized(this){if(!seenCounters.add(c))return null;if(c>highestCounter)highestCounter=c;if(seenCounters.size()>4096){Iterator<Long> it=seenCounters.iterator();it.next();it.remove();}}
   return plain;}
  catch(GeneralSecurityException e){rejected++;return null;}
 }
 // ---- candidates and probing ----
 synchronized void addCandidates(Collection<String> candidates){
  boolean added=false;for(String c:candidates){InetSocketAddress a=UdpEndpoint.parse(c);if(a!=null&&!remote.contains(a)&&remote.size()<16){remote.add(a);added=true;}}
  if(added){probeBurst=now();lastProbe=0;}
 }
 static String encodeCandidates(List<String> list){StringBuilder b=new StringBuilder();for(String c:list){if(b.length()>0)b.append(',');b.append(c);}return b.toString();}
 static List<String> decodeCandidates(String s){ArrayList<String> out=new ArrayList<>();if(s==null||s.isEmpty())return out;for(String c:s.split(",",17))if(!c.isEmpty())out.add(c);return out.size()>16?out.subList(0,16):out;}
 void shareCandidates(){byte[] body=encodeCandidates(endpoint.candidates()).getBytes(StandardCharsets.UTF_8);sendRelay(seal(CANDIDATES,body));synchronized(this){lastCandidates=now();if(direct&&active!=null)udp(seal(CANDIDATES,body),active);}}
 void udp(byte[] packet,InetSocketAddress to){udpSent++;endpoint.send(packet,packet.length,to);}
 void sendRelay(byte[] packet){if(closed)return;relaySent++;try{relay.send(packet);}catch(RuntimeException ignored){}}
 static byte[] stamp(int id,long time){return ByteBuffer.allocate(12).putInt(id).putLong(time).array();}
 void tick(){
  try{List<Runnable> after=new ArrayList<>();
   synchronized(this){if(closed)return;long t=now();
    if(direct&&t-lastDirectReceive>3500){direct=false;active=null;probeBurst=t;after.add(this::announcePath);}
    long sinceBurst=t-probeBurst,interval=sinceBurst<2500?50:sinceBurst<12000?250:1000;
    if(t-lastProbe>=interval&&(!direct||t-startedAt<4000)){lastProbe=t;byte[] probe=seal(PROBE,stamp(++probeId,t));for(InetSocketAddress a:remote)udp(probe,a);}
    if(direct&&t-lastPing>=1000){lastPing=t;udp(seal(PING,stamp(0,t)),active);}
    if(!direct&&t-lastRelayPing>=5000){lastRelayPing=t;byte[] ping=seal(PING,stamp(1,t));after.add(()->sendRelay(ping));}
    if(t-lastCandidates>(t-startedAt<6000?2000:15000))after.add(this::shareCandidates);
    int rto=direct?Math.max(120,Math.min(800,(srtt<0?100:srtt)*2+40)):Math.max(1200,Math.min(4000,relayRtt<0?1500:relayRtt*2));
    for(Outgoing o:unacked.values())if(t-o.lastSent>=rto){o.lastSent=t;o.attempts++;byte[] packet=seal(DATA,o.body);if(direct)udp(packet,active);
     if(t-o.lastRelay>=1200){o.lastRelay=t;after.add(()->sendRelay(packet));}}
   }
   for(Runnable r:after)r.run();
  }catch(RuntimeException ignored){}
 }
 // ---- reliable messages ----
 void send(String text){byte[] utf=text.getBytes(StandardCharsets.UTF_8);if(utf.length>MAX_TEXT)throw new IllegalArgumentException("message too long");byte[] packet;boolean viaRelay;
  synchronized(this){if(closed)return;long t=now();int seq=nextSeq++;byte[] body=ByteBuffer.allocate(4+utf.length).putInt(seq).put(utf).array();Outgoing o=new Outgoing(seq,body,t);o.lastSent=t;unacked.put(seq,o);
   packet=seal(DATA,body);viaRelay=!direct;if(direct)udp(packet,active);if(viaRelay)o.lastRelay=t;}
  if(viaRelay)sendRelay(packet);
 }
 void receiveRelay(byte[] packet){receive(packet,packet.length,null,true);}
 void receive(byte[] p,int n,InetSocketAddress from,boolean viaRelay){
  byte[] plain=open(p,n);if(plain==null)return;int type=plain[0]&255;ByteBuffer body=ByteBuffer.wrap(plain,1,plain.length-1);
  List<Runnable> after=new ArrayList<>();
  synchronized(this){if(closed)return;long t=now();lastPeerReceive=t;if(viaRelay)relayReceived++;else{udpReceived++;
    if(!remote.contains(from)&&remote.size()<16)remote.add(from);
    if(direct&&from.equals(active))lastDirectReceive=t;
    else if(direct&&t-lastDirectReceive>1500&&type!=PROBE){active=from;lastDirectReceive=t;after.add(this::announcePath);}}
   switch(type){
    case PROBE:if(!viaRelay&&body.remaining()>=12){byte[] echo=new byte[12];body.get(echo);udp(seal(PROBE_ACK,echo),from);}break;
    case PROBE_ACK:if(!viaRelay&&body.remaining()>=12){body.getInt();int rtt=(int)Math.max(0,t-body.getLong());Integer old=pathRtt.get(from);pathRtt.put(from,old==null?rtt:Math.min(old,rtt));
     if(!direct||(!from.equals(active)&&rtt+3<(int)(pathRtt.getOrDefault(active,rtt)*0.7))){direct=true;active=from;lastDirectReceive=t;sample(rtt);after.add(this::announcePath);}}break;
    case PING:if(body.remaining()>=12){byte[] echo=new byte[12];body.get(echo);byte[] pong=seal(PONG,echo);if(viaRelay)after.add(()->sendRelay(pong));else udp(pong,from);}break;
    case PONG:if(body.remaining()>=12){body.getInt();int rtt=(int)Math.max(0,t-body.getLong());if(viaRelay)relayRtt=relayRtt<0?rtt:(relayRtt*3+rtt)/4;else{sample(rtt);}after.add(this::announcePath);}break;
    case CANDIDATES:{byte[] b=new byte[body.remaining()];body.get(b);List<String> list=decodeCandidates(new String(b,StandardCharsets.UTF_8));after.add(()->addCandidates(list));break;}
    case DATA:if(body.remaining()>=4){int seq=body.getInt();byte[] b=new byte[body.remaining()];body.get(b);
     if(seq>=expected&&seq<expected+1024&&!early.containsKey(seq))early.put(seq,new String(b,StandardCharsets.UTF_8));
     while(early.containsKey(expected)){String text=early.remove(expected);expected++;after.add(()->listener.message(text));}
     byte[] ack=seal(ACK,ByteBuffer.allocate(4).putInt(expected-1).array());if(viaRelay)after.add(()->sendRelay(ack));if(!viaRelay)udp(ack,from);else if(direct)udp(ack,active);}break;
    case ACK:if(body.remaining()>=4){int upTo=body.getInt();while(!unacked.isEmpty()&&unacked.firstKey()<=upTo){Outgoing o=unacked.pollFirstEntry().getValue();if(o.attempts==0&&o.lastRelay==0&&!viaRelay)sample((int)(t-o.first));else if(o.attempts==0&&o.lastRelay!=0&&viaRelay)relayRtt=relayRtt<0?(int)(t-o.first):(relayRtt*3+(int)(t-o.first))/4;}}break;
    case BYE:closed=true;after.add(listener::peerClosed);break;
    default:break;
   }
   // Enqueue while holding the lock: UDP and relay copies are processed on different threads, and
   // delivery order must equal the order in which sequence numbers were accepted.
   for(Runnable r:after)try{events.execute(r);}catch(RejectedExecutionException ignored){}
  }
 }
 private void sample(int rtt){srtt=srtt<0?rtt:(srtt*7+rtt)/8;}
 private void announcePath(){boolean d;int rtt;String route;synchronized(this){d=direct;rtt=d?srtt:relayRtt;route=d&&active!=null?UdpEndpoint.format(active.getAddress(),active.getPort()):"relay";}listener.path(d,rtt,route);}
 synchronized boolean isDirect(){return direct;}
 boolean heard(){return udpReceived+relayReceived>0;}
 synchronized int rtt(){return direct?srtt:relayRtt;}
 synchronized long silenceMillis(){return now()-lastPeerReceive;}
 synchronized int pending(){return unacked.size();}
 String stats(){synchronized(this){return "direct="+direct+" route="+(active==null?"relay":active)+" rtt="+srtt+" relayRtt="+relayRtt+" udp="+udpSent+"/"+udpReceived+" relay="+relaySent+"/"+relayReceived+" candidates="+remote.size()+" rejected="+rejected;}}
 @Override public void close(){byte[] bye;InetSocketAddress to;synchronized(this){if(closed){timer.shutdownNow();events.shutdown();return;}bye=seal(BYE,new byte[0]);to=direct?active:null;closed=true;}
  if(to!=null)for(int i=0;i<3;i++)udp(bye,to);relaySent++;try{relay.send(bye);}catch(RuntimeException ignored){}timer.shutdownNow();events.shutdown();if(endpoint.handler==handler)endpoint.handler=null;}
}
