package com.presentsoft.shogi3minutes;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Plain-text, even-game KIF importer. Never mutates the current study while parsing. */
final class KifRecord {
 static final int MAX_CHARS=262144,MAX_MOVES=4096;
 private static final Pattern ROW=Pattern.compile("^(\\d+)\\s+(.+)$");
 private static final Pattern MOVE=Pattern.compile("^([1-9][一二三四五六七八九1-9]|同)(成香|成桂|成銀|歩|香|桂|銀|金|角|飛|玉|王|と|杏|圭|全|馬|龍|竜)(不成|成|打)?(?:\\(([0-9])([0-9])\\))?$");
 private static final Set<String> END=new HashSet<>(Arrays.asList("投了","中断","千日手","持将棋","詰み","切れ負け","時間切れ","反則負け","反則勝ち","入玉勝ち","不戦勝","不戦敗","トライ"));
 final List<String> positions,labels;final String title,sente,gote,result,originalText;
 private KifRecord(List<String> positions,List<String> labels,String title,String sente,String gote,String result,String originalText){this.sente=sente;this.gote=gote;this.result=result;this.originalText=originalText;this.positions=Collections.unmodifiableList(positions);this.labels=Collections.unmodifiableList(labels);this.title=title;}
 static String normalize(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC).replace("\r\n","\n").replace('\r','\n').replace("\uFEFF","").replaceAll("\\\\[ \\t]*(?=\\n|$)","");}
 static boolean looksLike(String text){
  if(text==null||text.length()>MAX_CHARS)return false;String s=normalize(text);
  boolean header=Pattern.compile("(?m)^\\s*(?:棋戦|手合割|先手|後手|開始日時|手数)\\s*[:：-]").matcher(s).find();
  return header&&Pattern.compile("(?m)^\\s*1\\s+[▲△☗☖]?(?:[1-9][一二三四五六七八九1-9]|同)\\s*(?:成香|成桂|成銀|歩|香|桂|銀|金|角|飛|玉|王|と|馬|龍|竜)").matcher(s).find();
 }
 static KifRecord parse(String text){
  if(text==null||text.length()>MAX_CHARS)throw new IllegalArgumentException("棋譜が大きすぎます（最大256K文字）");
  ShogiGame game=new ShogiGame();ArrayList<String> positions=new ArrayList<>(),labels=new ArrayList<>();positions.add(EvaluationHistory.key(game));labels.add("開始局面");
  int previousRow=-1,previousCol=-1,next=1;boolean ended=false;String title="コピーした棋譜",sente="先手",gote="後手",result="記載なし";
  for(String raw:normalize(text).split("\\n")){
   String line=raw.trim();if(line.isEmpty()||line.startsWith("#")||line.startsWith("*")||line.startsWith("手数")||line.startsWith("まで"))continue;
   if(line.startsWith("変化")||line.startsWith("後手番")||line.startsWith("先手の持駒")||line.startsWith("後手の持駒")||line.startsWith("|"))throw new IllegalArgumentException("平手の本譜のみ対応しています（変化・途中配置は未対応）");
   if(line.startsWith("手合割")){if(!line.matches("手合割\\s*:\\s*平手\\s*"))throw new IllegalArgumentException("手合割は平手のみ対応しています");continue;}
   if(line.startsWith("棋戦:")){title=line.substring(3).trim();continue;}
   if(line.startsWith("先手:")){sente=line.substring(3).trim();continue;}
   if(line.startsWith("後手:")){gote=line.substring(3).trim();continue;}
   Matcher row=ROW.matcher(line);
   if(!row.matches()){
    if(line.matches("[^:]+:.*"))continue;
    throw new IllegalArgumentException("棋譜の行を解釈できません："+shortText(line));
   }
   int number;try{number=Integer.parseInt(row.group(1));}catch(NumberFormatException e){throw new IllegalArgumentException("手数が不正です");}
   if(number!=next)throw error(next,"手数が連続していません（"+number+"手目）");
   if(ended)throw error(number,"終局後に指し手があります");
   String body=row.group(2).replaceFirst("\\s*\\(\\s*\\d+\\s*:.*$","").replaceAll("\\s+","");
   if(END.contains(body)){result=body;ended=true;next++;continue;}
   if(number>MAX_MOVES)throw error(number,"最大4096手まで対応しています");
   if(game.winner!=0)throw error(number,"終局後に指し手があります");
   if(body.startsWith("▲")||body.startsWith("☗")){if(game.turn!=1)throw error(number,"先後の記号が手番と一致しません");body=body.substring(1);}
   else if(body.startsWith("△")||body.startsWith("☖")){if(game.turn!=-1)throw error(number,"先後の記号が手番と一致しません");body=body.substring(1);}
   Matcher m=MOVE.matcher(body);if(!m.matches())throw error(number,"対応していない指し手です："+shortText(body));
   String dst=m.group(1),name=m.group(2),suffix=m.group(3);int r,c;
   if(dst.equals("同")){if(previousRow<0)throw error(number,"「同」の直前の着手がありません");r=previousRow;c=previousCol;}
   else{c=9-(dst.charAt(0)-'0');char rank=dst.charAt(1);r=rank>='1'&&rank<='9'?rank-'1':"一二三四五六七八九".indexOf(rank);}
   int piece=piece(name);boolean promote="成".equals(suffix),drop="打".equals(suffix);int sr=-1,sc=-1;
   if(m.group(4)!=null){int file=m.group(4).charAt(0)-'0',rank=m.group(5).charAt(0)-'0';if(file==0&&rank==0)drop=true;else{if(file==0||rank==0)throw error(number,"移動元の座標が不正です");sr=rank-1;sc=9-file;}}
   if(drop&&(sr>=0||piece>7||promote||"不成".equals(suffix)))throw error(number,"駒打ちの表記が不正です");
   if(!drop&&suffix!=null&&(piece>8||piece==5||piece==8))throw error(number,"成りの表記が不正です");
   ArrayList<ShogiGame.Move> candidates=new ArrayList<>();
   for(ShogiGame.Move legal:game.legal(game.turn)){
    if(legal.row!=r||legal.col!=c||legal.promote!=promote)continue;
    if(drop){if(legal.drop==piece)candidates.add(legal);}
    else if(legal.drop==0&&Math.abs(game.board[legal.fromRow][legal.fromCol])==piece&&(sr<0||legal.fromRow==sr&&legal.fromCol==sc))candidates.add(legal);
   }
   if(candidates.size()!=1)throw error(number,candidates.isEmpty()?"合法な指し手ではありません："+shortText(body):"移動元を特定できません："+shortText(body));
   game.apply(candidates.get(0),game.turn);positions.add(EvaluationHistory.key(game));labels.add(number+"手目  "+body);previousRow=r;previousCol=c;next++;
  }
  if(positions.size()<2)throw new IllegalArgumentException("読み込める指し手がありません");
  if(game.winner!=0)result=game.result;
  return new KifRecord(positions,labels,title,sente,gote,result,normalize(text));
 }
 private static int piece(String name){switch(name){case "歩":return 1;case "香":return 2;case "桂":return 3;case "銀":return 4;case "金":return 5;case "角":return 6;case "飛":return 7;case "玉":case "王":return 8;case "と":return 9;case "杏":case "成香":return 10;case "圭":case "成桂":return 11;case "全":case "成銀":return 12;case "馬":return 14;case "龍":case "竜":return 15;default:throw new IllegalArgumentException("未知の駒");}}
 private static IllegalArgumentException error(int move,String why){return new IllegalArgumentException(move+"手目："+why);}
 private static String shortText(String s){return s.length()>60?s.substring(0,60)+"…":s;}
}
