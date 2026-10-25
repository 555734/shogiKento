import Foundation
import SwiftUI

/// The bundled engine's rules for online games; Android peers check the same moves with their own rules.
struct EngineRules:OnlineRules {
    func legal(_ sfen:String)->[String]? {guard let p=try? Position(sfen),let moves=try? Rules.legal(p) else{return nil};return moves.map(\.usi)}
    func apply(_ sfen:String,_ usi:String)->String? {guard let p=try? Position(sfen),let next=try? Rules.apply(p,Move(usi:usi)) else{return nil};return next.sfen}
    func inCheck(_ sfen:String)->Bool {(try? Position(sfen)).map{Rules.checked($0)} ?? false}
}

struct OnlineState {
    var game:OnlineGame?
    var status="",network="",notice="",route="",roomCode:String?
    var relaysOpen=0,relaysTotal=0,rtt = -1,seekMode = -1
    var direct=false,silenceMillis:Int64=0
    /// The opponent's verified account card, whether this session is rated (random matches), the round whose card
    /// was sent, and the last settled rated result for the result line.
    var peer:OnlineIdentity?,rated=false,cardRound=0,lastResult:OnlineAccount.Game?
}

enum OnlineEvent {case status(String),relays(Int,Int),network(String),matched(Int),message(String),path(Bool,Int,String),peerLeft,failed(String)}

/// Matchmaking callbacks arrive on the network queue. They are queued in arrival order and drained on the main
/// actor, so moves are applied in the order the peer sent them; events of a replaced session are dropped.
final class OnlineEvents:NetplayListener,@unchecked Sendable {
    private let lock=NSLock();private var queue=[OnlineEvent]();private weak var model:AppModel?;private weak var net:Netplay?
    init(model:AppModel,net:Netplay){self.model=model;self.net=net}
    private func post(_ e:OnlineEvent){lock.lock();queue.append(e);lock.unlock();Task{@MainActor [weak self] in self?.drain()}}
    @MainActor private func drain(){lock.lock();let items=queue;queue=[];lock.unlock();guard let model,let net,model.net===net else{return};for e in items{model.handle(e)}}
    func status(_ text:String){post(.status(text))}
    func relays(open:Int,total:Int){post(.relays(open,total))}
    func network(_ text:String){post(.network(text))}
    func matched(localSide:Int){post(.matched(localSide))}
    func message(_ text:String){post(.message(text))}
    func path(direct:Bool,rtt:Int,route:String){post(.path(direct,rtt,route))}
    func peerLeft(){post(.peerLeft)}
    func failed(_ reason:String){post(.failed(reason))}
}

