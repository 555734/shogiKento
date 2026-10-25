package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/**
 * The Android rules as a line protocol, so the iOS online core can be checked on Linux against exactly the
 * rules an Android peer applies. Commands: "legal SFEN", "apply USI SFEN", "check SFEN"; one reply line each.
 */
public final class RulesOracle {
 static ShogiGame game(String sfen){String[] f=sfen.trim().split(" ");if(f.length!=4)throw new IllegalArgumentException("sfen");
  ShogiGame g=UsiPosition.studyPosition(f[0]+" "+f[2]);g.turn=f[1].equals("b")?1:-1;g.movesPlayed=Integer.parseInt(f[3])-1;return g;}
 static String answer(String line){
  try{if(line.startsWith("legal ")){ShogiGame g=game(line.substring(6));StringBuilder b=new StringBuilder("ok");for(ShogiGame.Move m:g.legal(g.turn))b.append(' ').append(UsiPosition.move(m));return b.toString();}
   if(line.startsWith("apply ")){String[] p=line.split(" ",3);ShogiGame g=game(p[2]);ShogiGame.Move m=UsiPosition.legalMove(g,p[1]);if(m==null)return "err illegal";
    // Applying through ShogiGame.apply also runs its repetition bookkeeping, which is irrelevant for a single step.
    g.apply(m,g.turn);return "ok "+UsiPosition.sfen(g);}
   if(line.startsWith("check ")){ShogiGame g=game(line.substring(6));return g.checked(g.turn)?"ok 1":"ok 0";}
   return "err command";}
  catch(RuntimeException e){return "err "+e.getClass().getSimpleName();}
 }
 public static void main(String[] args)throws IOException{BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));PrintStream out=new PrintStream(new FileOutputStream(FileDescriptor.out),false,"UTF-8");
  for(String line;(line=in.readLine())!=null;){out.println(answer(line));out.flush();}}
}
