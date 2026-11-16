package com.presentsoft.shogi3minutes;
import java.util.Random;

/**
 * The opponent for a random match when no other player is found within WAIT_MILLIS. It is clearly a bot: its name
 * says ボット and the top bar shows ボット対局. It is weak on purpose: each move is ShogiGame.cpuChoice(), a one-ply
 * greedy pick (captures, promotions, central squares) with random noise and no look-ahead. It talks to the
 * player's OnlineGame with the same text protocol as a remote peer, so clocks, results, rematches and rating work
 * exactly as in a game against a person. The game is rated against the bot's fixed rating.
 */
final class OnlineBot {
 static final String PUBKEY="bot-hiyoko-v1",NAME="ボット・ひよこ";static final int RATING=1000,GAMES=100;
 /** How long a random match waits for a person before the bot steps in. Tests shorten it. */
 static long waitMillis=20000;
 /** Thinking time per move: 1 to 4 seconds, never more than a fortieth of the bot's remaining time. */
 static final long THINK_MIN=1000,THINK_SPREAD=3000;
 interface Scheduler {void after(long millis,Runnable r);long now();}
 interface Delivery {void deliver(String message);}
 final Scheduler scheduler;final Delivery toPlayer;final Random random;OnlineGame game;boolean closed;int scheduledPly=-1,scheduledRound;
 OnlineBot(Scheduler scheduler,Delivery toPlayer,long seed){this.scheduler=scheduler;this.toPlayer=toPlayer;random=new Random(seed);}
 static OnlineIdentity identity(){return new OnlineIdentity(PUBKEY,NAME,RATING,GAMES);}
 int side(){return -game.mySide;}
 /** The player's OnlineGame sends here (its Sender); replies go back through Delivery, never synchronously. */
 void fromPlayer(String text){if(closed)return;String[] f=text.trim().split(" ");if(f.length==0)return;
  switch(f[0]){
   case "HELLO":scheduler.after(150,()->{if(closed)return;toPlayer.deliver("HELLO "+OnlineGame.PROTOCOL+" "+game.time);think();});break;
   case "MOVE":scheduler.after(0,this::think);break;
   case "REMATCH":if(f.length>=2)scheduler.after(400,()->{if(closed)return;toPlayer.deliver("REMATCH "+f[1]);think();});break;
   default:break;
  }}
 /** Schedules one move when it is the bot's turn (at most one per ply and round). */
 void think(){if(closed||game==null||!game.started||game.over()||game.game.turn!=side())return;int ply=game.game.movesPlayed+1;if(scheduledPly==ply&&scheduledRound==game.round)return;
  scheduledPly=ply;scheduledRound=game.round;long left=game.remaining(side(),scheduler.now());long wait=Math.min(THINK_MIN+(long)(random.nextDouble()*THINK_SPREAD),Math.max(200,left/40));
  int round=game.round;scheduler.after(wait,()->play(round,ply));}
 void play(int round,int ply){if(closed||game.round!=round||game.over()||game.game.turn!=side()||game.game.movesPlayed+1!=ply)return;
  ShogiGame.Move m=game.game.cpuChoice();if(m==null)return;long left=game.remaining(side(),scheduler.now());if(left<=0)return;
  toPlayer.deliver("MOVE "+round+" "+ply+" "+UsiPosition.move(m)+" "+left);}
 void close(){closed=true;}
}
