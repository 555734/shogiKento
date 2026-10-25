package com.presentsoft.shogi3minutes;
import java.security.SecureRandom;import java.util.*;
/**
 * The online player: an account key held only on this device (its public half is the player ID), a display name,
 * the Elo rating with win/loss/draw counts, and the recent rated games. Random matches are rated; rooms are not.
 * A rated game that was still running when the app stopped counts as a loss the next time the account loads,
 * exactly as leaving the game does. Serialized as JSON without the secret, which the platform keystore protects.
 */
final class OnlineAccount {
 static final int HISTORY=200;
 static final class Game {
  String opponent,opponentName,reason;int opponentRating,before,after,result;long time;
  Map<String,Object> json(){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("o",opponent);m.put("on",opponentName);m.put("or",(long)opponentRating);m.put("b",(long)before);m.put("a",(long)after);m.put("r",(long)result);m.put("t",time);m.put("why",reason);return m;}
  static Game of(Map<String,Object> m){Game g=new Game();g.opponent=NetJson.string(m,"o");g.opponentName=NetJson.string(m,"on");g.opponentRating=(int)NetJson.number(m,"or",Rating.INITIAL);g.before=(int)NetJson.number(m,"b",Rating.INITIAL);
   g.after=(int)NetJson.number(m,"a",Rating.INITIAL);g.result=(int)NetJson.number(m,"r",0);g.time=NetJson.number(m,"t",0);g.reason=NetJson.string(m,"why");if(g.opponent==null||g.opponentName==null)return null;if(g.reason==null)g.reason="";return g;}
 }
 /** The rated game in progress, persisted so that quitting the app cannot avoid the result. */
 static final class Pending {String opponent,name;int rating,games,round;long started;}
 private final byte[] secret;final Schnorr.Key key;String name;int rating=Rating.INITIAL,games,wins,losses,draws;boolean publish=true;long updated;
 final ArrayList<Game> history=new ArrayList<>();Pending pending;
 OnlineAccount(byte[] secret,String name){this.secret=secret.clone();key=new Schnorr.Key(secret);this.name=OnlineIdentity.cleanName(name).isEmpty()?defaultName(key):OnlineIdentity.cleanName(name);}
 static byte[] newSecret(){return Schnorr.newSecret(new SecureRandom());}
 static String defaultName(Schnorr.Key key){return "棋士"+key.publicHex.substring(0,4).toUpperCase(Locale.US);}
 /** The secret as "nsec1…": whoever has it controls the account, so the app shows it only on request. */
 String transferCode(){return Nip19.encode("nsec",secret);}
 byte[] secret(){return secret.clone();}
 String npub(){return Nip19.encode("npub",key.publicKey);}
 /** A short, readable player ID: the first and last characters of the npub. */
 String shortId(){String n=npub();return n.substring(0,9)+"…"+n.substring(n.length()-4);}
 boolean provisional(){return games<Rating.PROVISIONAL_GAMES;}
 String ratingText(){return rating+(provisional()?"（暫定）":"");}
 boolean rename(String raw){String n=OnlineIdentity.cleanName(raw);if(n.isEmpty())return false;name=n;return true;}
 void begin(OnlineIdentity opponent,int round,long now){Pending p=new Pending();p.opponent=opponent.pubkey;p.name=opponent.name;p.rating=opponent.rating;p.games=opponent.games;p.round=round;p.started=now;pending=p;}
 /** Applies one result (1 win, 0.5 draw, 0 loss) against the opponent's pre-game rating and returns the new history entry. */
 Game record(String opponent,String opponentName,int opponentRating,double score,String reason,long now){
  Game g=new Game();g.opponent=opponent;g.opponentName=opponentName;g.opponentRating=opponentRating;g.before=rating;g.reason=reason==null?"":reason;g.time=now;
  g.after=Rating.next(rating,games,opponentRating,score);g.result=score>0.75?1:score<0.25?-1:0;
  rating=g.after;games++;if(g.result>0)wins++;else if(g.result<0)losses++;else draws++;history.add(g);while(history.size()>HISTORY)history.remove(0);pending=null;updated=now;return g;
 }
 Game finish(double score,String reason,long now){Pending p=pending;if(p==null)return null;return record(p.opponent,p.name,p.rating,score,reason,now);}
 /** A rated game left unfinished by a crash or a killed app is a loss, as on the opponent's device. */
 Game resolveAbandoned(long now){return pending==null?null:finish(0,"切断（対局を終了せずに退出）",now);}
 static final int UNCHANGED=0,BEGUN=1,SETTLED=2,ABORTED=3;
 /** The latest settled game, for the result screen. */
 Game lastSettled;
 /**
  * Call after every change of an online game. Starts a rated game once it has begun (persist on BEGUN), and
  * settles it once when it ends (SETTLED). A game decided before both players have moved is aborted, unrated.
  */
 int settle(boolean rated,OnlineIdentity opponent,OnlineGame game,long nowSeconds){
  if(!rated||opponent==null||game==null||opponent.pubkey.equals(key.publicHex))return UNCHANGED;
  boolean current=pending!=null&&pending.round==game.round&&pending.opponent.equals(opponent.pubkey);
  if(game.started&&!game.over()&&!current){begin(opponent,game.round,nowSeconds);return BEGUN;}
  // Each round starts with fresh cards; a rematch card carries the opponent's rating after the previous round.
  if(current&&!game.over()&&(pending.rating!=opponent.rating||pending.games!=opponent.games||!pending.name.equals(opponent.name))){pending.rating=opponent.rating;pending.games=opponent.games;pending.name=opponent.name;return BEGUN;}
  if(!game.over()||!current)return UNCHANGED;
  if(game.game.movesPlayed<2&&!game.game.result.equals("時間切れ")){pending=null;return ABORTED;}
  double score=game.game.winner==2?0.5:game.game.winner==game.mySide?1:0;lastSettled=finish(score,game.game.result,nowSeconds);return SETTLED;
 }
 int[] headToHead(String opponent){int[] r=new int[3];for(Game g:history)if(g.opponent.equals(opponent))r[g.result>0?0:g.result<0?1:2]++;return r;}
 String json(){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("v",1L);m.put("name",name);m.put("rating",(long)rating);m.put("games",(long)games);m.put("w",(long)wins);m.put("l",(long)losses);m.put("d",(long)draws);
  m.put("publish",publish);m.put("updated",updated);ArrayList<Object> h=new ArrayList<>();for(Game g:history)h.add(g.json());m.put("history",h);
  if(pending!=null){LinkedHashMap<String,Object> p=new LinkedHashMap<>();p.put("o",pending.opponent);p.put("on",pending.name);p.put("or",(long)pending.rating);p.put("og",(long)pending.games);p.put("round",(long)pending.round);p.put("t",pending.started);m.put("pending",p);}
  return NetJson.write(m);}
 static OnlineAccount load(byte[] secret,String json){
  Map<String,Object> m=json==null||json.isEmpty()?new HashMap<>():NetJson.object(NetJson.parse(json));OnlineAccount a=new OnlineAccount(secret,NetJson.string(m,"name"));
  int r=(int)NetJson.number(m,"rating",Rating.INITIAL);a.rating=Rating.valid(r)?r:Rating.INITIAL;a.games=Math.max(0,(int)NetJson.number(m,"games",0));a.wins=Math.max(0,(int)NetJson.number(m,"w",0));a.losses=Math.max(0,(int)NetJson.number(m,"l",0));a.draws=Math.max(0,(int)NetJson.number(m,"d",0));
  a.publish=!Boolean.FALSE.equals(m.get("publish"));a.updated=NetJson.number(m,"updated",0);
  Object h=m.get("history");if(h instanceof List)for(Object x:NetJson.array(h)){Game g=Game.of(NetJson.object(x));if(g!=null)a.history.add(g);}
  Object p=m.get("pending");if(p instanceof Map){Map<String,Object> pm=NetJson.object(p);String o=NetJson.string(pm,"o"),on=NetJson.string(pm,"on");
   if(o!=null&&on!=null){Pending x=new Pending();x.opponent=o;x.name=on;x.rating=(int)NetJson.number(pm,"or",Rating.INITIAL);x.games=(int)NetJson.number(pm,"og",0);x.round=(int)NetJson.number(pm,"round",1);x.started=NetJson.number(pm,"t",0);a.pending=x;}}
  return a;
 }
 /** Adopts a published profile after a transfer when it is newer than what this device has. */
 void adopt(Profiles.Profile p){if(p==null||p.updated<=updated)return;name=p.name;rating=p.rating;games=p.games;wins=p.wins;losses=p.losses;draws=p.draws;updated=p.updated;}
}
