import Foundation

/// Shogi rules over SFEN/USI text. The app answers with the bundled engine; host tests can plug in another rules source.
protocol OnlineRules {
    func legal(_ sfen:String)->[String]?
    func apply(_ sfen:String,_ usi:String)->String?
    func inCheck(_ sfen:String)->Bool
}

/// Two-player 3-minute sudden death over an unreliable network, without a server; message-compatible with
/// Android's OnlineGame. Both peers check every received move against their own rules. Each player's own clock
/// is measured locally; a mover reports its remaining time, and the receiver accepts it only within a latency
/// allowance, so neither side is charged for network delay and a modified client cannot stop its own clock.
/// Messages to the peer collect in `outbox`; the owner sends and clears them after each call.
struct OnlineGame {
    static let protocolVersion=1,time:Int64=180000,minAllowance:Int64=2000,grace:Int64=4000
    static let initialSFEN="lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
    let rules:OnlineRules
    private(set) var sfen=OnlineGame.initialSFEN,legal=[String](),turn=1,winner=0,result="",movesPlayed=0,remainingTime:[Int64]=[OnlineGame.time,OnlineGame.time]
    private var positions=[String](),checkingSides=[Int]()
    private(set) var mySide:Int,round=1,helloSent=false,peerHello=false,started=false,rematchMine=false,rematchPeer=false,peerLeft=false
    private var turnStart:Int64=0,turnStartRemaining:Int64=OnlineGame.time
    var rttMillis = -1;private(set) var error:String?,lastMove:String?,moves=[String]();var outbox=[String]()
    init(mySide:Int,rules:OnlineRules){self.mySide=mySide;self.rules=rules;restart()}
    static func slot(_ side:Int)->Int {side==1 ? 0:1}
    static func key(_ sfen:String)->String {sfen.split(separator:" ").prefix(3).joined(separator:" ")}
    var over:Bool {winner != 0}
    var myTurn:Bool {started && !over && turn==mySide}
    var allowance:Int64 {max(Self.minAllowance,rttMillis<0 ? 0:2*Int64(rttMillis)+500)}
    private mutating func restart(){sfen=Self.initialSFEN;turn=1;winner=0;result="";movesPlayed=0;remainingTime=[Self.time,Self.time];positions=[Self.key(sfen)];checkingSides=[];moves=[];legal=rules.legal(sfen) ?? []}
    func inCheck(_ side:Int)->Bool {side==turn && rules.inCheck(sfen)}
    mutating func hello(_ now:Int64){if !helloSent{helloSent=true;outbox.append("HELLO \(Self.protocolVersion)")};maybeStart(now)}
    private mutating func maybeStart(_ now:Int64){if helloSent && peerHello && !started{started=true;turnStart=now;turnStartRemaining=remainingTime[Self.slot(turn)]}}
    /// Remaining time as currently displayed; the side to move counts down from its turn start. May be negative for the opponent before a claim.
    func remaining(_ side:Int,_ now:Int64)->Int64 {if !started || over || side != turn{return remainingTime[Self.slot(side)]};return turnStartRemaining-(now-turnStart)}
    private mutating func finish(_ winner:Int,_ result:String){if over{return};self.winner=winner;self.result=result}
    /// Same repetition and mate rules as the Android ShogiGame, so both peers reach the same result.
    private mutating func play(_ usi:String,_ side:Int)->Bool {
        guard let next=rules.apply(sfen,usi) else{return false}
        sfen=next;turn = -side;movesPlayed+=1;moves.append(usi);lastMove=usi;checkingSides.append(rules.inCheck(next) ? side:0)
        let key=Self.key(next);positions.append(key);let occurrences=positions.indices.filter{positions[$0]==key}
        legal=rules.legal(next) ?? []
        if occurrences.count>=4 {let start=occurrences[occurrences.count-4]
            for s in [1,-1] {var continuous=true,any=false
                for i in start..<checkingSides.count {let mover=(movesPlayed-i)%2==0 ? turn:-turn;if mover==s{any=true;if checkingSides[i] != s{continuous=false}}}
                if continuous && any {winner = -s;result="連続王手の千日手";return true}}
            winner=2;result="千日手・引き分け";return true}
        if legal.isEmpty {winner=side;result=rules.inCheck(next) ? "詰み":"合法手なし"}
        return true
    }
    /// Detects my own flag immediately and the opponent's only after GRACE, so their in-flight move can still arrive.
    mutating func tick(_ now:Int64){
        guard started,!over else{return};let left=remaining(turn,now)
        if turn==mySide && left<=0 {remainingTime[Self.slot(mySide)]=0;finish(-mySide,"時間切れ");outbox.append("FLAG \(round)")}
        else if turn != mySide && left <= -max(Self.grace,allowance) {remainingTime[Self.slot(-mySide)]=0;finish(mySide,"時間切れ");outbox.append("CLAIM \(round) \(movesPlayed)")}
    }
    @discardableResult mutating func localMove(_ usi:String,_ now:Int64)->Bool {
        tick(now);guard myTurn,legal.contains(usi) else{return false};let left=turnStartRemaining-(now-turnStart);guard left>0,play(usi,mySide) else{return false}
        remainingTime[Self.slot(mySide)]=left;outbox.append("MOVE \(round) \(movesPlayed) \(usi) \(left)")
        turnStart=now;turnStartRemaining=remainingTime[Self.slot(turn)];return true
    }
    mutating func resign(){if started && !over{outbox.append("RESIGN \(round)");finish(-mySide,"投了")}}
    mutating func requestRematch(_ now:Int64){if !over || rematchMine || peerLeft{return};rematchMine=true;outbox.append("REMATCH \(round+1)");if rematchPeer{nextRound(now)}}
    private mutating func nextRound(_ now:Int64){round+=1;mySide = -mySide;restart();rematchMine=false;rematchPeer=false;lastMove=nil;error=nil;started=true;turnStart=now;turnStartRemaining=Self.time}
    mutating func markPeerLeft(){peerLeft=true;if started{finish(mySide,"相手の退出")}}
    /// Applies one peer message; malformed or out-of-order input never changes the board. Returns true if the board changed.
    @discardableResult mutating func receive(_ text:String,_ now:Int64)->Bool {
        let f=text.trimmingCharacters(in:.whitespaces).components(separatedBy:" ");guard let op=f.first else{return false}
        func number(_ i:Int)->Int? {i<f.count ? Int(f[i]):nil}
        switch op {
        case "HELLO":guard number(1)==Self.protocolVersion else{error="相手のアプリのバージョンが異なります";finish(2,"バージョン不一致");return false};peerHello=true;maybeStart(now);return false
        case "MOVE":
            guard f.count>=5,number(1)==round,!over,started,turn != mySide,let ply=number(2),ply==movesPlayed+1,let reported=Int64(f[4]) else{return false}
            guard legal.contains(f[3]) else{error="相手から不正な手を受信しました: "+f[3];finish(mySide,"相手の不正な手");return false}
            let measured=max(0,now-turnStart),upper=turnStartRemaining-max(0,measured-allowance),opponentLeft=max(0,min(min(reported,turnStartRemaining),upper))
            let opp=turn;guard play(f[3],opp) else{return false};remainingTime[Self.slot(opp)]=opponentLeft;turnStart=now;turnStartRemaining=remainingTime[Self.slot(turn)];return true
        case "RESIGN":if number(1)==round && started{finish(mySide,"投了")};return false
        case "FLAG":if number(1)==round && started{remainingTime[Self.slot(-mySide)]=0;finish(mySide,"時間切れ")};return false
        case "CLAIM":if number(1)==round && started{remainingTime[Self.slot(mySide)]=0;finish(-mySide,"時間切れ")};return false
        case "REMATCH":if number(1)==round+1 && over{rematchPeer=true;if rematchMine{nextRound(now);return true}};return false
        default:return false
        }
    }
    func status(_ now:Int64)->String {
        if over {let r=winner==2 ? result:(winner==mySide ? "勝ち":"負け")+"・"+result;return rematchMine ? r+"・再戦を申し込みました":rematchPeer ? r+"・相手が再戦を希望しています":r}
        if !started {return peerHello || !helloSent ? "対局を準備しています…":"相手の準備を待っています…"}
        return turn==mySide ? (inCheck(mySide) ? "王手です・あなたの手番":"あなたの手番"):"相手の手番"
    }
}
