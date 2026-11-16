import SwiftUI
import UIKit

private let gold=Color(red:0.86,green:0.77,blue:0.56),cream=Color(red:1,green:0.94,blue:0.81),burgundy=Color(red:0.25,green:0.04,blue:0.06)
struct RootView:View {
    @EnvironmentObject var model:AppModel
    @EnvironmentObject var coffee:CoffeeStore
    @Environment(\.scenePhase) private var phase
    @State private var showCoffee=false
    @State private var settings=false
    @State private var paste=false
    @State private var moves=false
    @State private var pendingPaste:String?
    @State private var leaveOnline=false
    private let timer=Timer.publish(every:0.1,on:.main,in:.common).autoconnect()
    var body: some View {
        NavigationStack {
            Group {switch model.screen{case .home:HomeView(settings:$settings,paste:$paste);case .archive:ArchiveView();case .online:OnlineScreen();case .study,.match:BoardScreen(showMoves:$moves)}}
                .background(burgundy.ignoresSafeArea()).navigationTitle(model.screen == .home ? "":model.screen == .study ? "検討モード":model.screen == .match ? "対局モード":model.screen == .online ? "オンライン対戦":"過去の棋譜")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {if model.screen != .home{ToolbarItem(placement:.navigationBarLeading){Button("ホーム"){if model.onlineInProgress{leaveOnline=true}else{model.enter(.home)}}.accessibilityIdentifier("home")}}}
        }
        .tint(gold).onAppear{coffee.foreground()}.onChange(of:phase){value in if value == .background{coffee.background(offerVisible:showCoffee)}else if value == .active{coffee.foreground();Task{await coffee.refresh()}}}.onChange(of:coffee.owned){_ in model.membershipChanged()}.onReceive(timer){_ in model.tick();if coffee.checkedEntitlements && coffee.offerPending && !showCoffee && !settings && !paste && !moves && model.canShowCoffeeOffer && model.importOffer==nil && model.archiveOffer==nil && model.error==nil && !ProcessInfo.processInfo.arguments.contains("-coffee-test-suppress"){coffee.offerPending=false;showCoffee=true}}
        .sheet(isPresented:$showCoffee){CoffeeView()}.onChange(of:showCoffee){value in if model.screen == .match{model.setMatchSettingsOpen(value)}}
        .sheet(isPresented:$settings){SettingsView()}.sheet(isPresented:$paste,onDismiss:{if let text=pendingPaste{pendingPaste=nil;model.parseClipboard(text)}}){PasteView{pendingPaste=$0;paste=false}}.sheet(isPresented:$moves){MoveListView()}
        .alert("棋譜の取り込み",isPresented:Binding(get:{model.importOffer != nil},set:{if !$0{model.importOffer=nil}})){Button("キャンセル",role:.cancel){model.importOffer=nil};Button("検討する"){if let r=model.importOffer{model.importOffer=nil;paste=false;model.acceptImport(r)}}}message:{Text("コピーされた棋譜を検討しますか？\(model.session.study.moves>0 ? "現在の検討内容を置き換えます。保存済みの棋譜は残ります。":"")")}
        .alert("検討棋譜の置き換え",isPresented:Binding(get:{model.archiveOffer != nil},set:{if !$0{model.archiveOffer=nil}})){Button("キャンセル",role:.cancel){model.archiveOffer=nil};Button("開く"){if let r=model.archiveOffer{model.archiveOffer=nil;model.open(r)}}}message:{Text("保存した棋譜を検討で開きますか？現在の検討内容を置き換えます。")}
        .alert("対局中です。投了して退出しますか？",isPresented:$leaveOnline){Button("キャンセル",role:.cancel){};Button("投了して退出",role:.destructive){model.enter(.home)}}
        .alert("エラー",isPresented:Binding(get:{model.error != nil},set:{if !$0{model.error=nil}})){Button("閉じる"){model.error=nil}}message:{Text(model.error ?? "")}
    }
}
struct HomeView:View {
    @EnvironmentObject var model:AppModel
    @Binding var settings:Bool
    @Binding var paste:Bool
    var body: some View {
        GeometryReader {geo in
            ScrollView {VStack(spacing:15){
                Text("AI将棋検討").font(AppFonts.black(58)).minimumScaleFactor(0.6).lineLimit(1).foregroundStyle(LinearGradient(colors:[cream,gold,Color(red:0.7,green:0.43,blue:0.12)],startPoint:.top,endPoint:.bottom)).shadow(color:.black,radius:2,y:3).frame(height:max(140,min(240,geo.size.height*0.29))).padding(.horizontal,16).padding(.top,32).accessibilityLabel("AI将棋検討")
                Spacer(minLength:15)
                Button{model.enter(.study)}label:{HStack(spacing:22){Image(systemName:"circle.dashed").font(.system(size:65,weight:.thin));VStack(alignment:.leading,spacing:8){Text("検討モード").font(AppFonts.black(34));Text("合法手で検討・自由配置にも切替可").font(AppFonts.ui(12)).foregroundStyle(Color(red:0.92,green:0.87,blue:0.78).opacity(0.7))}}.padding(24).frame(maxWidth:.infinity,minHeight:145).background(LinearGradient(colors:[Color(red:0.7,green:0.03,blue:0.04),burgundy],startPoint:.topLeading,endPoint:.bottomTrailing))}.buttonStyle(HomeCard()).accessibilityIdentifier("mode_study")
                Button{model.enter(.match)}label:{HStack(spacing:22){Image(uiImage:AppAssets.piece(8)).resizable().scaledToFit().frame(width:56,height:66);VStack(alignment:.leading,spacing:5){Text("対局モード").font(AppFonts.black(25));Text("CPUと対局・時間や先後を設定").font(AppFonts.ui(12)).foregroundStyle(Color(red:0.92,green:0.87,blue:0.78).opacity(0.7))};Spacer()}.padding(.horizontal,22).padding(.vertical,12).frame(maxWidth:.infinity).background(Color(red:0.12,green:0.09,blue:0.06))}.buttonStyle(HomeCard()).accessibilityIdentifier("mode_match")
                Button{model.enter(.online)}label:{HStack(spacing:22){Image(systemName:"person.2.wave.2").font(.system(size:38,weight:.light)).frame(width:56,height:56);VStack(alignment:.leading,spacing:5){Text("オンライン対戦").font(AppFonts.black(22));Text("無料・登録不要のP2P対人戦").font(AppFonts.ui(11)).foregroundStyle(Color(red:0.92,green:0.87,blue:0.78).opacity(0.7))};Spacer()}.padding(.horizontal,22).padding(.vertical,12).frame(maxWidth:.infinity).background(Color(red:0.06,green:0.1,blue:0.16))}.buttonStyle(HomeCard()).accessibilityIdentifier("mode_online")
                Button{paste=true}label:{Label("棋譜を貼り付け",systemImage:"doc.text").font(AppFonts.ui(16,bold:true)).foregroundStyle(Lacquer.cream).frame(maxWidth:.infinity,minHeight:46).background(RoundedRectangle(cornerRadius:10).fill(Color.black.opacity(0.7))).overlay(RoundedRectangle(cornerRadius:10).strokeBorder(Lacquer.hairline,lineWidth:0.8))}.buttonStyle(.plain).padding(.horizontal,16).padding(.top,6).accessibilityIdentifier("open_paste")
                Button{model.enter(.archive)}label:{Label("過去の棋譜",systemImage:"clock.arrow.circlepath").font(AppFonts.ui(16,bold:true)).foregroundStyle(Lacquer.cream).frame(maxWidth:.infinity,minHeight:46).background(RoundedRectangle(cornerRadius:10).fill(Color.black.opacity(0.7))).overlay(RoundedRectangle(cornerRadius:10).strokeBorder(Lacquer.hairline,lineWidth:0.8))}.buttonStyle(.plain).padding(.horizontal,16).accessibilityIdentifier("open_archive")
                TestAdSlot(placement:"home")
                Spacer(minLength:35)
            }.padding(.horizontal,20).frame(maxWidth:560,minHeight:geo.size.height).frame(maxWidth:.infinity)}
                .background(Image(uiImage:AppAssets.image("home_background")).resizable().scaledToFill().ignoresSafeArea())
                .overlay(alignment:.topTrailing){Button{settings=true}label:{VStack(spacing:2){Image(systemName:"gearshape").font(.title3);Text("設定").font(AppFonts.style(11,.caption2))}.frame(width:48,height:48).background(burgundy.opacity(0.8)).clipShape(RoundedRectangle(cornerRadius:9)).overlay(RoundedRectangle(cornerRadius:9).stroke(gold.opacity(0.7)))}.accessibilityIdentifier("settings").padding(12)}
        }
    }
}
struct HomeCard:ButtonStyle {func makeBody(configuration:Configuration)->some View {configuration.label.foregroundStyle(cream).clipShape(RoundedRectangle(cornerRadius:14)).overlay(RoundedRectangle(cornerRadius:14).stroke(LinearGradient(colors:[cream,Color.brown,gold],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1.5)).shadow(color:gold.opacity(configuration.isPressed ? 0.6:0.18),radius:5).scaleEffect(configuration.isPressed ? 0.98:1)}}
struct BoardScreen:View {
    @EnvironmentObject var model:AppModel
    @Binding var showMoves:Bool
    @State private var resign=false
    @State private var restart=false
    @State private var matchSettings=false
    var body:some View {
        GeometryReader {viewport in ScrollView {VStack(spacing:16){VStack(spacing:8){
            if model.screen == .match{MatchPanel(side:model.cpuSide)}else{HandView(side:-1)}
            BoardView().aspectRatio(1226/1283.0,contentMode:.fit).anchorPreference(key:ArrowAnchorKey.self,value:.bounds){["board":$0]}
                .overlay{if model.screen == .match,let banner=model.banner{GameBannerLayer(banner:banner)}}
                .overlay{if model.screen == .match && model.session.winner != 0 && model.banner != nil{MatchEndButtons().offset(y:70).transition(.opacity.animation(.easeIn(duration:0.25).delay(model.reducedEffects ? 0:GameBanner.buttonsAfter)))}}
            if model.screen == .match{MatchPanel(side:model.humanSide)}else{HandView(side:1)}
            if model.screen == .match {
                Text(model.session.winner != 0 ? model.session.match.result:!model.session.matchStarted ? "対局を開始してください":model.thinking ? "CPUの手番":"あなたの手番").font(AppFonts.ui(18,bold:true)).foregroundStyle(model.session.matchStarted && model.session.winner==0 && model.position.side==model.humanSide ? Color(red:0.94,green:0.83,blue:0.56):Lacquer.cream).accessibilityIdentifier("match_status")
                Text("\(model.session.match.moves)手目").accessibilityIdentifier("match_ply")
                if !model.session.matchStarted{Button{model.startMatch()}label:{Text("対局開始").padding(.horizontal,28)}.buttonStyle(LacquerButtonStyle(kind:.primary,size:20,minHeight:46)).accessibilityIdentifier("start_match")}
                HStack {Button("やり直し"){restart=true}.buttonStyle(LacquerButtonStyle());Spacer();Button("AI再試行"){model.scheduleCPU()}.buttonStyle(LacquerButtonStyle()).disabled(!model.thinking && model.position.side==model.humanSide);Spacer();Button("投了"){resign=true}.buttonStyle(LacquerButtonStyle(kind:.danger)).disabled(!model.session.matchStarted || model.session.winner != 0)}.padding(.horizontal)
                Button("対局設定 · \(model.matchOptions.minutes)分 / AI Lv.\(model.matchOptions.level) / あなた\(model.humanSide==1 ? "先手":"後手")"){matchSettings=true}.buttonStyle(LacquerButtonStyle(size:13)).accessibilityIdentifier("match_settings").padding(.horizontal)
            }else{AnalysisView(showMoves:$showMoves)}
        }.overlayPreferenceValue(ArrowAnchorKey.self){anchors in GeometryReader{proxy in
            MoveEffectOverlay(anchors:anchors,proxy:proxy)
            if let board=anchors["board"],!model.boardCandidates.isEmpty {
                DropCandidateArrows(candidates:model.boardCandidates,side:model.position.side,board:proxy[board],hands:handFrames(anchors,proxy:proxy))
            }
        }}.frame(minHeight:viewport.size.height,alignment:model.screen == .match ? .center:.top)
            TestAdSlot(placement:model.screen == .study ? "study":"match").id(model.screen.rawValue).padding(.horizontal,12)
        }.frame(maxWidth:600).frame(maxWidth:.infinity).padding(.bottom,15)}}
        .background(Image(uiImage:AppAssets.image("background")).resizable().scaledToFill().overlay(Color(red:0.04,green:0.01,blue:0.02).opacity(0.3)).overlay(ProgressTint(moves:model.screen == .match ? model.session.match.moves:-1)).ignoresSafeArea())
        .sheet(isPresented:$matchSettings,onDismiss:{model.setMatchSettingsOpen(false)}){MatchSettingsView(options:model.matchOptions).onAppear{model.setMatchSettingsOpen(true)}}
        .alert("投了しますか？",isPresented:$resign){Button("キャンセル",role:.cancel){};Button("投了",role:.destructive){model.resign()}}
        .alert("対局をやり直しますか？",isPresented:$restart){Button("キャンセル",role:.cancel){};Button("やり直す",role:.destructive){model.restartMatch()}}
    }
    /// Hand pieces of the side to move, keyed by piece type.
    func handFrames(_ anchors:[String:Anchor<CGRect>],proxy:GeometryProxy)->[Int:CGRect] {
        var frames=[Int:CGRect]();for piece in 1...7{if let a=anchors["hand_\(model.position.side)_\(piece)"]{frames[piece]=proxy[a]}};return frames
    }
}
struct BoardView:View {
    @EnvironmentObject var model:AppModel
    var body:some View {
        GeometryReader {geo in let w=geo.size.width,h=geo.size.height
            ZStack {Image(uiImage:AppAssets.image("board")).resizable()
                Canvas {context,size in var grid=Path();for i in 0...9{let x=size.width*(0.01+0.98*Double(i)/9),y=size.height*(0.01+0.98*Double(i)/9);grid.move(to:CGPoint(x:x,y:size.height*0.01));grid.addLine(to:CGPoint(x:x,y:size.height*0.99));grid.move(to:CGPoint(x:size.width*0.01,y:y));grid.addLine(to:CGPoint(x:size.width*0.99,y:y))};context.stroke(grid,with:.color(Color(red:0.30,green:0.15,blue:0.03)),lineWidth:0.8)}
                VStack(spacing:0){ForEach(0..<9,id:\.self){r in HStack(spacing:0){ForEach(0..<9,id:\.self){c in let square=model.viewSide == -1 ? 80-r*9-c:r*9+c,piece=model.position.board[square],target=model.targets.contains{$0.destination==square}
                    Button{model.tap(square)}label:{ZStack{if let last=model.lastMoveSquares,last.to==square || last.from==square{LastMoveMark(destination:last.to==square)};if model.selected==square{Color.blue.opacity(0.4)}else if target{Color.yellow.opacity(0.5)};if piece != 0 && model.effectHiddenSquare != square{Image(uiImage:AppAssets.piece(piece)).resizable().scaledToFit().rotationEffect(.degrees(piece*model.viewSide<0 ? 180:0)).padding(0.7)};if let last=model.lastMoveSquares,last.to==square{Rectangle().strokeBorder(Color(red:0.89,green:0.37,blue:0),lineWidth:3)}}.frame(maxWidth:.infinity,maxHeight:.infinity).contentShape(Rectangle())}.buttonStyle(.plain).frame(width:w*0.98/9,height:h*0.98/9)
                        .accessibilityIdentifier("cell_\(square/9)_\(square%9)").accessibilityLabel("\(9-square%9)\(Array("一二三四五六七八九")[square/9]) \(Position.glyphs[abs(piece)])").accessibilityValue(model.selected==square ? "選択":target ? "移動可能":"")
                        .onLongPressGesture {model.togglePromotion(square)}
                }}}}.padding(.horizontal,w*0.01).padding(.vertical,h*0.01)
                if !model.promotion.isEmpty{PromotionPicker(size:CGSize(width:w,height:h))}
                if !model.boardCandidates.isEmpty{CandidateArrows(candidates:model.boardCandidates,side:model.position.side,handDrops:Set((1...7).filter{model.position.hands[model.position.side==1 ? 0:1][$0]>0}))}
            }
        }
    }
}
/// CPU-match player panel, 1.5x the study strip: icon, name, clock (red from 30 seconds) and komadai. The side to
/// move is lit with a gold frame; the other is dimmed.
struct MatchPanel:View {
    @EnvironmentObject var model:AppModel
    let side:Int
    var body:some View {
        let turn=model.session.matchStarted && model.session.winner==0 && model.position.side==side
        let seconds=model.session.clocks[side==1 ? 0:1],time=max(0,Int(ceil(seconds)))
        HStack(spacing:10){
            PlayerIcon(side:side,lit:turn).frame(width:58,height:58)
            VStack(alignment:.leading,spacing:0){
                Text(model.playerName(side)).font(AppFonts.ui(15.5)).foregroundStyle(turn ? Lacquer.cream:Color(red:0.73,green:0.68,blue:0.62)).lineLimit(1).minimumScaleFactor(0.7)
                Text(String(format:"%02d:%02d",time/60,time%60)).font(AppFonts.ui(33,bold:true).monospacedDigit()).foregroundStyle(seconds<=30 ? Color(red:1,green:0.39,blue:0.31):turn ? Color(red:1,green:0.97,blue:0.93):Color(red:0.66,green:0.62,blue:0.56)).accessibilityIdentifier("match_clock_\(side)")
            }.layoutPriority(1)
            Spacer(minLength:0)
            ForEach(1..<8,id:\.self){p in let n=model.position.hands[side==1 ? 0:1][p],shown=model.shownHand(side,p);if n>0{Button{model.selectHand(p,side:side)}label:{ZStack(alignment:.bottomTrailing){Image(uiImage:AppAssets.piece(p*side)).resizable().scaledToFit().rotationEffect(.degrees(side*model.viewSide<0 ? 180:0));Text("\(shown)").font(AppFonts.ui(14,bold:true)).foregroundStyle(.white).padding(.horizontal,3).background(.black.opacity(0.7))}.opacity(shown>0 ? 1:0).scaleEffect(model.handPop==HandSlot(side:side,piece:p) ? 1.28:1).frame(maxWidth:48,maxHeight:54).background(model.selectedHand==p && model.selectedSide==side ? .yellow.opacity(0.4):.clear)}.buttonStyle(.plain).anchorPreference(key:ArrowAnchorKey.self,value:.bounds){["hand_\(side)_\(p)":$0]}.accessibilityIdentifier("hand_\(side)_\(p)")}}
        }.foregroundStyle(cream).padding(.horizontal,10).frame(height:76)
        .inkCard(radius:8,lit:turn).overlay{if !turn{RoundedRectangle(cornerRadius:8).fill(.black.opacity(0.28)).allowsHitTesting(false)}}
        .anchorPreference(key:ArrowAnchorKey.self,value:.bounds){["handbar_\(side)":$0]}
    }
}
/// Player icon in the match panels.
struct PlayerIcon:View {
    @EnvironmentObject var model:AppModel
    let side:Int,lit:Bool
    var body:some View {
        let me=side==model.humanSide,level=max(1,min(10,model.matchOptions.level))
        let art=AppAssets.image(me ? "avatar_player":String(format:"avatar_cpu_%02d",level)),rgb=MatchOptions.cpuColors[level-1]
        ZStack{
            if art.size.width>0 {Image(uiImage:art).resizable().scaledToFill().clipShape(Circle())}
            else {Circle().fill(RadialGradient(colors:[me ? Color(red:0.29,green:0.53,blue:0.75):Color(red:Double(rgb>>16&255)/255,green:Double(rgb>>8&255)/255,blue:Double(rgb&255)/255),me ? Color(red:0.11,green:0.23,blue:0.36):Color(red:0.16,green:0.06,blue:0.05)],center:.init(x:0.5,y:0.35),startRadius:2,endRadius:40))
                .overlay(Text(me ? "自":MatchOptions.cpuGlyphs[level-1]).font(AppFonts.black(26)).foregroundStyle(Color(red:1,green:0.95,blue:0.84)))}
        }.overlay(Circle().strokeBorder(lit ? Lacquer.hairlineLit:Color(red:0.63,green:0.53,blue:0.41).opacity(0.6),lineWidth:1.5))
        .overlay(alignment:.bottom){Text(side==1 ? "先手":"後手").font(AppFonts.ui(9.5,bold:true)).foregroundStyle(side==1 ? .white:Color(red:0.16,green:0.1,blue:0.06)).padding(.horizontal,5).padding(.vertical,1).background(RoundedRectangle(cornerRadius:3).fill(side==1 ? Color(red:0.09,green:0.06,blue:0.05):Color(red:0.95,green:0.91,blue:0.83))).overlay(RoundedRectangle(cornerRadius:3).strokeBorder(Lacquer.hairlineLit.opacity(0.7),lineWidth:0.8)).offset(y:5)}
    }
}
struct HandView:View {
    @EnvironmentObject var model:AppModel
    let side:Int
    var body:some View {
        HStack(spacing:5){VStack(alignment:.leading,spacing:3){Text(model.screen == .match || model.screen == .online ? ((side==model.viewSide ? "あなた":model.screen == .online ? "相手":"CPU")+"・"+(side==1 ? "先手":"後手")):(side==1 ? "先手":"後手")).font(AppFonts.style(12,.caption));if model.screen == .match{let time=max(0,Int(ceil(model.session.clocks[side==1 ? 0:1])));Text(String(format:"%02d:%02d",time/60,time%60)).font(.system(.title3,design:.monospaced))}else if model.screen == .online,let game=model.online.game{let time=Int(ceil(Double(max(0,game.remaining(side,AppModel.onlineNow)))/1000));Text(String(format:"%02d:%02d",time/60,time%60)).font(.system(.title3,design:.monospaced)).foregroundStyle(game.started && !game.over && game.turn==side ? gold:cream).accessibilityIdentifier("online_clock_\(side)")}else{Text("検討・時計なし").font(AppFonts.style(11,.caption2))}}.frame(minWidth:90,alignment:.leading)
            Spacer(minLength:0)
            ForEach(1..<(model.screen == .study && model.freePlacement ? 9:8),id:\.self){p in let n=model.position.hands[side==1 ? 0:1][p],shown=model.shownHand(side,p);if n>0{Button{model.selectHand(p,side:side)}label:{ZStack(alignment:.bottomTrailing){Image(uiImage:AppAssets.piece(p*side)).resizable().scaledToFit().rotationEffect(.degrees(side*model.viewSide<0 ? 180:0));Text("\(shown)").font(AppFonts.style(12,.caption,bold:true)).foregroundStyle(.white).padding(2).background(.black.opacity(0.7))}.opacity(shown>0 ? 1:0).scaleEffect(model.handPop==HandSlot(side:side,piece:p) ? 1.28:1).frame(maxWidth:36,maxHeight:40).background(model.selectedHand==p && model.selectedSide==side ? .yellow.opacity(0.4):.clear)}.buttonStyle(.plain).anchorPreference(key:ArrowAnchorKey.self,value:.bounds){["hand_\(side)_\(p)":$0]}.accessibilityIdentifier("hand_\(side)_\(p)")}}
        }.foregroundStyle(cream).padding(.horizontal,8).frame(height:48).background(.black.opacity(0.72)).anchorPreference(key:ArrowAnchorKey.self,value:.bounds){["handbar_\(side)":$0]}
    }
}
struct AnalysisView:View {
    @EnvironmentObject var model:AppModel
    @Binding var showMoves:Bool
    var body:some View {VStack(alignment:.leading,spacing:12){
        HStack {Text(model.score.map{String(format:"先手 %.1f%%",$0.percentage)} ?? "先手 ―").foregroundStyle(.blue);Spacer();Text(model.score.map{String(format:"後手 %.1f%%",100-$0.percentage)} ?? "後手 ―").foregroundStyle(.red)}.font(AppFonts.style(20,.title3,bold:true))
        Text("形勢の目安 · 深さ \(model.depth) / \(model.nodes)ノード").font(AppFonts.style(11,.caption2)).foregroundStyle(.secondary)
        HStack {Menu(model.freePlacement ? "自由配置 ▾":"合法手 ▾"){Button("通常移動（合法手のみ）"){model.setFree(false)};Button("自由配置"){model.setFree(true)}}.accessibilityIdentifier("study_operation");Spacer();Button("解析：\(model.position.side==1 ? "先手":"後手") ▾"){model.changeSide()}}
        if model.candidates.isEmpty{Text(model.analysisStatus).font(AppFonts.style(15,.subheadline)).accessibilityIdentifier("analysis_status");if !model.analyzing{Button("解析を再試行"){model.refreshAnalysis()}}}
        else{ForEach(Array(model.candidates.enumerated()),id:\.offset){index,c in HStack{Text("\(index+1). \(model.position.notation(c.move))");Spacer();Text(c.score.text)}.font(AppFonts.style(15,.subheadline)).accessibilityElement(children:.combine).accessibilityIdentifier("candidate_\(index)")};Text(model.analysisStatus).font(AppFonts.style(11,.caption2)).accessibilityIdentifier("analysis_status")}
        HStack {Text("評価値の推移（先手基準）");Spacer();Text("\(model.session.cursor)手目").accessibilityIdentifier("study_ply")}.font(AppFonts.style(12,.caption))
        EvaluationGraph().frame(height:85)
        HStack(spacing:5){ForEach(Array(["初期","前へ","次へ","最終","手順"].enumerated()),id:\.offset){i,label in Button{switch i{case 0:model.seek(0);case 1:model.seek(model.session.cursor-1);case 2:model.seek(model.session.cursor+1);case 3:model.seek(model.session.study.moves);default:showMoves=true}}label:{NavLabel(label:label,forks:i==2 ? model.session.studyBranchesAfterCursor:0)}.font(AppFonts.style(15,.subheadline)).frame(maxWidth:.infinity).buttonStyle(.bordered).accessibilityIdentifier("ply_\(i)")}}
    }.padding(14).background(.black.opacity(0.78)).foregroundStyle(cream)}
}
struct EvaluationGraph:View {
    @EnvironmentObject var model:AppModel
    var body:some View {GeometryReader{geo in let count=max(10,model.session.study.moves)
        Canvas{context,size in var grid=Path();for n in 0...4{let y=size.height*Double(n)/4;grid.move(to:CGPoint(x:0,y:y));grid.addLine(to:CGPoint(x:size.width,y:y))};context.stroke(grid,with:.color(.gray.opacity(0.5)),lineWidth:0.5);var line=Path(),previous:Int?
            for i in model.session.study.positions.indices{guard let score=model.session.scores[i] else{previous=nil;continue};let point=CGPoint(x:Double(i)/Double(count)*size.width,y:size.height*(0.5-score.graph/4000));if previous==i-1{line.addLine(to:point)}else{line.move(to:point)};previous=i};context.stroke(line,with:.color(.cyan),lineWidth:2);let x=Double(model.session.cursor)/Double(count)*size.width;var cursor=Path();cursor.move(to:CGPoint(x:x,y:0));cursor.addLine(to:CGPoint(x:x,y:size.height));context.stroke(cursor,with:.color(gold),lineWidth:1)}
            .background(Color(red:0.06,green:0.12,blue:0.2)).gesture(DragGesture(minimumDistance:0).onEnded{value in model.seek(max(0,min(model.session.study.moves,Int((value.location.x/geo.size.width*Double(count)).rounded()))))}).accessibilityLabel("評価グラフ。タップで局面へ移動")
    }}
}
/// Study navigation label; 次へ also shows how many alternatives the next move has.
struct NavLabel:View {
    let label:String
    let forks:Int
    var body:some View {VStack(spacing:1){Text(label);if forks>0{Text("◆分岐\(forks)通り").font(AppFonts.ui(9,bold:true)).foregroundStyle(gold)}}}
}
struct MoveListView:View {
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    @State private var expanded=Set<Int>()
    var title:String {model.session.studyLine.last?.offMain==true ? "手順（変化を表示中）":"手順（本譜）"}
    var body:some View {NavigationStack{ScrollViewReader{proxy in
        List {
            Section {Text("◆の手には分岐があります。下の「分岐」タブをタップすると展開・収納できます。「次へ」「最終」は表示中の手順を進み、分岐を選んでいない所では本譜（元の棋譜）を進みます。").font(AppFonts.style(12,.caption)).foregroundStyle(.secondary)}
            ForEach(model.session.studyLine){row in MoveLineEntry(row:row,expanded:binding(row.ply)){dismiss()}}
        }.listStyle(.plain).onAppear{proxy.scrollTo(model.session.cursor,anchor:.center)}
    }.navigationTitle(title).toolbar{Button("閉じる"){dismiss()}}}}
    func binding(_ ply:Int)->Binding<Bool> {
        Binding(get:{expanded.contains(ply)},set:{open in if open{expanded.insert(ply)}else{expanded.remove(ply)}})
    }
}
/// A move jumps to its ply; under a move with alternatives a pull tab expands or collapses its branches.
struct MoveLineEntry:View {
    @EnvironmentObject var model:AppModel
    let row:StudyTree.LineRow
    @Binding var expanded:Bool
    let close:()->Void
    var body:some View {
        Button{model.seek(row.ply);close()}label:{MoveLineRow(row:row)}.id(row.ply).accessibilityIdentifier("move_\(row.node)")
        if row.total>1 {
            BranchTab(row:row,expanded:$expanded).accessibilityIdentifier("branch_tab_\(row.ply)")
            if expanded {
                ForEach(model.session.studyAlternatives(row.node)){alt in BranchButton(alt:alt,close:close)}
            }
        }
    }
}
struct BranchTab:View {
    let row:StudyTree.LineRow
    @Binding var expanded:Bool
    var text:String {"分岐 \(row.total)通り（表示中："+StudyTree.branchName(row.branch)+"）"}
    var body:some View {
        Button{withAnimation(.easeInOut(duration:0.2)){expanded.toggle()}}label:{HStack(spacing:6){
            Image(systemName:expanded ? "chevron.down":"chevron.right").font(.caption.bold())
            Text(text).font(AppFonts.style(15,.subheadline,bold:true))
            Spacer()
            Text(expanded ? "収納":"展開").font(AppFonts.style(12,.caption))
        }.foregroundStyle(Color(red:0.54,green:0.23,blue:0.07)).padding(.horizontal,12).padding(.vertical,7)
         .background(Capsule().fill(expanded ? Color(red:0.97,green:0.79,blue:0.55):Color(red:0.99,green:0.91,blue:0.82)))
         .overlay(Capsule().stroke(Color(red:0.78,green:0.45,blue:0.18),lineWidth:1.5))}
        .buttonStyle(.plain).padding(.leading,28)
    }
}
struct BranchButton:View {
    @EnvironmentObject var model:AppModel
    let alt:StudyTree.Alternative
    let close:()->Void
    var text:String {
        let rest:String=alt.following==0 ? "続きなし":"続き\(alt.following)手"
        let shown:String=alt.shown ? "・表示中":""
        return StudyTree.branchName(alt.branch)+"　"+alt.label+"　"+rest+shown
    }
    var body:some View {Button{model.selectStudyNode(alt.node);close()}label:{Text("└ "+text).fontWeight(alt.shown ? .bold:.regular)}.padding(.leading,44).listRowBackground(alt.shown ? Color(red:0.98,green:0.89,blue:0.77):Color(red:1,green:0.96,blue:0.92)).accessibilityIdentifier("branch_\(alt.node)")}
}
/// A normal record row; a move with alternatives shows ◆ and a pull tab below it.
struct MoveLineRow:View {
    let row:StudyTree.LineRow
    var marker:String {row.total>1 ? "◆":row.offMain ? "┃":""}
    var body:some View {HStack(alignment:.top,spacing:6){
        Text(marker).font(.system(.body,design:.monospaced)).foregroundStyle(gold).frame(width:16)
        Text(row.label).fontWeight(row.total>1 ? .bold:.regular).foregroundStyle(.primary)
        Spacer();if row.current{Image(systemName:"checkmark.circle.fill").foregroundStyle(gold)}
    }.contentShape(Rectangle())}
}
struct ArchiveView:View {
    @EnvironmentObject var model:AppModel
    var body:some View {ScrollView{VStack(alignment:.leading,spacing:12){
        if model.records.isEmpty{Text("まだ棋譜がありません。取り込みや対局をすると自動保存されます。").padding()}
        if model.damagedRecords>0{Text("読み込めない棋譜が\(model.damagedRecords)件あります。").foregroundStyle(.orange)}
        ForEach(model.records){r in Button{if model.session.study.moves>0{model.archiveOffer=r}else{model.open(r)}}label:{VStack(alignment:.leading,spacing:7){Text("\(r.source=="match" ? "対局":"コピー") · \(r.title)").font(AppFonts.style(20,.title3,bold:true));Text("\(r.sente) 対 \(r.gote)");Text("\(r.moves)手 · \(r.result)");Text(r.created,style:.date).font(AppFonts.style(12,.caption))}.frame(maxWidth:.infinity,alignment:.leading).padding(16).foregroundStyle(Color(red:0.25,green:0.14,blue:0.07)).background(LinearGradient(colors:[cream,gold],startPoint:.leading,endPoint:.trailing)).clipShape(RoundedRectangle(cornerRadius:12))}.accessibilityIdentifier("record_\(r.id)")}
        TestAdSlot(placement:"archive")
        Text("端末内に保存されています。棋譜を選ぶと検討で開きます。").font(AppFonts.style(12,.caption)).foregroundStyle(.secondary)
    }.padding(16).frame(maxWidth:650).frame(maxWidth:.infinity)}}
}
struct PasteView:View {
    var onLoad:(String)->Void
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    @State private var text=""
    var body:some View{NavigationStack{VStack(alignment:.leading,spacing:18){Text("iOSのプライバシー保護に合わせ、下の貼り付けボタンから棋譜を取り込みます。").font(AppFonts.style(15,.subheadline));PasteButton(payloadType:String.self){values in if let first=values.first{text=first}}.accessibilityIdentifier("system_paste");TextEditor(text:$text).accessibilityIdentifier("kif_text").font(.system(.body,design:.monospaced)).overlay(RoundedRectangle(cornerRadius:8).stroke(gold.opacity(0.5)));Button("棋譜を読み込む"){onLoad(text)}.buttonStyle(.borderedProminent).disabled(text.isEmpty).accessibilityIdentifier("load_kif")}.padding().navigationTitle("棋譜を貼り付け").toolbar{Button("閉じる"){dismiss()}}}}
}
struct SettingsView:View {
    @EnvironmentObject var model:AppModel
    @EnvironmentObject var coffee:CoffeeStore
    @State private var coffeeSheet=false
    @Environment(\.dismiss) var dismiss
    @AppStorage("volume") private var volume=65.0
    @AppStorage("sound") private var sound=true
    @AppStorage("vibration") private var vibration=false
    @AppStorage("reduced_effects") private var reducedEffects=false
    @State private var licenses=false
    @ObservedObject private var adPrivacy=AdPrivacy.shared
    var body:some View{NavigationStack{Form{Section{Toggle("効果音（SE）",isOn:$sound).accessibilityIdentifier("sound");Text("効果音の音量 \(Int(volume))%");Slider(value:$volume,in:0...100,step:1).accessibilityIdentifier("volume");Button("効果音を試す"){model.previewSound()}.disabled(!sound || volume==0);Toggle("着手時のバイブレーション",isOn:$vibration).accessibilityIdentifier("vibration");Button("振動を試す"){UIImpactFeedbackGenerator(style:.light).impactOccurred()}.disabled(!vibration);Toggle("演出を減らす（低スペック端末向け）",isOn:$reducedEffects).accessibilityIdentifier("reduced_effects")}header:{Text("音・振動・演出の設定")}footer:{Text("設定は自動保存されます。音量・振動・消音は端末本体の設定にも従います。")};Section{Button(coffee.owned ? "☕ コーヒー応援・購入済み／復元":"☕ コーヒーを奢る・広告除去と最強AI"){coffeeSheet=true}.accessibilityIdentifier("coffee_settings")};Section{Text(AdPrivacy.testing ? "この版はGoogleの公式テスト広告のみを表示します。広告の取得で通信が発生します。本番配信・収益化は行いません。":"広告は年齢に配慮した内容に制限し、パーソナライズしません。広告の取得には通信が必要です。").font(AppFonts.style(12,.caption));if adPrivacy.optionsRequired{Button("広告のプライバシー設定"){adPrivacy.showOptions()}.accessibilityIdentifier("ad_privacy")}};Section{Button("ライセンスとAIについて"){licenses=true}}}.navigationTitle("設定").toolbar{Button("閉じる"){dismiss()}.accessibilityIdentifier("settings_close")}.sheet(isPresented:$coffeeSheet){CoffeeView()}.sheet(isPresented:$licenses){NavigationStack{ScrollView{Text(licenseText).font(.caption.monospaced()).padding()}.navigationTitle("ライセンス").toolbar{Button("閉じる"){licenses=false}}}}}}
    var licenseText:String {let header="やねうら王 9.80git / つようら王11micro\nエンジン GPL-3.0 / 評価関数 MIT\n対応ソースとビルド手順：https://github.com/555734/shogiKento\n\n";return header+["YaneuraOu-GPL-3.0","TsuyouraOu11micro-MIT","zenkakugothicnew-OFL"].compactMap{Bundle.main.url(forResource:$0,withExtension:"txt",subdirectory:"GameAssets/licenses").flatMap{try? String(contentsOf:$0,encoding:.utf8)}}.joined(separator:"\n\n")}
}

/// On-board promotion choice replacing the OS dialog: the promoted and the unpromoted piece side by side over the
/// destination. Tapping either commits it; tapping anywhere else on the board or komadai cancels the move.
struct PromotionPicker:View {
    @EnvironmentObject var model:AppModel
    let size:CGSize
    var body:some View {
        if let pro=model.promotion.first(where:{$0.promotes}),let plain=model.promotion.first(where:{!$0.promotes}),let source=pro.source {
            let geo=BoardGeometry(rect:CGRect(origin:.zero,size:size),viewSide:model.viewSide),cell=geo.cell,center=geo.center(pro.destination)
            let w=cell.width*1.3,h=cell.height*1.3,gap:CGFloat=6,total=w*2+gap,piece=model.position.board[source],sign=piece>0 ? 1:-1
            let left=max(size.width*0.01+2,min(size.width*0.99-2-total,center.x-total/2)),top=max(size.height*0.01+2,min(size.height*0.99-2-h-16,center.y-h/2))
            HStack(spacing:gap){
                option(sign*(abs(piece)+8),"成る",pro,width:w,height:h,primary:true).accessibilityIdentifier("promote_yes")
                option(piece,"成らない",plain,width:w,height:h,primary:false).accessibilityIdentifier("promote_no")
            }.padding(6).inkCard(radius:9)
            .position(x:left+total/2,y:top+h/2+8)
        }
    }
    func option(_ piece:Int,_ label:String,_ move:Move,width:CGFloat,height:CGFloat,primary:Bool)->some View {
        Button{model.commit(move)}label:{VStack(spacing:2){
            Image(uiImage:AppAssets.piece(piece)).resizable().scaledToFit().rotationEffect(.degrees(piece*model.viewSide<0 ? 180:0)).padding(4)
                .frame(width:width,height:height).background(RoundedRectangle(cornerRadius:5).fill(LinearGradient(colors:[Color(red:0.97,green:0.94,blue:0.86),primary ? Color(red:0.93,green:0.86,blue:0.75):Color(red:0.9,green:0.85,blue:0.76)],startPoint:.top,endPoint:.bottom)))
                .overlay(RoundedRectangle(cornerRadius:5).stroke(primary ? Color(red:0.78,green:0.25,blue:0.17):Color(red:0.66,green:0.53,blue:0.31),lineWidth:primary ? 1.8:1))
            Text(label).font(AppFonts.ui(11,bold:true)).foregroundStyle(primary ? Color(red:1,green:0.71,blue:0.63):Lacquer.cream)
        }}.buttonStyle(.plain)
    }
}

/// The previous move stays marked until the next one: its source square in blue, its destination in deep orange.
struct LastMoveMark:View {
    let destination:Bool
    var body:some View {
        Rectangle().fill(destination ? Color(red:1,green:0.55,blue:0.1).opacity(0.7):Color(red:0.16,green:0.48,blue:1).opacity(0.62))
            .overlay(Rectangle().strokeBorder(destination ? Color(red:0.82,green:0.35,blue:0):Color(red:0.18,green:0.44,blue:0.75),lineWidth:destination ? 2.5:1.2))
    }
}
