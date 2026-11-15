package com.presentsoft.shogi3minutes;

import android.graphics.*;

/**
 * Visual-only move effects shared by CPU matches and online games. The game state is always applied at once;
 * these effects only change how the latest move is drawn, so input is never delayed by an animation.
 */
final class BoardEffects {
 /** What the board looked like just before a move: enough to know where a dropped piece came from. */
 static final class Snapshot {
  final int[][] board=new int[9][9],hands=new int[2][9];
  static Snapshot of(ShogiGame g){Snapshot s=new Snapshot();for(int i=0;i<9;i++)System.arraycopy(g.board[i],0,s.board[i],0,9);for(int i=0;i<2;i++)System.arraycopy(g.hands[i],0,s.hands[i],0,9);return s;}
 }
 /** Drawing services of the board view, in its logical coordinates. */
 interface Geometry {
  RectF cell(int row,int col);
  /** Where hand piece type n of side was drawn in the given hands (before the drop). */
  RectF handSlot(int side,int n,int[][] hands);
  float boardPieceHeight();
  float handPieceHeight();
  /** Draws piece p centred at (x,y) with the given visual height. */
  void drawPieceAt(Canvas c,int p,float x,float y,float height);
 }
 static final long TWEEN=150,LAND=140,RING=250;
 static float easeOut(float t){t=Math.max(0,Math.min(1,t));float u=1-t;return 1-u*u*u;}
 /** 1.0 → 1.12 → 1.0 over the landing. */
 static float landingScale(float t){t=Math.max(0,Math.min(1,t));return 1+.12f*(float)Math.sin(Math.PI*t);}

 boolean reduced;
 // The latest move animation.
 int piece,toRow=-1,toCol=-1,fromRow=-1,fromCol=-1;RectF dropFrom;long start=Long.MIN_VALUE;
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

 /** Starts the move effect after the move was applied. before is the position just before it. */
 void move(ShogiGame.Move m,int mover,int pieceAfter,Snapshot before,Geometry g,long now){
  if(reduced){start=Long.MIN_VALUE;toRow=-1;return;}
  piece=pieceAfter;toRow=m.row;toCol=m.col;fromRow=m.drop>0?-1:m.fromRow;fromCol=m.drop>0?-1:m.fromCol;
  dropFrom=m.drop>0?g.handSlot(mover,m.drop,before.hands):null;start=now;
 }
 void clear(){start=Long.MIN_VALUE;toRow=-1;}
 boolean active(long now){return start!=Long.MIN_VALUE&&now-start<TWEEN+Math.max(LAND,RING);}
 /** The destination piece is drawn by the effect (in flight or landing), not by the static board. */
 boolean hides(int row,int col,long now){return start!=Long.MIN_VALUE&&row==toRow&&col==toCol&&now-start<TWEEN+LAND;}

 /** Moving piece, landing pulse and the white ring; call after the static pieces. */
 void draw(Canvas c,Geometry g,long now){
  if(!active(now))return;long t=now-start;RectF to=g.cell(toRow,toCol);
  if(t<TWEEN){
   float k=easeOut(t/(float)TWEEN);RectF from=dropFrom!=null?dropFrom:g.cell(fromRow,fromCol);
   float h0=dropFrom!=null?g.handPieceHeight():g.boardPieceHeight(),h1=g.boardPieceHeight();
   g.drawPieceAt(c,piece,from.centerX()+(to.centerX()-from.centerX())*k,from.centerY()+(to.centerY()-from.centerY())*k,h0+(h1-h0)*k);return;
  }
  long landed=t-TWEEN;
  if(landed<LAND)g.drawPieceAt(c,piece,to.centerX(),to.centerY(),g.boardPieceHeight()*landingScale(landed/(float)LAND));
  if(landed<RING){float r=landed/(float)RING,radius=Math.min(to.width(),to.height())*(.45f+.55f*easeOut(r));
   paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.4f*(1-r)+.6f);paint.setColor(Color.argb(Math.round(230*(1-r)),255,255,255));c.drawCircle(to.centerX(),to.centerY(),radius,paint);}
 }
}
