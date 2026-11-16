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
    /// Round whose start sound played and whether its end sound played.
    var flowRound=0,flowEnded=false
    /// The chosen time control (saved) and whether the room page is shown instead of the lobby.
    var timeControl=OnlineGame.validTimeControl(UserDefaults.standard.string(forKey:"online_tc")) ? UserDefaults.standard.string(forKey:"online_tc")!:"3m",roomPage=false
    /// The opponent's verified account card, whether this session is rated (random matches), the round whose card
    /// was sent, and the last settled rated result for the result line.
    var peer:OnlineIdentity?,rated=false,cardRound=0,lastResult:OnlineAccount.Game?
    /// The bot opponent of the current game (nil against a person) and the random-match seek its timer belongs to.
    var bot:OnlineBot?,seekToken=0
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
        if let wait=env["ONLINE_BOT_WAIT_MS"].flatMap({Int64($0)}){OnlineBot.waitMillis=wait}
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
        case .matched where online.bot != nil:net?.stopSeeking()
        case .message where online.bot != nil,.peerLeft where online.bot != nil:break
        case .matched(let side):var g=OnlineGame(mySide:side,rules:EngineRules(),time:OnlineGame.millis(net?.matchedTimeControl));g.hello(now);online.peer=nil;online.cardRound=0;online.lastResult=nil;online.rated=net?.ratedSession ?? false
            online.flowRound=0;online.flowEnded=false;online.game=g;online.seekMode = -1;online.notice="";online.direct=false;online.rtt = -1;online.route="";flushOnline();showOnlinePosition()
        case .message(let text) where text.hasPrefix("ID "):online.peer=net?.sessionId.flatMap{OnlineIdentity.parse(text,sessionId:$0)};syncAccount()
        case .message(let text):guard online.game != nil else{return};online.game!.rttMillis=online.rtt;let before=position;let moved=online.game!.receive(text,now);flushOnline();if moved{showOnlinePosition();if online.game!.lastMove != nil{beginMoveEffect(from:before);feedback(human:false,kind:SoundKind.forMove(before,position))}};observeOnlineFlow()
        case .path(let direct,let rtt,let route):online.direct=direct;online.rtt=rtt;online.route=route
        case .peerLeft:online.game?.markPeerLeft();online.notice="相手が退出しました";clearSelection()
        case .failed(let reason):online.notice=reason
        }
    }
    /// Sends what the game queued; the network sends it off the main thread, in order.
    func flushOnline(){if let out=online.game?.outbox,!out.isEmpty{online.game!.outbox=[];for m in out{if let bot=online.bot{bot.fromPlayer(m)}else{net?.send(m)}}};syncAccount()}
    /// No person found within OnlineBot.waitMillis: a rated game against the clearly labelled, weak bot.
    func startBotGame(){
        net?.stopSeeking();online.seekMode = -1;online.seekToken+=1;online.roomCode=nil;online.status=""
        let bot=OnlineBot(game:{[weak self] in self?.online.game},deliver:{[weak self] m in self?.botMessage(m)})
        var g=OnlineGame(mySide:Bool.random() ? 1:-1,rules:EngineRules(),time:OnlineGame.millis(online.timeControl))
        online.bot=bot;online.peer=OnlineBot.identity;online.cardRound=0;online.lastResult=nil;online.rated=true;online.notice="";online.direct=false;online.rtt = -1;online.route=""
        online.flowRound=0;online.flowEnded=false;g.hello(Self.onlineNow);online.game=g;flushOnline();showOnlinePosition()
    }
    /// A bot message, applied exactly like one from a remote player.
    func botMessage(_ text:String){guard online.bot != nil,online.game != nil else{return};let before=position;let moved=online.game!.receive(text,Self.onlineNow);flushOnline()
        if moved{showOnlinePosition();if online.game!.lastMove != nil{beginMoveEffect(from:before);feedback(human:false,kind:SoundKind.forMove(before,position))}};observeOnlineFlow()}
    func endBot(){online.seekToken+=1;online.bot?.close();online.bot=nil}
    /// After every game change: settle rated games (persisting each step) and send a fresh account card each round.
    func syncAccount(){
        guard let game=online.game else{return}
        switch account.settle(rated:online.rated,opponent:online.peer,game:game,now:Int64(Date().timeIntervalSince1970),myKey:accountKey.publicHex){
        case .begun:accountStore.saveAccount(account)
        case .settled(let entry):online.lastResult=entry;accountStore.saveAccount(account);publishProfile()
        case .aborted:accountStore.saveAccount(account);online.notice="対局不成立のためレートは変動しません"
        case .unchanged:break
        }
        if online.bot==nil,game.round != online.cardRound,let net,let sid=net.sessionId{online.cardRound=game.round;net.send(OnlineIdentity.message(accountKey,sessionId:sid,name:account.name,rating:account.rating,games:account.games))}
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
    /// The squares of the last move in an online game (source may be nil for a drop), for the board highlight.
    var lastMoveSquares:(from:Int?,to:Int)? {if screen == .match{return matchLastMove};guard screen == .online,let usi=online.game?.lastMove,usi.count>=4 else{return nil};let m=Move(usi:usi);let to=m.destination;guard (0..<81).contains(to) else{return nil};return (m.source,to)}
    /// Start and end sounds once per online round.
    func observeOnlineFlow(){guard let g=online.game else{return}
        if g.started && online.flowRound != g.round{online.flowRound=g.round;online.flowEnded=g.over;if !g.over && g.movesPlayed==0{playSound(.start);banner=GameBanner(kind:.start("あなた："+(g.mySide==1 ? "先手":"後手")+"　相手："+(g.mySide==1 ? "後手":"先手")),reduced:reducedEffects)}}
        if g.over && !online.flowEnded{online.flowEnded=true;playSound(.end);banner=GameBanner(kind:.end(outcome:g.winner==2 ? 0:g.winner==g.mySide ? 1:-1,detail:g.result),reduced:reducedEffects)}}
    func showOnlinePosition(){position=online.game.flatMap{try? Position($0.sfen)} ?? (try! Position());clearSelection()}
    func onlineTick(){guard online.game != nil else{return};online.game!.rttMillis=online.rtt;let was=online.game!.over;online.game!.tick(Self.onlineNow);flushOnline();observeOnlineFlow();if !was && online.game!.over{clearSelection()}
        online.silenceMillis=online.bot != nil ? 0:net?.link?.silenceMillis ?? 0}
    func onlineCommit(_ move:Move){guard online.game != nil else{return};let before=position;if online.game!.localMove(move.usi,Self.onlineNow){flushOnline();showOnlinePosition();beginMoveEffect(from:before);feedback(human:true,kind:SoundKind.forMove(before,position));observeOnlineFlow()}else{clearSelection()}}
    func startSeek(_ mode:Int,code:String?){
        guard let net else{online.notice="通信を準備しています…";ensureNetplay();return}
        net.rating=account.rating;net.games=account.games;online.seekMode=mode;online.roomCode=code;online.notice="";online.status="";net.seek(mode,code:code,timeControl:online.timeControl)
        online.seekToken+=1;let token=online.seekToken
        if mode==Netplay.quick{let wait=OnlineBot.waitMillis;Task{@MainActor [weak self] in try? await Task.sleep(nanoseconds:UInt64(max(0,wait))*1_000_000)
            guard let self,self.online.seekToken==token,self.online.seekMode==Netplay.quick,self.online.game==nil,self.net===net else{return};self.startBotGame()}}
    }
    func chooseTimeControl(_ tc:String){guard online.seekMode<0,OnlineGame.validTimeControl(tc) else{return};online.timeControl=tc;UserDefaults.standard.set(tc,forKey:"online_tc")}
    func openRoomPage(_ open:Bool){guard open != online.roomPage else{return};if online.seekMode>=0{cancelSeek()};online.roomPage=open;online.notice=""}
    func hostRoom(){startSeek(Netplay.host,code:Netplay.newRoomCode())}
    /// Accepts full-width digits too; the room tag is always derived from the ASCII code, as on Android.
    func joinRoom(_ text:String){let trimmed=text.filter{!$0.isWhitespace},digits=trimmed.compactMap(\.wholeNumberValue)
        guard digits.count==6,digits.count==trimmed.count else{online.notice="ルームコードは6桁の数字です";return};startSeek(Netplay.guest,code:digits.map(String.init).joined())}
    func cancelSeek(){online.seekToken+=1;net?.stopSeeking();online.seekMode = -1;online.roomCode=nil;online.status=""}
    func onlineResign(){guard online.game != nil else{return};online.game!.resign();flushOnline();clearSelection()}
    func onlineRematch(){guard online.game != nil else{return};online.game!.requestRematch(Self.onlineNow);flushOnline();showOnlinePosition()}
    /// Leaving resigns a running game. With all=true the network closes too (off the main thread; it waits briefly for the goodbye).
    func leaveOnline(all:Bool){
        if online.game != nil{online.game!.resign();flushOnline()}
        endBot();online.game=nil;online.seekMode = -1;online.roomCode=nil;online.direct=false;online.rtt = -1;online.route="";online.notice=""
        if all{online.roomPage=false;let n=net;net=nil;onlineEvents=nil;online.status="";online.relaysOpen=0;if let n{DispatchQueue.global(qos:.utility).async{n.close()}}}else{net?.stopSeeking()}
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
        GeometryReader {viewport in ScrollViewReader {proxy in ScrollView {VStack(spacing:14){
            if let game=model.online.game {OnlineGameView(game:game,resign:$resign)}else{lobby}
            TestAdSlot(placement:"match").id("online").padding(.horizontal,12)
        }.frame(minHeight:viewport.size.height,alignment:.top).frame(maxWidth:600).frame(maxWidth:.infinity).padding(.bottom,15)}
            // The result card sits below the board; bring it into view when the game ends.
            .onChange(of:model.online.game?.over ?? false){over in if over{withAnimation(.easeOut(duration:0.35)){proxy.scrollTo("online_result",anchor:.bottom)}}}}}
        .background(Image(uiImage:AppAssets.image("background")).resizable().scaledToFill().overlay(ProgressTint(moves:model.online.game?.movesPlayed ?? -1)).ignoresSafeArea())
        .alert("ルームに参加",isPresented:$joining){TextField("6桁のコード",text:$code).keyboardType(.numberPad).accessibilityIdentifier("room_code_field");Button("キャンセル",role:.cancel){};Button("参加"){model.joinRoom(code)}.accessibilityIdentifier("room_join")}message:{Text("友だちのルームコードを入力してください")}
        .alert("投了しますか？",isPresented:$resign){Button("キャンセル",role:.cancel){};Button("投了",role:.destructive){model.onlineResign()}}
        .sheet(isPresented:$accountSheet){AccountView()}
    }
    private var timePicker:some View {
        HStack(spacing:8){ForEach(OnlineGame.timeControls,id:\.self){tc in let on=model.online.timeControl==tc
            Button{model.chooseTimeControl(tc)}label:{Text(OnlineGame.label(tc)).font(AppFonts.ui(15,bold:true)).foregroundStyle(on ? burgundy:cream).frame(maxWidth:.infinity,minHeight:38)
                .background(Capsule().fill(on ? gold:Color.black.opacity(0.6))).overlay(Capsule().stroke(gold.opacity(on ? 1:0.5),lineWidth:1.2))}
                .buttonStyle(.plain).disabled(model.online.seekMode>=0).accessibilityIdentifier("online_tc_\(tc)")}}
    }
    private func seekButton(_ title:String,_ caption:String,_ mode:Int,_ id:String,_ action:@escaping ()->Void)->some View {
        let active=model.online.seekMode==mode
        return Button{if model.online.seekMode<0{action()}}label:{VStack(spacing:6){Text(title+(active ? "・待機中":"")).font(AppFonts.mincho(22)).foregroundStyle(active ? gold:cream);Text(caption).font(AppFonts.mincho(13))}.frame(maxWidth:.infinity,minHeight:78).background(Color.black.opacity(0.8))}
            .buttonStyle(HomeCard()).disabled(model.online.seekMode>=0 && !active).accessibilityIdentifier(id)
    }
    private var statusPanel:some View {
        VStack(alignment:.leading,spacing:6){
            Text(model.net==nil ? "未接続":"公開リレー接続 \(model.online.relaysOpen)/\(model.online.relaysTotal)"+(model.online.relaysOpen==0 ? "（接続中…）":"")).font(AppFonts.style(12,.caption)).foregroundStyle(model.online.relaysOpen>0 ? .green:.yellow).accessibilityIdentifier("online_relays")
            Text(model.online.network.isEmpty ? "UDP候補を収集しています…":model.online.network).font(AppFonts.style(11,.caption2)).foregroundStyle(cream.opacity(0.85))
            if model.online.seekMode==Netplay.host,let room=model.online.roomCode{Text(verbatim:"ルームコード \(room.prefix(3)) \(room.suffix(3))（\(OnlineGame.label(model.online.timeControl))）").font(AppFonts.ui(22,bold:true).monospacedDigit()).foregroundStyle(gold).frame(maxWidth:.infinity).accessibilityIdentifier("online_room_code")}
            Text(model.online.seekMode<0 ? (model.online.roomPage ? "ルームを作るか、コードで参加してください":"持ち時間を選んでランダム対戦"):model.online.status.isEmpty ? "開始しています…":model.online.status).font(AppFonts.style(15,.subheadline)).frame(maxWidth:.infinity).accessibilityIdentifier("online_lobby_status")
            if model.online.seekMode==Netplay.quick{Text("相手が\(OnlineBot.waitMillis/1000)秒見つからない時は、ボット（弱め）と対局します").font(AppFonts.style(11,.caption2)).foregroundStyle(cream.opacity(0.85)).multilineTextAlignment(.center).frame(maxWidth:.infinity).accessibilityIdentifier("online_bot_note")}
            if model.online.seekMode==Netplay.guest,let room=model.online.roomCode{Text(verbatim:"ルーム \(room)").font(AppFonts.style(12,.caption)).frame(maxWidth:.infinity)}
            if !model.online.notice.isEmpty{Text(model.online.notice).font(AppFonts.style(12,.caption)).foregroundStyle(.orange).frame(maxWidth:.infinity).accessibilityIdentifier("online_notice")}
            if model.online.seekMode>=0{Button("キャンセル"){model.cancelSeek()}.buttonStyle(.bordered).frame(maxWidth:.infinity).accessibilityIdentifier("online_cancel")}
        }.padding(14).foregroundStyle(cream).background(.black.opacity(0.78)).clipShape(RoundedRectangle(cornerRadius:10))
    }
    @ViewBuilder private var lobby:some View {
        if model.online.roomPage {roomPage}else{mainLobby}
    }
    private var mainLobby:some View {
        VStack(spacing:12){
            Text("3分／10分切れ負け・無料のオンライン対戦").font(AppFonts.mincho(20)).foregroundStyle(gold).padding(.top,12)
            Button{accountSheet=true}label:{HStack{VStack(alignment:.leading,spacing:4){Text(model.account.name).font(AppFonts.style(17,.headline,bold:true));Text(verbatim:"レート \(model.account.ratingText) · \(model.account.wins)勝\(model.account.losses)敗\(model.account.draws)分").font(AppFonts.style(12,.caption))};Spacer();Label("アカウント",systemImage:"person.crop.circle").font(AppFonts.style(15,.subheadline))}
                .padding(12).foregroundStyle(cream).background(Color.black.opacity(0.78)).clipShape(RoundedRectangle(cornerRadius:10))}.buttonStyle(.plain).accessibilityIdentifier("online_account")
            timePicker
            seekButton("ランダム対戦","レート戦・近いレートの相手と対局",Netplay.quick,"online_quick"){model.startSeek(Netplay.quick,code:nil)}
            Button{model.openRoomPage(true)}label:{Text("ルームで友だちと対局 ›").font(AppFonts.style(13,.footnote,bold:true)).foregroundStyle(gold).padding(.horizontal,16).padding(.vertical,7).background(Capsule().fill(Color.black.opacity(0.45))).overlay(Capsule().stroke(gold.opacity(0.6),lineWidth:1))}
                .buttonStyle(.plain).accessibilityIdentifier("online_room_page")
            statusPanel
            Text("サーバーを使わず、公開Nostrリレーで相手を探してP2P（UDP）で直接つながります。直結できない回線ではリレー経由で対局を続けます。Android版とも対戦できます。対戦相手にはあなたのIPアドレスが伝わります。対局中はアプリを離れても時計は止まりません。").font(AppFonts.style(11,.caption2)).foregroundStyle(cream.opacity(0.8)).padding(.horizontal,6)
        }.padding(.horizontal,16)
    }
    private var roomPage:some View {
        VStack(spacing:12){
            HStack{Button{model.openRoomPage(false)}label:{Label("戻る",systemImage:"chevron.left").font(AppFonts.style(15,.subheadline,bold:true)).foregroundStyle(cream)}.buttonStyle(.plain).accessibilityIdentifier("online_room_back");Spacer()}.padding(.top,8)
            Text("ルーム対局").font(AppFonts.mincho(24)).foregroundStyle(gold)
            Text("6桁のコードで友だちと対局・レート変動なし").font(AppFonts.style(12,.caption)).foregroundStyle(cream)
            Text("持ち時間（ルームを作る側が決めます）").font(AppFonts.style(11,.caption2)).foregroundStyle(cream.opacity(0.85))
            timePicker
            seekButton("ルームを作る","6桁のコードを友だちに伝えます",Netplay.host,"online_host"){model.hostRoom()}
            seekButton("ルームに参加","友だちのコードを入力します",Netplay.guest,"online_join"){code="";joining=true}
            statusPanel
        }.padding(.horizontal,16)
    }
}
