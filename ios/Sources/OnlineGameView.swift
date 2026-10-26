import SwiftUI

private let gold=Color(red:1,green:0.84,blue:0.48),cream=Color(red:1,green:0.94,blue:0.81),ink=Color(red:0.16,green:0.07,blue:0.03),lacquer=Color(red:0.19,green:0.05,blue:0.06)

/// The online game: a header with the connection, the opponent's card and komadai, the board with the last move
/// marked, the player's komadai and card, the turn pill, and a result card with the rating change at the end.
struct OnlineGameView:View {
    @EnvironmentObject var model:AppModel
    let game:OnlineGame
    @Binding var resign:Bool
    var body:some View {
        VStack(spacing:8){
            OnlineHeader(game:game)
            OnlinePlayerCard(side:-model.viewSide,game:game)
            OnlineKomadai(side:-model.viewSide)
            BoardView().aspectRatio(1226/1283.0,contentMode:.fit)
                .clipShape(RoundedRectangle(cornerRadius:4)).shadow(color:.black.opacity(0.55),radius:10,y:4)
                .overlay{if !game.started{VStack(spacing:10){ProgressView().tint(gold);Text("対局を準備しています…").font(.headline).foregroundStyle(cream)}.padding(22).background(RoundedRectangle(cornerRadius:16).fill(.black.opacity(0.78)))}}
            OnlineKomadai(side:model.viewSide)
            OnlinePlayerCard(side:model.viewSide,game:game)
            OnlineTurnPill(game:game)
            if game.over{OnlineResultCard(game:game)}
            else if game.started{Button{resign=true}label:{Label("投了",systemImage:"flag.fill").font(.subheadline.weight(.semibold)).padding(.horizontal,18).padding(.vertical,8)}
                .foregroundStyle(cream).background(Capsule().fill(Color.red.opacity(0.32))).overlay(Capsule().stroke(Color.red.opacity(0.7))).accessibilityIdentifier("online_resign")}
            if !model.online.notice.isEmpty{Label(model.online.notice,systemImage:"info.circle").font(.caption).foregroundStyle(.orange)}
            if let error=game.error{Label(error,systemImage:"exclamationmark.triangle").font(.caption).foregroundStyle(.orange)}
        }.padding(.horizontal,10).padding(.top,4)
    }
}

/// Connection quality, rated or friendly, round, ply and the player's side.
private struct OnlineHeader:View {
    @EnvironmentObject var model:AppModel
    let game:OnlineGame
    private var dot:Color {model.online.direct ? Color(red:0.43,green:0.88,blue:0.48):game.started ? Color(red:1,green:0.78,blue:0.34):.gray}
    private var route:String {let rtt=model.online.rtt>=0 ? " \(model.online.rtt)ms":"";return model.online.direct ? "P2P直結"+rtt:game.started ? "リレー経由"+rtt:"接続中…"}
    var body:some View {
        HStack(spacing:6){
            HStack(spacing:6){Circle().fill(dot).frame(width:8,height:8).shadow(color:dot,radius:4);Text(route).font(.caption.weight(.semibold)).accessibilityIdentifier("online_route")}
                .padding(.horizontal,10).padding(.vertical,5).background(Capsule().fill(.black.opacity(0.6))).overlay(Capsule().stroke(dot.opacity(0.55)))
            Spacer(minLength:4)
            Chip(text:model.online.rated ? "レート戦":"フレンド戦",filled:model.online.rated)
            Chip(text:"第\(game.round)局",filled:false)
            Chip(text:"あなた"+(game.mySide==1 ? "先手":"後手"),filled:false).accessibilityIdentifier("online_side")
            Text(verbatim:"\(game.movesPlayed)手目").font(.caption.monospacedDigit()).foregroundStyle(cream.opacity(0.85)).accessibilityIdentifier("online_ply")
        }.foregroundStyle(cream)
    }
}
private struct Chip:View {
    let text:String,filled:Bool
    var body:some View {Text(text).font(.caption2.weight(.bold)).padding(.horizontal,7).padding(.vertical,3).foregroundStyle(filled ? ink:cream)
        .background(Capsule().fill(filled ? gold:Color.white.opacity(0.08))).overlay(Capsule().stroke(gold.opacity(filled ? 0:0.45)))}
}

/// A round badge with the name's first letter, coloured from the account key so each player keeps one colour.
private struct Avatar:View {
    let name:String,seed:String
    private var hue:Double {Double(seed.unicodeScalars.reduce(UInt32(7)){($0 &* 31) &+ $1.value}%360)/360}
    var body:some View {
        Text(String(name.first ?? "?")).font(.system(size:17,weight:.heavy)).foregroundStyle(.white).frame(width:38,height:38)
            .background(Circle().fill(LinearGradient(colors:[Color(hue:hue,saturation:0.55,brightness:0.85),Color(hue:hue,saturation:0.75,brightness:0.45)],startPoint:.topLeading,endPoint:.bottomTrailing)))
            .overlay(Circle().stroke(gold.opacity(0.7),lineWidth:1.5)).accessibilityHidden(true)
    }
}

