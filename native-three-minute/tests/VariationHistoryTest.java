package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class VariationHistoryTest {
 static int checks;
 static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void select(StudySession s,EvaluationHistory h,int node){List<String> path=h.keysTo(node);ok(s.seek(path,path.size()-1)&&h.selectNode(node),"tree selection restores board");s.game.turn=h.side();ok(EvaluationHistory.key(s.game).equals(h.points.get(h.cursor).key),"board, hands and selected node agree");}
 static void play(StudySession s,EvaluationHistory h,ShogiGame.Move move){int side=s.game.turn;String label=StudyAnalysis.notation(s.game,move);ok(move.drop>0?s.drop(side,move.drop,move.row,move.col):s.move(move.fromRow,move.fromCol,move.row,move.col,move.promote),"legal move accepted");h.advance(EvaluationHistory.key(s.game),label);h.side(-side);s.game.turn=-side;}
 static void step(StudySession s,EvaluationHistory h,int index){ok(s.seek(h.keys(),index)&&h.seek(index),"step navigation accepted");s.game.turn=h.side();ok(EvaluationHistory.key(s.game).equals(h.points.get(h.cursor).key),"board follows step navigation");}
 /** Prev/next without choosing a branch keeps the original record as the main line. */
 static void mainLineNavigation(KifRecord record){
  EvaluationHistory h=new EvaluationHistory();h.load(record);StudySession s=new StudySession();ok(s.seek(h.keys(),h.cursor),"record restored");
  while(h.cursor>64)step(s,h,h.cursor-1);ok(h.points.size()==80,"stepping back keeps the record continuation");
  ShogiGame.Move move=null;for(ShogiGame.Move m:s.game.legal(s.game.turn)){ShogiGame after=UsiPosition.studyPosition(h.points.get(64).key);after.turn=s.game.turn;after.apply(m,after.turn);if(!EvaluationHistory.key(after).equals(record.positions.get(65))){move=m;break;}}
  play(s,h,move);int variation=h.currentNode();for(int i=0;i<3;i++)play(s,h,s.game.legal(s.game.turn).get(0));String variationEnd=EvaluationHistory.key(s.game);ok(h.cursor==68&&h.points.size()==69,"AI study variation is displayed while it is played");
  ok(h.rows().stream().anyMatch(row->row.node==65&&row.label.contains("本譜 1/2"))&&h.rows().stream().anyMatch(row->row.node==variation&&row.label.contains("変化1 2/2")),"move list names the main line and the variation");
  List<EvaluationHistory.LineRow> line=h.line();ok(line.size()==69&&line.get(65).fork()&&line.get(65).branch==1&&line.get(65).branches==2&&line.get(68).offMain&&!line.get(64).offMain&&h.showingVariation(),"line view shows only the displayed variation with its fork");ok(line.get(65).display().startsWith("◆")&&line.get(65).display().contains("分岐 2通り・表示中：変化1")&&line.get(66).display().startsWith("┃")&&!line.get(64).display().startsWith("┃"),"line labels mark the fork and the variation");
  List<EvaluationHistory.Alternative> alts=h.alternatives(variation);ok(alts.size()==2&&alts.get(0).node==65&&alts.get(0).following==14&&!alts.get(0).shown&&alts.get(1).node==variation&&alts.get(1).following==3&&alts.get(1).shown,"fork lists main line and variation with continuation lengths");
  List<EvaluationHistory.ListEntry> closed=h.listEntries(new HashSet<>());ok(closed.size()==70&&closed.get(66).kind==EvaluationHistory.ListEntry.TAB&&closed.get(66).text().startsWith("▶ 分岐 2通り（表示中：変化1）"),"collapsed pull tab follows the forked move");
  List<EvaluationHistory.ListEntry> open=h.listEntries(new HashSet<>(List.of(65)));ok(open.size()==72&&open.get(66).text().startsWith("▼")&&open.get(67).kind==EvaluationHistory.ListEntry.BRANCH&&open.get(67).text().contains("本譜")&&open.get(68).text().contains("変化1")&&open.get(68).text().contains("表示中"),"expanded pull tab lists main line and variation");
  while(h.cursor>64)step(s,h,h.cursor-1);ok(h.points.size()==80&&h.points.get(65).key.equals(record.positions.get(65)),"back at the fork the original record is the continuation");ok(!h.showingVariation()&&h.branchesAfterCursor()==2&&h.line().size()==80,"main line shown again with a fork marker after the cursor");
  step(s,h,65);ok(h.currentNode()==65&&EvaluationHistory.key(s.game).equals(record.positions.get(65)),"next follows the original record, not the AI study variation");
  step(s,h,h.points.size()-1);ok(h.cursor==79&&EvaluationHistory.key(s.game).equals(record.positions.get(79)),"final follows the original record");
  step(s,h,0);step(s,h,h.points.size()-1);ok(h.cursor==79,"initial then final stays on the original record");
  select(s,h,variation);ok(h.points.size()==69&&h.points.get(68).key.equals(variationEnd),"choosing a variation shows its continuation");
  while(h.cursor>60)step(s,h,h.cursor-1);while(h.cursor<h.points.size()-1)step(s,h,h.cursor+1);ok(EvaluationHistory.key(s.game).equals(variationEnd),"a chosen variation is kept by prev/next");
  select(s,h,70);while(h.cursor>60)step(s,h,h.cursor-1);while(h.cursor<h.points.size()-1)step(s,h,h.cursor+1);ok(EvaluationHistory.key(s.game).equals(record.positions.get(79)),"choosing a record move restores the main line");
  select(s,h,64);play(s,h,recordMove(s,record.positions.get(65)));ok(h.currentNode()==65&&h.points.size()==80,"replaying the record move rejoins the main line with its continuation");
 }
 static ShogiGame.Move recordMove(StudySession s,String key){for(ShogiGame.Move m:s.game.legal(s.game.turn)){ShogiGame after=StudyAnalysis.snapshot(s.game,s.game.turn);after.apply(m,after.turn);if(EvaluationHistory.key(after).equals(key))return m;}throw new AssertionError("record move not legal");}
 public static void main(String[] args)throws Exception {
  KifRecord record=KifRecord.parse(Files.readString(Path.of("tests/fixtures/shogi_quest_79.kif")));
  EvaluationHistory h=new EvaluationHistory();h.load(record);StudySession s=new StudySession();select(s,h,64);
  List<ShogiGame.Move> alternatives=s.game.legal(s.game.turn);int[] roots=new int[3],tips=new int[3];roots[0]=65;tips[0]=79;String[] finalKeys=new String[3];finalKeys[0]=record.positions.get(79);
  for(int branch=1;branch<3;branch++) {
   select(s,h,64);ShogiGame.Move move=alternatives.get(branch-1);ShogiGame after=UsiPosition.studyPosition(h.points.get(64).key);after.turn=h.side();after.apply(move,after.turn);if(EvaluationHistory.key(after).equals(record.positions.get(65)))move=alternatives.get(10+branch);
   play(s,h,move);roots[branch]=h.currentNode();h.complete(EvaluationHistory.key(s.game),new EvaluationHistory.Score(branch*111,false,0));
   for(int i=0;i<3;i++)play(s,h,s.game.legal(s.game.turn).get(0));tips[branch]=h.currentNode();finalKeys[branch]=EvaluationHistory.key(s.game);
  }
  ok(h.rows().stream().filter(row->row.ply==65&&row.branches==3).count()==3,"three alternatives displayed at ply 65");
  for(int branch:new int[]{0,2,1,0,1,2}){select(s,h,roots[branch]);ok(h.cursor==65,"selected branch starts at ply 65");ok(h.points.get(h.points.size()-1).key.equals(finalKeys[branch]),"each branch keeps its complete future");if(branch>0)ok(h.current().cp==branch*111,"scores belong to their branch");select(s,h,tips[branch]);ok(EvaluationHistory.key(s.game).equals(finalKeys[branch]),"branch endpoint restored exactly");}
  select(s,h,roots[1]);int oldTip=tips[1];play(s,h,s.game.legal(s.game.turn).get(1));int nested=h.currentNode();ok(h.rows().size()==89,"nested branch retained");select(s,h,oldTip);ok(EvaluationHistory.key(s.game).equals(finalKeys[1]),"original nested continuation still available");select(s,h,nested);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(h);out.writeObject(s);}try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){h=(EvaluationHistory)in.readObject();s=(StudySession)in.readObject();}
  select(s,h,tips[1]);for(int branch=0;branch<3;branch++){select(s,h,roots[branch]);ok(h.points.get(h.points.size()-1).key.equals(finalKeys[branch]),"branches survive app recreation");}
  select(s,h,64);int count=h.rows().size(); // Replay a known existing child using its key.
  h.advance(record.positions.get(65));ok(h.currentNode()==65&&h.rows().size()==count,"same continuation reuses node and retains later moves");
  EvaluationHistory legacy=new EvaluationHistory();legacy.points.add(new EvaluationHistory.Point(record.positions.get(0)));legacy.points.add(new EvaluationHistory.Point(record.positions.get(1)));legacy.cursor=1;ok(legacy.rows().size()==2&&legacy.selectNode(0)&&legacy.points.size()==2,"legacy linear history migrates without losing future");
  mainLineNavigation(record);
  System.out.println("PASS persistent variation tree: "+checks+" assertions");
 }
}
