package com.presentsoft.shogi3minutes;
import android.graphics.*;

/** Text and hit areas are native. Artwork is independent of device aspect ratio and menu labels. */
final class HomeScreen {
 static final String BACKGROUND="generated/home_background_v112.webp",TITLE="generated/home_title_v112.webp";
 static final class Layout {
  final float unit,titleY;final RectF study,match,online,paste,archive,settings=new RectF(328,4,392,64);
  Layout(float height){unit=Math.min(1,Math.max(.65f,(height-160)/460));float top=Math.max(145*unit,Math.min(height*.395f,height-430*unit-30));titleY=Math.min(height*.20f,top-68*unit);study=new RectF(20,top,380,top+140*unit);top=study.bottom+14*unit;match=new RectF(20,top,380,top+80*unit);top=match.bottom+12*unit;online=new RectF(20,top,380,top+62*unit);top=online.bottom+22*unit;paste=new RectF(36,top,364,top+46*unit);top=paste.bottom+10*unit;archive=new RectF(36,top,364,top+46*unit);}
 }
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Typeface serif=Fonts.black;
 void bitmap(Canvas c,Bitmap image,RectF box,boolean cover){float factor=cover?Math.max(box.width()/image.getWidth(),box.height()/image.getHeight()):Math.min(box.width()/image.getWidth(),box.height()/image.getHeight());float w=image.getWidth()*factor,h=image.getHeight()*factor;paint.reset();paint.setFilterBitmap(true);c.drawBitmap(image,null,new RectF(box.centerX()-w/2,box.centerY()-h/2,box.centerX()+w/2,box.centerY()+h/2),paint);}
 void words(Canvas c,String value,float x,float y,float size){words(c,value,x,y,size,0xfffff2ca,serif);}
 /** Secondary text: smaller, lighter weight and dimmer than the title above it. */
 void caption(Canvas c,String value,float x,float y,float size){words(c,value,x,y,size,0xb3eadfc8,Fonts.medium);}
 void words(Canvas c,String value,float x,float y,float size,int color,Typeface face){paint.reset();paint.setAntiAlias(true);paint.setTypeface(face);paint.setTextSize(size);paint.setColor(color);paint.setShadowLayer(2,0,1,0xff170600);c.drawText(value,x,y,paint);}
 private void panel(Canvas c,RectF rect,boolean primary,Bitmap texture){
  paint.reset();paint.setAntiAlias(true);if(primary)for(int i=4;i>0;i--){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1+i*1.4f);paint.setColor((18-i*3)<<24|0xffb522);c.drawRoundRect(rect,14,14,paint);}
  paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,primary?new int[]{0xff71080a,0xffa90c11,0xff390407}:new int[]{0xff36241a,0xff17110e,0xff23170f},null,Shader.TileMode.CLAMP));c.drawRoundRect(rect,13,13,paint);
  c.save();Path clip=new Path();clip.addRoundRect(rect,13,13,Path.Direction.CW);c.clipPath(clip);
  if(primary){paint.reset();paint.setFilterBitmap(true);paint.setAlpha(100);c.drawBitmap(texture,new Rect(texture.getWidth()/4,texture.getHeight()/3,texture.getWidth()*3/4,texture.getHeight()*2/3),rect,paint);}
  c.restore();paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(primary?1.8f:1.1f);paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,new int[]{0xffffe6a6,0xff8d5720,0xffffde85,0xff8c4f19,0xffffe8ae},null,Shader.TileMode.CLAMP));c.drawRoundRect(rect,13,13,paint);RectF inner=new RectF(rect);inner.inset(2.4f,2.4f);paint.setShader(null);paint.setColor(primary?0xffeab858:0x77ab8956);paint.setStrokeWidth(.5f);c.drawRoundRect(inner,11,11,paint);
 }
 /** Tertiary action: dark ink with a faint hairline, no gold frame. */
 private void quietPanel(Canvas c,RectF rect){paint.reset();paint.setAntiAlias(true);paint.setColor(0xb3140b09);c.drawRoundRect(rect,10,10,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(0x59d8b46a);c.drawRoundRect(rect,10,10,paint);}
 private void rule(Canvas c,float left,float right,float y){paint.reset();paint.setStrokeWidth(.7f);paint.setShader(new LinearGradient(left,y,right,y,new int[]{0xff8a5a26,0xffffe3a0,0xff8a5a26},null,Shader.TileMode.CLAMP));c.drawLine(left,y,right,y,paint);}
 private void document(Canvas c,float x,float y,boolean history){paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xffe4b563);paint.setStrokeWidth(1.8f);Path p=new Path();p.moveTo(x-12,y-17);p.lineTo(x+5,y-17);p.lineTo(x+13,y-9);p.lineTo(x+13,y+17);p.lineTo(x-12,y+17);p.close();c.drawPath(p,paint);c.drawLine(x+5,y-17,x+5,y-9,paint);c.drawLine(x+5,y-9,x+13,y-9,paint);if(history){c.drawCircle(x,y+3,7,paint);c.drawLine(x,y+3,x,y-2,paint);c.drawLine(x,y+3,x+4,y+5,paint);}else for(int i=0;i<3;i++)c.drawLine(x-6,y-1+i*6,x+6,y-1+i*6,paint);}
 /** Two players joined by a direct line: the online panel's mark, drawn like the document marks. */
 private void network(Canvas c,float x,float y,float u){paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xffe4b563);paint.setStrokeWidth(1.8f);float r=7*u;c.drawCircle(x-11,y,r,paint);c.drawCircle(x+11,y,r,paint);c.drawLine(x-11+r,y,x+11-r,y,paint);paint.setStrokeWidth(1.1f);c.drawArc(x-20,y-15*u,x+20,y+15*u,200,140,false,paint);c.drawArc(x-20,y-15*u,x+20,y+15*u,20,140,false,paint);}
 void draw(Canvas c,float height,Bitmap background,Bitmap title,Bitmap king){
  Layout l=new Layout(height);float u=l.unit;c.save();c.clipRect(0,0,400,height);bitmap(c,background,new RectF(0,0,400,height),true);words(c,"AI将棋検討",37,l.titleY+17,48);
  panel(c,l.study,true,background);float cy=l.study.centerY();paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*u);paint.setShader(new LinearGradient(35,cy-50*u,120,cy+50*u,new int[]{0xffd69c36,0xffffedb0,0xffdca846,0xffa1641f},null,Shader.TileMode.CLAMP));c.drawArc(39,cy-48*u,131,cy+48*u,45,282,false,paint);paint.setStrokeWidth(1.5f);c.drawArc(34,cy-53*u,136,cy+53*u,28,303,false,paint);words(c,"検討モード",147,l.study.top+69*u,34*u);caption(c,"合法手で検討・自由配置にも切替可",149,l.study.top+98*u,12*u);
  panel(c,l.match,false,background);bitmap(c,king,new RectF(45,l.match.top+10*u,99,l.match.bottom-10*u),false);words(c,"対局モード",120,l.match.top+39*u,25*u);caption(c,"CPUと対局・時間／先後を設定",120,l.match.top+62*u,11.5f*u);
  panel(c,l.online,false,background);network(c,74,l.online.centerY(),u);words(c,"オンライン対戦",120,l.online.top+30*u,22*u);caption(c,"無料・登録不要のP2P対人戦",120,l.online.top+49*u,11*u);
  quietPanel(c,l.paste);c.save();c.scale(.7f,.7f,66,l.paste.centerY());document(c,66,l.paste.centerY(),false);c.restore();words(c,"棋譜を貼り付け",96,l.paste.centerY()+6*u,16*u,0xffeadfc8,Fonts.bold);
  quietPanel(c,l.archive);c.save();c.scale(.7f,.7f,66,l.archive.centerY());document(c,66,l.archive.centerY(),true);c.restore();words(c,"過去の棋譜",96,l.archive.centerY()+6*u,16*u,0xffeadfc8,Fonts.bold);
  paint.reset();paint.setAntiAlias(true);paint.setColor(0xcc240b0b);c.drawRoundRect(new RectF(342,9,388,55),8,8,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(0xffd6b16e);c.drawRoundRect(new RectF(342,9,388,55),8,8,paint);float gx=365,gy=27;paint.setStrokeWidth(1.4f);c.drawCircle(gx,gy,8,paint);c.drawCircle(gx,gy,3,paint);for(int i=0;i<8;i++){double a=Math.PI*i/4;c.drawLine(gx+8*(float)Math.cos(a),gy+8*(float)Math.sin(a),gx+11*(float)Math.cos(a),gy+11*(float)Math.sin(a),paint);}words(c,"設定",gx-10,49,10);c.restore();
 }
}
