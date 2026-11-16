import Foundation

/// The opponent for a random match when no person is found within `waitMillis`, as on Android (OnlineBot.java).
/// It is clearly a bot: its name says ボット and the top bar shows ボット対局. Ten levels with fixed ratings 700 to
/// 2050; the one closest to the player's rating plays. Level 1 is a one-ply greedy pick with random noise (the same
/// scoring as Android's ShogiGame.cpuChoice); levels 2 to 10 ask the bundled engine with the CPU-match budgets of
/// AI Lv.1 to Lv.9, falling back to the greedy pick if the engine fails. It answers through the same text protocol as a remote player, so clocks,
/// results, rematches and rating work as in a game against a person; the game is rated against its fixed rating.
@MainActor final class OnlineBot {
    static let names=["ひよこ","すずめ","めじろ","うぐいす","つばめ","かもめ","はやぶさ","たか","わし","くまたか"],levels=10,games=100
    static func clamp(_ level:Int)->Int {max(1,min(levels,level))}
    static func rating(_ level:Int)->Int {700+150*(clamp(level)-1)}
    static func name(_ level:Int)->String {"ボット・\(names[clamp(level)-1])"}
    static func pubkey(_ level:Int)->String {"bot-lv\(clamp(level))-v1"}
    /// The level whose rating is closest to the player's (the weaker one on a tie).
    static func level(for playerRating:Int)->Int {var best=1;for k in 2...levels where abs(rating(k)-playerRating)<abs(rating(best)-playerRating){best=k};return best}
    /// How long a random match waits for a person before the bot steps in (UI tests shorten it).
    static var waitMillis:Int64=20000
    var identity:OnlineIdentity {OnlineIdentity(pubkey:Self.pubkey(level),name:Self.name(level),rating:Self.rating(level),games:Self.games)}
    let level:Int
    private(set) var closed=false
    private var scheduledPly = -1,scheduledRound=0
    private let game:@MainActor ()->OnlineGame?,deliver:@MainActor (String)->Void
    init(level:Int,game:@escaping @MainActor ()->OnlineGame?,deliver:@escaping @MainActor (String)->Void){self.level=Self.clamp(level);self.game=game;self.deliver=deliver}
    private func after(_ ms:Int64,_ work:@escaping @MainActor ()->Void){Task{@MainActor [weak self] in if ms>0{try? await Task.sleep(nanoseconds:UInt64(ms)*1_000_000)};guard let self,!self.closed else{return};work()}}
    /// The player's game sends here; replies always arrive later, never inside the call.
    func fromPlayer(_ text:String){
        let f=text.split(separator:" ").map(String.init);guard let kind=f.first else{return}
        switch kind {
        case "HELLO":after(150){[self] in if let g=game(){deliver("HELLO \(OnlineGame.protocolVersion) \(g.time)")};think()}
        case "MOVE":after(0){[self] in think()}
        case "REMATCH" where f.count>=2:let next=f[1];after(400){[self] in deliver("REMATCH \(next)");think()}
        default:break
        }
    }
    /// Schedules one move when it is the bot's turn: 1 to 4 seconds, never more than a fortieth of its remaining time.
    func think(){
        guard let g=game(),g.started,!g.over,g.turn == -g.mySide else{return};let ply=g.movesPlayed+1
        guard !(scheduledPly==ply && scheduledRound==g.round) else{return};scheduledPly=ply;scheduledRound=g.round
        let left=g.remaining(g.turn,AppModel.onlineNow),wait=min(1000+Int64.random(in:0..<3000),max(200,left/40)),round=g.round
        after(wait){[self] in play(round:round,ply:ply)}
    }
    private func play(round:Int,ply:Int){
        guard let g=game(),g.round==round,!g.over,g.turn == -g.mySide,g.movesPlayed+1==ply else{return}
        let send:@MainActor (String?)->Void={[weak self] usi in
            guard let self,!self.closed,let usi,let now=self.game(),now.round==round,!now.over,now.turn == -now.mySide,now.movesPlayed+1==ply,now.legal.contains(usi) else{return}
            let left=now.remaining(now.turn,AppModel.onlineNow);guard left>0 else{return};self.deliver("MOVE \(round) \(ply) \(usi) \(left)")}
        guard level>1,let position=try? Position(g.sfen) else{send(Self.choice(g));return}
        let budget=MatchOptions.nodeBudgets[level-2],time=MatchOptions.timeBudgets[level-2],sfen=g.sfen
        NativeEngine.shared.search(position,continuous:false,nodes:budget,milliseconds:time){[weak self] result in
            guard let self,let now=self.game(),now.sfen==sfen else{return}
            if case .success(let update)=result,let best=update.best,now.legal.contains(best){send(best)}else{send(Self.choice(now))}}
    }
    /// The weak move: random noise up to 95 plus 2.6 times the captured piece's value, 180 for a promotion and up to 32 for central files.
    static func choice(_ g:OnlineGame)->String? {
        guard let p=try? Position(g.sfen) else{return nil};let values=[0,100,270,310,400,500,800,950,10000];var best:String?,high = -1e9
        for usi in g.legal {let m=Move(usi:usi),to=m.destination;guard (0..<81).contains(to) else{continue};let dest=p.board[to]
            let score=Double.random(in:0..<95)+(dest==0 ? 0:2.6*Double(values[Position.base(dest)]))+(m.promotes ? 180:0)+Double(4-abs(to%9-4))*8
            if score>high{high=score;best=usi}}
        return best
    }
    func close(){closed=true}
}
