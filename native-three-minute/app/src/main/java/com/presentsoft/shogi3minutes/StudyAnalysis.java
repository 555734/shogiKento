package com.presentsoft.shogi3minutes;
import java.util.*;
/** Immutable study snapshots and USI MultiPV scores. Positive scores always favour sente. */
final class StudyAnalysis {
 static ShogiGame snapshot(ShogiGame source,int side){
  ShogiGame g=new ShogiGame();for(int r=0;r<9;r++)System.arraycopy(source.board[r],0,g.board[r],0,9);
  for(int s=0;s<2;s++)System.arraycopy(source.hands[s],0,g.hands[s],0,9);
  g.turn=side;g.movesPlayed=source.movesPlayed;return g;
 }
 static String invalid(ShogiGame g){
  int[] kings=new int[2],total=new int[9],maximum={0,18,4,4,4,4,2,2,2};
  for(int r=0;r<9;r++)for(int c=0;c<9;c++){int p=g.board[r][c];if(p==0)continue;int b=ShogiGame.base(p),a=Math.abs(p);
   if(b<1||b>8||a==13||a>15)return "駒の種類が不正です";
   total[b]++;if(b==8)kings[ShogiGame.slot(ShogiGame.side(p))]++;
  }
  if(kings[0]!=1||kings[1]!=1||g.hands[0][8]!=0||g.hands[1][8]!=0)return "解析には盤上に双方の玉が必要です";
  for(int s=0;s<2;s++)for(int b=1;b<=7;b++){if(g.hands[s][b]<0)return "持ち駒の枚数が不正です";total[b]+=g.hands[s][b];}
  for(int b=1;b<=8;b++)if(total[b]>maximum[b])return "駒の総数が多すぎるため解析できません";
  for(int side:new int[]{1,-1})for(int c=0;c<9;c++){int pawns=0;for(int r=0;r<9;r++){int p=g.board[r][c];if(p==side) pawns++;if(ShogiGame.side(p)==side&&Math.abs(p)<=8&&ShogiGame.forced(side,Math.abs(p),r))return "行き所のない駒があります（配置変更可）";}if(pawns>1)return "二歩の局面です（配置は自由に変更可）";}
  if(g.checked(-g.turn))return "王手されている側を解析側に選んでください";
  return null;
 }
 static final class Candidate {
  final int rank,depth,value;final boolean mate;final String bound,move;
  Candidate(int rank,int depth,int value,boolean mate,String bound,String move){this.rank=rank;this.depth=depth;this.value=value;this.mate=mate;this.bound=bound;this.move=move;}
  String score(int side){String comparison=bound.equals("lowerbound")?(side==1?"≥":"≤"):bound.equals("upperbound")?(side==1?"≤":"≥"):"";
   long n=(long)value*side;return comparison+(mate?"詰み ":"")+(n>0?"+":"")+n;
  }
 }
 static Candidate parse(String line){
  if(!line.startsWith("info ")||line.startsWith("info string "))return null;
  String[] a=line.trim().split("\\s+");int rank=1,depth=-1,value=0;boolean score=false,mate=false;String bound="",move=null;
  try{for(int i=1;i<a.length;i++){
   if(a[i].equals("string"))break;
   if(a[i].equals("depth")&&i+1<a.length)depth=Integer.parseInt(a[++i]);
   else if(a[i].equals("multipv")&&i+1<a.length)rank=Integer.parseInt(a[++i]);
   else if(a[i].equals("score")&&i+2<a.length){String type=a[++i],n=a[++i];if(!type.equals("cp")&&!type.equals("mate"))return null;mate=type.equals("mate");if(n.equals("+")||n.equals("-"))return null;value=Integer.parseInt(n);score=true;}
   else if(a[i].equals("lowerbound")||a[i].equals("upperbound"))bound=a[i];
   else if(a[i].equals("pv")&&i+1<a.length){move=a[i+1];break;}
  }}catch(NumberFormatException e){return null;}
  if(!score||depth<1||rank<1||rank>3||move==null||!move.matches("(?:[1-9][a-i][1-9][a-i]\\+?|[PLNSGBR]\\*[1-9][a-i])"))return null;
  return new Candidate(rank,depth,value,mate,bound,move);
 }
 static final class Result {
  final String bestMove;final List<Candidate> candidates;final long nodes;final double millis;final int depth;
  Result(String best,List<Candidate> rows,long nodes,double millis,int depth){bestMove=best;candidates=Collections.unmodifiableList(new ArrayList<>(rows));this.nodes=nodes;this.millis=millis;this.depth=depth;}
 }
 static final class Collector {
  final TreeMap<Integer,TreeMap<Integer,Candidate>> iterations=new TreeMap<>();
  void accept(String line){Candidate row=parse(line);if(row!=null)iterations.computeIfAbsent(row.depth,k->new TreeMap<>()).put(row.rank,row);}
  List<Candidate> finish(int count){
   for(TreeMap<Integer,Candidate> rows:iterations.descendingMap().values()){
    ArrayList<Candidate> result=new ArrayList<>();HashSet<String> unique=new HashSet<>();
    for(int rank=1;rank<=count;rank++){Candidate c=rows.get(rank);if(c==null||!unique.add(c.move))break;result.add(c);}
    if(result.size()==count)return result;
   }
   // No completed iteration: never combine ranks from different depths.
   for(TreeMap<Integer,Candidate> rows:iterations.descendingMap().values())if(rows.containsKey(1))return Collections.singletonList(rows.get(1));
   return Collections.emptyList();
  }
 }
 static String notation(ShogiGame g,ShogiGame.Move m){
  String[] names={"","歩","香","桂","銀","金","角","飛","玉","と","成香","成桂","成銀","","馬","龍"};
  int p=m.drop>0?m.drop:Math.abs(g.board[m.fromRow][m.fromCol]);
  return (g.turn==1?"▲":"△")+"１２３４５６７８９".charAt(8-m.col)+"一二三四五六七八九".charAt(m.row)+names[p]+(m.drop>0?"打":m.promote?"成":"")+(m.drop>0?"":"("+(9-m.fromCol)+(m.fromRow+1)+")");
 }
}