extension AppModel {
    nonisolated static var onlineNow:Int64 {Int64(ProcessInfo.processInfo.systemUptime*1000)}
    var onlineInProgress:Bool {online.game.map{$0.started && !$0.over} ?? false}
    func ensureNetplay(){
        guard net==nil else{return}
        #if DEBUG
        // UI and interop tests point the app at a local relay and a private lobby.
        let env=ProcessInfo.processInfo.environment
        if let relay=env["ONLINE_RELAY"],!relay.isEmpty{Netplay.relayOverride=[relay];Netplay.stunOverride=[];UDPEndpoint.allowLoopback=true}
        if let lobby=env["ONLINE_LOBBY"],!lobby.isEmpty{Netplay.lobbyTag=lobby}
        #endif
        online.notice="";online.status="";online.network="";online.relaysOpen=0;online.relaysTotal=Netplay.relays.count
        do{let n=try Netplay(factory:URLSessionWebSocket.factory);let events=OnlineEvents(model:self,net:n);n.listener=events;onlineEvents=events;net=n;n.start()}
        catch{online.notice="通信を開始できません："+error.localizedDescription}
    }
    func handle(_ event:OnlineEvent){
        let now=Self.onlineNow
        switch event {
        case .status(let text):online.status=text
        case .relays(let open,let total):online.relaysOpen=open;online.relaysTotal=total
        case .network(let text):online.network=text
        case .matched(let side):var g=OnlineGame(mySide:side,rules:EngineRules());g.hello(now);online.peer=nil;online.cardRound=0;online.lastResult=nil;online.rated=net?.ratedSession ?? false
            online.game=g;online.seekMode = -1;online.notice="";online.direct=false;online.rtt = -1;online.route="";flushOnline();showOnlinePosition()
        case .message(let text) where text.hasPrefix("ID "):online.peer=net?.sessionId.flatMap{OnlineIdentity.parse(text,sessionId:$0)};syncAccount()
        case .message(let text):guard online.game != nil else{return};online.game!.rttMillis=online.rtt;let moved=online.game!.receive(text,now);flushOnline();if moved{showOnlinePosition();if online.game!.lastMove != nil{feedback(human:false)}}
        case .path(let direct,let rtt,let route):online.direct=direct;online.rtt=rtt;online.route=route
        case .peerLeft:online.game?.markPeerLeft();online.notice="相手が退出しました";clearSelection()
        case .failed(let reason):online.notice=reason
        }
    }
    /// Sends what the game queued; the network sends it off the main thread, in order.
    func flushOnline(){if let out=online.game?.outbox,!out.isEmpty{online.game!.outbox=[];for m in out{net?.send(m)}};syncAccount()}
    /// After every game change: settle rated games (persisting each step) and send a fresh account card each round.
    func syncAccount(){
        guard let game=online.game else{return}
        switch account.settle(rated:online.rated,opponent:online.peer,game:game,now:Int64(Date().timeIntervalSince1970),myKey:accountKey.publicHex){
        case .begun:accountStore.saveAccount(account)
        case .settled(let entry):online.lastResult=entry;accountStore.saveAccount(account);publishProfile()
        case .aborted:accountStore.saveAccount(account);online.notice="対局不成立のためレートは変動しません"
        case .unchanged:break
        }
        if game.round != online.cardRound,let net,let sid=net.sessionId{online.cardRound=game.round;net.send(OnlineIdentity.message(accountKey,sessionId:sid,name:account.name,rating:account.rating,games:account.games))}
    }
    /// Publishes the profile (when allowed) in the background; relays that refuse are simply skipped.
    func publishProfile(){guard account.publish else{return};let a=account,key=accountKey,relays=Netplay.relays;DispatchQueue.global(qos:.utility).async{_=Profiles.publish(a,key:key,relays:relays,factory:URLSessionWebSocket.factory,timeout:8)}}
    func renameAccount(_ text:String)->Bool {guard account.rename(text) else{return false};account.updated=Int64(Date().timeIntervalSince1970);accountStore.saveAccount(account);publishProfile();return true}
    func setProfilePublic(_ value:Bool){account.publish=value;accountStore.saveAccount(account);if value{publishProfile()}}
    var transferCode:String {Nip19.encode("nsec",accountSecret)}
    /// Replaces this device's account with the one in the transfer code and restores its published rating.
    func restoreAccount(_ code:String)->Bool {
        guard online.game == nil,let secret=Nip19.decode("nsec",code),let key=Secp256k1.Key(secret:secret),accountStore.saveSecret(secret) else{return false}
        accountSecret=secret;accountKey=key;account=OnlineAccount(name:OnlineAccount.defaultName(key));accountStore.saveAccount(account);rankingStatus="引き継いだアカウントのプロフィールを探しています…"
        let relays=Netplay.relays;DispatchQueue.global(qos:.userInitiated).async{let found=Profiles.fetch(relays:relays,filter:Profiles.authorFilter(key.publicHex),factory:URLSessionWebSocket.factory,timeout:8)[key.publicHex]
            Task{@MainActor in guard self.accountKey.publicHex==key.publicHex else{return};self.account.adopt(found);self.accountStore.saveAccount(self.account);self.rankingStatus=found==nil ? "公開プロフィールが見つかりません。レートは1500から始まります":"レート\(self.account.rating)を復元しました"}}
        return true
    }
    func loadRanking(){rankingStatus="ランキングを読み込んでいます…";let relays=Netplay.relays
        DispatchQueue.global(qos:.userInitiated).async{let top=Profiles.ranking(Profiles.fetch(relays:relays,filter:Profiles.rankingFilter(limit:300),factory:URLSessionWebSocket.factory,timeout:8),minimumGames:1)
            Task{@MainActor in self.ranking=Array(top.prefix(100));self.rankingStatus=top.isEmpty ? "公開プロフィールが見つかりませんでした":"公開プロフィール\(top.count)件（各端末が計算した値）"}}}
    func showOnlinePosition(){position=online.game.flatMap{try? Position($0.sfen)} ?? (try! Position());clearSelection()}
    func onlineTick(){guard online.game != nil else{return};online.game!.rttMillis=online.rtt;let was=online.game!.over;online.game!.tick(Self.onlineNow);flushOnline();if !was && online.game!.over{clearSelection()}
        online.silenceMillis=net?.link?.silenceMillis ?? 0}
    func onlineCommit(_ move:Move){guard online.game != nil else{return};if online.game!.localMove(move.usi,Self.onlineNow){flushOnline();showOnlinePosition();feedback(human:true)}else{clearSelection()}}
    func startSeek(_ mode:Int,code:String?){
        guard let net else{online.notice="通信を準備しています…";ensureNetplay();return}
        net.rating=account.rating;net.games=account.games;online.seekMode=mode;online.roomCode=code;online.notice="";online.status="";net.seek(mode,code:code)
    }
    func hostRoom(){startSeek(Netplay.host,code:Netplay.newRoomCode())}
    /// Accepts full-width digits too; the room tag is always derived from the ASCII code, as on Android.
    func joinRoom(_ text:String){let trimmed=text.filter{!$0.isWhitespace},digits=trimmed.compactMap(\.wholeNumberValue)
        guard digits.count==6,digits.count==trimmed.count else{online.notice="ルームコードは6桁の数字です";return};startSeek(Netplay.guest,code:digits.map(String.init).joined())}
    func cancelSeek(){net?.stopSeeking();online.seekMode = -1;online.roomCode=nil;online.status=""}
    func onlineResign(){guard online.game != nil else{return};online.game!.resign();flushOnline();clearSelection()}
    func onlineRematch(){guard online.game != nil else{return};online.game!.requestRematch(Self.onlineNow);flushOnline();showOnlinePosition()}
    /// Leaving resigns a running game. With all=true the network closes too (off the main thread; it waits briefly for the goodbye).
    func leaveOnline(all:Bool){
        if online.game != nil{online.game!.resign();flushOnline()}
        online.game=nil;online.seekMode = -1;online.roomCode=nil;online.direct=false;online.rtt = -1;online.route="";online.notice=""
        if all{let n=net;net=nil;onlineEvents=nil;online.status="";online.relaysOpen=0;if let n{DispatchQueue.global(qos:.utility).async{n.close()}}}else{net?.stopSeeking()}
        if screen == .online{showOnlinePosition()}
    }
}

