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

    /// Keychain secret and JSON record survive a reload; an unfinished rated game is settled as a loss on load.
    func testAccountStoreAndRatedLifecycle()throws {
        let store=OnlineAccountStore(service:"test-\(UUID().uuidString)",defaults:UserDefaults(suiteName:"online-test-\(UUID().uuidString)")!);defer{store.deleteAll()}
        let first=store.load();XCTAssertEqual(first.account.rating,1500);XCTAssertTrue(first.account.name.hasPrefix("棋士"));XCTAssertEqual(store.load().key.publicHex,first.key.publicHex,"the key persists in the Keychain")
        XCTAssertEqual(Nip19.decode("nsec",Nip19.encode("nsec",first.secret)),first.secret)
        var a=first.account;let other=Secp256k1.Key.random(),sid=[UInt8](repeating:9,count:32),card=try XCTUnwrap(OnlineIdentity.parse(OnlineIdentity.message(other,sessionId:sid,name:"相手",rating:1600,games:40),sessionId:sid))
        var (x,y)=pair();guard case .begun=a.settle(rated:true,opponent:card,game:x,now:1,myKey:first.key.publicHex) else{return XCTFail("rated game begins")};store.saveAccount(a)
        let reloaded=store.load();XCTAssertEqual(reloaded.abandoned?.result,-1);XCTAssertEqual(reloaded.account.rating,1500+Rating.delta(1500,games:0,1600,score:0),"quitting mid-game is a loss")
        a=reloaded.account;x=OnlineGame(mySide:1,rules:EngineRules());y=OnlineGame(mySide:-1,rules:EngineRules());x.hello(0);y.hello(0);deliver(&x,&y,0)
        _=a.settle(rated:true,opponent:card,game:x,now:2,myKey:first.key.publicHex);XCTAssertTrue(x.localMove("7g7f",10));deliver(&x,&y,10);XCTAssertTrue(y.localMove("3c3d",20));deliver(&x,&y,20);y.resign();deliver(&x,&y,30)
        guard case .settled(let g)=a.settle(rated:true,opponent:card,game:x,now:3,myKey:first.key.publicHex) else{return XCTFail("settled once")};XCTAssertEqual(g.result,1);XCTAssertEqual(a.wins,1)
        guard case .unchanged=a.settle(rated:true,opponent:card,game:x,now:4,myKey:first.key.publicHex) else{return XCTFail("not settled twice")}
        var (p,q)=pair();_=a.settle(rated:true,opponent:card,game:p,now:5,myKey:first.key.publicHex);p.resign();deliver(&p,&q,1)
        guard case .aborted=a.settle(rated:true,opponent:card,game:p,now:6,myKey:first.key.publicHex) else{return XCTFail("resigning before both moved is aborted")};XCTAssertEqual(a.games,2)
        guard case .unchanged=a.settle(rated:false,opponent:card,game:x,now:7,myKey:first.key.publicHex) else{return XCTFail("rooms are unrated")}
    }

    final class Peer:NetplayListener,@unchecked Sendable {
        let lock=NSLock();var game:OnlineGame?;var direct=false,rtt = -1,left=false,relays=0;let net:Netplay
        var account=OnlineAccount(name:"iPhoneTest");let key=Secp256k1.Key.random();var peerId:OnlineIdentity?,cardRound=0
        init(relay:String){net=try! Netplay(relays:[relay],stun:[],factory:URLSessionWebSocket.factory);net.listener=self;net.start()}
        func with<T>(_ f:(inout OnlineGame)->T)->T? {lock.lock();defer{lock.unlock()};guard var g=game else{return nil};let r=f(&g);let out=g.outbox;g.outbox=[];game=g;for m in out{net.send(m)};sync();return r}
        /// As the app does: settle rated games and send a fresh account card each round.
        private func sync(){_=account.settle(rated:net.ratedSession,opponent:peerId,game:game,now:Int64(Date().timeIntervalSince1970),myKey:key.publicHex)
            if let g=game,let sid=net.sessionId,g.round != cardRound{cardRound=g.round;net.send(OnlineIdentity.message(key,sessionId:sid,name:account.name,rating:account.rating,games:account.games))}}
        func matched(localSide:Int){lock.lock();var g=OnlineGame(mySide:localSide,rules:EngineRules());g.hello(AppModel.onlineNow);let out=g.outbox;g.outbox=[];game=g;peerId=nil;cardRound=0;for m in out{net.send(m)};sync();lock.unlock()}
        func message(_ text:String){if text.hasPrefix("ID "){lock.lock();peerId=net.sessionId.flatMap{OnlineIdentity.parse(text,sessionId:$0)};sync();lock.unlock();return};_=with{$0.rttMillis=rtt;return $0.receive(text,AppModel.onlineNow)}}
        var card:OnlineIdentity? {lock.lock();defer{lock.unlock()};return peerId}
        var record:OnlineAccount {lock.lock();defer{lock.unlock()};return account}
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
        wait(10,"the Android account card"){peer.card != nil};XCTAssertEqual(peer.card?.name,"AndroidBot");XCTAssertTrue(peer.net.ratedSession)
        var latencies=[Double]()
        for ply in 0..<30 {
            guard let g=peer.snapshot,!g.over else{break};let t0=Date()
            if g.myTurn {let move=try XCTUnwrap(g.legal.randomElement());XCTAssertEqual(peer.with{$0.localMove(move,AppModel.onlineNow)},true,move)}
            wait(20,"ply \(ply+1) (\(peer.snapshot?.error ?? "no error"))"){(peer.snapshot?.movesPlayed ?? 0)>ply || peer.snapshot?.over==true}
            if !g.myTurn{latencies.append(Date().timeIntervalSince(t0)*1000)}
        }
        let played=try XCTUnwrap(peer.snapshot);XCTAssertNil(played.error,"every Android move was legal under the iOS engine rules");XCTAssertGreaterThanOrEqual(played.movesPlayed,20)
        let botRating=try XCTUnwrap(peer.card?.rating)
        if !played.over{_=peer.with{$0.resign()};XCTAssertEqual(peer.snapshot?.winner,-played.mySide)}
        let final=try XCTUnwrap(peer.snapshot);let score=final.winner==2 ? 0.5:final.winner==final.mySide ? 1:0
        XCTAssertEqual(peer.record.games,1);XCTAssertEqual(peer.record.rating,Rating.next(1500,games:0,botRating,score:score),"rated with the opponent's signed rating")
        _=peer.with{$0.requestRematch(AppModel.onlineNow)};wait(15,"rematch accepted by the Android client"){peer.snapshot?.round==2 && peer.snapshot?.over==false}
        XCTAssertEqual(peer.snapshot?.mySide,-played.mySide)
        for ply in 0..<4 {guard let g=peer.snapshot,!g.over else{break};if g.myTurn{_=peer.with{$0.localMove(g.legal.randomElement()!,AppModel.onlineNow)}};wait(20,"round 2 ply \(ply+1)"){(peer.snapshot?.movesPlayed ?? 0)>ply}}
        XCTAssertNil(peer.snapshot?.error)
        let sorted=latencies.sorted(),report=XCTAttachment(string:"Android bot replies (includes its 300 ms think time): median \(Int(sorted.isEmpty ? 0:sorted[sorted.count/2]))ms max \(Int(sorted.last ?? 0))ms n=\(sorted.count)\nfinal round 1 SFEN \(played.sfen)\n\(peer.net.diagnostics)")
        report.name="online-interop";report.lifetime = .keepAlways;add(report)
    }
}
