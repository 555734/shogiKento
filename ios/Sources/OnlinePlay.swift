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
    var bot:OnlineBot?,seekToken=0,seekStarted=Date()
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
        let bot=OnlineBot(level:OnlineBot.level(for:account.rating),game:{[weak self] in self?.online.game},deliver:{[weak self] m in self?.botMessage(m)})
        var g=OnlineGame(mySide:Bool.random() ? 1:-1,rules:EngineRules(),time:OnlineGame.millis(online.timeControl))
        online.bot=bot;online.peer=bot.identity;online.cardRound=0;online.lastResult=nil;online.rated=true;online.notice="";online.direct=false;online.rtt = -1;online.route=""
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
        online.seekStarted=Date();online.seekToken+=1;let token=online.seekToken
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

private let gold=Color(red:0.86,green:0.77,blue:0.56),cream=Color(red:0.96,green:0.92,blue:0.85),burgundy=Color(red:0.25,green:0.04,blue:0.06)
struct OnlineScreen:View {
    @EnvironmentObject var model:AppModel
    @State private var joining=false
    @State private var code=""
    @State private var resign=false
    @State private var accountSheet=false
    var body:some View {
        GeometryReader {viewport in ScrollViewReader {proxy in ScrollView {VStack(spacing:14){
            if let game=model.online.game {OnlineGameView(game:game,resign:$resign)}else{lobby.frame(minHeight:viewport.size.height-24)}
            TestAdSlot(placement:"match").id("online").padding(.horizontal,12)
        }.frame(minHeight:viewport.size.height,alignment:.top).frame(maxWidth:600).frame(maxWidth:.infinity).padding(.bottom,15)}
            // The result card sits below the board; bring it into view when the game ends.
            .onChange(of:model.online.game?.over ?? false){over in if over{withAnimation(.easeOut(duration:0.35)){proxy.scrollTo("online_result",anchor:.bottom)}}}}}
        .background(Image(uiImage:AppAssets.image("background")).resizable().scaledToFill().overlay(Color(red:0.04,green:0.01,blue:0.02).opacity(0.3)).overlay(ProgressTint(moves:model.online.game?.movesPlayed ?? -1)).ignoresSafeArea())
        .alert("ルームに参加",isPresented:$joining){TextField("6桁のコード",text:$code).keyboardType(.numberPad).accessibilityIdentifier("room_code_field");Button("キャンセル",role:.cancel){};Button("参加"){model.joinRoom(code)}.accessibilityIdentifier("room_join")}message:{Text("友だちのルームコードを入力してください")}
        .alert("投了しますか？",isPresented:$resign){Button("キャンセル",role:.cancel){};Button("投了",role:.destructive){model.onlineResign()}}
        .sheet(isPresented:$accountSheet){AccountView()}
    }
    private var timePicker:some View {
        HStack(spacing:8){ForEach(OnlineGame.timeControls,id:\.self){tc in let on=model.online.timeControl==tc
            Button{model.chooseTimeControl(tc)}label:{Text(OnlineGame.label(tc)).font(AppFonts.ui(12.5,bold:true)).foregroundStyle(on ? LacquerUI.ivory:LacquerUI.dim).frame(width:116,height:40)
                .background(Capsule().fill(LinearGradient(colors:on ? LacquerUI.red:LacquerUI.ink,startPoint:.top,endPoint:.bottom)))
                .overlay(Capsule().strokeBorder(on ? LacquerUI.rim:Lacquer.hairline,lineWidth:on ? 1.3:0.9))}
                .buttonStyle(.plain).disabled(model.online.seekMode>=0).accessibilityIdentifier("online_tc_\(tc)")}}
    }
    private func hero(_ title:String,_ caption:String)->some View {
        VStack(spacing:8){Text(title).font(AppFonts.black(32)).kerning(4).foregroundStyle(LinearGradient(colors:[Color(red:0.95,green:0.9,blue:0.78),Color(red:0.8,green:0.69,blue:0.48)],startPoint:.top,endPoint:.bottom)).shadow(color:.black.opacity(0.55),radius:0,x:0,y:1.5)
            Text(caption).font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream.opacity(0.7))}
    }
    /// The account as one quiet line: the name brightest, the rating dimmer, a small link.
    private var accountCard:some View {
        Button{accountSheet=true}label:{HStack(alignment:.firstTextBaseline,spacing:8){Text(model.account.name).font(AppFonts.ui(14,bold:true)).foregroundStyle(Lacquer.cream);Text(verbatim:"レート \(model.account.ratingText)").font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream.opacity(0.6));Spacer()
                Text("アカウント ›").font(AppFonts.ui(11)).foregroundStyle(gold.opacity(0.85))}.padding(.vertical,8).contentShape(Rectangle()).overlay(alignment:.bottom){Rectangle().fill(Lacquer.hairline).frame(height:0.8)}}
            .buttonStyle(.plain).accessibilityIdentifier("online_account")
    }
    private func note(_ text:String)->some View {Text(text).font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream.opacity(0.6)).multilineTextAlignment(.center).frame(maxWidth:.infinity)}
    private var networkCard:some View {
        VStack(spacing:6){
            HStack(spacing:6){Circle().fill(model.online.relaysOpen>0 ? Color.green.opacity(0.8):Color.yellow.opacity(0.8)).frame(width:6,height:6)
                Text((model.net==nil ? "未接続":"公開リレー \(model.online.relaysOpen)/\(model.online.relaysTotal)")+(model.online.network.isEmpty ? "":" · "+model.online.network)).font(AppFonts.ui(10)).foregroundStyle(Lacquer.cream.opacity(0.6)).lineLimit(1).accessibilityIdentifier("online_relays");Spacer()}
            Text(model.online.roomPage ? "ルームを作るか、コードで参加してください":"持ち時間を選んで対局開始").font(AppFonts.ui(1)).opacity(0.01).frame(height:1).accessibilityIdentifier("online_lobby_status")
            if !model.online.notice.isEmpty{Text(model.online.notice).font(AppFonts.ui(11)).foregroundStyle(.orange).accessibilityIdentifier("online_notice")}
        }
    }
    @ViewBuilder private var lobby:some View {
        if model.online.seekMode>=0 {MatchingView()}else if model.online.roomPage {roomPage}else{mainLobby}
    }
    /// Random match is the main action: its title and time control in the middle, 対局開始 low within thumb reach,
    /// the room as a small link beneath, account and network as quiet lines.
    private var mainLobby:some View {
        VStack(spacing:12){
            accountCard.padding(.top,10)
            Spacer(minLength:30)
            hero("ランダム対戦","近いレートの相手と対局・レートが変動します")
            timePicker
            note("\(OnlineBot.waitMillis/1000)秒で相手が見つからなければ、レートの近いボットと対局")
            Spacer(minLength:30)
            Button("対局開始"){model.startSeek(Netplay.quick,code:nil)}.buttonStyle(GlossButtonStyle(kind:.golden,size:32,height:70)).accessibilityIdentifier("online_quick")
            Button{model.openRoomPage(true)}label:{Text("友だちとルームで対局 ›").font(AppFonts.ui(12)).foregroundStyle(gold.opacity(0.85)).padding(8)}.buttonStyle(.plain).accessibilityIdentifier("online_room_page")
            networkCard.padding(.top,4)
            Text("P2Pで直接接続・相手にはIPアドレスが伝わります・対局中は時計が止まりません").font(AppFonts.ui(9)).foregroundStyle(Lacquer.cream.opacity(0.4)).padding(.bottom,8)
        }.padding(.horizontal,14)
    }
    private var roomPage:some View {
        VStack(spacing:12){
            HStack{Button{model.openRoomPage(false)}label:{Label("ランダム対戦",systemImage:"chevron.left").font(AppFonts.ui(13,bold:true)).foregroundStyle(Lacquer.cream.opacity(0.8))}.buttonStyle(.plain).accessibilityIdentifier("online_room_back");Spacer()}.padding(.top,8)
            accountCard
            Spacer(minLength:30)
            hero("ルーム対局","6桁のコードで友だちと対局・レート変動なし")
            timePicker
            Spacer(minLength:30)
            Button("ルームを作る"){model.hostRoom()}.buttonStyle(GlossButtonStyle(kind:.golden,size:24,height:62)).accessibilityIdentifier("online_host")
            Button("コードで参加"){code="";joining=true}.buttonStyle(LacquerButtonStyle(size:15,minHeight:48)).accessibilityIdentifier("online_join")
            networkCard.padding(.top,4).padding(.bottom,8)
        }.padding(.horizontal,14)
    }
}

