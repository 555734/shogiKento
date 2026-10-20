package com.presentsoft.shogi3minutes;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
/**
 * Online play without servers: Nostr signaling, BIP-340, STUN parsing, encrypted UDP link, reliable delivery
 * with relay fallback, matchmaking with many seekers and rooms, and complete games with lag-compensated clocks.
 * Default mode is deterministic (in-process relay, loopback UDP). "--live" uses real public relays and STUN.
 */
public final class NetplayTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static long now(){return System.nanoTime()/1000000;}
 static void waitFor(BooleanSupplier c,long ms,String what)throws InterruptedException{long end=now()+ms;while(!c.getAsBoolean()){if(now()>end)throw new AssertionError("timeout: "+what);Thread.sleep(1);}checks++;}
 static final class Client implements Netplay.Listener {
  final String name;final Netplay net;volatile OnlineGame game;volatile int side,rtt=-1,relaysOpen,matches;volatile boolean direct,left;volatile String failure,route="",network="";
  Client(String name,List<String> relays,List<String> stun)throws IOException{this.name=name;net=new Netplay(relays,stun,null,this);net.start();}
  public synchronized void matched(int s){side=s;matches++;game=new OnlineGame(s,net::send);game.hello(now());}
  public synchronized void message(String t){if(game!=null){game.rttMillis=rtt;game.receive(t,now());}}
  public void path(boolean d,int r,String route){direct=d;rtt=r;this.route=route;}
  public void relays(int open,int total){relaysOpen=open;}
  public void network(String text){network=text;}
  public void peerLeft(){left=true;synchronized(this){if(game!=null)game.peerLeft();}}
  public void failed(String r){failure=r;}
  synchronized boolean started(){return game!=null&&game.started;}
  synchronized String sfen(){return game==null?"":UsiPosition.sfen(game.game);}
  synchronized int plies(){return game==null?-1:game.game.movesPlayed;}
 }
 static void close(Client... cs){for(Client c:cs)c.net.close();}
 static boolean paired(Client a,Client b){return a.net.peer()!=null&&a.net.peer().equals(b.net.key.publicHex)&&b.net.peer()!=null&&b.net.peer().equals(a.net.key.publicHex);}
 /** Plays random legal moves alternately; returns per-move delivery latencies in ms. */
 static List<Long> play(Client a,Client b,int plies)throws InterruptedException{
  ArrayList<Long> latency=new ArrayList<>();
  for(int i=0;i<plies;i++){Client mover,other;synchronized(a){mover=a.game.myTurn()?a:b;}other=mover==a?b:a;if(a.game.over()||b.game.over())break;
   int before=other.plies();long sent;synchronized(mover){ok(mover.game.myTurn(),"turn alternates");ShogiGame.Move m=mover.game.game.cpuChoice();sent=now();ok(mover.game.localMove(m,sent),"local move accepted");}
   waitFor(()->other.plies()==before+1,20000,"peer receives move "+i);latency.add(now()-sent);ok(a.sfen().equals(b.sfen()),"boards identical after ply "+i);
  }
  return latency;
 }
 static String summary(List<Long> l){if(l.isEmpty())return "n/a";ArrayList<Long> s=new ArrayList<>(l);Collections.sort(s);return "median="+s.get(s.size()/2)+"ms max="+s.get(s.size()-1)+"ms n="+s.size();}
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--live")){live(args.length>1?args[1]:null);return;}
  json();schnorr(args.length>0?args[0]:"tests/bip340.csv");stun();candidates();clocks();
  UdpEndpoint.allowLoopback=true;link();
  try(LocalRelay relay=new LocalRelay()){List<String> relays=Collections.singletonList(relay.url());
   quickGame(relays);fastDiscovery(relays);relayOnly(relays);failover(relays);manySeekers(relays);rooms(relays);relayRestart(relay,relays);
   relay.dropPercent=30;lossyRelay(relays);relay.dropPercent=0;relay.delayMillis=150;slowRelay(relays);relay.delayMillis=0;
   ok(relay.rejected==0,"strict relay accepted every event id/signature: rejected="+relay.rejected);
  }
  refusals();
  System.out.println("PASS netplay: "+checks+" assertions");
 }
 static void json(){
  String content="line\nbreak \"quote\" back\\slash tab\t cr\r bs\b ff\f 日本語 \u0001 /slash";
  List<List<String>> tags=Arrays.asList(Arrays.asList("t","x"),Arrays.asList("p","b".repeat(64)));
  ok(NostrRelayPool.eventId("a".repeat(64),1700000000L,25150,tags,content).equals("e2b3f13fcefa49af0cb6456931333bfce0d9a3590ed75572950ddf1ad8394d03"),"NIP-01 id matches independent Python serialization");
  Object v=NetJson.parse("{\"a\":[1,-2.5,true,false,null,\"\\u65e5\\n\"],\"b\":{}}");ok(NetJson.write(v).equals("{\"a\":[1,-2.5,true,false,null,\"日\\n\"],\"b\":{}}"),"JSON round trip");
  for(String bad:new String[]{"{","[1,]","{\"a\" 1}","\"\\x\"","[1] 2","{\"a\":tru}"}){boolean rejected=false;try{NetJson.parse(bad);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"malformed JSON rejected: "+bad);}
  Random r=new Random(5);for(int n=0;n<40;n++){byte[] b=new byte[n];r.nextBytes(b);String e=NetJson.base64(b);ok(e.equals(java.util.Base64.getEncoder().encodeToString(b))&&Arrays.equals(NetJson.unbase64(e),b),"base64 length "+n);}
  for(String bad:new String[]{"abc","ab=c","a===","@@@@"}){boolean rejected=false;try{NetJson.unbase64(bad);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"bad base64 "+bad);}
  boolean deep=false;try{NetJson.parse("[".repeat(100)+"]".repeat(100));}catch(IllegalArgumentException e){deep=true;}ok(deep,"nesting depth bounded");
 }
 static void schnorr(String csv)throws IOException{
  // tests/bip340.csv: the official BIP-340 vectors from github.com/bitcoin/bips (bip-0340/test-vectors.csv, BSD-3-Clause).
  List<String> lines=Files.readAllLines(Paths.get(csv));int n=0;
  for(String l:lines.subList(1,lines.size())){String[] f=l.split(",",-1);byte[] pub=Schnorr.unhex(f[2]),msg=Schnorr.unhex(f[4]),sig=Schnorr.unhex(f[5]);
   if(!f[1].isEmpty()){byte[] sk=Schnorr.unhex(f[1]);ok(Arrays.equals(Schnorr.publicKey(sk),pub),"BIP-340 public key "+f[0]);ok(Arrays.equals(Schnorr.sign(msg,sk,Schnorr.unhex(f[3])),sig),"BIP-340 signature "+f[0]);}
   boolean got;try{got=Schnorr.verify(msg,pub,sig);}catch(RuntimeException e){got=false;}ok(got==f[6].equals("TRUE"),"BIP-340 verification "+f[0]+" "+f[7]);n++;}
  ok(n==19,"all BIP-340 vectors");
  Schnorr.Key k=new Schnorr.Key(Schnorr.newSecret(new java.security.SecureRandom()));NostrRelayPool pool=new NostrRelayPool(Collections.emptyList(),k,null,e->{});
  Map<String,Object> e=pool.sign(NostrRelayPool.KIND,Collections.singletonList(Arrays.asList("t","test")),"{\"op\":\"x\"}");
  NostrRelayPool.Event parsed=NostrRelayPool.parseEvent(NetJson.parse(NetJson.write(e)),"x");ok(parsed!=null&&NostrRelayPool.verified(parsed,(String)e.get("sig")),"signed event verifies after JSON transport");
  e.put("content","{\"op\":\"y\"}");ok(NostrRelayPool.parseEvent(NetJson.parse(NetJson.write(e)),"x")==null,"tampered content changes id and is rejected");pool.close();
 }
 static void stun()throws Exception{
  byte[] tx=Schnorr.unhex("b7e7a701bc34d686fa87dfae");
  byte[] v4=Schnorr.unhex("0101003c2112a442b7e7a701bc34d686fa87dfae"+"8022000b"+"7465737420766563746f7220"+"00200008"+"0001a147e112a643"+"00080014"+"2b91f599fd9e90c38c7489f92af9ba53f06be7d7"+"80280004"+"c07d4c96");
  Map<String,String> pending=new HashMap<>();pending.put(Schnorr.hex(tx),"server");ok("192.0.2.1:32853".equals(UdpEndpoint.parseStun(v4,v4.length,pending)),"RFC 5769 IPv4 XOR-MAPPED-ADDRESS");
  ok(UdpEndpoint.parseStun(v4,v4.length,pending)==null,"STUN reply only accepted for an outstanding transaction");
  byte[] v6=Schnorr.unhex("010100482112a442b7e7a701bc34d686fa87dfae"+"8022000b"+"7465737420766563746f7220"+"00200014"+"0002a1470113a9faa5d3f179bc25f4b5bed2b9d9"+"00080014"+"a382954e4be67bf11784c97c8292c275bfe3ed41"+"80280004"+"c8fb0b4c");
  ok("[2001:db8:1234:5678:11:2233:4455:6677]:32853".equals(UdpEndpoint.parseStun(v6,v6.length,null)),"RFC 5769 IPv6 XOR-MAPPED-ADDRESS");
  ok(UdpEndpoint.parseStun(Arrays.copyOf(v4,30),30,null)==null,"truncated STUN reply ignored");
 }
 static void candidates(){
  ok(UdpEndpoint.parse("203.0.113.5:4000")!=null&&UdpEndpoint.parse("[2001:db8::1]:4000")!=null,"IPv4 and IPv6 literals");
  for(String bad:new String[]{"example.com:4000","1.2.3.4:0","1.2.3.4:70000","[fe80::1]:5","224.0.0.1:5","0.0.0.0:5","255.255.255.255:9","[::]:5","1.2.3:5","[2001:db8::1%eth0]:5"})ok(UdpEndpoint.parse(bad)==null,"rejected candidate "+bad);
  ok(P2PLink.decodeCandidates(String.join(",",Collections.nCopies(40,"1.2.3.4:5"))).size()==16,"candidate list bounded");
 }
 /** Two OnlineGames exchanging messages through a virtual-time queue with fixed latency. */
 static final class Wire {long t;boolean cut;final long latency;final PriorityQueue<long[]> due=new PriorityQueue<>((x,y)->Long.compare(x[0],y[0]));final List<String> payloads=new ArrayList<>();OnlineGame a,b;
  Wire(long latency){this.latency=latency;}
  OnlineGame.Sender to(int target){return m->{if(cut)return;payloads.add(m);due.add(new long[]{t+latency,target,payloads.size()-1,due.size()});};}
  void run(long until){while(true){long[] next=due.peek();if(next==null||next[0]>until)break;due.poll();t=next[0];(next[1]==0?a:b).receive(payloads.get((int)next[2]),t);}t=until;a.tick(t);b.tick(t);}
 }
 static Wire pair(long latency){Wire w=new Wire(latency);w.a=new OnlineGame(1,w.to(1));w.b=new OnlineGame(-1,w.to(0));w.a.hello(0);w.b.hello(0);w.run(latency);return w;}
 static void clocks(){
  Wire w=pair(100);ok(w.a.started&&w.b.started,"both start after HELLO");ok(w.a.myTurn()&&!w.b.myTurn(),"sente moves first");
  w.t=5100;ok(w.a.localMove(UsiPosition.legalMove(w.a.game,"7g7f"),w.t),"sente move");ok(w.a.game.remaining[0]==180000-5000,"own clock measured locally");
  w.run(5200);ok(w.b.game.movesPlayed==1&&w.b.game.remaining[0]==175000,"honest mover is not charged network latency");
  ok(w.b.remaining(-1,5200)==180000&&w.a.remaining(-1,5200)==180000-100,"each side counts the opponent from its own turn start");
  w.run(8200);ok(w.b.localMove(UsiPosition.legalMove(w.b.game,"3c3d"),8200),"gote reply");w.run(8300);ok(w.a.game.remaining[1]==177000,"gote time agrees on both sides");
  // A modified client that never stops its own clock is capped at the latency allowance.
  w.run(38300);w.a.out.send("MOVE 1 3 2g2f 175000");w.a.game.apply(UsiPosition.legalMove(w.a.game,"2g2f"),1);w.run(38400);
  ok(w.b.game.remaining[0]==175000-(30200-OnlineGame.MIN_ALLOWANCE),"reported time cannot hide more than the allowance: "+w.b.game.remaining[0]);
  Wire f=pair(50);f.run(f.t+180000-1);ok(!f.a.over(),"no flag before zero");f.run(f.t+2);ok(f.a.over()&&f.a.game.winner==-1&&f.a.game.result.equals("時間切れ"),"own flag detected");
  f.run(f.t+100);ok(f.b.over()&&f.b.game.winner==-1,"opponent learns of the flag");
  Wire c=pair(50);c.cut=true;c.run(c.t+180000+OnlineGame.GRACE-10);ok(!c.b.over(),"claim waits for grace (in-flight move)");c.run(c.t+20);ok(c.b.over()&&c.b.game.winner==-1,"disconnected opponent loses on time after grace");
  Wire r=pair(30);r.a.receive("MOVE 1 1 3c3d 1000",r.t);ok(r.a.game.movesPlayed==0,"peer move during my turn ignored");r.b.receive("MOVE 9 1 7g7f 1000",r.t);ok(r.b.game.movesPlayed==0,"wrong round ignored");
  r.b.receive("MOVE 1 2 7g7f 1000",r.t);ok(r.b.game.movesPlayed==0,"wrong ply ignored");r.b.receive("garbage",r.t);r.b.receive("MOVE 1 x",r.t);ok(!r.b.over(),"malformed input ignored");
  r.b.receive("MOVE 1 1 5i5a 1000",r.t);ok(r.b.over()&&r.b.game.winner==-1&&r.b.error!=null,"illegal move from peer forfeits");
  Wire m=pair(30);m.a.resign();m.run(m.t+100);ok(m.a.over()&&m.b.over()&&m.a.game.winner==-1&&m.b.game.winner==-1,"resignation agreed");
  m.a.requestRematch(m.t);m.run(m.t+100);ok(!m.b.started||m.b.over(),"rematch needs both");m.b.requestRematch(m.t);m.run(m.t+100);
  ok(!m.a.over()&&!m.b.over()&&m.a.mySide==-1&&m.b.mySide==1&&m.a.round==2&&m.b.round==2,"rematch swaps sides");ok(m.b.myTurn()&&m.b.game.remaining[0]==180000,"new round fresh clocks");
  OnlineGame v=new OnlineGame(1,x->{});v.hello(0);v.receive("HELLO 99",0);ok(v.over()&&v.error!=null,"protocol mismatch reported");
 }
 static void link()throws Exception{
  try(UdpEndpoint ea=new UdpEndpoint();UdpEndpoint eb=new UdpEndpoint()){
   byte[] key=new byte[32],id=new byte[32];new Random(1).nextBytes(key);new Random(2).nextBytes(id);
   List<byte[]> captured=new CopyOnWriteArrayList<>();P2PLink[] links=new P2PLink[2];List<String> ra=new CopyOnWriteArrayList<>(),rb=new CopyOnWriteArrayList<>();
   P2PLink.Listener la=new P2PLink.Listener(){public void message(String t){ra.add(t);}public void path(boolean d,int r,String x){}public void peerClosed(){}};
   P2PLink.Listener lb=new P2PLink.Listener(){public void message(String t){rb.add(t);}public void path(boolean d,int r,String x){}public void peerClosed(){}};
   links[0]=new P2PLink(ea,key,id,0,p->{captured.add(p);links[1].receiveRelay(p);},la);links[1]=new P2PLink(eb,key,id,1,p->links[0].receiveRelay(p),lb);
   ea.gather(Collections.emptyList());eb.gather(Collections.emptyList());links[0].addCandidates(eb.candidates());links[1].addCandidates(ea.candidates());
   waitFor(()->links[0].isDirect()&&links[1].isDirect(),5000,"direct UDP path");
   ea.dropPercent=30;eb.dropPercent=30;for(int i=0;i<200;i++){links[0].send("a"+i);links[1].send("b"+i);}
   waitFor(()->ra.size()==200&&rb.size()==200,30000,"reliable delivery under 30% UDP loss");
   for(int i=0;i<200;i++){ok(rb.get(i).equals("a"+i)&&ra.get(i).equals("b"+i),"in order exactly once "+i);}
   ea.dropPercent=0;eb.dropPercent=0;
   byte[] old=captured.get(0);int before=rb.size();links[1].receiveRelay(old);byte[] forged=old.clone();forged[forged.length-1]^=1;links[1].receiveRelay(forged);byte[] fresh=old.clone();fresh[12]=0x7f;links[1].receiveRelay(fresh);
   P2PLink stranger=new P2PLink(new UdpEndpoint(),new byte[32],id,0,p->{},la);stranger.counter=1L<<40;byte[] other=stranger.seal(P2PLink.DATA,new byte[]{0,0,0,1,'x'});stranger.close();stranger.endpoint.close();links[1].receiveRelay(other);
   Thread.sleep(200);ok(rb.size()==before&&links[1].rejected==2,"replayed and tampered packets dropped; fresh-counter forgery and wrong key fail AES-GCM: "+links[1].rejected);
   ea.dropPercent=100;eb.dropPercent=100;waitFor(()->!links[0].isDirect()&&!links[1].isDirect(),8000,"dead UDP path detected");
   links[0].send("via relay");waitFor(()->rb.contains("via relay"),8000,"relay fallback delivers");
   ea.dropPercent=0;eb.dropPercent=0;waitFor(()->links[0].isDirect()&&links[1].isDirect(),15000,"direct path recovers");
   links[0].close();links[1].close();
  }
 }
 static Client[] matchTwo(List<String> relays,int mode,String code)throws Exception{
  Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());
  waitFor(()->a.relaysOpen==relays.size()&&b.relaysOpen==relays.size(),10000,"relays connected");
  if(mode==Netplay.QUICK){a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);}else{a.net.seek(Netplay.HOST,code);b.net.seek(Netplay.GUEST,code);}
  waitFor(()->a.started()&&b.started(),20000,"matched and started");ok(paired(a,b),"mutual pairing");ok(a.side==-b.side,"opposite sides");return new Client[]{a,b};
 }
 static void quickGame(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-quick";Client[] p=matchTwo(relays,Netplay.QUICK,null);Client a=p[0],b=p[1];
  waitFor(()->a.direct&&b.direct,5000,"direct UDP after match");List<Long> lat=play(a,b,40);
  System.out.println("direct loopback move latency "+summary(lat));ok(Collections.max(lat)<1000,"direct moves arrive quickly");
  Client sente=a.side==1?a:b,gote=sente==a?b:a;synchronized(sente){sente.game.resign();}waitFor(()->gote.game.over(),5000,"resignation delivered");
  ok(sente.game.game.winner==-1&&gote.game.game.winner==-1,"both agree gote won");
  synchronized(a){a.game.requestRematch(now());}synchronized(b){b.game.requestRematch(now());}waitFor(()->a.game.round==2&&b.game.round==2&&!a.game.over()&&!b.game.over(),5000,"rematch");
  ok(a.game.mySide==-a.side&&b.game.mySide==-b.side,"rematch swapped");play(a,b,6);
  a.net.close();waitFor(()->b.left&&b.game.over()&&b.game.game.winner==b.game.mySide,8000,"leaving player loses; peer notified");b.net.close();
 }
 /** A waiting seeker with the larger key answers a newcomer's first announcement at once, so matching is not held to the 3 s cycle. */
 static void fastDiscovery(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-discovery";Client x=new Client("X",relays,Collections.emptyList()),y=new Client("Y",relays,Collections.emptyList());
  Client early=x.net.key.publicHex.compareTo(y.net.key.publicHex)>0?x:y,late=early==x?y:x;waitFor(()->x.relaysOpen==1&&y.relaysOpen==1,10000,"relays");
  early.net.seek(Netplay.QUICK,null);Thread.sleep(1500);long t0=now();late.net.seek(Netplay.QUICK,null);waitFor(()->x.started()&&y.started(),10000,"discovery match");
  long took=now()-t0;System.out.println("newcomer matched with waiting larger-key seeker in "+took+"ms");ok(took<1500,"newcomer does not wait for the periodic announcement: "+took+"ms");close(x,y);
 }
 static void relayOnly(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-relay";Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());a.net.endpoint.dropPercent=100;b.net.endpoint.dropPercent=100;
  waitFor(()->a.relaysOpen==1&&b.relaysOpen==1,10000,"relays");a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->a.started()&&b.started(),20000,"relay-only match");
  List<Long> lat=play(a,b,12);ok(!a.direct&&!b.direct,"UDP blocked: relay only");System.out.println("relay-only move latency (local relay) "+summary(lat));close(a,b);
 }
 static void failover(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-failover";Client[] p=matchTwo(relays,Netplay.QUICK,null);Client a=p[0],b=p[1];waitFor(()->a.direct&&b.direct,5000,"direct");play(a,b,4);
  a.net.endpoint.dropPercent=100;b.net.endpoint.dropPercent=100;List<Long> lat=play(a,b,4);System.out.println("moves during UDP outage "+summary(lat));
  a.net.endpoint.dropPercent=0;b.net.endpoint.dropPercent=0;waitFor(()->a.direct&&b.direct,15000,"direct after outage");play(a,b,4);close(a,b);
 }
 static void manySeekers(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-many";ArrayList<Client> cs=new ArrayList<>();for(int i=0;i<5;i++)cs.add(new Client("C"+i,relays,Collections.emptyList()));
  waitFor(()->cs.stream().allMatch(c->c.relaysOpen==1),10000,"relays");for(Client c:cs)c.net.seek(Netplay.QUICK,null);
  waitFor(()->cs.stream().filter(Client::started).count()==4,25000,"two pairs from five seekers");Thread.sleep(1500);
  ok(cs.stream().filter(Client::started).count()==4&&cs.stream().allMatch(c->c.matches<=1),"nobody matched twice");
  for(Client c:cs)if(c.started())ok(cs.stream().filter(o->o!=c&&paired(c,o)).count()==1,"exactly one partner for "+c.name);
  Client waiting=cs.stream().filter(c->!c.started()).findFirst().get();Client late=new Client("late",relays,Collections.emptyList());waitFor(()->late.relaysOpen==1,10000,"relay");late.net.seek(Netplay.QUICK,null);
  waitFor(()->waiting.started()&&late.started(),20000,"waiting seeker matched with newcomer");ok(paired(waiting,late),"newcomer pairing");
  for(Client c:cs)c.net.close();late.net.close();
 }
 static void rooms(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-rooms";Client host=new Client("host",relays,Collections.emptyList()),wrong=new Client("wrong",relays,Collections.emptyList()),quick=new Client("quick",relays,Collections.emptyList());
  waitFor(()->host.relaysOpen==1&&wrong.relaysOpen==1&&quick.relaysOpen==1,10000,"relays");
  host.net.seek(Netplay.HOST,"123456");wrong.net.seek(Netplay.GUEST,"654321");quick.net.seek(Netplay.QUICK,null);Thread.sleep(4000);
  ok(!host.started()&&!wrong.started()&&!quick.started(),"wrong code and public lobby never join a room");
  Client guest=new Client("guest",relays,Collections.emptyList());waitFor(()->guest.relaysOpen==1,10000,"relay");guest.net.seek(Netplay.GUEST,"123456");
  waitFor(()->host.started()&&guest.started(),20000,"room code match");ok(paired(host,guest)&&!wrong.started()&&!quick.started(),"only the right guest");play(host,guest,6);
  close(host,wrong,quick,guest);
 }
 static void relayRestart(LocalRelay relay,List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-restart";Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());waitFor(()->a.relaysOpen==1&&b.relaysOpen==1,10000,"relays");
  relay.dropConnections();waitFor(()->a.relaysOpen==0||b.relaysOpen==0,5000,"disconnect noticed");waitFor(()->a.relaysOpen==1&&b.relaysOpen==1,15000,"reconnected");
  a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->a.started()&&b.started(),20000,"match after relay restart (resubscribed)");close(a,b);
 }
 static void lossyRelay(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-lossy";Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());waitFor(()->a.relaysOpen==1&&b.relaysOpen==1,10000,"relays");
  a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->a.started()&&b.started(),40000,"match despite 30% relay loss");ok(paired(a,b),"lossy pairing");play(a,b,6);close(a,b);
 }
 static void slowRelay(List<String> relays)throws Exception{
  Netplay.lobbyTag="test-lobby-slow";Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());a.net.endpoint.dropPercent=100;b.net.endpoint.dropPercent=100;
  waitFor(()->a.relaysOpen==1&&b.relaysOpen==1,10000,"relays");a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->a.started()&&b.started(),30000,"match with 150ms relay");
  List<Long> lat=play(a,b,6);System.out.println("relay-only with 150ms relay hop "+summary(lat));ok(Collections.max(lat)<3000,"slow relay still delivers each move promptly");close(a,b);
 }
 /** Relays that rate-limit or refuse by policy must stop receiving our writes while the others keep the game going. */
 static void refusals()throws Exception{
  ok(NostrRelayPool.refusalKind("banned: too many rate-limit violations, try again later")==1&&NostrRelayPool.refusalKind("rate-limited: slow down")==1,"rate limits pause writes");
  ok(NostrRelayPool.refusalKind("Policy violated and pubkey is not in our web of trust.")==2&&NostrRelayPool.refusalKind("auth-required: login first")==2&&NostrRelayPool.refusalKind("pow: difficulty 28 required")==2&&NostrRelayPool.refusalKind("blocked: kind not allowed")==2,"policy refusals stop writes");
  ok(NostrRelayPool.refusalKind("duplicate: already have this event")==0&&NostrRelayPool.refusalKind("invalid: created_at too early")==0,"other rejections keep writing");
  try(LocalRelay limited=new LocalRelay();LocalRelay trusted=new LocalRelay();LocalRelay good=new LocalRelay()){
   limited.refuse="rate-limited: slow down";trusted.refuse="blocked: pubkey is not in our web of trust";List<String> relays=Arrays.asList(limited.url(),trusted.url(),good.url());
   Netplay.lobbyTag="test-lobby-refusals";Client a=new Client("A",relays,Collections.emptyList()),b=new Client("B",relays,Collections.emptyList());a.net.endpoint.dropPercent=100;b.net.endpoint.dropPercent=100;
   waitFor(()->a.relaysOpen==3&&b.relaysOpen==3,10000,"three relays");a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);
   waitFor(()->a.started()&&b.started(),20000,"match through the one accepting relay");play(a,b,6);
   ok(a.net.pool.writableCount()==1&&b.net.pool.writableCount()==1,"rate-limited relay paused and policy relay refused: "+a.net.pool.diagnostics());
   int before=limited.refused+trusted.refused;play(a,b,6);ok(limited.refused+trusted.refused==before,"no further writes to refusing relays: "+(limited.refused+trusted.refused-before));
   close(a,b);
  }
 }
 static final List<String> SURVEY_EXTRA=Arrays.asList("wss://nostr.oxtr.dev","wss://nostr-pub.wellorder.net","wss://nostr.bitcoiner.social","wss://relay-jp.nostr.wirednet.jp","wss://r.kojira.io",
  "wss://nostr.holybea.com","wss://relay.nostr.band","wss://nostr.fmt.wiz.biz","wss://relay.nostrich.de","wss://nostr.einundzwanzig.space","wss://relay.nostr.wirednet.jp","wss://offchain.pub","wss://nostr.wine");
 /** One relay at a time: does it accept our ephemeral events and forward them to a #p subscriber, and how fast? */
 static String survey(String url){
  java.security.SecureRandom r=new java.security.SecureRandom();Schnorr.Key ka=new Schnorr.Key(Schnorr.newSecret(r)),kb=new Schnorr.Key(Schnorr.newSecret(r));List<Long> latency=new CopyOnWriteArrayList<>();
  NostrRelayPool b=new NostrRelayPool(Collections.singletonList(url),kb,null,e->{try{latency.add(System.currentTimeMillis()-NetJson.number(NetJson.object(NetJson.parse(e.content)),"t",0));}catch(RuntimeException ignored){}});
  NostrRelayPool a=new NostrRelayPool(Collections.singletonList(url),ka,null,e->{});
  try{b.subscribe("s",Netplay.filter("#p",kb.publicHex));b.start();a.start();long end=now()+10000;while((a.openCount()<1||b.openCount()<1)&&now()<end)Thread.sleep(20);
   if(a.openCount()<1||b.openCount()<1)return String.format(Locale.US,"%-36s CONNECT FAILED %s",url,a.relays.get(0).lastProblem);Thread.sleep(700);
   for(int i=0;i<3;i++){a.publish(Collections.singletonList(Arrays.asList("p",kb.publicHex)),"{\"v\":0,\"op\":\"survey\",\"t\":"+System.currentTimeMillis()+"}");Thread.sleep(400);}
   end=now()+4000;while(latency.size()<3&&now()<end)Thread.sleep(20);NostrRelayPool.Relay x=a.relays.get(0);ArrayList<Long> sorted=new ArrayList<>(latency);Collections.sort(sorted);
   return String.format(Locale.US,"%-36s ok=%d rejected=%d delivered=%d/3 median=%s %s",url,x.accepted,x.rejected,latency.size(),sorted.isEmpty()?"-":sorted.get(sorted.size()/2)+"ms",x.lastProblem);
  }catch(InterruptedException e){return url+" interrupted";}finally{a.close();b.close();}
 }
 /** CI check against real public relays and STUN servers in an isolated lobby. Prints diagnostics either way. */
 static void live(String report)throws Exception{
  Netplay.lobbyTag="shogikento-ci-"+Long.toHexString(new java.security.SecureRandom().nextLong());StringBuilder out=new StringBuilder();Client a=null,b=null;
  ArrayList<String> surveyed=new ArrayList<>(NostrRelayPool.DEFAULT_RELAYS);for(String u:SURVEY_EXTRA)if(!surveyed.contains(u))surveyed.add(u);
  ExecutorService pool=Executors.newFixedThreadPool(8);List<Future<String>> rows=new ArrayList<>();for(String u:surveyed)rows.add(pool.submit(()->survey(u)));
  out.append("relay survey (default relays first):\n");for(Future<String> f:rows)out.append("SURVEY ").append(f.get()).append('\n');pool.shutdown();System.out.print(out);
  try{a=new Client("A",NostrRelayPool.DEFAULT_RELAYS,UdpEndpoint.DEFAULT_STUN);b=new Client("B",NostrRelayPool.DEFAULT_RELAYS,UdpEndpoint.DEFAULT_STUN);Client x=a,y=b;
   waitFor(()->x.relaysOpen>=2&&y.relaysOpen>=2,30000,"at least two public relays");Thread.sleep(3000);
   out.append("relays open A=").append(a.relaysOpen).append(" B=").append(b.relaysOpen).append(" of ").append(NostrRelayPool.DEFAULT_RELAYS.size()).append('\n');
   out.append("STUN replies A=").append(a.net.endpoint.stunReplies).append(" candidates=").append(a.net.endpoint.candidates()).append(" symmetric=").append(a.net.endpoint.symmetricNat()).append('\n');
   ok(a.net.endpoint.stunReplies>0,"public STUN reachable");
   long t0=now();a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->x.started()&&y.started(),60000,"live match");out.append("match time ").append(now()-t0).append("ms\n");
   waitFor(()->x.direct&&y.direct,15000,"live direct path");out.append("direct path ").append(a.route).append(" rtt=").append(a.rtt).append('\n');
   out.append("direct move latency ").append(summary(play(a,b,10))).append('\n');a.net.close();b.net.close();
   a=new Client("A2",NostrRelayPool.DEFAULT_RELAYS,UdpEndpoint.DEFAULT_STUN);b=new Client("B2",NostrRelayPool.DEFAULT_RELAYS,UdpEndpoint.DEFAULT_STUN);Client p=a,q=b;a.net.endpoint.dropPercent=100;b.net.endpoint.dropPercent=100;
   waitFor(()->p.relaysOpen>=2&&q.relaysOpen>=2,30000,"relays again");t0=now();a.net.seek(Netplay.QUICK,null);b.net.seek(Netplay.QUICK,null);waitFor(()->p.started()&&q.started(),60000,"live relay-only match");
   out.append("relay-only match time ").append(now()-t0).append("ms\n").append("relay-only move latency ").append(summary(play(a,b,8))).append('\n');
   out.append("PASS live public relays/STUN: ").append(checks).append(" assertions\n");
  }finally{if(a!=null){out.append("diagnostics A:\n").append(a.net.diagnostics());a.net.close();}if(b!=null)b.net.close();System.out.print(out);if(report!=null)Files.write(Paths.get(report),out.toString().getBytes(StandardCharsets.UTF_8));}
 }
}
