package com.presentsoft.shogi3minutes;
import android.app.*;import android.graphics.*;import android.os.*;import android.util.Base64;import android.view.*;import java.io.*;import java.util.*;
public final class MainActivity extends Activity {
 static final int HOME=0,MATCH=1,STUDY=2;
 BoardView view;boolean resumed;AlertDialog choiceDialog;
 @Override public void onCreate(Bundle b){super.onCreate(b);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);getWindow().setStatusBarColor(0xff400b0c);getWindow().setNavigationBarColor(0xff400b0c);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);view=new BoardView();setContentView(view);if(b!=null)view.enterMode(b.getInt("screen_mode",HOME));}
 @Override protected void onResume(){super.onResume();resumed=true;view.resumeMode();}
 @Override protected void onPause(){view.pauseMode();resumed=false;view.save();super.onPause();}
 @Override protected void onDestroy(){view.handler.removeCallbacksAndMessages(null);view.ai.destroy();view.sounds.close();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("screen_mode",view.mode);view.save();super.onSaveInstanceState(b);}
 @Override public void onBackPressed(){if(view.mode!=HOME)view.enterMode(HOME);else super.onBackPressed();}
 final class BoardView extends View {
  int mode=HOME;ShogiGame match,game;StudySession study;MatchClock clock;boolean matchStarted,started,thinking,dialogOpen,longPressed,dragged;
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final Map<String,Bitmap> images=new HashMap<>();final Map<String,Rect> inkBounds=new HashMap<>();final Handler handler=new Handler(Looper.getMainLooper());
  final String[] normal={"","fu","kyo","kei","gin","kin","kaku","hisha","gyoku"},promoted={"","to","narikyo","narikei","narigin","","uma","ryu"};
  float scale=1,offsetX,H=720,boardY,boardX,boardSize=400,boardHeight=418.6f;static final float BOARD_HEIGHT_RATIO=1283f/1226f;
  float studyScroll,studyContentHeight;static final float ANALYSIS_HEIGHT=240;
  EvaluationHistory evalHistory=new EvaluationHistory();EvaluationHistory.Score displayScore;
  Bitmap pieceAtlas,panelTexture;
  final String[] atlasNames={"fu","kyo","kei","gin","kin","kaku","hisha","gyoku","ou","to","narikyo","narikei","narigin","uma","ryu"};
  final int[][] atlasBodies={{49,35,295,303},{353,35,600,303},{653,35,901,303},{956,35,1205,303},{48,341,297,609},{351,340,602,609},{652,341,901,609},{955,340,1206,608},{48,646,298,915},{349,645,603,915},{652,645,901,915},{954,645,1206,915},{48,948,298,1214},{350,948,603,1214},{651,948,902,1214}};
  final MoveSound sounds=new MoveSound(MainActivity.this);
  final AiEngine ai=AiEngine.get(MainActivity.this);int aiModel=0,nodeBudget=getPreferences(MODE_PRIVATE).getInt("nnue_nodes_v13",50000);String aiError,lastAiStats="";int nativeReplies;
  int analysisSide=getPreferences(MODE_PRIVATE).getInt("study_analysis_side",1),analysisRevision;boolean analyzing;
  String analysisStatus="局面を解析します",analyzedPosition="";StudyAnalysis.Result analysisResult;ShogiGame analysisBoard;List<String> analysisMoves=Collections.emptyList();
  final Runnable analysisTask=()->startAnalysis();
  int selectedR=-1,selectedC=-1,selectedHand,selectedSide=1;ShogiGame.Move lastMove;List<ShogiGame.Move> highlights=Collections.emptyList();
  final Runnable ticker=new Runnable(){long displayed=Long.MIN_VALUE;public void run(){if(!resumed||mode!=MATCH)return;clock.settle(SystemClock.elapsedRealtime());long signature=((game.remaining[0]+999)/1000)*10000+((game.remaining[1]+999)/1000)*10+game.winner+2;if(signature!=displayed){displayed=signature;invalidate();}handler.postDelayed(this,50);}};
  final Runnable cpu=()->startSearch();final GestureDetector gestures;
  BoardView(){super(MainActivity.this);setContentDescription("対局モード・検討モードを選択");restore();setVerticalScrollBarEnabled(true);game=match;clock=new MatchClock(game);
   for(String s:new String[]{"backgrounds/background_red_gold_9x16.png","generated/board_v184.webp","ui/timer_panel_03_00.png","ui/piece_tray_fu_kei_gin.png","ui/highlight_selected_gold.png","ui/highlight_move_target_pink.png"})load(s);
   for(int n=1;n<=8;n++)load("pieces/normal/"+normal[n]+".png");load("pieces/normal/ou.png");for(int n=1;n<=7;n++)if(!promoted[n].isEmpty())load("pieces/promoted/"+promoted[n]+".png");for(int n=0;n<10;n++)load("digits/"+n+".png");load("digits/colon.png");
   gestures=new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(mode!=STUDY||maxStudyScroll()<=0)return false;dragged=true;scrollStudy(dy/scale);return true;}@Override public void onLongPress(MotionEvent e){if(mode!=STUDY)return;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(cell(r,c).contains(x,y)){int piece=game.board[r][c],base=ShogiGame.base(piece);if(piece!=0&&base!=5&&base!=8){longPressed=true;promotionEditor(r,c);}return;}}});
  }
  void resumeMode(){if(mode==MATCH){clock.resume(SystemClock.elapsedRealtime(),started);if(!ai.ready)configureAi();else scheduleCpu();handler.removeCallbacks(ticker);handler.post(ticker);}else if(mode==STUDY)refreshAnalysis();invalidate();}
  void pauseMode(){if(mode==MATCH)clock.pause(SystemClock.elapsedRealtime());handler.removeCallbacks(ticker);cancelCpu();sounds.pause();}
  void enterMode(int next){if(next<HOME||next>STUDY)next=HOME;pauseMode();save();mode=next;studyScroll=0;game=mode==STUDY?study.game:match;started=mode==STUDY||mode==MATCH&&matchStarted;clock=new MatchClock(game);lastMove=null;layoutBoard(getWidth(),getHeight());clearSelection();setContentDescription(mode==HOME?"対局モード・検討モードを選択":mode==STUDY?"検討モード・先後自由な駒配置":"3分切れ負け将棋盤");if(resumed)resumeMode();}
  void save(){if(mode==MATCH)matchStarted=started;try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeBoolean(matchStarted);obj.writeObject(match);}getPreferences(MODE_PRIVATE).edit().putString("match_v2",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeObject(study);obj.writeObject(evalHistory);}getPreferences(MODE_PRIVATE).edit().putString("study_v1",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();}catch(IOException e){android.util.Log.e("Shogi","Save failed",e);}}
  void restore(){try{String encoded=getPreferences(MODE_PRIVATE).getString("match_v2",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){matchStarted=in.readBoolean();match=(ShogiGame)in.readObject();}}catch(Exception e){android.util.Log.e("Shogi","Invalid match",e);}if(match==null)match=new ShogiGame();try{String encoded=getPreferences(MODE_PRIVATE).getString("study_v1",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){study=(StudySession)in.readObject();try{evalHistory=(EvaluationHistory)in.readObject();}catch(EOFException oldVersion){evalHistory=new EvaluationHistory();}}}catch(Exception e){android.util.Log.e("Shogi","Invalid study",e);}if(study==null)study=new StudySession();if(evalHistory==null)evalHistory=new EvaluationHistory();evalHistory.sync(EvaluationHistory.key(study.game));}
  void cancelCpu(){handler.removeCallbacks(cpu);handler.removeCallbacks(analysisTask);analysisRevision++;ai.cancel();thinking=false;analyzing=false;}
  void refreshAnalysis(){
   if(mode!=STUDY)return;evalHistory.sync(EvaluationHistory.key(game));evalHistory.points.get(evalHistory.cursor).score=null;displayScore=null;cancelCpu();analysisResult=null;analysisBoard=null;analyzedPosition="";analysisStatus="解析中…";analyzing=true;
   if(resumed)handler.postDelayed(analysisTask,180);invalidate();
  }
  void setAnalysisSide(int side){analysisSide=side;getPreferences(MODE_PRIVATE).edit().putInt("study_analysis_side",side).apply();refreshAnalysis();}
  void startAnalysis(){
   if(mode!=STUDY||!resumed)return;final int revision=analysisRevision;final ShogiGame position=StudyAnalysis.snapshot(game,analysisSide);
   String invalid=StudyAnalysis.invalid(position);if(invalid!=null){analyzing=false;analysisStatus=invalid;invalidate();return;}
   List<ShogiGame.Move> legal=position.legal(position.turn);if(legal.isEmpty()){analyzing=false;if(position.checked(position.turn)){displayScore=new EvaluationHistory.Score(-position.turn,true,0);evalHistory.complete(EvaluationHistory.key(position),displayScore);save();}analysisStatus=(analysisSide==1?"先手":"後手")+(position.checked(position.turn)?"の玉が詰みです・候補手なし":"に合法手がありません");invalidate();return;}
   if(!ai.ready){analysisStatus="AI読み込み中…";configureAi();return;}
   final String sfen=UsiPosition.sfen(position);ai.analyze(sfen,nodeBudget,750,Math.min(3,legal.size()),(answer,error)->{
    if(mode!=STUDY||!resumed||revision!=analysisRevision||!UsiPosition.sfen(StudyAnalysis.snapshot(game,analysisSide)).equals(sfen))return;
    analyzing=false;analysisResult=null;analysisBoard=null;aiError=error;
    if(error!=null||answer==null){analysisStatus="AIエラー・タップで再試行";invalidate();return;}
    ArrayList<String> labels=new ArrayList<>();for(StudyAnalysis.Candidate candidate:answer.candidates){ShogiGame.Move move=UsiPosition.legalMove(position,candidate.move);if(move==null){analysisStatus="候補手を検証できません・タップで再試行";invalidate();return;}labels.add(StudyAnalysis.notation(position,move));}
    if(answer.candidates.isEmpty()){analysisStatus="評価値を取得できません・タップで再試行";invalidate();return;}
    analysisResult=answer;analysisBoard=position;analysisMoves=labels;analyzedPosition=sfen;analysisStatus="";displayScore=EvaluationHistory.Score.from(answer.candidates.get(0),position.turn);evalHistory.complete(EvaluationHistory.key(position),displayScore);save();invalidate();
   });
  }
  void showAnalysisError(){choiceDialog=new AlertDialog.Builder(MainActivity.this).setTitle("AI解析のエラー詳細").setMessage(aiError==null?"解析結果を取得できませんでした":aiError).setPositiveButton("再試行",(d,k)->refreshAnalysis()).setNegativeButton("閉じる",null).create();choiceDialog.setOnDismissListener(d->choiceDialog=null);choiceDialog.show();}
  void configureAi(){thinking=false;aiError=null;ai.configure(aiModel,error->{aiError=error;if(mode==STUDY){if(error==null)refreshAnalysis();else{analyzing=false;analysisStatus="AIエラー・タップで再試行";}}else scheduleCpu();invalidate();});invalidate();}
  void startSearch(){if(mode!=MATCH||!resumed||!started||game.turn!=-1||game.winner!=0||!ai.ready){thinking=false;return;}clock.settle(SystemClock.elapsedRealtime());if(game.winner!=0){thinking=false;save();invalidate();return;}String position=UsiPosition.sfen(game);int limit=Math.max(1,Math.min(750,(int)game.remaining[1]));ai.search(position,nodeBudget,limit,(answer,error)->{thinking=false;clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!resumed||!started||game.winner!=0||game.turn!=-1||!UsiPosition.sfen(game).equals(position))return;if(error==null&&answer!=null){ShogiGame.Move m=UsiPosition.legalMove(game,answer.split("\\|")[0]);if(m!=null){game.apply(m,-1);placementSound();lastMove=m;lastAiStats=answer;nativeReplies++;}else error="AIが合法手を返しませんでした: "+answer;}if(answer==null&&error==null)error="AI応答が空です";if(error!=null){aiError=error;new AlertDialog.Builder(MainActivity.this).setTitle("AIエラー").setMessage(error).setPositiveButton("再試行",(d,k)->configureAi()).show();}save();invalidate();});}
  void scheduleCpu(){if(mode==MATCH&&resumed&&started&&game.turn==-1&&game.winner==0&&!thinking&&ai.ready&&aiError==null){thinking=true;handler.postDelayed(cpu,150);}}
    void aiSettings(){
      cancelCpu();String[] choices={"低負荷・3,000ノード","標準・10,000ノード","強い・50,000ノード（初期設定）"};int[] budgets={3000,10000,50000};int selected=nodeBudget==3000?0:nodeBudget==10000?1:2;
      new AlertDialog.Builder(MainActivity.this).setTitle("やねうら王 / つようら王11micro").setSingleChoiceItems(choices,selected,(d,k)->{aiModel=0;nodeBudget=budgets[k];getPreferences(MODE_PRIVATE).edit().putInt("nnue_nodes_v13",nodeBudget).apply();d.dismiss();configureAi();
      }).setNeutralButton("ライセンス",(d,k)->{try{String notice;try(InputStream input=getAssets().open("licenses/YaneuraOu-GPL-3.0.txt");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);notice=bytes.toString("UTF-8");try(InputStream mit=getAssets().open("licenses/TsuyouraOu11micro-MIT.txt")){ByteArrayOutputStream m=new ByteArrayOutputStream();byte[] b=new byte[2048];int z;while((z=mit.read(b))!=-1)m.write(b,0,z);notice+="\n\n"+m.toString("UTF-8");}}
        new AlertDialog.Builder(MainActivity.this).setTitle("エンジン GPL v3 / 評価関数 MIT").setMessage("やねうら王 9.80git (2026-09-14)\nつようら王11micro (2026-10-14) / FV_SCALE 24\n同じ評価関数・1スレッド・最大750ms・Hash 16MiB\nエンジンはGPL v3、評価関数nn.bin/progress.binはMIT。対応ソース、全パラメーター、権利表記、ビルド手順はソースZIPに同梱。\nhttps://github.com/555734/shogidopa/pull/10\nhttps://github.com/yaneurao/YaneuraOu\nhttps://yaneurao.fanbox.cc/posts/12761272\n\n"+notice).setPositiveButton("閉じる",(x,j)->scheduleCpu()).setOnCancelListener(x->scheduleCpu()).show();
        }catch(IOException e){aiError=e.toString();}}).setNegativeButton("閉じる",(d,k)->scheduleCpu()).setOnCancelListener(d->scheduleCpu()).show();
    }
    Bitmap load(String path){Bitmap b=images.get(path);if(b!=null)return b;
      try{if(path.startsWith("pieces/")){if(pieceAtlas==null)try(InputStream in=getAssets().open("generated/pieces_v184.png")){pieceAtlas=BitmapFactory.decodeStream(in);}String name=path.substring(path.lastIndexOf('/')+1,path.length()-4);int index=Arrays.asList(atlasNames).indexOf(name);if(index<0||pieceAtlas==null)throw new IOException("Missing sprite: "+path);int[] body=atlasBodies[index];int w=body[2]-body[0],h=body[3]-body[1];b=Bitmap.createBitmap(pieceAtlas,body[0]-3,body[1]-3,w+6,h+6);images.put(path,b);inkBounds.put(path,new Rect(3,3,w+3,h+3));return b;}
       try(InputStream in=getAssets().open(path.startsWith("generated/")?path:"original/"+path)){b=BitmapFactory.decodeStream(in);}if(b==null)throw new IOException(path);images.put(path,b);
        if(path.startsWith("pieces/")){int w=b.getWidth(),h=b.getHeight();int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);int left=w,top=h,right=0,bottom=0;
          for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((pixels[y*w+x]>>>24)>=80){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);}
          inkBounds.put(path,new Rect(left,top,right,bottom));}
        return b;}
      catch(IOException e){throw new IllegalStateException("Missing image: "+path,e);}}
    void image(Canvas c,String path,Rect src,RectF dst){paint.reset();paint.setAntiAlias(true);paint.setFilterBitmap(true);c.drawBitmap(load(path),src,dst,paint);}
    void fitted(Canvas c,String path,RectF rect,boolean cover){Bitmap b=load(path);
      float f=cover?Math.max(rect.width()/b.getWidth(),rect.height()/b.getHeight()):Math.min(rect.width()/b.getWidth(),rect.height()/b.getHeight());
      float w=b.getWidth()*f,h=b.getHeight()*f;image(c,path,null,new RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2));}
    void text(Canvas c,String s,float x,float y,float size,int color){paint.reset();paint.setAntiAlias(true);paint.setTextSize(size);paint.setColor(color);
      paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setShadowLayer(2,0,1,0xff260600);c.drawText(s,x,y,paint);}
    void tray(Canvas c,float y){tray(c,y,52);}
    void tray(Canvas c,float y,float height){String p="ui/piece_tray_fu_kei_gin.png";
      image(c,p,new Rect(381,194,541,552),new RectF(12,y,35,y+height));image(c,p,new Rect(541,194,601,552),new RectF(35,y,365,y+height));image(c,p,new Rect(1629,194,1789,552),new RectF(365,y,388,y+height));}
    void darkPanel(Canvas c,RectF rect){
      if(panelTexture==null)panelTexture=Bitmap.createBitmap(load("ui/piece_tray_fu_kei_gin.png"),541,258,60,230);
      paint.reset();paint.setAntiAlias(true);paint.setFilterBitmap(true);BitmapShader texture=new BitmapShader(panelTexture,Shader.TileMode.REPEAT,Shader.TileMode.CLAMP);
      Matrix matrix=new Matrix();float f=rect.height()/panelTexture.getHeight();matrix.setScale(f,f);matrix.postTranslate(rect.left,rect.top);texture.setLocalMatrix(matrix);
      paint.setShader(texture);paint.setColorFilter(new PorterDuffColorFilter(0xff655f59,PorterDuff.Mode.MULTIPLY));c.drawRect(rect,paint);
      paint.reset();paint.setColor(0xff5a4c3d);paint.setStrokeWidth(.6f);c.drawLine(rect.left,rect.top,rect.right,rect.top,paint);c.drawLine(rect.left,rect.bottom,rect.right,rect.bottom,paint);
    }
    void clockPanel(Canvas c,float y,long time,boolean active){
      long sec=(Math.max(0,time)+999)/1000;String st=String.format(Locale.US,"%02d:%02d",sec/60,sec%60);
      leftText(c,st,12,y+37,22,Color.WHITE);if(active)text(c,"●",90,y+34,8,0xffffd471);
    }
  float gridInset(){return boardSize*.01f;}
  RectF cell(int r,int col){float w=(boardSize-2*gridInset())/9,h=(boardHeight-2*gridInset())/9;return new RectF(boardX+gridInset()+col*w,boardY+gridInset()+r*h,boardX+gridInset()+(col+1)*w,boardY+gridInset()+(r+1)*h);}
  void board(Canvas c){
   image(c,"generated/board_v184.webp",null,new RectF(boardX,boardY,boardX+boardSize,boardY+boardHeight));
   RectF first=cell(0,0),last=cell(8,8);paint.reset();paint.setAntiAlias(true);paint.setColor(0xff4d2b0a);paint.setStrokeWidth(.75f);
   for(int i=0;i<=9;i++){float x=first.left+i*first.width(),y=first.top+i*first.height();c.drawLine(x,first.top,x,last.bottom,paint);c.drawLine(first.left,y,last.right,y,paint);}
   for(int row:new int[]{3,6})for(int col:new int[]{3,6})c.drawCircle(first.left+col*first.width(),first.top+row*first.height(),1.2f,paint);
  }
  String piecePath(int p){int v=Math.abs(p),b=ShogiGame.base(p);return v>8?"pieces/promoted/"+promoted[b]+".png":"pieces/normal/"+((v==8&&p<0)?"ou":normal[v])+".png";}
  float commonHeight(float w,float h){float aspect=0;for(Rect ink:inkBounds.values())aspect=Math.max(aspect,(float)ink.width()/ink.height());return Math.min(h,w/aspect);}
  float boardPieceHeight(){RectF f=cell(0,0);return commonHeight(f.width()-1.2f,f.height()-1.4f);}
  RectF pieceImageRect(int p,RectF dst,float height){String path=piecePath(p);Bitmap b=load(path);Rect ink=inkBounds.get(path);float factor=height/ink.height(),x=dst.centerX()-ink.exactCenterX()*factor,y=dst.centerY()-ink.exactCenterY()*factor;return new RectF(x,y,x+b.getWidth()*factor,y+b.getHeight()*factor);}
  void drawPiece(Canvas c,int p,RectF dst,float height){c.save();if(p<0)c.rotate(180,dst.centerX(),dst.centerY());image(c,piecePath(p),null,pieceImageRect(p,dst,height));c.restore();}
  void boardPiece(Canvas c,int p,RectF dst){drawPiece(c,p,dst,boardPieceHeight());}
  float handStep(int count){return Math.min(40,260f/Math.max(1,count-1));}
  float handX(int i,int count){return 378-(count-1-i)*handStep(count);}
  int handLimit(){return mode==STUDY?8:7;}
  int handCount(int side){int count=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0)count++;return count;}
  float topHandY(){return boardY-46;}float bottomHandY(){return boardY+boardHeight+2;}
  void hand(Canvas c,int side,float y){
   darkPanel(c,new RectF(4,y,396,y+44));leftText(c,mode==MATCH?(side==1?"あなた・先手":"CPU・後手"):(side==1?"先手":"後手"),12,y+15,10.5f,Color.WHITE);
   if(mode==MATCH)clockPanel(c,y,game.remaining[side==1?0:1],started&&game.turn==side&&game.winner==0);else leftText(c,"検討・時計なし",12,y+34,8.7f,0xffc5c0b9);
   int count=handCount(side);if(count==0){text(c,"持ち駒なし",302,y+27,9,0xffc5c0b9);return;}
   int i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){float x=handX(i,count);
    if(selectedSide==side&&selectedHand==n)fitted(c,"ui/highlight_selected_gold.png",new RectF(x-19,y,x+19,y+44),false);
    RectF dst=new RectF(x-16,y+3,x+16,y+37);drawPiece(c,side*n,dst,commonHeight(dst.width(),dst.height()));text(c,""+game.hands[side==1?0:1][n],x+13,y+41,9,Color.WHITE);i++;
   }
  }
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutBoard(w,h);}
  void layoutBoard(int w,int h){
   if(w<=0||h<=0)return;scale=w/400f;offsetX=0;H=h/scale;boardSize=400;boardHeight=boardSize*BOARD_HEIGHT_RATIO;boardX=0;boardY=80;
   studyContentHeight=Math.max(H,analysisY()+ANALYSIS_HEIGHT+8);studyScroll=Math.max(0,Math.min(studyScroll,maxStudyScroll()));
  }
  float homeMatchY(){return H/2-76;}float homeStudyY(){return H/2+24;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.clipRect(0,0,getWidth(),getHeight());fitted(c,"backgrounds/background_red_gold_9x16.png",new RectF(0,0,getWidth(),getHeight()),true);c.translate(offsetX,mode==STUDY?-studyScroll*scale:0);c.scale(scale,scale);
   if(mode==HOME){text(c,"紅将棋 3分",200,H/2-140,30,0xffffe3ae);tray(c,homeMatchY());text(c,"対局モード",200,homeMatchY()+33,21,Color.WHITE);text(c,"CPUと3分切れ負け",200,homeMatchY()+73,13,Color.WHITE);tray(c,homeStudyY());text(c,"検討モード",200,homeStudyY()+33,21,Color.WHITE);text(c,"先後自由に配置・AI候補手と評価値",200,homeStudyY()+73,13,Color.WHITE);c.restore();return;}
   darkPanel(c,new RectF(4,0,396,32));text(c,"モード",45,22,11,0xfff5e4c8);
   if(mode==MATCH){text(c,"やり直し",145,22,10.5f,Color.WHITE);text(c,"AI設定",253,22,10.5f,Color.WHITE);paint.setColor(0xff285681);c.drawRoundRect(320,4,391,29,3,3,paint);text(c,"投了",355,22,11,Color.WHITE);}
   else{text(c,"初期配置",200,22,11,Color.WHITE);text(c,"一手戻す",345,22,11,Color.WHITE);}
   hand(c,-1,topHandY());board(c);if(lastMove!=null)fitted(c,"ui/highlight_move_target_pink.png",cell(lastMove.row,lastMove.col),false);if(selectedR>=0)fitted(c,"ui/highlight_selected_gold.png",cell(selectedR,selectedC),false);Set<Integer> shown=new HashSet<>();for(ShogiGame.Move m:highlights)if(shown.add(m.row*9+m.col))fitted(c,"ui/highlight_move_target_pink.png",cell(m.row,m.col),false);for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(game.board[r][col]!=0)boardPiece(c,game.board[r][col],cell(r,col));hand(c,1,bottomHandY());
   if(mode==MATCH){String status=game.winner==2?game.result:game.winner!=0?(game.winner==1?"勝利":"敗北")+"・"+game.result:!ai.ready?(aiError==null?"AI読み込み中…":"AIエラー・AI設定から再試行"):!started?"タップして対局開始":game.turn==1?(game.checked(1)?"王手です・あなたの手番":"あなたの手番"):"CPUの手番";text(c,status,200,bottomHandY()+64,12,Color.WHITE);if(!started){paint.setColor(0xe0300909);c.drawRoundRect(75,H/2-35,325,H/2+35,12,12,paint);text(c,ai.ready?"対局開始":"AI読み込み中…",200,H/2+7,25,0xffffdd8b);}}else analysisPanel(c);c.restore();
  }
  float analysisY(){return boardY+boardHeight+50;}
  void leftText(Canvas c,String s,float x,float y,float size,int color){text(c,"",x,y,size,color);paint.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,paint);}
  float maxStudyScroll(){return mode==STUDY?Math.max(0,studyContentHeight-H):0;}
  void scrollStudy(float amount){studyScroll=Math.max(0,Math.min(maxStudyScroll(),studyScroll+amount));awakenScrollBars();invalidate();}
  @Override protected int computeVerticalScrollRange(){return mode==STUDY?Math.round(studyContentHeight*scale):getHeight();}
  @Override protected int computeVerticalScrollOffset(){return mode==STUDY?Math.round(studyScroll*scale):0;}
  @Override protected int computeVerticalScrollExtent(){return getHeight();}
  void analysisTray(Canvas c,float y){darkPanel(c,new RectF(4,y,396,y+ANALYSIS_HEIGHT));}
  void analysisPanel(Canvas c){float y=analysisY();analysisTray(c,y);
   String sente=displayScore==null?"先手 ―":"先手 "+displayScore.percentage(true),gote=displayScore==null?"後手 ―":"後手 "+displayScore.percentage(false);
   leftText(c,sente,13,y+22,17,0xff91d1ff);text(c,gote,302,y+22,17,0xffffb9b3);
   RectF bar=new RectF(13,y+31,387,y+39);paint.reset();paint.setAntiAlias(true);paint.setColor(0xff45484a);c.drawRoundRect(bar,2,2,paint);
   if(displayScore!=null){c.save();Path clip=new Path();clip.addRoundRect(bar,2,2,Path.Direction.CW);c.clipPath(clip);float middle=bar.left+bar.width()*displayScore.senteTenths()/1000f;
    paint.setShader(new LinearGradient(bar.left,0,bar.right,0,0xff247dc4,0xff6bbdf9,Shader.TileMode.CLAMP));c.drawRect(bar.left,bar.top,middle,bar.bottom,paint);
    paint.setShader(new LinearGradient(bar.left,0,bar.right,0,0xffdb3a35,0xffa51d29,Shader.TileMode.CLAMP));c.drawRect(middle,bar.top,bar.right,bar.bottom,paint);paint.setShader(null);c.restore();
    paint.setColor(Color.WHITE);paint.setStrokeWidth(1.3f);c.drawLine(middle,bar.top-2,middle,bar.bottom+2,paint);
   }
   text(c,displayScore==null?(analyzing?"形勢 解析中…":"形勢の目安 ―"):"形勢の目安",200,y+51,8.5f,0xffc9c4bc);
   paint.reset();paint.setColor(0xff5a4c3d);paint.setStrokeWidth(.5f);c.drawLine(13,y+57,387,y+57,paint);
   leftText(c,"候補手 / 評価値は先手基準",13,y+73,10,0xffdfd8cc);text(c,"解析："+(analysisSide==1?"先手":"後手")+" ▾",324,y+73,10,Color.WHITE);
   if(analysisResult==null){text(c,analysisStatus,200,y+96,11,Color.WHITE);text(c,aiError!=null?"タップで原因の詳細と再試行":"成りは移動時に選択・長押しで変更",200,y+121,9,0xffc9c4bc);}
   else{int i=0;for(StudyAnalysis.Candidate candidate:analysisResult.candidates){float row=y+94+i*19;
    leftText(c,(i+1)+". "+analysisMoves.get(i),15,row,11.5f,Color.WHITE);leftText(c,candidate.score(analysisBoard.turn),272,row,11,0xffeee0c7);i++;}
   }
   evaluationGraph(c,y);
  }
  void evaluationGraph(Canvas c,float y){
   leftText(c,"評価値の推移（先手基準）",13,y+150,10,0xffdfd8cc);text(c,"編集 "+evalHistory.currentEdit(),353,y+150,9,Color.WHITE);text(c,"破線＝上下限",247,y+150,7,0xffbfbab3);
   RectF chart=new RectF(43,y+159,386,y+220);paint.reset();paint.setAntiAlias(true);paint.setColor(0xff172332);c.drawRect(chart,paint);
   paint.setStrokeWidth(.5f);for(int i=0;i<=4;i++){float py=chart.top+chart.height()*i/4;paint.setColor(i==2?0xffa9b3bd:0xff3d4855);if(i==2)paint.setPathEffect(new DashPathEffect(new float[]{2,2},0));c.drawLine(chart.left,py,chart.right,py,paint);paint.setPathEffect(null);}
   for(int i=0;i<=4;i++){float px=chart.left+chart.width()*i/4;paint.setColor(0xff415569);c.drawLine(px,chart.top,px,chart.bottom,paint);}
   text(c,"+2000",24,chart.top+4,7,0xffe6d5b8);text(c,"0",30,chart.centerY()+3,7,0xffe6d5b8);text(c,"−2000",24,chart.bottom,7,0xffe6d5b8);
   int first=Math.max(0,evalHistory.cursor-60),span=Math.max(10,evalHistory.cursor-first);float previousX=0,previousY=0;EvaluationHistory.Score previous=null;boolean any=false;
   c.save();c.clipRect(chart.left-2,chart.top-2,chart.right+2,chart.bottom+2);
   for(int i=first;i<=evalHistory.cursor&&i<evalHistory.points.size();i++){EvaluationHistory.Score score=evalHistory.points.get(i).score;
    if(score==null){previous=null;continue;}any=true;float px=chart.left+(i-first)*chart.width()/span,py=chart.centerY()-score.graphValue()*chart.height()/4000f;
    paint.reset();paint.setAntiAlias(true);paint.setColor(0xff65c9ff);paint.setStrokeWidth(1.8f);
    if(previous!=null){if(score.bound!=0||previous.bound!=0)paint.setPathEffect(new DashPathEffect(new float[]{3,2},0));c.drawLine(previousX,previousY,px,py,paint);paint.setPathEffect(null);}
    c.drawCircle(px,py,i==evalHistory.cursor?2.6f:1.4f,paint);previousX=px;previousY=py;previous=score;
   }
   float cursorX=chart.left+(evalHistory.cursor-first)*chart.width()/span;paint.reset();paint.setColor(0xff81d8ed);paint.setStrokeWidth(1);c.drawLine(cursorX,chart.top,cursorX,chart.bottom,paint);c.restore();
   if(!any)text(c,"解析結果がここに記録されます",chart.centerX(),chart.centerY()+3,9,0xffcbd5df);
   leftText(c,"編集 "+(evalHistory.firstEdit+first),chart.left,y+233,8,0xfff2d4a8);text(c,"編集 "+String.format(Locale.US,"%.1f",evalHistory.firstEdit+first+span/2.0),chart.centerX(),y+233,8,0xfff2d4a8);text(c,"編集 "+(evalHistory.firstEdit+first+span),359,y+233,8,0xfff2d4a8);
  }
  void placementSound(){if(resumed&&mode!=HOME)sounds.play();}
  void clearSelection(){selectedR=selectedC=-1;selectedHand=0;highlights=Collections.emptyList();invalidate();}
  void select(){ArrayList<ShogiGame.Move> list=new ArrayList<>();for(ShogiGame.Move m:game.legal(1))if(selectedHand>0?m.drop==selectedHand:m.drop==0&&m.fromRow==selectedR&&m.fromCol==selectedC)list.add(m);highlights=list;invalidate();}
  void move(ShogiGame.Move m){clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!started||game.winner!=0||game.turn!=1)return;game.apply(m,1);placementSound();lastMove=m;clearSelection();save();scheduleCpu();}
  void promotionChoice(Runnable promote,Runnable plain){
   dialogOpen=true;AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle("成りますか？")
    .setPositiveButton("成る",(x,k)->promote.run()).setNegativeButton("成らない",(x,k)->plain.run()).setNeutralButton("キャンセル",null).create();
   d.setOnDismissListener(x->{dialogOpen=false;if(choiceDialog==d)choiceDialog=null;invalidate();});choiceDialog=d;d.show();
  }
  void choose(int r,int col){
   ArrayList<ShogiGame.Move> list=new ArrayList<>();for(ShogiGame.Move m:highlights)if(m.row==r&&m.col==col)list.add(m);
   if(!list.isEmpty()){
    ShogiGame.Move pro=null,plain=null;for(ShogiGame.Move m:list){if(m.promote)pro=m;else plain=m;}
    if(pro!=null&&plain!=null){ShogiGame.Move promoted=pro,unpromoted=plain;promotionChoice(()->move(promoted),()->move(unpromoted));}
    else move(list.get(0));return;
   }
   if(game.board[r][col]>0){selectedR=r;selectedC=col;selectedHand=0;selectedSide=1;select();}else clearSelection();
  }
  boolean commitStudyMove(int sr,int sc,int r,int c,int drop,int side,boolean promote){
   if(mode!=STUDY)return false;boolean changed=drop>0?study.drop(side,drop,r,c):study.move(sr,sc,r,c,promote);
   if(changed){placementSound();lastMove=new ShogiGame.Move(sr,sc,r,c,promote,drop);int mover=ShogiGame.side(game.board[r][c]);clearSelection();evalHistory.advance(EvaluationHistory.key(game));save();setAnalysisSide(-mover);}return changed;
  }
  void chooseStudy(int r,int c){
   int sr=selectedR,sc=selectedC;
   if(selectedHand>0&&commitStudyMove(sr,sc,r,c,selectedHand,selectedSide,false))return;
   if(sr>=0&&study.canMove(sr,sc,r,c)){
    if(study.canPromoteOnMove(sr,sc,r,c))promotionChoice(()->commitStudyMove(sr,sc,r,c,0,selectedSide,true),()->commitStudyMove(sr,sc,r,c,0,selectedSide,false));
    else commitStudyMove(sr,sc,r,c,0,selectedSide,false);return;
   }
   if(game.board[r][c]!=0){if(sr==r&&sc==c){clearSelection();return;}selectedR=r;selectedC=c;selectedHand=0;selectedSide=ShogiGame.side(game.board[r][c]);highlights=Collections.emptyList();invalidate();}else clearSelection();
  }
  void promotionEditor(int r,int c){dialogOpen=true;String label=Math.abs(game.board[r][c])>8?"成りを戻す":"成る";AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle("駒の変更").setNegativeButton("キャンセル",null).setPositiveButton(label,(x,k)->{study.togglePromotion(r,c);evalHistory.advance(EvaluationHistory.key(game));clearSelection();save();refreshAnalysis();}).create();d.setOnDismissListener(x->{dialogOpen=false;choiceDialog=null;invalidate();});choiceDialog=d;d.show();}
  void restart(){cancelCpu();if(mode==STUDY){study.reset();evalHistory.reset(EvaluationHistory.key(game));studyScroll=0;}else{match.restart();matchStarted=false;started=false;clock=new MatchClock(game);}lastMove=null;clearSelection();save();if(mode==STUDY)refreshAnalysis();}
  boolean tapHand(float x,float y,int side,float top){if(y<top||y>=top+44)return false;int count=handCount(side),i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){if(Math.abs(x-handX(i,count))<handStep(count)/2){selectedR=selectedC=-1;selectedHand=n;selectedSide=side;if(mode==MATCH)select();else invalidate();return true;}i++;}return false;}
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){longPressed=false;dragged=false;}gestures.onTouchEvent(e);if(e.getAction()!=MotionEvent.ACTION_UP)return true;performClick();if(dialogOpen||longPressed||dragged)return true;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;
   if(mode==HOME){if(x>=12&&x<=388){if(y>=homeMatchY()&&y<=homeMatchY()+52)enterMode(MATCH);else if(y>=homeStudyY()&&y<=homeStudyY()+52)enterMode(STUDY);}return true;}
   if(mode==MATCH)clock.settle(SystemClock.elapsedRealtime());if(y<32){if(x<90){enterMode(HOME);return true;}if(mode==STUDY){if(x<280)new AlertDialog.Builder(MainActivity.this).setMessage("初期配置に戻しますか？").setNegativeButton("キャンセル",null).setPositiveButton("戻す",(d,k)->restart()).show();else{if(study.undo())evalHistory.undo(EvaluationHistory.key(game));lastMove=null;clearSelection();save();if(mode==STUDY)refreshAnalysis();}return true;}if(x<200){new AlertDialog.Builder(MainActivity.this).setMessage("対局をやり直しますか？").setNegativeButton("キャンセル",null).setPositiveButton("やり直す",(d,k)->restart()).show();}else if(x<300)aiSettings();else if(started&&game.winner==0)new AlertDialog.Builder(MainActivity.this).setMessage("投了しますか？").setNegativeButton("戻る",null).setPositiveButton("投了",(d,k)->{clock.settle(SystemClock.elapsedRealtime());game.resign(1);clearSelection();save();}).show();return true;}
   if(mode==STUDY&&y>=analysisY()&&y<=analysisY()+ANALYSIS_HEIGHT){if(y>=analysisY()+57&&y<=analysisY()+78&&x>=265)setAnalysisSide(-analysisSide);else if(analysisResult==null&&!analyzing){if(aiError!=null)showAnalysisError();else refreshAnalysis();}return true;}
   if(mode==MATCH){if(!started){if(ai.ready){started=true;clock.resume(SystemClock.elapsedRealtime(),true);save();invalidate();}return true;}if(game.winner!=0||game.turn!=1||thinking)return true;}
   for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(cell(r,col).contains(x,y)){if(mode==STUDY)chooseStudy(r,col);else choose(r,col);return true;}
   if(tapHand(x,y,1,bottomHandY())||mode==STUDY&&tapHand(x,y,-1,topHandY()))return true;clearSelection();return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
 }
}



