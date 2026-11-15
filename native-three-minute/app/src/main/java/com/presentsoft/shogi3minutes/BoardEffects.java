package com.presentsoft.shogi3minutes;

import android.graphics.*;

/**
 * Visual-only move effects shared by CPU matches and online games. The game state is always applied at once;
 * these effects only change how the latest move is drawn, so input is never delayed by an animation.
 * Timeline of one move: the piece slides (TWEEN), then lands with a pulse and a white ring, or flips over with a
 * red glow when it promotes; a captured piece shrinks while flying to the mover's komadai, whose piece then pops;
 * a check shows 王手 over the board while the checked king's square blinks red.
 */
final class BoardEffects {
 /** What the board looked like at one moment: enough to know where pieces came from and went to. */
 static final class Snapshot {
  final int[][] board=new int[9][9],hands=new int[2][9];
  static Snapshot of(ShogiGame g){Snapshot s=new Snapshot();for(int i=0;i<9;i++)System.arraycopy(g.board[i],0,s.board[i],0,9);for(int i=0;i<2;i++)System.arraycopy(g.hands[i],0,s.hands[i],0,9);return s;}
 }
 /** Drawing services of the board view, in its logical coordinates. */
 interface Geometry {
  RectF cell(int row,int col);
  /** Where hand piece type n of side is drawn in the given hands. */
  RectF handSlot(int side,int n,int[][] hands);
  float boardPieceHeight();
  float handPieceHeight();
  /** Draws piece p centred at (x,y) with the given visual height, squeezed horizontally by scaleX (flip). */
  void drawPieceAt(Canvas c,int p,float x,float y,float height,float scaleX);
 }
 static final long TWEEN=150,LAND=140,RING=250,FLIP=220,GLOW=320,FLY=300,POP=160,CHECK=600;
 static float easeOut(float t){t=clamp(t);float u=1-t;return 1-u*u*u;}
 static float clamp(float t){return Math.max(0,Math.min(1,t));}
 /** 1.0 → 1.12 → 1.0 over the landing. */
 static float landingScale(float t){return 1+.12f*(float)Math.sin(Math.PI*clamp(t));}
 /** Horizontal squeeze of a flip: 1 → 0 (old face) → 1 (new face). */
 static float flipScale(float t){return Math.abs((float)Math.cos(Math.PI*clamp(t)));}

 boolean reduced;
 int piece,pieceBefore,toRow=-1,toCol=-1,fromRow=-1,fromCol=-1,mover;RectF dropFrom;long start=Long.MIN_VALUE;boolean promoted;
 int captured,capturedBase;RectF captureTo;
 int checkRow=-1,checkCol=-1;
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

 /** Starts the move effect after the move was applied: before/after are the positions around it; turnAfter is the side to move next. */
 void move(ShogiGame.Move m,int mover,Snapshot before,Snapshot after,boolean check,int turnAfter,Geometry g,long now){
  this.mover=mover;toRow=m.row;toCol=m.col;piece=after.board[m.row][m.col];promoted=m.promote;start=now;
  pieceBefore=m.drop>0?mover*m.drop:before.board[m.fromRow][m.fromCol];fromRow=m.drop>0?-1:m.fromRow;fromCol=m.drop>0?-1:m.fromCol;
  dropFrom=m.drop>0?g.handSlot(mover,m.drop,before.hands):null;
  captured=m.drop>0?0:before.board[m.row][m.col];capturedBase=captured==0?0:ShogiGame.base(captured);captureTo=captured==0?null:g.handSlot(mover,capturedBase,after.hands);
  checkRow=checkCol=-1;if(check)for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(after.board[r][c]==turnAfter*ShogiGame.OU){checkRow=r;checkCol=c;}
 }
 void clear(){start=Long.MIN_VALUE;toRow=-1;captured=0;checkRow=-1;}
 long end(){if(reduced)return checkRow>=0?TWEEN+CHECK:0;return TWEEN+Math.max(Math.max(promoted?Math.max(FLIP,GLOW):Math.max(LAND,RING),captured!=0?FLY+POP:0),checkRow>=0?CHECK:0);}
 boolean active(long now){return start!=Long.MIN_VALUE&&now-start<end();}
 long landing(){return reduced?0:promoted?FLIP:LAND;}
 /** The destination piece is drawn by the effect (in flight, landing or flipping), not by the static board. */
 boolean hides(int row,int col,long now){return !reduced&&start!=Long.MIN_VALUE&&row==toRow&&col==toCol&&now-start<TWEEN+landing();}
 /** The captured piece has not reached the komadai yet: show one fewer there. */
 boolean handPending(int side,int n,long now){return !reduced&&start!=Long.MIN_VALUE&&captured!=0&&side==mover&&n==capturedBase&&now-start<TWEEN+FLY;}
 /** Pop of the komadai piece that just received the captured piece. */
 float handScale(int side,int n,long now){if(reduced||start==Long.MIN_VALUE||captured==0||side!=mover||n!=capturedBase)return 1;long t=now-start-TWEEN-FLY;return t<0||t>=POP?1:1+.28f*(float)Math.sin(Math.PI*t/POP);}

