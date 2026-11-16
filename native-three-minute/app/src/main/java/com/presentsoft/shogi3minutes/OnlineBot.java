package com.presentsoft.shogi3minutes;
import java.util.Random;

/**
 * The opponent for a random match when no other player is found within waitMillis. It is clearly a bot: its name
 * says ボット and the top bar shows ボット対局. There are ten levels with fixed ratings 700 to 2050; the one closest
 * to the player's rating plays. Level 1 is ShogiGame.cpuChoice(), a one-ply greedy pick with random noise; levels
 * 2 to 10 ask the bundled engine with the CPU-match budgets of AI Lv.1 to Lv.9 (MatchOptions), falling back to the
 * greedy pick if the engine is not available. It talks to the player's OnlineGame with the same text protocol as a
 * remote peer, so clocks, results, rematches and rating work exactly as in a game against a person.
 */
final class OnlineBot {
 static final String[] NAMES={"ひよこ","すずめ","めじろ","うぐいす","つばめ","かもめ","はやぶさ","たか","わし","くまたか"};
 static final int LEVELS=10,GAMES=100;
 static int rating(int level){return 700+150*(clamp(level)-1);}
 static String name(int level){return "ボット・"+NAMES[clamp(level)-1];}
 static String pubkey(int level){return "bot-lv"+clamp(level)+"-v1";}
 static int clamp(int level){return Math.max(1,Math.min(LEVELS,level));}
 /** The level whose rating is closest to the player's (the weaker one on a tie). */
 static int levelFor(int playerRating){int best=1;for(int k=2;k<=LEVELS;k++)if(Math.abs(rating(k)-playerRating)<Math.abs(rating(best)-playerRating))best=k;return best;}
 /** Picks a move for the side to move and hands it back on the main thread; null means none. */
 interface Brain {void choose(ShogiGame game,int level,java.util.function.Consumer<ShogiGame.Move> done);}
 static final Brain GREEDY=(g,level,done)->done.accept(g.cpuChoice());
 /** How long a random match waits for a person before the bot steps in. Tests shorten it. */
 static long waitMillis=8000;
 /** Thinking time per move: 1 to 4 seconds, never more than a fortieth of the bot's remaining time. */
 static final long THINK_MIN=1000,THINK_SPREAD=3000;
 interface Scheduler {void after(long millis,Runnable r);long now();}
 interface Delivery {void deliver(String message);}
 final Scheduler scheduler;final Delivery toPlayer;final Random random;final int level;final Brain brain;OnlineGame game;boolean closed;int scheduledPly=-1,scheduledRound;
 OnlineBot(Scheduler scheduler,Delivery toPlayer,long seed,int level,Brain brain){this.scheduler=scheduler;this.toPlayer=toPlayer;random=new Random(seed);this.level=clamp(level);this.brain=brain==null?GREEDY:brain;}
 OnlineIdentity identity(){return new OnlineIdentity(pubkey(level),name(level),rating(level),GAMES);}
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
  brain.choose(game.game,level,m->{if(m==null||closed||game.round!=round||game.over()||game.game.turn!=side()||game.game.movesPlayed+1!=ply)return;
   long left=game.remaining(side(),scheduler.now());if(left<=0)return;toPlayer.deliver("MOVE "+round+" "+ply+" "+UsiPosition.move(m)+" "+left);});}
 void close(){closed=true;}
}
