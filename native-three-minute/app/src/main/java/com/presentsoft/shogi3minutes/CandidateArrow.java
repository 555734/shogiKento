package com.presentsoft.shogi3minutes;

import android.graphics.*;

/** Board-space overlay; never changes cell layout or participates in touch handling. */
final class CandidateArrow {
 static final float[] WIDTH={.18f,.125f,.08f};
 static final int[] ALPHA={225,160,100};
 static void draw(Canvas canvas,RectF source,RectF target,int rank,int side){draw(canvas,source,target,rank,side,source==null);}
 /** A drop with a hand-piece source runs from that piece on the komadai to its destination square. */
 static void draw(Canvas canvas,RectF source,RectF target,int rank,int side,boolean drop){
  float unit=Math.min(target.width(),target.height());
  float tx=target.centerX(),ty=target.centerY();
  // Without a hand-piece source, a drop keeps its short arrow entirely inside its destination.
  float sx=source==null?tx:source.centerX(),sy=source==null?ty+side*unit*.43f:source.centerY();
  float dx=tx-sx,dy=ty-sy,length=(float)Math.hypot(dx,dy);if(length<.01f)return;
  float ux=dx/length,uy=dy/length,nx=-uy,ny=ux;
  if(source!=null){sx+=ux*unit*.18f;sy+=uy*unit*.18f;tx-=ux*unit*.10f;ty-=uy*unit*.10f;length-=unit*.28f;}
  int i=Math.max(0,Math.min(2,rank-1));float half=unit*WIDTH[i]/2,head=Math.min(unit*.32f,length*.55f),wing=half*2.15f;
  float bx=tx-ux*head,by=ty-uy*head;
  Path path=new Path();path.moveTo(sx+nx*half,sy+ny*half);path.lineTo(bx+nx*half,by+ny*half);path.lineTo(bx+nx*wing,by+ny*wing);path.lineTo(tx,ty);path.lineTo(bx-nx*wing,by-ny*wing);path.lineTo(bx-nx*half,by-ny*half);path.lineTo(sx-nx*half,sy-ny*half);path.close();
  Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.argb(ALPHA[i],255,91,24));canvas.drawPath(path,paint);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(unit*.018f);paint.setColor(Color.argb(ALPHA[i],255,225,159));canvas.drawPath(path,paint);
  if(drop&&source!=null){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1.2f,unit*.04f));paint.setColor(Color.argb(ALPHA[i],255,91,24));canvas.drawRoundRect(new RectF(source.left-1,source.top-1,source.right+1,source.bottom+1),4,4,paint);}
  if(drop){paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(unit*.23f);paint.setColor(Color.argb(ALPHA[i],139,45,7));canvas.drawText("打",target.right-unit*.16f,target.top+unit*.27f,paint);}
 }
}
