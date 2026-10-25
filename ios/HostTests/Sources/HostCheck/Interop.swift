import Foundation
@testable import OnlineCore

/// Apple platforms use the app's URLSession transport; Linux Foundation has no WebSocket client.
#if canImport(Darwin)
let transport=URLSessionWebSocket.factory
#else
let transport=PlainWebSocket.factory
#endif
func monotonic()->Int64 {Int64(DispatchTime.now().uptimeNanoseconds/1_000_000)}
func waitFor(_ ms:Int64,_ what:String,_ c:()->Bool){let end=monotonic()+ms;while !c(){if monotonic()>end{print("FAIL timeout:",what);exit(1)};usleep(2000)};checks+=1}

/// A Java helper process with line-based stdin/stdout.
final class JavaProcess {
    let process=Process();private let input=Pipe(),output=Pipe();private var pending=Data();var onLine:((String)->Void)?
    init(classpath:String,main:String,args:[String]){
        process.executableURL=URL(fileURLWithPath:"/usr/bin/env");process.arguments=["java","-cp",classpath,"com.presentsoft.shogi3minutes."+main]+args
        process.standardInput=input;process.standardOutput=output;process.standardError=FileHandle(forWritingAtPath:"/dev/null")
        output.fileHandleForReading.readabilityHandler={[weak self] h in let d=h.availableData;guard let self,!d.isEmpty else{return};self.pending.append(d)
            while let i=self.pending.firstIndex(of:10){let line=String(decoding:self.pending[self.pending.startIndex..<i],as:UTF8.self);self.pending=Data(self.pending[(i+1)...]);self.onLine?(line)}}
        try! process.run()
    }
    func send(_ line:String){input.fileHandleForWriting.write(Data((line+"\n").utf8))}
    func stop(){if process.isRunning{process.terminate()}}
}

/// Android's rules as the OnlineRules of the Swift game, so both peers apply identical rules in these tests.
final class JavaRules:OnlineRules {
    private let p:JavaProcess;private let cond=NSCondition();private var replies=[String]()
    init(_ cp:String){p=JavaProcess(classpath:cp,main:"RulesOracle",args:[]);p.onLine={[weak self] l in guard let self else{return};self.cond.lock();self.replies.append(l);self.cond.signal();self.cond.unlock()}}
    private let serial=NSLock()
    private func ask(_ q:String)->String? {serial.lock();defer{serial.unlock()};p.send(q);cond.lock();while replies.isEmpty{cond.wait()};let r=replies.removeFirst();cond.unlock();return r.hasPrefix("ok") ? String(r.dropFirst(r.count>2 ? 3:2)):nil}
    func legal(_ sfen:String)->[String]? {ask("legal "+sfen).map{$0.split(separator:" ").map(String.init)}}
    func apply(_ sfen:String,_ usi:String)->String? {ask("apply \(usi) \(sfen)")}
    func inCheck(_ sfen:String)->Bool {ask("check "+sfen)=="1"}
}

/// The Android client, driven over stdin; its STATE lines are parsed into a dictionary.
final class Bot {
    let p:JavaProcess;private let lock=NSLock();private var state=[String:String]()
    init(_ cp:String,relay:String,lobby:String){p=JavaProcess(classpath:cp,main:"NetplayBot",args:[relay,lobby])
        p.onLine={[weak self] l in guard let self,l.hasPrefix("STATE ") else{return};var d=[String:String]();let body=l.dropFirst(6)
            if let r=body.range(of:" sfen="){d["sfen"]=String(body[r.upperBound...])};for kv in body.split(separator:" ") where !kv.hasPrefix("sfen=") && kv.contains("="){let parts=kv.split(separator:"=",maxSplits:1);if parts.count==2{d[String(parts[0])]=String(parts[1])}}
            self.lock.lock();self.state=d;self.lock.unlock()}}
    subscript(_ k:String)->String {lock.lock();defer{lock.unlock()};return state[k] ?? ""}
    func int(_ k:String)->Int {Int(self[k]) ?? -99}
    func send(_ c:String){p.send(c)}
}

