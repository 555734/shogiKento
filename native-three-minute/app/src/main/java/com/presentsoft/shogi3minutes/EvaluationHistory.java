package com.presentsoft.shogi3minutes;
import java.io.Serializable;import java.util.*;
/** Study-branch history. Percentages are an uncalibrated score-derived guide, not measured odds. */
final class EvaluationHistory implements Serializable {
 private static final long serialVersionUID=1L;
 static final int LIMIT=301;
 static final class Score implements Serializable {
  private static final long serialVersionUID=1L;
  final long cp;final boolean mate;final int bound;
  Score(long cp,boolean mate,int bound){this.cp=cp;this.mate=mate;this.bound=bound;}
  static Score from(StudyAnalysis.Candidate c,int side){int b=c.bound.equals("lowerbound")?1:c.bound.equals("upperbound")?-1:0;return new Score((long)c.value*side,c.mate,b*side);}
  int senteTenths(){if(mate)return cp>0?1000:0;double p=1.0/(1.0+Math.exp(-Math.max(-60000,Math.min(60000,cp))/600.0));return Math.max(1,Math.min(999,(int)Math.round(1000*p)));}
  int goteTenths(){return 1000-senteTenths();}
  String percentage(boolean sente){int value=sente?senteTenths():goteTenths();int b=sente?bound:-bound;return (b>0?"≥":b<0?"≤":"")+String.format(Locale.US,"%.1f%%",value/10.0);}
  float graphValue(){return mate?(cp>0?2000:-2000):Math.max(-2000,Math.min(2000,cp));}
 }
 static final class Point implements Serializable {
  private static final long serialVersionUID=1L;
  final String key;Score score;int side;
  Point(String key){this.key=key;}
 }
 final ArrayList<Point> points=new ArrayList<>();int cursor,firstEdit;
 static String key(ShogiGame g){String[] s=UsiPosition.sfen(g).split(" ");StringBuilder hand=new StringBuilder(s[2].equals("-")?"":s[2]);for(int side=0;side<2;side++)if(g.hands[side][8]>0){if(g.hands[side][8]>1)hand.append(g.hands[side][8]);hand.append(side==0?'K':'k');}return s[0]+" "+(hand.length()==0?"-":hand.toString());}
 void reset(String key){points.clear();points.add(new Point(key));cursor=firstEdit=0;}
 void sync(String key){if(points.isEmpty()||!points.get(cursor).key.equals(key))reset(key);}
 void advance(String key){if(points.isEmpty()){reset(key);return;}if(points.get(cursor).key.equals(key))return;
  while(points.size()>cursor+1)points.remove(points.size()-1);points.add(new Point(key));cursor++;
  if(points.size()>LIMIT){points.remove(0);cursor--;firstEdit++;}
 }
 void undo(String key){if(cursor>0&&points.get(cursor-1).key.equals(key))cursor--;else reset(key);}
 boolean complete(String key,Score score){if(points.isEmpty()||!points.get(cursor).key.equals(key))return false;points.get(cursor).score=score;return true;}
 int currentEdit(){return firstEdit+cursor;}
 List<String> keys(){ArrayList<String> result=new ArrayList<>();for(Point p:points)result.add(p.key);return result;}
 boolean seek(int index){if(index<0||index>=points.size())return false;cursor=index;return true;}
 void side(int side){if(!points.isEmpty())points.get(cursor).side=side;}
 int side(){int value=points.isEmpty()?0:points.get(cursor).side;return value==1||value==-1?value:1;}
 Score current(){return points.isEmpty()?null:points.get(cursor).score;}
}
