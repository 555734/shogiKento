package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class VariationHistoryTest {
 static int checks;
 static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void select(StudySession s,EvaluationHistory h,int node){List<String> path=h.keysTo(node);ok(s.seek(path,path.size()-1)&&h.selectNode(node),"tree selection restores board");s.game.turn=h.side();ok(EvaluationHistory.key(s.game).equals(h.points.get(h.cursor).key),"board, hands and selected node agree");}
 static void play(StudySession s,EvaluationHistory h,ShogiGame.Move move){int side=s.game.turn;String label=StudyAnalysis.notation(s.game,move);ok(move.drop>0?s.drop(side,move.drop,move.row,move.col):s.move(move.fromRow,move.fromCol,move.row,move.col,move.promote),"legal move accepted");h.advance(EvaluationHistory.key(s.game),label);h.side(-side);s.game.turn=-side;}
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
  System.out.println("PASS persistent variation tree: "+checks+" assertions");
 }
}
