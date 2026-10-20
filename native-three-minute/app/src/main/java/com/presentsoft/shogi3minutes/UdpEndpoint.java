package com.presentsoft.shogi3minutes;
import java.io.*;import java.net.*;import java.security.SecureRandom;import java.util.*;import java.util.concurrent.*;
/**
 * One dual-stack UDP socket per online session. It gathers host candidates (LAN IPv4, global IPv6) and
 * server-reflexive candidates from free public STUN servers, then hands encrypted game packets to a P2PLink.
 */
final class UdpEndpoint implements Closeable {
 static final List<String> DEFAULT_STUN=Collections.unmodifiableList(Arrays.asList("stun.l.google.com:19302","stun1.l.google.com:19302","stun.cloudflare.com:3478"));
 static volatile boolean allowLoopback;
 interface PacketHandler {void packet(byte[] data,int length,InetSocketAddress from);}
 interface CandidateListener {void candidates(List<String> all);}
 static final int COOKIE=0x2112A442;
 final DatagramSocket socket;final int port;final SecureRandom random=new SecureRandom();
 final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"udp-timer");t.setDaemon(true);return t;});
 private final Map<String,String> pendingStun=new ConcurrentHashMap<>();private final Set<String> reflexive=new CopyOnWriteArraySet<>();
 private final Map<String,Set<Integer>> mappedPorts=new ConcurrentHashMap<>();
 private final List<String> host=new CopyOnWriteArrayList<>();
 volatile PacketHandler handler;volatile CandidateListener candidateListener;volatile boolean closed;volatile int stunReplies;
 UdpEndpoint()throws SocketException{socket=open();port=socket.getLocalPort();
  Thread t=new Thread(this::receiveLoop,"udp-recv");t.setDaemon(true);t.start();}
 /** Binds the IPv6 wildcard so one socket reaches both IPv4 and IPv6 peers; falls back to IPv4-only systems. */
 static DatagramSocket open()throws SocketException{try{return new DatagramSocket(new InetSocketAddress(InetAddress.getByName("::"),0));}catch(IOException|RuntimeException e){return new DatagramSocket(new InetSocketAddress(0));}}
 static boolean usable(InetAddress a){return !a.isAnyLocalAddress()&&!a.isMulticastAddress()&&!a.isLinkLocalAddress()&&(allowLoopback||!a.isLoopbackAddress())&&!a.getHostAddress().equals("255.255.255.255");}
 static String format(InetAddress a,int port){String h=a.getHostAddress();int scope=h.indexOf('%');if(scope>=0)h=h.substring(0,scope);return a instanceof Inet6Address?"["+h+"]:"+port:h+":"+port;}
 /** Parses only literal addresses so a peer can never make us resolve names. */
 static InetSocketAddress parse(String candidate){
  if(candidate==null||candidate.length()>64)return null;String h;String p;
  if(candidate.startsWith("[")){int end=candidate.indexOf("]:");if(end<0)return null;h=candidate.substring(1,end);p=candidate.substring(end+2);if(!h.matches("[0-9a-fA-F:.]+"))return null;}
  else{int colon=candidate.lastIndexOf(':');if(colon<0)return null;h=candidate.substring(0,colon);p=candidate.substring(colon+1);if(!h.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return null;}
  try{int port=Integer.parseInt(p);if(port<1||port>65535)return null;InetAddress a=InetAddress.getByName(h);return usable(a)?new InetSocketAddress(a,port):null;}catch(NumberFormatException|UnknownHostException e){return null;}
 }
 List<String> hostCandidates(){ArrayList<String> out=new ArrayList<>();
  try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces())){if(!n.isUp())continue;
    for(InetAddress a:Collections.list(n.getInetAddresses())){if(!usable(a))continue;if(a instanceof Inet6Address&&a.isSiteLocalAddress())continue;String c=format(a,port);if(!out.contains(c))out.add(c);if(out.size()>=8)return out;}}}
  catch(SocketException|RuntimeException ignored){}
  return out;
 }
 /** Host candidates first (LAN and IPv6 work without STUN), then reflexive ones as STUN replies arrive. */
 List<String> candidates(){ArrayList<String> all=new ArrayList<>(host);for(String r:reflexive)if(!all.contains(r))all.add(r);return all;}
 /** True when STUN servers saw different public ports: an endpoint-dependent (symmetric) NAT that usually needs the relay. */
 boolean symmetricNat(){for(Set<Integer> ports:mappedPorts.values())if(ports.size()>1)return true;return false;}
 void gather(List<String> stunServers){host.clear();host.addAll(hostCandidates());announce();
  if(closed)return;timer.execute(()->{for(String server:stunServers){if(closed)return;int colon=server.lastIndexOf(':');
   try{for(InetAddress a:InetAddress.getAllByName(server.substring(0,colon))){InetSocketAddress target=new InetSocketAddress(a,Integer.parseInt(server.substring(colon+1)));
     for(long delay:new long[]{0,250,800,2000})timer.schedule(()->stun(target),delay,TimeUnit.MILLISECONDS);}}
   catch(IOException|RuntimeException ignored){}}});
 }
 void announce(){CandidateListener l=candidateListener;if(l!=null)l.candidates(candidates());}
 void stun(InetSocketAddress server){if(closed)return;byte[] p=new byte[20];p[1]=1;p[4]=0x21;p[5]=0x12;p[6]=(byte)0xA4;p[7]=0x42;byte[] tx=new byte[12];random.nextBytes(tx);System.arraycopy(tx,0,p,8,12);
  pendingStun.put(Schnorr.hex(tx),server.toString());send(p,p.length,server);}
 /** Outgoing loss for tests only; always 0 in the app. */
 volatile int dropPercent;
 void send(byte[] data,int length,InetSocketAddress to){if(closed||dropPercent>0&&random.nextInt(100)<dropPercent)return;try{socket.send(new DatagramPacket(data,length,to));}catch(IOException|RuntimeException ignored){}}
 /** Returns ip:port from a STUN Binding success response, or null. Accepts XOR-MAPPED-ADDRESS and legacy MAPPED-ADDRESS. */
 static String parseStun(byte[] p,int n,Map<String,String> pending){
  if(n<20||p[0]!=0x01||p[1]!=0x01||((p[4]&255)<<24|(p[5]&255)<<16|(p[6]&255)<<8|(p[7]&255))!=COOKIE)return null;
  String tx=Schnorr.hex(Arrays.copyOfRange(p,8,20));if(pending!=null&&pending.remove(tx)==null)return null;
  int len=Math.min(n-20,(p[2]&255)<<8|(p[3]&255)),i=20;String mapped=null;
  while(i+4<=20+len){int type=(p[i]&255)<<8|(p[i+1]&255),alen=(p[i+2]&255)<<8|(p[i+3]&255),v=i+4;if(v+alen>n)break;
   boolean xor=type==0x0020||type==0x8020;if((xor||type==0x0001)&&alen>=8){int family=p[v+1];int port=(p[v+2]&255)<<8|(p[v+3]&255);if(xor)port^=0x2112;
    byte[] addr=Arrays.copyOfRange(p,v+4,v+4+(family==2?16:4));if(family==2&&alen<20)break;
    if(xor)for(int k=0;k<addr.length;k++)addr[k]^=k<4?p[4+k]:p[8+k-4];
    try{InetAddress a=InetAddress.getByAddress(addr);String c=format(a,port);if(xor||mapped==null)mapped=c;}catch(UnknownHostException ignored){}}
   i=v+((alen+3)&~3);}
  return mapped;
 }
 private void receiveLoop(){byte[] buffer=new byte[2048];
  while(!closed){DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
   try{socket.receive(packet);}catch(IOException e){if(closed)return;continue;}
   int n=packet.getLength();InetSocketAddress from=(InetSocketAddress)packet.getSocketAddress();
   if(n>=20&&buffer[0]==0x01&&buffer[1]==0x01){String c=parseStun(buffer,n,pendingStun);if(c!=null){stunReplies++;InetSocketAddress m=parse(c);
     if(m!=null){mappedPorts.computeIfAbsent(m.getAddress().getHostAddress(),k->new CopyOnWriteArraySet<>()).add(m.getPort());if(reflexive.add(c))announce();}}continue;}
   PacketHandler h=handler;if(h!=null)try{h.packet(Arrays.copyOf(buffer,n),n,from);}catch(RuntimeException ignored){}
  }
 }
 @Override public void close(){closed=true;timer.shutdownNow();socket.close();}
}
