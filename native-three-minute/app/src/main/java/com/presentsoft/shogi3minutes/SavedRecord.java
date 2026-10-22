package com.presentsoft.shogi3minutes;
import java.io.Serializable;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;

/** Immutable archive snapshot, independent of subsequent study edits. */
final class SavedRecord implements Serializable {
 private static final long serialVersionUID=1L;
 final String id,source,title,sente,gote,result,originalText;
 final long created,updated;final int firstPly;
 final ArrayList<String> positions,labels;final ArrayList<Integer> sides;
 SavedRecord(String id,String source,String title,String sente,String gote,String result,String originalText,long created,int firstPly,List<String> positions,List<String> labels,List<Integer> sides){
  this.id=id;this.source=source;this.title=title;this.sente=sente;this.gote=gote;this.result=result;this.originalText=originalText;this.created=created;updated=System.currentTimeMillis();this.firstPly=firstPly;
  this.positions=new ArrayList<>(positions);this.labels=new ArrayList<>(labels);this.sides=new ArrayList<>(sides);
 }
 static String digest(String text){try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.US,"%02x",b&255));return out.toString();}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 static SavedRecord imported(KifRecord r){ArrayList<Integer> sides=new ArrayList<>();for(int i=0;i<r.positions.size();i++)sides.add(i%2==0?1:-1);return new SavedRecord("kif-"+digest(r.originalText),"import",r.title,r.sente,r.gote,r.result,r.originalText,System.currentTimeMillis(),0,r.positions,r.labels,sides);}
 static SavedRecord migrated(EvaluationHistory h){ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> sides=new ArrayList<>();for(int i=0;i<h.points.size();i++){EvaluationHistory.Point p=h.points.get(i);labels.add(p.label==null?(h.firstEdit+i)+"手目":p.label);sides.add(p.side==0?((i+h.firstEdit)%2==0?1:-1):p.side);}return new SavedRecord("legacy-"+digest(h.keys().toString()),"import",h.title==null?"以前に取り込んだ棋譜":h.title,"先手","後手","","",System.currentTimeMillis(),h.firstEdit,h.keys(),labels,sides);}
 int lastPly(){return firstPly+positions.size()-1;}
 String signature(){return digest(source+title+sente+gote+result+firstPly+positions.toString()+labels.toString()+sides.toString());}
 void validate(){if(id==null||!id.matches("[a-zA-Z0-9-]{1,100}")||positions==null||positions.isEmpty()||positions.size()>20000||labels==null||sides==null||positions.size()!=labels.size()||positions.size()!=sides.size()||firstPly<0||title==null||source==null||sente==null||gote==null||result==null)throw new IllegalArgumentException("保存された棋譜の形式が不正です");for(int i=0;i<positions.size();i++)if(positions.get(i)==null||labels.get(i)==null||sides.get(i)==null||Math.abs(sides.get(i))!=1)throw new IllegalArgumentException("保存された手順が不正です");}
}
