import XCTest
@testable import BeniShogi

/// Online play with the bundled engine's rules, and (when CI provides a local relay and an Android bot) complete
/// games between this app's network code on the simulator and the Android Java client.
final class OnlineTests:XCTestCase {
    /// Two games wired back to back, as two phones would be.
    private func pair()->(OnlineGame,OnlineGame) {var a=OnlineGame(mySide:1,rules:EngineRules()),b=OnlineGame(mySide:-1,rules:EngineRules());a.hello(0);b.hello(0);deliver(&a,&b,0);return (a,b)}
    private func deliver(_ a:inout OnlineGame,_ b:inout OnlineGame,_ now:Int64){while !a.outbox.isEmpty || !b.outbox.isEmpty{let fromA=a.outbox,fromB=b.outbox;a.outbox=[];b.outbox=[];for m in fromA{b.receive(m,now)};for m in fromB{a.receive(m,now)}}}
    func testEngineRulesMatchTheAndroidProtocol()throws {
        var (a,b)=pair();XCTAssertTrue(a.started && b.started && a.myTurn);XCTAssertEqual(a.legal.count,30)
        XCTAssertTrue(a.localMove("7g7f",1000));deliver(&a,&b,1000)
        // The same SFEN the Android rules produce for this move (tests/interop/RulesOracle).
        XCTAssertEqual(b.sfen,"lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2");XCTAssertEqual(a.sfen,b.sfen)
        for (i,usi) in ["3c3d","8h2b+","3a2b","B*4e"].enumerated(){if i%2==0{XCTAssertTrue(b.localMove(usi,2000),usi)}else{XCTAssertTrue(a.localMove(usi,2000),usi)};deliver(&a,&b,2000);XCTAssertEqual(a.sfen,b.sfen)}
        XCTAssertTrue(a.sfen.contains(" w b "),a.sfen);XCTAssertTrue(b.inCheck(-1) == false)
        a.receive("MOVE 1 6 5a5i 1000",3000);XCTAssertTrue(a.over && a.winner==1 && a.error != nil,"an illegal peer move forfeits")
        var (x,y)=pair()
        for (i,usi) in Array(repeating:["2h3h","8b7b","3h2h","7b8b"],count:3).flatMap({$0}).enumerated(){if i%2==0{XCTAssertTrue(x.localMove(usi,Int64(i)))}else{XCTAssertTrue(y.localMove(usi,Int64(i)))};deliver(&x,&y,Int64(i))}
        XCTAssertEqual(x.winner,2);XCTAssertEqual(y.result,"千日手・引き分け")
        x.requestRematch(100);y.requestRematch(100);deliver(&x,&y,100);XCTAssertEqual(x.round,2);XCTAssertEqual(x.mySide,-1);XCTAssertTrue(y.myTurn)
    }

    final class Peer:NetplayListener,@unchecked Sendable {
        let lock=NSLock();var game:OnlineGame?;var direct=false,rtt = -1,left=false,relays=0;let net:Netplay
        init(relay:String){net=try! Netplay(relays:[relay],stun:[],factory:URLSessionWebSocket.factory);net.listener=self;net.start()}
        func with<T>(_ f:(inout OnlineGame)->T)->T? {lock.lock();defer{lock.unlock()};guard var g=game else{return nil};let r=f(&g);let out=g.outbox;g.outbox=[];game=g;for m in out{net.send(m)};return r}
        func matched(localSide:Int){lock.lock();var g=OnlineGame(mySide:localSide,rules:EngineRules());g.hello(AppModel.onlineNow);let out=g.outbox;g.outbox=[];game=g;lock.unlock();for m in out{net.send(m)}}
        func message(_ text:String){_=with{$0.rttMillis=rtt;return $0.receive(text,AppModel.onlineNow)}}
        func path(direct:Bool,rtt:Int,route:String){lock.lock();self.direct=direct;self.rtt=rtt;lock.unlock()}
        func relays(open:Int,total:Int){lock.lock();relays=open;lock.unlock()}
        func peerLeft(){lock.lock();left=true;lock.unlock();_=with{$0.markPeerLeft()}}
        var snapshot:OnlineGame? {lock.lock();defer{lock.unlock()};return game}
        var isDirect:Bool {lock.lock();defer{lock.unlock()};return direct}
    }
    private func wait(_ seconds:Double,_ what:String,_ condition:()->Bool){let end=Date().addingTimeInterval(seconds);while !condition(){if Date()>end{XCTFail("timeout: "+what);return};RunLoop.current.run(until:Date().addingTimeInterval(0.01))}}
    func testCompleteGamesAgainstAndroidClient()throws {
        let env=ProcessInfo.processInfo.environment
        guard let relay=env["ONLINE_RELAY"],!relay.isEmpty,let lobby=env["ONLINE_LOBBY"] else{throw XCTSkip("CI starts tests/relay/LocalRelay and an Android NetplayBot for this test")}
        Netplay.lobbyTag=lobby;UDPEndpoint.allowLoopback=true;defer{UDPEndpoint.allowLoopback=false}
        let peer=Peer(relay:relay);defer{peer.net.close()}
        wait(15,"local relay connected"){peer.relays==1};peer.net.seek(Netplay.quick,code:nil)
        wait(40,"matched with the Android bot"){peer.snapshot?.started==true};wait(10,"direct UDP path to the Android client"){peer.isDirect}
        var latencies=[Double]()
        for ply in 0..<30 {
            guard let g=peer.snapshot,!g.over else{break};let t0=Date()
            if g.myTurn {let move=try XCTUnwrap(g.legal.randomElement());XCTAssertEqual(peer.with{$0.localMove(move,AppModel.onlineNow)},true,move)}
            wait(20,"ply \(ply+1) (\(peer.snapshot?.error ?? "no error"))"){(peer.snapshot?.movesPlayed ?? 0)>ply || peer.snapshot?.over==true}
            if !g.myTurn{latencies.append(Date().timeIntervalSince(t0)*1000)}
        }
        let played=try XCTUnwrap(peer.snapshot);XCTAssertNil(played.error,"every Android move was legal under the iOS engine rules");XCTAssertGreaterThanOrEqual(played.movesPlayed,20)
        if !played.over{_=peer.with{$0.resign()};XCTAssertEqual(peer.snapshot?.winner,-played.mySide)}
        _=peer.with{$0.requestRematch(AppModel.onlineNow)};wait(15,"rematch accepted by the Android client"){peer.snapshot?.round==2 && peer.snapshot?.over==false}
        XCTAssertEqual(peer.snapshot?.mySide,-played.mySide)
        for ply in 0..<4 {guard let g=peer.snapshot,!g.over else{break};if g.myTurn{_=peer.with{$0.localMove(g.legal.randomElement()!,AppModel.onlineNow)}};wait(20,"round 2 ply \(ply+1)"){(peer.snapshot?.movesPlayed ?? 0)>ply}}
        XCTAssertNil(peer.snapshot?.error)
        let sorted=latencies.sorted(),report=XCTAttachment(string:"Android bot replies (includes its 300 ms think time): median \(Int(sorted.isEmpty ? 0:sorted[sorted.count/2]))ms max \(Int(sorted.last ?? 0))ms n=\(sorted.count)\nfinal round 1 SFEN \(played.sfen)\n\(peer.net.diagnostics)")
        report.name="online-interop";report.lifetime = .keepAlways;add(report)
    }
}