/// Name, rating, side and clock. The side to move glows gold; under 30 seconds the clock turns red.
private struct OnlinePlayerCard:View {
    @EnvironmentObject var model:AppModel
    let side:Int,game:OnlineGame
    private var me:Bool {side==model.viewSide}
    private var name:String {me ? model.account.name:model.online.peer?.name ?? "対戦相手"}
    private var seed:String {me ? model.accountKey.publicHex:model.online.peer?.pubkey ?? "opponent"}
    private var active:Bool {game.started && !game.over && game.turn==side}
    private var millis:Int64 {max(0,game.remaining(side,AppModel.onlineNow))}
    private var low:Bool {game.started && !game.over && millis<30000}
    private var detail:String {
        if me {let a=model.account;return "レート\(a.rating)"+(a.provisional ? "（暫定）":"")+" · \(a.wins)勝\(a.losses)敗\(a.draws)分"}
        guard let p=model.online.peer else{return model.online.rated ? "相手の情報を確認しています…":"フレンド対戦（レート変動なし）"}
        let h=model.account.headToHead(p.pubkey),record=h.wins+h.losses+h.draws>0 ? " · 対戦成績\(h.wins)勝\(h.losses)敗\(h.draws)分":""
        return "レート\(p.rating)"+(p.games<Rating.provisionalGames ? "（暫定）":"")+(model.online.rated ? " · レート戦":" · レート変動なし")+record
    }
    private var clock:String {let s=Int((millis+999)/1000);return String(format:"%02d:%02d",s/60,s%60)}
    var body:some View {
        HStack(spacing:10){
            Avatar(name:name,seed:seed)
            VStack(alignment:.leading,spacing:3){
                HStack(spacing:6){Text(name).font(.system(size:16,weight:.bold)).lineLimit(1).accessibilityIdentifier(me ? "online_me_name":"online_opponent_name")
                    if me{Text("あなた").font(.system(size:10,weight:.bold)).padding(.horizontal,5).padding(.vertical,1).foregroundStyle(ink).background(Capsule().fill(gold))}}
                Text(detail).font(.caption2).foregroundStyle(cream.opacity(0.75)).lineLimit(1).minimumScaleFactor(0.8).accessibilityIdentifier(me ? "online_me":"online_opponent")
            }
            Spacer(minLength:4)
            VStack(alignment:.trailing,spacing:3){
                Text(side==1 ? "▲ 先手":"△ 後手").font(.caption2.weight(.semibold)).foregroundStyle(cream.opacity(0.8))
                Text(verbatim:clock).font(.system(size:21,weight:.bold,design:.monospaced)).foregroundStyle(low ? .white:active ? ink:cream)
                    .padding(.horizontal,10).padding(.vertical,2).background(Capsule().fill(low ? Color.red:active ? gold:Color.white.opacity(0.08))).accessibilityIdentifier("online_clock_\(side)")
            }
        }.foregroundStyle(cream).padding(.horizontal,12).padding(.vertical,8)
            .background(RoundedRectangle(cornerRadius:14).fill(LinearGradient(colors:[Color.black.opacity(0.78),lacquer.opacity(0.9)],startPoint:.leading,endPoint:.trailing)))
            .overlay(RoundedRectangle(cornerRadius:14).stroke(active ? gold:gold.opacity(0.22),lineWidth:active ? 2:1))
            .shadow(color:active ? gold.opacity(0.55):.clear,radius:active ? 9:0)
            .animation(.easeInOut(duration:0.25),value:active)
    }
}

/// Pieces in hand, on the right of each player as on a real komadai: the opponent's at the top left.
private struct OnlineKomadai:View {
    @EnvironmentObject var model:AppModel
    let side:Int
    private var counts:[(Int,Int)] {(1..<8).compactMap{p in let n=model.position.hands[side==1 ? 0:1][p];return n>0 ? (p,n):nil}}
    var body:some View {
        HStack(spacing:4){
            if counts.isEmpty{Text("持ち駒なし").font(.caption2).foregroundStyle(cream.opacity(0.45))}
            ForEach(counts,id:\.0){p,n in
                Button{model.selectHand(p,side:side)}label:{ZStack(alignment:.bottomTrailing){
                    Image(uiImage:AppAssets.piece(p*side)).resizable().scaledToFit().rotationEffect(.degrees(side*model.viewSide<0 ? 180:0))
                    if n>1{Text(verbatim:"\(n)").font(.system(size:11,weight:.heavy)).foregroundStyle(ink).frame(minWidth:15,minHeight:15).background(Circle().fill(gold))}
                }.frame(width:34,height:38).padding(2).background(RoundedRectangle(cornerRadius:6).fill(model.selectedHand==p && model.selectedSide==side ? gold.opacity(0.45):.clear))}
                .buttonStyle(.plain).accessibilityIdentifier("hand_\(side)_\(p)")
            }
        }.frame(maxWidth:.infinity,minHeight:42,alignment:side==model.viewSide ? .trailing:.leading).padding(.horizontal,8).padding(.vertical,2)
            .background(RoundedRectangle(cornerRadius:10).fill(Color.black.opacity(0.42))).overlay(RoundedRectangle(cornerRadius:10).stroke(gold.opacity(0.15)))
    }
}

