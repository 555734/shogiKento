package com.presentsoft.shogi3minutes;
import java.io.*;import java.util.*;
public final class StudyTest {
 static int checks;static void ok(boolean x,String s){checks++;if(!x)throw new AssertionError(s);}
 static int[] inventory(ShogiGame g){int[] n=new int[9];for(int[] row:g.board)for(int p:row)if(p!=0)n[ShogiGame.base(p)]++;for(int[] hand:g.hands)for(int p=1;p<=8;p++)n[p]+=hand[p];return n;}
 public static void main(String[] args)throws Exception{
  StudySession s=new StudySession();ShogiGame g=s.game;int[] initial=inventory(g);
  ok(s.move(6,0,3,5),"pawn may move freely");ok(s.move(3,5,2,5),"same side moves twice and captures");ok(g.hands[0][1]==1,"capture to correct hand");ok(s.move(0,0,4,0),"gote moves out of turn");ok(s.drop(-1,1,5,5)==false,"empty opposing hand rejected");
  ok(s.drop(1,1,0,0),"nifu and dead rank allowed in free editor");ok(s.togglePromotion(0,0)&&g.board[0][0]==9,"free promotion");ok(s.togglePromotion(0,0)&&g.board[0][0]==1,"unpromotion");
  ok(!s.togglePromotion(8,4),"king cannot promote");ok(s.move(8,4,1,4),"king freely relocates");ok(s.move(1,4,0,4),"editor permits capturing king");ok(g.hands[0][8]==1,"captured king retained");ok(s.drop(1,8,4,4),"king can be placed from hand");ok(Arrays.equals(initial,inventory(g)),"all physical pieces conserved");
  int[][] before=new int[9][];for(int i=0;i<9;i++)before[i]=g.board[i].clone();ok(s.move(4,0,5,0),"move before undo");ok(s.undo()&&Arrays.deepEquals(before,g.board),"undo restores board");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();new ObjectOutputStream(bytes).writeObject(s);StudySession copy=(StudySession)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();ok(Arrays.deepEquals(copy.game.board,g.board)&&copy.undoCount()==s.undoCount(),"save restores edits and history");ok(copy.undo(),"restored history works");
  ok(g.turn==1&&g.winner==0&&g.remaining[0]==180000&&g.remaining[1]==180000,"editing never switches match turn or clocks / ends a game");
  s.reset();ok(s.undoCount()==0&&g.board[6][0]==1&&g.board[0][4]==-8,"reset");ok(!s.undo(),"nothing to undo");
  for(int side:new int[]{1,-1})for(int type:new int[]{1,2,3,4,6,7})for(boolean promote:new boolean[]{false,true}){
   s=new StudySession();g=s.game;for(int[] row:g.board)Arrays.fill(row,0);int sr=side==1?3:5,r=side==1?2:6;g.board[sr][2]=side*type;g.board[r][2]=-side*4;
   ok(s.canPromoteOnMove(sr,2,r,2),"either side can choose promotion "+side+"/"+type);
   ok(g.board[sr][2]==side*type&&s.undoCount()==0,"checking choice never mutates board/history");
   ok(s.move(sr,2,r,2,promote),"commit selected promotion");
   ok(g.board[r][2]==side*(type+(promote?8:0))&&g.hands[side==1?0:1][4]==1,"selected form and captured piece retained");
   ok(s.undoCount()==1,"move, capture and promotion use one undo entry");
   ok(s.undo()&&g.board[sr][2]==side*type&&g.board[r][2]==-side*4&&g.hands[side==1?0:1][4]==0,"one undo restores move, capture, form and hand");
  }
  s=new StudySession();g=s.game;for(int[] row:g.board)Arrays.fill(row,0);g.board[3][2]=1;
  ok(!s.canPromoteOnMove(3,2,4,2)&&!s.move(3,2,4,2,true)&&s.undoCount()==0,"no promotion outside zone and no phantom history");
  for(int type:new int[]{5,8,9,10,11,12,14,15}){g.board[3][2]=type;ok(!s.canPromoteOnMove(3,2,2,2),"gold, king and already promoted piece do not prompt");}
  g.board[3][2]=1;g.board[2][2]=4;ok(!s.canPromoteOnMove(3,2,2,2)&&!s.move(3,2,2,2,true),"own occupied destination does not prompt or move");
  g.board[2][2]=0;g.board[3][2]=0;g.board[1][2]=1;ok(s.canPromoteOnMove(1,2,3,2),"leaving enemy zone still offers promotion");
  ok(s.move(1,2,3,2,true)&&g.board[3][2]==9,"promotion when leaving zone");
  s=new StudySession();g=s.game;
  for(int i=0;i<350;i++)ok(s.move(i%2==0?6:5,0,i%2==0?5:6,0),"bounded history moves");ok(s.undoCount()==300,"bounded history");System.out.println("PASS study editor: "+checks+" assertions");
 }
}

