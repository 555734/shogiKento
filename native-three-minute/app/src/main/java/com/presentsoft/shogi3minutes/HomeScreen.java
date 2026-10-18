package com.presentsoft.shogi3minutes;
import android.graphics.*;

/** Text and hit areas are native. Artwork is independent of device aspect ratio and menu labels. */
final class HomeScreen {
 static final String BACKGROUND="generated/home_background_v112.webp",TITLE="generated/home_title_v112.webp";
 static final class Layout {
  final float unit,titleY;final RectF study,match,paste,archive,settings=new RectF(328,4,392,64);
  Layout(float height){unit=Math.min(1,Math.max(.65f,(height-160)/460));float top=Math.max(145*unit,Math.min(height*.395f,height-392*unit-30));titleY=Math.min(height*.20f,top-68*unit);study=new RectF(20,top,380,top+146*unit);top=study.bottom+14*unit;match=new RectF(20,top,380,top+88*unit);top=match.bottom+14*unit;paste=new RectF(20,top,380,top+62*unit);top=paste.bottom+14*unit;archive=new RectF(20,top,380,top+54*unit);}
 }
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Typeface serif=Typeface.create("serif",Typeface.BOLD);
 void bitmap(Canvas c,Bitmap image,RectF box,boolean cover){float factor=cover?Math.max(box.width()/image.getWidth(),box.height()/image.getHeight()):Math.min(box.width()/image.getWidth(),box.height()/image.getHeight());float w=image.getWidth()*factor,h=image.getHeight()*factor;paint.reset();paint.setFilterBitmap(true);c.drawBitmap(image,null,new RectF(box.centerX()-w/2,box.centerY()-h/2,box.centerX()+w/2,box.centerY()+h/2),paint);}
 void words(Canvas c,String value,float x,float y,float size){paint.reset();paint.setAntiAlias(true);paint.setTypeface(serif);paint.setTextSize(size);paint.setColor(0xfffff2ca);paint.setShadowLayer(2,0,1,0xff170600);c.drawText(value,x,y,paint);}
 private void panel(Canvas c,RectF rect,boolean primary,Bitmap texture){
  paint.reset();paint.setAntiAlias(true);if(primary)for(int i=4;i>0;i--){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1+i*1.4f);paint.setColor((18-i*3)<<24|0xffb522);c.drawRoundRect(rect,14,14,paint);}
  paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,primary?new int[]{0xff71080a,0xffa90c11,0xff390407}:new int[]{0xff36241a,0xff17110e,0xff23170f},null,Shader.TileMode.CLAMP));c.drawRoundRect(rect,13,13,paint);
  c.save();Path clip=new Path();clip.addRoundRect(rect,13,13,Path.Direction.CW);c.clipPath(clip);
  if(primary){paint.reset();paint.setFilterBitmap(true);paint.setAlpha(100);c.drawBitmap(texture,new Rect(texture.getWidth()/4,texture.getHeight()/3,texture.getWidth()*3/4,texture.getHeight()*2/3),rect,paint);}
  else{paint.reset();paint.setColor(0x0effd699);paint.setStrokeWidth(.45f);for(int i=0;i<32;i++){float y=rect.top+rect.height()*i/32;c.drawLine(rect.left,y,rect.right,y+.8f*(float)Math.sin(i),paint);}}
  paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(primary?0x66ffcb61:0x33dba74e);
  for(int side:new int[]{0,1})for(int row=0;row<2;row++)for(int col=0;col<4;col++){float x=side==0?rect.left+col*25-20:rect.right-col*25+20,y=rect.bottom+row*10;for(int ring=1;ring<4;ring++){float r=ring*6;c.drawArc(x-r,y-r,x+r,y+r,180,180,false,paint);}}
  c.restore();paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(primary?1.8f:1.1f);paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,new int[]{0xffffe6a6,0xff8d5720,0xffffde85,0xff8c4f19,0xffffe8ae},null,Shader.TileMode.CLAMP));c.drawRoundRect(rect,13,13,paint);RectF inner=new RectF(rect);inner.inset(2.4f,2.4f);paint.setShader(null);paint.setColor(primary?0xffeab858:0x77ab8956);paint.setStrokeWidth(.5f);c.drawRoundRect(inner,11,11,paint);
 }
 private void rule(Canvas c,float left,float right,float y){paint.reset();paint.setStrokeWidth(.7f);paint.setShader(new LinearGradient(left,y,right,y,new int[]{0xff8a5a26,0xffffe3a0,0xff8a5a26},null,Shader.TileMode.CLAMP));c.drawLine(left,y,right,y,paint);}
 private void document(Canvas c,float x,float y,boolean history){paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xffe4b563);paint.setStrokeWidth(1.8f);Path p=new Path();p.moveTo(x-12,y-17);p.lineTo(x+5,y-17);p.lineTo(x+13,y-9);p.lineTo(x+13,y+17);p.lineTo(x-12,y+17);p.close();c.drawPath(p,paint);c.drawLine(x+5,y-17,x+5,y-9,paint);c.drawLine(x+5,y-9,x+13,y-9,paint);if(history){c.drawCircle(x,y+3,7,paint);c.drawLine(x,y+3,x,y-2,paint);c.drawLine(x,y+3,x+4,y+5,paint);}else for(int i=0;i<3;i++)c.drawLine(x-6,y-1+i*6,x+6,y-1+i*6,paint);}
 void draw(Canvas c,float height,Bitmap background,Bitmap title,Bitmap king){
  Layout l=new Layout(height);float u=l.unit;c.save();c.clipRect(0,0,400,height);bitmap(c,background,new RectF(0,0,400,height),true);bitmap(c,title,new RectF(25,l.titleY-65,375,l.titleY+65),false);
  panel(c,l.study,true,background);float cy=l.study.centerY();paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*u);paint.setShader(new LinearGradient(35,cy-50*u,120,cy+50*u,new int[]{0xffd69c36,0xffffedb0,0xffdca846,0xffa1641f},null,Shader.TileMode.CLAMP));c.drawArc(39,cy-48*u,131,cy+48*u,45,282,false,paint);paint.setStrokeWidth(1.5f);c.drawArc(34,cy-53*u,136,cy+53*u,28,303,false,paint);words(c,"検討モード",147,l.study.top+69*u,34*u);rule(c,147,354,l.study.top+91*u);words(c,"合法手で検討・自由配置にも切替可",147,l.study.top+114*u,11.4f*u);
  panel(c,l.match,false,background);bitmap(c,king,new RectF(45,l.match.top+8*u,103,l.match.bottom-8*u),false);words(c,"対局モード",124,l.match.top+40*u,26*u);rule(c,124,350,l.match.top+51*u);words(c,"CPUと3分切れ負け",124,l.match.top+72*u,14*u);
  panel(c,l.paste,false,background);document(c,76,l.paste.centerY(),false);words(c,"棋譜を貼り付け",120,l.paste.centerY()+8*u,23*u);
  panel(c,l.archive,false,background);document(c,76,l.archive.centerY(),true);words(c,"過去の棋譜",120,l.archive.centerY()+8*u,22*u);
  paint.reset();paint.setAntiAlias(true);paint.setColor(0xcc240b0b);c.drawRoundRect(new RectF(342,9,388,55),8,8,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(0xffd6b16e);c.drawRoundRect(new RectF(342,9,388,55),8,8,paint);float gx=365,gy=27;paint.setStrokeWidth(1.4f);c.drawCircle(gx,gy,8,paint);c.drawCircle(gx,gy,3,paint);for(int i=0;i<8;i++){double a=Math.PI*i/4;c.drawLine(gx+8*(float)Math.cos(a),gy+8*(float)Math.sin(a),gx+11*(float)Math.cos(a),gy+11*(float)Math.sin(a),paint);}words(c,"設定",gx-10,49,10);c.restore();
 }
}
