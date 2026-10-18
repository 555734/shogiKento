package com.presentsoft.shogi3minutes;
import android.content.Context;import android.os.*;import java.io.*;import java.util.concurrent.*;
final class AiEngine {
 interface Ready {void done(String error);}interface Reply {void done(String result,String error);}
 interface AnalysisReply {void done(StudyAnalysis.Result result,String error);}
 private static AiEngine instance;static synchronized AiEngine get(Context c){if(instance==null)instance=new AiEngine(c);return instance;}
 final Context context;final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
 volatile UsiProcess engine;volatile boolean ready;volatile int generation;int restarts;volatile boolean compatibility;
 AiEngine(Context c){context=c.getApplicationContext();compatibility=context.getSharedPreferences("native_engine",0).getBoolean("compatibility_v182",false);}
 String engineFile(){return compatibility?"libyaneuraou_compat.so":"libyaneuraou.so";}
 void recordFailure(Throwable error){
  if(!compatibility&&UsiProcess.isNativeCrash(error)&&new File(context.getApplicationInfo().nativeLibraryDir,"libyaneuraou_compat.so").isFile()){
   compatibility=true;context.getSharedPreferences("native_engine",0).edit().putBoolean("compatibility_v182",true).apply();
   android.util.Log.w("ShogiAI","Native signal: switching to scalar engine with the same TsuyouraOu11micro model",error);
  }
 }

 void cancel(){generation++;UsiProcess e=engine;if(e!=null)e.stop();}
 // Called only on the serial worker. Never reuse a process after any protocol failure.
 UsiProcess ensureEngine()throws Exception{
  if(engine==null||!engine.alive()){if(engine!=null&&!engine.closed)recordFailure(engine.failure("AIプロセスが終了しました"));discard();File dir=ModelAssets.prepare(context);
   engine=new UsiProcess(new File(context.getApplicationInfo().nativeLibraryDir,engineFile()).getPath(),dir.getPath());
  }return engine;
 }
 void discard(){UsiProcess e=engine;engine=null;if(e!=null)e.close();ready=false;}
 String failure(Throwable e){recordFailure(e);android.util.Log.e("ShogiAI","Native AI failure",e);return (compatibility?"互換AI / ":"通常AI / ")+e.toString();}
 void configure(int ignored,Ready callback){cancel();ready=false;final int token=generation;
  worker.execute(()->{if(token!=generation)return;String error=null;for(int attempt=0;attempt<2&&token==generation;attempt++)try{ensureEngine();error=null;break;}catch(Exception e){error=failure(e);discard();if(attempt==0)restarts++;}final String result=error;main.post(()->{if(token==generation){ready=result==null;callback.done(result);}});});
 }
 void search(String sfen,int nodes,int millis,Reply callback){cancel();final int token=generation;
  worker.execute(()->{if(token!=generation)return;String result=null,error=null;
   for(int attempt=0;attempt<2&&token==generation;attempt++)try{result=ensureEngine().search(sfen,nodes,millis,()->token!=generation);error=null;break;}catch(Exception e){error=failure(e);discard();if(attempt==0)restarts++;}
   final String answer=result,problem=error;main.post(()->{if(token==generation){ready=problem==null;callback.done(answer,problem);}});});
 }
 void destroy(){cancel();}
 void analyze(String sfen,int nodes,int millis,int count,AnalysisReply callback){cancel();final int token=generation;
  worker.execute(()->{if(token!=generation)return;StudyAnalysis.Result result=null;String error=null;
   for(int attempt=0;attempt<2&&token==generation;attempt++)try{result=ensureEngine().analyze(sfen,nodes,millis,()->token!=generation,count);error=null;break;}catch(Exception e){error=failure(e);discard();if(attempt==0)restarts++;}
   final StudyAnalysis.Result answer=result;final String problem=error;main.post(()->{if(token==generation){ready=problem==null;callback.done(answer,problem);}});});
 }
}

