package com.presentsoft.shogi3minutes;
import java.io.Serializable;import java.util.*;

/** Serialized beside the resumable match; the archive has its own durable copy. */
final class MatchJournal implements Serializable {
 private static final long serialVersionUID=1L;
 MatchOptions options;
 String id=UUID.randomUUID().toString();long created=System.currentTimeMillis();int firstPly;
 final ArrayList<String> positions=new ArrayList<>(),labels=new ArrayList<>();final ArrayList<Integer> sides=new ArrayList<>();
 MatchJournal(ShogiGame g){firstPly=g.movesPlayed;append(g,firstPly==0?"開始局面":firstPly+"手目（更新前の保存局面）");}
 private void append(ShogiGame g,String label){positions.add(EvaluationHistory.key(g));labels.add(label);sides.add(g.turn);}
 boolean matches(ShogiGame g){return firstPly+positions.size()-1==g.movesPlayed&&positions.get(positions.size()-1).equals(EvaluationHistory.key(g))&&sides.get(sides.size()-1)==g.turn;}
 String label(ShogiGame g,ShogiGame.Move m){int p=m.drop>0?m.drop:Math.abs(g.board[m.fromRow][m.fromCol]);String[] names={"","歩","香","桂","銀","金","角","飛","玉","と","成香","成桂","成銀","","馬","龍"};return (g.movesPlayed+1)+"手目  "+(g.turn==1?"▲":"△")+(9-m.col)+"一二三四五六七八九".charAt(m.row)+names[p]+(m.drop>0?"打":m.promote?"成":"")+(m.drop>0?"":"("+(9-m.fromCol)+(m.fromRow+1)+")");}
 void moved(ShogiGame g,String label){append(g,label);}
 SavedRecord record(ShogiGame g){String result=g.winner==0?"対局途中":(g.winner==2?"引き分け":g.winner==1?"先手勝ち":"後手勝ち")+"・"+g.result;return new SavedRecord(id,"match",(options==null?new MatchOptions():options).title(),(options==null||options.humanSide==1)?"あなた":"CPU",(options==null||options.humanSide==1)?"CPU":"あなた",result,"",created,firstPly,positions,labels,sides);}
}
