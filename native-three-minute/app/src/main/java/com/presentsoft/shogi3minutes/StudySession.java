package com.presentsoft.shogi3minutes;
import java.io.Serializable;import java.util.*;
/** Free board editor. Never calls match adjudication, clocks or automatic CPU moves. */
public final class StudySession implements Serializable {
 private static final long serialVersionUID=1L;
 public final ShogiGame game=new ShogiGame();
 private final ArrayDeque<Snapshot> history=new ArrayDeque<>();
 private static final class Snapshot implements Serializable {
  private static final long serialVersionUID=1L;final int[][] board=new int[9][9],hands=new int[2][9];
  Snapshot(ShogiGame g){for(int i=0;i<9;i++)board[i]=g.board[i].clone();for(int i=0;i<2;i++)hands[i]=g.hands[i].clone();}
  void restore(ShogiGame g){for(int i=0;i<9;i++)System.arraycopy(board[i],0,g.board[i],0,9);for(int i=0;i<2;i++)System.arraycopy(hands[i],0,g.hands[i],0,9);}
 }
 void remember(){if(history.size()==300)history.removeFirst();history.addLast(new Snapshot(game));}
 public int undoCount(){return history.size();}
 public boolean canMove(int sr,int sc,int r,int c){
  return ShogiGame.in(sr,sc)&&ShogiGame.in(r,c)&&(sr!=r||sc!=c)&&game.board[sr][sc]!=0&&ShogiGame.side(game.board[r][c])!=ShogiGame.side(game.board[sr][sc]);
 }
 public boolean canPromoteOnMove(int sr,int sc,int r,int c){
  if(!canMove(sr,sc,r,c))return false;int p=game.board[sr][sc],type=ShogiGame.base(p),side=ShogiGame.side(p);
  return Math.abs(p)<=8&&type!=ShogiGame.KIN&&type!=ShogiGame.OU&&(ShogiGame.promotionZone(side,sr)||ShogiGame.promotionZone(side,r));
 }
 public boolean move(int sr,int sc,int r,int c){return move(sr,sc,r,c,false);}
 /** A move, capture and optional promotion form one undoable editor action. */
 public boolean move(int sr,int sc,int r,int c,boolean promote){
  if(!canMove(sr,sc,r,c)||promote&&!canPromoteOnMove(sr,sc,r,c))return false;
  int p=game.board[sr][sc],dest=game.board[r][c],side=ShogiGame.side(p);
  remember();if(dest!=0)game.hands[side==1?0:1][ShogiGame.base(dest)]++;
  game.board[sr][sc]=0;game.board[r][c]=promote?side*(Math.abs(p)+8):p;return true;
 }
 public boolean drop(int side,int piece,int r,int c){
  if((side!=1&&side!=-1)||piece<1||piece>8||!ShogiGame.in(r,c)||game.board[r][c]!=0||game.hands[side==1?0:1][piece]<=0)return false;
  remember();game.hands[side==1?0:1][piece]--;game.board[r][c]=side*piece;return true;
 }
 public boolean togglePromotion(int r,int c){
  if(!ShogiGame.in(r,c))return false;int p=game.board[r][c],type=ShogiGame.base(p);
  if(p==0||type==ShogiGame.KIN||type==ShogiGame.OU)return false;
  remember();game.board[r][c]=ShogiGame.side(p)*(Math.abs(p)>8?type:type+8);return true;
 }
 public boolean undo(){if(history.isEmpty())return false;history.removeLast().restore(game);return true;}
 public void reset(){game.restart();history.clear();}
}

