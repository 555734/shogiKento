import Foundation

struct Evaluation: Codable, Equatable {
    var value: Int,mate=false,bound=0
    var percentage: Double {mate ? (value>0 ? 100:0):100/(1+exp(-Double(max(-60000,min(60000,value)))/600))}
    var graph: Double {mate ? (value>0 ? 2000 : -2000):Double(max(-2000,min(2000,value)))}
    var text: String {(bound>0 ? "≥":bound<0 ? "≤":"")+(mate ? "詰み \(value)":"\(value>0 ? "+":"")\(value)")}
}
struct Candidate {var move: Move,score: Evaluation,depth: Int}
struct EngineUpdate {var candidates: [Candidate],depth: Int,nodes: Int,best: String?}
final class NativeEngine: @unchecked Sendable {
    static let shared=NativeEngine()
    private let worker=DispatchQueue(label:"benishogi.usi"),lock=NSLock()
    private var generation=0,prepared=false,started=false
    private func token()->Int {lock.lock();defer{lock.unlock()};return generation}
    func cancel(){lock.lock();generation+=1;let running=started;lock.unlock();if running{BKSend("stop")}}
    private func send(_ text:String){BKSend(text)}
    private func read(_ milliseconds:Int32=50)->String? {guard let p=BKRead(milliseconds) else{return nil};defer{BKFree(p)};return String(cString:p)}
    private func until(_ marker:String,timeout:Double=20)throws {let end=ProcessInfo.processInfo.systemUptime+timeout;while ProcessInfo.processInfo.systemUptime<end{if let line=read(){if line==marker{return};if line.contains("Error!") || line.hasPrefix("Error:"){throw ShogiError.message(line)}}};throw ShogiError.message("AIの応答がタイムアウトしました：\(marker)")}
    private func prepare()throws {
        if prepared{return};guard let dir=Bundle.main.url(forResource:"ai",withExtension:nil,subdirectory:"GameAssets"),FileManager.default.fileExists(atPath:dir.appendingPathComponent("nn.bin").path),FileManager.default.fileExists(atPath:dir.appendingPathComponent("progress.bin").path) else{throw ShogiError.message("AI評価関数が見つかりません")}
        BKStart();lock.lock();started=true;lock.unlock();send("usi");try until("usiok")
        for option in ["Threads value 1","USI_Hash value 16","USI_Ponder value false","EvalDir value \(dir.path)","FV_SCALE value 24","BookFile value no_book","USI_OwnBook value false","EnteringKingRule value NoEnteringKing","PvInterval value 200"] {send("setoption name "+option)}
        send("isready");try until("readyok");prepared=true
    }
    func search(_ position:Position,continuous:Bool,nodes:Int=50000,receive:@escaping (Result<EngineUpdate,Error>)->Void) {
        cancel();let revision=token()
        worker.async { [self] in
            do {try prepare();while token()==revision {
                send("isready");try until("readyok",timeout:10);if token() != revision{return}
                let count=continuous ? max(1,min(3,try Rules.legal(position).count)):1
                send("setoption name MultiPV value \(count)");send("position sfen "+position.sfen);send(continuous ? "go infinite":"go nodes \(nodes) movetime 750")
                var rows=[Int:Candidate](),visited=0,lastPublish=0.0,stopAt:Double?,finished=false
                let began=ProcessInfo.processInfo.systemUptime
                while !finished {
                    let now=ProcessInfo.processInfo.systemUptime
                    if stopAt==nil && (token() != revision || !continuous && now-began>0.9){send("stop");stopAt=now+5}
                    if let stopAt,now>stopAt {throw ShogiError.message("AIの停止がタイムアウトしました")}
                    guard let line=read() else{continue}
                    if line.contains("Error!") || line.hasPrefix("Error:"){throw ShogiError.message(line)}
                    var best:String?
                    if line.hasPrefix("bestmove "){best=String(line.split(separator:" ")[1]);finished=true}
                    if line.hasPrefix("info ") && !line.hasPrefix("info string ") {
                        let a=line.split(separator:" ").map(String.init);func number(_ key:String)->Int?{guard let i=a.firstIndex(of:key),i+1<a.count else{return nil};return Int(a[i+1])}
                        visited=number("nodes") ?? visited
                        if let depth=number("depth"),let score=a.firstIndex(of:"score"),score+2<a.count,let value=Int(a[score+2]),let pv=a.firstIndex(of:"pv"),pv+1<a.count {
                            rows[number("multipv") ?? 1]=Candidate(move:Move(usi:a[pv+1]),score:Evaluation(value:value*position.side,mate:a[score+1]=="mate",bound:(a.contains("lowerbound") ? 1:a.contains("upperbound") ? -1:0)*position.side),depth:depth)
                        }
                    }
                    let complete=(1...count).compactMap{rows[$0]}
                    if token()==revision && (finished || continuous && now-lastPublish>=0.25),complete.count==count,Set(complete.map(\.depth)).count==1 {
                        lastPublish=now;let update=EngineUpdate(candidates:complete,depth:complete[0].depth,nodes:visited,best:best)
                        DispatchQueue.main.async{if self.token()==revision{receive(.success(update))}}
                    } else if finished && !continuous && token()==revision {let update=EngineUpdate(candidates:complete,depth:complete.first?.depth ?? 0,nodes:visited,best:best);DispatchQueue.main.async{if self.token()==revision{receive(.success(update))}}}
                }
                if !continuous || token() != revision{return};Thread.sleep(forTimeInterval:0.25)
            }}catch{DispatchQueue.main.async{if self.token()==revision{receive(.failure(error))}}}
        }
    }
}
