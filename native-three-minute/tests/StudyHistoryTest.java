package com.presentsoft.shogi3minutes;
import java.io.*;import java.util.*;
public final class StudyHistoryTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void record(StudySession s,EvaluationHistory h,int side){h.advance(EvaluationHistory.key(s.game));h.side(side);h.complete(EvaluationHistory.key(s.game),new EvaluationHistory.Score(h.cursor*100,false,0));}
 static void jump(StudySession s,EvaluationHistory h,int target){ok(s.seek(h.keys(),target)&&h.seek(target),"seek accepted");ok(EvaluationHistory.key(s.game).equals(h.points.get(target).key),"board and all hands restored exactly");}
 public static void main(String[] a)throws Exception{
  StudySession s=new StudySession();EvaluationHistory h=new EvaluationHistory();h.reset(EvaluationHistory.key(s.game));h.side(1);
  ok(s.move(6,0,2,0,true),"move captures and promotes");record(s,h,-1);ok(s.drop(1,1,5,0),"hand drop");record(s,h,-1);ok(s.togglePromotion(2,0),"unpromotion");record(s,h,1);ok(s.move(8,4,0,4),"free capture of opposing king");record(s,h,-1);
  List<String> keys=new ArrayList<>(h.keys());for(int t:new int[]{0,4,1,3,2,4,0,2}){jump(s,h,t);ok(h.points.size()==5&&h.keys().equals(keys),"navigation keeps future graph");ok(s.undoCount()==t,"undo rebuilt to selected ply");ok(t==0||h.current().cp==t*100,"selected score belongs to selected position");}
  jump(s,h,4);ok(s.game.hands[0][8]==1,"king in hand retained in free-editor key");ok(s.undo()&&EvaluationHistory.key(s.game).equals(keys.get(3)),"undo after graph seek restores full previous board");
  jump(s,h,2);ok(s.move(8,0,7,0),"branch after seek");record(s,h,-1);ok(h.points.size()==4&&h.cursor==3&&!h.keys().contains(keys.get(4)),"new move truncates obsolete future");
  ByteArrayOutputStream b=new ByteArrayOutputStream();try(ObjectOutputStream o=new ObjectOutputStream(b)){o.writeObject(s);o.writeObject(h);}try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()))){s=(StudySession)in.readObject();h=(EvaluationHistory)in.readObject();}
  jump(s,h,0);jump(s,h,3);ok(h.side()==-1&&s.undoCount()==3,"seek, side and undo survive recreation");
  String before=EvaluationHistory.key(s.game);int undo=s.undoCount();List<String> broken=new ArrayList<>(h.keys());broken.set(1,"bad position");ok(!s.seek(broken,3)&&EvaluationHistory.key(s.game).equals(before)&&s.undoCount()==undo,"corrupt history rejected atomically");ok(!s.seek(h.keys(),-1)&&!s.seek(h.keys(),999),"out-of-range history rejected");
  for(String bad:new String[]{"9/9 -","9/9/9/9/9/9/9/9/8 -","9/9/9/9/9/9/9/9/9 2","9/9/9/9/9/9/9/9/+K8 -","9/9/9/9/9/9/9/9/9 0P"}){try{UsiPosition.studyPosition(bad);throw new AssertionError(bad);}catch(IllegalArgumentException expected){checks++;}}
  s=new StudySession();h=new EvaluationHistory();h.reset(EvaluationHistory.key(s.game));for(int i=0;i<350;i++){ok(s.move(i%2==0?6:5,0,i%2==0?5:6,0),"bounded move");record(s,h,i%2==0?-1:1);}ok(h.firstEdit==50&&h.points.size()==301,"retained range uses absolute ply numbers");jump(s,h,0);ok(h.currentEdit()==50,"oldest retained ply reachable");jump(s,h,300);ok(h.currentEdit()==350&&s.undoCount()==300,"latest retained ply reachable");
  System.out.println("PASS graph history navigation: "+checks+" assertions");
 }
}
