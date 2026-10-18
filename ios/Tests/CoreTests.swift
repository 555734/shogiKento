import XCTest
@testable import BeniShogi

final class CoreTests:XCTestCase {
    func fixture(_ ext:String)throws->URL {try XCTUnwrap(Bundle(for:Self.self).url(forResource:"quest79",withExtension:ext))}
    func testQuestEveryPositionAndNotation()throws {
        let source=try String(contentsOf:fixture("kif"),encoding:.utf8),record=try KifParser.parse(source)
        let oracle=try JSONSerialization.jsonObject(with:Data(contentsOf:fixture("json"))) as! [String:Any]
        let expected=oracle["positions"] as! [String],moves=oracle["moves"] as! [String]
        XCTAssertEqual(record.moves,79);XCTAssertEqual(record.positions,expected);XCTAssertEqual(record.sente,"ppap99(1715)");XCTAssertEqual(record.gote,"hiroponman(1702)");XCTAssertEqual(record.result,"詰み")
        for i in 0..<80 {let p=try Position(record.positions[i]);XCTAssertEqual(p.sfen,expected[i]);XCTAssertEqual(p.side,i%2==0 ? 1:-1);if i<79{let m=Move(usi:moves[i]);XCTAssertTrue(try Rules.legal(p).contains(m));XCTAssertEqual(try Rules.apply(p,m).sfen,expected[i+1])}}
        let last=try Position(record.positions[79]);XCTAssertTrue(Rules.checked(last));XCTAssertTrue(try Rules.legal(last).isEmpty)
        let variant=source.replacingOccurrences(of:"(47)",with:"（４７）").replacingOccurrences(of:" ",with:"　").replacingOccurrences(of:"\n",with:"\r\n")
        XCTAssertEqual(try KifParser.parse(variant).positions,expected)
        XCTAssertThrowsError(try KifParser.parse(source.replacingOccurrences(of:"79 ８三金打",with:"79 ８三歩打"))){XCTAssertTrue($0.localizedDescription.contains("79手目"))}
        XCTAssertThrowsError(try KifParser.parse(source.replacingOccurrences(of:"手合割：平手",with:"手合割：二枚落ち")))
        XCTAssertThrowsError(try KifParser.parse("ordinary clipboard text"))
    }
    func testLegalTargetsPinsDropsAndPromotion()throws {
        let p=try Position();XCTAssertEqual(try Rules.legal(p).count,30)
        let pawn=try Rules.legal(p).filter{$0.source==54};XCTAssertEqual(pawn.map(\.destination),[45])
        XCTAssertThrowsError(try Rules.apply(p,Move(usi:"9g9e")))
        // Gold is pinned on the rook's file; sideways moves would expose its king.
        var pin=try Position("4k4/9/9/4r4/9/9/4G4/9/4K4 b - 1")
        XCTAssertFalse(try Rules.legal(pin).contains(Move(usi:"5g4g")));XCTAssertTrue(try Rules.legal(pin).contains(Move(usi:"5g5f")))
        pin.hands[0][1]=1;pin.board[54]=1
        let drops=try Rules.legal(pin).filter{$0.drop==1};XCTAssertFalse(drops.contains{$0.destination%9==0 || $0.destination/9==0})
        let pro=try Position("4k4/P8/9/9/9/9/9/9/4K4 b - 1");let promotion=try Rules.legal(pro).filter{$0.source==9};XCTAssertEqual(promotion.count,1);XCTAssertTrue(promotion[0].promotes)
        var free=try Position();XCTAssertTrue(free.freeMove(source:54,drop:0,side:1,to:36));XCTAssertEqual(free.board[36],1);XCTAssertEqual(free.board[54],0)
        free.hands[0][1]=1;XCTAssertTrue(free.freeMove(source:nil,drop:1,side:1,to:45));XCTAssertEqual(free.analysisProblem,"二歩の局面です")
        XCTAssertThrowsError(try Rules.legal(free))
        let illegalTurn=try Position("4k4/9/4R4/9/9/9/9/9/4K4 b - 1")
        XCTAssertEqual(illegalTurn.analysisProblem,"手番ではない側の玉に王手がかかっています")
        XCTAssertThrowsError(try Rules.legal(illegalTurn))
    }
    func testDurableIndependentArchive()throws {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),store=try RecordStore(root:dir)
        defer{try? FileManager.default.removeItem(at:dir)}
        XCTAssertTrue(try store.list().isEmpty)
        let original=try KifParser.parse(String(contentsOf:fixture("kif"),encoding:.utf8));try store.save(original);try store.save(original);XCTAssertEqual(try store.list().count,1)
        let reopened=try RecordStore(root:dir).read(original.id);XCTAssertEqual(reopened,original)
        var working=reopened;working.positions=Array(working.positions.prefix(41));working.labels=Array(working.labels.prefix(41));XCTAssertEqual(try store.read(original.id).moves,79)
        var current=CurrentSession();current.study=working;current.cursor=40;try store.saveCurrent(current);XCTAssertEqual(try RecordStore(root:dir).current().study,working)
        var match=GameRecord();let p=try Position(),move=try XCTUnwrap(try Rules.legal(p).first),after=try Rules.apply(p,move);match.positions.append(after.sfen);match.labels.append("1手目 "+p.notation(move));try store.save(match);match.result="後手勝ち・投了";try store.save(match)
        XCTAssertEqual(try store.list().count,2);XCTAssertEqual(try store.read(match.id).moves,1);XCTAssertTrue(try store.read(match.id).result.contains("投了"))
        try Data("corrupted".utf8).write(to:dir.appendingPathComponent("broken.record.json"));XCTAssertEqual(try store.list().count,2);XCTAssertEqual(store.damaged,1)
        XCTAssertThrowsError(try store.read("../escape"))
        var bad=original;bad.labels=[];XCTAssertThrowsError(try store.save(bad));XCTAssertEqual(try store.read(original.id),original)
    }
    @MainActor func testClockPauseTimeoutAndMatchRetention()throws {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),store=try RecordStore(root:dir)
        defer{try? FileManager.default.removeItem(at:dir)}
        let model=AppModel(store:store);model.enter(.match);model.startMatch();let id=model.session.match.id
        model.sceneActive(false);let left=model.session.clocks;Thread.sleep(forTimeInterval:0.02);model.tick();XCTAssertEqual(model.session.clocks,left)
        model.sceneActive(true);model.session.clocks[0]=0.001;Thread.sleep(forTimeInterval:0.01);model.tick();XCTAssertEqual(model.session.winner,-1);XCTAssertTrue(try store.read(id).result.contains("時間切れ"))
        model.restartMatch();XCTAssertNotEqual(model.session.match.id,id);XCTAssertTrue(try store.read(id).result.contains("時間切れ"));model.sceneActive(false)
    }
    func testRealEmbeddedNNUEAndContinuousSearch()async throws {
        let p=try Position(),complete=expectation(description:"native NNUE reply")
        NativeEngine.shared.search(p,continuous:false,nodes:3000){result in switch result{case .failure(let error):XCTFail(error.localizedDescription);case .success(let update):if let best=update.best{XCTAssertTrue((try? Rules.legal(p).contains(Move(usi:best)))==true);XCTAssertGreaterThan(update.nodes,0);complete.fulfill()}}}
        await fulfillment(of:[complete],timeout:30)
        let progressing=expectation(description:"real unbounded NNUE updates"),lock=NSLock();var visits=[Int]()
        NativeEngine.shared.search(p,continuous:true){result in switch result{case .failure(let error):XCTFail(error.localizedDescription);case .success(let update):lock.lock();defer{lock.unlock()};guard visits.count<2 else{return};if let previous=visits.last{if update.nodes<=previous{return}};visits.append(update.nodes);XCTAssertEqual(update.candidates.count,3);XCTAssertTrue(update.candidates.allSatisfy{(try? Rules.legal(p).contains($0.move))==true});if visits.count==2{progressing.fulfill();NativeEngine.shared.cancel()}}}
        await fulfillment(of:[progressing],timeout:30);NativeEngine.shared.cancel()
    }
}
