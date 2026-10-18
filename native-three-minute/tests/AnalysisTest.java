package com.presentsoft.shogi3minutes;
import java.util.*;
public final class AnalysisTest {
 static int checks;static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static String info(int depth,int rank,String score,String move){return "info depth "+depth+" seldepth 18 multipv "+rank+" score "+score+" nodes 345 nps 10000 time 34 pv "+move+" 3c3d";}
 public static void main(String[] args){
  ShogiGame original=new ShogiGame(),g=StudyAnalysis.snapshot(original,-1);ok(g.turn==-1&&original.turn==1,"analysis side does not alter editor turn");g.board[6][0]=0;g.hands[0][1]=1;ok(original.board[6][0]==1&&original.hands[0][1]==0,"snapshot has no mutable aliases");
  for(int side:new int[]{1,-1}){g=StudyAnalysis.snapshot(original,side);ok(StudyAnalysis.invalid(g)==null,"both sides of initial board valid");ok(UsiPosition.sfen(g).contains(side==1?" b ":" w "),"snapshot serializes chosen side");}
  g=StudyAnalysis.snapshot(original,1);g.board[0][4]=0;ok(StudyAnalysis.invalid(g).contains("玉"),"missing king rejected");g.board[0][4]=-8;g.hands[0][8]=1;ok(StudyAnalysis.invalid(g).contains("玉"),"king in hand rejected");
  g=StudyAnalysis.snapshot(original,1);g.board[5][0]=1;ok(StudyAnalysis.invalid(g)!=null,"too many physical pawns rejected");g.board[6][1]=0;ok(StudyAnalysis.invalid(g).contains("二歩"),"nifu editor board explained");
  for(int side:new int[]{1,-1})for(int p:new int[]{1,2,3}){g=StudyAnalysis.snapshot(original,side);for(int[] row:g.board)Arrays.fill(row,0);g.board[8][4]=8;g.board[0][4]=-8;g.board[side==1?0:8][0]=side*p;ok(StudyAnalysis.invalid(g).contains("行き所"),"dead pawn/lance/knight rejected");}
  g=StudyAnalysis.snapshot(original,1);for(int[] row:g.board)Arrays.fill(row,0);g.board[8][4]=8;g.board[0][4]=-8;g.board[4][4]=7;ok(StudyAnalysis.invalid(g).contains("王手"),"nonmoving king cannot be attacked");g.turn=-1;ok(StudyAnalysis.invalid(g)==null,"checked side may be analysed");
  StudyAnalysis.Candidate c=StudyAnalysis.parse(info(8,2,"cp -123","7g7f"));ok(c!=null&&c.depth==8&&c.rank==2,"info fields parsed");ok(c.score(1).equals("-123")&&c.score(-1).equals("+123"),"cp converted to sente perspective");
  c=StudyAnalysis.parse(info(8,1,"mate 5","P*5b"));ok(c!=null&&c.mate&&c.score(1).equals("詰み +5")&&c.score(-1).equals("詰み -5"),"mate is not mislabelled as cp");
  c=StudyAnalysis.parse(info(8,1,"mate -4 upperbound","2b3c+"));ok(c.score(-1).equals("≥詰み +4"),"negative mate bound reverses for gote");
  c=StudyAnalysis.parse(info(8,1,"cp 200 lowerbound","7g7f"));ok(c.score(1).equals("≥+200")&&c.score(-1).equals("≤-200"),"bounds preserved and reversed");
  for(String line:new String[]{"info string score cp 100 pv 7g7f", "bestmove 7g7f",info(0,1,"cp 50","7g7f"),info(8,4,"cp 5","7g7f"),info(8,1,"cp bad","7g7f"),info(8,1,"cp 1","none"),info(8,1,"mate +","7g7f"),"info depth 7 score cp 4",info(8,1,"cp 999999999999","7g7f")})ok(StudyAnalysis.parse(line)==null,"malformed/incomplete info ignored");
  StudyAnalysis.Collector collector=new StudyAnalysis.Collector();String[] roots={"7g7f","2g2f","5i6h"};for(int rank=1;rank<=3;rank++)collector.accept(info(7,rank,"cp "+(100-rank),roots[rank-1]));collector.accept(info(8,1,"cp 150","6i7h"));
  List<StudyAnalysis.Candidate> rows=collector.finish(3);ok(rows.size()==3&&rows.get(0).depth==7&&rows.get(2).depth==7,"interrupted iteration does not mix depth or ranks");
  collector.accept(info(8,2,"cp 140","2g2f"));collector.accept(info(8,3,"cp 130","7g7f"));rows=collector.finish(3);ok(rows.size()==3&&rows.get(0).depth==8&&rows.get(0).move.equals("6i7h"),"latest complete iteration used");
  collector.accept(info(9,1,"cp 160","2g2f"));collector.accept(info(9,2,"cp 140","2g2f"));collector.accept(info(9,3,"cp 130","7g7f"));ok(collector.finish(3).get(0).depth==8,"duplicate roots excluded");
  collector=new StudyAnalysis.Collector();collector.accept(info(2,1,"cp 1","7g7f"));ok(collector.finish(3).size()==1,"incomplete first iteration uses only known principal candidate");ok(new StudyAnalysis.Collector().finish(3).isEmpty(),"never fabricate missing scores");
  g=new ShogiGame();ShogiGame.Move move=UsiPosition.legalMove(g,"7g7f");ok(StudyAnalysis.notation(g,move).equals("▲７六歩(77)"),"Japanese notation includes unambiguous source");g.turn=-1;move=UsiPosition.legalMove(g,"3c3d");ok(StudyAnalysis.notation(g,move).equals("△３四歩(33)"),"gote notation");
  g.hands[1][4]=1;move=UsiPosition.legalMove(g,"S*5e");ok(StudyAnalysis.notation(g,move).equals("△５五銀打"),"drop notation");
  for(int d=1;d<=40;d++){c=StudyAnalysis.parse(info(d,1,"cp "+d,"2b3c+"));ok(c.depth==d&&c.move.equals("2b3c+"),"depth and promoted root retained");}
  System.out.println("PASS study analysis: "+checks+" assertions");
 }
}
