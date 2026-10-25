import Foundation

/// The online player, as on Android: an account key held only on this device (its public half is the player ID),
/// a display name, the Elo rating with win/loss/draw counts and recent rated games. Random matches are rated; rooms
/// are not. A rated game still running when the app stopped counts as a loss when the account next loads, exactly
/// as leaving the game does. Encoded without the secret, which the Keychain holds.
struct OnlineAccount:Codable {
    static let historyLimit=200
    struct Game:Codable,Equatable,Identifiable {var opponent,opponentName,reason:String;var opponentRating,before,after,result:Int;var time:Int64;var id:String {"\(time)-\(opponent)"}}
    /// The rated game in progress, persisted so that quitting the app cannot avoid the result.
    struct Pending:Codable,Equatable {var opponent,name:String;var rating,games,round:Int;var started:Int64}
    var name:String,rating=Rating.initial,games=0,wins=0,losses=0,draws=0,publish=true,updated:Int64=0,history=[Game](),pending:Pending?
    init(name:String){self.name=name}
    static func defaultName(_ key:Secp256k1.Key)->String {"棋士"+key.publicHex.prefix(4).uppercased()}
    var provisional:Bool {games<Rating.provisionalGames}
    var ratingText:String {"\(rating)"+(provisional ? "（暫定）":"")}
    mutating func rename(_ raw:String)->Bool {let n=OnlineIdentity.cleanName(raw);guard !n.isEmpty else{return false};name=n;return true}
    mutating func begin(_ opponent:OnlineIdentity,round:Int,now:Int64){pending=Pending(opponent:opponent.pubkey,name:opponent.name,rating:opponent.rating,games:opponent.games,round:round,started:now)}
    /// Applies one result (1 win, 0.5 draw, 0 loss) against the opponent's pre-game rating and returns the history entry.
    @discardableResult mutating func record(opponent:String,name opponentName:String,rating opponentRating:Int,score:Double,reason:String,now:Int64)->Game {
        let after=Rating.next(rating,games:games,opponentRating,score:score),result=score>0.75 ? 1:score<0.25 ? -1:0
        let g=Game(opponent:opponent,opponentName:opponentName,reason:reason,opponentRating:opponentRating,before:rating,after:after,result:result,time:now)
        rating=after;games+=1;if result>0{wins+=1}else if result<0{losses+=1}else{draws+=1};history.append(g);if history.count>Self.historyLimit{history.removeFirst(history.count-Self.historyLimit)};pending=nil;updated=now;return g
    }
    mutating func finish(score:Double,reason:String,now:Int64)->Game? {guard let p=pending else{return nil};return record(opponent:p.opponent,name:p.name,rating:p.rating,score:score,reason:reason,now:now)}
    /// A rated game left unfinished by a crash or a killed app is a loss, as on the opponent's device.
    mutating func resolveAbandoned(now:Int64)->Game? {pending==nil ? nil:finish(score:0,reason:"切断（対局を終了せずに退出）",now:now)}
    enum Settle {case unchanged,begun,settled(Game),aborted}
    /// Call after every change of an online game. Starts a rated game once it has begun (persist on .begun) and settles
    /// it once when it ends. A game decided before both players have moved is aborted, unrated. Same rules as Android.
    mutating func settle(rated:Bool,opponent:OnlineIdentity?,game:OnlineGame?,now:Int64,myKey:String)->Settle {
        guard rated,let opponent,let game,opponent.pubkey != myKey else{return .unchanged}
        let current=pending.map{$0.round==game.round && $0.opponent==opponent.pubkey} ?? false
        if game.started && !game.over && !current {begin(opponent,round:game.round,now:now);return .begun}
        // Each round starts with fresh cards; a rematch card carries the opponent's rating after the previous round.
        if current && !game.over,let p=pending,p.rating != opponent.rating || p.games != opponent.games || p.name != opponent.name {pending?.rating=opponent.rating;pending?.games=opponent.games;pending?.name=opponent.name;return .begun}
        guard game.over,current else{return .unchanged}
        if game.movesPlayed<2 && game.result != "時間切れ" {pending=nil;return .aborted}
        let score=game.winner==2 ? 0.5:game.winner==game.mySide ? 1:0;return finish(score:score,reason:game.result,now:now).map{.settled($0)} ?? .unchanged
    }
    func headToHead(_ opponent:String)->(wins:Int,losses:Int,draws:Int) {let h=history.filter{$0.opponent==opponent};return (h.filter{$0.result>0}.count,h.filter{$0.result<0}.count,h.filter{$0.result==0}.count)}
    /// Adopts a published profile after a transfer when it is newer than what this device has.
    mutating func adopt(_ p:Profiles.Profile?){guard let p,p.updated>updated else{return};name=p.name;rating=p.rating;games=p.games;wins=p.wins;losses=p.losses;draws=p.draws;updated=p.updated}
}

