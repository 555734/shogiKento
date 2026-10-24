package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Exercises real process pipes, cancellation and restart with a deterministic USI peer. */
public final class ProtocolTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static final String INITIAL="lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1";
 public static void main(String[] args)throws Exception{
  for(int code:new int[]{139,132,134,135})ok(UsiProcess.isNativeCrash(new UsiProcess.NativeFailure("signal",code)),"native fatal signal selects compatibility "+code);
  for(Integer code:new Integer[]{null,0,9,137})ok(!UsiProcess.isNativeCrash(new UsiProcess.NativeFailure("other",code)),"normal exit, OOM/kill, and unknown EOF do not change backend "+code);
  ok(!UsiProcess.isNativeCrash(new IOException("exit=139")),"untyped error text does not select backend");
  String peer=Path.of(args[0]).toAbsolutePath().toString();
  try(UsiProcess e=new UsiProcess(peer,"unused")){
   // Old bestmove output must be consumed before the next search starts.
   e.lines.offer("bestmove 1a1b");StudyAnalysis.Result r=e.analyze(INITIAL,3000,750,()->false,3);
   ok(r.bestMove.equals("7g7f")&&r.candidates.size()==3,"barrier excludes stale bestmove");
   AtomicBoolean cancelled=new AtomicBoolean();ExecutorService thread=Executors.newSingleThreadExecutor();
   Future<StudyAnalysis.Result> old=thread.submit(()->e.analyze(INITIAL,999999,5000,cancelled::get,3));
   Thread.sleep(80);cancelled.set(true);e.stop();ok(old.get(2,TimeUnit.SECONDS)==null,"cancel drains old bestmove without publishing");thread.shutdownNow();
   r=e.analyze(INITIAL,3000,750,()->false,3);ok(r.candidates.size()==3&&r.bestMove.equals("7g7f"),"new search after cancellation has fresh scores");
   AtomicBoolean stop=new AtomicBoolean();java.util.List<StudyAnalysis.Result> stream=new CopyOnWriteArrayList<>();CountDownLatch samples=new CountDownLatch(3);ExecutorService ongoing=Executors.newSingleThreadExecutor();
   Future<?> live=ongoing.submit(()->{try{e.analyzeContinuous(INITIAL,stop::get,3,x->{stream.add(x);samples.countDown();});}catch(Exception ex){throw new RuntimeException(ex);}});
   ok(samples.await(3,TimeUnit.SECONDS)&&!live.isDone(),"infinite search streams repeated results before stop");
   ok(stream.size()>=3&&stream.get(2).depth>stream.get(0).depth&&stream.get(2).nodes>stream.get(0).nodes,"same search deepens and visits more nodes");
   for(StudyAnalysis.Result x:stream)ok(x.candidates.size()==3&&x.candidates.stream().allMatch(c->c.depth==x.depth),"stream publishes complete same-depth MultiPV sets");
   stop.set(true);e.stop();live.get(2,TimeUnit.SECONDS);int reports=stream.size();Thread.sleep(100);ok(stream.size()==reports,"no callbacks after cancelled continuous search drained");ongoing.shutdownNow();
   r=e.analyze(INITIAL,3000,750,()->false,3);ok(r.depth==1&&r.candidates.size()==3,"bounded match search remains fresh after continuous analysis");
   AtomicInteger none=new AtomicInteger();e.analyzeContinuous(INITIAL,()->true,3,x->none.incrementAndGet());ok(none.get()==0,"pre-cancelled continuous search never publishes");
   e.close();ok(!e.alive(),"closed process immediately unavailable before OS reap");
  }
  try(UsiProcess e=new UsiProcess(peer,"unused")){
   e.send("crash");try{e.analyze(INITIAL,3000,750,()->false,3);throw new AssertionError("expected EOF");}catch(IOException ex){ok(ex.getMessage().contains("終了")||ex.getMessage().contains("送信"),"failure reports native process termination: "+ex);}
  }
  if(args.length>1)try(UsiProcess e=new UsiProcess(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString())){
   for(int side:new int[]{1,-1}){ShogiGame g=new ShogiGame();g.turn=side;StudyAnalysis.Result r=e.analyze(UsiPosition.sfen(g),3000,750,()->false,3);ok(r.candidates.size()==3,"native three candidates for both sides");for(StudyAnalysis.Candidate c:r.candidates)ok(UsiPosition.legalMove(g,c.move)!=null,"native candidate legal");}
   // The user's screenshot: free edits 2g2f, 3c3d, 2f2e; gote analysis.
   ShogiGame g=new ShogiGame();g.board[6][7]=0;g.board[4][7]=1;g.board[2][6]=0;g.board[3][6]=-1;g.turn=-1;
   StudyAnalysis.Result r=e.analyze(UsiPosition.sfen(g),50000,750,()->false,3);ok(r.candidates.size()==3,"screenshot position analysed");for(StudyAnalysis.Candidate c:r.candidates)ok(UsiPosition.legalMove(g,c.move)!=null,"screenshot candidate legal");
  }
  System.out.println("PASS process protocol: "+checks+" assertions");
 }
}

