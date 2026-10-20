package com.presentsoft.shogi3minutes;
import java.util.*;
/**
 * Two-player 3-minute sudden death over an unreliable network, without a server. Both peers run the same
 * Java rules, so every received move must be legal here too. Each player's own clock is measured locally;
 * a mover reports its remaining time, and the receiver accepts it only within a latency allowance, so
 * neither side is charged for network delay and a modified client cannot stop its own clock.
 */
final class OnlineGame {
 interface Sender {void send(String message);}
 static final int PROTOCOL=1;static final long TIME=180000,MIN_ALLOWANCE=2000,GRACE=4000;
 final ShogiGame game=new ShogiGame();final Sender out;
 int mySide,round=1;boolean helloSent,peerHello,started,rematchMine,rematchPeer,peerLeft;
 long turnStart,turnStartRemaining=TIME;int rttMillis=-1;String error;ShogiGame.Move lastMove;
 OnlineGame(int mySide,Sender out){this.mySide=mySide;this.out=out;game.restart();}
 static int slot(int side){return side==1?0:1;}
 boolean over(){return game.winner!=0;}
 boolean myTurn(){return started&&!over()&&game.turn==mySide;}
 long allowance(){return Math.max(MIN_ALLOWANCE,rttMillis<0?0:2L*rttMillis+500);}
 void hello(long now){if(!helloSent){helloSent=true;out.send("HELLO "+PROTOCOL);}maybeStart(now);}
 void maybeStart(long now){if(helloSent&&peerHello&&!started){started=true;turnStart=now;turnStartRemaining=game.remaining[slot(game.turn)];}}
 /** Remaining time as currently displayed; the side to move counts down from its turn start. May be negative for the opponent before a claim. */
 long remaining(int side,long now){if(!started||over()||side!=game.turn)return game.remaining[slot(side)];return turnStartRemaining-(now-turnStart);}
 void finish(int winner,String result){if(over())return;game.winner=winner;game.result=result;}
 /** Detects my own flag immediately and the opponent's only after GRACE, so their in-flight move can still arrive. */
 void tick(long now){
  if(!started||over())return;long left=remaining(game.turn,now);
  if(game.turn==mySide&&left<=0){game.remaining[slot(mySide)]=0;finish(-mySide,"時間切れ");out.send("FLAG "+round);}
  else if(game.turn!=mySide&&left<=-Math.max(GRACE,allowance())){game.remaining[slot(-mySide)]=0;finish(mySide,"時間切れ");out.send("CLAIM "+round+" "+game.movesPlayed);}
 }
 boolean localMove(ShogiGame.Move m,long now){
  tick(now);if(!myTurn()||UsiPosition.legalMove(game,UsiPosition.move(m))==null)return false;long left=turnStartRemaining-(now-turnStart);if(left<=0)return false;
  game.apply(m,mySide);game.remaining[slot(mySide)]=left;lastMove=m;out.send("MOVE "+round+" "+game.movesPlayed+" "+UsiPosition.move(m)+" "+left);
  turnStart=now;turnStartRemaining=game.remaining[slot(game.turn)];return true;
 }
 void resign(){if(started&&!over()){out.send("RESIGN "+round);finish(-mySide,"投了");}}
 void requestRematch(long now){if(!over()||rematchMine||peerLeft)return;rematchMine=true;out.send("REMATCH "+(round+1));if(rematchPeer)nextRound(now);}
 void nextRound(long now){round++;mySide=-mySide;game.restart();rematchMine=rematchPeer=false;lastMove=null;error=null;started=true;turnStart=now;turnStartRemaining=TIME;}
 void peerLeft(){peerLeft=true;if(started)finish(mySide,"相手の退出");}
 /** Applies one peer message; malformed or out-of-order input never changes the board. Returns true if the board changed. */
 boolean receive(String text,long now){
  String[] f=text.trim().split(" ");if(f.length==0)return false;
  try{switch(f[0]){
   case "HELLO":if(f.length<2||Integer.parseInt(f[1])!=PROTOCOL){error="相手のアプリのバージョンが異なります";finish(2,"バージョン不一致");return false;}peerHello=true;maybeStart(now);return false;
   case "MOVE":{if(f.length<5||Integer.parseInt(f[1])!=round||over()||!started||game.turn==mySide)return false;int ply=Integer.parseInt(f[2]);if(ply!=game.movesPlayed+1)return false;
    ShogiGame.Move m=UsiPosition.legalMove(game,f[3]);if(m==null){error="相手から不正な手を受信しました: "+f[3];finish(mySide,"相手の不正な手");return false;}
    long reported=Long.parseLong(f[4]),measured=Math.max(0,now-turnStart),upper=turnStartRemaining-Math.max(0,measured-allowance());
    long opponentLeft=Math.max(0,Math.min(Math.min(reported,turnStartRemaining),upper));
    int opp=game.turn;game.apply(m,opp);game.remaining[slot(opp)]=opponentLeft;lastMove=m;turnStart=now;turnStartRemaining=game.remaining[slot(game.turn)];return true;}
   case "RESIGN":if(f.length>=2&&Integer.parseInt(f[1])==round&&started)finish(mySide,"投了");return false;
   case "FLAG":if(f.length>=2&&Integer.parseInt(f[1])==round&&started){game.remaining[slot(-mySide)]=0;finish(mySide,"時間切れ");}return false;
   case "CLAIM":if(f.length>=2&&Integer.parseInt(f[1])==round&&started){game.remaining[slot(mySide)]=0;finish(-mySide,"時間切れ");}return false;
   case "REMATCH":if(f.length>=2&&Integer.parseInt(f[1])==round+1&&over()){rematchPeer=true;if(rematchMine){nextRound(now);return true;}}return false;
   default:return false;
  }}catch(RuntimeException e){return false;}
 }
 String status(long now){
  if(over()){String r=game.winner==2?game.result:(game.winner==mySide?"勝ち":"負け")+"・"+game.result;return rematchMine?r+"・再戦を申し込みました":rematchPeer?r+"・相手が再戦を希望しています":r;}
  if(!started)return peerHello||!helloSent?"対局を準備しています…":"相手の準備を待っています…";
  return game.turn==mySide?(game.checked(mySide)?"王手です・あなたの手番":"あなたの手番"):"相手の手番";
 }
}