 /** Moving, flipping and captured pieces, rings and glows; call after the static pieces and both komadai. */
 void draw(Canvas c,Geometry g,long now){
  if(!active(now))return;long t=now-start;RectF to=g.cell(toRow,toCol);
  if(checkRow>=0)drawCheckSquare(c,g.cell(checkRow,checkCol),t);
  if(reduced){if(checkRow>=0)drawCheckBanner(c,g,t);return;}
  if(t<TWEEN){
   float k=easeOut(t/(float)TWEEN);RectF from=dropFrom!=null?dropFrom:g.cell(fromRow,fromCol);
   float h0=dropFrom!=null?g.handPieceHeight():g.boardPieceHeight(),h1=g.boardPieceHeight();
   if(captured!=0)g.drawPieceAt(c,captured,to.centerX(),to.centerY(),h1,1);
   g.drawPieceAt(c,pieceBefore,from.centerX()+(to.centerX()-from.centerX())*k,from.centerY()+(to.centerY()-from.centerY())*k,h0+(h1-h0)*k,1);
   if(checkRow>=0)drawCheckBanner(c,g,t);return;
  }
  long landed=t-TWEEN;
  if(captured!=0&&landed<FLY&&captureTo!=null){float k=easeOut(landed/(float)FLY),h=g.boardPieceHeight()*(1-.55f*k);
   g.drawPieceAt(c,captured,to.centerX()+(captureTo.centerX()-to.centerX())*k,to.centerY()+(captureTo.centerY()-to.centerY())*k,h,1);}
  if(promoted){
   if(landed<GLOW){float r=landed/(float)GLOW;paint.reset();paint.setAntiAlias(true);float radius=Math.max(to.width(),to.height())*(.55f+.5f*r);
    paint.setShader(new RadialGradient(to.centerX(),to.centerY(),radius,new int[]{Color.argb(Math.round(200*(1-r)),255,60,40),Color.argb(0,255,40,20)},null,Shader.TileMode.CLAMP));c.drawCircle(to.centerX(),to.centerY(),radius,paint);paint.setShader(null);}
   if(landed<FLIP){float f=landed/(float)FLIP;g.drawPieceAt(c,f<.5f?pieceBefore:piece,to.centerX(),to.centerY(),g.boardPieceHeight(),Math.max(.02f,flipScale(f)));}
  }else{
   if(landed<LAND)g.drawPieceAt(c,piece,to.centerX(),to.centerY(),g.boardPieceHeight()*landingScale(landed/(float)LAND),1);
   if(landed<RING){float r=landed/(float)RING,radius=Math.min(to.width(),to.height())*(.45f+.55f*easeOut(r));
    paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.4f*(1-r)+.6f);paint.setColor(Color.argb(Math.round(230*(1-r)),255,255,255));c.drawCircle(to.centerX(),to.centerY(),radius,paint);}
  }
  if(checkRow>=0)drawCheckBanner(c,g,t);
 }
 /** The checked king's square blinks red three times (solid when effects are reduced). */
 void drawCheckSquare(Canvas c,RectF r,long t){if(t>=CHECK+TWEEN)return;float a=reduced?.6f:(float)Math.abs(Math.sin(Math.PI*3*t/(double)(CHECK+TWEEN)));
  paint.reset();paint.setAntiAlias(true);paint.setColor(Color.argb(Math.round(170*a),230,30,30));c.drawRect(r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setColor(Color.argb(Math.round(255*a),255,90,80));c.drawRect(r,paint);}
 /** 王手 over the board centre for 600ms, growing in and fading out. */
 void drawCheckBanner(Canvas c,Geometry g,long t){long b=t-TWEEN/2;if(b<0||b>=CHECK)return;float k=b/(float)CHECK,alpha=k<.15f?k/.15f:k>.75f?(1-k)/.25f:1,size=reduced?54:54*(.8f+.2f*easeOut(Math.min(1,k/.25f)));
  RectF a=g.cell(0,0),z=g.cell(8,8);float cx=(Math.min(a.left,z.left)+Math.max(a.right,z.right))/2,cy=(Math.min(a.top,z.top)+Math.max(a.bottom,z.bottom))/2;
  paint.reset();paint.setAntiAlias(true);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("serif",Typeface.BOLD));paint.setTextSize(size);
  paint.setColor(Color.argb(Math.round(150*alpha),40,0,0));c.drawRoundRect(cx-size*1.35f,cy-size*.85f,cx+size*1.35f,cy+size*.55f,12,12,paint);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*.08f);paint.setColor(Color.argb(Math.round(255*alpha),255,240,210));c.drawText("王手",cx,cy+size*.32f,paint);
  paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(Math.round(255*alpha),215,25,25));c.drawText("王手",cx,cy+size*.32f,paint);}
 // ---- Match start and end ----
 static final long START=1000,FLASH=260,END_TEXT=500,CONFETTI=2600,BUTTONS_AFTER=900;
 long startAt=Long.MIN_VALUE,endAt=Long.MIN_VALUE;String startSub="",endDetail="";int endOutcome;float[] confetti=new float[0];
 /** White flash and a growing 対局開始 with both players' sides, about one second. */
 void start(String sub,long now){startAt=now;startSub=sub;endAt=Long.MIN_VALUE;}
 /** outcome: 1 the local player won, -1 lost, 0 draw. A win also throws confetti over the board. */
 void end(int outcome,String detail,RectF board,long now){endAt=now;endOutcome=outcome;endDetail=detail;
  java.util.Random r=new java.util.Random(now);int n=outcome>0&&!reduced?90:0;confetti=new float[n*6];
  for(int i=0;i<n;i++){confetti[i*6]=board.left+r.nextFloat()*board.width();confetti[i*6+1]=board.top-r.nextFloat()*board.height()*.5f;confetti[i*6+2]=(r.nextFloat()-.5f)*60;confetti[i*6+3]=120+r.nextFloat()*160;confetti[i*6+4]=r.nextFloat()*360;confetti[i*6+5]=r.nextInt(5);}}
 void clearOverlays(){startAt=endAt=Long.MIN_VALUE;confetti=new float[0];}
 boolean ended(){return endAt!=Long.MIN_VALUE;}
 boolean endButtonsVisible(long now){return ended()&&now-endAt>=(reduced?0:BUTTONS_AFTER);}
 boolean overlayAnimating(long now){return startAt!=Long.MIN_VALUE&&now-startAt<START||ended()&&now-endAt<(confetti.length>0?CONFETTI:Math.max(END_TEXT,BUTTONS_AFTER));}
 static final int[] CONFETTI_COLORS={0xffffd25a,0xffe2403a,0xffffffff,0xff3f8fe0,0xff4fc46a};
 void drawStart(Canvas c,RectF board,long now){if(startAt==Long.MIN_VALUE)return;long t=now-startAt;if(t>=START)return;float k=t/(float)START,cx=board.centerX(),cy=board.centerY();
  if(!reduced&&t<FLASH){paint.reset();paint.setColor(Color.argb(Math.round(220*(1-t/(float)FLASH)),255,255,255));c.drawRect(board,paint);}
  float alpha=k<.12f?k/.12f:k>.7f?(1-k)/.3f:1,size=reduced?44:44*(.6f+.55f*easeOut(Math.min(1,k/.45f)));
  paint.reset();paint.setAntiAlias(true);paint.setColor(Color.argb(Math.round(170*alpha),30,6,4));c.drawRoundRect(cx-150,cy-size*1.05f,cx+150,cy+size*.75f+26,14,14,paint);
  paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("serif",Typeface.BOLD));paint.setTextSize(size);paint.setColor(Color.argb(Math.round(255*alpha),255,222,140));c.drawText("対局開始",cx,cy+size*.32f,paint);
  paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(14);paint.setColor(Color.argb(Math.round(255*alpha),255,255,255));c.drawText(startSub,cx,cy+size*.32f+24,paint);}
 void drawEnd(Canvas c,RectF board,long now){if(!ended())return;long t=now-endAt;float cx=board.centerX(),cy=board.centerY()-40;
  for(int i=0;i<confetti.length/6;i++){float s=t/1000f;if(t>=CONFETTI)break;float x=confetti[i*6]+confetti[i*6+2]*s,y=confetti[i*6+1]+confetti[i*6+3]*s+60*s*s;if(y>board.bottom)continue;
   paint.reset();paint.setAntiAlias(true);paint.setColor(CONFETTI_COLORS[(int)confetti[i*6+5]]);paint.setAlpha(Math.round(255*Math.max(0,1-t/(float)CONFETTI)));c.save();c.rotate(confetti[i*6+4]+s*240,x,y);c.drawRect(x-3.5f,y-2,x+3.5f,y+2,paint);c.restore();}
  float k=reduced?1:easeOut(Math.min(1,t/(float)END_TEXT)),size=64*(reduced?1:.7f+.3f*k);String word=endOutcome>0?"勝ち":endOutcome<0?"負け":"引き分け";
  paint.reset();paint.setAntiAlias(true);paint.setColor(Color.argb(Math.round(200*k),24,6,4));c.drawRoundRect(cx-150,cy-size*.95f,cx+150,cy+size*.5f+30,16,16,paint);
  paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("serif",Typeface.BOLD));paint.setTextSize(size);int color=endOutcome>0?0xffffd25a:endOutcome<0?0xffa9c7ff:0xffffffff;paint.setColor(color);paint.setAlpha(Math.round(255*k));c.drawText(word,cx,cy+size*.3f,paint);
  paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(14);paint.setColor(Color.WHITE);paint.setAlpha(Math.round(255*k));c.drawText(endDetail,cx,cy+size*.3f+24,paint);}
}
