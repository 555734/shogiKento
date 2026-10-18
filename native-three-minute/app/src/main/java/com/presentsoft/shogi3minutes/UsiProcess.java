package com.presentsoft.shogi3minutes;
import java.io.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
/** Separate GPL native executable; communicates only through standard USI pipes. */
final class UsiProcess implements Closeable {
 static final class NativeFailure extends IOException {
  final Integer exitCode;
  NativeFailure(String message,Integer code){super(message);exitCode=code;}
 }
 static boolean isNativeCrash(Throwable error){
  for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof NativeFailure){Integer code=((NativeFailure)e).exitCode;if(code!=null&&(code==139||code==132||code==134||code==135))return true;}
  return false;
 }
 Integer exitCode(){try{return process.exitValue();}catch(IllegalThreadStateException ignored){return null;}}

 final Process process;final BufferedWriter input;final BlockingQueue<String> lines=new LinkedBlockingQueue<>();final StringBuilder initialization=new StringBuilder();volatile boolean closed;final java.util.ArrayDeque<String> recent=new java.util.ArrayDeque<>();
 UsiProcess(String executable,String evalDir)throws Exception{
  process=new ProcessBuilder(executable).redirectErrorStream(true).start();input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),"UTF-8"));
  Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){String s;while((s=in.readLine())!=null)lines.offer(s);}catch(IOException ignored){}finally{try{process.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();}lines.offer("__EOF__");}},"shogi-usi-output");reader.setDaemon(true);reader.start();
  try{send("usi");until("usiok",15000);
   send("setoption name Threads value 1");send("setoption name USI_Hash value 16");send("setoption name USI_Ponder value false");
   send("setoption name EvalDir value "+evalDir);send("setoption name FV_SCALE value 24");send("setoption name BookFile value no_book");send("setoption name USI_OwnBook value false");send("setoption name EnteringKingRule value NoEnteringKing");send("setoption name PvInterval value 0");send("isready");until("readyok",20000);
  }catch(Exception e){close();throw e;}
 }
 synchronized void send(String s)throws IOException{try{input.write(s);input.newLine();input.flush();}catch(IOException e){IOException error=failure("AIへの送信に失敗: "+e.getMessage());error.initCause(e);throw error;}}
 synchronized IOException failure(String reason){Integer code=exitCode();String exit=code==null?"":" / exit="+code;StringBuilder log=new StringBuilder();for(String line:recent){if(log.length()>0)log.append(" | ");log.append(line);}return new NativeFailure(reason+exit+" / "+log,code);}
 synchronized void remember(String line){if(recent.size()==8)recent.removeFirst();recent.addLast(line.length()>240?line.substring(0,240):line);}
 String next(long deadline)throws Exception{long left=deadline-System.nanoTime();if(left<=0)throw failure("AI応答がタイムアウトしました");String s=lines.poll(left,TimeUnit.NANOSECONDS);if(s==null)throw failure("AI応答がタイムアウトしました");if(s.equals("__EOF__"))throw failure("AIプロセスが終了しました");remember(s);return s;}
 void until(String marker,int millis)throws Exception{long end=System.nanoTime()+millis*1000000L;while(true){String line=next(end);initialization.append(line).append('\n');if(line.equals(marker))return;if(line.contains("Error!"))throw new IOException(line);} }
 void stop(){try{send("stop");}catch(IOException ignored){} }
 String search(String sfen,int nodes,int millis,BooleanSupplier cancelled)throws Exception{
  StudyAnalysis.Result r=run(sfen,nodes,millis,cancelled,1);
  return r==null?null:r.bestMove+"|"+r.nodes+"|"+r.millis+"|"+r.depth;
 }
 StudyAnalysis.Result analyze(String sfen,int nodes,int millis,BooleanSupplier cancelled,int count)throws Exception{return run(sfen,nodes,millis,cancelled,Math.max(1,Math.min(3,count)));}
 private StudyAnalysis.Result run(String sfen,int nodes,int millis,BooleanSupplier cancelled,int count)throws Exception{
  StudyAnalysis.Collector scores=new StudyAnalysis.Collector();
  // Drain all output from the previous request before accepting a new bestmove.
  send("isready");until("readyok",10000);
  synchronized(this){if(cancelled.getAsBoolean())return null;send("setoption name MultiPV value "+count);send("position sfen "+sfen);send("go nodes "+nodes+" movetime "+millis);}
  long start=System.nanoTime(),end=start+(millis+5000L)*1000000L,visited=0;int depth=0;boolean stopped=false;
  while(true){if(cancelled.getAsBoolean()&&!stopped){stop();stopped=true;}if(!stopped&&System.nanoTime()-start>=(millis+100L)*1000000L){stop();stopped=true;}
   String line=lines.poll(25,TimeUnit.MILLISECONDS);if(line==null){if(System.nanoTime()>end){IOException error=failure("AI停止がタイムアウトしました");close();throw error;}continue;}
   if(line.equals("__EOF__"))throw failure("AIプロセスが終了しました");remember(line);
   if(line.startsWith("info ")&&!line.startsWith("info string ")){scores.accept(line);String[] a=line.split(" ");for(int i=1;i+1<a.length;i++){if(a[i].equals("pv"))break;if(a[i].equals("nodes"))visited=Long.parseLong(a[i+1]);if(a[i].equals("depth"))depth=Integer.parseInt(a[i+1]);}}
   if(line.startsWith("bestmove "))return cancelled.getAsBoolean()?null:new StudyAnalysis.Result(line.split(" ")[1],scores.finish(count),visited,(System.nanoTime()-start)/1000000.0,depth);
  }
 }
 boolean alive(){if(closed)return false;try{process.exitValue();return false;}catch(IllegalThreadStateException e){return true;}}
 @Override public void close(){if(closed)return;closed=true;try{send("quit");}catch(IOException ignored){}process.destroy();}
}

