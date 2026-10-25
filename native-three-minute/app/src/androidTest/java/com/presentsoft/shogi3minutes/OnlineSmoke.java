package com.presentsoft.shogi3minutes;
import android.graphics.*;import android.os.*;import java.io.*;import java.util.*;
/**
 * Online mode end-to-end inside the emulator: OS-routed touches drive the app against an in-process strict
 * Nostr relay and a second Netplay peer, over real device UDP sockets. No internet service is involved.
 */
final class OnlineSmoke {
 static final class Peer implements Netplay.Listener {
  final Netplay net;volatile OnlineGame game;volatile boolean direct,left;volatile int relays;
  /** A second player with its own account: sends its card each round and verifies the app's card. */
  final OnlineAccount account=new OnlineAccount(OnlineAccount.newSecret(),"テスト相手");volatile OnlineIdentity appCard;int cardRound;
  Peer(String relay)throws IOException{net=new Netplay(Collections.singletonList(relay),Collections.emptyList(),null,this);net.start();}
  static long now(){return SystemClock.elapsedRealtime();}
  public synchronized void matched(int side){appCard=null;cardRound=0;game=new OnlineGame(side,net::send);game.hello(now());card();}
  public synchronized void message(String t){if(t.startsWith("ID ")){appCard=OnlineIdentity.parse(t,net.sessionId());return;}if(game!=null){game.receive(t,now());card();}}
  synchronized void card(){byte[] sid=net.sessionId();if(game!=null&&sid!=null&&game.round!=cardRound){cardRound=game.round;net.send(OnlineIdentity.message(account.key,sid,account.name,account.rating,account.games,new java.security.SecureRandom()));}}
  public void path(boolean d,int rtt,String route){direct=d;}
  public void relays(int open,int total){relays=open;}
  public void peerLeft(){left=true;synchronized(this){if(game!=null)game.peerLeft();}}
  synchronized String sfen(){return game==null?"":UsiPosition.sfen(game.game);}
 }
 static void waitFor(NativeSmokeTest t,java.util.function.BooleanSupplier c,long ms,String what){long end=SystemClock.elapsedRealtime()+ms;boolean[] ok=new boolean[1];
  while(true){t.main(()->ok[0]=c.getAsBoolean());if(ok[0])break;if(SystemClock.elapsedRealtime()>end)break;SystemClock.sleep(25);}t.check(ok[0],"online: "+what);}
 static String appSfen(NativeSmokeTest t){String[] s=new String[1];t.main(()->s[0]=MainActivity.online.game==null?"":UsiPosition.sfen(MainActivity.online.game.game));return s[0];}
 /** A plain board move that never opens the promotion dialog, chosen from the app's own legal moves. */
 static ShogiGame.Move simpleMove(ShogiGame g,int side){List<ShogiGame.Move> all=g.legal(side);
  for(ShogiGame.Move m:all){if(m.drop!=0||m.promote)continue;boolean optional=false;for(ShogiGame.Move o:all)if(o.promote&&o.fromRow==m.fromRow&&o.fromCol==m.fromCol&&o.row==m.row&&o.col==m.col)optional=true;if(!optional)return m;}return null;}
 static void play(NativeSmokeTest t,Peer peer,int plies,List<Long> latency){
  for(int i=0;i<plies;i++){boolean[] mine=new boolean[1];t.main(()->mine[0]=MainActivity.online.game.myTurn());int before=peer.game.game.movesPlayed;
   if(mine[0]){ShogiGame.Move[] m=new ShogiGame.Move[1];t.main(()->m[0]=simpleMove(MainActivity.online.game.game,MainActivity.online.game.mySide));t.check(m[0]!=null,"legal simple move");
    long sent=SystemClock.elapsedRealtime();t.realSquareTap(m[0].fromRow,m[0].fromCol);t.realSquareTap(m[0].row,m[0].col);
    waitFor(t,()->peer.game.game.movesPlayed==before+1,15000,"peer received touch move "+i);latency.add(SystemClock.elapsedRealtime()-sent);}
   else{long sent;synchronized(peer){ShogiGame.Move m=simpleMove(peer.game.game,peer.game.mySide);t.check(m!=null,"peer move");sent=SystemClock.elapsedRealtime();t.check(peer.game.localMove(m,sent),"peer move accepted");}
    waitFor(t,()->MainActivity.online.game.game.movesPlayed==before+1,15000,"app received peer move "+i);latency.add(SystemClock.elapsedRealtime()-sent);}
   t.check(appSfen(t).equals(peer.sfen()),"boards identical after online ply "+i);
  }
 }
 static void orientation(NativeSmokeTest t){t.main(()->{int side=MainActivity.online.game.mySide;RectF a=t.v.cell(0,0),b=t.v.cell(8,8);
  t.check(side==1?a.top<b.top&&a.left<b.left:a.top>b.top&&a.left>b.left,"own side is drawn at the bottom for side "+side);t.check(t.v.viewSide()==side,"view side follows online side");});}
 static void run(NativeSmokeTest t)throws Exception{
  StringBuilder report=new StringBuilder();
  try(LocalRelay relay=new LocalRelay()){
   Netplay.relayOverride=Collections.singletonList(relay.url());Netplay.stunOverride=Collections.emptyList();UdpEndpoint.allowLoopback=true;Netplay.lobbyTag="android-instrumentation";
   t.tap(45,26);t.main(()->t.check(t.v.mode==MainActivity.HOME,"back to mode selection"));t.screenshot("50_mode_selection_online.png");
   float[] y=new float[1];t.main(()->y[0]=t.v.homeOnlineY()+26);t.tap(200,y[0]);t.main(()->t.check(t.v.mode==MainActivity.ONLINE,"online mode selected by touch"));
   waitFor(t,()->MainActivity.online.net!=null&&MainActivity.online.relaysOpen==1,15000,"app connected to relay");waitFor(t,()->!MainActivity.online.network.isEmpty(),5000,"UDP candidates summarized");
   t.screenshot("51_online_lobby.png");
   Peer peer=new Peer(relay.url());waitFor(t,()->peer.relays==1,10000,"peer relay");
   t.main(()->y[0]=t.v.lobbyButtonY(0)+26);t.tap(200,y[0]);t.main(()->t.check(MainActivity.online.seekMode==Netplay.QUICK,"random match started by touch"));peer.net.seek(Netplay.QUICK,null);
   waitFor(t,()->MainActivity.online.game!=null&&MainActivity.online.game.started&&peer.game!=null&&peer.game.started,20000,"quick match started");
   t.main(()->t.check(MainActivity.online.game.mySide==-peer.game.mySide,"opposite online sides"));orientation(t);
   waitFor(t,()->MainActivity.online.direct&&peer.direct,8000,"direct UDP path on device");
   int[] before=new int[2];t.main(()->{before[0]=MainActivity.online.account.rating;before[1]=MainActivity.online.account.games;});
   waitFor(t,()->MainActivity.online.peer!=null&&peer.appCard!=null,5000,"account cards exchanged");
   t.main(()->{t.check(MainActivity.online.rated&&MainActivity.online.peer.name.equals("テスト相手")&&MainActivity.online.peer.rating==1500,"app verified the peer's signed card");
    t.check(peer.appCard.pubkey.equals(MainActivity.online.account.key.publicHex)&&peer.appCard.rating==before[0],"peer verified the app's card");
    t.check(MainActivity.online.account.pending!=null,"rated game in progress is persisted");});
   int[] sounds=new int[1];t.main(()->sounds[0]=t.v.sounds.playCount);ArrayList<Long> latency=new ArrayList<>();play(t,peer,8,latency);
   t.main(()->{AppSettings s=t.activity.settings;t.check(!s.sound()||s.volume()==0||t.v.sounds.playCount>=sounds[0]+8,"local and remote online moves play the placement sound when enabled");});t.screenshot("52_online_game.png");
   t.main(()->y[0]=MainActivity.online.game.mySide);int firstSide=(int)y[0];
   t.tap(355,16);t.clickDialog("投了");waitFor(t,()->peer.game.over()&&MainActivity.online.game.over(),8000,"online resignation agreed");
   t.main(()->t.check(MainActivity.online.game.game.winner==-firstSide&&peer.game.game.winner==-firstSide,"resigning player loses on both devices"));
   t.main(()->{OnlineAccount a=MainActivity.online.account;t.check(a.games==before[1]+1&&a.losses>=1&&a.rating==Rating.next(before[0],before[1],1500,0)&&MainActivity.online.lastResult!=null&&a.pending==null,"rated loss recorded: "+a.rating);
    OnlineAccount stored=new AccountStore(t.activity).load();t.check(stored.rating==a.rating&&stored.key.publicHex.equals(a.key.publicHex),"account persisted through the keystore");});
   t.screenshot("53_online_result.png");
   t.tap(355,16);synchronized(peer){peer.game.requestRematch(Peer.now());}waitFor(t,()->MainActivity.online.game.round==2&&!MainActivity.online.game.over()&&peer.game.round==2,8000,"rematch by touch");
   t.main(()->t.check(MainActivity.online.game.mySide==-firstSide,"rematch swaps sides"));orientation(t);play(t,peer,4,latency);t.screenshot("54_online_rematch_flipped.png");
   t.tap(45,26);t.clickDialog("投了して退出");waitFor(t,()->MainActivity.online.game==null&&peer.game.over()&&peer.game.game.winner==peer.game.mySide,8000,"leaving resigns and returns to lobby");
   t.main(()->t.check(MainActivity.online.account.games==before[1]+2,"leaving a rated game counts as a loss"));
   t.tap(200,MainActivity.BoardView.ACCOUNT_CARD_Y+22);waitFor(t,()->t.activity.choiceDialog!=null&&t.activity.choiceDialog.isShowing(),5000,"account screen opened from the lobby card");
   SystemClock.sleep(500);t.screenshot("56_online_account.png");t.main(()->t.activity.choiceDialog.dismiss());
   report.append("quick match + rematch move latency (touch to peer / peer to app) ms: ").append(latency).append('\n');
   peer.net.close();
   Peer guest=new Peer(relay.url());waitFor(t,()->guest.relays==1,10000,"guest relay");
   t.main(()->y[0]=t.v.lobbyButtonY(1)+26);t.tap(200,y[0]);String[] code=new String[1];t.main(()->code[0]=MainActivity.online.roomCode);t.check(code[0]!=null&&code[0].matches("[0-9]{6}"),"room code shown");
   t.screenshot("55_online_room_code.png");guest.net.seek(Netplay.GUEST,code[0]);
   waitFor(t,()->MainActivity.online.game!=null&&MainActivity.online.game.started&&guest.game!=null&&guest.game.started,20000,"guest joined hosted room");
   ArrayList<Long> roomLatency=new ArrayList<>();play(t,guest,2,roomLatency);t.tap(45,26);t.clickDialog("投了して退出");waitFor(t,()->MainActivity.online.game==null,5000,"left room game");guest.net.close();
   Peer host=new Peer(relay.url());waitFor(t,()->host.relays==1,10000,"host relay");host.net.seek(Netplay.HOST,"424242");
   t.main(()->y[0]=t.v.lobbyButtonY(2)+26);t.tap(200,y[0]);waitFor(t,()->t.v.joinField!=null&&t.activity.choiceDialog!=null&&t.activity.choiceDialog.isShowing(),5000,"join dialog");
   t.main(()->t.v.joinField.setText("424242"));t.clickDialog("参加");
   waitFor(t,()->MainActivity.online.game!=null&&MainActivity.online.game.started&&host.game!=null&&host.game.started,20000,"joined friend's room by code");play(t,host,2,roomLatency);
   report.append("room games move latency ms: ").append(roomLatency).append('\n');
   t.tap(45,26);t.clickDialog("投了して退出");waitFor(t,()->host.game.over(),8000,"host sees guest leave");host.net.close();
   t.tap(45,26);waitFor(t,()->t.v.mode==MainActivity.HOME&&MainActivity.online.net==null,5000,"leaving online mode disconnects");
   t.check(relay.rejected==0,"every app event accepted by strict relay");report.append("relay accepted=").append(relay.accepted).append(" forwarded=").append(relay.forwarded).append(" rejected=").append(relay.rejected).append('\n');
  }finally{Netplay.relayOverride=null;Netplay.stunOverride=null;UdpEndpoint.allowLoopback=false;Netplay.lobbyTag="shogikento-v1-3m";}
  try(PrintWriter out=new PrintWriter(new File(t.getTargetContext().getExternalFilesDir(null),"online_play.txt"))){out.println("PASS native online mode: touch-driven quick match, direct UDP, sounds, resign, rematch with flipped board, hosted room, joined room, leave/disconnect");out.print(report);}
 }
}
