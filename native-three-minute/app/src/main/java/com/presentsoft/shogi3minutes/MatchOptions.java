package com.presentsoft.shogi3minutes;
import java.io.Serializable;

/** Shared match profile. The same ten budgets are used by the iOS app. */
final class MatchOptions implements Serializable {
 private static final long serialVersionUID=1L;
 static final int[] MINUTES={1,3,5,10,15,30};
 private static final int[] NODES={100,300,800,2000,5000,10000,20000,50000,100000,250000};
 private static final int[] MILLIS={50,75,100,150,250,400,600,750,1200,2000};
 final int minutes,humanSide,level;
 MatchOptions(){this(3,1,8);}
 MatchOptions(int minutes,int side,int level){boolean allowed=false;for(int n:MINUTES)if(n==minutes)allowed=true;this.minutes=allowed?minutes:3;humanSide=side==-1?-1:1;this.level=Math.max(1,Math.min(10,level));}
 int nodes(){return NODES[level-1];}int millis(){return MILLIS[level-1];}
 long initialMillis(){return minutes*60000L;}
 String title(){return minutes+"分切れ負け・AI Lv."+level;}
 boolean same(MatchOptions other){return other!=null&&minutes==other.minutes&&humanSide==other.humanSide&&level==other.level;}
}
