package com.presentsoft.shogi3minutes;
import java.util.*;import java.util.concurrent.*;import javax.net.ssl.HostnameVerifier;
/**
 * Public player profiles on the same free Nostr relays: one replaceable application-data event (NIP-78, kind
 * 30078) per account, signed with the account key. It restores the rating after a transfer to a new device and
 * feeds the ranking. Values are computed by each player's own app, so the ranking is informative, not authoritative.
 */
final class Profiles {
 static final int KIND=30078;static final String TAG="shogikento-profile-v1";
 static final class Profile {String pubkey,name;int rating,games,wins,losses,draws;long updated;}
 private Profiles(){}
 static List<List<String>> tags(){return Arrays.asList(Arrays.asList("d",TAG),Arrays.asList("t",TAG));}
 static String content(OnlineAccount a){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("v",1L);m.put("name",a.name);m.put("rating",(long)a.rating);m.put("games",(long)a.games);
  m.put("w",(long)a.wins);m.put("l",(long)a.losses);m.put("d",(long)a.draws);m.put("updated",a.updated);return NetJson.write(m);}
 static Profile parse(NostrRelayPool.Event e){
  if(e.kind!=KIND||!TAG.equals(e.tag("d")))return null;
  try{Map<String,Object> m=NetJson.object(NetJson.parse(e.content));if(NetJson.number(m,"v",-1)!=1)return null;Profile p=new Profile();p.pubkey=e.pubkey;p.name=NetJson.string(m,"name");
   p.rating=(int)NetJson.number(m,"rating",-1);p.games=(int)NetJson.number(m,"games",-1);p.wins=(int)NetJson.number(m,"w",-1);p.losses=(int)NetJson.number(m,"l",-1);p.draws=(int)NetJson.number(m,"d",-1);p.updated=NetJson.number(m,"updated",0);
   if(p.name==null||p.name.isEmpty()||!p.name.equals(OnlineIdentity.cleanName(p.name))||!Rating.valid(p.rating)||p.games<0||p.wins<0||p.losses<0||p.draws<0||(long)p.wins+p.losses+p.draws!=p.games)return null;
   return p;}catch(RuntimeException x){return null;}
 }
 static Map<String,Object> authorFilter(String pubkey){LinkedHashMap<String,Object> f=new LinkedHashMap<>();f.put("kinds",Collections.singletonList((long)KIND));f.put("authors",Collections.singletonList(pubkey));f.put("#d",Collections.singletonList(TAG));return f;}
 static Map<String,Object> rankingFilter(int limit){LinkedHashMap<String,Object> f=new LinkedHashMap<>();f.put("kinds",Collections.singletonList((long)KIND));f.put("#t",Collections.singletonList(TAG));f.put("limit",(long)limit);return f;}
 /** Publishes the profile to every relay that opens within the timeout; returns how many relays confirmed it. */
 static int publish(OnlineAccount a,List<String> relays,HostnameVerifier verifier,long timeoutMillis)throws InterruptedException{
  NostrRelayPool pool=new NostrRelayPool(relays,a.key,verifier,e->{});
  try{pool.start();long end=System.currentTimeMillis()+timeoutMillis/2;while(pool.openCount()<relays.size()&&System.currentTimeMillis()<end)Thread.sleep(50);
   if(pool.openCount()==0)return 0;pool.publish(KIND,tags(),content(a));end=System.currentTimeMillis()+timeoutMillis/2;
   while(pool.acceptedCount()<pool.openCount()&&System.currentTimeMillis()<end)Thread.sleep(50);return pool.acceptedCount();}
  finally{pool.close();}
 }
 /** Collects the newest valid profile per account until every open relay has sent its stored events, or the timeout. */
 static Map<String,Profile> fetch(List<String> relays,HostnameVerifier verifier,Map<String,Object> filter,long timeoutMillis)throws InterruptedException{
  ConcurrentHashMap<String,Profile> found=new ConcurrentHashMap<>();Set<String> done=ConcurrentHashMap.newKeySet();
  NostrRelayPool pool=new NostrRelayPool(relays,new Schnorr.Key(Schnorr.newSecret(new java.security.SecureRandom())),verifier,new NostrRelayPool.Listener(){
   public void event(NostrRelayPool.Event e){Profile p=parse(e);if(p!=null)found.merge(p.pubkey,p,(x,y)->y.updated>x.updated?y:x);}
   public void endOfStored(String sub,String relay){done.add(relay);}});
  try{pool.subscribe("profiles",filter);pool.start();long start=System.currentTimeMillis(),end=start+timeoutMillis;
   while(System.currentTimeMillis()<end){int open=pool.openCount();if(open>0&&done.size()>=open&&System.currentTimeMillis()-start>800)break;Thread.sleep(50);}
   return new HashMap<>(found);}
  finally{pool.close();}
 }
 static List<Profile> ranking(Map<String,Profile> all,int minimumGames){ArrayList<Profile> r=new ArrayList<>();for(Profile p:all.values())if(p.games>=minimumGames)r.add(p);
  r.sort((a,b)->a.rating!=b.rating?b.rating-a.rating:a.games!=b.games?b.games-a.games:a.pubkey.compareTo(b.pubkey));return r;}
}
