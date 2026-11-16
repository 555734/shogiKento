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
  paint.reset();paint.setAntiAlias(true);paint.setColor(Color.argb(Math.round(130*a),214,36,24));c.drawRect(r,paint);RectF in=new RectF(r);in.inset(1.2f,1.2f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(Color.argb(Math.round(255*a),255,104,84));c.drawRect(in,paint);}
 /** 王手 in the brush face on a dark band across the board centre for 600ms, growing in and fading out. */
 void drawCheckBanner(Canvas c,Geometry g,long t){long b=t-TWEEN/2;if(b<0||b>=CHECK)return;float k=b/(float)CHECK,alpha=k<.15f?k/.15f:k>.75f?(1-k)/.25f:1,size=reduced?60:60*(.8f+.2f*easeOut(Math.min(1,k/.25f)));
  RectF a=g.cell(0,0),z=g.cell(8,8);float left=Math.min(a.left,z.left),right=Math.max(a.right,z.right),cx=(left+right)/2,cy=(Math.min(a.top,z.top)+Math.max(a.bottom,z.bottom))/2;
  band(c,left,right,cy,size*.62f,alpha);
  word(c,"王手",60,size,cx,cy+size*.36f,0xffff785c,0xffce2218,alpha);}
 // ---- Banner words ----
 /** Device pixels per logical unit, set by the view so cached words stay sharp. */
 float pixelScale=1;
 private final java.util.HashMap<String,Bitmap> words=new java.util.HashMap<>();private final java.util.HashMap<String,float[]> wordMetrics=new java.util.HashMap<>();
 /**
  * A brush-face word with a vertical gradient, rasterised once at its full size into a bitmap and then drawn
  * scaled and faded: the brush outlines are complex, and redrawing them as glyphs every animation frame is costly.
  */
 void word(Canvas c,String text,float full,float size,float cx,float baseline,int top,int bottom,float alpha){
  String key=text+"|"+full+"|"+top+"|"+bottom+"|"+pixelScale;Bitmap b=words.get(key);float[] m=wordMetrics.get(key);
  if(b==null){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Fonts.brush);p.setTextSize(full*pixelScale);Paint.FontMetrics fm=p.getFontMetrics();
   int w=Math.max(1,(int)Math.ceil(p.measureText(text))+4),h=Math.max(1,(int)Math.ceil(fm.descent-fm.ascent)+4);float base=2-fm.ascent;
   b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);p.setShader(new LinearGradient(0,base-full*pixelScale*.8f,0,base,top,bottom,Shader.TileMode.CLAMP));new Canvas(b).drawText(text,2,base,p);
   m=new float[]{w/pixelScale,h/pixelScale,base/pixelScale};words.put(key,b);wordMetrics.put(key,m);}
  float k=size/full,w=m[0]*k,h=m[1]*k,top0=baseline-m[2]*k;
  paint.reset();paint.setAntiAlias(true);paint.setFilterBitmap(true);paint.setAlpha(Math.round(255*Math.max(0,Math.min(1,alpha))));c.drawBitmap(b,null,new RectF(cx-w/2,top0,cx+w/2,top0+h),paint);}
 /** A dark band with gold hairlines, fading out toward both ends: the stage for the start, check and result words. */
 void band(Canvas c,float left,float right,float cy,float half,float alpha){int ink=Color.argb(Math.round(214*alpha),16,5,4),gold=Color.argb(Math.round(220*alpha),222,184,104);
  paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(left,0,right,0,new int[]{ink&0x00ffffff,ink,ink,ink&0x00ffffff},new float[]{0,.16f,.84f,1},Shader.TileMode.CLAMP));c.drawRect(left,cy-half,right,cy+half,paint);
  paint.setShader(new LinearGradient(left,0,right,0,new int[]{gold&0x00ffffff,gold,gold,gold&0x00ffffff},new float[]{0,.28f,.72f,1},Shader.TileMode.CLAMP));c.drawRect(left,cy-half,right,cy-half+.9f,paint);c.drawRect(left,cy+half-.9f,right,cy+half,paint);paint.setShader(null);}
 // ---- Background tint by game progress ----
 /** Opening (0-30 moves) keeps the lacquer red; middle game warms to amber, late middle game deepens to crimson, endgame turns to night indigo. */
 static final int[] STAGE_TINTS={0x00000000,0x38ffa020,0x4c8a0a3c,0x5c141a5a};
 static int stage(int moves){return moves<=30?0:moves<=60?1:moves<=100?2:3;}
 int tintStage=-1,tintFrom;long tintAt=Long.MIN_VALUE;static final long TINT_FADE=900;
 /** Current tint, fading over TINT_FADE when the stage changes. */
 int tint(int moves,long now){int stage=stage(moves);if(tintStage<0){tintStage=stage;tintFrom=STAGE_TINTS[stage];tintAt=Long.MIN_VALUE;}
  if(stage!=tintStage){tintFrom=tint(now);tintStage=stage;tintAt=now;}return tint(now);}
 private int tint(long now){int to=STAGE_TINTS[tintStage];if(reduced||tintAt==Long.MIN_VALUE||now-tintAt>=TINT_FADE)return to;float k=(now-tintAt)/(float)TINT_FADE;
  return Color.argb(Math.round(Color.alpha(tintFrom)+(Color.alpha(to)-Color.alpha(tintFrom))*k),Math.round(Color.red(tintFrom)+(Color.red(to)-Color.red(tintFrom))*k),Math.round(Color.green(tintFrom)+(Color.green(to)-Color.green(tintFrom))*k),Math.round(Color.blue(tintFrom)+(Color.blue(to)-Color.blue(tintFrom))*k));}
 boolean tintFading(long now){return !reduced&&tintAt!=Long.MIN_VALUE&&now-tintAt<TINT_FADE;}
 void resetTint(){tintStage=-1;tintAt=Long.MIN_VALUE;}
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
 /** Gold leaf, washi white and a little vermilion. */
 static final int[] CONFETTI_COLORS={0xffe9c46a,0xfff6e3a8,0xffc8902f,0xffd94a33,0xfffff4e0};
 void drawStart(Canvas c,RectF board,long now){if(startAt==Long.MIN_VALUE)return;long t=now-startAt;if(t>=START)return;float k=t/(float)START,cx=board.centerX(),cy=board.centerY();
  if(!reduced&&t<FLASH){paint.reset();paint.setColor(Color.argb(Math.round(200*(1-t/(float)FLASH)),255,255,255));c.drawRect(board,paint);}
  float alpha=k<.12f?k/.12f:k>.7f?(1-k)/.3f:1,size=reduced?50:50*(.6f+.4f*easeOut(Math.min(1,k/.45f)));
  band(c,board.left,board.right,cy+4,44,alpha);
  word(c,"対局開始",50,size,cx,cy+size*.2f-2,0xfffaeaba,0xffd4a24c,alpha);
  paint.setTypeface(Fonts.medium);paint.setTextSize(12.5f);paint.setLetterSpacing(.08f);paint.setColor(Color.argb(Math.round(230*alpha),236,224,200));c.drawText(startSub,cx,cy+38,paint);}
 void drawEnd(Canvas c,RectF board,long now){if(!ended())return;long t=now-endAt;float cx=board.centerX(),cy=board.centerY()-40;
  for(int i=0;i<confetti.length/6;i++){float s=t/1000f;if(t>=CONFETTI)break;float x=confetti[i*6]+confetti[i*6+2]*s,y=confetti[i*6+1]+confetti[i*6+3]*s+60*s*s;if(y>board.bottom)continue;
   // Each leaf turns as it falls: its width follows the cosine of its own phase.
   float turn=.25f+.75f*Math.abs((float)Math.cos(confetti[i*6+4]*.05f+s*5));
   paint.reset();paint.setAntiAlias(true);paint.setColor(CONFETTI_COLORS[(int)confetti[i*6+5]]);paint.setAlpha(Math.round(235*Math.max(0,1-t/(float)CONFETTI)));c.save();c.rotate(confetti[i*6+4]+s*160,x,y);c.drawRect(x-3.6f*turn,y-2.4f,x+3.6f*turn,y+2.4f,paint);c.restore();}
  float k=reduced?1:easeOut(Math.min(1,t/(float)END_TEXT)),size=74*(reduced?1:.72f+.28f*k);String word=endOutcome>0?"勝ち":endOutcome<0?"負け":"引き分け";
  band(c,board.left,board.right,cy+4,62,k);
  int top=endOutcome>0?0xfffaeab8:endOutcome<0?0xffe6e1d8:0xfff4ead6,bottom=endOutcome>0?0xffcc9640:endOutcome<0?0xff98a2b2:0xffcdbb98;float f=endOutcome==0?.8f:1;
  word(c,word,74*f,size*f,cx,cy+size*.26f,top,bottom,k);
  paint.setTypeface(Fonts.medium);paint.setTextSize(13);paint.setLetterSpacing(.06f);paint.setColor(Color.argb(Math.round(230*k),236,224,200));c.drawText(endDetail,cx,cy+50,paint);}
}