final class SwiftClient:NetplayListener {
    let name:String;let net:Netplay;let rules:OnlineRules;private let lock=NSRecursiveLock()
    private var gameStore:OnlineGame?;private(set) var side=0,matches=0,relaysOpen=0,rtt = -1;private(set) var direct=false,left=false;var failure=""
    private var ticker:Thread?
    init(_ name:String,relays:[String],rules:OnlineRules){self.name=name;self.rules=rules;net=try! Netplay(relays:relays,stun:[],factory:transport);net.listener=self;net.start()
        let t=Thread{[weak self] in while let self{self.with{$0?.rttMillis=self.rtt;$0?.tick(monotonic())};usleep(50000)}};ticker=t;t.start()}
    /// Runs a mutation on the game and sends whatever it queued, in order.
    @discardableResult func with<T>(_ f:(inout OnlineGame?)->T)->T {lock.lock();defer{lock.unlock()};let r=f(&gameStore);if var g=gameStore,!g.outbox.isEmpty{let out=g.outbox;g.outbox=[];gameStore=g;for m in out{net.send(m)}};return r}
    var game:OnlineGame? {lock.lock();defer{lock.unlock()};return gameStore}
    func matched(localSide:Int){with{g in side=localSide;matches+=1;g=OnlineGame(mySide:localSide,rules:rules);g!.hello(monotonic())}}
    func message(_ text:String){with{$0?.rttMillis=rtt;$0?.receive(text,monotonic())}}
    func path(direct:Bool,rtt:Int,route:String){self.direct=direct;self.rtt=rtt}
    func relays(open:Int,total:Int){relaysOpen=open}
    func peerLeft(){left=true;with{$0?.markPeerLeft()}}
    func failed(_ reason:String){failure=reason}
    var started:Bool {game?.started ?? false}
    func close(){net.close()}
}

