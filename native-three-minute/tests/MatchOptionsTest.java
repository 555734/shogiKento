package com.presentsoft.shogi3minutes;
import java.io.*;
public final class MatchOptionsTest {
 public static void main(String[] args)throws Exception {
  int lastNodes=0,lastTime=0;
  for(int level=1;level<=10;level++){MatchOptions o=new MatchOptions(5,-1,level);if(o.nodes()<=lastNodes||o.millis()<=lastTime)throw new AssertionError("ten increasing profiles");lastNodes=o.nodes();lastTime=o.millis();}
  for(int minutes:MatchOptions.MINUTES)for(int side:new int[]{1,-1}){
   MatchOptions o=new MatchOptions(minutes,side,10);ShogiGame game=new ShogiGame();game.remaining[0]=game.remaining[1]=o.initialMillis();MatchJournal journal=new MatchJournal(game);journal.options=o;
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(journal);}MatchJournal restored=(MatchJournal)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
   if(!o.same(restored.options)||game.remaining[0]!=minutes*60000L)throw new AssertionError("options and clocks persist");SavedRecord record=restored.record(game);
   if(!record.title.contains(minutes+"分")||!record.title.contains("10")||!(side==1?record.sente:record.gote).equals("あなた"))throw new AssertionError("correct archived settings and players");
  }
  if(new MatchOptions(9,0,99).minutes!=3||new MatchOptions(1,-1,0).level!=1)throw new AssertionError("invalid options clamped");
  System.out.println("PASS 10 AI profiles; six times x both sides; serialized settings and archive player identity");
 }
}
