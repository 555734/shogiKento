package com.presentsoft.shogi3minutes;
/**
 * Elo ratings for online random matches, identical on Android and iOS: start 1500, K 40 for the first 10
 * (provisional) games, 24 until 30 games, then 16; differences beyond 800 count as 800. Each device updates its
 * own player's rating from its own view of the result; rounding is floor(x+0.5) on both platforms.
 */
final class Rating {
 static final int INITIAL=1500,FLOOR=100,CEILING=4000,PROVISIONAL_GAMES=10;
 private Rating(){}
 static int k(int games){return games<PROVISIONAL_GAMES?40:games<30?24:16;}
 static double expected(int mine,int theirs){int d=Math.max(-800,Math.min(800,theirs-mine));return 1/(1+Math.pow(10,d/400.0));}
 /** score: 1 for a win, 0.5 for a draw, 0 for a loss. */
 static int delta(int mine,int games,int theirs,double score){return (int)Math.floor(k(games)*(score-expected(mine,theirs))+0.5);}
 static int next(int mine,int games,int theirs,double score){return Math.max(FLOOR,Math.min(CEILING,mine+delta(mine,games,theirs,score)));}
 static boolean valid(int rating){return rating>=FLOOR&&rating<=CEILING;}
}