enum Interop {
    static func clocks(_ rules:OnlineRules){
        final class Wire {var t:Int64=0,cut=false;let latency:Int64;var due=[(Int64,Int,String)]();var a:OnlineGame,b:OnlineGame
            init(_ latency:Int64,_ rules:OnlineRules){self.latency=latency;a=OnlineGame(mySide:1,rules:rules);b=OnlineGame(mySide:-1,rules:rules);a.hello(0);b.hello(0);flush();run(latency)}
            func flush(){for m in a.outbox where !cut{due.append((t+latency,1,m))};for m in b.outbox where !cut{due.append((t+latency,0,m))};a.outbox=[];b.outbox=[]}
            func run(_ until:Int64){while let i=due.indices.min(by:{due[$0].0<due[$1].0}),due[i].0<=until{let (at,target,m)=due.remove(at:i);t=at;if target==0{a.receive(m,t)}else{b.receive(m,t)};flush()};t=until;a.tick(t);b.tick(t);flush()}}
        let w=Wire(100,rules);ok(w.a.started && w.b.started,"both start after HELLO");ok(w.a.myTurn && !w.b.myTurn,"sente moves first")
        w.t=5100;ok(w.a.localMove("7g7f",w.t),"sente move");ok(w.a.remainingTime[0]==175000,"own clock measured locally");w.flush()
        w.run(5200);ok(w.b.movesPlayed==1 && w.b.remainingTime[0]==175000,"honest mover is not charged network latency")
        ok(w.b.remaining(-1,5200)==180000 && w.a.remaining(-1,5200)==180000-100,"each side counts the opponent from its own turn start")
        w.run(8200);ok(w.b.localMove("3c3d",8200),"gote reply");w.flush();w.run(8300);ok(w.a.remainingTime[1]==177000,"gote time agrees on both sides")
        let f=Wire(50,rules);f.run(f.t+180000-1);ok(!f.a.over,"no flag before zero");f.run(f.t+2);ok(f.a.over && f.a.winner == -1 && f.a.result=="時間切れ","own flag detected")
        f.run(f.t+100);ok(f.b.over && f.b.winner == -1,"opponent learns of the flag")
        let c=Wire(50,rules);c.cut=true;c.run(c.t+180000+OnlineGame.grace-10);ok(!c.b.over,"claim waits for grace");c.run(c.t+20);ok(c.b.over && c.b.winner == -1,"disconnected opponent loses on time after grace")
        let r=Wire(30,rules);r.a.receive("MOVE 1 1 3c3d 1000",r.t);ok(r.a.movesPlayed==0,"peer move during my turn ignored");r.b.receive("MOVE 9 1 7g7f 1000",r.t);ok(r.b.movesPlayed==0,"wrong round ignored")
        r.b.receive("MOVE 1 2 7g7f 1000",r.t);ok(r.b.movesPlayed==0,"wrong ply ignored");r.b.receive("garbage",r.t);r.b.receive("MOVE 1 x",r.t);ok(!r.b.over,"malformed input ignored")
        r.b.receive("MOVE 1 1 5i5a 1000",r.t);ok(r.b.over && r.b.winner == -1 && r.b.error != nil,"illegal move from peer forfeits")
        let m=Wire(30,rules);m.a.resign();m.flush();m.run(m.t+100);ok(m.a.over && m.b.over && m.a.winner == -1 && m.b.winner == -1,"resignation agreed")
        m.a.requestRematch(m.t);m.flush();m.run(m.t+100);ok(!m.b.started || m.b.over,"rematch needs both");m.b.requestRematch(m.t);m.flush();m.run(m.t+100)
        ok(!m.a.over && !m.b.over && m.a.mySide == -1 && m.b.mySide==1 && m.a.round==2 && m.b.round==2,"rematch swaps sides");ok(m.b.myTurn && m.b.remainingTime[0]==180000,"new round fresh clocks")
        var v=OnlineGame(mySide:1,rules:rules);v.hello(0);v.receive("HELLO 99",0);ok(v.over && v.error != nil,"protocol mismatch reported")
        // Repetition: the initial position a fourth time is a draw on both sides.
        let s=Wire(10,rules);for (i,mv) in Array(repeating:["2h3h","8b7b","3h2h","7b8b"],count:3).flatMap({$0}).enumerated(){if i%2==0{ok(s.a.localMove(mv,s.t),"rep \(i)")}else{ok(s.b.localMove(mv,s.t),"rep \(i)")};s.flush();s.run(s.t+20)}
        ok(s.a.winner==2 && s.b.winner==2 && s.a.result=="千日手・引き分け","sennichite draw on both sides: \(s.a.result)")
        print("clocks ok")
    }
    static func link(){
        let ea=try! UDPEndpoint(),eb=try! UDPEndpoint();var key=[UInt8](repeating:0,count:32),id=[UInt8](repeating:0,count:32);for i in 0..<32{key[i]=UInt8(i*7&255);id[i]=UInt8(i*13&255)}
        let ra=NSMutableArray(),rb=NSMutableArray();var links=[P2PLink?](repeating:nil,count:2);let captured=NSMutableArray()
        links[0]=P2PLink(endpoint:ea,secret:key,sessionId:id,role:0,relay:{p in captured.add(p);links[1]?.receiveRelay(p)},onMessage:{ra.add($0)},onPath:{_,_,_ in},onPeerClosed:{})
        links[1]=P2PLink(endpoint:eb,secret:key,sessionId:id,role:1,relay:{p in links[0]?.receiveRelay(p)},onMessage:{rb.add($0)},onPath:{_,_,_ in},onPeerClosed:{})
        ea.gather([]);eb.gather([]);ok(!ea.candidates.isEmpty,"loopback host candidates: \(ea.candidates)");links[0]!.addCandidates(eb.candidates);links[1]!.addCandidates(ea.candidates)
        waitFor(5000,"direct UDP path"){links[0]!.isDirect && links[1]!.isDirect}
        ea.dropPercent=30;eb.dropPercent=30;for i in 0..<200{links[0]!.send("a\(i)");links[1]!.send("b\(i)")}
        waitFor(30000,"reliable delivery under 30% UDP loss"){ra.count==200 && rb.count==200}
        for i in 0..<200{ok(rb[i] as! String=="a\(i)" && ra[i] as! String=="b\(i)","in order exactly once \(i)")}
        ea.dropPercent=0;eb.dropPercent=0
        let old=captured[0] as! [UInt8];let before=rb.count;links[1]!.receiveRelay(old);var forged=old;forged[forged.count-1]^=1;links[1]!.receiveRelay(forged);var fresh=old;fresh[12]=0x7f;links[1]!.receiveRelay(fresh)
        usleep(200000);ok(rb.count==before && links[1]!.stats.hasSuffix("rejected=1"),"replayed and tampered packets dropped; the fresh-counter forgery fails AES-GCM: \(links[1]!.stats)")
        ea.dropPercent=100;eb.dropPercent=100;waitFor(8000,"dead UDP path detected"){!links[0]!.isDirect && !links[1]!.isDirect}
        links[0]!.send("via relay");waitFor(8000,"relay fallback delivers"){rb.contains("via relay")}
        ea.dropPercent=0;eb.dropPercent=0;waitFor(15000,"direct path recovers"){links[0]!.isDirect && links[1]!.isDirect}
        links[0]!.close();links[1]!.close();ea.close();eb.close();print("link ok")
    }
    /// Alternating moves: the Swift side picks a random legal move, the Android bot uses its own CPU choice.
    static func play(_ s:SwiftClient,_ bot:Bot,_ plies:Int,script:[String]=[])->[Int64] {
        var latency=[Int64]()
        for i in 0..<plies {
            guard let g=s.game,!g.over,bot.int("winner")==0 else{break};let before=g.movesPlayed;let t0=monotonic()
            let usi=i<script.count ? script[i]:nil
            if g.myTurn {let mv=usi ?? g.legal.randomElement()!;ok(s.with{$0!.localMove(mv,monotonic())},"swift move \(mv)");waitFor(20000,"bot receives ply \(before+1)"){bot.int("plies")==before+1}}
            else {bot.send(usi.map{"play "+$0} ?? "move");waitFor(20000,"swift receives ply \(before+1) (bot: \(bot["error"]) swift: \(s.game?.error ?? "-"))"){s.game?.movesPlayed==before+1}}
            latency.append(monotonic()-t0);waitFor(3000,"same board after ply \(before+1)"){bot["sfen"]==s.game?.sfen}
        }
        return latency
    }
    static func summary(_ l:[Int64])->String {let s=l.sorted();return s.isEmpty ? "n/a":"median=\(s[s.count/2])ms max=\(s.last!)ms n=\(s.count)"}
    static func matchBot(_ cp:String,_ relay:String,_ lobby:String,_ rules:OnlineRules,swiftMode:Int,botSeek:String,code:String?=nil,drop:Bool=false)->(SwiftClient,Bot){
        Netplay.lobbyTag=lobby;let s=SwiftClient("swift",relays:[relay],rules:rules),bot=Bot(cp,relay:relay,lobby:lobby)
        waitFor(10000,"relays open"){s.relaysOpen==1 && bot.int("relays")==1}
        if drop{s.net.endpoint.dropPercent=100;bot.send("drop 100")}
        s.net.seek(swiftMode,code:code);bot.send("seek "+botSeek)
        waitFor(20000,"cross-platform match"){s.started && bot["started"]=="1"};ok(s.side == -bot.int("side"),"opposite sides");return (s,bot)
    }
    static func run(classpath cp:String){
        let rules=JavaRules(cp);clocks(rules);UDPEndpoint.allowLoopback=true;link()
        let relay=JavaProcess(classpath:cp,main:"LocalRelay",args:[]);var url="";let got=DispatchSemaphore(value:0);relay.onLine={l in if url.isEmpty{url=l;got.signal()}};_=got.wait(timeout:.now()+10);ok(url.hasPrefix("ws://"),"local relay \(url)")
        defer{relay.stop()}
        // 1. Quick match iPhone vs Android with direct UDP, resignation, rematch with swapped sides, leaving.
        var (s,bot)=matchBot(cp,url,"interop-quick",rules,swiftMode:Netplay.quick,botSeek:"quick")
        waitFor(5000,"direct UDP between Swift and Java"){s.direct && bot["direct"]=="1"}
        var lat=play(s,bot,40);print("swift<->java direct loopback move latency \(summary(lat))");ok(lat.max()!<1000,"direct moves arrive quickly")
        if s.game!.myTurn || s.side==1 {s.with{$0!.resign()}}else{bot.send("resign")}
        waitFor(5000,"resignation agreed"){s.game!.over && bot.int("winner") != 0};ok(s.game!.winner==bot.int("winner"),"same winner \(s.game!.winner) \(bot.int("winner"))")
        s.with{$0!.requestRematch(monotonic())};bot.send("rematch");waitFor(5000,"rematch"){s.game!.round==2 && bot.int("round")==2 && !s.game!.over && bot.int("winner")==0}
        ok(s.game!.mySide == -s.side && bot.int("side") == -s.game!.mySide,"rematch swapped");_=play(s,bot,6)
        s.close();waitFor(8000,"Android peer sees the iPhone leave and wins"){bot["left"]=="1" && bot.int("winner")==bot.int("side")};bot.send("quit")
        // 2. Repetition draw reached through the network on both platforms.
        (s,bot)=matchBot(cp,url,"interop-repetition",rules,swiftMode:Netplay.quick,botSeek:"quick")
        _=play(s,bot,12,script:Array(repeating:["2h3h","8b7b","3h2h","7b8b"],count:3).flatMap{$0})
        waitFor(3000,"draw on both"){s.game!.winner==2 && bot.int("winner")==2};ok(s.game!.result==bot["result"],"same result text \(s.game!.result)");s.close();bot.send("quit")
        // 3. UDP blocked on both sides: everything through the relay.
        (s,bot)=matchBot(cp,url,"interop-relay",rules,swiftMode:Netplay.quick,botSeek:"quick",drop:true)
        lat=play(s,bot,10);ok(!s.direct && bot["direct"]=="0","relay only");print("swift<->java relay-only move latency (local relay) \(summary(lat))");s.close();bot.send("quit")
        // 4. Rooms in both directions.
        (s,bot)=matchBot(cp,url,"interop-room1",rules,swiftMode:Netplay.guest,botSeek:"host 246810",code:"246810");_=play(s,bot,4);s.close();bot.send("quit")
        (s,bot)=matchBot(cp,url,"interop-room2",rules,swiftMode:Netplay.host,botSeek:"guest 135790",code:"135790");_=play(s,bot,4);s.close();bot.send("quit")
        // 5. Two iPhones.
        Netplay.lobbyTag="interop-swift-only";let a=SwiftClient("a",relays:[url],rules:rules),b=SwiftClient("b",relays:[url],rules:rules)
        waitFor(10000,"relays"){a.relaysOpen==1 && b.relaysOpen==1};a.net.seek(Netplay.quick,code:nil);b.net.seek(Netplay.quick,code:nil);waitFor(20000,"swift pair"){a.started && b.started}
        waitFor(5000,"swift direct"){a.direct && b.direct}
        for i in 0..<10 {let mover=a.game!.myTurn ? a:b,other=mover===a ? b:a;let mv=mover.game!.legal.randomElement()!;ok(mover.with{$0!.localMove(mv,monotonic())},"move \(i)");waitFor(5000,"swift peer receives \(i)"){other.game!.movesPlayed==i+1};ok(a.game!.sfen==b.game!.sfen,"swift boards equal")}
        a.close();waitFor(8000,"leave noticed"){b.left};b.close()
        print("interop ok")
    }
}