private let gold=Color(red:1,green:0.86,blue:0.59),cream=Color(red:1,green:0.94,blue:0.81),burgundy=Color(red:0.25,green:0.04,blue:0.06)
struct OnlineScreen:View {
    @EnvironmentObject var model:AppModel
    @State private var joining=false
    @State private var code=""
    @State private var resign=false
    @State private var accountSheet=false
    var body:some View {
        GeometryReader {viewport in ScrollView {VStack(spacing:14){
            if let game=model.online.game {OnlineGameView(game:game,resign:$resign)}else{lobby}
            TestAdSlot(placement:"match").id("online").padding(.horizontal,12)
        }.frame(minHeight:viewport.size.height,alignment:.top).frame(maxWidth:600).frame(maxWidth:.infinity).padding(.bottom,15)}}
        .background(Image(uiImage:AppAssets.image("background")).resizable().scaledToFill().ignoresSafeArea())
        .alert("ルームに参加",isPresented:$joining){TextField("6桁のコード",text:$code).keyboardType(.numberPad).accessibilityIdentifier("room_code_field");Button("キャンセル",role:.cancel){};Button("参加"){model.joinRoom(code)}.accessibilityIdentifier("room_join")}message:{Text("友だちのルームコードを入力してください")}
        .alert("投了しますか？",isPresented:$resign){Button("キャンセル",role:.cancel){};Button("投了",role:.destructive){model.onlineResign()}}
        .sheet(isPresented:$accountSheet){AccountView()}
    }
    private var lobby:some View {
        VStack(spacing:12){
            Text("3分切れ負け・無料のオンライン対戦").font(.system(size:20,weight:.bold,design:.serif)).foregroundStyle(gold).padding(.top,12)
            Button{accountSheet=true}label:{HStack{VStack(alignment:.leading,spacing:4){Text(model.account.name).font(.headline);Text(verbatim:"レート \(model.account.ratingText) · \(model.account.wins)勝\(model.account.losses)敗\(model.account.draws)分").font(.caption)};Spacer();Label("アカウント",systemImage:"person.crop.circle").font(.subheadline)}
                .padding(12).foregroundStyle(cream).background(Color.black.opacity(0.78)).clipShape(RoundedRectangle(cornerRadius:10))}.buttonStyle(.plain).accessibilityIdentifier("online_account")
            ForEach(Array([("ランダム対戦","レート戦・近いレートの相手と対局",Netplay.quick,"online_quick"),("ルームを作る","6桁のコードで友だちと対局（レート変動なし）",Netplay.host,"online_host"),("ルームに参加","友だちのルームコードを入力（レート変動なし）",Netplay.guest,"online_join")].enumerated()),id:\.offset){_,item in
                let active=model.online.seekMode==item.2
                Button{if model.online.seekMode<0{switch item.2{case Netplay.quick:model.startSeek(Netplay.quick,code:nil);case Netplay.host:model.hostRoom();default:code="";joining=true}}}label:{VStack(spacing:6){Text(item.0+(active ? "・待機中":"")).font(.system(size:22,weight:.bold,design:.serif)).foregroundStyle(active ? gold:cream);Text(item.1).font(.system(size:13,design:.serif))}.frame(maxWidth:.infinity,minHeight:78).background(Color.black.opacity(0.8))}
                    .buttonStyle(HomeCard()).disabled(model.online.seekMode>=0 && !active).accessibilityIdentifier(item.3)
            }
            VStack(alignment:.leading,spacing:6){
                Text(model.net==nil ? "未接続":"公開リレー接続 \(model.online.relaysOpen)/\(model.online.relaysTotal)"+(model.online.relaysOpen==0 ? "（接続中…）":"")).font(.caption).foregroundStyle(model.online.relaysOpen>0 ? .green:.yellow).accessibilityIdentifier("online_relays")
                Text(model.online.network.isEmpty ? "UDP候補を収集しています…":model.online.network).font(.caption2).foregroundStyle(cream.opacity(0.85))
                if model.online.seekMode==Netplay.host,let room=model.online.roomCode{Text(verbatim:"ルームコード \(room.prefix(3)) \(room.suffix(3))").font(.system(size:26,weight:.bold,design:.monospaced)).foregroundStyle(gold).frame(maxWidth:.infinity).accessibilityIdentifier("online_room_code")}
                Text(model.online.seekMode<0 ? "対局方法を選んでください":model.online.status.isEmpty ? "開始しています…":model.online.status).font(.subheadline).frame(maxWidth:.infinity).accessibilityIdentifier("online_lobby_status")
                if model.online.seekMode==Netplay.guest,let room=model.online.roomCode{Text(verbatim:"ルーム \(room)").font(.caption).frame(maxWidth:.infinity)}
                if !model.online.notice.isEmpty{Text(model.online.notice).font(.caption).foregroundStyle(.orange).frame(maxWidth:.infinity).accessibilityIdentifier("online_notice")}
                if model.online.seekMode>=0{Button("キャンセル"){model.cancelSeek()}.buttonStyle(.bordered).frame(maxWidth:.infinity).accessibilityIdentifier("online_cancel")}
            }.padding(14).foregroundStyle(cream).background(.black.opacity(0.78)).clipShape(RoundedRectangle(cornerRadius:10))
            Text("サーバーを使わず、公開Nostrリレーで相手を探してP2P（UDP）で直接つながります。直結できない回線ではリレー経由で対局を続けます。Android版とも対戦できます。対戦相手にはあなたのIPアドレスが伝わります。対局中はアプリを離れても時計は止まりません。").font(.caption2).foregroundStyle(cream.opacity(0.8)).padding(.horizontal,6)
        }.padding(.horizontal,16)
    }
}
struct OnlineGameView:View {
    @EnvironmentObject var model:AppModel
    let game:OnlineGame
    @Binding var resign:Bool
    var body:some View {
        VStack(spacing:8){
            HStack {Text(model.online.direct ? "P2P直結"+(model.online.rtt>=0 ? " \(model.online.rtt)ms":""):game.started ? "リレー経由"+(model.online.rtt>=0 ? " \(model.online.rtt)ms":""):"接続中…").foregroundStyle(model.online.direct ? .green:.yellow).accessibilityIdentifier("online_route");Spacer();Text(verbatim:"第\(game.round)局 · あなた\(game.mySide==1 ? "先手":"後手")").accessibilityIdentifier("online_side")}
                .font(.caption).foregroundStyle(cream).padding(.horizontal,10).frame(height:30).background(.black.opacity(0.72))
            Text(opponentText).font(.caption).foregroundStyle(cream).frame(maxWidth:.infinity,alignment:.leading).padding(.horizontal,10).accessibilityIdentifier("online_opponent")
            HandView(side:-model.viewSide)
            BoardView().aspectRatio(1226/1283.0,contentMode:.fit)
            HandView(side:model.viewSide)
            Text(statusText).font(game.over ? .headline:.body).foregroundStyle(game.over ? gold:cream).multilineTextAlignment(.center).accessibilityIdentifier("online_status")
            Text(verbatim:"\(game.movesPlayed)手目").foregroundStyle(cream).accessibilityIdentifier("online_ply")
            if game.over,let r=model.online.lastResult{Text(verbatim:"レート \(r.before) → \(r.after)（\(r.after>=r.before ? "+":"")\(r.after-r.before)）").font(.headline).foregroundStyle(r.after>=r.before ? .green:.red).accessibilityIdentifier("online_rating_change")}
            if !model.online.notice.isEmpty{Text(model.online.notice).font(.caption).foregroundStyle(.orange)}
            if let error=game.error{Text(error).font(.caption).foregroundStyle(.orange)}
            HStack {
                if game.started && !game.over{Button("投了"){resign=true}.accessibilityIdentifier("online_resign")}
                if game.over && !game.peerLeft && !game.rematchMine{Button("再戦する"){model.onlineRematch()}.accessibilityIdentifier("online_rematch")}
                if game.over{Button("ロビーへ"){model.leaveOnline(all:false)}.accessibilityIdentifier("online_lobby")}
            }.buttonStyle(.bordered).padding(.horizontal)
        }
    }
    private var opponentText:String {
        guard let p=model.online.peer else{return model.online.rated ? "相手の情報を確認しています…":"フレンド対戦（レート変動なし）"}
        let h=model.account.headToHead(p.pubkey),record=h.wins+h.losses+h.draws>0 ? " · 対戦成績 \(h.wins)勝\(h.losses)敗\(h.draws)分":""
        return "相手：\(p.name)（レート\(p.rating)\(p.games<Rating.provisionalGames ? "・暫定":"")）"+(model.online.rated ? " · レート戦":" · レート変動なし")+record
    }
    private var statusText:String {let s=game.status(AppModel.onlineNow);let quiet=model.online.silenceMillis;return game.started && !game.over && quiet>9000 ? s+"・相手の応答なし \(quiet/1000)秒":s}
}