/// Lacquer colors of the online screens.
enum LacquerUI {
    static let red=[Color(red:0.59,green:0.16,blue:0.13),Color(red:0.45,green:0.1,blue:0.06),Color(red:0.33,green:0.06,blue:0.04)],champagne=[Color(red:0.93,green:0.87,blue:0.71),Color(red:0.84,green:0.74,blue:0.53),Color(red:0.68,green:0.55,blue:0.33)]
    static let ink=[Color(red:0.16,green:0.13,blue:0.12),Color(red:0.1,green:0.07,blue:0.07),Color(red:0.05,green:0.04,blue:0.04)]
    static let ivory=Color(red:0.96,green:0.92,blue:0.85),dim=Color(red:0.6,green:0.55,blue:0.48),rim=Color(red:0.93,green:0.86,blue:0.7).opacity(0.8)
}
extension View {
    /// A thin dark outline around light text on lacquer, drawn with four hard shadows.
    @ViewBuilder func outlined(_ on:Bool)->some View {
        if on{let c=Color(red:0.29,green:0.03,blue:0.02);self.shadow(color:c,radius:0,x:1,y:0).shadow(color:c,radius:0,x:-1,y:0).shadow(color:c,radius:0,x:0,y:1).shadow(color:c,radius:0,x:0,y:-1)}else{self}
    }
}
/// Big lacquer button: champagne gold or crimson, a thin bevel (no gloss), a light rim and a plain label.
struct GlossButtonStyle:ButtonStyle {
    enum Kind {case golden,red}
    var kind:Kind,size:CGFloat,height:CGFloat
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration:Configuration)->some View {
        let shape=RoundedRectangle(cornerRadius:min(10,height*0.24)),golden=kind == .golden
        configuration.label.font(AppFonts.black(size)).kerning(size*0.1).foregroundStyle(golden ? Color(red:0.17,green:0.1,blue:0.05):LacquerUI.ivory)
            .shadow(color:golden ? .white.opacity(0.4):.black.opacity(0.5),radius:0,x:0,y:1)
            .frame(maxWidth:.infinity,minHeight:height)
            .background(shape.fill(LinearGradient(colors:golden ? LacquerUI.champagne:LacquerUI.red,startPoint:.top,endPoint:.bottom)))
            .overlay(alignment:.top){Rectangle().fill(.white.opacity(golden ? 0.5:0.25)).frame(height:0.8).padding(.horizontal,10).padding(.top,1.2)}
            .overlay(shape.strokeBorder(golden ? Color(red:0.96,green:0.92,blue:0.82).opacity(0.9):LacquerUI.rim,lineWidth:1.2))
            .shadow(color:.black.opacity(0.45),radius:1.5,y:2.5)
            .scaleEffect(configuration.isPressed ? 0.97:1).opacity(enabled ? 1:0.5)
    }
}
/// While a seek runs: a game-clock dial whose gold arc fills until the bot steps in (a turning segment while a room
/// waits), the host's room code, the player's komadai, the status and 中止.
struct MatchingView:View {
    @EnvironmentObject var model:AppModel
    private var quick:Bool {model.online.seekMode==Netplay.quick}
    private var title:String {quick ? "対戦相手を探しています":model.online.seekMode==Netplay.host ? "相手の参加を待っています":"ルームに接続しています"}
    var body:some View {
        VStack(spacing:12){
            VStack(spacing:4){Text(title).font(AppFonts.black(22)).kerning(2).foregroundStyle(LinearGradient(colors:[Color(red:0.95,green:0.9,blue:0.78),Color(red:0.8,green:0.69,blue:0.48)],startPoint:.top,endPoint:.bottom))
                Text(OnlineGame.label(model.online.timeControl)+(quick ? "・レート戦":"・ルーム対局")).font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream)}
                .padding(.top,14).accessibilityIdentifier("online_matching")
            if model.online.seekMode==Netplay.host,let room=model.online.roomCode{
                VStack(spacing:4){Text("ルームコード").font(AppFonts.ui(11));Text(verbatim:"\(room.prefix(3)) \(room.suffix(3))").font(AppFonts.black(34).monospacedDigit()).kerning(4).foregroundStyle(Color(red:0.93,green:0.86,blue:0.7)).accessibilityIdentifier("online_room_code");Text("友だちに伝えてください").font(AppFonts.ui(10)).opacity(0.8)}
                    .foregroundStyle(Lacquer.cream).padding(.horizontal,26).padding(.vertical,10).inkCard(radius:10,lit:true)}
            MatchClock(start:model.online.seekStarted,waitMillis:quick ? OnlineBot.waitMillis:nil).frame(width:204,height:204)
            HStack{VStack(alignment:.leading,spacing:3){Text(model.account.name).font(AppFonts.ui(14,bold:true));Text(verbatim:"レート \(model.account.ratingText)").font(AppFonts.ui(11)).opacity(0.8)};Spacer()
                Image(uiImage:AppAssets.piece(8)).resizable().scaledToFit().frame(width:44,height:46)}
                .foregroundStyle(Lacquer.cream).padding(.horizontal,14).padding(.vertical,6).inkCard().padding(.horizontal,14)
            Text(model.online.status.isEmpty ? "開始しています…":model.online.status).font(AppFonts.ui(13,bold:true)).foregroundStyle(Lacquer.cream).accessibilityIdentifier("online_lobby_status")
            if quick{Text("相手が\(OnlineBot.waitMillis/1000)秒見つからない時は、レートの近いボットと対局します").font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream.opacity(0.8)).multilineTextAlignment(.center).accessibilityIdentifier("online_bot_note")}
            else if model.online.seekMode==Netplay.guest,let room=model.online.roomCode{Text(verbatim:"ルーム \(room)").font(AppFonts.ui(11)).foregroundStyle(Lacquer.cream.opacity(0.8))}
            if !model.online.notice.isEmpty{Text(model.online.notice).font(AppFonts.ui(11)).foregroundStyle(.orange).accessibilityIdentifier("online_notice")}
            HStack{Text(model.net==nil ? "未接続":"公開リレー \(model.online.relaysOpen)/\(model.online.relaysTotal)").font(AppFonts.ui(11)).foregroundStyle(model.online.relaysOpen>0 ? .green:.yellow);Spacer()
                Button("中止"){model.cancelSeek()}.buttonStyle(GlossButtonStyle(kind:.red,size:22,height:54)).frame(width:170).accessibilityIdentifier("online_cancel")}.padding(.horizontal,14)
        }
    }
}
/// A game-clock dial: minute ticks, the elapsed time, and a gold arc that fills over `waitMillis` (or turns while a room waits).
struct MatchClock:View {
    let start:Date,waitMillis:Int64?
    var body:some View {
        TimelineView(.animation){timeline in
            let elapsed=max(0,timeline.date.timeIntervalSince(start))
            ZStack{
                Circle().fill(LinearGradient(colors:LacquerUI.ink,startPoint:.top,endPoint:.bottom)).shadow(color:.black.opacity(0.5),radius:3,y:3)
                Circle().strokeBorder(LacquerUI.rim,lineWidth:1.2)
                Canvas{context,size in
                    let c=CGPoint(x:size.width/2,y:size.height/2),R=size.width/2-6
                    for k in 0..<60{let a=Double(k)*Double.pi/30,r0=R-(k%5==0 ? 10:5);var p=Path();p.move(to:CGPoint(x:c.x+r0*sin(a),y:c.y-r0*cos(a)));p.addLine(to:CGPoint(x:c.x+(R-2)*sin(a),y:c.y-(R-2)*cos(a)))
                        context.stroke(p,with:.color(Color(red:0.86,green:0.77,blue:0.56).opacity(k%5==0 ? 0.8:0.4)),lineWidth:k%5==0 ? 1.6:0.8)}
                    let ring=CGRect(x:c.x-R+10,y:c.y-R+10,width:(R-10)*2,height:(R-10)*2)
                    context.stroke(Path(ellipseIn:ring),with:.color(Color(red:0.86,green:0.77,blue:0.56).opacity(0.2)),style:StrokeStyle(lineWidth:6))
                    var arc=Path()
                    if let wait=waitMillis{let p=min(1,elapsed*1000/Double(max(1,wait)));arc.addArc(center:c,radius:R-10,startAngle:.degrees(-90),endAngle:.degrees(-90+max(1,360*p)),clockwise:false)}
                    else{let s=elapsed.truncatingRemainder(dividingBy:3)/3*360-90;arc.addArc(center:c,radius:R-10,startAngle:.degrees(s),endAngle:.degrees(s+70),clockwise:false)}
                    context.stroke(arc,with:.color(Color(red:0.86,green:0.77,blue:0.56)),style:StrokeStyle(lineWidth:6,lineCap:.round))
                }
                VStack(spacing:2){Text(String(format:"%d:%02d",Int(elapsed)/60,Int(elapsed)%60)).font(AppFonts.black(48).monospacedDigit()).foregroundStyle(LacquerUI.ivory)
                    Text(waitMillis.map{"\($0/1000)秒でボットと対局"} ?? "経過時間").font(AppFonts.ui(10)).foregroundStyle(Lacquer.cream.opacity(0.8))}.offset(y:6)
            }
        }.accessibilityHidden(true)
    }
}
