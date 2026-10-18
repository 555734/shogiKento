package com.presentsoft.shogi3minutes;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.io.*;import java.util.*;
public final class KifRecordTest {
 static int checks;static void ok(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static void reject(String text,String reason){try{KifRecord.parse(text);throw new AssertionError("accepted "+reason);}catch(IllegalArgumentException ex){ok(ex.getMessage().contains(reason),ex.getMessage()+" / expected "+reason);}}
 public static void main(String[] args)throws Exception{
  String text=Files.readString(Path.of(args[0]),StandardCharsets.UTF_8);KifRecord record=KifRecord.parse(text);
  ok(KifRecord.looksLike(text),"Quest recognized");ok(record.positions.size()==80&&record.labels.size()==80,"all 79 plies and initial retained");
  if(args.length>1)Files.write(Path.of(args[1]),record.positions,StandardCharsets.UTF_8);
  ok(record.title.equals("Shogi Quest"),"metadata title");
  ShogiGame finalBoard=UsiPosition.studyPosition(record.positions.get(79));finalBoard.turn=-1;
  System.out.println("Final SFEN: "+UsiPosition.sfen(finalBoard));System.out.println("Final checked="+finalBoard.checked(-1)+" legal="+finalBoard.legal(-1).size());
  ok(finalBoard.checked(-1)&&finalBoard.legal(-1).isEmpty(),"79 is checkmate: no legal continuation");
  ok(finalBoard.board[2][1]==ShogiGame.KIN,"79 G*83");ok(finalBoard.board[1][2]==15,"77 dragon on 72");ok(finalBoard.board[2][0]==-8,"king 93");ok(finalBoard.board[7][4]==-11,"78 promoted knight 58");
  ok(record.positions.equals(KifRecord.parse(text.replace("\n","\\\r\n")).positions),"trailing backslash + CRLF copied formatting");
  String normalized=KifRecord.normalize(text);ok(record.positions.equals(KifRecord.parse(normalized.replace(" ","\t")).positions),"ASCII numbers tabs and times");
  StringBuilder wide=new StringBuilder();for(char c:normalized.toCharArray())wide.append(c>='0'&&c<='9'?(char)(c-'0'+'０'):c);ok(record.positions.equals(KifRecord.parse(wide.toString()).positions),"fullwidth move and source numbers");
  ok(!KifRecord.looksLike("meeting 1 2 3")&&!KifRecord.looksLike("1 ４六歩(47)"),"ordinary text ignored");
  reject(text.replace("2 ５四歩","3 ５四歩"),"2手目");reject(text.replace("４六歩(47)","４五歩(47)"),"1手目");reject(text.replace("手合割：平手","手合割：角落ち"),"平手のみ");reject(text+"\n変化：20手\n","変化");
  reject("棋戦: test\n1 同 歩(47)","「同」");reject("棋戦: test\n1 ４六歩打","1手目");reject(text.replace("79 ８三金打","79 ８三金打(83)"),"駒打ち");
  ok(KifRecord.parse(text+"\n80 投了 (0:01/00:01:00)\nまで79手で先手の勝ち").positions.equals(record.positions),"terminal marker adds no fake ply");
  EvaluationHistory h=new EvaluationHistory();h.load(record);StudySession s=new StudySession();
  for(int i=0;i<80;i++){ok(s.seek(h.keys(),i)&&h.seek(i),"seek every imported ply "+i);ok(EvaluationHistory.key(s.game).equals(record.positions.get(i)),"exact board/hands "+i);ok(h.side()==(i%2==0?1:-1),"side "+i);}
  h.seek(79);s.seek(h.keys(),79);String end=EvaluationHistory.key(s.game);ok(h.firstEdit==0&&h.currentEdit()==79,"absolute ply");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream o=new ObjectOutputStream(bytes)){o.writeObject(s);o.writeObject(h);}try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){s=(StudySession)in.readObject();h=(EvaluationHistory)in.readObject();}
  ok(h.imported&&h.title.equals("Shogi Quest")&&h.points.get(79).label.contains("8三金打"),"import metadata survives restart");ok(s.seek(h.keys(),0)&&s.seek(h.keys(),79)&&EvaluationHistory.key(s.game).equals(end),"whole game survives restart");
  h.seek(20);s.seek(h.keys(),20);ok(s.move(6,0,5,0),"free edit imported branch");h.advance(EvaluationHistory.key(s.game));ok(h.points.size()==22&&h.points.get(21).label==null,"branch removes stale future labels");
  h.load(record);for(int i=0;i<350;i++)h.advance("position-"+i);ok(h.firstEdit==0&&h.points.size()==430,"imported beginning never evicted after 300");
  System.out.println("PASS KIF import: "+checks+" assertions");
 }
}