/// Whose turn it is, check, or the final status.
private struct OnlineTurnPill:View {
    @EnvironmentObject var model:AppModel
    let game:OnlineGame
    private var text:String {let s=game.status(AppModel.onlineNow),quiet=model.online.silenceMillis;return game.started && !game.over && quiet>9000 ? s+"・相手の応答なし \(quiet/1000)秒":s}
    private var check:Bool {game.myTurn && game.inCheck(game.mySide)}
    var body:some View {
        HStack(spacing:6){
            if game.myTurn{Image(systemName:check ? "exclamationmark.triangle.fill":"hand.point.up.left.fill")}else if !game.over && game.started{ProgressView().controlSize(.mini).tint(cream)}
            Text(text).font(.subheadline.weight(.bold)).accessibilityIdentifier("online_status")
        }.foregroundStyle(game.myTurn && !check ? ink:cream).padding(.horizontal,16).padding(.vertical,7)
            .background(Capsule().fill(check ? Color.red:game.myTurn ? gold:Color.black.opacity(0.6)))
    }
}

/// Win, loss or draw, the reason, the rating change and what to do next.
private struct OnlineResultCard:View {
    @EnvironmentObject var model:AppModel
    let game:OnlineGame
    private var title:String {game.winner==2 ? "引き分け":game.winner==game.mySide ? "勝ち":"負け"}
    private var titleColors:[Color] {game.winner==game.mySide ? [Color(red:1,green:0.95,blue:0.7),gold,Color(red:0.78,green:0.5,blue:0.14)]:[cream,Color(red:0.7,green:0.75,blue:0.85)]}
    var body:some View {
        VStack(spacing:8){
            Text(title).font(.system(size:38,weight:.heavy,design:.serif)).foregroundStyle(LinearGradient(colors:titleColors,startPoint:.top,endPoint:.bottom)).shadow(color:.black,radius:2,y:2)
            Text(game.result).font(.subheadline).foregroundStyle(cream.opacity(0.85))
            if let r=model.online.lastResult{
                let d=r.after-r.before,signed=(d>=0 ? "+":"")+"\(d)",spoken:String="レート \(r.before) → \(r.after)（\(signed)）"
                HStack(spacing:10){
                    Text(verbatim:"\(r.before)").font(.title3.monospacedDigit()).foregroundStyle(cream.opacity(0.65))
                    Image(systemName:"arrow.right").foregroundStyle(cream.opacity(0.65))
                    Text(verbatim:"\(r.after)").font(.system(size:30,weight:.bold,design:.rounded).monospacedDigit()).foregroundStyle(gold)
                    Text(verbatim:signed).font(.headline.monospacedDigit()).foregroundStyle(.white).padding(.horizontal,9).padding(.vertical,3).background(Capsule().fill(d>=0 ? Color.green.opacity(0.8):Color.red.opacity(0.8)))
                }.accessibilityElement(children:.ignore).accessibilityLabel(spoken).accessibilityIdentifier("online_rating_change")
            }else if !model.online.rated{Text("フレンド対戦・レート変動なし").font(.caption).foregroundStyle(cream.opacity(0.7))}
            if game.rematchMine{Label("再戦を申し込みました。相手の返事を待っています",systemImage:"hourglass").font(.caption).foregroundStyle(cream)}
            else if game.rematchPeer{Label("相手が再戦を希望しています",systemImage:"arrow.triangle.2.circlepath").font(.caption.weight(.bold)).foregroundStyle(gold)}
            HStack(spacing:12){
                if !game.peerLeft && !game.rematchMine{Button{model.onlineRematch()}label:{Label("再戦する",systemImage:"arrow.triangle.2.circlepath").font(.headline).frame(maxWidth:.infinity).padding(.vertical,10)}
                    .foregroundStyle(ink).background(Capsule().fill(LinearGradient(colors:[Color(red:1,green:0.93,blue:0.66),gold],startPoint:.top,endPoint:.bottom))).accessibilityIdentifier("online_rematch")}
                Button{model.leaveOnline(all:false)}label:{Text("ロビーへ").font(.headline).frame(maxWidth:.infinity).padding(.vertical,10)}
                    .foregroundStyle(cream).background(Capsule().fill(Color.white.opacity(0.1))).overlay(Capsule().stroke(gold.opacity(0.5))).accessibilityIdentifier("online_lobby")
            }.buttonStyle(.plain)
        }.padding(18).frame(maxWidth:.infinity)
            .background(RoundedRectangle(cornerRadius:20).fill(LinearGradient(colors:[lacquer.opacity(0.96),Color.black.opacity(0.9)],startPoint:.top,endPoint:.bottom)))
            .overlay(RoundedRectangle(cornerRadius:20).stroke(LinearGradient(colors:[gold,gold.opacity(0.2)],startPoint:.top,endPoint:.bottom),lineWidth:1.5))
            .shadow(color:.black.opacity(0.5),radius:12,y:6)
    }
}