/// Public player profiles on the same free relays, wire-compatible with Android: one replaceable NIP-78 event
/// (kind 30078) per account signed with the account key. It restores the rating after a transfer and feeds the
/// ranking. Values are computed by each player's own app, so the ranking is informative, not authoritative.
enum Profiles {
    static let kind=30078,tag="shogikento-profile-v1"
    struct Profile:Equatable,Identifiable {var pubkey,name:String;var rating,games,wins,losses,draws:Int;var updated:Int64;var id:String {pubkey}}
    static var tags:[[String]] {[["d",tag],["t",tag]]}
    static func content(_ a:OnlineAccount)->String {NetJSON.object([("v",.int(1)),("name",.string(a.name)),("rating",.int(Int64(a.rating))),("games",.int(Int64(a.games))),("w",.int(Int64(a.wins))),("l",.int(Int64(a.losses))),("d",.int(Int64(a.draws))),("updated",.int(a.updated))]).text}
    static func parse(_ e:NostrPool.Event)->Profile? {
        guard e.kind==kind,e.tag("d")==tag,let m=try? NetJSON.parse(e.content),m["v"]?.int==1,let name=m["name"]?.string,!name.isEmpty,name==OnlineIdentity.cleanName(name),
              let r=m["rating"]?.int,let g=m["games"]?.int,let w=m["w"]?.int,let l=m["l"]?.int,let d=m["d"]?.int,Rating.valid(Int(r)),g>=0,w>=0,l>=0,d>=0,w+l+d==g else{return nil}
        return Profile(pubkey:e.pubkey,name:name,rating:Int(r),games:Int(g),wins:Int(w),losses:Int(l),draws:Int(d),updated:m["updated"]?.int ?? 0)
    }
    static func authorFilter(_ pubkey:String)->NetJSON {.object([("kinds",.array([.int(Int64(kind))])),("authors",.array([.string(pubkey)])),("#d",.array([.string(tag)]))])}
    static func rankingFilter(limit:Int)->NetJSON {.object([("kinds",.array([.int(Int64(kind))])),("#t",.array([.string(tag)])),("limit",.int(Int64(limit)))])}
    /// Publishes to every relay that opens within the timeout; returns how many relays confirmed it. Blocks; call off the main thread.
    static func publish(_ a:OnlineAccount,key:Secp256k1.Key,relays:[String],factory:@escaping WebSocketFactory,timeout:Double)->Int {
        let pool=NostrPool(urls:relays,key:key,factory:factory,deliver:DispatchQueue(label:"profiles.publish"));defer{pool.close()};pool.start()
        var end=Date().addingTimeInterval(timeout/2);while pool.openCount<relays.count && Date()<end{Thread.sleep(forTimeInterval:0.05)}
        guard pool.openCount>0 else{return 0};pool.publish(kind:kind,tags,content(a));end=Date().addingTimeInterval(timeout/2)
        while pool.acceptedCount<pool.openCount && Date()<end{Thread.sleep(forTimeInterval:0.05)};return pool.acceptedCount
    }
    /// The newest valid profile per account, collected until every open relay has sent its stored events or the timeout. Blocks.
    static func fetch(relays:[String],filter:NetJSON,factory:@escaping WebSocketFactory,timeout:Double)->[String:Profile] {
        let q=DispatchQueue(label:"profiles.fetch");var found=[String:Profile](),done=Set<String>()
        let pool=NostrPool(urls:relays,key:.random(),factory:factory,deliver:q);defer{pool.close()}
        pool.onEvent={e in if let p=parse(e),(found[p.pubkey]?.updated ?? -1)<p.updated{found[p.pubkey]=p}};pool.onEndOfStored={_,relay in done.insert(relay)}
        pool.subscribe("profiles",filter);pool.start();let start=Date(),end=start.addingTimeInterval(timeout)
        while Date()<end {let open=pool.openCount;if open>0 && q.sync(execute:{done.count})>=open && Date().timeIntervalSince(start)>0.8{break};Thread.sleep(forTimeInterval:0.05)}
        return q.sync{found}
    }
    static func ranking(_ all:[String:Profile],minimumGames:Int)->[Profile] {all.values.filter{$0.games>=minimumGames}.sorted{$0.rating != $1.rating ? $0.rating>$1.rating:$0.games != $1.games ? $0.games>$1.games:$0.pubkey<$1.pubkey}}
}
