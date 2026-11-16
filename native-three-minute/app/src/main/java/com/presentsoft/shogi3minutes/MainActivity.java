package com.presentsoft.shogi3minutes;
import android.app.*;import android.graphics.*;import android.os.*;import android.util.Base64;import android.view.*;import java.io.*;import java.util.*;
public final class MainActivity extends Activity {
 static final int HOME=0,MATCH=1,STUDY=2,ONLINE=3;
 /** Online play outlives activity recreation; only leaving the mode or finishing the activity ends the match. */
 static final Handler mainLoop=new Handler(Looper.getMainLooper());static BoardView currentView;
 static final class Online {Netplay net;OnlineGame game;String status="",network="",route="",notice="",roomCode,timeControl="3m";boolean roomPage;int relaysOpen,relaysTotal,rtt=-1,seekMode=-1;boolean direct,connecting;
  /** The account (loaded once per process), the opponent's verified card, whether the session is rated, the round whose card was sent, the last rated result. */
  OnlineAccount account;AccountStore accounts;OnlineIdentity peer;boolean rated;int cardRound;OnlineAccount.Game lastResult;
  /** The bot opponent of the current game (null against a person), and the random-match seek its timer belongs to. */
  OnlineBot bot;long seekToken,seekStarted;}
 static final Online online=new Online();
 /** Netplay callbacks arrive on network threads; state changes only on the main thread and only for the current session. */
 static final class OnlineEvents implements Netplay.Listener {
  Netplay owner;
  void post(Runnable r){mainLoop.post(()->{if(owner==null||online.net!=owner)return;r.run();BoardView v=currentView;if(v!=null)v.onlineUpdated();});}
  public void status(String t){post(()->online.status=t);}
  public void relays(int open,int total){post(()->{online.relaysOpen=open;online.relaysTotal=total;});}
  public void network(String t){post(()->online.network=t);}
  public void matched(int side){post(()->{Netplay n=owner;if(online.bot!=null){n.stopSeeking();return;}online.peer=null;online.cardRound=0;online.lastResult=null;online.rated=n.ratedSession();online.game=new OnlineGame(side,n::send,OnlineGame.millis(n.matchedTimeControl()));online.seekMode=-1;online.notice="";online.direct=false;online.rtt=-1;online.route="";online.game.hello(SystemClock.elapsedRealtime());syncAccount();});}
  public void message(String t){post(()->{if(online.bot!=null)return;if(t.startsWith("ID ")){online.peer=OnlineIdentity.parse(t,owner.sessionId());syncAccount();return;}OnlineGame g=online.game;if(g==null)return;g.rttMillis=online.rtt;BoardEffects.Snapshot before=BoardEffects.Snapshot.of(g.game);int mover=g.game.turn;boolean moved=g.receive(t,SystemClock.elapsedRealtime());syncAccount();if(moved){BoardView v=currentView;if(v!=null)v.remoteMoved(before,mover);}});}
  public void path(boolean d,int rtt,String route){post(()->{online.direct=d;online.rtt=rtt;online.route=route;});}
  public void peerLeft(){post(()->{if(online.bot!=null)return;if(online.game!=null)online.game.peerLeft();online.notice="相手が退出しました";syncAccount();});}
  public void failed(String reason){post(()->online.notice=reason);}
 }
 /**
  * After every change of the online game (main thread): settles rated games, persisting each step so quitting
  * cannot dodge a result, and sends a fresh signed account card at the start of every round.
  */
 static void syncAccount(){OnlineGame g=online.game;Netplay n=online.net;OnlineAccount a=online.account;if(g==null||a==null)return;
  int r=a.settle(online.rated,online.peer,g,System.currentTimeMillis()/1000);
  if(r==OnlineAccount.BEGUN)online.accounts.save(a);else if(r==OnlineAccount.SETTLED){online.lastResult=a.lastSettled;online.accounts.save(a);publishProfile();}
  else if(r==OnlineAccount.ABORTED){online.accounts.save(a);online.notice="対局不成立のためレートは変動しません";}
  byte[] sid=n==null||online.bot!=null?null:n.sessionId();if(sid!=null&&g.round!=online.cardRound){online.cardRound=g.round;n.send(OnlineIdentity.message(a.key,sid,a.name,a.rating,a.games,new java.security.SecureRandom()));}
 }
 /** No person found within OnlineBot.waitMillis: a rated game against the clearly labelled, weak bot. */
 static void startBotGame(OnlineBot.Brain brain){Netplay n=online.net;if(n!=null)n.stopSeeking();online.seekMode=-1;online.seekToken++;online.roomCode=null;online.status="";
  OnlineBot[] holder=new OnlineBot[1];
  OnlineBot bot=new OnlineBot(new OnlineBot.Scheduler(){public void after(long ms,Runnable r){mainLoop.postDelayed(r,ms);}public long now(){return SystemClock.elapsedRealtime();}},m->botMessage(holder[0],m),System.nanoTime(),OnlineBot.levelFor(online.account==null?Rating.INITIAL:online.account.rating),brain);holder[0]=bot;
  online.bot=bot;online.peer=bot.identity();online.cardRound=0;online.lastResult=null;online.rated=true;online.notice="";online.direct=false;online.rtt=-1;online.route="";
  online.game=new OnlineGame(Math.random()<.5?1:-1,bot::fromPlayer,OnlineGame.millis(online.timeControl));bot.game=online.game;online.game.hello(SystemClock.elapsedRealtime());syncAccount();
  BoardView v=currentView;if(v!=null)v.onlineUpdated();}
 /** A bot message, applied exactly like one from a remote player. */
 static void botMessage(OnlineBot bot,String t){if(bot==null||online.bot!=bot||bot.closed)return;OnlineGame g=online.game;if(g==null)return;
  BoardEffects.Snapshot before=BoardEffects.Snapshot.of(g.game);int mover=g.game.turn;boolean moved=g.receive(t,SystemClock.elapsedRealtime());syncAccount();
  BoardView v=currentView;if(v!=null){v.onlineUpdated();if(moved)v.remoteMoved(before,mover);}}
 static void endBot(){online.seekToken++;if(online.bot!=null){online.bot.close();online.bot=null;}}
 /** Publishes a snapshot of the profile in the background when the player allows it; refusing relays are skipped. */
 static void publishProfile(){OnlineAccount a=online.account;if(a==null||!a.publish)return;OnlineAccount copy=OnlineAccount.load(a.secret(),a.json());List<String> relays=Netplay.relays();
  new Thread(()->{try{Profiles.publish(copy,relays,javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),8000);}catch(InterruptedException|RuntimeException ignored){}},"profile-publish").start();}
 static String shortName(String name,int max){return name.codePointCount(0,name.length())<=max?name:name.substring(0,name.offsetByCodePoints(0,max-1))+"…";}
 static final String BACKGROUND_ART="generated/background_red_lacquer_v191.webp",BOARD_ART="generated/board_v191.webp";
 CoffeeBilling coffee;final Runnable coffeeObserver=()->coffeeChanged();boolean coffeeDialogOpen,launchingArchive;
 AppSettings settings;RecordStore records;boolean archiveErrorShown;
 BoardView view;boolean resumed;AlertDialog choiceDialog;ClipboardKifImport clipboardImport;
 @Override public void onCreate(Bundle b){super.onCreate(b);Fonts.load(getAssets());setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);getWindow().setStatusBarColor(0xff400b0c);getWindow().setNavigationBarColor(0xff400b0c);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);coffee=CoffeeBilling.get(this);coffee.registerLaunch();settings=new AppSettings(this);if(online.accounts==null){online.accounts=new AccountStore(this);online.account=online.accounts.load();if(online.accounts.settledAbandoned)publishProfile();}records=new RecordStore(new File(getFilesDir(),"records"));view=new BoardView();SafeWindow.setContent(this,view);updateBack();clipboardImport=new ClipboardKifImport(this);coffee.observe(coffeeObserver);if(b!=null)view.enterMode(b.getInt("screen_mode",HOME));}
 @Override protected void onResume(){super.onResume();resumed=true;coffee.registerLaunch();launchingArchive=false;view.resumeMode();clipboardImport.resume();coffee.refresh(false);scheduleCoffeeOffer();}
 @Override protected void onPause(){clipboardImport.pause();view.pauseMode();resumed=false;view.save();super.onPause();}
 @Override protected void onStop(){super.onStop();if(!isChangingConfigurations()&&!coffeeDialogOpen&&!launchingArchive)coffee.backgrounded=true;}
 @Override protected void onDestroy(){if(isFinishing())view.leaveOnline(true);if(currentView==view)currentView=null;coffee.remove(coffeeObserver);clipboardImport.close();view.handler.removeCallbacksAndMessages(null);view.ai.destroy();view.sounds.close();if(view.adCard!=null)view.adCard.close();super.onDestroy();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&clipboardImport!=null){clipboardImport.schedule();scheduleCoffeeOffer();}}
 void showChoice(AlertDialog dialog){choiceDialog=dialog;dialog.setOnDismissListener(d->{if(choiceDialog==dialog)choiceDialog=null;clipboardImport.schedule();scheduleCoffeeOffer();});dialog.show();}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("screen_mode",view.mode);view.save();super.onSaveInstanceState(b);}
 @Override public void onBackPressed(){if(view.mode!=HOME)handleBack();else super.onBackPressed();}
 /** Back from match, study or online returns to the app's home screen; back on home leaves the app as usual. */
 void handleBack(){if(view.mode==ONLINE)view.onlineBack();else if(view.mode!=HOME)view.enterMode(HOME);updateBack();}
 Object backCallback;
 /** Android 13+ delivers back only to registered callbacks (always on Android 16 with targetSdk 36), so register one while away from home. */
 void updateBack(){boolean away=view!=null&&view.mode!=HOME;if(away==(backCallback!=null))return;if(away)backCallback=BackCompat.register(this,this::handleBack);else{BackCompat.unregister(this,backCallback);backCallback=null;}}
 void archiveError(Exception e){android.util.Log.e("Shogi","Record archive failed",e);if(!archiveErrorShown){archiveErrorShown=true;android.widget.Toast.makeText(this,"棋譜を保存できません："+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
 void showSettings(){showChoice(FeedbackSettingsDialog.create(this));}
 void coffeeChanged(){if(view==null)return;view.layoutBoard(view.getWidth(),view.getHeight());view.updateAd();view.requestLayout();int budget=view.matchOptions().nodes();if(view.nodeBudget!=budget){view.cancelCpu();view.nodeBudget=budget;if(resumed&&view.mode==MATCH)view.scheduleCpu();}view.invalidate();}
 void scheduleCoffeeOffer(){if(view==null)return;view.handler.postDelayed(()->{if(!resumed||!hasWindowFocus()||choiceDialog!=null||view.matchSettingsOpen||view.dialogOpen||view.pick!=null||view.mode==ONLINE||!coffee.wantsPrompt()||BuildConfig.DEBUG&&getIntent().getBooleanExtra("coffee_test_suppress",false))return;coffee.promptPending=false;showCoffee();},1200);}
 void showCoffee(){if(coffeeDialogOpen)return;coffee.promptPending=false;coffeeDialogOpen=true;view.pauseMode();if(!coffee.fixture)coffee.queryOrConnect();AlertDialog dialog=CoffeeDialog.create(this);showChoice(dialog);dialog.setOnDismissListener(d->{coffeeDialogOpen=false;if(choiceDialog==dialog)choiceDialog=null;if(resumed){view.resumeMode();clipboardImport.schedule();}scheduleCoffeeOffer();});}
 /** Opens a saved record in study mode, asking first when it would replace current study work. */
 void openForStudy(SavedRecord record){Runnable open=()->view.openSavedRecord(record);boolean occupied=view.evalHistory.points.size()>1||view.evalHistory.imported||!EvaluationHistory.key(view.study.game).equals(EvaluationHistory.key(new ShogiGame()));if(occupied)showChoice(new AlertDialog.Builder(this).setTitle("検討棋譜の置き換え").setMessage("保存した棋譜を開き、現在の検討内容を置き換えますか？\n保存済みの棋譜は残ります。").setNegativeButton("キャンセル",null).setPositiveButton("開く",(d,k)->open.run()).create());else open.run();}
 void showArchive(){launchingArchive=true;view.save();startActivityForResult(new android.content.Intent(this,RecordArchiveActivity.class).putExtra("ads_fixture",getIntent().getBooleanExtra("ads_fixture",false)).putExtra("coffee_test_suppress",BuildConfig.DEBUG&&getIntent().getBooleanExtra("coffee_test_suppress",false)),41);}
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request!=41||result!=RESULT_OK||data==null)return;try{openForStudy(records.read(data.getStringExtra("record_id")));}catch(IOException e){showChoice(new AlertDialog.Builder(this).setTitle("棋譜を開けません").setMessage(e.getMessage()).setPositiveButton("閉じる",null).create());}}
 final class BoardView extends android.widget.FrameLayout {
  final BoardEffects effects=new BoardEffects();
  final BoardEffects.Geometry geometry=new BoardEffects.Geometry(){
   @Override public RectF cell(int r,int c){return BoardView.this.cell(r,c);}
   @Override public RectF handSlot(int side,int n,int[][] hands){return BoardView.this.handSlot(side,n,hands);}
   @Override public float boardPieceHeight(){return BoardView.this.boardPieceHeight();}
   @Override public float handPieceHeight(){return commonHeight(handPieceW(),handPieceH());}
   @Override public void drawPieceAt(Canvas c,int p,float x,float y,float height,float scaleX){c.save();if(scaleX!=1)c.scale(scaleX,1,x,y);drawPiece(c,p,new RectF(x-20,y-20,x+20,y+20),height);c.restore();}
  };
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
  Bitmap pieceAtlas,panelTexture;BitmapShader panelShader;final Matrix panelMatrix=new Matrix();final PorterDuffColorFilter PANEL_TINT=new PorterDuffColorFilter(0xff655f59,PorterDuff.Mode.MULTIPLY);
  final String[] atlasNames={"fu","kyo","kei","gin","kin","kaku","hisha","gyoku","ou","to","narikyo","narikei","narigin","uma","ryu"};
  final int[][] atlasBodies={{49,35,295,303},{353,35,600,303},{653,35,901,303},{956,35,1205,303},{48,341,297,609},{351,340,602,609},{652,341,901,609},{955,340,1206,608},{48,646,298,915},{349,645,603,915},{652,645,901,915},{954,645,1206,915},{48,948,298,1214},{350,948,603,1214},{651,948,902,1214}};
  final MoveSound sounds=new MoveSound(MainActivity.this);
  final AiEngine ai=AiEngine.get(MainActivity.this);int aiModel=0,nodeBudget=50000;String aiError,lastAiStats="";int nativeReplies;
  int analysisSide=getPreferences(MODE_PRIVATE).getInt("study_analysis_side",1),analysisRevision;boolean analyzing;
  int analysisUpdates;long lastAnalysisSaved;String analysisStatus="局面を解析します",analyzedPosition="";StudyAnalysis.Result analysisResult;ShogiGame analysisBoard;List<String> analysisMoves=Collections.emptyList();
  final Runnable analysisTask=()->startAnalysis();
  int selectedR=-1,selectedC=-1,selectedHand,selectedSide=1;ShogiGame.Move lastMove;List<ShogiGame.Move> highlights=Collections.emptyList(),analysisRoots=Collections.emptyList();
  final Runnable ticker=new Runnable(){long displayed=Long.MIN_VALUE;public void run(){if(!resumed||mode!=MATCH||matchSettingsOpen||coffeeDialogOpen)return;clock.settle(SystemClock.elapsedRealtime());countdownTick();long signature=((game.remaining[0]+999)/1000)*10000+((game.remaining[1]+999)/1000)*10+game.winner+2;if(signature!=displayed){displayed=signature;if(game.winner!=0)save();invalidate();}handler.postDelayed(this,50);}};
  final Runnable cpu=()->startSearch();final GestureDetector gestures;
  BoardView(){super(MainActivity.this);currentView=this;setWillNotDraw(false);setClipChildren(true);setBackgroundColor(0xff400b0c);setContentDescription("紅将棋3分・検討モード・対局モード・オンライン対戦・棋譜を貼り付け・過去の棋譜・設定");restore();setVerticalScrollBarEnabled(true);game=match;nodeBudget=matchOptions().nodes();clock=new MatchClock(game);
   for(String s:new String[]{BACKGROUND_ART,BOARD_ART,"ui/timer_panel_03_00.png","ui/piece_tray_fu_kei_gin.png","ui/highlight_selected_gold.png","ui/highlight_move_target_pink.png"})load(s);
   for(int n=1;n<=8;n++)load("pieces/normal/"+normal[n]+".png");load("pieces/normal/ou.png");for(int n=1;n<=7;n++)if(!promoted[n].isEmpty())load("pieces/promoted/"+promoted[n]+".png");for(int n=0;n<10;n++)load("digits/"+n+".png");load("digits/colon.png");
   gestures=new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(maxStudyScroll()<=0)return false;dragged=true;scrollStudy(dy/scale);return true;}@Override public void onLongPress(MotionEvent e){if(mode!=STUDY||!freePlacement)return;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(cell(r,c).contains(x,y)){int piece=game.board[r][c],base=ShogiGame.base(piece);if(piece!=0&&base!=5&&base!=8){longPressed=true;promotionEditor(r,c);}return;}}});
  }
  void resumeMode(){if(mode==MATCH&&!matchSettingsOpen&&!coffeeDialogOpen){clock.resume(SystemClock.elapsedRealtime(),started);if(!ai.ready)configureAi();else scheduleCpu();handler.removeCallbacks(ticker);handler.post(ticker);}else if(mode==STUDY)refreshAnalysis();else if(mode==ONLINE){handler.removeCallbacks(onlineTicker);handler.post(onlineTicker);}invalidate();}
  void pauseMode(){if(mode==MATCH)clock.pause(SystemClock.elapsedRealtime());handler.removeCallbacks(ticker);handler.removeCallbacks(onlineTicker);cancelCpu();sounds.pause();}
  void enterMode(int next){if(next<HOME||next>ONLINE)next=HOME;pauseMode();save();boolean leftOnline=mode==ONLINE&&next!=ONLINE;if(leftOnline)leaveOnline(true);mode=next;effects.clear();effects.clearOverlays();effects.resetTint();flowStarted=false;updateBack();if(mode==ONLINE&&online.game==null){String tc=getPreferences(MODE_PRIVATE).getString("online_tc","3m");online.timeControl=OnlineGame.validTimeControl(tc)?tc:"3m";}studyScroll=0;game=mode==STUDY?study.game:mode==ONLINE?onlineBoard():match;started=mode==STUDY||mode==MATCH&&matchStarted;clock=new MatchClock(game);lastMove=mode==ONLINE&&online.game!=null?online.game.lastMove:null;layoutBoard(getWidth(),getHeight());updateAd();requestLayout();clearSelection();if(mode==ONLINE)ensureNetplay();setContentDescription(mode==HOME?"紅将棋3分・検討モード・対局モード・オンライン対戦・棋譜を貼り付け・過去の棋譜・設定":mode==STUDY?(freePlacement?"検討モード・自由配置":"検討モード・合法手の移動先表示"):mode==ONLINE?"オンライン対戦":"3分切れ負け将棋盤");if(resumed)resumeMode();if(leftOnline){clipboardImport.schedule();scheduleCoffeeOffer();}}
  void save(){if(mode==MATCH)matchStarted=started;archiveMatch();try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeBoolean(matchStarted);obj.writeObject(match);obj.writeObject(matchJournal);}getPreferences(MODE_PRIVATE).edit().putString("match_v2",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();out=new ByteArrayOutputStream();try(ObjectOutputStream obj=new ObjectOutputStream(out)){obj.writeObject(study);obj.writeObject(evalHistory);}getPreferences(MODE_PRIVATE).edit().putString("study_v1",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit();}catch(IOException e){android.util.Log.e("Shogi","Save failed",e);}}
  void restore(){try{String encoded=getPreferences(MODE_PRIVATE).getString("match_v2",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){matchStarted=in.readBoolean();match=(ShogiGame)in.readObject();try{matchJournal=(MatchJournal)in.readObject();}catch(EOFException legacy){}}}catch(Exception e){android.util.Log.e("Shogi","Invalid match",e);}if(match==null)match=new ShogiGame();if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);if(!matchStarted&&match.movesPlayed==0&&matchJournal.options==null){matchJournal.options=configuredMatchOptions();match.remaining[0]=match.remaining[1]=matchOptions().initialMillis();}try{String encoded=getPreferences(MODE_PRIVATE).getString("study_v1",null);if(encoded!=null)try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT)))){study=(StudySession)in.readObject();try{evalHistory=(EvaluationHistory)in.readObject();}catch(EOFException oldVersion){evalHistory=new EvaluationHistory();}}}catch(Exception e){android.util.Log.e("Shogi","Invalid study",e);}if(study==null)study=new StudySession();if(evalHistory==null)evalHistory=new EvaluationHistory();evalHistory.sync(EvaluationHistory.key(study.game));if(evalHistory.imported&&!getPreferences(MODE_PRIVATE).getBoolean("archive_migrated",false))try{records.save(SavedRecord.migrated(evalHistory));getPreferences(MODE_PRIVATE).edit().putBoolean("archive_migrated",true).apply();}catch(IOException e){archiveError(e);}}
  void archiveMatch(){if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);if(!matchStarted&&match.movesPlayed==0&&match.winner==0)return;try{records.save(matchJournal.record(match));}catch(IOException e){archiveError(e);}}
  BoardEffects.Snapshot applyMatchMove(ShogiGame.Move move,int side){if(matchJournal==null||!matchJournal.matches(match))matchJournal=new MatchJournal(match);String label=matchJournal.label(game,move);BoardEffects.Snapshot before=BoardEffects.Snapshot.of(game);game.apply(move,side);matchJournal.moved(game,label);pieceMoved(move,side,before);return before;}
  /** Starts the visual move effect; the game state is already updated. */
  /** Effect clock; tests may freeze it to photograph one animation frame (no redraw loop while frozen). */
  long frozenFrame=-1;
  long frameTime(){return frozenFrame>=0?frozenFrame:SystemClock.uptimeMillis();}
  void pieceMoved(ShogiGame.Move m,int mover,BoardEffects.Snapshot before){effects.reduced=settings.reducedEffects();effects.move(m,mover,before,BoardEffects.Snapshot.of(game),game.checked(game.turn),game.turn,geometry,SystemClock.uptimeMillis());invalidate();}
  RectF handSlot(int side,int n,int[][] hands){int slot=side==1?0:1,count=0,i=0;for(int k=1;k<=handLimit();k++)if(hands[slot][k]>0||k==n){if(k<n)i++;count++;}float x=handX(i,count);if(mode==ONLINE&&side!=viewSide())x=400-x;float y=side==viewSide()?bottomHandY():topHandY();return handBox(x,y);}
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
  void startSearch(){if(mode!=MATCH||!resumed||matchSettingsOpen||coffeeDialogOpen||!started||game.turn!=cpuSide()||game.winner!=0||!ai.ready){thinking=false;return;}clock.settle(SystemClock.elapsedRealtime());if(game.winner!=0){thinking=false;save();invalidate();return;}String position=UsiPosition.sfen(game);int limit=Math.max(1,Math.min(matchOptions().millis(),(int)game.remaining[ShogiGame.slot(cpuSide())]));ai.search(position,nodeBudget,limit,(answer,error)->{thinking=false;clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!resumed||matchSettingsOpen||coffeeDialogOpen||!started||game.winner!=0||game.turn!=cpuSide()||!UsiPosition.sfen(game).equals(position))return;if(error==null&&answer!=null){ShogiGame.Move m=UsiPosition.legalMove(game,answer.split("\\|")[0]);if(m!=null){placementSound(moveKind(m,applyMatchMove(m,cpuSide())));lastMove=m;lastAiStats=answer;nativeReplies++;}else error="AIが合法手を返しませんでした: "+answer;}if(answer==null&&error==null)error="AI応答が空です";if(error!=null){aiError=error;new AlertDialog.Builder(MainActivity.this).setTitle("AIエラー").setMessage(error).setPositiveButton("再試行",(d,k)->configureAi()).show();}save();invalidate();});}
  void scheduleCpu(){if(mode==MATCH&&resumed&&!matchSettingsOpen&&!coffeeDialogOpen&&started&&game.turn==cpuSide()&&game.winner==0&&!thinking&&ai.ready&&aiError==null){thinking=true;handler.postDelayed(cpu,150);}}
  MatchOptions configuredMatchOptions(){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);return new MatchOptions(p.getInt("match_minutes",3),p.getInt("match_human_side",1),p.getInt("match_level",p.getInt("nnue_nodes_v13",50000)<=3000?4:p.getInt("nnue_nodes_v13",50000)<=10000?6:8));}
  MatchOptions matchOptions(){MatchOptions options=matchJournal==null||matchJournal.options==null?new MatchOptions():matchJournal.options;return new MatchOptions(options.minutes,options.humanSide,coffee.level(options.level));}
  int humanSide(){return matchOptions().humanSide;}int cpuSide(){return -humanSide();}
  void applyMatchOptions(MatchOptions next){if(next.level==10&&!coffee.unlocked()){view.handler.post(()->showCoffee());return;}if(next.same(matchOptions()))return;getPreferences(MODE_PRIVATE).edit().putInt("match_minutes",next.minutes).putInt("match_human_side",next.humanSide).putInt("match_level",next.level).apply();restart();}
  void aiSettings(){clock.pause(SystemClock.elapsedRealtime());cancelCpu();matchSettingsOpen=true;if(aiError!=null||!ai.ready)configureAi();AlertDialog dialog=MatchSettingsDialog.create(MainActivity.this);showChoice(dialog);dialog.setOnDismissListener(d->{matchSettingsOpen=false;if(choiceDialog==dialog)choiceDialog=null;if(resumed&&mode==MATCH){clock.resume(SystemClock.elapsedRealtime(),started);handler.removeCallbacks(ticker);handler.post(ticker);scheduleCpu();}clipboardImport.schedule();invalidate();});}
  void aiLicenses(){try{String notice;try(InputStream input=getAssets().open("licenses/YaneuraOu-GPL-3.0.txt");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);notice=bytes.toString("UTF-8");try(InputStream mit=getAssets().open("licenses/TsuyouraOu11micro-MIT.txt")){ByteArrayOutputStream m=new ByteArrayOutputStream();byte[] b=new byte[2048];int z;while((z=mit.read(b))!=-1)m.write(b,0,z);notice+="\n\n"+m.toString("UTF-8");}}
   for(String[] font:new String[][]{{"Zen Kaku Gothic New","zenkakugothicnew"}})try(InputStream f=getAssets().open("licenses/"+font[1]+"-OFL.txt")){ByteArrayOutputStream m=new ByteArrayOutputStream();byte[] b=new byte[2048];int z;while((z=f.read(b))!=-1)m.write(b,0,z);notice+="\n\nフォント："+font[0]+"（SIL Open Font License 1.1）\n"+m.toString("UTF-8");}
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
      paint.setTypeface(Fonts.bold);paint.setTextAlign(Paint.Align.CENTER);paint.setShadowLayer(1.5f,0,1,0x99160402);c.drawText(s,x,y,paint);}
    void tray(Canvas c,float y){tray(c,y,52);}
    void tray(Canvas c,float y,float height){String p="ui/piece_tray_fu_kei_gin.png";
      image(c,p,new Rect(381,194,541,552),new RectF(12,y,35,y+height));image(c,p,new Rect(541,194,601,552),new RectF(35,y,365,y+height));image(c,p,new Rect(1629,194,1789,552),new RectF(365,y,388,y+height));}
    static final int INK_TOP=0xeb1a0b08,INK_BOTTOM=0xeb0e0504,HAIRLINE=0x66d8b46a,HAIRLINE_LIT=0xffdcb86c;
    /** Lacquer-ink panel: a deep vertical gradient with gold hairlines above and below. */
    void darkPanel(Canvas c,RectF rect){
      paint.reset();paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,INK_TOP,INK_BOTTOM,Shader.TileMode.CLAMP));c.drawRect(rect,paint);paint.setShader(null);
      paint.setColor(HAIRLINE);c.drawRect(rect.left,rect.top,rect.right,rect.top+.8f,paint);c.drawRect(rect.left,rect.bottom-.8f,rect.right,rect.bottom,paint);
    }
    /** Rounded ink card with a gold hairline; lit cards get a brighter edge and an inner rule. */
    void inkCard(Canvas c,RectF r,float radius,boolean lit){
      paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,r.top,0,r.bottom,INK_TOP,INK_BOTTOM,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,paint);paint.setShader(null);
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(lit?1.3f:.9f);paint.setColor(lit?HAIRLINE_LIT:HAIRLINE);c.drawRoundRect(r,radius,radius,paint);
      if(lit){RectF in=new RectF(r);in.inset(3.5f,3.5f);paint.setStrokeWidth(.6f);paint.setColor(0x55dcb86c);c.drawRoundRect(in,Math.max(0,radius-3),Math.max(0,radius-3),paint);}
    }
    static final int PRIMARY=0,SECONDARY=1,DANGER=2;
    /** Buttons: PRIMARY vermilion lacquer, SECONDARY ink, DANGER an outlined vermilion; all with a gold or vermilion hairline. */
    void button(Canvas c,RectF r,int style,String label,float size){
      paint.reset();paint.setAntiAlias(true);float radius=Math.min(7,r.height()/2);
      if(style==PRIMARY){paint.setShader(new LinearGradient(0,r.top,0,r.bottom,CRIMSON[0],CRIMSON[2],Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,paint);paint.setShader(null);}
      else{paint.setColor(style==DANGER?0x40a8321f:0xb3120605);c.drawRoundRect(r,radius,radius,paint);}
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(style==DANGER?1.2f:.9f);paint.setColor(style==PRIMARY?CHAMPAGNE_RIM:style==DANGER?0xffb0503c:0x99b89a62);c.drawRoundRect(r,radius,radius,paint);
      paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.bold);paint.setTextSize(size);paint.setLetterSpacing(.06f);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(style==DANGER?0xfff0c4b4:CREAM);
      Paint.FontMetrics m=paint.getFontMetrics();c.drawText(label,r.centerX(),r.centerY()-(m.ascent+m.descent)/2,paint);
    }
    void clockPanel(Canvas c,float y,long time,boolean active){
      long sec=(Math.max(0,time)+999)/1000;String st=String.format(Locale.US,"%02d:%02d",sec/60,sec%60);
      leftText(c,st,12,y+37,22,Color.WHITE);if(active)text(c,"●",90,y+34,8,0xffffd471);
    }
  float gridInset(){return boardSize*.01f;}
  /** The local player's pieces are at the bottom: the human side in CPU matches, the assigned side online. */
  int viewSide(){return mode==MATCH?humanSide():mode==ONLINE&&online.game!=null?online.game.mySide:1;}
  RectF cell(int r,int col){if(viewSide()==-1){r=8-r;col=8-col;}return gridCell(r,col);}
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
  void drawPiece(Canvas c,int p,RectF dst,float height){c.save();if(p*viewSide()<0)c.rotate(180,dst.centerX(),dst.centerY());image(c,piecePath(p),null,pieceImageRect(p,dst,height));c.restore();}
  void boardPiece(Canvas c,int p,RectF dst){drawPiece(c,p,dst,boardPieceHeight());}
  void squareColor(Canvas c,RectF rect,int fill,int edge){
   RectF inside=new RectF(rect);inside.inset(.9f,.9f);paint.reset();paint.setAntiAlias(true);paint.setColor(fill);c.drawRect(inside,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(edge);c.drawRect(inside,paint);
  }
  static final int LAST_FROM_FILL=0xa0287bff,LAST_FROM_EDGE=0xff1f5fd0,LAST_TO_FILL=0xb4ff8c1a,LAST_TO_EDGE=0xffe25f00;
  /** The previous move stays marked until the next one: its source square in blue, its destination in deep orange. */
  void lastMoveColors(Canvas c){if(lastMove==null)return;if(lastMove.drop==0)squareColor(c,cell(lastMove.fromRow,lastMove.fromCol),LAST_FROM_FILL,LAST_FROM_EDGE);RectF to=cell(lastMove.row,lastMove.col);squareColor(c,to,LAST_TO_FILL,LAST_TO_EDGE);}
  /** Drawn above the pieces so the destination stays visible even under a large piece. */
  void lastMoveFrame(Canvas c){if(lastMove==null)return;RectF to=cell(lastMove.row,lastMove.col);paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3f);paint.setColor(LAST_TO_EDGE);RectF inner=new RectF(to);inner.inset(1.5f,1.5f);c.drawRect(inner,paint);}
  void selectionColors(Canvas c){
   if(selectedR>=0)squareColor(c,cell(selectedR,selectedC),SELECTED_FILL,SELECTED_EDGE);
   Set<Integer> shown=new HashSet<>();for(ShogiGame.Move m:highlights)if(shown.add(m.row*9+m.col))squareColor(c,cell(m.row,m.col),TARGET_FILL,TARGET_EDGE);
  }
  // ---- Match layout: toolbar, opponent panel, board centred vertically, own panel and turn line, all 1.5x ----
  static final float MATCH_BAR=44,MATCH_STATUS=40;float matchPanelH=70;
  float panelH(){return mode==MATCH?matchPanelH:44;}
  float handPieceW(){return mode==MATCH?48:32;}float handPieceH(){return mode==MATCH?51:34;}
  float handStep(int count){if(mode==MATCH)return Math.min(handPieceW()+2,(392-178-handPieceW())/Math.max(1,count-1));return Math.min(40,260f/Math.max(1,count-1));}
  float handX(int i,int count){if(mode==MATCH)return 392-handPieceW()/2-(count-1-i)*handStep(count);return 378-(count-1-i)*handStep(count);}
  float handPieceTop(float y){return mode==MATCH?y+(panelH()-handPieceH()-14)/2:y+3;}
  RectF handBox(float x,float y){float top=handPieceTop(y);return new RectF(x-handPieceW()/2,top,x+handPieceW()/2,top+handPieceH());}
  /** Drawn piece area of piece type n in side's hand panel, or null when that piece is not shown there. */
  RectF handPieceRect(int side,int n){if(mode==ONLINE||n<1||n>handLimit()||game.hands[side==1?0:1][n]<=0)return null;int count=handCount(side),i=0;for(int k=1;k<n;k++)if(game.hands[side==1?0:1][k]>0)i++;float x=handX(i,count),y=side==viewSide()?bottomHandY():topHandY();return handBox(x,y);}
  int handLimit(){return mode==STUDY?8:7;}
  int handCount(int side){int count=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0)count++;return count;}
  float topHandY(){return mode==MATCH?boardY-4-matchPanelH:boardY-46;}float bottomHandY(){return mode==MATCH?boardY+boardHeight+4:boardY+boardHeight+2;}
  /** One confirmation step on the board before resigning. */
  boolean resignConfirm;
  RectF[] resignButtons(){float cy=boardY+boardHeight/2;return new RectF[]{new RectF(70,cy+8,196,cy+52),new RectF(204,cy+8,330,cy+52)};}
  void drawResignConfirm(Canvas c){if(!resignConfirm)return;float cy=boardY+boardHeight/2;inkCard(c,new RectF(50,cy-62,350,cy+66),10,true);
   styledText(c,"投了しますか？",200,cy-18,22,0xfff3e6cf,true,Paint.Align.CENTER,Fonts.black);RectF[] b=resignButtons();
   button(c,b[0],PRIMARY,"投了する",16);button(c,b[1],SECONDARY,"続ける",16);}
  void resignTouch(float x,float y){RectF[] b=resignButtons();resignConfirm=false;if(b[0].contains(x,y)&&started&&game.winner==0){clock.settle(SystemClock.elapsedRealtime());game.resign(humanSide());clearSelection();save();}invalidate();}
 /** Bot moves: level 1 greedy; higher levels the engine with the CPU-match budget one level below. */
  void botMove(ShogiGame g,int level,java.util.function.Consumer<ShogiGame.Move> done){
   if(level<=1||!ai.ready||aiError!=null){done.accept(g.cpuChoice());return;}MatchOptions o=new MatchOptions(3,1,level-1);String position=UsiPosition.sfen(g);
   ai.search(position,o.nodes(),o.millis(),(answer,error)->{ShogiGame.Move m=null;if(error==null&&answer!=null&&UsiPosition.sfen(g).equals(position))m=UsiPosition.legalMove(g,answer.split("\\|")[0]);done.accept(m!=null?m:UsiPosition.sfen(g).equals(position)?g.cpuChoice():null);});}
  /** Ink bar: three quiet text actions divided by hairlines, and 投了 as an outlined vermilion button. */
  void matchToolbar(Canvas c){darkPanel(c,new RectF(0,0,400,MATCH_BAR));
   styledText(c,"‹ モード",45,28.5f,15,0xffeadfc8,false,Paint.Align.CENTER,null);styledText(c,"やり直し",145,28.5f,15,0xffeadfc8,false,Paint.Align.CENTER,null);styledText(c,"対局設定",253,28.5f,15,0xffeadfc8,false,Paint.Align.CENTER,null);
   paint.reset();paint.setColor(0x40d8b46a);for(float x:new float[]{92,199,306})c.drawRect(x,13,x+.8f,MATCH_BAR-13,paint);
   button(c,new RectF(320,7,392,MATCH_BAR-7),DANGER,"投了",15);}
  /** Match player panel: icon, name, clock (red at 30s) and komadai, 1.5x; the side to move is lit, the other dimmed. */
  void matchPanel(Canvas c,int side,float y){float h=matchPanelH;boolean turn=started&&game.winner==0&&game.turn==side,me=side==humanSide();RectF r=new RectF(4,y,396,y+h);
   inkCard(c,r,8,turn);if(!turn){paint.reset();paint.setAntiAlias(true);paint.setColor(0x47000000);c.drawRoundRect(r,8,8,paint);}
   float icon=Math.min(h-14,60);drawPlayerIcon(c,side,new RectF(10,y+(h-icon)/2,10+icon,y+(h-icon)/2+icon),turn);float tx=18+icon;
   styledText(c,playerName(side),tx,y+h/2-8,15.5f,turn?0xfff3e6cf:0xffb9ad9e,false,Paint.Align.LEFT,null);
   long left=game.remaining[side==1?0:1],sec=(Math.max(0,left)+999)/1000;clockText(c,String.format(Locale.US,"%02d:%02d",sec/60,sec%60),tx,y+h/2+26,33,left<=30000?0xffff6450:(turn?0xfffff8ec:0xffa89d90));
   int count=handCount(side);if(count==0){styledText(c,"持ち駒なし",310,y+h/2+5,13,0x99d9cfc2,false,Paint.Align.CENTER,null);return;}long frame=frameTime();
   int i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){float x=handX(i,count);int shown=game.hands[side==1?0:1][n]-(effects.handPending(side,n,frame)?1:0);RectF box=handBox(x,y);
    if(selectedSide==side&&selectedHand==n)fitted(c,"ui/highlight_selected_gold.png",new RectF(box.left-4,y+2,box.right+4,y+h-2),false);
    if(shown>0){float pop=effects.handScale(side,n,frame);drawPiece(c,side*n,box,commonHeight(box.width(),box.height())*pop);text(c,""+shown,box.right-3,box.bottom+12,14*pop,Color.WHITE);}i++;}
  }
  /** Clock digits in the bold UI face with tabular figures, so the time does not shift as it counts down. */
  void clockText(Canvas c,String s,float x,float y,float size,int color){paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.bold);paint.setFontFeatureSettings("tnum");paint.setTextSize(size);paint.setColor(color);paint.setShadowLayer(1.5f,0,1,0x99160402);c.drawText(s,x,y,paint);}
  /** あなた / the CPU persona with its rank label (目安), each with the side tag. */
  String playerName(int side){MatchOptions o=matchOptions();return side==humanSide()?"あなた":o.cpuName()+"　"+o.cpuRank();}
  /** Generated icon (assets/generated/avatars) when supplied, otherwise an original emblem drawn per level. */
  final Map<String,Boolean> avatarPresent=new HashMap<>();
  boolean hasAsset(String path){Boolean known=avatarPresent.get(path);if(known!=null)return known;boolean found;try(InputStream in=getAssets().open(path)){found=true;}catch(IOException e){found=false;}avatarPresent.put(path,found);return found;}
  void drawPlayerIcon(Canvas c,int side,RectF r,boolean lit){boolean me=side==humanSide();int level=matchOptions().level;String path=me?"generated/avatars/avatar_player.webp":String.format(Locale.US,"generated/avatars/avatar_cpu_%02d.webp",level);
   paint.reset();paint.setAntiAlias(true);if(hasAsset(path)){c.save();Path clip=new Path();clip.addOval(r,Path.Direction.CW);c.clipPath(clip);image(c,path,null,r);c.restore();}
   else{paint.setShader(new RadialGradient(r.centerX(),r.top+r.height()*.35f,r.width()*.7f,new int[]{me?0xff4a86c0:(MatchOptions.CPU_COLORS[level-1]|0xff000000),me?0xff1d3a5c:0xff2a0f0c},null,Shader.TileMode.CLAMP));c.drawOval(r,paint);paint.setShader(null);
    text(c,me?"自":MatchOptions.CPU_GLYPHS[level-1],r.centerX(),r.centerY()+r.height()*.18f,r.height()*.5f,0xfffff1d6);}
   paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(lit?HAIRLINE_LIT:0x99a08868);c.drawOval(r,paint);
   String tag=side==1?"先手":"後手";paint.reset();paint.setAntiAlias(true);paint.setColor(side==1?0xff17100c:0xfff2e8d4);RectF t=new RectF(r.centerX()-15,r.bottom-11,r.centerX()+15,r.bottom+3);c.drawRoundRect(t,3,3,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(0xb3d8b46a);c.drawRoundRect(t,3,3,paint);styledText(c,tag,t.centerX(),t.bottom-3.8f,9,side==1?0xfff2e8d4:0xff2a1a10,true,Paint.Align.CENTER,null);}
  void hand(Canvas c,int side,float y){
   if(mode==ONLINE){onlineKomadai(c,side,y);return;}
   if(mode==MATCH){matchPanel(c,side,y);return;}
   darkPanel(c,new RectF(4,y,396,y+44));leftText(c,mode==MATCH||mode==ONLINE?(side==viewSide()?"あなた":mode==ONLINE?"相手":"CPU")+"・"+(side==1?"先手":"後手"):(side==1?"先手":"後手"),12,y+15,10.5f,Color.WHITE);
   if(mode==MATCH)clockPanel(c,y,game.remaining[side==1?0:1],started&&game.turn==side&&game.winner==0);else if(mode==ONLINE){OnlineGame g=online.game;clockPanel(c,y,Math.max(0,g.remaining(side,SystemClock.elapsedRealtime())),g.started&&game.turn==side&&!g.over());}else leftText(c,"検討・時計なし",12,y+34,8.7f,0xffc5c0b9);
   int count=handCount(side);if(count==0){text(c,"持ち駒なし",302,y+27,9,0xffc5c0b9);return;}long frame=frameTime();
   int i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){float x=handX(i,count);int shown=game.hands[side==1?0:1][n]-(effects.handPending(side,n,frame)?1:0);
    if(selectedSide==side&&selectedHand==n)fitted(c,"ui/highlight_selected_gold.png",new RectF(x-19,y,x+19,y+44),false);
    RectF dst=new RectF(x-16,y+3,x+16,y+37);if(shown>0){float pop=effects.handScale(side,n,frame);drawPiece(c,side*n,dst,commonHeight(dst.width(),dst.height())*pop);text(c,""+shown,x+13,y+41,9*pop,Color.WHITE);}i++;
   }
  }
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutBoard(w,h);}
  void layoutBoard(int w,int h){
   if(w<=0||h<=0)return;scale=w/400f;effects.pixelScale=scale;offsetX=0;H=h/scale;boardSize=400;boardHeight=boardSize*BOARD_HEIGHT_RATIO;boardX=0;boardY=mode==STUDY?46:mode==ONLINE?128:80;
   if(mode==MATCH){matchPanelH=Math.max(70,Math.min(112,(H-MATCH_BAR-boardHeight-MATCH_STATUS-24)/2));float low=MATCH_BAR+8+matchPanelH+4,high=H-(boardHeight+4+matchPanelH+MATCH_STATUS);boardY=Math.max(low,Math.min(high,(H-boardHeight)/2));}
   studyContentHeight=coffee.unlocked()?Math.max(H,mode==STUDY?analysisY()+ANALYSIS_HEIGHT+8:mode==MATCH?bottomHandY()+matchPanelH+MATCH_STATUS+8:0):adTop()+adHeight()+16;studyScroll=Math.max(0,Math.min(studyScroll,maxStudyScroll()));
  }
  HomeScreen.Layout homeLayout(){return new HomeScreen.Layout(H);}
  float adTop(){return Math.max(H,mode==STUDY?analysisY()+ANALYSIS_HEIGHT+8:mode==MATCH?bottomHandY()+matchPanelH+MATCH_STATUS:mode==ONLINE?(online.game!=null?onlineStatusY()+168:online.seekMode>=0?cancelRect().bottom+20:lobbyPanelY()+110):homeLayout().archive.bottom)+16;}
  float adHeight(){return coffee.unlocked()?0:360*getResources().getDisplayMetrics().density/scale;}
  void updateAd(){if(getWidth()==0)return;if(coffee.unlocked()){if(adCard!=null){adCard.close();removeView(adCard);adCard=null;}adMode=-1;return;}if(adMode!=mode){if(adCard!=null){adCard.close();removeView(adCard);}adMode=mode;adCard=new TestAdCard(MainActivity.this,mode==STUDY?"study":mode==MATCH||mode==ONLINE?"match":"home",getIntent().getBooleanExtra("ads_fixture",false));addView(adCard);adCard.load();}int left=Math.round(12*scale),top=Math.round((adTop()-studyScroll)*scale);adCard.measure(MeasureSpec.makeMeasureSpec(getWidth()-left*2,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(adHeight()*scale),MeasureSpec.EXACTLY));adCard.layout(left,top,getWidth()-left,top+Math.round(adHeight()*scale));}
  @Override protected void onMeasure(int w,int h){super.onMeasure(w,h);if(adCard!=null)adCard.measure(MeasureSpec.makeMeasureSpec(Math.max(0,getMeasuredWidth()-Math.round(24*scale)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(adHeight()*scale),MeasureSpec.EXACTLY));}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){updateAd();}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){interceptDownX=e.getX();interceptDownY=e.getY();}if(e.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(e.getY()-interceptDownY)>ViewConfiguration.get(MainActivity.this).getScaledTouchSlop()&&Math.abs(e.getY()-interceptDownY)>Math.abs(e.getX()-interceptDownX)){MotionEvent down=MotionEvent.obtain(e.getDownTime(),e.getDownTime(),MotionEvent.ACTION_DOWN,interceptDownX,interceptDownY,0);gestures.onTouchEvent(down);down.recycle();dragged=true;return true;}return false;}
  float homeMatchY(){return homeLayout().match.top;}float homeStudyY(){return homeLayout().study.top;}
  float homeImportY(){return homeLayout().paste.top;}float homeArchiveY(){return homeLayout().archive.top;}float homeOnlineY(){return homeLayout().online.top;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.clipRect(0,0,getWidth(),getHeight());if(mode==HOME){c.translate(offsetX,-studyScroll*scale);c.scale(scale,scale);homeScreen.draw(c,H,load(HomeScreen.BACKGROUND),load(HomeScreen.TITLE),load("pieces/normal/ou.png"));c.restore();return;}fitted(c,BACKGROUND_ART,new RectF(0,0,getWidth(),getHeight()),true);paint.reset();paint.setColor(0x4d0a0204);c.drawRect(0,0,getWidth(),getHeight(),paint);if(mode==MATCH||mode==ONLINE&&online.game!=null){int tint=effects.tint(game.movesPlayed,frameTime());if(Color.alpha(tint)>0){paint.reset();paint.setColor(tint);c.drawRect(0,0,getWidth(),getHeight(),paint);}}c.translate(offsetX,-studyScroll*scale);c.scale(scale,scale);
   if(mode==ONLINE&&online.game==null){onlineLobby(c);c.restore();if(online.seekMode>=0&&frozenFrame<0)postInvalidateOnAnimation();return;}
   observeFlow();
   if(mode==ONLINE)onlineTopBar(c);
   if(mode==MATCH)matchToolbar(c);
   hand(c,-viewSide(),topHandY());board(c);lastMoveColors(c);selectionColors(c);long frame=frameTime();for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(game.board[r][col]!=0&&!effects.hides(r,col,frame))boardPiece(c,game.board[r][col],cell(r,col));lastMoveFrame(c);hand(c,viewSide(),bottomHandY());if(mode!=STUDY)effects.draw(c,geometry,frame);drawCandidateArrows(c);drawPick(c);if(mode!=STUDY){effects.drawStart(c,boardRect(),frame);effects.drawEnd(c,boardRect(),frame);if(mode==MATCH&&game.winner!=0&&effects.endButtonsVisible(frame))drawEndButtons(c);}
   if(mode==MATCH){String status=game.winner==2?game.result:game.winner!=0?(game.winner==humanSide()?"勝利":"敗北")+"・"+game.result:!ai.ready?(aiError==null?"AI読み込み中…":"AIエラー・対局設定から再試行"):!started?"タップして対局開始":game.turn==humanSide()?(game.checked(humanSide())?"王手です・あなたの手番":"あなたの手番"):"CPUの手番";text(c,status,200,bottomHandY()+matchPanelH+28,17,game.winner==0&&started&&game.turn==humanSide()?0xfff0d48e:0xfff3e6cf);if(!started){float cy=boardY+boardHeight/2;effects.band(c,boardX,boardX+boardSize,cy,38,1);
    paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setTextSize(25);paint.setLetterSpacing(.12f);paint.setTextAlign(Paint.Align.CENTER);paint.setShader(new LinearGradient(0,cy-14,0,cy+10,0xfffaeab8,0xffd0a050,Shader.TileMode.CLAMP));c.drawText(ai.ready?"タップして対局開始":"AI読み込み中…",200,cy+9,paint);paint.setShader(null);}drawResignConfirm(c);}else if(mode==ONLINE)onlineStatus(c);else analysisPanel(c);c.restore();
   long tick=frameTime();if(frozenFrame<0&&(effects.active(tick)||effects.overlayAnimating(tick)||effects.tintFading(tick)||mode==ONLINE&&online.game==null&&online.seekMode>=0))postInvalidateOnAnimation();
  }
  int candidateArrowCount(){return mode==STUDY&&analysisResult!=null&&analysisBoard!=null?Math.min(3,analysisRoots.size()):0;}
  void drawCandidateArrows(Canvas c){
   int count=candidateArrowCount();if(count==0)return;
   RectF first=gridCell(0,0),last=gridCell(8,8);int side=analysisBoard.turn;
   for(int i=count-1;i>=0;i--){ShogiGame.Move m=analysisRoots.get(i);RectF source=m.drop>0?handPieceRect(side,m.drop):cell(m.fromRow,m.fromCol);c.save();
    // Board moves stay on the board; a drop may run from the komadai above or below it.
    if(m.drop>0&&source!=null)c.clipRect(0,topHandY(),400,bottomHandY()+44);else c.clipRect(first.left,first.top,last.right,last.bottom);
    CandidateArrow.draw(c,source,cell(m.row,m.col),i+1,side,m.drop>0);c.restore();}
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
   pauseMode();save();setFreePlacement(false);study=replacement;evalHistory=new EvaluationHistory();evalHistory.load(record);mode=STUDY;updateBack();game=study.game;analysisSide=evalHistory.side();getPreferences(MODE_PRIVATE).edit().putInt("study_analysis_side",analysisSide).apply();game.turn=analysisSide;game.movesPlayed=evalHistory.currentEdit();started=true;clock=new MatchClock(game);lastMove=null;studyScroll=0;clearSelection();layoutBoard(getWidth(),getHeight());updateAd();requestLayout();setContentDescription("検討モード・保存した棋譜");save();if(resumed)resumeMode();
  }
  final Set<Integer> expandedForks=new HashSet<>();android.widget.ListView moveList;List<EvaluationHistory.ListEntry> moveEntries=new ArrayList<>();
  /** The displayed line as a record; under each move with alternatives a pull tab opens or closes its branches. */
  void studyMoves(){
   android.widget.ListView list=new android.widget.ListView(MainActivity.this);moveList=list;moveEntries=evalHistory.listEntries(expandedForks);list.setDivider(null);
   android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
    @Override public int getCount(){return moveEntries.size();}
    @Override public Object getItem(int i){return moveEntries.get(i);}
    @Override public long getItemId(int i){return i;}
    @Override public View getView(int i,View convert,ViewGroup parent){return moveRow(moveEntries.get(i),convert instanceof android.widget.TextView?(android.widget.TextView)convert:new android.widget.TextView(MainActivity.this));}
   };
   list.setAdapter(adapter);
   AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle(evalHistory.showingVariation()?"手順（変化を表示中）":"手順（本譜）").setView(list).setPositiveButton("閉じる",null).create();
   list.setOnItemClickListener((parent,row,i,id)->{
    EvaluationHistory.ListEntry e=moveEntries.get(i);
    if(e.kind==EvaluationHistory.ListEntry.TAB){if(!expandedForks.remove(e.row.ply))expandedForks.add(e.row.ply);moveEntries=evalHistory.listEntries(expandedForks);adapter.notifyDataSetChanged();return;}
    d.dismiss();if(e.kind==EvaluationHistory.ListEntry.MOVE)jumpStudy(e.row.index);else jumpStudyNode(e.alternative.node);
   });
   showChoice(d);int current=0;for(int i=0;i<moveEntries.size();i++)if(moveEntries.get(i).kind==EvaluationHistory.ListEntry.MOVE&&moveEntries.get(i).row.current)current=i;list.setSelection(Math.max(0,current-3));
  }
  android.widget.TextView moveRow(EvaluationHistory.ListEntry e,android.widget.TextView t){
   float dp=getResources().getDisplayMetrics().density;t.setText(e.text());t.setTypeface(e.kind==EvaluationHistory.ListEntry.MOVE&&!e.row.fork()?Fonts.medium:Fonts.bold);t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(Math.round(44*dp));
   t.setMaxLines(Integer.MAX_VALUE);t.setEllipsize(null);
   if(e.kind==EvaluationHistory.ListEntry.TAB){
    android.graphics.drawable.GradientDrawable tab=new android.graphics.drawable.GradientDrawable();tab.setCornerRadius(18*dp);tab.setColor(e.expanded?0xfff7c98b:0xfffde9d2);tab.setStroke(Math.round(1.5f*dp),0xffc8742e);
    t.setBackground(new android.graphics.drawable.InsetDrawable(tab,Math.round(32*dp),Math.round(4*dp),Math.round(12*dp),Math.round(4*dp)));t.setPadding(Math.round(44*dp),0,Math.round(20*dp),0);t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setTextSize(14);t.setTextColor(0xff8a3b12);
   }else if(e.kind==EvaluationHistory.ListEntry.BRANCH){
    t.setBackgroundColor(e.alternative.shown?0xfffbe3c4:0xfffff6ea);t.setPadding(Math.round(56*dp),Math.round(6*dp),Math.round(16*dp),Math.round(6*dp));t.setTextSize(15);t.setTextColor(e.alternative.branch==0?0xff1d3d6b:0xff8a3b12);
   }else{
    t.setBackgroundColor(e.row.current?0xfff6e3b4:Color.TRANSPARENT);t.setPadding(Math.round(24*dp),Math.round(6*dp),Math.round(16*dp),Math.round(6*dp));t.setTextSize(16);t.setTextColor(e.row.fork()?0xffa1420f:0xff222222);
   }
   return t;
  }
  void jumpStudyNode(int node){
   if(mode!=STUDY||node==evalHistory.currentNode())return;List<String> path=evalHistory.keysTo(node);if(path.isEmpty()||!study.seek(path,path.size()-1))return;
   cancelCpu();evalHistory.selectNode(node);game.movesPlayed=evalHistory.currentEdit();game.turn=evalHistory.side();lastMove=null;clearSelection();setAnalysisSide(evalHistory.side());if(resumed)sounds.play();save();
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
   evaluationGraph(c,y);String[] nav={"初期","前へ","次へ","最終","手順"};for(int i=0;i<nav.length;i++){paint.reset();paint.setColor(0xff49352a);c.drawRoundRect(6+i*79,y+242,79+i*79,y+282,3,3,paint);text(c,nav[i],42+i*79,y+(i==2&&evalHistory.branchesAfterCursor()>0?263:267),12,Color.WHITE);}
   int forks=evalHistory.branchesAfterCursor();if(forks>0)text(c,"◆分岐"+forks+"通り",200,y+277,7.5f,0xffdcc48e);
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
  void placementSound(){placementSound(MoveSound.Kind.PLACE);}
  /** One sound per move (check > promotion > capture > placement) and a 15ms vibration for the player's own move. */
  void placementSound(MoveSound.Kind kind){if(resumed&&mode!=HOME){sounds.play(kind);if(settings.vibration()&&(mode==STUDY||game.turn==-viewSide())){hapticRequests++;lastHapticAccepted=vibrate(15);}}}
  boolean vibrate(long millis){Vibrator vibrator=(Vibrator)getSystemService(android.content.Context.VIBRATOR_SERVICE);if(vibrator==null||!vibrator.hasVibrator())return false;if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(millis,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(millis);return true;}
  MoveSound.Kind moveKind(ShogiGame.Move m,BoardEffects.Snapshot before){if(game.winner==0&&game.checked(game.turn))return MoveSound.Kind.CHECK;if(m.promote)return MoveSound.Kind.PROMOTE;return before!=null&&m.drop==0&&before.board[m.row][m.col]!=0?MoveSound.Kind.CAPTURE:MoveSound.Kind.PLACE;}
  /** Plays the start and end sounds once per game (and per online round); restored games stay silent. */
  int flowRound=-1;boolean flowStarted,flowEnded;
  void observeFlow(){if(mode!=MATCH&&mode!=ONLINE)return;OnlineGame g=online.game;boolean st=mode==MATCH?started:g!=null&&g.started;int round=mode==ONLINE&&g!=null?g.round:0;
   if(!st){flowStarted=false;flowEnded=false;return;}
   if(!flowStarted||round!=flowRound){flowStarted=true;flowRound=round;flowEnded=game.winner!=0;if(game.winner==0&&game.movesPlayed==0)onGameStart();else if(game.winner!=0&&mode==MATCH)showEnd(SystemClock.uptimeMillis()-60000);}
   if(game.winner!=0&&!flowEnded){flowEnded=true;onGameEnd();}else if(game.winner==0)flowEnded=false;}
  void onGameStart(){effects.reduced=settings.reducedEffects();int me=mode==MATCH?humanSide():online.game.mySide;
   effects.start("あなた："+(me==1?"先手":"後手")+"　"+(mode==MATCH?"CPU":"相手")+"："+(me==1?"後手":"先手"),SystemClock.uptimeMillis());if(resumed)sounds.play(MoveSound.Kind.START);invalidate();}
  void onGameEnd(){showEnd(SystemClock.uptimeMillis());if(resumed)sounds.play(MoveSound.Kind.END);}
  void showEnd(long at){effects.reduced=settings.reducedEffects();int me=mode==MATCH?humanSide():online.game!=null?online.game.mySide:1;effects.end(game.winner==2?0:game.winner==me?1:-1,game.result,boardRect(),at);invalidate();}
  RectF boardRect(){return new RectF(boardX,boardY,boardX+boardSize,boardY+boardHeight);}
  /** もう一局 / 検討する / ホームへ, under the result in CPU matches. */
  RectF[] endButtons(){float y=boardY+boardHeight/2+48;return new RectF[]{new RectF(14,y,136,y+42),new RectF(139,y,261,y+42),new RectF(264,y,386,y+42)};}
  void drawEndButtons(Canvas c){String[] labels={"もう一局","検討する","ホームへ"};RectF[] b=endButtons();
   for(int i=0;i<3;i++)button(c,b[i],i==0?PRIMARY:SECONDARY,labels[i],15);}
  /** Handles the result buttons; returns true when the tap was used. */
  boolean endButtonTouch(float x,float y){if(mode!=MATCH||game.winner==0||!effects.endButtonsVisible(SystemClock.uptimeMillis()))return false;RectF[] b=endButtons();
   if(b[0].contains(x,y)){restart();started=true;clock.resume(SystemClock.elapsedRealtime(),true);save();scheduleCpu();invalidate();return true;}
   if(b[1].contains(x,y)){if(matchJournal==null)matchJournal=new MatchJournal(match);save();activity().openForStudy(matchJournal.record(match));return true;}
   if(b[2].contains(x,y)){enterMode(HOME);return true;}return false;}
  MainActivity activity(){return MainActivity.this;}
  void clearSelection(){pick=null;selectedR=selectedC=-1;selectedHand=0;highlights=Collections.emptyList();invalidate();}
  void select(){
   ArrayList<ShogiGame.Move> list=new ArrayList<>();int side=mode==MATCH||mode==ONLINE?viewSide():selectedSide;
   if(mode==STUDY&&freePlacement){for(int r=0;r<9;r++)for(int c=0;c<9;c++){
    if(selectedHand>0){if(game.hands[ShogiGame.slot(side)][selectedHand]>0&&game.board[r][c]==0)list.add(new ShogiGame.Move(-1,-1,r,c,false,selectedHand));}
    else if(study.canMove(selectedR,selectedC,r,c))list.add(new ShogiGame.Move(selectedR,selectedC,r,c,false,0));
   }}else for(ShogiGame.Move m:game.legal(side))if(selectedHand>0?m.drop==selectedHand:m.drop==0&&m.fromRow==selectedR&&m.fromCol==selectedC)list.add(m);
   highlights=list;invalidate();
  }
  void move(ShogiGame.Move m){if(mode==ONLINE){OnlineGame g=online.game;BoardEffects.Snapshot before=g==null?null:BoardEffects.Snapshot.of(g.game);if(g!=null&&g.localMove(m,SystemClock.elapsedRealtime())){pieceMoved(m,g.mySide,before);placementSound(moveKind(m,before));lastMove=m;syncAccount();}clearSelection();return;}clock.settle(SystemClock.elapsedRealtime());if(mode!=MATCH||!started||game.winner!=0||game.turn!=humanSide())return;placementSound(moveKind(m,applyMatchMove(m,humanSide())));lastMove=m;clearSelection();save();scheduleCpu();}
  /** On-board promotion choice: the promoted and the unpromoted piece side by side at the destination, no OS dialog. */
  static final class PromotionPick {final int row,col,side,base;final Runnable promote,plain;final boolean edit;
   PromotionPick(int row,int col,int side,int base,Runnable promote,Runnable plain,boolean edit){this.row=row;this.col=col;this.side=side;this.base=base;this.promote=promote;this.plain=plain;this.edit=edit;}}
  PromotionPick pick;
  void promotionChoice(int r,int c,int side,int base,Runnable promote,Runnable plain){pick=new PromotionPick(r,c,side,base,promote,plain,false);invalidate();}
  /** [promoted option, unpromoted option], next to each other over the destination and kept inside the board. */
  RectF[] pickRects(){RectF cell=cell(pick.row,pick.col),a=gridCell(0,0),z=gridCell(8,8);float w=cell.width()*1.3f,h=cell.height()*1.3f,gap=6,total=w*2+gap;
   float left=Math.max(a.left+2,Math.min(z.right-2-total,cell.centerX()-total/2)),top=Math.max(a.top+2,Math.min(z.bottom-2-h,cell.centerY()-h/2));
   return new RectF[]{new RectF(left,top,left+w,top+h),new RectF(left+w+gap,top,left+total,top+h)};}
  void drawPick(Canvas c){if(pick==null)return;RectF[] r=pickRects();RectF all=new RectF(r[0].left-6,r[0].top-6,r[1].right+6,r[1].bottom+18);
   inkCard(c,all,9,false);
   for(int i=0;i<2;i++){paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,r[i].top,0,r[i].bottom,0xfff8efdc,i==0?0xffeedcc0:0xffe6d9c2,Shader.TileMode.CLAMP));c.drawRoundRect(r[i],5,5,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(i==0?1.8f:1);paint.setColor(i==0?0xffc8402c:0xffa88850);c.drawRoundRect(r[i],5,5,paint);
    int p=pick.side*(i==0?pick.base+8:pick.base);drawPiece(c,p,r[i],Math.min(r[i].height()*.86f,boardPieceHeight()*1.15f));
    styledText(c,pick.edit?(i==0?"成る":"元の駒"):(i==0?"成る":"成らない"),r[i].centerX(),r[i].bottom+13,10.5f,i==0?0xffffb4a0:0xfff3e6cf,true,Paint.Align.CENTER,null);}}
  /** A tap on an option commits it; any other tap cancels the move and keeps the piece selected, like the old dialog's cancel. */
  void choosePick(float x,float y){RectF[] r=pickRects();PromotionPick p=pick;pick=null;if(r[0].contains(x,y))p.promote.run();else if(r[1].contains(x,y))p.plain.run();invalidate();}
  void choose(int r,int col){
   if(selectedHand==0&&selectedR==r&&selectedC==col){clearSelection();return;}
   ArrayList<ShogiGame.Move> list=new ArrayList<>();for(ShogiGame.Move m:highlights)if(m.row==r&&m.col==col)list.add(m);
   if(!list.isEmpty()){
    ShogiGame.Move pro=null,plain=null;for(ShogiGame.Move m:list){if(m.promote)pro=m;else plain=m;}
    if(pro!=null&&plain!=null){ShogiGame.Move promoted=pro,unpromoted=plain;int mover=ShogiGame.side(game.board[pro.fromRow][pro.fromCol]);promotionChoice(r,col,mover,ShogiGame.base(game.board[pro.fromRow][pro.fromCol]),()->move(promoted),()->move(unpromoted));}
    else move(list.get(0));return;
   }
   if(ShogiGame.side(game.board[r][col])==viewSide()){selectedR=r;selectedC=col;selectedHand=0;selectedSide=viewSide();select();}else clearSelection();
  }
  boolean commitStudyMove(int sr,int sc,int r,int c,int drop,int side,boolean promote){
   if(mode!=STUDY)return false;
   if(!freePlacement){ShogiGame.Move requested=new ShogiGame.Move(sr,sc,r,c,promote,drop);boolean legal=false;if(side==analysisSide)for(ShogiGame.Move candidate:game.legal(side))if(ShogiGame.same(candidate,requested)){legal=true;break;}if(!legal)return false;}
   String label=(evalHistory.currentEdit()+1)+"手目  "+StudyAnalysis.notation(game,new ShogiGame.Move(sr,sc,r,c,promote,drop));boolean changed=drop>0?study.drop(side,drop,r,c):study.move(sr,sc,r,c,promote);
   if(changed){placementSound();lastMove=new ShogiGame.Move(sr,sc,r,c,promote,drop);int mover=ShogiGame.side(game.board[r][c]);evalHistory.advance(EvaluationHistory.key(game),label);game.movesPlayed=evalHistory.currentEdit();game.turn=-mover;setAnalysisSide(-mover);save();}return changed;
  }
  void chooseStudy(int r,int c){
   int sr=selectedR,sc=selectedC;
   if(selectedHand==0&&sr==r&&sc==c){clearSelection();return;}
   if(!freePlacement){
    ArrayList<ShogiGame.Move> targets=new ArrayList<>();for(ShogiGame.Move m:highlights)if(m.row==r&&m.col==c)targets.add(m);
    if(!targets.isEmpty()){
     ShogiGame.Move pro=null,plain=null;for(ShogiGame.Move m:targets){if(m.promote)pro=m;else plain=m;}
     int side=selectedSide;if(pro!=null&&plain!=null){ShogiGame.Move p=pro,n=plain;promotionChoice(r,c,ShogiGame.side(game.board[p.fromRow][p.fromCol]),ShogiGame.base(game.board[p.fromRow][p.fromCol]),()->commitStudyMove(p.fromRow,p.fromCol,p.row,p.col,p.drop,side,true),()->commitStudyMove(n.fromRow,n.fromCol,n.row,n.col,n.drop,side,false));}
     else{ShogiGame.Move m=targets.get(0);commitStudyMove(m.fromRow,m.fromCol,m.row,m.col,m.drop,side,m.promote);}return;
    }
    if(ShogiGame.side(game.board[r][c])==analysisSide){selectedR=r;selectedC=c;selectedHand=0;selectedSide=analysisSide;select();}else clearSelection();return;
   }
   if(selectedHand>0&&commitStudyMove(sr,sc,r,c,selectedHand,selectedSide,false))return;
   if(sr>=0&&study.canMove(sr,sc,r,c)){
    if(study.canPromoteOnMove(sr,sc,r,c)){int mover=selectedSide;promotionChoice(r,c,ShogiGame.side(game.board[sr][sc]),ShogiGame.base(game.board[sr][sc]),()->commitStudyMove(sr,sc,r,c,0,mover,true),()->commitStudyMove(sr,sc,r,c,0,mover,false));}
    else commitStudyMove(sr,sc,r,c,0,selectedSide,false);return;
   }
   if(game.board[r][c]!=0){selectedR=r;selectedC=c;selectedHand=0;selectedSide=ShogiGame.side(game.board[r][c]);select();}else clearSelection();
  }
  /** Free-placement promotion edit, on the board like the move choice: promoted or original face. */
  void promotionEditor(int r,int c){int p=game.board[r][c];boolean promoted=Math.abs(p)>8;Runnable toggle=()->{study.togglePromotion(r,c);evalHistory.advance(EvaluationHistory.key(game));clearSelection();save();refreshAnalysis();};
   pick=new PromotionPick(r,c,ShogiGame.side(p),ShogiGame.base(p),()->{if(!promoted)toggle.run();else clearSelection();},()->{if(promoted)toggle.run();else clearSelection();},true);invalidate();}
  /** Last ten seconds of the player's own clock: one tick sound per second. */
  long lastTickSecond=-1;
  void countdownTick(){if(!started||game.winner!=0||game.turn!=humanSide()){lastTickSecond=-1;return;}long left=game.remaining[humanSide()==1?0:1];if(left>10000||left<=0)return;long sec=(left+999)/1000;if(sec!=lastTickSecond){lastTickSecond=sec;if(resumed)sounds.play(MoveSound.Kind.TICK);}}
  void restart(){cancelCpu();resignConfirm=false;effects.clear();effects.clearOverlays();flowStarted=false;if(mode==STUDY){study.reset();evalHistory.reset(EvaluationHistory.key(game));studyScroll=0;}else{save();match.restart();matchJournal=new MatchJournal(match);matchJournal.options=configuredMatchOptions();match.remaining[0]=match.remaining[1]=matchOptions().initialMillis();nodeBudget=matchOptions().nodes();matchJournal.options=matchOptions();matchStarted=false;started=false;clock=new MatchClock(game);}lastMove=null;clearSelection();save();if(mode==STUDY)refreshAnalysis();}
  boolean tapHand(float x,float y,int side,float top){if(y<top||y>=top+panelH())return false;int count=handCount(side),i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){if(Math.abs(x-handX(i,count))<handStep(count)/2){if(mode==STUDY&&!freePlacement&&(side!=analysisSide||n==ShogiGame.OU)||selectedR<0&&selectedHand==n&&selectedSide==side){clearSelection();return true;}selectedR=selectedC=-1;selectedHand=n;selectedSide=side;select();return true;}i++;}return false;}
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_CANCEL){gestures.onTouchEvent(e);clearSelection();return true;}if(e.getAction()==MotionEvent.ACTION_DOWN){longPressed=false;dragged=false;}gestures.onTouchEvent(e);if(e.getAction()!=MotionEvent.ACTION_UP)return true;performClick();if(dialogOpen||longPressed||dragged)return true;float x=(e.getX()-offsetX)/scale,y=e.getY()/scale+studyScroll;
   if(pick!=null&&mode!=HOME){choosePick(x,y);return true;}
   if(mode==HOME){HomeScreen.Layout l=homeLayout();if(l.settings.contains(x,y))showSettings();else if(l.study.contains(x,y))enterMode(STUDY);else if(l.match.contains(x,y))enterMode(MATCH);else if(l.online.contains(x,y))enterMode(ONLINE);else if(l.paste.contains(x,y))clipboardImport.paste();else if(l.archive.contains(x,y))showArchive();return true;}
   if(mode==ONLINE)return onlineTouch(x,y);
   if(mode==MATCH)clock.settle(SystemClock.elapsedRealtime());if(mode==MATCH&&resignConfirm){resignTouch(x,y);return true;}if(mode==MATCH&&y<MATCH_BAR){if(x<90){enterMode(HOME);return true;}if(x<200){new AlertDialog.Builder(MainActivity.this).setMessage("対局をやり直しますか？").setNegativeButton("キャンセル",null).setPositiveButton("やり直す",(d,k)->restart()).show();}else if(x<300)aiSettings();else if(started&&game.winner==0){resignConfirm=true;clearSelection();invalidate();}return true;}
   if(mode==STUDY&&y>=analysisY()&&y<=analysisY()+ANALYSIS_HEIGHT){if(y>=analysisY()+242&&y<=analysisY()+282){int action=(int)((x-6)/79);if(x>=6&&x<395){if(action==0)jumpStudy(0);else if(action==1)jumpStudy(evalHistory.cursor-1);else if(action==2)jumpStudy(evalHistory.cursor+1);else if(action==3)jumpStudy(evalHistory.points.size()-1);else studyMoves();}return true;}if(graphRect().contains(x,y)){jumpStudy(graphIndexAt(x));return true;}if(y>=analysisY()+57&&y<=analysisY()+78&&x<160)studyInputMode();else if(y>=analysisY()+57&&y<=analysisY()+78&&x>=265)setAnalysisSide(-analysisSide);else if(analysisResult==null&&!analyzing){if(aiError!=null)showAnalysisError();else refreshAnalysis();}return true;}
   if(endButtonTouch(x,y))return true;
   if(mode==MATCH){if(!started){if(ai.ready){started=true;clock.resume(SystemClock.elapsedRealtime(),true);save();scheduleCpu();invalidate();}return true;}if(game.winner!=0||game.turn!=humanSide()||thinking)return true;}
   for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(cell(r,col).contains(x,y)){if(mode==STUDY)chooseStudy(r,col);else choose(r,col);return true;}
   if(tapHand(x,y,mode==MATCH?humanSide():1,bottomHandY())||mode==STUDY&&tapHand(x,y,-1,topHandY()))return true;clearSelection();return true;
  }
  // ---- online mode ----
  ShogiGame lobbyBoard;android.widget.EditText joinField;
  ShogiGame onlineBoard(){if(online.game!=null)return online.game.game;if(lobbyBoard==null)lobbyBoard=new ShogiGame();return lobbyBoard;}
  final Runnable onlineTicker=new Runnable(){String shown="";public void run(){if(!resumed||mode!=ONLINE)return;long now=SystemClock.elapsedRealtime();OnlineGame g=online.game;
   if(g!=null){g.rttMillis=online.rtt;boolean was=g.over();g.tick(now);syncAccount();if(!was&&g.over())clearSelection();}String sig=onlineSignature(now);if(!sig.equals(shown)){shown=sig;invalidate();}handler.postDelayed(this,50);}};
  String onlineSignature(long now){OnlineGame g=online.game;StringBuilder b=new StringBuilder().append(online.status).append(online.relaysOpen).append(online.network).append(online.notice).append(online.direct).append(online.rtt).append(online.peer!=null).append(online.lastResult!=null);
   if(online.account!=null)b.append(online.account.name).append(online.account.rating);
   if(g!=null)b.append((g.remaining(1,now)+999)/1000).append(',').append((g.remaining(-1,now)+999)/1000).append(g.status(now)).append(silence()/1000);return b.toString();}
  long silence(){if(online.bot!=null)return 0;Netplay n=online.net;P2PLink l=n==null?null:n.link();return l==null?0:l.silenceMillis();}
  void ensureNetplay(){
   if(online.net!=null||online.connecting)return;online.connecting=true;online.notice="";online.status="";online.network="";online.relaysOpen=0;online.relaysTotal=Netplay.relays().size();OnlineEvents events=new OnlineEvents();
   new Thread(()->{Netplay made=null;String problem=null;try{made=new Netplay(Netplay.relays(),Netplay.stunServers(),javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),events);}catch(IOException|RuntimeException e){problem=e.toString();}
    final Netplay n=made;final String error=problem;
    mainLoop.post(()->{online.connecting=false;BoardView v=currentView;if(n==null){online.notice="通信を開始できません: "+error;if(v!=null)v.invalidate();return;}
     if(v==null||v.mode!=ONLINE||online.net!=null){new Thread(n::close,"netplay-close").start();return;}online.net=n;events.owner=n;v.invalidate();});
    if(n!=null)n.start();
   },"netplay-start").start();
  }
  /** Ends the current match (an unfinished game is resigned). all=true also disconnects from the relays. */
  void leaveOnline(boolean all){
   Netplay n=online.net;if(online.game!=null){online.game.resign();syncAccount();}endBot();online.game=null;online.peer=null;online.lastResult=null;online.seekMode=-1;online.roomCode=null;online.direct=false;online.rtt=-1;online.route="";online.notice="";
   if(all){online.roomPage=false;online.net=null;online.status="";online.relaysOpen=0;if(n!=null)new Thread(n::close,"netplay-close").start();}else if(n!=null)n.stopSeeking();
   if(mode==ONLINE){game=onlineBoard();lastMove=null;studyScroll=0;clearSelection();layoutBoard(getWidth(),getHeight());updateAd();requestLayout();}
  }
  void onlineBack(){OnlineGame g=online.game;
   if(g!=null&&g.started&&!g.over()){confirm("対局中です。投了して退出しますか？","投了して退出",()->leaveOnline(false));return;}
   if(g!=null){leaveOnline(false);return;}
   if(online.roomPage){stopSeek();online.roomPage=false;invalidate();return;}enterMode(HOME);
  }
  void stopSeek(){Netplay n=online.net;online.seekToken++;if(online.seekMode>=0&&n!=null)n.stopSeeking();online.seekMode=-1;online.roomCode=null;online.status="";}
  void onlineUpdated(){if(mode!=ONLINE)return;ShogiGame g=onlineBoard();if(game!=g){effects.clear();game=g;lastMove=online.game==null?null:online.game.lastMove;studyScroll=0;clearSelection();layoutBoard(getWidth(),getHeight());updateAd();requestLayout();}invalidate();}
  void remoteMoved(BoardEffects.Snapshot before,int mover){if(mode!=ONLINE||online.game==null)return;lastMove=online.game.lastMove;if(lastMove!=null&&before!=null)pieceMoved(lastMove,mover,before);clearSelection();if(lastMove!=null)placementSound(moveKind(lastMove,before));invalidate();}
  void confirm(String message,String ok,Runnable action){showChoice(new AlertDialog.Builder(MainActivity.this).setMessage(message).setNegativeButton("キャンセル",null).setPositiveButton(ok,(x,k)->{action.run();invalidate();}).create());}
  /** Lobby: time control, random match, a small link to the room page. Room page: time control, create, join. */
  // Match-conditions screen, by importance: the big 対局開始 (or the room buttons) first, then the mode and time
  // choices, then quiet one-line account, description and network notes.
  static final float ACCOUNT_CARD_Y=76;
  RectF modeTile(int i){return new RectF(12+i*192,132,196+i*192,190);}
  /** The room tile: opens the room page from the random-match page. */
  RectF roomLinkRect(){return modeTile(1);}
  RectF timeRect(int i){return new RectF(12+i*192,200,196+i*192,244);}
  float lobbyButtonY(int i){return 296+i*66;}
  RectF startRect(){return new RectF(12,lobbyButtonY(0),388,lobbyButtonY(0)+66);}
  RectF roomButton(int i){float y=lobbyButtonY(i);return new RectF(12,y,388,y+(i==0?58:48));}
  float lobbyPanelY(){return online.roomPage?lobbyButtonY(1)+74:lobbyButtonY(0)+96;}
  // Matching screen while a seek runs: a game-clock dial whose gold arc fills over the wait, the player's komadai, and 中止.
  float clockY(){return online.seekMode==Netplay.HOST?336:276;}
  static final float CLOCK_R=96;
  RectF trayRect(){float y=clockY()+CLOCK_R+20;return new RectF(12,y,388,y+56);}
  RectF cancelRect(){float y=trayRect().bottom+80;return new RectF(212,y,388,y+56);}
  void chooseTimeControl(String tc){if(online.seekMode>=0||!OnlineGame.validTimeControl(tc))return;online.timeControl=tc;getPreferences(MODE_PRIVATE).edit().putString("online_tc",tc).apply();invalidate();}
  void openRoomPage(boolean open){if(open==online.roomPage)return;stopSeek();online.roomPage=open;online.notice="";invalidate();}
  void startSeek(int seekMode,String code){Netplay n=online.net;if(n==null){online.notice="通信を準備しています…";ensureNetplay();invalidate();return;}
   n.rating=online.account.rating;n.games=online.account.games;online.seekMode=seekMode;online.roomCode=code;online.notice="";online.status="";n.seek(seekMode,code,online.timeControl);
   online.seekStarted=SystemClock.uptimeMillis();long token=++online.seekToken;if(seekMode==Netplay.QUICK)mainLoop.postDelayed(()->{if(online.seekToken==token&&online.seekMode==Netplay.QUICK&&online.game==null&&online.net==n){if(!ai.ready)configureAi();startBotGame(this::botMove);}},OnlineBot.waitMillis);invalidate();}
  void lobbyTouch(float x,float y){
   if(online.seekMode>=0){if(cancelRect().contains(x,y)){stopSeek();invalidate();}return;}
   if(y>=ACCOUNT_CARD_Y&&y<=ACCOUNT_CARD_Y+44&&x>=12&&x<=388){showChoice(AccountDialog.create(MainActivity.this));return;}
   for(int i=0;i<2;i++)if(modeTile(i).contains(x,y)){openRoomPage(i==1);return;}
   for(int i=0;i<2;i++)if(timeRect(i).contains(x,y)){chooseTimeControl(OnlineGame.TIME_CONTROLS[i]);return;}
   if(online.roomPage){for(int i=0;i<2;i++)if(roomButton(i).contains(x,y)){if(i==0)startSeek(Netplay.HOST,Netplay.newRoomCode(new java.security.SecureRandom()));else joinRoomDialog();return;}return;}
   if(startRect().contains(x,y)){startSeek(Netplay.QUICK,null);return;}
  }
  void joinRoomDialog(){android.widget.EditText field=new android.widget.EditText(MainActivity.this);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});field.setHint("6桁のルームコード");joinField=field;
   showChoice(new AlertDialog.Builder(MainActivity.this).setTitle("ルームに参加").setMessage("友だちの画面に表示された6桁のコードを入力してください").setView(field).setNegativeButton("キャンセル",null).setPositiveButton("参加",(x,k)->{String code=field.getText().toString().trim();
    if(code.matches("[0-9]{6}"))startSeek(Netplay.GUEST,code);else{online.notice="ルームコードは6桁の数字です";invalidate();}}).create());}
  boolean onlineTouch(float x,float y){OnlineGame g=online.game;
   if(y<32){if(x<90)onlineBack();else if(g!=null&&x>=300){if(g.started&&!g.over())confirm("投了しますか？","投了",()->{g.resign();syncAccount();clearSelection();});else if(g.over()&&!g.peerLeft&&!g.rematchMine){g.requestRematch(SystemClock.elapsedRealtime());syncAccount();lastMove=g.lastMove;clearSelection();}}return true;}
   if(g==null){lobbyTouch(x,y);return true;}
   if(g.over()){float by=onlineStatusY()+112;if(y>=by&&y<=by+34){if(x>=24&&x<=196&&!g.peerLeft&&!g.rematchMine){g.requestRematch(SystemClock.elapsedRealtime());syncAccount();lastMove=g.lastMove;clearSelection();}else if(x>=204&&x<=376)leaveOnline(false);}return true;}
   if(!g.myTurn()){clearSelection();return true;}
   for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(cell(r,col).contains(x,y)){choose(r,col);return true;}
   if(tapHand(x,y,viewSide(),bottomHandY()))return true;clearSelection();return true;
  }
  void onlineLobby(Canvas c){if(online.seekMode>=0){matchingScreen(c);return;}
   boolean room=online.roomPage;darkPanel(c,new RectF(0,0,400,32));styledText(c,room?"‹ ランダム対戦":"‹ モード",14,21.5f,12,0xffeadfc8,false,Paint.Align.LEFT,null);styledText(c,room?"ルーム対局":"オンライン対戦",200,21.5f,12,0xfff3e6cf,true,Paint.Align.CENTER,null);
   // Account: one quiet line, the name brightest.
   OnlineAccount a=online.account;float ay=ACCOUNT_CARD_Y+26;
   if(a!=null){String n=shortName(a.name,12);styledText(c,n,16,ay,13,0xfff3e6cf,true,Paint.Align.LEFT,null);styledText(c,"レート "+a.ratingText(),16+textWidth(n,13,true)+10,ay,10.5f,0x99eadfc8,false,Paint.Align.LEFT,null);}
   styledText(c,"アカウント ›",386,ay,10.5f,0xccdcc48e,false,Paint.Align.RIGHT,null);
   paint.reset();paint.setColor(0x33d8b46a);c.drawRect(12,ay+12,388,ay+12.8f,paint);
   for(int i=0;i<2;i++)modeTileDraw(c,modeTile(i),i,i==(room?1:0));
   for(int i=0;i<2;i++){String tc=OnlineGame.TIME_CONTROLS[i];segmentDraw(c,timeRect(i),"10m".equals(tc)?"10分":"3分","切れ負け",tc.equals(online.timeControl));}
   styledText(c,room?"6桁のコードで友だちと対局・レート変動なし":"近いレートの相手と対局・"+OnlineBot.waitMillis/1000+"秒で見つからなければボット",200,270,10.5f,0x99eadfc8,false,Paint.Align.CENTER,null);
   if(room){bigButton(c,roomButton(0),GOLDEN,"ルームを作る",22);button(c,roomButton(1),SECONDARY,"コードで参加",15);}
   else bigButton(c,startRect(),GOLDEN,"対局開始",30);
   float y=lobbyPanelY();boolean up=online.relaysOpen>0;paint.reset();paint.setAntiAlias(true);paint.setColor(up?0xff7fcf86:0xffd9b45a);c.drawCircle(18,y-3.5f,3,paint);
   styledText(c,(online.net==null?(online.connecting?"通信を準備しています":"未接続"):"公開リレー "+online.relaysOpen+"/"+online.relaysTotal)+(online.network.isEmpty()?"":" · "+online.network),27,y,9,0x99eadfc8,false,Paint.Align.LEFT,null);
   float t=y+20;if(!online.notice.isEmpty()){text(c,online.notice,200,t,10,0xffffb9b3);t+=18;}
   styledText(c,"P2Pで直接つなぎ、つながらない時だけ公開リレーを経由します",200,t,8.5f,0x73eadfc8,false,Paint.Align.CENTER,null);styledText(c,"相手にはIPアドレスが伝わります・対局中は時計が止まりません",200,t+13,8.5f,0x73eadfc8,false,Paint.Align.CENTER,null);
  }
  /** While seeking: a game-clock dial (the gold arc fills until the bot steps in; a turning segment while a room waits), the player's komadai and 中止. */
  void matchingScreen(Canvas c){boolean quick=online.seekMode==Netplay.QUICK;long now=SystemClock.uptimeMillis(),elapsed=Math.max(0,now-online.seekStarted);
   darkPanel(c,new RectF(0,0,400,32));styledText(c,"オンライン対戦",200,21.5f,12,0xfff3e6cf,true,Paint.Align.CENTER,null);
   goldTitle(c,quick?"対戦相手を探しています":online.seekMode==Netplay.HOST?"相手の参加を待っています":"ルームに接続しています",200,78,22);
   styledText(c,OnlineGame.label(online.timeControl)+(quick?"・レート戦":"・ルーム対局"),200,100,10.5f,0xffeadfc8,false,Paint.Align.CENTER,null);
   if(online.seekMode==Netplay.HOST&&online.roomCode!=null){RectF r=new RectF(70,116,330,200);inkCard(c,r,10,true);styledText(c,"ルームコード",200,r.top+20,10.5f,0xffeadfc8,false,Paint.Align.CENTER,null);
    paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setFontFeatureSettings("tnum");paint.setTextSize(34);paint.setLetterSpacing(.12f);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xffeedcb2);c.drawText(online.roomCode.substring(0,3)+" "+online.roomCode.substring(3),200,r.top+58,paint);
    styledText(c,"友だちに伝えてください",200,r.top+76,9.5f,0xffcfc3b0,false,Paint.Align.CENTER,null);}
   float cx=200,cy=clockY(),R=CLOCK_R;
   paint.reset();paint.setAntiAlias(true);paint.setColor(0x80000000);c.drawCircle(cx,cy+3,R+6,paint);
   paint.setShader(new LinearGradient(0,cy-R,0,cy+R,INKS,new float[]{0,.55f,1},Shader.TileMode.CLAMP));c.drawCircle(cx,cy,R+4,paint);paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);paint.setColor(CHAMPAGNE_RIM);c.drawCircle(cx,cy,R+4,paint);paint.setStrokeWidth(.7f);paint.setColor(0x55dcc48e);c.drawCircle(cx,cy,R-16,paint);
   for(int k=0;k<60;k++){double a=Math.PI*2*k/60;float r0=R-(k%5==0?10:5),r1=R-2;paint.setStrokeWidth(k%5==0?1.6f:.8f);paint.setColor(k%5==0?0xccdcc48e:0x66dcc48e);
    c.drawLine(cx+r0*(float)Math.sin(a),cy-r0*(float)Math.cos(a),cx+r1*(float)Math.sin(a),cy-r1*(float)Math.cos(a),paint);}
   RectF ring=new RectF(cx-R+8,cy-R+8,cx+R-8,cy+R-8);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(6);
   if(quick){float pr=Math.min(1,elapsed/(float)OnlineBot.waitMillis);paint.setColor(0x33dcc48e);c.drawArc(ring,-90,360,false,paint);paint.setColor(0xffdcc48e);c.drawArc(ring,-90,Math.max(1,360*pr),false,paint);}
   else{paint.setColor(0x33dcc48e);c.drawArc(ring,-90,360,false,paint);paint.setColor(0xffdcc48e);c.drawArc(ring,(elapsed%3000)/3000f*360-90,70,false,paint);}
   paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setFontFeatureSettings("tnum");paint.setTextSize(50);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(CREAM);
   long sec=elapsed/1000;c.drawText(String.format(Locale.US,"%d:%02d",sec/60,sec%60),cx,cy+14,paint);
   styledText(c,quick?OnlineBot.waitMillis/1000+"秒でボットと対局":"経過時間",cx,cy+38,10,0xffcfc3b0,false,Paint.Align.CENTER,null);
   // The player's komadai: name and rating on the left, the 王将 waiting on the right.
   RectF tray=trayRect();inkCard(c,tray,8,false);OnlineAccount a=online.account;
   if(a!=null){styledText(c,shortName(a.name,12),26,tray.top+24,14,0xfff3e6cf,true,Paint.Align.LEFT,null);styledText(c,"レート "+a.ratingText(),26,tray.top+42,10.5f,0xffcfc3b0,false,Paint.Align.LEFT,null);}
   drawPiece(c,8,new RectF(tray.right-60,tray.top+5,tray.right-16,tray.bottom-5),tray.height()-12);
   float y=tray.bottom+26;styledText(c,online.status.isEmpty()?"開始しています…":online.status,200,y,12.5f,0xfff3e6cf,true,Paint.Align.CENTER,null);
   if(quick)styledText(c,"相手が"+OnlineBot.waitMillis/1000+"秒見つからない時は、レートの近いボットと対局します",200,y+19,9.5f,0xffcfc3b0,false,Paint.Align.CENTER,null);
   else if(online.seekMode==Netplay.GUEST&&online.roomCode!=null)styledText(c,"ルーム "+online.roomCode,200,y+19,10.5f,0xffcfc3b0,false,Paint.Align.CENTER,null);
   if(!online.notice.isEmpty())text(c,online.notice,200,y+36,10,0xffffb9b3);
   styledText(c,online.net==null?"未接続":"公開リレー "+online.relaysOpen+"/"+online.relaysTotal,24,cancelRect().centerY()+4,10,online.relaysOpen>0?0xff9be59b:0xffffd471,false,Paint.Align.LEFT,null);
   bigButton(c,cancelRect(),RED,"中止",22);
  }
  /** Large title: the black weight in champagne with a soft drop shadow. */
  void goldTitle(Canvas c,String s,float x,float y,float size){paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setTextSize(size);paint.setLetterSpacing(.14f);paint.setTextAlign(Paint.Align.CENTER);
   paint.setColor(0x8c000000);c.drawText(s,x,y+1.5f,paint);paint.setShader(new LinearGradient(0,y-size*.8f,0,y,0xfff2e6c6,0xffccb07a,Shader.TileMode.CLAMP));c.drawText(s,x,y,paint);paint.setShader(null);}
  static final int GOLDEN=0,RED=1;
  /** Large lacquer button: drop shadow, three-stop body, glossy top half, light rim, outlined white label. */
  void bigButton(Canvas c,RectF r,int kind,String label,float size){float rad=Math.min(10,r.height()*.24f);
   paint.reset();paint.setAntiAlias(true);paint.setColor(0x73000000);c.drawRoundRect(new RectF(r.left,r.top+2.5f,r.right,r.bottom+3.5f),rad,rad,paint);paint.setColor(Color.BLACK);
   paint.setShader(new LinearGradient(0,r.top,0,r.bottom,kind==GOLDEN?CHAMPAGNE:CRIMSON,new float[]{0,.55f,1},Shader.TileMode.CLAMP));c.drawRoundRect(r,rad,rad,paint);paint.setShader(null);
   // A thin bevel instead of gloss: one light line under the top edge and a dark one above the bottom.
   paint.setColor(kind==GOLDEN?0x80fff8e6:0x40ffd9c8);c.drawRect(r.left+rad,r.top+1.2f,r.right-rad,r.top+2,paint);paint.setColor(0x40000000);c.drawRect(r.left+rad,r.bottom-2,r.right-rad,r.bottom-1.2f,paint);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);paint.setColor(kind==GOLDEN?0xe6f6ead0:CHAMPAGNE_RIM);c.drawRoundRect(r,rad,rad,paint);
   paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setTextSize(size);paint.setLetterSpacing(.1f);paint.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics m=paint.getFontMetrics();float base=r.centerY()-(m.ascent+m.descent)/2;
   if(kind==GOLDEN){paint.setColor(0x66fff8e6);c.drawText(label,r.centerX(),base+1,paint);paint.setColor(0xff2b1a0c);}else{paint.setColor(0x80000000);c.drawText(label,r.centerX(),base+1.2f,paint);paint.setColor(CREAM);}
   c.drawText(label,r.centerX(),base,paint);}
  /** Mode tile: red lacquer with a gold rim when chosen, dark ink otherwise; a drawn icon and a label. */
  void modeTileDraw(Canvas c,RectF r,int kind,boolean on){
   paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,r.top,0,r.bottom,on?CRIMSON:INKS,new float[]{0,.55f,1},Shader.TileMode.CLAMP));c.drawRoundRect(r,9,9,paint);paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(on?1.4f:1);paint.setColor(on?CHAMPAGNE_RIM:MUTED_RIM);c.drawRoundRect(r,9,9,paint);
   int ink=on?CREAM:0xff8f8270;float cx=r.left+40,cy=r.centerY();paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setColor(ink);paint.setStrokeCap(Paint.Cap.ROUND);
   if(kind==0){float R=15;c.drawCircle(cx,cy,R,paint);c.drawOval(new RectF(cx-R*.45f,cy-R,cx+R*.45f,cy+R),paint);c.drawLine(cx-R,cy,cx+R,cy,paint);}
   else{for(int k=0;k<2;k++){float hx=cx-8+k*16,hy=cy-6;c.drawCircle(hx,hy,5.5f,paint);c.drawArc(new RectF(hx-10,hy+8,hx+10,hy+26),200,140,false,paint);}}
   paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setTextSize(17);paint.setLetterSpacing(.06f);paint.setColor(ink);c.drawText(kind==0?"ランダム":"ルーム",r.left+66,cy+6,paint);}
  /** Time-control segment: big minutes and a small caption; chosen in red lacquer. */
  void segmentDraw(Canvas c,RectF r,String big,String small,boolean on){
   paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,r.top,0,r.bottom,on?CRIMSON:INKS,new float[]{0,.55f,1},Shader.TileMode.CLAMP));c.drawRoundRect(r,8,8,paint);paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(on?1.4f:1);paint.setColor(on?CHAMPAGNE_RIM:MUTED_RIM);c.drawRoundRect(r,8,8,paint);
   paint.reset();paint.setAntiAlias(true);paint.setTypeface(Fonts.black);paint.setTextSize(18);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(on?CREAM:0xff8f8270);c.drawText(big,r.centerX(),r.top+r.height()*.5f+2,paint);
   styledText(c,small,r.centerX(),r.bottom-7,9,on?0xccead8c4:0xff8f8270,false,Paint.Align.CENTER,null);}
  // ---- online game screen: header, player cards, komadai, turn pill and result card ----
  static final int GOLD=0xffdcc48e,CREAM=0xfff4ead8,INK=0xff2b1a0c;
  /** Reduced palette: one crimson lacquer, one champagne gold, warm ink; no orange, no white gloss. */
  static final int[] CRIMSON={0xff962a20,0xff74190f,0xff530f0a},CHAMPAGNE={0xffeedfb6,0xffd6bd86,0xffae8d55},INKS={0xff2a221f,0xff191311,0xff0e0a09};
  static final int CHAMPAGNE_RIM=0xccecdcb2,MUTED_RIM=0x66b89a62;
  float onlineStatusY(){return bottomHandY()+100;}
  void pill(Canvas c,RectF r,int fill,int stroke){paint.reset();paint.setAntiAlias(true);paint.setColor(fill);c.drawRoundRect(r,r.height()/2,r.height()/2,paint);if(stroke!=0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(stroke);c.drawRoundRect(r,r.height()/2,r.height()/2,paint);}}
  float textWidth(String s,float size,boolean bold){paint.reset();paint.setTextSize(size);paint.setTypeface(bold?Fonts.bold:Fonts.medium);return paint.measureText(s);}
  void styledText(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align,Typeface face){paint.reset();paint.setAntiAlias(true);paint.setTextSize(size);paint.setColor(color);paint.setTypeface(face!=null?face:bold?Fonts.bold:Fonts.medium);paint.setTextAlign(align);c.drawText(s,x,y,paint);}
  void onlineTopBar(Canvas c){OnlineGame g=online.game;
   paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(0,0,0,32,0xf2241010,0xf2110707,Shader.TileMode.CLAMP));c.drawRect(4,0,396,32,paint);paint.setShader(null);paint.setColor(0x80b58a47);c.drawRect(4,31,396,32,paint);
   styledText(c,"‹ ロビー",14,21,11,0xfff5e4c8,true,Paint.Align.LEFT,null);
   boolean bot=online.bot!=null;int dot=bot?0xffdcb86c:online.direct?0xff6ee07a:g.started?0xffffc857:0xffaaaaaa;String route=bot?"ボット対局":(online.direct?"P2P直結":g.started?"リレー経由":"接続中…")+(online.rtt>=0&&g.started?" "+online.rtt+"ms":"");
   float w=textWidth(route,10,true)+30,cx=200;pill(c,new RectF(cx-w/2,6,cx+w/2,26),0x99000000,(dot&0x00ffffff)|0x99000000);paint.reset();paint.setAntiAlias(true);paint.setColor(dot);c.drawCircle(cx-w/2+11,16,3.5f,paint);
   styledText(c,route,cx-w/2+20,20,10,CREAM,true,Paint.Align.LEFT,null);
   if(!g.over()||!g.peerLeft){boolean resignButton=!g.over();button(c,new RectF(320,5,391,27),resignButton?DANGER:g.rematchMine?SECONDARY:PRIMARY,resignButton?"投了":g.rematchMine?"申込済":"再戦",11);}
  }
  /** Pieces in hand on a lacquer tray: the player's on the right, the opponent's mirrored to the left as across a real board. */
  void onlineKomadai(Canvas c,int side,float y){boolean me=side==viewSide();RectF r=new RectF(4,y,396,y+44);
   inkCard(c,r,8,false);
   int count=handCount(side);if(count==0){styledText(c,"持ち駒なし",me?378:22,y+27,9,0x80fff1d6,false,me?Paint.Align.RIGHT:Paint.Align.LEFT,null);return;}
   int i=0;for(int n=1;n<=handLimit();n++)if(game.hands[side==1?0:1][n]>0){float x=me?handX(i,count):400-handX(i,count);
    if(selectedSide==side&&selectedHand==n){paint.reset();paint.setAntiAlias(true);paint.setColor(0x73ffd27a);c.drawRoundRect(new RectF(x-19,y+2,x+19,y+42),7,7,paint);}
    long frame=frameTime();int k=game.hands[side==1?0:1][n]-(effects.handPending(side,n,frame)?1:0);float pop=effects.handScale(side,n,frame);
    RectF dst=new RectF(x-16,y+3,x+16,y+37);if(k>0)drawPiece(c,side*n,dst,commonHeight(dst.width(),dst.height())*pop);
    if(k>1){paint.reset();paint.setAntiAlias(true);paint.setColor(GOLD);c.drawCircle(x+13,y+37,6.5f,paint);styledText(c,""+k,x+13,y+40.5f,9,INK,true,Paint.Align.CENTER,null);}i++;}
  }
  /** Avatar, name, rating and side, and the clock: the side to move glows gold and turns red under 30 seconds. */
  void playerCard(Canvas c,int side,float y){OnlineGame g=online.game;boolean me=side==viewSide();OnlineAccount a=online.account;OnlineIdentity p=online.peer;
   boolean active=g.started&&!g.over()&&game.turn==side;long ms=Math.max(0,g.remaining(side,SystemClock.elapsedRealtime()));boolean low=g.started&&!g.over()&&ms<30000;
   String name=me?(a==null?"あなた":a.name):p==null?"対戦相手":p.name,seed=me?(a==null?"me":a.key.publicHex):p==null?"opponent":p.pubkey;
   String detail=me?(a==null?"":"レート"+a.ratingText()+" · "+a.wins+"勝"+a.losses+"敗"+a.draws+"分"):opponentDetail();
   RectF r=new RectF(4,y,396,y+44);
   inkCard(c,r,8,active);
   int h=7;for(int i=0;i<seed.length();i++)h=h*31+seed.charAt(i);float[] hsv={Math.abs(h%360),0.6f,0.8f};int top=Color.HSVToColor(hsv);hsv[2]=0.42f;int bottom=Color.HSVToColor(hsv);
   paint.reset();paint.setAntiAlias(true);paint.setShader(new LinearGradient(10,y+6,40,y+38,top,bottom,Shader.TileMode.CLAMP));c.drawCircle(25,y+22,15,paint);paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);paint.setColor(0xb3ffd27a);c.drawCircle(25,y+22,15,paint);
   styledText(c,name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1)),25,y+27.5f,14,Color.WHITE,true,Paint.Align.CENTER,null);
   String shown=shortName(name,10);styledText(c,shown,48,y+19,12.5f,CREAM,true,Paint.Align.LEFT,null);
   if(me){float nx=48+textWidth(shown,12.5f,true)+6;pill(c,new RectF(nx,y+8,nx+30,y+21),GOLD,0);styledText(c,"あなた",nx+15,y+18,8,INK,true,Paint.Align.CENTER,null);}
   styledText(c,detail.length()>30?detail.substring(0,29)+"…":detail,48,y+35,8.5f,0xc0fff1d6,false,Paint.Align.LEFT,null);
   styledText(c,side==1?"▲ 先手":"△ 後手",388,y+12,8.5f,0xccfff1d6,true,Paint.Align.RIGHT,null);
   long sec=(ms+999)/1000;String clock=String.format(Locale.US,"%02d:%02d",sec/60,sec%60);RectF pillRect=new RectF(318,y+16,390,y+40);
   pill(c,pillRect,low?0xffa8322a:active?GOLD:0x1fffffff,low||active?0:0x40ffd27a);styledText(c,clock,354,y+34,16,low?Color.WHITE:active?INK:CREAM,true,Paint.Align.CENTER,Fonts.bold);
  }
  void onlineStatus(Canvas c){OnlineGame g=online.game;playerCard(c,-viewSide(),34);playerCard(c,viewSide(),bottomHandY()+48);
   float y=onlineStatusY();String s=g.status(SystemClock.elapsedRealtime());long quiet=silence();if(g.started&&!g.over()&&quiet>9000)s+="・相手の応答なし "+quiet/1000+"秒";
   boolean check=g.myTurn()&&g.game.checked(g.mySide);
   if(!g.over()){float w=textWidth(s,12,true)+36;pill(c,new RectF(200-w/2,y,200+w/2,y+28),check?0xffa8322a:g.myTurn()?GOLD:0xa6000000,g.myTurn()?0:0x40ffd27a);styledText(c,s,200,y+19,12,g.myTurn()&&!check?INK:Color.WHITE,true,Paint.Align.CENTER,null);}
   float line=y+(g.over()?0:46);if(g.over())line=resultCard(c,g,y)+18;
   if(!online.notice.isEmpty()){text(c,online.notice,200,line,10,0xffffb9b3);line+=18;}if(g.error!=null){text(c,g.error,200,line,9,0xffffb9b3);line+=18;}
   if(!g.started){float cy=boardY+boardHeight/2;paint.reset();paint.setAntiAlias(true);paint.setColor(0xe0140808);c.drawRoundRect(new RectF(80,cy-34,320,cy+34),16,16,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(0x99ffd27a);c.drawRoundRect(new RectF(80,cy-34,320,cy+34),16,16,paint);
    styledText(c,"対局を準備しています…",200,cy+7,17,0xffe6d2a0,true,Paint.Align.CENTER,null);}
  }
  /** Returns the card's bottom edge. Buttons: 再戦する at x 24..196, ロビーへ at 204..376, y+112..y+146 (see onlineTouch). */
  float resultCard(Canvas c,OnlineGame g,float y){RectF card=new RectF(14,y,386,y+156);boolean win=g.game.winner==g.mySide,draw=g.game.winner==2;
   inkCard(c,card,10,true);
   String title=draw?"引き分け":win?"勝ち":"負け";effects.word(c,title,34,34,200,y+42,win?0xfffff2b3:0xfffff1d6,win?0xffc98a23:0xffaab4c8,1);
   styledText(c,g.game.result,200,y+60,10.5f,0xd9fff1d6,false,Paint.Align.CENTER,null);
   OnlineAccount.Game r=online.lastResult;
   if(r!=null){int d=r.after-r.before;String chip=(d>=0?"+":"")+d,before=r.before+"  →",after=""+r.after;float bw=textWidth(before,13,false),aw=textWidth(after,20,true),cw=textWidth(chip,12,true)+16,total=bw+8+aw+8+cw,x0=200-total/2;
    styledText(c,before,x0,y+87,13,0xa6fff1d6,false,Paint.Align.LEFT,null);styledText(c,after,x0+bw+8,y+88,20,GOLD,true,Paint.Align.LEFT,null);
    pill(c,new RectF(x0+bw+8+aw+8,y+73,x0+total,y+91),d>=0?0xd92e9e4a:0xd9c8372d,0);styledText(c,chip,x0+bw+8+aw+8+cw/2,y+86,12,Color.WHITE,true,Paint.Align.CENTER,null);}
   else if(!online.rated)styledText(c,"フレンド対戦・レート変動なし",200,y+86,10,0xb3fff1d6,false,Paint.Align.CENTER,null);
   if(g.rematchMine)styledText(c,"再戦を申し込みました。相手の返事を待っています",200,y+105,9,CREAM,false,Paint.Align.CENTER,null);
   else if(g.rematchPeer)styledText(c,"相手が再戦を希望しています",200,y+105,9.5f,GOLD,true,Paint.Align.CENTER,null);
   float by=y+112;if(!g.peerLeft&&!g.rematchMine)button(c,new RectF(24,by,196,by+34),PRIMARY,"再戦する",12.5f);
   button(c,new RectF(204,by,376,by+34),SECONDARY,"ロビーへ",12.5f);
   return y+156;
  }
  /** The opponent card's second line (the name is shown above it): "レート1520（暫定）· レート戦 · 対戦成績…". */
  String opponentDetail(){OnlineIdentity p=online.peer;if(online.bot!=null&&p!=null){int[] h=online.account==null?new int[3]:online.account.headToHead(p.pubkey);return "ボット Lv."+online.bot.level+" · レート"+p.rating+" · レート戦"+(h[0]+h[1]+h[2]>0?" · 対戦成績"+h[0]+"勝"+h[1]+"敗"+h[2]+"分":"");}if(p==null)return online.rated?"相手の情報を確認しています…":"フレンド対戦（レート変動なし）";
   int[] h=online.account==null?new int[3]:online.account.headToHead(p.pubkey);return "レート"+p.rating+(p.games<Rating.PROVISIONAL_GAMES?"（暫定）":"")+(online.rated?" · レート戦":" · レート変動なし")+(h[0]+h[1]+h[2]>0?" · 対戦成績"+h[0]+"勝"+h[1]+"敗"+h[2]+"分":"");}
  @Override public boolean performClick(){super.performClick();return true;}
 }
}
