package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.security.spec.*;import java.util.*;import java.util.concurrent.*;
import javax.crypto.*;import javax.crypto.spec.*;import javax.net.ssl.HostnameVerifier;
/**
 * Serverless matchmaking. Seekers announce themselves on public Nostr relays; pairing is a three-way
 * offer/accept/confirm handshake where the lower relay key always offers, so every seeker ends in at most
 * one match. Offer and accept carry ephemeral P-256 ECDH keys and encrypted UDP candidates, letting both
 * sides start hole punching immediately. All state lives on one executor thread.
 */
final class Netplay implements Closeable {
 static final int QUICK=0,HOST=1,GUEST=2,VERSION=1;
 static final int IDLE=0,SEEKING=1,OFFERED=2,ACCEPTED=3,MATCHED=4;
 /** The public 3-minute lobby. Tests use a private tag so they never meet real players. */
 static volatile String lobbyTag="shogikento-v1-3m";
 /** Instrumentation tests point the app at an in-process relay without internet STUN. */
 static volatile List<String> relayOverride,stunOverride;
 static List<String> relays(){List<String> r=relayOverride;return r!=null?r:NostrRelayPool.DEFAULT_RELAYS;}
 static List<String> stunServers(){List<String> s=stunOverride;return s!=null?s:UdpEndpoint.DEFAULT_STUN;}
 interface Listener {
  default void status(String text){}default void relays(int open,int total){}default void network(String text){}
  void matched(int localSide);void message(String text);default void path(boolean direct,int rtt,String route){}
  default void peerLeft(){}default void failed(String reason){}
 }
 static final class Seek {String nonce,ecdh,role;long since,lastSeen;}
 static final class Session {
  String peer,peerNonce;int role,confirms;byte[] key,id;P2PLink link;boolean live;long deadline,lastConfirm;List<String> peerCandidates=new ArrayList<>();
  int localSide(){return ((id[31]&1)==0)==(role==0)?1:-1;}
 }
 final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"netplay");t.setDaemon(true);return t;});
 final SecureRandom random=new SecureRandom();final Schnorr.Key key;final NostrRelayPool pool;final UdpEndpoint endpoint;final KeyPair ecdh;final String ecdhText;
 final List<String> stun;volatile Listener listener;
 int mode=QUICK,state=IDLE,seeks;String roomTag,nonce,target,targetNonce;long deadline,seekStarted,lastSeek,lastReply;volatile Session session;Session pending;
 final Map<String,Seek> seen=new HashMap<>();final Map<String,Long> blocked=new HashMap<>();
 volatile int relaysOpen,relaysTotal;volatile String lastStatus="";boolean closed;
 Netplay(List<String> relays,List<String> stun,HostnameVerifier verifier,Listener listener)throws IOException{
  this.stun=stun;this.listener=listener;key=new Schnorr.Key(Schnorr.newSecret(random));
  try{KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"),random);ecdh=g.generateKeyPair();}catch(GeneralSecurityException e){throw new IOException(e);}
  ecdhText=NetJson.base64(ecdh.getPublic().getEncoded());endpoint=new UdpEndpoint();
  pool=new NostrRelayPool(relays,key,verifier,new NostrRelayPool.Listener(){
   public void event(NostrRelayPool.Event e){run(()->incoming(e));}
   public void relays(int open,int total){relaysOpen=open;relaysTotal=total;Listener l=Netplay.this.listener;if(l!=null)l.relays(open,total);}});
  endpoint.candidateListener=all->run(()->{Session s=session!=null?session:pending;if(s!=null&&s.link!=null)s.link.shareCandidates();networkSummary();});
 }
 /** May race with close() from another thread; a closed instance simply does not start. */
 synchronized void start(){if(closed)return;try{pool.subscribe("direct",filter("#p",key.publicHex));pool.start();endpoint.gather(stun);loop.scheduleWithFixedDelay(()->{try{tick();}catch(RuntimeException e){fail("内部エラー: "+e);}},500,500,TimeUnit.MILLISECONDS);run(this::networkSummary);}catch(RejectedExecutionException ignored){}}
 void run(Runnable r){try{loop.execute(()->{try{r.run();}catch(RuntimeException e){fail("内部エラー: "+e);}});}catch(RejectedExecutionException ignored){}}
 static Map<String,Object> filter(String tag,String value){LinkedHashMap<String,Object> f=new LinkedHashMap<>();f.put("kinds",Collections.singletonList((long)NostrRelayPool.KIND));f.put(tag,Collections.singletonList(value));f.put("since",System.currentTimeMillis()/1000-30);return f;}
 static String roomTag(String code){return "shogikento-v1-room-"+Schnorr.hex(Schnorr.sha256(("shogikento-room:"+code).getBytes(StandardCharsets.UTF_8))).substring(0,24);}
 static String newRoomCode(SecureRandom r){return String.format(Locale.US,"%06d",r.nextInt(1000000));}
 // ---- public controls (any thread) ----
 void seek(int mode,String code){run(()->{closeSession();this.mode=mode;roomTag=mode==QUICK?lobbyTag:roomTag(code);nonce=randomHex(16);state=SEEKING;seekStarted=System.currentTimeMillis();lastSeek=0;seeks=0;seen.clear();blocked.clear();
  pool.subscribe("lobby",filter("#t",roomTag));tick();});}
 void stopSeeking(){run(()->{state=IDLE;pool.unsubscribe("lobby");closeSession();status("");});}
 /** Ordered with all other session work and never on the caller's (UI) thread. */
 void send(String text){run(()->{Session s=session;if(s!=null&&s.link!=null)s.link.send(text);});}
 String peer(){Session s=session;return s==null?null:s.peer;}
 P2PLink link(){Session s=session;return s==null?null:s.link;}
 String randomHex(int bytes){byte[] b=new byte[bytes];random.nextBytes(b);return Schnorr.hex(b);}
 // ---- matchmaking ----
 void publish(String peer,Map<String,Object> content){List<List<String>> tags=new ArrayList<>();tags.add(Arrays.asList(peer==null?"t":"p",peer==null?roomTag:peer));content.put("v",(long)VERSION);pool.publish(tags,NetJson.write(content));}
 Map<String,Object> message(String op){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("op",op);m.put("n",nonce);return m;}
 void tick(){
  long now=System.currentTimeMillis();seen.values().removeIf(s->now-s.lastSeen>9000);blocked.values().removeIf(until->until<now);
  // The second announcement covers peers whose lobby subscription reached the relays just after our first one.
  if(state==SEEKING){if(now-lastSeek>=(seeks==1?700:3000))announce(now);
   offerIfPossible(now);status((mode==QUICK?"対戦相手を探しています":mode==HOST?"相手の参加を待っています":"ルームに接続しています")+"（"+(now-seekStarted)/1000+"秒）");}
  else if((state==OFFERED||state==ACCEPTED)&&now>deadline){if(target!=null)blocked.put(target,now+10000);closePending();state=SEEKING;}
  else if(state==MATCHED&&session!=null&&!session.live){Session s=session;if(s.link!=null&&s.link.heard())s.live=true;
   else if(now>s.deadline){closeSession();state=SEEKING;fail("相手の応答がありません。もう一度探します");}
   else if(s.role==0&&s.confirms<4&&now-s.lastConfirm>=800){s.confirms++;s.lastConfirm=now;publish(s.peer,confirm(s));}}
 }
 void announce(long now){lastSeek=now;seeks++;Map<String,Object> m=message("seek");m.put("e",ecdhText);m.put("w",(seekStarted/1000));m.put("r",mode==HOST?"host":mode==GUEST?"guest":"");m.put("tc","3m");publish(null,m);}
 Map<String,Object> confirm(Session s){Map<String,Object> m=message("confirm");m.put("to",s.peerNonce);return m;}
 boolean mayOffer(Seek s,String pubkey){return mode==QUICK?pubkey.compareTo(key.publicHex)>0&&s.role.isEmpty():mode==HOST&&s.role.equals("guest");}
 void offerIfPossible(long now){
  String best=null;Seek pick=null;for(Map.Entry<String,Seek> e:seen.entrySet()){Seek s=e.getValue();if(blocked.containsKey(e.getKey())||!mayOffer(s,e.getKey()))continue;
   if(pick==null||s.since<pick.since||s.since==pick.since&&e.getKey().compareTo(best)<0){pick=s;best=e.getKey();}}
  if(pick==null)return;Session s=derive(best,pick.nonce,pick.ecdh,0);if(s==null){blocked.put(best,now+60000);return;}
  target=best;targetNonce=pick.nonce;deadline=now+4000;state=OFFERED;closePending();pending=s;
  Map<String,Object> m=message("offer");m.put("to",pick.nonce);m.put("e",ecdhText);m.put("c",seal(s.key,P2PLink.encodeCandidates(endpoint.candidates())));publish(best,m);
 }
 void incoming(NostrRelayPool.Event e){
  Map<String,Object> m;try{m=NetJson.object(NetJson.parse(e.content));}catch(RuntimeException x){return;}
  if(NetJson.number(m,"v",-1)!=VERSION)return;String op=NetJson.string(m,"op"),n=NetJson.string(m,"n"),to=NetJson.string(m,"to");if(op==null||n==null||n.length()>64)return;
  if(!op.equals("seek")&&!key.publicHex.equals(e.tag("p")))return;
  long now=System.currentTimeMillis();
  switch(op){
   case "seek":{if(state==IDLE||roomTag==null||!roomTag.equals(e.tag("t"))||!"3m".equals(NetJson.string(m,"tc")))return;Seek s=seen.get(e.pubkey);boolean fresh=s==null||!s.nonce.equals(n);
    if(fresh){s=new Seek();s.nonce=n;s.ecdh=NetJson.string(m,"e");s.since=NetJson.number(m,"w",now/1000);String r=NetJson.string(m,"r");s.role=r==null?"":r;}
    s.lastSeen=now;if(s.ecdh!=null)seen.put(e.pubkey,s);if(state!=SEEKING)return;offerIfPossible(now);
    // A newcomer that must offer to us learns about us now instead of at our next periodic announcement.
    if(fresh&&state==SEEKING&&!mayOffer(s,e.pubkey)&&now-lastReply>=1000){lastReply=now;announce(now);}return;}
   case "offer":{if(!nonce.equals(to))return;boolean allowed=mode==QUICK?e.pubkey.compareTo(key.publicHex)<0:mode==GUEST;
    if(!allowed||(state!=SEEKING&&state!=OFFERED)){Map<String,Object> d=message("decline");d.put("to",n);publish(e.pubkey,d);return;}
    Session s=derive(e.pubkey,n,NetJson.string(m,"e"),1);if(s==null)return;List<String> cand=open(s.key,NetJson.string(m,"c"));if(cand==null)return;
    closePending();s.peerCandidates=cand;pending=s;target=e.pubkey;targetNonce=n;deadline=now+5000;state=ACCEPTED;startLink(s);
    Map<String,Object> a=message("accept");a.put("to",n);a.put("c",seal(s.key,P2PLink.encodeCandidates(endpoint.candidates())));a.put("e",ecdhText);publish(e.pubkey,a);status("対戦相手が見つかりました・接続中…");return;}
   case "accept":{if(!nonce.equals(to))return;if(state!=OFFERED||!e.pubkey.equals(target)||!n.equals(targetNonce)||pending==null){Map<String,Object> d=message("decline");d.put("to",n);publish(e.pubkey,d);return;}
    List<String> cand=open(pending.key,NetJson.string(m,"c"));if(cand==null)return;Session s=pending;pending=null;s.peerCandidates=cand;matched(s,now);s.confirms=1;s.lastConfirm=now;publish(s.peer,confirm(s));return;}
   case "confirm":{if(!nonce.equals(to))return;if(state==ACCEPTED&&e.pubkey.equals(target)&&n.equals(targetNonce)&&pending!=null){Session s=pending;pending=null;matched(s,now);}return;}
   case "decline":{if(!nonce.equals(to))return;if((state==OFFERED||state==ACCEPTED)&&e.pubkey.equals(target)){blocked.put(target,now+10000);closePending();state=SEEKING;}
    else if(state==MATCHED&&session!=null&&!session.live&&e.pubkey.equals(session.peer)){closeSession();state=SEEKING;}return;}
   case "pkt":{Session s=session!=null&&e.pubkey.equals(session.peer)?session:pending!=null&&e.pubkey.equals(pending.peer)?pending:null;if(s==null||s.link==null)return;
    String sid=NetJson.string(m,"s"),data=NetJson.string(m,"d");if(sid==null||data==null||!sid.equals(Schnorr.hex(Arrays.copyOf(s.id,8))))return;
    byte[] packet;try{packet=NetJson.unbase64(data);}catch(IllegalArgumentException x){return;}
    if(s==pending&&state==ACCEPTED){pending=null;matched(s,now);}s.link.receiveRelay(packet);return;}
   default:
  }
 }
 void matched(Session s,long now){state=MATCHED;session=s;s.deadline=now+10000;pool.unsubscribe("lobby");if(s.link==null)startLink(s);
  status("接続しました");Listener l=listener;int side=s.localSide();if(l!=null)l.matched(side);}
 void startLink(Session s){
  s.link=new P2PLink(endpoint,s.key,s.id,s.role,packet->{Map<String,Object> m=message("pkt");m.put("s",Schnorr.hex(Arrays.copyOf(s.id,8)));m.put("d",NetJson.base64(packet));publish(s.peer,m);},new P2PLink.Listener(){
   public void message(String text){run(()->{if(session==s){s.live=true;Listener l=listener;if(l!=null)l.message(text);}});}
   public void path(boolean direct,int rtt,String route){run(()->{if(session==s){s.live=s.live||direct;Listener l=listener;if(l!=null)l.path(direct,rtt,route);}});}
   public void peerClosed(){run(()->{if(session==s){Listener l=listener;if(l!=null)l.peerLeft();}});}});
  s.link.addCandidates(s.peerCandidates);
 }
 /** Session secret = HKDF(ECDH, salt=id). The id binds both nonces and both public keys in offer order. */
 Session derive(String peer,String peerNonce,String peerEcdh,int role){
  if(peerEcdh==null||peerEcdh.length()>256)return null;
  try{PublicKey theirs=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(NetJson.unbase64(peerEcdh)));
   KeyAgreement agreement=KeyAgreement.getInstance("ECDH");agreement.init(ecdh.getPrivate());agreement.doPhase(theirs,true);byte[] shared=agreement.generateSecret();
   String first=role==0?nonce+ecdhText:peerNonce+peerEcdh,second=role==0?peerNonce+peerEcdh:nonce+ecdhText;
   Session s=new Session();s.peer=peer;s.peerNonce=peerNonce;s.role=role;s.id=Schnorr.sha256(("shogikento-session-v1|"+first+"|"+second).getBytes(StandardCharsets.UTF_8));
   s.key=hkdf(shared,s.id,"shogikento p2p v1");return s;
  }catch(GeneralSecurityException|IllegalArgumentException e){return null;}
 }
 static byte[] hkdf(byte[] ikm,byte[] salt,String info)throws GeneralSecurityException{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(salt,"HmacSHA256"));byte[] prk=mac.doFinal(ikm);
  mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update(info.getBytes(StandardCharsets.UTF_8));mac.update((byte)1);return mac.doFinal();}
 String seal(byte[] key,String text){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD("candidates".getBytes(StandardCharsets.UTF_8));
  byte[] ct=c.doFinal(text.getBytes(StandardCharsets.UTF_8)),out=new byte[12+ct.length];System.arraycopy(iv,0,out,0,12);System.arraycopy(ct,0,out,12,ct.length);return NetJson.base64(out);}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 static List<String> open(byte[] key,String sealed){if(sealed==null||sealed.length()>4096)return null;
  try{byte[] in=NetJson.unbase64(sealed);if(in.length<28)return null;Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,in,0,12));c.updateAAD("candidates".getBytes(StandardCharsets.UTF_8));
   return P2PLink.decodeCandidates(new String(c.doFinal(in,12,in.length-12),StandardCharsets.UTF_8));}catch(GeneralSecurityException|IllegalArgumentException e){return null;}}
 void closePending(){if(pending!=null&&pending.link!=null)pending.link.close();pending=null;}
 void closeSession(){closePending();if(session!=null&&session.link!=null)session.link.close();session=null;}
 void status(String text){if(text.equals(lastStatus))return;lastStatus=text;Listener l=listener;if(l!=null)l.status(text);}
 void fail(String reason){Listener l=listener;if(l!=null)l.failed(reason);}
 void networkSummary(){List<String> c=endpoint.candidates();int v6=0,v4=0;for(String x:c)if(x.startsWith("["))v6++;else v4++;
  String text="UDP候補 IPv4×"+v4+" / IPv6×"+v6+(endpoint.stunReplies>0?(endpoint.symmetricNat()?"・NAT: 対称型（リレー経由の可能性）":"・NAT: 直結可能型"):"・STUN応答待ち");Listener l=listener;if(l!=null)l.network(text);}
 String diagnostics(){Session s=session;return "state="+state+" relays="+relaysOpen+"/"+relaysTotal+" candidates="+endpoint.candidates()+" symmetric="+endpoint.symmetricNat()+(s!=null&&s.link!=null?" link: "+s.link.stats():"")+"\n"+pool.diagnostics();}
 @Override public void close(){synchronized(this){closed=true;}Future<?> f=null;try{f=loop.submit(this::closeSession);}catch(RejectedExecutionException ignored){}
  if(f!=null)try{f.get(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException|ExecutionException|TimeoutException ignored){}
  loop.shutdownNow();try{Thread.sleep(150);}catch(InterruptedException ignored){}pool.close();endpoint.close();}
}
