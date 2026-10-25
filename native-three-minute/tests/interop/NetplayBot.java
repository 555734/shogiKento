package com.presentsoft.shogi3minutes;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/**
 * An Android online client driven over stdin, for cross-platform tests against the iOS implementation.
 * Usage: NetplayBot RELAY_URL LOBBY_TAG. Commands: seek quick|host CODE|guest CODE, stop, move, play USI, resign,
 * rematch, drop PERCENT, quit. Every change of the client's state is printed as one "STATE key=value ... sfen=SFEN" line.
 */
public final class NetplayBot implements Netplay.Listener {
 final Netplay net;OnlineGame game;int rtt=-1,relaysOpen,matches;boolean direct,left;String failure="",last="";final PrintStream out;
 NetplayBot(String relay,PrintStream out)throws IOException{this.out=out;net=new Netplay(Collections.singletonList(relay),Collections.emptyList(),null,this);net.start();}
 static long now(){return System.nanoTime()/1000000;}
 synchronized void report(){
  String s=game==null?"STATE matched=0 relays="+relaysOpen+" failure="+(failure.isEmpty()?"-":failure.replace(' ','_')):
   "STATE matched="+matches+" relays="+relaysOpen+" round="+game.round+" side="+game.mySide+" started="+(game.started?1:0)+" plies="+game.game.movesPlayed+" winner="+game.game.winner+" result="+(game.game.result.isEmpty()?"-":game.game.result)
   +" direct="+(direct?1:0)+" left="+(left?1:0)+" rematch="+(game.rematchPeer?1:0)+" error="+(game.error==null?"-":game.error.replace(' ','_'))+" sfen="+UsiPosition.sfen(game.game);
  if(!s.equals(last)){last=s;out.println(s);out.flush();}
 }
 public synchronized void matched(int side){matches++;game=new OnlineGame(side,net::send);game.hello(now());report();}
 public synchronized void message(String t){if(game!=null){game.rttMillis=rtt;game.receive(t,now());}report();}
 public synchronized void path(boolean d,int r,String route){direct=d;rtt=r;report();}
 public synchronized void relays(int open,int total){relaysOpen=open;report();}
 public synchronized void peerLeft(){left=true;if(game!=null)game.peerLeft();report();}
 public synchronized void failed(String r){failure=r;report();}
 synchronized void command(String line){
  String[] f=line.trim().split(" ");
  switch(f[0]){
   case "seek":game=null;matches=0;direct=false;left=false;net.seek(f[1].equals("quick")?Netplay.QUICK:f[1].equals("host")?Netplay.HOST:Netplay.GUEST,f.length>2?f[2]:null);break;
   case "stop":game=null;net.stopSeeking();break;
   case "move":if(game!=null&&game.myTurn()){ShogiGame.Move m=game.game.cpuChoice();if(m!=null)game.localMove(m,now());}break;
   case "play":if(game!=null){ShogiGame.Move m=UsiPosition.legalMove(game.game,f[1]);if(m!=null)game.localMove(m,now());}break;
   case "resign":if(game!=null)game.resign();break;
   case "rematch":if(game!=null)game.requestRematch(now());break;
   case "drop":net.endpoint.dropPercent=Integer.parseInt(f[1]);break;
   default:break;
  }
  report();
 }
 public static void main(String[] args)throws Exception{
  UdpEndpoint.allowLoopback=true;Netplay.lobbyTag=args[1];PrintStream out=new PrintStream(new FileOutputStream(FileDescriptor.out),true,"UTF-8");
  NetplayBot bot=new NetplayBot(args[0],out);
  Thread ticker=new Thread(()->{while(true){synchronized(bot){if(bot.game!=null){bot.game.rttMillis=bot.rtt;bot.game.tick(now());}bot.report();}try{Thread.sleep(50);}catch(InterruptedException e){return;}}},"bot-tick");ticker.setDaemon(true);ticker.start();
  BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
  for(String line;(line=in.readLine())!=null;){if(line.trim().equals("quit"))break;if(!line.trim().isEmpty())bot.command(line);}
  bot.net.close();out.println("BYE");out.flush();System.exit(0);
 }
}
