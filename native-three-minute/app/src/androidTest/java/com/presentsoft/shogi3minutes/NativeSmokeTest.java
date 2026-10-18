package com.presentsoft.shogi3minutes;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.io.*;import java.util.*;
public final class NativeSmokeTest extends Instrumentation {
 MainActivity activity;MainActivity.BoardView v;int assertions;
 void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 void main(Runnable r){runOnMainSync(r);}
 void tap(float x,float y){main(()->{long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,v.offsetX+x*v.scale,(y-(v.mode==MainActivity.STUDY?v.studyScroll:0))*v.scale,0);v.onTouchEvent(down);down.recycle();MotionEvent e=MotionEvent.obtain(t,t+1,MotionEvent.ACTION_UP,v.offsetX+x*v.scale,(y-(v.mode==MainActivity.STUDY?v.studyScroll:0))*v.scale,0);v.onTouchEvent(e);e.recycle();});}
 void realSquareTap(int r,int c){float[] xy=new float[2];main(()->{int[] location=new int[2];v.getLocationOnScreen(location);RectF cell=v.cell(r,c);xy[0]=location[0]+v.offsetX+cell.centerX()*v.scale;xy[1]=location[1]+(cell.centerY()-v.studyScroll)*v.scale;});long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy[0],xy[1],0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(down,true),"OS touch down injected");down.recycle();SystemClock.sleep(40);MotionEvent up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,xy[0],xy[1],0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(up,true),"OS touch up injected");up.recycle();waitForIdleSync();}
 void home(){main(()->activity.onBackPressed());waitForIdleSync();}
 void graphTap(int index){RectF chart=v.graphRect();tap(v.graphPointX(index)+.1f,chart.centerY());}
 void realGraphTap(int index){main(()->v.scrollStudy(Math.max(0,v.graphRect().bottom-v.H+8)));float[] xy=new float[2];main(()->{int[] location=new int[2];v.getLocationOnScreen(location);xy[0]=location[0]+v.offsetX+(v.graphPointX(index)+.1f)*v.scale;xy[1]=location[1]+(v.graphRect().centerY()-v.studyScroll)*v.scale;});
  long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy[0],xy[1],0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(down,true),"OS graph touch down injected");down.recycle();SystemClock.sleep(40);MotionEvent up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,xy[0],xy[1],0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(up,true),"OS graph touch up injected");up.recycle();waitForIdleSync();
 }
 void square(int r,int c){RectF f=v.cell(r,c);tap(f.centerX(),f.centerY());}
 void screenshot(String name)throws Exception{java.util.concurrent.CountDownLatch frames=new java.util.concurrent.CountDownLatch(1);main(()->{v.invalidate();Choreographer.getInstance().postFrameCallback(t->Choreographer.getInstance().postFrameCallback(t2->frames.countDown()));});check(frames.await(3,java.util.concurrent.TimeUnit.SECONDS),"frame rendered before screenshot");waitForIdleSync();SystemClock.sleep(350);android.view.accessibility.AccessibilityNodeInfo active=null;long focusEnd=SystemClock.elapsedRealtime()+3000;do{active=getUiAutomation().getRootInActiveWindow();if(active!=null&&"com.presentsoft.shogi3minutes".contentEquals(active.getPackageName()))break;SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<focusEnd);check(active!=null&&"com.presentsoft.shogi3minutes".contentEquals(active.getPackageName()),"screenshot foreground belongs to game; active package="+(active==null?"null":active.getPackageName()));Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"screenshot exists");File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 void fixture(){main(()->{v.cancelCpu();v.game.restart();for(int[] a:v.game.board)Arrays.fill(a,0);v.game.board[8][4]=8;v.game.board[0][4]=-8;v.started=true;v.clock=new MatchClock(v.game);v.clock.resume(SystemClock.elapsedRealtime(),true);v.clearSelection();});}
 void ready() {long until=SystemClock.elapsedRealtime()+20000;while(!v.ai.ready&&v.aiError==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);main(()->check(v.ai.ready,"native model ready: "+v.aiError));}
 void chooseMode(int mode){tap(200,(mode==MainActivity.MATCH?v.homeMatchY():v.homeStudyY())+26);main(()->check(v.mode==mode,"mode selected by touch"));}
 void clickDialog(String text){
  java.util.concurrent.atomic.AtomicBoolean clicked=new java.util.concurrent.atomic.AtomicBoolean();long end=SystemClock.elapsedRealtime()+10000;
  while(!clicked.get()&&SystemClock.elapsedRealtime()<end){
   main(()->{AlertDialog dialog=activity.choiceDialog;if(dialog==null||!dialog.isShowing())return;
    for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){android.widget.Button button=dialog.getButton(which);
     if(button!=null&&text.contentEquals(button.getText())&&button.isShown()&&button.isEnabled()){clicked.set(button.performClick());break;}
    }
   });if(!clicked.get())SystemClock.sleep(50);
  }
  check(clicked.get(),"visible native dialog button clicked: "+text);waitForIdleSync();
 }
 void longPress(int r,int c){RectF f=v.cell(r,c);main(()->{long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,v.offsetX+f.centerX()*v.scale,(f.centerY()-(v.mode==MainActivity.STUDY?v.studyScroll:0))*v.scale,0);v.onTouchEvent(e);e.recycle();});
  java.util.concurrent.atomic.AtomicBoolean opened=new java.util.concurrent.atomic.AtomicBoolean();long end=SystemClock.elapsedRealtime()+3500;
  while(SystemClock.elapsedRealtime()<end){main(()->opened.set(v.dialogOpen));if(opened.get())break;SystemClock.sleep(25);}
  main(()->{long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_UP,v.offsetX+f.centerX()*v.scale,(f.centerY()-(v.mode==MainActivity.STUDY?v.studyScroll:0))*v.scale,0);v.onTouchEvent(e);e.recycle();});waitForIdleSync();check(opened.get(),"long press opens promotion dialog");
 }
 void studyTests(Intent intent)throws Exception{
  main(()->check(v.mode==MainActivity.HOME,"cold launch presents mode selection"));screenshot("00_mode_selection.png");chooseMode(MainActivity.STUDY);soundReady();main(()->v.restart());
  main(()->{for(int r=0;r<9;r++)for(int c=0;c<9;c++)v.game.board[r][c]=(r<4?-1:1);});
  int selectionSounds=v.sounds.playCount;for(int r=0;r<9;r++)for(int c=0;c<9;c++){final int row=r,col=c;RectF f=v.cell(r,c);main(()->v.clearSelection());tap(f.left+f.width()*.03f,f.top+f.height()*.97f);main(()->check(v.selectedR==row&&v.selectedC==col,"rectangular cell touch near lower corner selects correct square"));}
  main(()->{check(v.sounds.playCount==selectionSounds,"selection alone is silent");v.restart();});int editSounds=v.sounds.playCount;
  square(6,0);square(4,0);square(4,0);square(3,0);square(2,1);square(4,1);main(()->check(v.game.board[3][0]==1&&v.game.board[4][1]==-1,"both sides move freely without alternation"));main(()->check(v.sounds.playCount==editSounds+3,"both sides free moves play exactly once"));
  square(3,0);square(4,1);main(()->check(v.game.hands[0][1]==1,"study captures to sente hand"));tap(v.handX(0,v.handCount(1)),v.bottomHandY()+22);square(5,1);main(()->check(v.game.board[5][1]==1&&v.game.board[6][1]==1,"sente drop ignores nifu in editor"));main(()->check(v.sounds.playCount==editSounds+5,"study capture and hand drop play once each"));
  square(6,2);square(5,2);square(2,2);square(5,2);main(()->check(v.game.hands[1][1]==1,"study captures to gote hand"));tap(v.handX(0,v.handCount(-1)),v.topHandY()+22);square(1,2);main(()->check(v.game.board[1][2]==-1&&v.game.board[5][2]==-1,"upper gote hand is selectable and drops freely"));
  longPress(6,3);clickDialog("成る");main(()->check(v.game.board[6][3]==9,"long press promotes freely"));longPress(6,3);clickDialog("成りを戻す");main(()->check(v.game.board[6][3]==1,"long press unpromotes"));graphTap(v.evalHistory.cursor-1);main(()->check(v.game.board[6][3]==9,"undo restores promoted piece"));
  int replies=v.nativeReplies;long[] times=v.game.remaining.clone();SystemClock.sleep(400);main(()->{check(v.nativeReplies==replies,"no CPU in study");check(Arrays.equals(v.game.remaining,times),"no clocks in study");v.save();});screenshot("06_study_free_moves.png");
  main(()->callActivityOnPause(activity));SystemClock.sleep(200);main(()->callActivityOnResume(activity));main(()->check(v.mode==MainActivity.STUDY&&Arrays.equals(v.game.remaining,times),"study resume does not start clock"));
  main(()->activity.finish());waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();main(()->check(v.mode==MainActivity.HOME,"relaunch asks mode again"));chooseMode(MainActivity.STUDY);main(()->check(v.game.board[6][3]==9&&v.study.undoCount()>0,"study position and undo survive relaunch"));
  home();chooseMode(MainActivity.MATCH);ready();main(()->{check(v.game==v.match&&v.game.board[6][0]==1&&v.game.board[2][1]==-1,"match independent of editor");check(v.game.remaining[0]==180000&&v.game.remaining[1]==180000,"editing preserves match clocks");});
  java.util.concurrent.atomic.AtomicBoolean stale=new java.util.concurrent.atomic.AtomicBoolean();main(()->v.ai.search(UsiPosition.sfen(v.game),100000000,5000,(answer,error)->stale.set(true)));SystemClock.sleep(30);home();chooseMode(MainActivity.STUDY);java.util.concurrent.CountDownLatch drain=new java.util.concurrent.CountDownLatch(1);v.ai.worker.execute(drain::countDown);check(drain.await(2,java.util.concurrent.TimeUnit.SECONDS),"mode switch cancels native search");waitForIdleSync();main(()->check(!stale.get()&&v.mode==MainActivity.STUDY,"no old CPU callback in study"));home();chooseMode(MainActivity.MATCH);ready();
 }
 void analysisReady() {
  long until=SystemClock.elapsedRealtime()+10000;java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
  while(SystemClock.elapsedRealtime()<until){main(()->done.set(v.analyzing&&v.analysisResult!=null&&v.analysisResult.candidates.size()==3));if(done.get())break;SystemClock.sleep(40);}
  main(()->{check(v.analysisResult!=null&&v.analyzing,"study analysis streaming: "+v.analysisStatus+" / "+v.aiError);check(v.analysisResult.candidates.size()==3,"three native MultiPV candidates and scores");check(v.analyzedPosition.equals(UsiPosition.sfen(StudyAnalysis.snapshot(v.game,v.analysisSide))),"displayed result belongs to current board and side");int depth=v.analysisResult.candidates.get(0).depth;HashSet<String> roots=new HashSet<>();for(StudyAnalysis.Candidate c:v.analysisResult.candidates){check(c.depth==depth&&roots.add(c.move),"same-depth distinct candidate roots");check(UsiPosition.legalMove(v.analysisBoard,c.move)!=null,"native study candidate is legal in Java rules");}});
 }
 void studyAnalysisTests()throws Exception{
  home();chooseMode(MainActivity.STUDY);main(()->{v.restart();v.setAnalysisSide(1);});analysisReady();
  int replies=v.nativeReplies;long[] clocks=v.game.remaining.clone();String initial=v.analyzedPosition;int sounds=v.sounds.playCount;
  screenshot("07_study_analysis_initial.png");
  square(6,2);square(5,2);main(()->{check(v.analysisResult==null&&v.analyzing,"editing immediately clears old evaluation");check(v.analysisSide==-1,"after sente edit analyse gote response");});analysisReady();main(()->check(!v.analyzedPosition.equals(initial),"move triggers new SFEN analysis"));
  screenshot("08_study_analysis_gote.png");tap(324,v.analysisY()+65);analysisReady();main(()->check(v.analysisSide==1,"analysis side changes by tapping panel header"));
  square(6,6);square(4,6);square(4,6);square(3,6);graphTap(v.evalHistory.cursor-1);analysisReady();main(()->check(v.game.board[4][6]==1&&v.game.board[3][6]==0,"rapid edits and undo keep latest board only"));
  longPress(6,3);clickDialog("成る");analysisReady();main(()->check(v.game.board[6][3]==9,"promotion change is reanalysed"));
  main(()->v.restart());square(6,0);square(2,0);clickDialog("成らない");longPress(2,0);clickDialog("成る");tap(v.handX(0,v.handCount(1)),v.bottomHandY()+22);square(5,0);analysisReady();main(()->{check(v.game.hands[0][1]==0&&v.game.board[5][0]==1,"analysis follows valid capture/promotion/hand drop");check(v.nativeReplies==replies&&Arrays.equals(v.game.remaining,clocks),"analysis never moves pieces or changes clocks");});
  main(()->{v.game.board[0][4]=0;v.refreshAnalysis();});SystemClock.sleep(350);main(()->check(v.analysisResult==null&&!v.analyzing&&v.analysisStatus.contains("玉"),"kingless editor board shows truthful status without engine crash"));
  main(()->{v.restart();v.study.move(6,0,5,1);v.refreshAnalysis();});SystemClock.sleep(350);main(()->check(v.analysisResult==null&&!v.analyzing&&v.analysisStatus.contains("二歩"),"nifu editor position explains unavailable evaluation"));screenshot("09_study_invalid_position.png");
  main(()->{for(int[] row:v.game.board)Arrays.fill(row,0);for(int[] hand:v.game.hands)Arrays.fill(hand,0);v.game.board[8][8]=8;v.game.board[0][0]=-8;v.game.board[2][1]=5;v.setAnalysisSide(-1);});
  long end=SystemClock.elapsedRealtime()+5000;while(v.analysisResult==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);
  main(()->{check(v.analysisResult!=null&&v.analysisResult.candidates.size()==1,"single legal move gives one scored candidate");check(UsiPosition.legalMove(v.analysisBoard,v.analysisResult.candidates.get(0).move)!=null,"single native candidate legal");v.game.board[2][1]=0;v.game.board[1][1]=5;v.game.board[3][2]=3;v.refreshAnalysis();});SystemClock.sleep(350);
  main(()->{check(v.analysisResult==null&&!v.analyzing&&v.analysisStatus.contains("詰み"),"checkmate shows terminal status without fabricated evaluation");v.restart();v.setAnalysisSide(1);});analysisReady();
  int revision=v.analysisRevision;main(()->{v.refreshAnalysis();callActivityOnPause(activity);});SystemClock.sleep(250);main(()->{check(!v.analyzing&&v.analysisRevision>revision,"background cancels pending study search");callActivityOnResume(activity);});analysisReady();
  main(()->check(v.sounds.playCount>sounds,"user placement sounds remain enabled during study analysis"));
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"study_analysis.txt"))){out.println("PASS native TsuyouraOu11micro MultiPV=3: initial/edit/free consecutive moves/promotion/capture/drop/undo/reset/side toggle/resume; all candidates Java-legal, distinct and same-depth; current SFEN only; invalid boards explained; no automatic moves or clocks.");for(StudyAnalysis.Candidate c:v.analysisResult.candidates)out.println(c.rank+"\t"+c.move+"\t"+c.score(v.analysisSide)+"\tdepth="+c.depth);}
  screenshot("10_study_analysis_final.png");screenshot("19_reference_study_layout.png");home();chooseMode(MainActivity.MATCH);ready();
 }
 void studyRecoveryTests()throws Exception{
  home();chooseMode(MainActivity.STUDY);
  main(()->{v.restart();v.setAnalysisSide(-1);});analysisReady();
  realSquareTap(6,7);realSquareTap(5,7);main(()->check(v.game.board[5][7]==1&&v.game.board[6][7]==0,"actual OS-routed screen taps select and move pawn"));analysisReady();
  main(()->{v.restart();v.setAnalysisSide(-1);});analysisReady();
  main(()->{v.game.board[6][7]=0;v.game.board[4][7]=1;v.game.board[2][6]=0;v.game.board[3][6]=-1;v.refreshAnalysis();});analysisReady();
  screenshot("14_reported_position.png");
  UsiProcess previous=v.ai.engine;main(()->{previous.close();check(!previous.alive(),"closed engine is unavailable immediately");v.refreshAnalysis();});analysisReady();main(()->check(v.ai.engine!=previous&&v.ai.ready,"dead engine replaced before study search"));
  int recovered=v.ai.restarts,oldUpdates=v.analysisUpdates;UsiProcess failed=v.ai.engine;main(()->v.ai.engine.lines.offer("__EOF__"));
  long recoveredEnd=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<recoveredEnd&&(v.ai.engine==failed||v.analysisUpdates<=oldUpdates))SystemClock.sleep(40);analysisReady();main(()->check(v.ai.restarts==recovered+1&&v.aiError==null&&v.ai.engine!=failed,"continuous protocol failure automatically restarts once and recovers percentages"));
  main(()->{v.cancelCpu();v.aiError="診断テスト: native process exit=9";v.analysisResult=null;v.analyzing=false;v.analysisStatus="AIエラー・タップで再試行";v.invalidate();});tap(200,v.analysisY()+66);clickDialog("再試行");analysisReady();main(()->check(v.aiError==null&&v.displayScore!=null,"error details retry recovers graph and percentages"));
  main(()->{for(int i=0;i<12;i++)v.setAnalysisSide(i%2==0?1:-1);});analysisReady();main(()->check(v.analysisSide==-1&&v.analysisBoard.turn==-1,"rapid switches retain latest analysis only"));
  screenshot("15_ai_recovery.png");
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"study_recovery.txt"))){out.println("PASS reported screenshot position; dead native process replacement; injected EOF automatic single retry; error-details manual retry; rapid side switches; percentages and graph recovered.");}
  home();chooseMode(MainActivity.MATCH);ready();
 }
 void promotionDialogVisible(){main(()->{
  check(v.dialogOpen&&activity.choiceDialog!=null&&activity.choiceDialog.isShowing(),"promotion popup visible after destination tap");
  check("成る".contentEquals(activity.choiceDialog.getButton(AlertDialog.BUTTON_POSITIVE).getText())&&"成らない".contentEquals(activity.choiceDialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText()),"popup offers both promote and do-not-promote");
 });}
 void studyPromotionFixture(int side,int type,int sr,int sc,int tr,int tc){main(()->{
  v.cancelCpu();v.study.reset();for(int[] row:v.game.board)Arrays.fill(row,0);v.game.board[8][8]=8;v.game.board[0][8]=-8;
  v.game.board[sr][sc]=side*type;v.game.board[tr][tc]=-side*4;v.evalHistory.reset(EvaluationHistory.key(v.game));v.clearSelection();v.displayScore=null;v.analysisResult=null;v.invalidate();
 });}
 void promotionPopupTests()throws Exception{
  home();chooseMode(MainActivity.STUDY);soundReady();
  for(int side:new int[]{1,-1})for(int type:new int[]{1,2,3,4,6,7})for(boolean promote:new boolean[]{false,true}){
   int sr=side==1?3:5,tr=side==1?2:6;studyPromotionFixture(side,type,sr,2,tr,2);int sounds=v.sounds.playCount;
   square(sr,2);square(tr,2);promotionDialogVisible();
   main(()->{check(v.game.board[sr][2]==side*type&&v.game.board[tr][2]==-side*4,"board unchanged until promotion choice");check(v.study.undoCount()==0&&v.evalHistory.cursor==0&&v.sounds.playCount==sounds,"pending choice does not edit undo, graph or sound");});
   if(side==1&&type==1&&!promote)screenshot("17_study_promotion_popup.png");
   clickDialog(promote?"成る":"成らない");main(()->{
    check(v.game.board[sr][2]==0&&v.game.board[tr][2]==side*(type+(promote?8:0)),"chosen form applied to either side and every promotable type");
    check(v.game.hands[side==1?0:1][4]==1&&v.study.undoCount()==1&&v.evalHistory.cursor==1,"capture plus promotion is one editor and graph entry");
    check(v.sounds.playCount==sounds+1,"one committed move produces one sound");
    check(v.piecePath(v.game.board[tr][2]).contains(promote?"promoted/":"normal/"),"selected form selects its dedicated image");
   });
   if(side==1&&type==1&&promote)screenshot("18_study_promoted_piece.png");
   graphTap(v.evalHistory.cursor-1);main(()->check(v.game.board[sr][2]==side*type&&v.game.board[tr][2]==-side*4&&v.game.hands[side==1?0:1][4]==0&&v.study.undoCount()==0&&v.evalHistory.cursor==0,"one Undo restores source, captured piece, hand, form and graph"));
  }
  studyPromotionFixture(1,1,3,2,2,2);int sounds=v.sounds.playCount;square(3,2);square(2,2);promotionDialogVisible();clickDialog("キャンセル");
  main(()->check(v.game.board[3][2]==1&&v.game.board[2][2]==-4&&v.study.undoCount()==0&&v.sounds.playCount==sounds,"cancel preserves study board, history and sound"));
  for(int type:new int[]{5,8,9}){studyPromotionFixture(1,type,3,2,2,2);square(3,2);square(2,2);main(()->check(!v.dialogOpen&&v.game.board[2][2]==type,"gold, king and already promoted piece move without popup"));}
  studyPromotionFixture(1,1,3,2,4,2);square(3,2);square(4,2);main(()->check(!v.dialogOpen&&v.game.board[4][2]==1,"outside-zone move has no promotion popup"));
  studyPromotionFixture(1,1,1,2,3,2);square(1,2);square(3,2);promotionDialogVisible();clickDialog("成る");main(()->check(v.game.board[3][2]==9,"leaving enemy zone also offers promotion"));
  studyPromotionFixture(1,1,3,2,2,2);main(()->{v.game.board[2][2]=0;v.game.hands[0][1]=1;});tap(v.handX(0,v.handCount(1)),v.bottomHandY()+22);square(2,2);main(()->check(!v.dialogOpen&&v.game.board[2][2]==1&&v.game.hands[0][1]==0,"drop into enemy zone never prompts or promotes"));
  main(()->v.restart());home();chooseMode(MainActivity.MATCH);ready();
  int[][] moves={{1,3,0,2,0},{2,3,1,2,1},{3,4,2,2,1},{4,3,2,2,2},{6,3,3,2,2},{7,3,1,2,1}};
  for(int[] q:moves)for(boolean promote:new boolean[]{false,true}){
   fixture();main(()->{v.game.board[0][4]=0;v.game.board[0][8]=-8;v.game.board[q[1]][q[2]]=q[0];});square(q[1],q[2]);square(q[3],q[4]);promotionDialogVisible();
   main(()->check(v.game.board[q[1]][q[2]]==q[0]&&v.game.board[q[3]][q[4]]==0&&v.game.movesPlayed==0,"match waits for choice before committing"));
   if(q[0]==1&&!promote)screenshot("16_match_promotion_popup.png");
   clickDialog(promote?"成る":"成らない");main(()->{v.cancelCpu();check(v.game.board[q[3]][q[4]]==q[0]+(promote?8:0),"match applies chosen form for all six piece types");});
  }
  fixture();main(()->v.game.board[3][2]=4);square(3,2);square(2,2);promotionDialogVisible();clickDialog("キャンセル");main(()->check(v.game.board[3][2]==4&&v.game.board[2][2]==0&&v.game.movesPlayed==0,"cancel leaves match move uncommitted"));
  for(int[] q:new int[][]{{1,1,0,0,0},{2,1,0,0,0},{3,2,2,0,1}}){fixture();main(()->v.game.board[q[1]][q[2]]=q[0]);square(q[1],q[2]);square(q[3],q[4]);main(()->{v.cancelCpu();check(!v.dialogOpen&&v.game.board[q[3]][q[4]]==q[0]+8,"mandatory pawn, lance and knight promotion remains automatic");});}
  fixture();main(()->v.game.board[3][2]=4);square(3,2);square(2,2);promotionDialogVisible();main(()->{v.game.remaining[0]=100;v.clock.resume(SystemClock.elapsedRealtime(),true);});SystemClock.sleep(250);clickDialog("成る");main(()->check(v.game.winner==-1&&v.game.board[3][2]==4&&v.game.board[2][2]==0,"promotion popup does not pause match clock or allow a move after flag fall"));
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"promotion_popup.txt"))){out.println("PASS actual native popup and buttons: all six piece types; study sente/gote; promote/do-not-promote/cancel; dedicated promoted images; atomic capture/history/graph/undo/sound; leaving zone; no popup for drops/gold/king/already-promoted/outside-zone; match optional and mandatory promotion; time continues while choosing.");}
  main(()->v.restart());
 }
 void compatibilityTests()throws Exception{
  v.ai.worker.submit(()->{v.ai.discard();v.ai.compatibility=false;v.ai.context.getSharedPreferences("native_engine",0).edit().clear().commit();return null;}).get();
  main(()->v.configureAi());ready();UsiProcess primary=v.ai.engine;
  check(v.ai.engineFile().equals("libyaneuraou.so"),"normal engine selected before fatal signal");
  // Real SIGSEGV to the actual child process, rather than an invented result.
  int pid=-1;String path=new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"libyaneuraou.so").getPath();
  for(File dir:new File("/proc").listFiles())try{if(!dir.getName().matches("[0-9]+"))continue;byte[] bytes=java.nio.file.Files.readAllBytes(new File(dir,"cmdline").toPath());String cmd=new String(bytes,"UTF-8");if(cmd.startsWith(path+"\0")){pid=Integer.parseInt(dir.getName());break;}}catch(Exception ignored){}
  check(pid>0,"actual engine PID found for fatal-signal recovery test");android.system.Os.kill(pid,11);primary.process.waitFor();
  check(primary.exitCode()!=null&&primary.exitCode()==139,"actual native SIGSEGV reports exit 139");
  main(()->v.configureAi());ready();
  check(v.ai.compatibility&&v.ai.engineFile().equals("libyaneuraou_compat.so"),"dead native process selects actual scalar executable");
  check(new AiEngine(getTargetContext()).compatibility,"compatibility choice persists across app restart");
  StudyAnalysis.Result result=v.ai.engine.analyze(UsiPosition.sfen(new ShogiGame()),3000,750,()->false,3);
  check(result.candidates.size()==3,"scalar engine returns three candidates after native crash");
  for(StudyAnalysis.Candidate c:result.candidates)check(UsiPosition.legalMove(new ShogiGame(),c.move)!=null,"actual fallback candidate legal");
  v.ai.worker.submit(()->{v.ai.discard();v.ai.compatibility=false;v.ai.context.getSharedPreferences("native_engine",0).edit().clear().commit();return null;}).get();
  main(()->v.configureAi());ready();
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"native_compatibility.txt"))){out.println("PASS actual OS SIGSEGV of native child, exit=139, automatic scalar executable selection, 3 legal candidates, persisted compatibility across engine recreation. Injected signal verifies recovery; original Motorola crash cause remains unconfirmed.");}
 }
 void evaluationGraphTests(Intent intent)throws Exception{
  home();chooseMode(MainActivity.STUDY);main(()->{v.restart();v.setAnalysisSide(1);});analysisReady();
  main(()->{check(v.displayScore!=null&&v.evalHistory.current()!=null,"native result feeds percentage and history");check(v.displayScore.senteTenths()+v.displayScore.goteTenths()==1000,"displayed rounded percentages sum to 100 percent");});
  square(6,2);square(5,2);analysisReady();square(2,6);square(3,6);analysisReady();square(6,7);square(5,7);analysisReady();square(2,1);square(3,1);analysisReady();
  square(6,1);square(1,1);clickDialog("成らない");main(()->check(v.displayScore==null,"old percentage hidden during new analysis"));analysisReady();screenshot("11_evaluation_graph_sente.png");
  main(()->{check(v.evalHistory.cursor==5&&v.evalHistory.points.size()==6,"five edits create six graph positions");for(EvaluationHistory.Point point:v.evalHistory.points)check(point.score!=null,"actual native scores on each analysed edit");});
  square(2,7);square(7,7);clickDialog("成らない");analysisReady();screenshot("12_evaluation_graph_history.png");graphTap(v.evalHistory.cursor-1);analysisReady();main(()->check(v.evalHistory.cursor==5,"undo moves graph cursor with board"));
  square(6,4);square(5,4);analysisReady();main(()->{check(v.evalHistory.cursor==6&&v.evalHistory.points.size()==7,"new branch removes obsolete future graph point");check(v.displayScore.cp==v.evalHistory.current().cp,"bar and current graph point share the same sente score");v.save();});
  main(()->activity.finish());waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();chooseMode(MainActivity.STUDY);analysisReady();main(()->check(v.evalHistory.cursor==6&&v.evalHistory.points.size()==7,"graph and branch survive activity recreation"));screenshot("13_evaluation_graph_restored.png");
  String[] before=new String[1];main(()->{before[0]=EvaluationHistory.key(v.game);check(v.study.move(8,4,0,4),"free editor captures king for invalid-position fixture");v.evalHistory.advance(EvaluationHistory.key(v.game));v.refreshAnalysis();});SystemClock.sleep(350);main(()->{check(v.displayScore==null&&v.evalHistory.current()==null,"invalid board has no percentage or invented graph point");check(v.evalHistory.points.size()==8&&v.evalHistory.points.get(5).score!=null,"invalid edit preserves earlier graph samples");});graphTap(v.evalHistory.cursor-1);analysisReady();
  int[] actual={v.getWidth(),v.getHeight()};main(()->{v.onSizeChanged(400,720,0,0);check(v.maxStudyScroll()>40&&v.boardSize>399.9,"short screen preserves board width and enables scrolling");long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,200,650,0);v.onTouchEvent(down);down.recycle();for(int i=1;i<=4;i++){MotionEvent move=MotionEvent.obtain(t,t+20*i,MotionEvent.ACTION_MOVE,200,650-20*i,0);v.onTouchEvent(move);move.recycle();}MotionEvent up=MotionEvent.obtain(t,t+100,MotionEvent.ACTION_UP,200,570,0);v.onTouchEvent(up);up.recycle();check(v.studyScroll>0&&v.studyScroll<=v.maxStudyScroll(),"native swipe scrolls analysis without out-of-range offset");check(EvaluationHistory.key(v.game).equals(before[0]),"scrolling never edits board");});
  square(6,0);main(()->{check(v.selectedR==6&&v.selectedC==0,"scrolled board taps use content coordinates");v.clearSelection();v.studyScroll=0;v.onSizeChanged(actual[0],actual[1],0,0);});
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"evaluation_graph.txt"))){out.println("PASS native percentage bar, score-derived 100-percent complement, real micro score history, moves/captures/undo/branch/reset/recreation, invalid gaps, current-score consistency, full board width and short-screen swipe/tap coordinates.");out.println("Formula: 100/(1+exp(-sente_cp/600)); uncalibrated guide, not measured win probability. Finite cp display 0.1..99.9 percent; mate endpoints.");}
  main(()->v.restart());home();chooseMode(MainActivity.MATCH);ready();
 }
 void continuousStudyTests()throws Exception{
  home();chooseMode(MainActivity.STUDY);main(()->{v.restart();v.setAnalysisSide(1);});analysisReady();
  String key=EvaluationHistory.key(v.game);long[] clocks=v.game.remaining.clone();int old=v.analysisUpdates,revision=v.analysisRevision;long nodes=v.analysisResult.nodes;int depth=v.analysisResult.depth;UsiProcess process=v.ai.engine;
  screenshot("21_continuous_initial.png");SystemClock.sleep(2200);main(()->{check(v.analyzing&&v.analysisUpdates>old+1,"same visible position continues publishing after several seconds");check(v.ai.engine==process&&v.analysisRevision==revision,"same native process and search remain active");check(v.analysisResult.nodes>nodes&&v.analysisResult.depth>=depth,"real native search visits more nodes without depth regression");check(EvaluationHistory.key(v.game).equals(key)&&Arrays.equals(clocks,v.game.remaining),"continuous analysis never plays moves or consumes clocks");});screenshot("22_continuous_later.png");
  int count=v.evalHistory.points.size();tap(45,22);tap(200,22);tap(345,22);main(()->check(v.mode==MainActivity.STUDY&&v.evalHistory.points.size()==count&&EvaluationHistory.key(v.game).equals(key),"former mode/reset/undo hit areas have no toolbar actions"));
  square(6,0);square(2,0);clickDialog("成る");analysisReady();String capture=EvaluationHistory.key(v.game);tap(v.handX(0,v.handCount(1)),v.bottomHandY()+22);square(5,0);analysisReady();String dropped=EvaluationHistory.key(v.game);int samples=v.evalHistory.points.size();
  realGraphTap(0);analysisReady();main(()->{check(v.evalHistory.cursor==0&&EvaluationHistory.key(v.game).equals(key)&&v.game.hands[0][1]==0,"graph restores initial board, hands and unpromoted pawn");check(v.evalHistory.points.size()==samples,"graph jump retains future history");});
  realGraphTap(1);analysisReady();main(()->check(v.game.board[2][0]==9&&v.game.hands[0][1]==1&&EvaluationHistory.key(v.game).equals(capture),"graph restores promoted capture and captured hand"));
  realGraphTap(2);analysisReady();main(()->check(v.game.board[5][0]==1&&v.game.hands[0][1]==0&&EvaluationHistory.key(v.game).equals(dropped),"future drop position remains selectable after going back"));
  realGraphTap(1);analysisReady();screenshot("23_graph_selected_ply.png");square(6,1);square(5,1);analysisReady();main(()->check(v.evalHistory.cursor==2&&v.evalHistory.points.size()==3&&!v.evalHistory.points.get(2).key.equals(dropped),"new move at selected ply replaces obsolete continuation"));
  main(()->{v.save();callActivityOnPause(activity);});int stopped=v.analysisUpdates;SystemClock.sleep(350);main(()->{check(!v.analyzing&&v.analysisUpdates==stopped,"background cancels continuous search and stops UI publications");callActivityOnResume(activity);});analysisReady();
  try(PrintWriter out=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"continuous_study.txt"))){out.println("PASS actual native go infinite: multiple updates over 2.2 seconds, more nodes/nondecreasing depth, same process/search, no automatic moves/clocks; removed toolbar hit regions inactive; OS-routed graph taps restore initial/capture/promotion/drop/future; edit branches; pause/resume cancels and restarts current position.");}
  main(()->v.restart());home();chooseMode(MainActivity.MATCH);ready();
 }
 void soundReady(){long end=SystemClock.elapsedRealtime()+3000;while(!v.sounds.ready&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(20);main(()->check(v.sounds.ready,"SoundPool sample decoded and ready"));}
 void modelMigrationTest()throws Exception{File root=new File(getTargetContext().getFilesDir(),"ai"),dir=new File(root,"tsuyou11micro");org.json.JSONObject files=ModelAssets.provenance(getTargetContext()).getJSONObject("model").getJSONObject("files");for(String n:new String[]{"nn.bin","progress.bin","eval_options.txt","tag.json"}){File f=new File(dir,n);check(f.length()==files.getJSONObject(n).getLong("bytes")&&ModelAssets.sha(f).equals(files.getJSONObject(n).getString("sha256")),"both model and progress/metadata installed with correct SHA: "+n);}for(String n:new String[]{"suisho5","full","light"})check(!new File(root,n).exists(),"legacy model deleted: "+n);check(!new File(dir,"nn.bin.tmp").exists(),"stale model temp removed");check(v.ai.engine.initialization.toString().contains("loading progress file")&&v.ai.engine.initialization.toString().contains("progress.bin"),"progress-aware engine loaded progress parameters");File evidence=new File(getTargetContext().getExternalFilesDir(null),"model_installation.txt");try(PrintWriter out=new PrintWriter(evidence)){out.println("PASS 4 model assets SHA-256, same-size corrupted nn.bin repaired, stale temp removed, legacy 64.2 MB model deleted; progress.bin loaded by native engine");out.print(v.ai.engine.initialization);}}
 void layoutTests(){main(()->{int w=v.getWidth(),h=v.getHeight();for(int[] size:new int[][]{{320,568},{360,640},{400,720},{720,1280},{1080,2400},{1440,2560},{800,1280}}){v.onSizeChanged(size[0],size[1],0,0);check(v.boardX*v.scale+v.offsetX>=-.1,"board left fits");check((v.boardX+v.boardSize)*v.scale+v.offsetX<=size[0]+.1,"board right fits");check(v.topHandY()>=32,"player and clock below compact toolbar");check(v.bottomHandY()+68<=v.H+.01f,"bottom labels fit");float step=v.cell(0,0).height();check(step/v.cell(0,0).width()>1.04f&&step/v.cell(0,0).width()<1.06f,"portrait cell aspect ratio maintained");check(v.topHandY()+44<v.boardY&&v.bottomHandY()>v.boardY+v.boardHeight,"hands outside board");for(int row=0;row<9;row++)check(Math.abs(v.cell(row,0).height()-step)<.001,"all nine rows equal");}v.mode=MainActivity.STUDY;for(int[] size:new int[][]{{320,568},{360,640},{400,720},{720,1280},{1080,2400},{1440,2560},{800,1280}}){v.onSizeChanged(size[0],size[1],0,0);check(Math.abs(v.topHandY())<.01f,"study starts at player strip with no obsolete toolbar");check(v.analysisY()>v.bottomHandY()+44,"analysis panel below lower hand");check(v.analysisY()+MainActivity.BoardView.ANALYSIS_HEIGHT<=v.studyContentHeight+.01f,"graph panel fits scrollable content");check(v.analysisY()+MainActivity.BoardView.ANALYSIS_HEIGHT-v.maxStudyScroll()<=v.H+.01f,"whole graph reachable on short viewport");check((v.boardX+v.boardSize)*v.scale+v.offsetX<=size[0]+.1f,"study board width fits");check(v.boardSize>=399.99f,"study keeps full board width");}v.mode=MainActivity.MATCH;v.onSizeChanged(w,h,0,0);check((v.cell(0,8).right-v.cell(0,0).left)*v.scale>=v.getWidth()*.97,"grid fills at least 97 percent of width");check(v.boardPieceHeight()>=v.cell(0,0).height()*.94f,"pieces fill at least 94 percent of cell height");v.mode=MainActivity.STUDY;for(int side:new int[]{1,-1}){for(int n=1;n<=8;n++)v.game.hands[side==1?0:1][n]=1;int count=8;check(v.handX(0,count)-16>100,"all eight hand types clear player information");check(v.handX(count-1,count)+16<=396,"rightmost hand and count fit");for(int i=0;i<count;i++)check(v.tapHand(v.handX(i,count),v.bottomHandY()+22,side,v.bottomHandY())&&v.selectedHand==i+1,"each compact hand type independently selectable");Arrays.fill(v.game.hands[side==1?0:1],0);}v.mode=MainActivity.MATCH;v.clearSelection();});}
 void benchmark()throws Exception{
  String[] positions;try(InputStream in=getTargetContext().getAssets().open("ai/test_positions.txt");ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] data=new byte[2048];int n;while((n=in.read(data))!=-1)b.write(data,0,n);positions=b.toString("UTF-8").trim().split("\\n");}
  File f=new File(getTargetContext().getExternalFilesDir(null),"ai_benchmark.tsv");try(PrintWriter out=new PrintWriter(f)){
   out.println("model\tnode_limit\tposition\tmove\tactual_nodes\tsearch_ms\tdepth\tconfigure_ms");
   long start=SystemClock.elapsedRealtime();ready();long init=SystemClock.elapsedRealtime()-start;
   for(int budget:new int[]{500,3000,10000,50000})for(int p=0;p<positions.length;p++){
    String reply=v.ai.engine.search(positions[p],budget,750,()->false);String[] parts=reply.split("\\|");
    check(parts.length==4&&!parts[0].equals("none")&&!parts[0].equals("error"),"modern USI best move exists "+reply);
    check(Long.parseLong(parts[1])<=budget+Math.max(1024,budget/100),"cooperative node budget "+reply);
    out.println("yaneura-tsuyou11micro\t"+budget+"\t"+p+"\t"+reply.replace('|','\t')+"\t"+init);
   }
  }
  main(()->{v.aiModel=0;v.nodeBudget=50000;v.configureAi();});ready();
 }
 void cancellation()throws Exception{
  long began=SystemClock.elapsedRealtime();String cancelled=v.ai.engine.search(UsiPosition.sfen(v.game),100000000,5000,()->true);
  check(SystemClock.elapsedRealtime()-began<1000&&cancelled==null,"cancel before USI go is preserved");
  java.util.concurrent.atomic.AtomicBoolean stale=new java.util.concurrent.atomic.AtomicBoolean();
  main(()->v.ai.search(UsiPosition.sfen(v.game),100000000,5000,(answer,error)->stale.set(true)));
  SystemClock.sleep(30);main(()->v.restart());
  java.util.concurrent.CountDownLatch drained=new java.util.concurrent.CountDownLatch(1);v.ai.worker.execute(drained::countDown);
  check(drained.await(2,java.util.concurrent.TimeUnit.SECONDS),"USI search cancellation drains worker");waitForIdleSync();
  main(()->{check(!stale.get(),"old native callback discarded on restart");check(v.game.movesPlayed==0&&!v.started,"restart remains initial after cancellation");});
 }
 void uniformPieces()throws Exception{
  main(()->{float height=v.boardPieceHeight();
   for(int p:new int[]{1,2,3,4,5,6,7,8,-8,9,10,11,12,14,15})for(int side:new int[]{1,-1})for(int row=0;row<9;row++){
    int piece=p*side;RectF cell=v.cell(row,4),image=v.pieceImageRect(piece,cell,height);Bitmap bitmap=v.load(v.piecePath(piece));Rect ink=v.inkBounds.get(v.piecePath(piece));float factor=image.height()/bitmap.getHeight();
    check(Math.abs(ink.height()*factor-height)<.001f,"all types and ranks share visible height");check(ink.width()*factor<=cell.width()&&ink.height()*factor<=cell.height(),"visible piece stays inside every row");check(Math.abs(image.width()/image.height()-(float)bitmap.getWidth()/bitmap.getHeight())<.001f,"generated sprite aspect ratio preserved");check((float)ink.height()/ink.width()>1.04f&&(float)ink.height()/ink.width()<1.12f,"broad golden piece proportions match reference");
   }
   v.restart();v.started=true;int[] types={1,2,3,4,5,6,7,8,9,10,11,12,14,15};for(int r=0;r<9;r++)for(int c=0;c<9;c++)v.game.board[r][c]=types[(r*9+c)%types.length]*(r<4?-1:1);v.invalidate();
  });screenshot("05_uniform_all_types.png");main(()->{v.restart();v.started=true;v.clock.resume(SystemClock.elapsedRealtime(),true);v.invalidate();});
 }
 void clipboardSource(String text,boolean sensitive,boolean waitForReturn){
  Intent source=new Intent().setClassName(getContext().getPackageName(),ClipboardSourceActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("text",text).putExtra("sensitive",sensitive);
  getTargetContext().startActivity(source);boolean paused=false;long deadline=SystemClock.elapsedRealtime()+10000;
  while(waitForReturn&&SystemClock.elapsedRealtime()<deadline){if(!activity.resumed)paused=true;if(paused&&activity.resumed&&activity.hasWindowFocus())break;SystemClock.sleep(40);}
  if(waitForReturn)check(paused&&activity.resumed&&activity.hasWindowFocus(),"external clipboard app backgrounds and resumes target");else SystemClock.sleep(1100);
 }
 void awaitChoice(String positive){long end=SystemClock.elapsedRealtime()+10000;java.util.concurrent.atomic.AtomicBoolean shown=new java.util.concurrent.atomic.AtomicBoolean();while(SystemClock.elapsedRealtime()<end){main(()->{AlertDialog d=activity.choiceDialog;shown.set(d!=null&&d.isShowing()&&d.getButton(AlertDialog.BUTTON_POSITIVE)!=null&&positive.contentEquals(d.getButton(AlertDialog.BUTTON_POSITIVE).getText()));});if(shown.get())return;SystemClock.sleep(40);}check(false,"expected clipboard dialog: "+positive);}
 void noClipboardDialog(){SystemClock.sleep(750);main(()->check(activity.choiceDialog==null,"ordinary, sensitive or already-handled clipboard remains silent"));}
 void awaitImported(int ply){long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end&&(!v.evalHistory.imported||v.evalHistory.cursor!=ply||v.analyzing))SystemClock.sleep(40);main(()->check(v.mode==MainActivity.STUDY&&v.evalHistory.imported&&v.evalHistory.cursor==ply,"clipboard import atomically opens final study position"));}
 void realNavigation(int action){
  main(()->v.scrollStudy(v.maxStudyScroll()));float[] xy=new float[2];main(()->{int[] where=new int[2];v.getLocationOnScreen(where);xy[0]=where[0]+(42+action*79)*v.scale;xy[1]=where[1]+(v.analysisY()+262-v.studyScroll)*v.scale;});
  long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy[0],xy[1],0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(down,true),"OS navigation down");down.recycle();MotionEvent up=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,xy[0],xy[1],0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(up,true),"OS navigation up");up.recycle();waitForIdleSync();
 }
 void clipboardTests(Intent intent)throws Exception{
  String text;try(InputStream in=getContext().getAssets().open("shogi_quest_79.kif")){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}KifRecord record=KifRecord.parse(text);
  main(()->{v.enterMode(MainActivity.STUDY);v.restart();v.enterMode(MainActivity.HOME);});
  clipboardSource("ordinary text: meeting at 10:00",false,true);noClipboardDialog();
  clipboardSource(text,true,true);noClipboardDialog();
  clipboardSource(text,false,true);awaitChoice("検討する");screenshot("24_clipboard_confirmation.png");clickDialog("キャンセル");main(()->check(v.mode==MainActivity.HOME,"cancel keeps current screen"));clipboardSource(text,false,true);noClipboardDialog();
  main(()->activity.finish());waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();noClipboardDialog();main(()->check(v.mode==MainActivity.HOME,"cancel suppression survives recreation"));
  main(()->activity.finish());waitForIdleSync();clipboardSource(text+"\n* cold-launch fixture",false,false);activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();awaitChoice("検討する");clickDialog("検討する");awaitImported(79);SystemClock.sleep(300);
  main(()->{check(EvaluationHistory.key(v.game).equals(record.positions.get(79))&&v.game.board[2][1]==5,"all final pieces and captured hands match Quest");check(v.analysisSide==-1&&!v.analyzing&&v.analysisStatus.contains("詰み")&&v.displayScore!=null&&v.displayScore.mate,"final checkmate uses terminal evaluation without fake AI search");check(v.evalHistory.points.size()==80,"all 79 moves plus initial position retained");});SystemClock.sleep(3500);screenshot("25_clipboard_final_checkmate.png");
  for(int i=0;i<80;i++){final int ply=i;main(()->{v.jumpStudy(ply);check(v.evalHistory.cursor==ply&&EvaluationHistory.key(v.game).equals(record.positions.get(ply)),"every imported ply reachable with correct board/hands: "+ply);check(v.analysisSide==(ply%2==0?1:-1),"correct analysis side for imported ply");});}
  realNavigation(0);main(()->check(v.evalHistory.cursor==0,"OS first-position button"));realNavigation(2);main(()->check(v.evalHistory.cursor==1,"OS next-position button"));realNavigation(3);main(()->check(v.evalHistory.cursor==79,"OS final-position button"));realNavigation(1);main(()->check(v.evalHistory.cursor==78,"OS previous-position button"));analysisReady();
  java.util.concurrent.atomic.AtomicInteger updates=new java.util.concurrent.atomic.AtomicInteger();main(()->updates.set(v.analysisUpdates));long updateDeadline=SystemClock.elapsedRealtime()+10000;java.util.concurrent.atomic.AtomicBoolean advanced=new java.util.concurrent.atomic.AtomicBoolean();while(SystemClock.elapsedRealtime()<updateDeadline){main(()->advanced.set(v.analyzing&&v.analysisUpdates>updates.get()));if(advanced.get())break;SystemClock.sleep(40);}main(()->check(advanced.get()&&v.ai.engine.alive(),"78th position continues native AI analysis: updates="+updates.get()+" -> "+v.analysisUpdates+" status="+v.analysisStatus+" error="+v.aiError));screenshot("26_clipboard_previous_analysis.png");
  realGraphTap(40);analysisReady();main(()->check(v.evalHistory.cursor==40&&EvaluationHistory.key(v.game).equals(record.positions.get(40)),"OS graph jump in imported record"));
  realNavigation(4);awaitChoice("閉じる");main(()->check(activity.choiceDialog.getListView().getCount()==80,"native move list includes entire imported game"));main(()->activity.choiceDialog.getListView().performItemClick(null,0,0));main(()->check(v.evalHistory.cursor==0,"native move list seeks initial position"));
  main(()->{v.jumpStudy(78);v.save();});main(()->activity.finish());waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();noClipboardDialog();chooseMode(MainActivity.STUDY);analysisReady();main(()->check(v.evalHistory.cursor==78&&v.evalHistory.points.size()==80&&EvaluationHistory.key(v.game).equals(record.positions.get(78)),"record and selected ply survive activity recreation"));
  String before=EvaluationHistory.key(v.game);String replacement=text+"\n* replacement fixture";clipboardSource(replacement,false,true);awaitChoice("検討する");clickDialog("検討する");awaitChoice("置き換える");screenshot("27_clipboard_replace_confirmation.png");clickDialog("キャンセル");main(()->check(EvaluationHistory.key(v.game).equals(before)&&v.evalHistory.cursor==78,"replacement cancel preserves existing study"));clipboardSource(replacement,false,true);noClipboardDialog();
  clipboardSource(text.replace("79 ８三金打","79 ８三歩打"),false,true);awaitChoice("検討する");clickDialog("検討する");awaitChoice("閉じる");main(()->{android.widget.TextView message=activity.choiceDialog.findViewById(android.R.id.message);check(message!=null&&message.getText().toString().contains("79手目"),"parse error identifies failing ply");check(EvaluationHistory.key(v.game).equals(before)&&v.evalHistory.points.size()==80,"failed import leaves current study intact");});screenshot("28_clipboard_import_error.png");clickDialog("閉じる");
  clipboardSource(text+"\n* accepted replacement",false,true);awaitChoice("検討する");clickDialog("検討する");awaitChoice("置き換える");clickDialog("置き換える");awaitImported(79);main(()->check(EvaluationHistory.key(v.game).equals(record.positions.get(79)),"accepted replacement imports exact new final position"));
  main(()->activity.finish());waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();noClipboardDialog();chooseMode(MainActivity.STUDY);awaitImported(79);SystemClock.sleep(300);main(()->check(v.analysisSide==-1&&v.analysisStatus.contains("詰み"),"final imported side and mate survive recreation"));SystemClock.sleep(3500);screenshot("29_clipboard_restored_final.png");
  try(PrintWriter evidence=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"clipboard_import.txt"))){evidence.println("PASS user-supplied Shogi Quest KIF through actual OS clipboard written by separate test APK: launch/resume confirmation, ordinary/sensitive text ignored, cancellation persisted, final mate at 79, all 80 positions with board/hands and side, OS-routed first/next/last/previous and graph taps, native move list, continuous real AI at 78, recreation persistence, replacement cancel/accept, invalid 79th move atomic error. Shogi Quest app itself and a physical phone were not exercised.");}
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  File aiRoot=new File(getTargetContext().getFilesDir(),"ai");for(String old:new String[]{"suisho5","full","light"}){File dir=new File(aiRoot,old);dir.mkdirs();try(RandomAccessFile f=new RandomAccessFile(new File(dir,"nn.bin"),"rw")){f.setLength(64217066L);}}File micro=new File(aiRoot,"tsuyou11micro");micro.mkdirs();try(RandomAccessFile f=new RandomAccessFile(new File(micro,"nn.bin"),"rw")){f.setLength(0);f.setLength(3864610L);}try(FileOutputStream f=new FileOutputStream(new File(micro,"nn.bin.tmp"))){f.write(1);}
  Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();studyTests(intent);studyAnalysisTests();studyRecoveryTests();evaluationGraphTests(intent);continuousStudyTests();modelMigrationTest();layoutTests();ready();compatibilityTests();promotionPopupTests();benchmark();cancellation();
  main(()->{v.restart();check(v.getWidth()>0&&v.getHeight()>0,"native board laid out");check(v.game.board[6][0]==1,"initial setup");check(v.game.remaining[0]==180000,"03:00 at start");});
  soundReady();int matchSounds=v.sounds.playCount;tap(200,v.H/2);square(6,0);main(()->check(v.selectedR==6&&v.selectedC==0,"touch selects pawn"));main(()->check(v.sounds.playCount==matchSounds,"match selection is silent"));square(4,0);main(()->check(v.sounds.playCount==matchSounds,"illegal move is silent"));square(6,0);square(5,0);main(()->check(v.game.board[5][0]==1&&v.game.turn==-1,"touch moves and switches turn"));
  long cpuDeadline=SystemClock.elapsedRealtime()+5000;while(v.game.turn==-1&&SystemClock.elapsedRealtime()<cpuDeadline)SystemClock.sleep(50);waitForIdleSync();main(()->check(v.game.movesPlayed==2&&v.game.turn==1,"CPU legal response"));main(()->check(v.sounds.playCount==matchSounds+2,"human and CPU committed moves play once each"));main(()->{check(v.nativeReplies>0,"actual YaneuraOu response applied");check(v.boardSize>=399.99f,"board uses full available width");check(v.boardX*v.scale+v.offsetX>=0,"left board fits");check((v.boardX+v.boardSize)*v.scale+v.offsetX<=v.getWidth()+.1f,"right board fits");});screenshot("01_initial_and_cpu.png");
  fixture();main(()->{v.game.board[1][1]=1;v.invalidate();});square(1,1);square(0,1);main(()->{check(v.game.board[0][1]==9,"touch forced promotion");v.cancelCpu();v.game.turn=1;v.game.board[4][3]=7;v.game.board[4][6]=-4;v.invalidate();});
  square(4,3);square(4,6);main(()->{check(v.game.hands[0][4]==1,"capture to hand");v.cancelCpu();v.game.turn=1;});tap(v.handX(0,v.handCount(1)),v.bottomHandY()+22);square(5,5);main(()->{check(v.game.board[5][5]==4&&v.game.hands[0][4]==0,"hand touch and drop");v.cancelCpu();});screenshot("02_promotion_capture_drop.png");
  main(()->{v.game.remaining[1]=100;v.clock.resume(SystemClock.elapsedRealtime(),true);});SystemClock.sleep(350);main(()->check(v.game.winner==1&&v.game.remaining[1]==0,"actual clock timeout"));screenshot("03_timeout.png");
  main(()->{v.restart();v.started=true;v.clock.resume(SystemClock.elapsedRealtime(),true);});long[] left=new long[1];main(()->{callActivityOnPause(activity);int n=v.sounds.playCount;v.placementSound();check(v.sounds.playCount==n,"background suppresses sound");left[0]=v.game.remaining[0];});SystemClock.sleep(350);main(()->{check(v.game.remaining[0]==left[0],"background pauses clock");callActivityOnResume(activity);});SystemClock.sleep(160);main(()->check(v.game.remaining[0]<left[0]&&v.game.remaining[0]>left[0]-300,"resume excludes background elapsed"));
  main(()->{v.game.resign(1);v.save();});activity.finish();waitForIdleSync();activity=(MainActivity)startActivitySync(intent);v=activity.view;waitForIdleSync();chooseMode(MainActivity.MATCH);ready();main(()->{check(v.game.winner==-1&&v.game.result.equals("投了"),"state restored on recreation");v.restart();});
  main(()->{v.restart();v.started=true;v.clock.resume(SystemClock.elapsedRealtime(),true);v.invalidate();});ready();uniformPieces();screenshot("04_enlarged_native_ai.png");screenshot("20_reference_match_layout.png");
  try(PrintWriter audio=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"placement_audio.txt"))){audio.println("PASS native SoundPool decoded placement WAV; study/match/human/CPU/capture/drop accepted playback once each; selection/illegal/background silent. Last active player streams accepted: "+v.sounds.playCount);}
  clipboardTests(intent);out.putString("stream","PASS native instrumentation: "+assertions+" assertions; UI screenshots saved");finish(Activity.RESULT_OK,out);
 }catch(Throwable t){try{screenshot("99_failure.png");}catch(Throwable ignored){}android.util.Log.e("NativeSmokeTest","FAIL",t);out.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,out);}}
}




