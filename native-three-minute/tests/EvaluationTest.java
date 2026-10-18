package com.presentsoft.shogi3minutes;
import java.io.*;
public final class EvaluationTest {
 static int n;static void ok(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 static EvaluationHistory.Score score(long cp){return new EvaluationHistory.Score(cp,false,0);}
 public static void main(String[] args)throws Exception{
  ok(score(0).senteTenths()==500&&score(0).goteTenths()==500,"neutral is 50/50");
  ok(score(600).senteTenths()==731&&score(-600).senteTenths()==269,"known sigmoid conversion");
  ok(score(1200).senteTenths()==881,"larger advantage");
  for(long cp:new long[]{-999999999L,-2000,-600,0,600,2000,999999999L}){EvaluationHistory.Score s=score(cp);ok(s.senteTenths()+s.goteTenths()==1000,"rounded percentages sum to 100.0");ok(s.senteTenths()>0&&s.senteTenths()<1000,"finite cp never displayed as guaranteed outcome");}
  ok(new EvaluationHistory.Score(1,true,0).senteTenths()==1000&&new EvaluationHistory.Score(-1,true,0).senteTenths()==0,"mate maps to endpoints");
  StudyAnalysis.Candidate c=StudyAnalysis.parse("info depth 4 score cp 600 lowerbound pv 7g7f");EvaluationHistory.Score s=EvaluationHistory.Score.from(c,-1);
  ok(s.cp==-600&&s.bound==-1&&s.percentage(true).equals("≤26.9%")&&s.percentage(false).equals("≥73.1%"),"side and bound both converted consistently");
  ok(score(9000).graphValue()==2000&&score(-9000).graphValue()==-2000,"graph range clipped without modifying raw cp");
  ok(new EvaluationHistory.Score(7,true,0).graphValue()==2000,"mate graph at winning edge");
  ShogiGame g=new ShogiGame();String key=EvaluationHistory.key(g);g.turn=-1;ok(EvaluationHistory.key(g).equals(key),"changing analysis side does not create an edit");
  EvaluationHistory h=new EvaluationHistory();h.sync("A");ok(h.cursor==0&&h.points.size()==1&&h.current()==null,"initial unanalysed point");
  ok(h.complete("A",score(0)),"valid analysis recorded");ok(!h.complete("wrong",score(600))&&h.current().cp==0,"stale result rejected");
  h.advance("B");h.complete("B",score(600));h.advance("C");ok(h.current()==null,"pending/invalid positions stay gaps");h.undo("B");ok(h.cursor==1&&h.current().cp==600,"undo restores corresponding score");
  h.advance("D");ok(h.points.size()==3&&h.points.get(2).key.equals("D"),"editing after undo truncates old branch");h.sync("D");ok(h.cursor==2,"resume keeps history");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(h);}EvaluationHistory copy;try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){copy=(EvaluationHistory)in.readObject();}ok(copy.cursor==2&&copy.points.get(1).score.cp==600,"history persists points, gaps and cursor");
  h.reset("0");for(int i=1;i<=350;i++)h.advance(""+i);ok(h.points.size()==301&&h.cursor==300&&h.firstEdit==50&&h.currentEdit()==350,"bounded 300-edit history keeps absolute edit numbers");h.undo("349");ok(h.currentEdit()==349,"undo after history trimming");
  bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(new StudySession());}boolean legacy=false;try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){in.readObject();try{in.readObject();}catch(EOFException expected){legacy=true;}}ok(legacy,"v1.7 save format can be read before missing optional graph history");
  System.out.println("PASS evaluation percentages and history: "+n+" assertions");
 }
}
