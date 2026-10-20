package com.presentsoft.shogi3minutes;
import android.app.*;import android.graphics.*;import android.os.*;import android.util.Base64;import android.view.*;import java.io.*;import java.util.*;
public final class MainActivity extends Activity {
 static final int HOME=0,MATCH=1,STUDY=2;
 static final String BACKGROUND_ART="generated/background_red_lacquer_v191.webp",BOARD_ART="generated/board_v191.webp";
 CoffeeBilling coffee;final Runnable coffeeObserver=()->coffeeChanged();boolean coffeeDialogOpen,launchingArchive;
 AppSettings settings;RecordStore records;boolean archiveErrorShown;
 BoardView view;boolean resumed;AlertDialog choiceDialog;ClipboardKifImport clipboardImport;
 @Override public void onCreate(Bundle b){super.onCreate(b);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);getWindow().setStatusBarColor(0xff400b0c);getWindow().setNavigationBarColor(0xff400b0c);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);coffee=CoffeeBilling.get(this);coffee.registerLaunch();settings=new AppSettings(this);records=new RecordStore(new File(getFilesDir(),"records"));view=new BoardView();SafeWindow.setContent(this,view);clipboardImport=new ClipboardKifImport(this);coffee.observe(coffeeObserver);if(b!=null)view.enterMode(b.getInt("screen_mode",HOME));}
 @Override protected void onResume(){super.onResume();resumed=true;coffee.registerLaunch();launchingArchive=false;view.resumeMode();clipboardImport.resume();coffee.refresh(false);scheduleCoffeeOffer();}
 @Override protected void onPause(){clipboardImport.pause();view.pauseMode();resumed=false;view.save();super.onPause();}
 @Override protected void onStop(){super.onStop();if(!isChangingConfigurations()&&!coffeeDialogOpen&&!launchingArchive)coffee.backgrounded=true;}
 @Override protected void onDestroy(){coffee.remove(coffeeObserver);clipboardImport.close();view.handler.removeCallbacksAndMessages(null);view.ai.destroy();view.sounds.close();if(view.adCard!=null)view.adCard.close();super.onDestroy();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&clipboardImport!=null){clipboardImport.schedule();scheduleCoffeeOffer();}}
 void showChoice(AlertDialog dialog){choiceDialog=dialog;dialog.setOnDismissListener(d->{if(choiceDialog==dialog)choiceDialog=null;clipboardImport.schedule();scheduleCoffeeOffer();});dialog.show();}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("screen_mode",view.mode);view.save();super.onSaveInstanceState(b);}
 @Override public void onBackPressed(){if(view.mode!=HOME)view.enterMode(HOME);else super.onBackPressed();}
 void archiveError(Exception e){android.util.Log.e("Shogi","Record archive failed",e);if(!archiveErrorShown){archiveErrorShown=true;android.widget.Toast.makeText(this,"棋譜を保存できません："+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
 void showSettings(){showChoice(FeedbackSettingsDialog.create(this));}
 void coffeeChanged(){if(view==null)return;view.layoutBoard(view.getWidth(),view.getHeight());view.updateAd();view.requestLayout();int budget=view.matchOptions().nodes();if(view.nodeBudget!=budget){view.cancelCpu();view.nodeBudget=budget;if(resumed&&view.mode==MATCH)view.scheduleCpu();}view.invalidate();}
 void scheduleCoffeeOffer(){if(view==null)return;view.handler.postDelayed(()->{if(!resumed||!hasWindowFocus()||choiceDialog!=null||view.matchSettingsOpen||view.dialogOpen||!coffee.wantsPrompt()||BuildConfig.DEBUG&&getIntent().getBooleanExtra("coffee_test_suppress",false))return;coffee.promptPending=false;showCoffee();},1200);}
 void showCoffee(){if(coffeeDialogOpen)return;coffee.promptPending=false;coffeeDialogOpen=true;view.pauseMode();if(!coffee.fixture)coffee.queryOrConnect();AlertDialog dialog=CoffeeDialog.create(this);showChoice(dialog);dialog.setOnDismissListener(d->{coffeeDialogOpen=false;if(choiceDialog==dialog)choiceDialog=null;if(resumed){view.resumeMode();clipboardImport.schedule();}scheduleCoffeeOffer();});}
 void showArchive(){launchingArchive=true;view.save();startActivityForResult(new android.content.Intent(this,RecordArchiveActivity.class).putExtra("ads_fixture",getIntent().getBooleanExtra("ads_fixture",false)).putExtra("coffee_test_suppress",BuildConfig.DEBUG&&getIntent().getBooleanExtra("coffee_test_suppress",false)),41);}
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request!=41||result!=RESULT_OK||data==null)return;try{SavedRecord record=records.read(data.getStringExtra("record_id"));Runnable open=()->view.openSavedRecord(record);boolean occupied=view.evalHistory.points.size()>1||view.evalHistory.imported||!EvaluationHistory.key(view.study.game).equals(EvaluationHistory.key(new ShogiGame()));if(occupied)showChoice(new AlertDialog.Builder(this).setTitle("検討棋譜の置き換え").setMessage("保存した棋譜を開き、現在の検討内容を置き換えますか？\n保存済みの棋譜は残ります。").setNegativeButton("キャンセル",null).setPositiveButton("開く",(d,k)->open.run()).create());else open.run();}catch(IOException e){showChoice(new AlertDialog.Builder(this).setTitle("棋譜を開けません").setMessage(e.getMessage()).setPositiveButton("閉じる",null).create());}}
 final class BoardView extends android.widget.FrameLayout {
  TestAdCard adCard;int adMode=-1;float interceptDownX,interceptDownY;
  MatchJournal matchJournal;
  int mode=HOME;boolean matchSettingsOpen;ShogiGame match,game;StudySession study;MatchClock clock;boolean matchStarted,started,thinking,dialogOpen,longPressed,dragged;
  boolean freePlacement=getPreferences(MODE_PRIVATE).getBoolean("study_free_placement",false);
  static final int TARGET_FILL=0x99fff3a6,TARGET_EDGE=0xffffd66b,SELECTED_FILL=0x6667baff,SELECTED_EDGE=0xffa0dcff;
  final HomeScreen homeScreen=new HomeScreen();int hapticRequests;boolean lastHapticAccepted;
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final Map<String,Bitmap> images=new HashMap<>();final Map<String,Rect> inkBounds=new HashMap<>();final Handler handler=new Handler(Looper.getMainLooper());
  final String[] normal={"","fu","kyo","kei","gin","kin","kaku","hisha","gyoku"},promoted={"","to","narikyo","narikei","narigin","","uma","ryu"};
  float scale=1,offsetX,H=720,boardY,boardX,boardSize=400,boardHeight=418.6f;static final float BOARD_HEIGHT_RATIO=1283f/1226f;
  float studyScroll,studyContentHeight;static final float ANALYSIS_HEIGHT=286;
  EvaluationHistory evalHistory=new EvaluationHistory();EvaluationHistory.Score displayScore;
  Bitmap pieceAtlas,panelTexture;
  final String[] atlasNames={"fu","kyo","kei","gin","kin","kaku","hisha","gyoku","ou","to","narikyo","narikei","narigin","uma","ryu"};
  final int[][] atlasBodies={{49,35,295,303},{353,35,600,303},{653,35,901,303},{956,35,1205,303},{48,341,297,609},{351,340,602,609},{652,341,901,609},{955,340,1206,608},{48,646,298,915},{349,645,603,915},{652,645,901,915},{954,645,1206,915},{48,948,298,1214},{350,948,603,1214},{651,948,902,1214}};
  final MoveSound sounds=new MoveSound(MainActivity.this);
  final AiEngine ai=AiEngine.get(MainActivity.this);int aiModel=0,nodeBudget=50000;String aiError,lastAiStats="";int nativeReplies;
  int analysisSide=getPreferences(MODE_PRIVATE).getInt("study_analysis_side",1),analysisRevision;boolean analyzing;
  int analysisUpdates;long lastAnalysisSaved;String analysisStatus="局面を解析します",analyzedPosition="";StudyAnalysis.Result analysisResult;ShogiGame analysisBoard;List<String> analysisMoves=Collections.emptyList();
  final Runnable analysisTask=()->startAnalysis();
  int selectedR=-1,selectedC=-1,selectedHand,selectedSide=1;ShogiGame.Move lastMove;List<ShogiGame.Move> highlights=Collections.emptyList(),analysisRoots=Collections.emptyList();
  final Runnable ticker=new Runnable(){long displayed=Long.MIN_VALUE;public void run(){if(!resumed||mode!=MATCH||matchSettingsOpen||coffeeDialogOpen)return;clock.settle(SystemClock.elapsedRealtime());long signature=((game.remaining[0]+999)/1000)*10000+((game.remaining[1]+999)/1000)*10+game.winner+2;if(signature!=displayed){displayed=signature;if(game.winner!=0)save();invalidate();}handler.postDelayed(this,50);}};
  final Runnable cpu=()->startSearch();final GestureDetector gestures;
  BoardView(){super(MainActivity.this);setWillNotDraw(false);setClipChildren(true);setBackgroundColor(0xff400b0c);setContentDescription("紅将棋3分・検討モード・対局モード・棋譜を貼り付け・過去の棋譜・設定");restore();setVerticalScrollBarEnabled(true);game=match;nodeBudget=matchOptions().nodes();clock=new MatchClock(game);
   for(String s:new String[]{BACKGROUND_ART,BOARD_ART,"ui/timer_panel_03_00.png","ui/piece_tray_fu_kei_gin.png","ui/highlight_selected_gold.png","ui/highlight_move_target_pink.png"})load(s);
   for(int n=1;n<=8;n++)load("pieces/normal/"+normal[n]+".png");load("pieces/normal/ou.png");for(int n=1;n<=7;n++)if(!promoted[n].isEmpty())load("pieces/promoted/"+promoted[n]+".png");for(int n=0;n<10;n++)load("digits/"+n+".png");load("digits/colon.png");
   gestures=new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(maxStudyScroll()<=0)return false;dragged=true;scrollStudy(dy/scale);return true;}@Override public void onLongPress(MotionEvent e){if(mode!=STUDY||!freePlacement)return;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(cell(r,c).contains(x,y)){int piece=game.board[r][c],base=ShogiGame.base(piece);if(piece!=0&&base!=5&&base!=8){longPressed=true;promotionEditor(r,c);}return;}}});
  }
  void resumeMode(){if(mode==MATCH&&!matchSettingsOpen&&!coffeeDialogOpen){clock.resume(SystemClock.elapsedRealtime(),started);if(!ai.ready)configureAi();else scheduleCpu();handler.removeCallbacks(ticker);handler.post(ticker);}else if(mode==STUDY)refreshAnalysis();invalidate();}
  void pauseMode(){if(mode==MATCH)clock.pause(SystemClock.elapsedRealtime());handler.removeCallbacks(ticker);cancelCpu();sounds.pause();}
  void enterMode(int next){if(next<HOME||next>STUDY)next=HOME;pauseMode();save();mode=next;studyScroll=0;game=mode==STUDY?study.game:match;started=mode==STUDY||mode==MATCH&&matchStarted;clock=new MatchClock(game);lastMove=null;layoutBoard(getWidth(),getHeight());updateAd();requestLayout();clearSelection();setContentDescription(mode==HOME?"紅将棋3分・検討モード・対局モード・棋譜を貼り付け・過去の棋譜・設定":mode==STUDY?(freePlacement?"検討モード・自由配置":"検討モード・合法手の移動先表示"):"3分切れ負け将棋盤");if(resumed)resumeMode();}
  void save(){if(mode==MATCH)matchStarted=started;archiveMatch();try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeBoolean(matchStarted);obj.writeObject(match);obj.writeObject(matchJournal);}getPreferences(MODE_PRIVATE).edit().putString("match_v2",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeObject(study);obj.writeObject(evalHistory);}getPreferences(MODE_PRIVATE).edit().putString("study_v1",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();}catch(IOException e){android.util.Log.e("Shogi","Save failed",e);}}
  void restore(){try{String encoded=getPreferences(MODE_PRIVATE).getString("match_v2",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){matchStarted=in.readBoolean();match=(ShogiGame)in.readObject();try{matchJournal=(MatchJournal)in.readObject();}catch(EOFException legacy){}}}catch(Exception e){android.util.Log.e("Shogi","Invalid match",e);}if(match==null)match=new ShogiGame();if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);if(!matchStarted&&match.movesPlayed==0&&matchJournal.options==null){matchJournal.options=configuredMatchOptions();match.remaining[0]=match.remaining[1]=matchOptions().initialMillis();}try{String encoded=getPreferences(MODE_PRIVATE).getString("study_v1",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){study=(StudySession)in.readObject();try{evalHistory=(EvaluationHistory)in.readObject();}catch(EOFException oldVersion){evalHistory=new EvaluationHistory();}}}catch(Exception e){android.util.Log.e("Shogi","Invalid study",e);}if(study==null)study=new StudySession();if(evalHistory==null)evalHistory=new EvaluationHistory();evalHistory.sync(EvaluationHistory.key(study.game));if(evalHistory.imported&&!getPreferences(MODE_PRIVATE).getBoolean("archive_migrated",false))try{records.save(SavedRecord.migrated(evalHistory));getPreferences(MODE_PRIVATE).edit().putBoolean("archive_migrated",true).apply();}catch(IOException e){archiveError(e);}}
  void archiveMatch(){if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);if(!matchStarted&&match.movesPlayed==0&&match.winner==0)return;try{records.save(matchJournal.record(match));}catch(IOException e){archiveError(e);}}
  void applyMatchMove(ShogiGame.Move move,int side){if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);String label=matchJournal.label(game,move);game.apply(move,side);matchJournal.moved(game,label);}
  void cancelCpu(){handler.removeCallbacks(cpu);handler.removeCallbacks(analysisTask);analysisRevision++;ai.cancel();thinking=false;analyzing=false;}
  void refreshAnalysis(){
   if(mode!=STUDY)return;evalHistory.sync(EvaluationHistory.key(game));evalHistory.side(analysisSide);displayScore=null;analysisUpdates=0;cancelCpu();analysisResult=null;analysisBoard=null;analyzedPosition="";analysisStatus="解析中…";analyzing=true;
   if(resumed)handler.postDelayed(analysisTask,180);invalidate();
  }
  void setAnalysisSide(int side){analysisSide=side;getPreferences(MODE_PRIVATE).edit().putInt("study_analysis_side",side).apply();clearSelection();refreshAnalysis();}
  void setFreePlacement(boolean free){freePlacement=free;getPreferences(MODE_PRIVATE).edit().putBoolean("study_free_placement",free).apply();if(mode==STUDY)setContentDescription(free?"検討モード・自由配置":"検討モード・合法手の移動先表示");clearSelection();}
  void studyInputMode(){showChoice(new AlertDialog.Builder(MainActivity.this).setTitle("検討の操作").setSingleChoiceItems(new String[]{"通常移動（合法手のみ）","自由配置（局面の編集）"},freePlacement?1:0,(d,k)->{setFreePlacement(k==1);d.dismiss();}).setNegativeButton("閉じる",null).create());}
  void startAnalysis(){
   if(mode!=STUDY||!resumed)return;final int revision=analysisRevision;final ShogiGame position=StudyAnalysis.snapshot(game,analysisSide);
   String invalid=StudyAnalysis.invalid(position);if(invalid!=null){analyzing=false;analysisStatus=invalid;invalidate();return;}
   List<ShogiGame.Move> legal=position.legal(position.turn);if(legal.isEmpty()){analyzing=false;if(position.checked(position.turn)){displayScore=new EvaluationHistory.Score(-position.turn,true,0);evalHistory.complete(EvaluationHistory.key(position),displayScore);save();}analysisStatus=(analysisSide==1?"先手":"後手")+(position.checked(position.turn)?"の玉が詰みです・候補手なし":"に合法手がありません");invalidate();return;}
   if(!ai.ready){analysisStatus="AI読み込み中…";configureAi();return;}
   final String sfen=UsiPosition.sfen(position);ai.analyzeContinuously(sfen,Math.min(3,legal.size()),(answer,error)->{
    if(mode!=STUDY||!resumed||revision!=analysisRevision||!UsiPosition.sfen(StudyAnalysis.snapshot(game,analysisSide)).equals(sfen))return;
    analysisResult=null;analysisBoard=null;aiError=error;
    if(error!=null||answer==null){analyzing=false;displayScore=null;analysisStatus="AIエラー・タップで再試行";invalidate();return;}
    ArrayList<String> labels=new ArrayList<>();ArrayList<ShogiGame.Move> roots=new ArrayList<>();for(StudyAnalysis.Candidate candidate:answer.candidates){ShogiGame.Move move=UsiPosition.legalMove(position,candidate.move);if(move==null){cancelCpu();displayScore=null;analysisStatus="候補手を検証できません・タップで再試行";invalidate();return;}labels.add(StudyAnalysis.notation(position,move));roots.add(move);}
    if(answer.candidates.isEmpty()){cancelCpu();displayScore=null;analysisStatus="評価値を取得できません・タップで再試行";invalidate();return;}
    analyzing=true;analysisUpdates++;analysisResult=answer;analysisBoard=position;analysisMoves=labels;analysisRoots=roots;analyzedPosition=sfen;analysisStatus="";displayScore=EvaluationHistory.Score.from(answer.candidates.get(0),position.turn);evalHistory.complete(EvaluationHistory.key(position),displayScore);
    long now=SystemClock.uptimeMillis();if(now-lastAnalysisSaved>=2000){lastAnalysisSaved=now;save();}invalidate();
   });
  }
  void showAnalysisError(){choiceDialog=new AlertDialog.Builder(MainActivity.this).setTitle("AI解析のエラー詳細").setMessage(aiError==null?"解析結果を取得できませんでした":aiError).setPositiveButton("再試行",(d,k)->refreshAnalysis()).setNegativeButton("閉じる",null).create();choiceDialog.setOnDismissListener(d->choiceDialog=null);choiceDialog.show();}
  void configureAi(){thinking=false;aiError=null;ai.configure(aiModel,error->{aiError=error;if(mode==STUDY){if(error==null)refreshAnalysis();else{analyzing=false;analysisStatus="AIエラー・タップで再試行";}}else scheduleCpu();invalidate();});invalidate();}
  void startSearch(){if(mode!=MATCH||!resumed||matchSettingsOpen||coffeeDialogOpen||!started||game.turn!=cpuSide()||game.winner!=0||!ai.ready){thinking=false;return;}clock.settle(SystemClock.elapsedRealtime());if(game.winner!=0){thinking=false;save();invalidate();return;}String position=UsiPosition.sfen(game);int limit=Math.max(1,Math.min(matchOptions().millis(),(int)game.remaining[ShogiGame.slot(cpuSide())]));ai.search(position,nodeBudget,limit,(answer,error)->{thinking=false;clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!resumed||matchSettingsOpen||coffeeDialogOpen||!started||game.winner!=0||game.turn!=cpuSide()||!UsiPosition.sfen(game).equals(position))return;if(error==null&&answer!=null){ShogiGame.Move m=UsiPosition.legalMove(game,answer.split("\\|")[0]);if(m!=null){applyMatchMove(m,cpuSide());placementSound();lastMove=m;lastAiStats=answer;nativeReplies++;}else error="AIが合法手を返しませんでした: "+answer;}if(answer==null&&error==null)error="AI応答が空です";if(error!=null){aiError=error;new AlertDialog.Builder(MainActivity.this).setTitle("AIエラー").setMessage(error).setPositiveButton("再試行",(d,k)->configureAi()).show();}save();invalidate();});}
  void scheduleCpu(){if(mode==MATCH&&resumed&&!matchSettingsOpen&&!coffeeDialogOpen&&started&&game.turn==cpuSide()&&game.winner==0&&!thinking&&ai.ready&&aiError==null){thinking=true;handler.postDelayed(cpu,150);}}
  MatchOptions configuredMatchOptions(){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);return new MatchOptions(p.getInt("match_minutes",3),p.getInt("match_human_side",1),p.getInt("match_level",p.getInt("nnue_nodes_v13",50000)<=3000?4:p.getInt("nnue_nodes_v13",50000)<=10000?6:8));}
  MatchOptions matchOptions(){MatchOptions options=matchJournal==null||matchJournal.options==null?new MatchOptions():matchJournal.options;return new MatchOptions(options.minutes,options.humanSide,coffee.level(options.level));}
  int humanSide(){return matchOptions().humanSide;}int cpuSide(){return -humanSide();}
  void applyMatchOptions(MatchOptions next){if(next.level==10&&!coffee.unlocked()){view.handler.post(()->showCoffee());return;}if(next.same(matchOptions()))return;getPreferences(MODE_PRIVATE).edit().putInt("match_minutes",next.minutes).putInt("match_human_side",next.humanSide).putInt("match_level",next.level).apply();restart();}
  void aiSettings(){clock.pause(SystemClock.elapsedRealtime());cancelCpu();matchSettingsOpen=true;if(aiError!=null||!ai.ready)configureAi();AlertDialog dialog=MatchSettingsDialog.create(MainActivity.this);showChoice(dialog);dialog.setOnDismissListener(d->{matchSettingsOpen=false;if(choiceDialog==dialog)choiceDialog=null;if(resumed&&mode==MATCH){clock.resume(SystemClock.elapsedRealtime(),started);handler.removeCallbacks(ticker);handler.post(ticker);scheduleCpu();}clipboardImport.schedule();invalidate();});}
  void aiLicenses(){try{String notice;try(InputStream input=getAssets().open("licenses/YaneuraOu-GPL-3.0.txt");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);notice=bytes.toString("UTF-8");try(InputStream mit=getAssets().open("licenses/TsuyouraOu11micro-MIT.txt")){ByteArrayOutputStream m=new ByteArrayOutputStream();byte[] b=new byte[2048];int z;while((z=mit.read(b))!=-1)m.write(b,0,z);notice+="\n\n"+m.toString("UTF-8");}}
        new AlertDialog.Builder(MainActivity.this).setTitle("エンジン GPL v3 / 評価関数 MIT").setMessage("やねうら王 9.80git (2026-09-14)\nつようら王11micro (2026-10-14) / FV_SCALE 24\n同じ評価関数・1スレッド・Hash 16MiB\n対局：強さに応じ50〜2000ms／検討：局面が変わるまで継続探索\nエンジンはGPL v3、評価関数nn.bin/progress.binはMIT。対応ソース、全パラメーター、権利表記、ビルド手順はソースZIPに同梱。\nhttps://github.com/555734/shogidopa/pull/10\nhttps://github.com/yaneurao/YaneuraOu\nhttps://yaneurao.fanbox.cc/posts/12761272\n\n"+notice).setPositiveButton("閉じる",(x,j)->scheduleCpu()).setOnCancelListener(x->scheduleCpu()).show();
        }catch(IOException e){aiError=e.toString();} }
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
  RectF cell(int r,int col){if(mode==MATCH&&humanSide()==-1){r=8-r;col=8-col;}return gridCell(r,col);}
  RectF gridCell(int r,int col){float w=(boardSize-2*gridInset())/9,h=(boardHeight-2*gridInset())/9;return new RectF(boardX+gridInset()+col*w,boardY+gridInset()+r*h,boardX+gridInset()+(col+1)*w,boardY+gridInset()+(r+1)*h);}
  void board(Canvas c){
   image(c,BOARD_ART,null,new RectF(boardX,boardY,boardX+boardSize,boardY+boardHeight));
   RectF first=gridCell(0,0),last=gridCell(8,8);paint.reset();paint.setAntiAlias(true);paint.setColor(0xff4d2b0a);paint.setStrokeWidth(.75f);
   for(int i=0;i<=9;i++){float x=first.left+i*first.width(),y=first.top+i*first.height();c.drawLine(x,first.top,x,last.bottom,paint);c.drawLine(first.left,y,last.right,y,paint);}
   for(int row:new int[]{3,6})for(int col:new int[]{3,6})c.drawCircle(first.left+col*first.width(),first.top+row*first.height(),1.2f,paint);
  }
  String piecePath(int p){int v=Math.abs(p),b=ShogiGame.base(p);return v>8?"pieces/promoted/"+promoted[b]+".png":"pieces/normal/"+((v==8&&p<0)?"ou":normal[v])+".png";}
  float commonHeight(float w,float h){float aspect=0;for(Rect ink:inkBounds.values())aspect=Math.max(aspect,(float)ink.width()/ink.height());return Math.min(h,w/aspect);}
  float boardPieceHeight(){RectF f=cell(0,0);return commonHeight(f.width()-1.2f,f.height()-1.4f);}
  RectF pieceImageRect(int p,RectF dst,float height){String path=piecePath(p);Bitmap b=load(path);Rect ink=inkBounds.get(path);float factor=height/ink.height(),x=dst.centerX()-ink.exactCenterX()*factor,y=dst.centerY()-ink.exactCenterY()*factor;return new RectF(x,y,x+b.getWidth()*factor,y+b.getHeight()*factor);}
  void drawPiece(Canvas c,int p,RectF dst,float height){c.save();if(p*(mode==MATCH?humanSide():1)<0)c.rotate(180,dst.centerX(),dst.centerY());image(c,piecePath(p),null,pieceImageRect(p,dst,height));c.restore();}
  void boardPiece(Canvas c,int p,RectF dst){drawPiece(c,p,dst,boardPieceHeight());}
  void squareColor(Canvas c,RectF rect,int fill,int edge){
   RectF inside=new RectF(rect);inside.inset(.9f,.9f);paint.reset();paint.setAntiAlias(true);paint.setColor(fill);c.drawRect(inside,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(edge);c.drawRect(inside,paint);
  }
  void selectionColors(Canvas c){
   if(selectedR>=0)squareColor(c,cell(selectedR,selectedC),SELECTED_FILL,SELECTED_EDGE);
   Set<Integer> shown=new HashSet<>();for(ShogiGame.Move m:highlights)if(shown.add(m.row*9+m.col))squareColor(c,cell(m.row,m.col),TARGET_FILL,TARGET_EDGE);
  }
  float handStep(int count){return Math.min(40,260f/Math.max(1,count-1));}
  float handX(int i,int count){return 378-(count-1-i)*handStep(count);}
  int handLimit(){return mode==STUDY?8:7;}
  int handCount(int side){int count=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0)count++;return count;}
  float topHandY(){return boardY-46;}float bottomHandY(){return boardY+boardHeight+2;}
  void hand(Canvas c,int side,float y){
   darkPanel(c,new RectF(4,y,396,y+44));leftText(c,mode==MATCH?(side==humanSide()?"あなた":"CPU")+"・"+(side==1?"先手":"後手"):(side==1?"先手":"後手"),12,y+15,10.5f,Color.WHITE);
   if(mode==MATCH)clockPanel(c,y,game.remaining[side==1?0:1],started&&game.turn==side&&game.winner==0);else leftText(c,"検討・時計なし",12,y+34,8.7f,0xffc5c0b9);
   int count=handCount(side);if(count==0){text(c,"持ち駒なし",302,y+27,9,0xffc5c0b9);return;}
   int i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){float x=handX(i,count);
    if(selectedSide==side&&selectedHand==n)fitted(c,"ui/highlight_selected_gold.png",new RectF(x-19,y,x+19,y+44),false);
    RectF dst=new RectF(x-16,y+3,x+16,y+37);drawPiece(c,side*n,dst,commonHeight(dst.width(),dst.height()));text(c,""+game.hands[side==1?0:1][n],x+13,y+41,9,Color.WHITE);i++;
   }
  }
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutBoard(w,h);}
  void layoutBoard(int w,int h){
   if(w<=0||h<=0)return;scale=w/400f;offsetX=0;H=h/scale;boardSize=400;boardHeight=boardSize*BOARD_HEIGHT_RATIO;boardX=0;boardY=mode==STUDY?46:80;
   studyContentHeight=coffee.unlocked()?Math.max(H,mode==STUDY?analysisY()+ANALYSIS_HEIGHT+8:0):adTop()+adHeight()+16;studyScroll=Math.max(0,Math.min(studyScroll,maxStudyScroll()));
  }
  HomeScreen.Layout homeLayout(){return new HomeScreen.Layout(H);}
  float adTop(){return Math.max(H,mode==STUDY?analysisY()+ANALYSIS_HEIGHT+8:mode==MATCH?bottomHandY()+80:homeLayout().archive.bottom)+16;}
  float adHeight(){return coffee.unlocked()?0:360*getResources().getDisplayMetrics().density/scale;}
  void updateAd(){if(getWidth()==0)return;if(coffee.unlocked()){if(adCard!=null){adCard.close();removeView(adCard);adCard=null;}adMode=-1;return;}if(adMode!=mode){if(adCard!=null){adCard.close();removeView(adCard);}adMode=mode;adCard=new TestAdCard(MainActivity.this,mode==STUDY?"study":mode==MATCH?"match":"home",getIntent().getBooleanExtra("ads_fixture",false));addView(adCard);adCard.load();}int left=Math.round(12*scale),top=Math.round((adTop()-studyScroll)*scale);adCard.measure(MeasureSpec.makeMeasureSpec(getWidth()-left*2,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(adHeight()*scale),MeasureSpec.EXACTLY));adCard.layout(left,top,getWidth()-left,top+Math.round(adHeight()*scale));}
  @Override protected void onMeasure(int w,int h){super.onMeasure(w,h);if(adCard!=null)adCard.measure(MeasureSpec.makeMeasureSpec(Math.max(0,getMeasuredWidth()-Math.round(24*scale)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(adHeight()*scale),MeasureSpec.EXACTLY));}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){updateAd();}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){interceptDownX=e.getX();interceptDownY=e.getY();}if(e.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(e.getY()-interceptDownY)>ViewConfiguration.get(MainActivity.this).getScaledTouchSlop()&&Math.abs(e.getY()-interceptDownY)>Math.abs(e.getX()-interceptDownX)){MotionEvent down=MotionEvent.obtain(e.getDownTime(),e.getDownTime(),MotionEvent.ACTION_DOWN,interceptDownX,interceptDownY,0);gestures.onTouchEvent(down);down.recycle();dragged=true;return true;}return false;}
  float homeMatchY(){return homeLayout().match.top;}float homeStudyY(){return homeLayout().study.top;}
  float homeImportY(){return homeLayout().paste.top;}float homeArchiveY(){return homeLayout().archive.top;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.clipRect(0,0,getWidth(),getHeight());if(mode==HOME){c.translate(offsetX,-studyScroll*scale);c.scale(scale,scale);homeScreen.draw(c,H,load(HomeScreen.BACKGROUND),load(HomeScreen.TITLE),load("pieces/normal/ou.png"));c.restore();return;}fitted(c,BACKGROUND_ART,new RectF(0,0,getWidth(),getHeight()),true);c.translate(offsetX,-studyScroll*scale);c.scale(scale,scale);
   if(mode==MATCH){darkPanel(c,new RectF(4,0,396,32));text(c,"モード",45,22,11,0xfff5e4c8);text(c,"やり直し",145,22,10.5f,Color.WHITE);text(c,"対局設定",253,22,10.5f,Color.WHITE);paint.setColor(0xff285681);c.drawRoundRect(320,4,391,29,3,3,paint);text(c,"投了",355,22,11,Color.WHITE);}
   hand(c,mode==MATCH?cpuSide():-1,topHandY());board(c);if(lastMove!=null&&selectedR<0&&selectedHand==0)fitted(c,"ui/highlight_move_target_pink.png",cell(lastMove.row,lastMove.col),false);selectionColors(c);for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(game.board[r][col]!=0)boardPiece(c,game.board[r][col],cell(r,col));drawCandidateArrows(c);hand(c,mode==MATCH?humanSide():1,bottomHandY());
   if(mode==MATCH){String status=game.winner==2?game.result:game.winner!=0?(game.winner==humanSide()?"勝利":"敗北")+"・"+game.result:!ai.ready?(aiError==null?"AI読み込み中…":"AIエラー・対局設定から再試行"):!started?"タップして対局開始":game.turn==humanSide()?(game.checked(humanSide())?"王手です・あなたの手番":"あなたの手番"):"CPUの手番";text(c,status,200,bottomHandY()+64,12,Color.WHITE);if(!started){paint.setColor(0xe0300909);c.drawRoundRect(75,H/2-35,325,H/2+35,12,12,paint);text(c,ai.ready?"対局開始":"AI読み込み中…",200,H/2+7,25,0xffffdd8b);}}else analysisPanel(c);c.restore();
  }
  int candidateArrowCount(){return mode==STUDY&&analysisResult!=null&&analysisBoard!=null?Math.min(3,analysisRoots.size()):0;}
  void drawCandidateArrows(Canvas c){
   int count=candidateArrowCount();if(count==0)return;
   c.save();RectF first=gridCell(0,0),last=gridCell(8,8);c.clipRect(first.left,first.top,last.right,last.bottom);
   for(int i=count-1;i>=0;i--){ShogiGame.Move m=analysisRoots.get(i);CandidateArrow.draw(c,m.drop>0?null:cell(m.fromRow,m.fromCol),cell(m.row,m.col),i+1,analysisBoard.turn);}
   c.restore();
  }
  float analysisY(){return boardY+boardHeight+50;}
  RectF graphRect(){float y=analysisY();return new RectF(43,y+159,386,y+220);}
  int graphSpan(){return Math.max(10,evalHistory.points.size()-1);}
  float graphPointX(int index){RectF r=graphRect();return r.left+r.width()*index/graphSpan();}
  int graphIndexAt(float x){RectF r=graphRect();return Math.max(0,Math.min(evalHistory.points.size()-1,Math.round((x-r.left)/r.width()*graphSpan())));}
  void jumpStudy(int index){
   if(mode!=STUDY||index==evalHistory.cursor||!study.seek(evalHistory.keys(),index))return;
   cancelCpu();evalHistory.seek(index);game.movesPlayed=evalHistory.currentEdit();game.turn=evalHistory.side();lastMove=null;clearSelection();setAnalysisSide(evalHistory.side());if(resumed)sounds.play();save();
  }
  void importRecord(KifRecord record){
   SavedRecord saved=SavedRecord.imported(record);try{records.save(saved);getPreferences(MODE_PRIVATE).edit().putBoolean("archive_migrated",true).apply();}catch(IOException e){showChoice(new AlertDialog.Builder(MainActivity.this).setTitle("棋譜を保存できません").setMessage(e.getMessage()).setPositiveButton("閉じる",null).create());return;}openSavedRecord(saved);
  }
  void openSavedRecord(SavedRecord record){
   StudySession replacement=new StudySession();if(!replacement.seek(record.positions,record.positions.size()-1)){showChoice(new AlertDialog.Builder(MainActivity.this).setTitle("棋譜を開けません").setMessage("保存された局面を復元できません").setPositiveButton("閉じる",null).create());return;}
   pauseMode();save();setFreePlacement(false);study=replacement;evalHistory=new EvaluationHistory();evalHistory.load(record);mode=STUDY;game=study.game;analysisSide=evalHistory.side();getPreferences(MODE_PRIVATE).edit().putInt("study_analysis_side",analysisSide).apply();game.turn=analysisSide;game.movesPlayed=evalHistory.currentEdit();started=true;clock=new MatchClock(game);lastMove=null;studyScroll=0;clearSelection();layoutBoard(getWidth(),getHeight());updateAd();requestLayout();setContentDescription("検討モード・保存した棋譜");save();if(resumed)resumeMode();
  }
  void studyMoves(){
   String[] labels=new String[evalHistory.points.size()];for(int i=0;i<labels.length;i++){EvaluationHistory.Point p=evalHistory.points.get(i);labels[i]=p.label==null?(evalHistory.firstEdit+i)+"手目":p.label;}
   AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle(evalHistory.title==null?"検討の手順":evalHistory.title).setSingleChoiceItems(labels,evalHistory.cursor,(dialog,index)->{jumpStudy(index);dialog.dismiss();}).setPositiveButton("閉じる",null).create();showChoice(d);
  }
  void leftText(Canvas c,String s,float x,float y,float size,int color){text(c,"",x,y,size,color);paint.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,paint);}
  float maxStudyScroll(){return Math.max(0,studyContentHeight-H);}
  void scrollStudy(float amount){studyScroll=Math.max(0,Math.min(maxStudyScroll(),studyScroll+amount));awakenScrollBars();updateAd();invalidate();}
  @Override protected int computeVerticalScrollRange(){return Math.round(studyContentHeight*scale);}
  @Override protected int computeVerticalScrollOffset(){return Math.round(studyScroll*scale);}
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
   text(c,displayScore==null?(analyzing?"形勢 解析中…":"形勢の目安 ―"):analysisResult==null?"形勢の目安":"形勢の目安  ·  探索中 深さ "+analysisResult.depth+" / "+String.format(Locale.US,"%,d",analysisResult.nodes)+"ノード",200,y+51,8.5f,0xffc9c4bc);
   paint.reset();paint.setColor(0xff5a4c3d);paint.setStrokeWidth(.5f);c.drawLine(13,y+57,387,y+57,paint);
   leftText(c,freePlacement?"候補手 / 自由配置 ▾":"候補手 / 合法手 ▾",13,y+73,10,0xffdfd8cc);text(c,"解析："+(analysisSide==1?"先手":"後手")+" ▾",324,y+73,10,Color.WHITE);
   if(analysisResult==null){text(c,analysisStatus,200,y+96,11,Color.WHITE);text(c,aiError!=null?"タップで原因の詳細と再試行":(freePlacement?"自由配置中・長押しで成り変更":"移動先は金色・選択中は青色"),200,y+121,9,0xffc9c4bc);}
   else{int i=0;for(StudyAnalysis.Candidate candidate:analysisResult.candidates){float row=y+94+i*19;
    leftText(c,(i+1)+". "+analysisMoves.get(i),15,row,11.5f,Color.WHITE);leftText(c,candidate.score(analysisBoard.turn),272,row,11,0xffeee0c7);i++;}
   }
   evaluationGraph(c,y);String[] nav={"初期","前へ","次へ","最終","手順"};for(int i=0;i<nav.length;i++){paint.reset();paint.setColor(0xff49352a);c.drawRoundRect(6+i*79,y+242,79+i*79,y+282,3,3,paint);text(c,nav[i],42+i*79,y+267,12,Color.WHITE);}
  }
  void evaluationGraph(Canvas c,float y){
   leftText(c,"評価値の推移（先手基準）",13,y+150,10,0xffdfd8cc);text(c,evalHistory.currentEdit()+"手目",353,y+150,9,Color.WHITE);text(c,"タップで局面移動",247,y+150,7,0xffbfbab3);
   RectF chart=graphRect();paint.reset();paint.setAntiAlias(true);paint.setColor(0xff172332);c.drawRect(chart,paint);
   paint.setStrokeWidth(.5f);for(int i=0;i<=4;i++){float py=chart.top+chart.height()*i/4;paint.setColor(i==2?0xffa9b3bd:0xff3d4855);if(i==2)paint.setPathEffect(new DashPathEffect(new float[]{2,2},0));c.drawLine(chart.left,py,chart.right,py,paint);paint.setPathEffect(null);}
   for(int i=0;i<=4;i++){float px=chart.left+chart.width()*i/4;paint.setColor(0xff415569);c.drawLine(px,chart.top,px,chart.bottom,paint);}
   text(c,"+2000",24,chart.top+4,7,0xffe6d5b8);text(c,"0",30,chart.centerY()+3,7,0xffe6d5b8);text(c,"−2000",24,chart.bottom,7,0xffe6d5b8);
   int first=0,span=graphSpan();float previousX=0,previousY=0;EvaluationHistory.Score previous=null;boolean any=false;
   c.save();c.clipRect(chart.left-2,chart.top-2,chart.right+2,chart.bottom+2);
   for(int i=first;i<evalHistory.points.size();i++){EvaluationHistory.Score score=evalHistory.points.get(i).score;
    if(score==null){previous=null;continue;}any=true;float px=chart.left+(i-first)*chart.width()/span,py=chart.centerY()-score.graphValue()*chart.height()/4000f;
    paint.reset();paint.setAntiAlias(true);paint.setColor(0xff65c9ff);paint.setStrokeWidth(1.8f);
    if(previous!=null){if(score.bound!=0||previous.bound!=0)paint.setPathEffect(new DashPathEffect(new float[]{3,2},0));c.drawLine(previousX,previousY,px,py,paint);paint.setPathEffect(null);}
    c.drawCircle(px,py,i==evalHistory.cursor?2.6f:1.4f,paint);previousX=px;previousY=py;previous=score;
   }
   float cursorX=chart.left+(evalHistory.cursor-first)*chart.width()/span;paint.reset();paint.setColor(0xff81d8ed);paint.setStrokeWidth(1);c.drawLine(cursorX,chart.top,cursorX,chart.bottom,paint);c.restore();
   if(!any)text(c,"解析結果がここに記録されます",chart.centerX(),chart.centerY()+3,9,0xffcbd5df);
   leftText(c,(evalHistory.firstEdit+first)+"手",chart.left,y+233,8,0xfff2d4a8);int middle=span/2;text(c,(evalHistory.firstEdit+first+middle)+"手",chart.left+chart.width()*middle/span,y+233,8,0xfff2d4a8);text(c,(evalHistory.firstEdit+first+span)+"手",372,y+233,8,0xfff2d4a8);
  }
  void placementSound(){if(resumed&&mode!=HOME){sounds.play();if(settings.vibration()&&(mode==STUDY||game.turn==cpuSide())){hapticRequests++;lastHapticAccepted=performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}}}
  void clearSelection(){selectedR=selectedC=-1;selectedHand=0;highlights=Collections.emptyList();invalidate();}
  void select(){
   ArrayList<ShogiGame.Move> list=new ArrayList<>();int side=mode==MATCH?humanSide():selectedSide;
   if(mode==STUDY&&freePlacement){for(int r=0;r<9;r++)for(int c=0;c<9;c++){
    if(selectedHand>0){if(game.hands[ShogiGame.slot(side)][selectedHand]>0&&game.board[r][c]==0)list.add(new ShogiGame.Move(-1,-1,r,c,false,selectedHand));}
    else if(study.canMove(selectedR,selectedC,r,c))list.add(new ShogiGame.Move(selectedR,selectedC,r,c,false,0));
   }}else for(ShogiGame.Move m:game.legal(side))if(selectedHand>0?m.drop==selectedHand:m.drop==0&&m.fromRow==selectedR&&m.fromCol==selectedC)list.add(m);
   highlights=list;invalidate();
  }
  void move(ShogiGame.Move m){clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!started||game.winner!=0||game.turn!=humanSide())return;applyMatchMove(m,humanSide());placementSound();lastMove=m;clearSelection();save();scheduleCpu();}
  void promotionChoice(Runnable promote,Runnable plain){
   dialogOpen=true;AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle("成りますか？")
    .setPositiveButton("成る",(x,k)->promote.run()).setNegativeButton("成らない",(x,k)->plain.run()).setNeutralButton("キャンセル",null).create();
   d.setOnDismissListener(x->{dialogOpen=false;if(choiceDialog==d)choiceDialog=null;invalidate();});choiceDialog=d;d.show();
  }
  void choose(int r,int col){
   if(selectedHand==0&&selectedR==r&&selectedC==col){clearSelection();return;}
   ArrayList<ShogiGame.Move> list=new ArrayList<>();for(ShogiGame.Move m:highlights)if(m.row==r&&m.col==col)list.add(m);
   if(!list.isEmpty()){
    ShogiGame.Move pro=null,plain=null;for(ShogiGame.Move m:list){if(m.promote)pro=m;else plain=m;}
    if(pro!=null&&plain!=null){ShogiGame.Move promoted=pro,unpromoted=plain;promotionChoice(()->move(promoted),()->move(unpromoted));}
    else move(list.get(0));return;
   }
   if(ShogiGame.side(game.board[r][col])==humanSide()){selectedR=r;selectedC=col;selectedHand=0;selectedSide=humanSide();select();}else clearSelection();
  }
  boolean commitStudyMove(int sr,int sc,int r,int c,int drop,int side,boolean promote){
   if(mode!=STUDY)return false;
   if(!freePlacement){ShogiGame.Move requested=new ShogiGame.Move(sr,sc,r,c,promote,drop);boolean legal=false;if(side==analysisSide)for(ShogiGame.Move candidate:game.legal(side))if(ShogiGame.same(candidate,requested)){legal=true;break;}if(!legal)return false;}
   boolean changed=drop>0?study.drop(side,drop,r,c):study.move(sr,sc,r,c,promote);
   if(changed){placementSound();lastMove=new ShogiGame.Move(sr,sc,r,c,promote,drop);int mover=ShogiGame.side(game.board[r][c]);evalHistory.advance(EvaluationHistory.key(game));game.movesPlayed=evalHistory.currentEdit();game.turn=-mover;setAnalysisSide(-mover);save();}return changed;
  }
  void chooseStudy(int r,int c){
   int sr=selectedR,sc=selectedC;
   if(selectedHand==0&&sr==r&&sc==c){clearSelection();return;}
   if(!freePlacement){
    ArrayList<ShogiGame.Move> targets=new ArrayList<>();for(ShogiGame.Move m:highlights)if(m.row==r&&m.col==c)targets.add(m);
    if(!targets.isEmpty()){
     ShogiGame.Move pro=null,plain=null;for(ShogiGame.Move m:targets){if(m.promote)pro=m;else plain=m;}
     int side=selectedSide;if(pro!=null&&plain!=null){ShogiGame.Move p=pro,n=plain;promotionChoice(()->commitStudyMove(p.fromRow,p.fromCol,p.row,p.col,p.drop,side,true),()->commitStudyMove(n.fromRow,n.fromCol,n.row,n.col,n.drop,side,false));}
     else{ShogiGame.Move m=targets.get(0);commitStudyMove(m.fromRow,m.fromCol,m.row,m.col,m.drop,side,m.promote);}return;
    }
    if(ShogiGame.side(game.board[r][c])==analysisSide){selectedR=r;selectedC=c;selectedHand=0;selectedSide=analysisSide;select();}else clearSelection();return;
   }
   if(selectedHand>0&&commitStudyMove(sr,sc,r,c,selectedHand,selectedSide,false))return;
   if(sr>=0&&study.canMove(sr,sc,r,c)){
    if(study.canPromoteOnMove(sr,sc,r,c))promotionChoice(()->commitStudyMove(sr,sc,r,c,0,selectedSide,true),()->commitStudyMove(sr,sc,r,c,0,selectedSide,false));
    else commitStudyMove(sr,sc,r,c,0,selectedSide,false);return;
   }
   if(game.board[r][c]!=0){selectedR=r;selectedC=c;selectedHand=0;selectedSide=ShogiGame.side(game.board[r][c]);select();}else clearSelection();
  }
  void promotionEditor(int r,int c){dialogOpen=true;String label=Math.abs(game.board[r][c])>8?"成りを戻す":"成る";AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle("駒の変更").setNegativeButton("キャンセル",null).setPositiveButton(label,(x,k)->{study.togglePromotion(r,c);evalHistory.advance(EvaluationHistory.key(game));clearSelection();save();refreshAnalysis();}).create();d.setOnDismissListener(x->{dialogOpen=false;choiceDialog=null;invalidate();});choiceDialog=d;d.show();}
  void restart(){cancelCpu();if(mode==STUDY){study.reset();evalHistory.reset(EvaluationHistory.key(game));studyScroll=0;}else{save();match.restart();matchJournal=new MatchJournal(match);matchJournal.options=configuredMatchOptions();match.remaining[0]=match.remaining[1]=matchOptions().initialMillis();nodeBudget=matchOptions().nodes();matchJournal.options=matchOptions();matchStarted=false;started=false;clock=new MatchClock(game);}lastMove=null;clearSelection();save();if(mode==STUDY)refreshAnalysis();}
  boolean tapHand(float x,float y,int side,float top){if(y<top||y>=top+44)return false;int count=handCount(side),i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){if(Math.abs(x-handX(i,count))<handStep(count)/2){if(mode==STUDY&&!freePlacement&&(side!=analysisSide||n==ShogiGame.OU)||selectedR<0&&selectedHand==n&&selectedSide==side){clearSelection();return true;}selectedR=selectedC=-1;selectedHand=n;selectedSide=side;select();return true;}i++;}return false;}
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_CANCEL){gestures.onTouchEvent(e);clearSelection();return true;}if(e.getAction()==MotionEvent.ACTION_DOWN){longPressed=false;dragged=false;}gestures.onTouchEvent(e);if(e.getAction()!=MotionEvent.ACTION_UP)return true;performClick();if(dialogOpen||longPressed||dragged)return true;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;
   if(mode==HOME){HomeScreen.Layout l=homeLayout();if(l.settings.contains(x,y))showSettings();else if(l.study.contains(x,y))enterMode(STUDY);else if(l.match.contains(x,y))enterMode(MATCH);else if(l.paste.contains(x,y))clipboardImport.paste();else if(l.archive.contains(x,y))showArchive();return true;}
   if(mode==MATCH)clock.settle(SystemClock.elapsedRealtime());if(mode==MATCH&&y<32){if(x<90){enterMode(HOME);return true;}if(x<200){new AlertDialog.Builder(MainActivity.this).setMessage("対局をやり直しますか？").setNegativeButton("キャンセル",null).setPositiveButton("やり直す",(d,k)->restart()).show();}else if(x<300)aiSettings();else if(started&&game.winner==0)new AlertDialog.Builder(MainActivity.this).setMessage("投了しますか？").setNegativeButton("戻る",null).setPositiveButton("投了",(d,k)->{clock.settle(SystemClock.elapsedRealtime());game.resign(humanSide());clearSelection();save();}).show();return true;}
   if(mode==STUDY&&y>=analysisY()&&y<=analysisY()+ANALYSIS_HEIGHT){if(y>=analysisY()+242&&y<=analysisY()+282){int action=(int)((x-6)/79);if(x>=6&&x<395){if(action==0)jumpStudy(0);else if(action==1)jumpStudy(evalHistory.cursor-1);else if(action==2)jumpStudy(evalHistory.cursor+1);else if(action==3)jumpStudy(evalHistory.points.size()-1);else studyMoves();}return true;}if(graphRect().contains(x,y)){jumpStudy(graphIndexAt(x));return true;}if(y>=analysisY()+57&&y<=analysisY()+78&&x<160)studyInputMode();else if(y>=analysisY()+57&&y<=analysisY()+78&&x>=265)setAnalysisSide(-analysisSide);else if(analysisResult==null&&!analyzing){if(aiError!=null)showAnalysisError();else refreshAnalysis();}return true;}
   if(mode==MATCH){if(!started){if(ai.ready){started=true;clock.resume(SystemClock.elapsedRealtime(),true);save();scheduleCpu();invalidate();}return true;}if(game.winner!=0||game.turn!=humanSide()||thinking)return true;}
   for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(cell(r,col).contains(x,y)){if(mode==STUDY)chooseStudy(r,col);else choose(r,col);return true;}
   if(tapHand(x,y,mode==MATCH?humanSide():1,bottomHandY())||mode==STUDY&&tapHand(x,y,-1,topHandY()))return true;clearSelection();return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
 }
}
