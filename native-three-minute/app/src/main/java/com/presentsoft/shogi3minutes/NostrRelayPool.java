package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.charset.StandardCharsets;import java.security.SecureRandom;import java.util.*;import java.util.concurrent.*;import javax.net.ssl.HostnameVerifier;
/**
 * Free signaling over several public Nostr relays at once. Events are ephemeral (kind 20000-29999), so relays
 * forward them to live subscribers without storing them. Every event is published to all open relays and
 * de-duplicated on receipt; incoming ids and BIP-340 signatures are verified before use.
 */
final class NostrRelayPool implements Closeable {
 static final int KIND=25150;
 static final List<String> DEFAULT_RELAYS=Collections.unmodifiableList(Arrays.asList(
  "wss://yabu.me","wss://relay.nostr.wirednet.jp","wss://relay.damus.io","wss://nos.lol",
  "wss://relay.primal.net","wss://nostr.mom","wss://offchain.pub","wss://relay.snort.social"));
 interface Listener {void event(Event e);default void relays(int open,int total){}}
 static final class Event {
  final String id,pubkey,content;final long createdAt;final int kind;final List<List<String>> tags;final String relay;
  Event(String id,String pubkey,long createdAt,int kind,List<List<String>> tags,String content,String relay){this.id=id;this.pubkey=pubkey;this.createdAt=createdAt;this.kind=kind;this.tags=tags;this.content=content;this.relay=relay;}
  String tag(String name){for(List<String> t:tags)if(t.size()>=2&&t.get(0).equals(name))return t.get(1);return null;}
 }
 final class Relay {
  final String url;volatile WebSocketClient socket;final ExecutorService sender=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"relay-send");t.setDaemon(true);return t;});
  int failures;volatile int accepted,rejected,received;volatile String lastProblem="";volatile long offsetSeconds;
  Relay(String url){this.url=url;}
  boolean open(){WebSocketClient s=socket;return s!=null&&s.isOpen();}
  void connect(){if(closed)return;try{sender.execute(()->{if(closed||open())return;
   try{WebSocketClient s=WebSocketClient.connect(url,8000,verifier,new WebSocketClient.Listener(){
     public void message(String text){incoming(Relay.this,text);}
     public void closed(String reason){lastProblem=reason;socket=null;notifyRelays();scheduleReconnect();}});
    offsetSeconds=s.serverClockOffsetSeconds;socket=s;failures=0;
    for(Map.Entry<String,Map<String,Object>> e:subscriptions.entrySet())s.send(NetJson.write(Arrays.asList("REQ",e.getKey(),e.getValue())));
    notifyRelays();
   }catch(IOException|RuntimeException e){lastProblem=String.valueOf(e.getMessage());socket=null;scheduleReconnect();}});}catch(RejectedExecutionException ignored){}}
  void scheduleReconnect(){if(closed)return;long delay=Math.min(30000,1000L<<Math.min(5,failures++));try{timer.schedule(this::connect,delay,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}}
  void send(String text){WebSocketClient s=socket;if(s==null||!s.isOpen())return;try{sender.execute(()->{try{s.send(text);}catch(IOException e){lastProblem=String.valueOf(e.getMessage());}});}catch(RejectedExecutionException ignored){}}
  void close(){WebSocketClient s=socket;socket=null;sender.shutdownNow();if(s!=null)new Thread(s::close,"relay-close").start();}
 }
 final Schnorr.Key key;final List<Relay> relays=new ArrayList<>();final Listener listener;final HostnameVerifier verifier;
 final Map<String,Map<String,Object>> subscriptions=new ConcurrentHashMap<>();
 final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"relay-timer");t.setDaemon(true);return t;});
 final ExecutorService dispatch=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"relay-dispatch");t.setDaemon(true);return t;});
 private final LinkedHashMap<String,Boolean> seen=new LinkedHashMap<String,Boolean>(256,.75f,false){protected boolean removeEldestEntry(Map.Entry<String,Boolean> e){return size()>4096;}};
 final SecureRandom random=new SecureRandom();volatile boolean closed;
 NostrRelayPool(List<String> urls,Schnorr.Key key,HostnameVerifier verifier,Listener listener){this.key=key;this.verifier=verifier;this.listener=listener;for(String u:urls)relays.add(new Relay(u));}
 void start(){for(Relay r:relays)r.connect();timer.scheduleWithFixedDelay(()->{for(Relay r:relays){WebSocketClient s=r.socket;if(s!=null)try{r.sender.execute(()->{try{s.ping();}catch(IOException ignored){}});}catch(RejectedExecutionException ignored){}}},25,25,TimeUnit.SECONDS);}
 int openCount(){int n=0;for(Relay r:relays)if(r.open())n++;return n;}
 void notifyRelays(){if(closed)return;int open=openCount(),total=relays.size();try{dispatch.execute(()->listener.relays(open,total));}catch(RejectedExecutionException ignored){}}
 void subscribe(String id,Map<String,Object> filter){subscriptions.put(id,filter);String text=NetJson.write(Arrays.asList("REQ",id,filter));for(Relay r:relays)r.send(text);}
 void unsubscribe(String id){if(subscriptions.remove(id)!=null){String text=NetJson.write(Arrays.asList("CLOSE",id));for(Relay r:relays)r.send(text);}}
 /** Device clocks may be wrong; strfry rejects ephemeral events older than about a minute. Use the relays' median clock. */
 long now(){ArrayList<Long> offsets=new ArrayList<>();for(Relay r:relays)if(r.open()&&r.offsetSeconds!=Long.MIN_VALUE)offsets.add(r.offsetSeconds);long local=System.currentTimeMillis()/1000;
  if(offsets.isEmpty())return local;Collections.sort(offsets);long median=offsets.get(offsets.size()/2);return Math.abs(median)>20?local+median:local;}
 static List<Object> serialized(String pubkey,long createdAt,int kind,List<List<String>> tags,String content){return Arrays.asList(0L,pubkey,createdAt,(long)kind,new ArrayList<Object>(tags),content);}
 static String eventId(String pubkey,long createdAt,int kind,List<List<String>> tags,String content){return Schnorr.hex(Schnorr.sha256(NetJson.write(serialized(pubkey,createdAt,kind,tags,content)).getBytes(StandardCharsets.UTF_8)));}
 Map<String,Object> sign(int kind,List<List<String>> tags,String content){
  long createdAt=now();String id=eventId(key.publicHex,createdAt,kind,tags,content);byte[] aux=new byte[32];random.nextBytes(aux);
  LinkedHashMap<String,Object> e=new LinkedHashMap<>();e.put("id",id);e.put("pubkey",key.publicHex);e.put("created_at",createdAt);e.put("kind",(long)kind);
  e.put("tags",new ArrayList<Object>(tags));e.put("content",content);e.put("sig",Schnorr.hex(key.sign(Schnorr.unhex(id),aux)));return e;
 }
 /** Publishes to every open relay and returns the number of relays it was handed to. */
 int publish(List<List<String>> tags,String content){Map<String,Object> e=sign(KIND,tags,content);synchronized(seen){seen.put((String)e.get("id"),true);}
  String text=NetJson.write(Arrays.asList("EVENT",e));int n=0;for(Relay r:relays)if(r.open()){r.send(text);n++;}return n;}
 static Event parseEvent(Object raw,String relay){
  Map<String,Object> m=NetJson.object(raw);String id=NetJson.string(m,"id"),pubkey=NetJson.string(m,"pubkey"),sig=NetJson.string(m,"sig"),content=NetJson.string(m,"content");
  if(id==null||pubkey==null||sig==null||content==null||id.length()!=64||pubkey.length()!=64||sig.length()!=128||content.length()>16384)return null;
  long created=NetJson.number(m,"created_at",-1),kind=NetJson.number(m,"kind",-1);if(created<0||kind<0||kind>65535)return null;
  ArrayList<List<String>> tags=new ArrayList<>();Object t=m.get("tags");if(!(t instanceof List))return null;
  for(Object row:NetJson.array(t)){if(!(row instanceof List))return null;ArrayList<String> tag=new ArrayList<>();for(Object x:NetJson.array(row)){if(!(x instanceof String))return null;tag.add((String)x);}tags.add(tag);if(tags.size()>32)return null;}
  if(!eventId(pubkey,created,(int)kind,tags,content).equals(id.toLowerCase(Locale.US)))return null;
  return new Event(id.toLowerCase(Locale.US),pubkey.toLowerCase(Locale.US),created,(int)kind,tags,content,relay);
 }
 static boolean verified(Event e,String sig){try{return Schnorr.verify(Schnorr.unhex(e.id),Schnorr.unhex(e.pubkey),Schnorr.unhex(sig));}catch(RuntimeException x){return false;}}
 void incoming(Relay relay,String text){
  try{List<Object> msg=NetJson.array(NetJson.parse(text));if(msg.isEmpty()||!(msg.get(0) instanceof String))return;String type=(String)msg.get(0);
   if(type.equals("EVENT")&&msg.size()>=3){Event e=parseEvent(msg.get(2),relay.url);if(e==null||e.pubkey.equals(key.publicHex))return;
    synchronized(seen){if(seen.containsKey(e.id))return;}
    if(!verified(e,NetJson.string(NetJson.object(msg.get(2)),"sig")))return;
    synchronized(seen){if(seen.put(e.id,true)!=null)return;}relay.received++;
    try{dispatch.execute(()->listener.event(e));}catch(RejectedExecutionException ignored){}
   }else if(type.equals("OK")&&msg.size()>=3){if(Boolean.TRUE.equals(msg.get(2)))relay.accepted++;else{relay.rejected++;relay.lastProblem=msg.size()>3?String.valueOf(msg.get(3)):"rejected";}}
   else if((type.equals("NOTICE")||type.equals("CLOSED"))&&msg.size()>=2)relay.lastProblem=String.valueOf(msg.get(msg.size()-1));
  }catch(RuntimeException ignored){}
 }
 String diagnostics(){StringBuilder b=new StringBuilder();for(Relay r:relays)b.append(r.url).append(r.open()?" open":" closed").append(" ok=").append(r.accepted).append(" rejected=").append(r.rejected).append(" received=").append(r.received).append(" clock=").append(r.offsetSeconds==Long.MIN_VALUE?"no Date header":r.offsetSeconds+"s").append(r.lastProblem.isEmpty()?"":" last="+r.lastProblem).append('\n');return b.toString();}
 @Override public void close(){closed=true;timer.shutdownNow();for(Relay r:relays)r.close();dispatch.shutdown();}
}
