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
  // True only after the user picks a branch from the move list; otherwise navigation follows the main line (first child).
  boolean chosen;
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
 /** Earliest child of each node: the original record move, or the first move played there. */
 private int[] mainChildren(){int[] main=new int[nodes.size()];Arrays.fill(main,-1);for(int i=nodes.size()-1;i>0;i--){int parent=nodes.get(i).parent;if(parent>=0&&parent<main.length)main[parent]=i;}return main;}
 private int next(int node,int[] main){Point p=nodes.get(node);if(p.chosen&&p.preferred>node&&p.preferred<nodes.size()&&nodes.get(p.preferred).parent==node)return p.preferred;return main[node];}
 /** Shows the path to node followed by its continuation: the chosen branch where one was picked, otherwise the main line. */
 private void project(int node){List<Integer> path=path(node);cursor=path.size()-1;int[] main=mainChildren();HashSet<Integer> seen=new HashSet<>(path);for(int n=next(node,main);n>=0&&seen.add(n);n=next(n,main))path.add(n);points.clear();for(int n:path)points.add(nodes.get(n));}
 /** Explicit branch selection from the move list; navigation keeps following this branch. */
 boolean selectNode(int node){tree();if(node<0||node>=nodes.size())return false;List<Integer> path=path(node);for(int i=1;i<path.size();i++){Point parent=nodes.get(path.get(i-1));parent.preferred=path.get(i);parent.chosen=true;}project(node);return true;}
 /** Moves to node without choosing a branch, so later forward navigation follows the main line. */
 boolean focusNode(int node){tree();if(node<0||node>=nodes.size())return false;project(node);return true;}
 /** One move of the displayed line. branch 0 is the main line; branches is the number of alternatives at this ply. */
 static final class LineRow {
  final int index,node,ply,branch,branches;final String move;final boolean current,offMain;
  LineRow(int index,int node,int ply,String move,int branch,int branches,boolean current,boolean offMain){this.index=index;this.node=node;this.ply=ply;this.move=move;this.branch=branch;this.branches=branches;this.current=current;this.offMain=offMain;}
  boolean fork(){return branches>1;}
  String branchName(){return branchName(branch);}
  static String branchName(int branch){return branch==0?"本譜":"変化"+branch;}
 }
 /** One alternative at a fork, with how many moves its continuation shows. */
 static final class Alternative {
  final int node,branch,following;final String move;final boolean shown;
  Alternative(int node,int branch,String move,int following,boolean shown){this.node=node;this.branch=branch;this.move=move;this.following=following;this.shown=shown;}
 }
 private String moveLabel(int node,int ply){Point p=nodes.get(node);return p.label==null?ply+"手目":p.label;}
 private List<Integer> siblings(int node){ArrayList<Integer> result=new ArrayList<>();int parent=nodes.get(node).parent;if(parent<0){result.add(node);return result;}for(int i=1;i<nodes.size();i++)if(nodes.get(i).parent==parent)result.add(i);return result;}
 /** The displayed line only, like a normal record, with fork markers. */
 List<LineRow> line(){
  tree();ArrayList<LineRow> rows=new ArrayList<>();if(points.isEmpty())return rows;int[] count=new int[nodes.size()],order=new int[nodes.size()];
  for(int i=1;i<nodes.size();i++){int parent=nodes.get(i).parent;if(parent>=0){order[i]=count[parent];count[parent]++;}}
  boolean offMain=false;for(int i=0;i<points.size();i++){int node=nodes.indexOf(points.get(i)),parent=nodes.get(node).parent,branch=parent<0?0:order[node];offMain|=branch>0;rows.add(new LineRow(i,node,firstEdit+i,moveLabel(node,firstEdit+i),branch,parent<0?1:count[parent],i==cursor,offMain));}
  return rows;
 }
 /** Alternatives at the ply of node, main line first. */
 List<Alternative> alternatives(int node){
  tree();ArrayList<Alternative> result=new ArrayList<>();if(node<0||node>=nodes.size())return result;int[] main=mainChildren();HashSet<Point> shown=new HashSet<>(points);List<Integer> ids=siblings(node);int ply=firstEdit+path(node).size()-1;
  for(int b=0;b<ids.size();b++){int id=ids.get(b),following=0;for(int n=next(id,main);n>=0&&following<nodes.size();n=next(n,main))following++;result.add(new Alternative(id,b,moveLabel(id,ply),following,shown.contains(nodes.get(id))));}
  return result;
 }
 /** True when the displayed line leaves the main line somewhere. */
 boolean showingVariation(){List<LineRow> rows=line();return !rows.isEmpty()&&rows.get(rows.size()-1).offMain;}
 /** Number of alternatives for the next move after the cursor, or 0 when there is no fork there. */
 int branchesAfterCursor(){if(cursor+1>=points.size())return 0;tree();int node=nodes.indexOf(points.get(cursor+1));int n=siblings(node).size();return n>1?n:0;}
 List<Row> rows(){
  tree();ArrayList<Row> rows=new ArrayList<>();if(nodes.isEmpty())return rows;ArrayList<ArrayList<Integer>> children=new ArrayList<>();for(int i=0;i<nodes.size();i++)children.add(new ArrayList<>());for(int i=1;i<nodes.size();i++)children.get(nodes.get(i).parent).add(i);
  ArrayDeque<int[]> stack=new ArrayDeque<>();stack.push(new int[]{0,0,0});int selected=currentNode();
  while(!stack.isEmpty()){int[] item=stack.pop();int id=item[0],depth=item[1],indent=item[2];Point p=nodes.get(id);int alternatives=p.parent<0?1:children.get(p.parent).size();String prefix="";for(int i=0;i<Math.min(8,indent);i++)prefix+="│  ";if(alternatives>1)prefix+="├─ ";String label=p.label==null?(firstEdit+depth)+"手目":p.label;if(alternatives>1){int branch=children.get(p.parent).indexOf(id);label+="  ［"+(branch==0?"本譜":"変化"+branch)+" "+(branch+1)+"/"+alternatives+"］";}rows.add(new Row(id,firstEdit+depth,prefix+label,id==selected,alternatives));List<Integer> kids=children.get(id);for(int i=kids.size()-1;i>=0;i--)stack.push(new int[]{kids.get(i),depth+1,indent+(kids.size()>1?1:0)});}
  return rows;
 }
 static String key(ShogiGame g){String[] s=UsiPosition.sfen(g).split(" ");StringBuilder hand=new StringBuilder(s[2].equals("-")?"":s[2]);for(int side=0;side<2;side++)if(g.hands[side][8]>0){if(g.hands[side][8]>1)hand.append(g.hands[side][8]);hand.append(side==0?'K':'k');}return s[0]+" "+(hand.length()==0?"-":hand.toString());}
 void reset(String key){nodes=null;points.clear();points.add(new Point(key));cursor=firstEdit=0;imported=false;title=null;}
 void load(KifRecord record){nodes=null;points.clear();firstEdit=0;imported=true;title=record.title;for(int i=0;i<record.positions.size();i++){Point p=new Point(record.positions.get(i));p.side=i%2==0?1:-1;p.label=record.labels.get(i);points.add(p);}cursor=points.size()-1;}
 void load(SavedRecord record){nodes=null;points.clear();firstEdit=record.firstPly;imported=true;title=record.title;for(int i=0;i<record.positions.size();i++){Point p=new Point(record.positions.get(i));p.side=record.sides.get(i);p.label=record.labels.get(i);points.add(p);}cursor=points.size()-1;}
 void sync(String key){if(points.isEmpty()||!points.get(cursor).key.equals(key))reset(key);}
 void advance(String key){advance(key,null);}
 void advance(String key,String label){if(points.isEmpty()){reset(key);return;}if(points.get(cursor).key.equals(key))return;tree();int parent=currentNode();for(int i=0;i<nodes.size();i++)if(nodes.get(i).parent==parent&&nodes.get(i).key.equals(key)){focusNode(i);return;}Point p=new Point(key);p.parent=parent;p.label=label;nodes.add(p);focusNode(nodes.size()-1);}
 void undo(String key){if(cursor>0&&points.get(cursor-1).key.equals(key))seek(cursor-1);else reset(key);}
 boolean complete(String key,Score score){if(points.isEmpty()||!points.get(cursor).key.equals(key))return false;points.get(cursor).score=score;return true;}
 int currentEdit(){return firstEdit+cursor;}
 List<String> keys(){ArrayList<String> result=new ArrayList<>();for(Point p:points)result.add(p.key);return result;}
 /** Step/jump within the displayed line; the continuation is re-derived so an unchosen fork resumes the main line. */
 boolean seek(int index){if(index<0||index>=points.size())return false;int node=index==cursor?-1:currentNodeAt(index);if(node>=0)project(node);else cursor=index;return true;}
 private int currentNodeAt(int index){tree();return nodes.indexOf(points.get(index));}
 void side(int side){if(!points.isEmpty())points.get(cursor).side=side;}
 int side(){int value=points.isEmpty()?0:points.get(cursor).side;return value==1||value==-1?value:1;}
 Score current(){return points.isEmpty()?null:points.get(cursor).score;}
}
