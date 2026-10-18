package com.presentsoft.shogi3minutes;
import java.util.*;
import java.io.*;
public final class RulesTest {
 static int checks;static void ok(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
 static ShogiGame empty(){ShogiGame g=new ShogiGame();for(int[] a:g.board)Arrays.fill(a,0);g.board[8][4]=8;g.board[0][4]=-8;return g;}
 static boolean has(ShogiGame g,int s,int fr,int fc,int r,int c,boolean p,int d){return g.legal(s).stream().anyMatch(m->m.fromRow==fr&&m.fromCol==fc&&m.row==r&&m.col==c&&m.promote==p&&m.drop==d);}
 public static void main(String[] args)throws Exception{
  ShogiGame g=new ShogiGame();ok(UsiPosition.sfen(g).equals("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"),"initial SFEN");for(ShogiGame.Move m:g.legal(1))ok(UsiPosition.legalMove(g,UsiPosition.move(m))!=null,"USI legal roundtrip");ok(g.legal(1).size()==30,"initial 30 moves");ok(g.legal(-1).size()==30,"gote initial 30");ok(Arrays.stream(g.board).flatMapToInt(Arrays::stream).filter(p->p!=0).count()==40,"40 pieces");
  g=empty();g.board[4][4]=1;ok(has(g,1,4,4,3,4,false,0),"pawn forward");ok(!has(g,1,4,4,5,4,false,0),"no pawn reverse");g.board[3][4]=5;ok(!has(g,1,4,4,3,4,false,0),"own piece blocks");
  g=empty();g.board[4][4]=3;ok(has(g,1,4,4,2,3,false,0),"knight leap");ok(!has(g,1,4,4,3,3,false,0),"knight cannot step");
  g=empty();g.board[4][4]=7;g.board[2][4]=1;ok(!has(g,1,4,4,1,4,false,0),"rook blocker");
  g=empty();g.board[4][4]=6;ok(has(g,1,4,4,2,2,true,0),"bishop promotion zone");
  g=empty();g.board[1][1]=1;ok(has(g,1,1,1,0,1,true,0),"forced pawn promotion");ok(!has(g,1,1,1,0,1,false,0),"no dead pawn move");
  g=empty();g.board[2][1]=3;ok(has(g,1,2,1,0,2,true,0),"forced knight promotion");ok(!has(g,1,2,1,0,2,false,0),"no dead knight move");
  g=empty();g.board[3][1]=4;ok(has(g,1,3,1,2,1,false,0)&&has(g,1,3,1,2,1,true,0),"optional silver promotion");
  for(int p=9;p<=11;p++){g=empty();g.board[1][1]=p;ok(has(g,1,1,1,0,1,false,0),"promoted minor can enter last rank "+p);}
  g=empty();g.board[4][4]=14;ok(has(g,1,4,4,3,4,false,0),"horse step");g.board[4][4]=15;ok(has(g,1,4,4,3,3,false,0),"dragon step");
  g=empty();g.board[3][3]=-12;ok(has(g,-1,3,3,4,2,false,0),"gote promoted gold direction");
  g=empty();g.board[3][3]=7;g.board[3][6]=-9;g.apply(new ShogiGame.Move(3,3,3,6,false,0),1);ok(g.hands[0][1]==1,"capture demotes");
  g=empty();g.hands[0][1]=1;g.board[6][2]=1;ok(!has(g,1,-1,-1,4,2,false,1),"nifu rejected");g.board[6][2]=9;ok(has(g,1,-1,-1,4,2,false,1),"tokin not nifu");
  ok(!has(g,1,-1,-1,0,1,false,1),"dead pawn drop");g.hands[0][2]=1;ok(!has(g,1,-1,-1,0,1,false,2),"dead lance drop");g.hands[0][3]=1;ok(!has(g,1,-1,-1,1,1,false,3),"dead knight drop");
  g.apply(new ShogiGame.Move(-1,-1,4,2,false,1),1);ok(g.hands[0][1]==0&&g.board[4][2]==1,"drop consumes hand");
  g=empty();g.board[4][4]=-7;ok(g.checked(1),"rook check");g.board[6][1]=1;ok(!has(g,1,6,1,5,1,false,0),"cannot ignore check");g.hands[0][5]=1;ok(has(g,1,-1,-1,6,4,false,5),"interpose drop");
  g=empty();g.board[3][4]=-7;g.board[6][4]=5;ok(!has(g,1,6,4,6,3,false,0),"pinned gold");
  g=empty();g.board[0][4]=0;g.board[6][4]=-8;ok(!has(g,1,8,4,7,4,false,0),"kings cannot touch");
  g=empty();g.hands[0][1]=1;g.board[0][3]=-2;g.board[0][5]=-2;g.board[1][3]=-1;g.board[1][5]=-1;g.board[2][4]=5;
  String before=Arrays.deepToString(g.board)+Arrays.deepToString(g.hands);ok(!has(g,1,-1,-1,1,4,false,1),"uchifuzume");ok(before.equals(Arrays.deepToString(g.board)+Arrays.deepToString(g.hands)),"generator leaves state unchanged");g.board[0][3]=0;ok(has(g,1,-1,-1,1,4,false,1),"checking pawn drop with escape");
  g=empty();g.board[0][3]=-2;g.board[0][5]=-2;g.board[1][3]=-1;g.board[1][5]=-1;g.board[2][4]=5;g.board[3][3]=3;g.apply(new ShogiGame.Move(2,4,1,4,false,0),1);ok(g.winner==1&&g.result.equals("詰み"),"checkmate ends game");
  g=new ShogiGame();boolean rejected=false;try{g.apply(new ShogiGame.Move(6,0,3,0,false,0),1);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"apply rejects teleport");g.resign(1);ok(g.winner==-1,"resignation");g.restart();ok(g.winner==0&&g.remaining[0]==180000&&g.turn==1,"restart");
  MatchClock clock=new MatchClock(g);clock.resume(1000,true);clock.settle(3400);ok(g.remaining[0]==177600&&g.remaining[1]==180000,"exact active clock");g.apply(g.legal(1).get(0),1);clock.settle(4400);ok(g.remaining[0]==177600&&g.remaining[1]==179000,"switch without increment");clock.pause(5000);clock.settle(600000);ok(g.remaining[1]==178400,"background stop");clock.resume(700000,true);clock.settle(700100);ok(g.remaining[1]==178300,"resume correct");clock.settle(999999);ok(g.remaining[1]==0&&g.winner==1,"timeout");long a=g.remaining[0];clock.settle(1999999);ok(g.remaining[0]==a,"finished clock frozen");
  g=new ShogiGame();for(int i=0;i<180&&g.winner==0;i++){ShogiGame.Move m=g.cpuChoice();ok(m!=null&&has(g,g.turn,m.fromRow,m.fromCol,m.row,m.col,m.promote,m.drop),"CPU legal ply "+i);g.apply(m,g.turn);}
  ByteArrayOutputStream stream=new ByteArrayOutputStream();new ObjectOutputStream(stream).writeObject(g);ShogiGame restored=(ShogiGame)new ObjectInputStream(new ByteArrayInputStream(stream.toByteArray())).readObject();ok(Arrays.deepEquals(g.board,restored.board)&&Arrays.deepEquals(g.hands,restored.hands)&&g.turn==restored.turn,"save restore");System.out.println("PASS "+checks+" assertions");
 }
}
