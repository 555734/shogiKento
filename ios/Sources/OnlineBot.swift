import Foundation

/// The opponent for a random match when no person is found within `waitMillis`, as on Android (OnlineBot.java).
/// It is clearly a bot: its name says ボット and the top bar shows ボット対局. It is weak on purpose: each move is a
/// one-ply greedy pick (captures, promotions, central squares) with random noise and no look-ahead, the same scoring
/// as Android's ShogiGame.cpuChoice. It answers through the same text protocol as a remote player, so clocks,
/// results, rematches and rating work as in a game against a person; the game is rated against its fixed rating.
@MainActor final class OnlineBot {
    static let pubkey="bot-hiyoko-v1",name="ボット・ひよこ",rating=1000,games=100
    /// How long a random match waits for a person before the bot steps in (UI tests shorten it).
    static var waitMillis:Int64=20000
    static var identity:OnlineIdentity {OnlineIdentity(pubkey:pubkey,name:name,rating:rating,games:games)}
    private(set) var closed=false
    private var scheduledPly = -1,scheduledRound=0
    private let game:@MainActor ()->OnlineGame?,deliver:@MainActor (String)->Void
    init(game:@escaping @MainActor ()->OnlineGame?,deliver:@escaping @MainActor (String)->Void){self.game=game;self.deliver=deliver}
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
        guard let g=game(),g.round==round,!g.over,g.turn == -g.mySide,g.movesPlayed+1==ply,let usi=Self.choice(g) else{return}
        let left=g.remaining(g.turn,AppModel.onlineNow);guard left>0 else{return};deliver("MOVE \(round) \(ply) \(usi) \(left)")
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
