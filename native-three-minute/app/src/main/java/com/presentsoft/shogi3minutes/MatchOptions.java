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
 /** CPU persona per level: name, rank label (a guide, not a measured strength) and fallback emblem glyph/colour. */
 static final String[] CPU_NAMES={"からくり丸","ちょうちん坊","たぬ吉","こん狐","ふくろう先生","鶴之丞","鎧からくり","木彫りの龍","能面の軍師","金鳳凰"};
 static final String[] CPU_RANKS={"10級","8級","6級","4級","2級","初段","二段","三段","四段","五段"};
 static final String[] CPU_GLYPHS={"丸","灯","狸","狐","梟","鶴","鎧","龍","面","凰"};
 static final int[] CPU_COLORS={0xff6f8f4a,0xffc77d2a,0xff8a6a3a,0xffd0702a,0xff5c6f8a,0xff7f8fa0,0xff6d3f2a,0xff4a2f1e,0xff7a1e2a,0xffc9962e};
 String cpuName(){return CPU_NAMES[level-1];}String cpuRank(){return CPU_RANKS[level-1];}
 int nodes(){return NODES[level-1];}int millis(){return MILLIS[level-1];}
 long initialMillis(){return minutes*60000L;}
 String title(){return minutes+"分切れ負け・AI Lv."+level;}
 boolean same(MatchOptions other){return other!=null&&minutes==other.minutes&&humanSide==other.humanSide&&level==other.level;}
}
