package com.presentsoft.shogi3minutes;
import java.io.Serializable;import java.util.*;
/** Study-branch history. Percentages are an uncalibrated score-derived guide, not measured odds. */
final class EvaluationHistory implements Serializable {
 private static final long serialVersionUID=1L;
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
  final String key;Score score;int side;String label;int parent=-1,preferred=-1;
  Point(String key){this.key=key;}
 }
 final ArrayList<Point> points=new ArrayList<>();int cursor,firstEdit;boolean imported;String title;
 // Flat node storage avoids recursive serialization and keeps repeated positions on distinct paths.
 private ArrayList<Point> nodes;
 static final class Row {
  final int node,ply;final String label;final boolean current;final int branches;
  Row(int node,int ply,String label,boolean current,int branches){this.node=node;this.ply=ply;this.label=label;this.current=current;this.branches=branches;}
 }
 private void tree(){if(nodes!=null)return;nodes=new ArrayList<>(points);for(int i=0;i<nodes.size();i++){nodes.get(i).parent=i-1;nodes.get(i).preferred=i+1<nodes.size()?i+1:-1;}}
 int currentNode(){tree();return points.isEmpty()?-1:nodes.indexOf(points.get(cursor));}
 private List<Integer> path(int node){ArrayList<Integer> path=new ArrayList<>();for(int n=node;n>=0;n=nodes.get(n).parent)path.add(n);Collections.reverse(path);return path;}
 List<String> keysTo(int node){tree();ArrayList<String> keys=new ArrayList<>();if(node<0||node>=nodes.size())return keys;for(int n:path(node))keys.add(nodes.get(n).key);return keys;}
 boolean selectNode(int node){tree();if(node<0||node>=nodes.size())return false;List<Integer> path=path(node);cursor=path.size()-1;for(int i=1;i<path.size();i++)nodes.get(path.get(i-1)).preferred=path.get(i);int next=nodes.get(node).preferred;while(next>=0){path.add(next);next=nodes.get(next).preferred;}points.clear();for(int n:path)points.add(nodes.get(n));return true;}
 List<Row> rows(){
  tree();ArrayList<Row> rows=new ArrayList<>();if(nodes.isEmpty())return rows;ArrayList<ArrayList<Integer>> children=new ArrayList<>();for(int i=0;i<nodes.size();i++)children.add(new ArrayList<>());for(int i=1;i<nodes.size();i++)children.get(nodes.get(i).parent).add(i);
  ArrayDeque<int[]> stack=new ArrayDeque<>();stack.push(new int[]{0,0,0});int selected=currentNode();
  while(!stack.isEmpty()){int[] item=stack.pop();int id=item[0],depth=item[1],indent=item[2];Point p=nodes.get(id);int alternatives=p.parent<0?1:children.get(p.parent).size();String prefix="";for(int i=0;i<Math.min(8,indent);i++)prefix+="│  ";if(alternatives>1)prefix+="├─ ";String label=p.label==null?(firstEdit+depth)+"手目":p.label;if(alternatives>1)label+="  ［"+(children.get(p.parent).indexOf(id)+1)+"/"+alternatives+"］";rows.add(new Row(id,firstEdit+depth,prefix+label,id==selected,alternatives));List<Integer> kids=children.get(id);for(int i=kids.size()-1;i>=0;i--)stack.push(new int[]{kids.get(i),depth+1,indent+(kids.size()>1?1:0)});}
  return rows;
 }
 static String key(ShogiGame g){String[] s=UsiPosition.sfen(g).split(" ");StringBuilder hand=new StringBuilder(s[2].equals("-")?"":s[2]);for(int side=0;side<2;side++)if(g.hands[side][8]>0){if(g.hands[side][8]>1)hand.append(g.hands[side][8]);hand.append(side==0?'K':'k');}return s[0]+" "+(hand.length()==0?"-":hand.toString());}
 void reset(String key){nodes=null;points.clear();points.add(new Point(key));cursor=firstEdit=0;imported=false;title=null;}
 void load(KifRecord record){nodes=null;points.clear();firstEdit=0;imported=true;title=record.title;for(int i=0;i<record.positions.size();i++){Point p=new Point(record.positions.get(i));p.side=i%2==0?1:-1;p.label=record.labels.get(i);points.add(p);}cursor=points.size()-1;}
 void load(SavedRecord record){nodes=null;points.clear();firstEdit=record.firstPly;imported=true;title=record.title;for(int i=0;i<record.positions.size();i++){Point p=new Point(record.positions.get(i));p.side=record.sides.get(i);p.label=record.labels.get(i);points.add(p);}cursor=points.size()-1;}
 void sync(String key){if(points.isEmpty()||!points.get(cursor).key.equals(key))reset(key);}
 void advance(String key){advance(key,null);}
 void advance(String key,String label){if(points.isEmpty()){reset(key);return;}if(points.get(cursor).key.equals(key))return;tree();int parent=currentNode();for(int i=0;i<nodes.size();i++)if(nodes.get(i).parent==parent&&nodes.get(i).key.equals(key)){selectNode(i);return;}Point p=new Point(key);p.parent=parent;p.label=label;nodes.add(p);nodes.get(parent).preferred=nodes.size()-1;selectNode(nodes.size()-1);}
 void undo(String key){if(cursor>0&&points.get(cursor-1).key.equals(key))cursor--;else reset(key);}
 boolean complete(String key,Score score){if(points.isEmpty()||!points.get(cursor).key.equals(key))return false;points.get(cursor).score=score;return true;}
 int currentEdit(){return firstEdit+cursor;}
 List<String> keys(){ArrayList<String> result=new ArrayList<>();for(Point p:points)result.add(p.key);return result;}
 boolean seek(int index){if(index<0||index>=points.size())return false;cursor=index;return true;}
 void side(int side){if(!points.isEmpty())points.get(cursor).side=side;}
 int side(){int value=points.isEmpty()?0:points.get(cursor).side;return value==1||value==-1?value:1;}
 Score current(){return points.isEmpty()?null:points.get(cursor).score;}
}
