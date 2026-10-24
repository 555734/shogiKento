package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class RecordStoreTest {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  File directory=Files.createTempDirectory("shogi-records").toFile();RecordStore store=new RecordStore(directory);
  ok(store.list().isEmpty(),"empty archive");KifRecord kif=KifRecord.parse(Files.readString(Path.of(args[0])));SavedRecord copy=SavedRecord.imported(kif);store.save(copy);
  ok(copy.sente.equals("ppap99(1715)")&&copy.gote.equals("hiroponman(1702)"),"Quest player headers retained");ok(copy.originalText.contains("0:02/00:00:02"),"approved original time fields retained");
  long timestamp=store.read(copy.id).updated;store.save(SavedRecord.imported(kif));ok(store.list().size()==1&&store.read(copy.id).updated==timestamp,"same pasted record neither duplicates nor reorders");
  store=new RecordStore(directory);SavedRecord read=store.read(copy.id);ok(read.lastPly()==79&&read.result.equals("詰み"),"79-move result retained after new store");
  StudySession study=new StudySession();EvaluationHistory history=new EvaluationHistory();history.load(read);
  for(int i=0;i<80;i++){ok(study.seek(read.positions,i),"saved ply opens");history.seek(i);ok(EvaluationHistory.key(study.game).equals(kif.positions.get(i))&&history.side()==(i%2==0?1:-1),"every board, hand and side exact");}
  ShogiGame match=new ShogiGame();MatchJournal journal=new MatchJournal(match);String id=journal.id;
  for(int i=0;i<12;i++){ShogiGame.Move move=match.legal(match.turn).get(0);String label=journal.label(match,move);match.apply(move,match.turn);journal.moved(match,label);store.save(journal.record(match));}
  ok(store.list().size()==2&&store.read(id).positions.size()==13,"one match is updated after every human and CPU ply");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(match);out.writeObject(journal);}try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){match=(ShogiGame)in.readObject();journal=(MatchJournal)in.readObject();}
  ok(journal.matches(match)&&journal.id.equals(id),"resume retains match identity and full history");match.resign(match.turn);store.save(journal.record(match));ok(store.read(id).result.contains("投了")&&store.read(id).positions.size()==13,"terminal result updates same full record");
  match.restart();MatchJournal next=new MatchJournal(match);store.save(next.record(match));ok(!next.id.equals(id)&&store.list().size()==3,"new game preserves previous game");
  match.tick(180000);store.save(next.record(match));ok(store.read(next.id).result.contains("時間切れ"),"timeout result retained");
  File original=new File(directory,id+".record"),backup=new File(directory,id+".record.bak");ok(original.renameTo(backup),"simulate interrupted update");store=new RecordStore(directory);ok(store.read(id).positions.size()==13&&original.exists(),"interrupted write recovered from backup");
  Files.writeString(new File(directory,"broken.record").toPath(),"invalid");ok(store.list().size()==3&&store.unreadable==1,"one corrupt file does not hide valid games");try{store.read("../escape");throw new AssertionError("traversal");}catch(IOException expected){checks++;}
  ShogiGame legacy=new ShogiGame();legacy.movesPlayed=15;MatchJournal migrated=new MatchJournal(legacy);ok(migrated.firstPly==15&&migrated.record(legacy).labels.get(0).contains("更新前"),"old saved match does not invent unavailable past moves");
  File blocked=new File(directory,"blocked");Files.writeString(blocked.toPath(),"file");try{new RecordStore(blocked).save(copy);throw new AssertionError("write failure");}catch(IOException expected){checks++;}ok(store.read(copy.id).positions.size()==80,"failed write cannot damage existing archive");
  System.out.println("PASS local record archive: "+checks+" assertions");
 }
}
