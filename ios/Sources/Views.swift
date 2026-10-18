import SwiftUI
import UIKit

private let gold=Color(red:1,green:0.86,blue:0.59),cream=Color(red:1,green:0.94,blue:0.81),burgundy=Color(red:0.25,green:0.04,blue:0.06)
struct RootView:View {
    @EnvironmentObject var model:AppModel
    @State private var settings=false
    @State private var paste=false
    @State private var moves=false
    @State private var pendingPaste:String?
    private let timer=Timer.publish(every:0.1,on:.main,in:.common).autoconnect()
    var body: some View {
        NavigationStack {
            Group {switch model.screen{case .home:HomeView(settings:$settings,paste:$paste);case .archive:ArchiveView();case .study,.match:BoardScreen(showMoves:$moves)}}
                .background(burgundy.ignoresSafeArea()).navigationTitle(model.screen == .home ? "":model.screen == .study ? "検討モード":model.screen == .match ? "対局モード":"過去の棋譜")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {if model.screen != .home{ToolbarItem(placement:.navigationBarLeading){Button("ホーム"){model.enter(.home)}.accessibilityIdentifier("home")}}}
        }
        .tint(gold).onReceive(timer){_ in model.tick()}
        .sheet(isPresented:$settings){SettingsView()}.sheet(isPresented:$paste,onDismiss:{if let text=pendingPaste{pendingPaste=nil;model.parseClipboard(text)}}){PasteView{pendingPaste=$0;paste=false}}.sheet(isPresented:$moves){MoveListView()}
        .alert("棋譜の取り込み",isPresented:Binding(get:{model.importOffer != nil},set:{if !$0{model.importOffer=nil}})){Button("キャンセル",role:.cancel){model.importOffer=nil};Button("検討する"){if let r=model.importOffer{model.importOffer=nil;paste=false;model.acceptImport(r)}}}message:{Text("コピーされた棋譜を検討しますか？\(model.session.study.moves>0 ? "現在の検討内容を置き換えます。保存済みの棋譜は残ります。":"")")}
        .alert("検討棋譜の置き換え",isPresented:Binding(get:{model.archiveOffer != nil},set:{if !$0{model.archiveOffer=nil}})){Button("キャンセル",role:.cancel){model.archiveOffer=nil};Button("開く"){if let r=model.archiveOffer{model.archiveOffer=nil;model.open(r)}}}message:{Text("保存した棋譜を検討で開きますか？現在の検討内容を置き換えます。")}
        .alert("エラー",isPresented:Binding(get:{model.error != nil},set:{if !$0{model.error=nil}})){Button("閉じる"){model.error=nil}}message:{Text(model.error ?? "")}
        .confirmationDialog("成りますか？",isPresented:Binding(get:{!model.promotion.isEmpty},set:{if !$0{model.promotion=[]}}),titleVisibility:.visible){Button("成る"){if let move=model.promotion.first(where:{$0.promotes}){model.commit(move)}};Button("成らない"){if let move=model.promotion.first(where:{!$0.promotes}){model.commit(move)}};Button("キャンセル",role:.cancel){model.clearSelection()}}
    }
}
struct HomeView:View {
    @EnvironmentObject var model:AppModel
    @Binding var settings:Bool
    @Binding var paste:Bool
    var body: some View {
        GeometryReader {geo in
            ScrollView {VStack(spacing:15){
                Image(uiImage:AppAssets.image("home_title")).resizable().scaledToFit().frame(height:max(140,min(240,geo.size.height*0.29))).padding(.top,32).accessibilityLabel("紅将棋3分")
                Spacer(minLength:15)
                Button{model.enter(.study)}label:{HStack(spacing:22){Image(systemName:"circle.dashed").font(.system(size:65,weight:.thin));VStack(alignment:.leading,spacing:15){Text("検討モード").font(.system(size:34,weight:.bold,design:.serif));Divider().overlay(gold);Text("合法手で検討・自由配置にも切替可").font(.system(size:12,design:.serif))}}.padding(24).frame(maxWidth:.infinity,minHeight:145).background(LinearGradient(colors:[Color(red:0.7,green:0.03,blue:0.04),burgundy],startPoint:.topLeading,endPoint:.bottomTrailing))}.buttonStyle(HomeCard()).accessibilityIdentifier("mode_study")
                Button{model.enter(.match)}label:{HStack(spacing:22){Image(uiImage:AppAssets.piece(8)).resizable().scaledToFit().frame(width:56,height:66);VStack(alignment:.leading,spacing:8){Text("対局モード").font(.system(size:26,weight:.bold,design:.serif));Text("CPUと3分切れ負け").font(.system(size:14,design:.serif))};Spacer()}.padding(.horizontal,22).padding(.vertical,12).frame(maxWidth:.infinity).background(Color(red:0.12,green:0.09,blue:0.06))}.buttonStyle(HomeCard()).accessibilityIdentifier("mode_match")
                Button{paste=true}label:{Label("棋譜を貼り付け",systemImage:"doc.text").font(.system(size:23,weight:.bold,design:.serif)).frame(maxWidth:.infinity,minHeight:60).background(Color.black.opacity(0.83))}.buttonStyle(HomeCard()).accessibilityIdentifier("open_paste")
                Button{model.enter(.archive)}label:{Label("過去の棋譜",systemImage:"clock.arrow.circlepath").font(.system(size:22,weight:.bold,design:.serif)).frame(maxWidth:.infinity,minHeight:56).background(Color.black.opacity(0.83))}.buttonStyle(HomeCard()).accessibilityIdentifier("open_archive")
                Spacer(minLength:35)
            }.padding(.horizontal,20).frame(maxWidth:560,minHeight:geo.size.height).frame(maxWidth:.infinity)}
                .background(Image(uiImage:AppAssets.image("home_background")).resizable().scaledToFill().ignoresSafeArea())
                .overlay(alignment:.topTrailing){Button{settings=true}label:{VStack(spacing:2){Image(systemName:"gearshape").font(.title3);Text("設定").font(.caption2)}.frame(width:48,height:48).background(burgundy.opacity(0.8)).clipShape(RoundedRectangle(cornerRadius:9)).overlay(RoundedRectangle(cornerRadius:9).stroke(gold.opacity(0.7)))}.accessibilityIdentifier("settings").padding(12)}
        }
    }
}
struct HomeCard:ButtonStyle {func makeBody(configuration:Configuration)->some View {configuration.label.foregroundStyle(cream).clipShape(RoundedRectangle(cornerRadius:14)).overlay(RoundedRectangle(cornerRadius:14).stroke(LinearGradient(colors:[cream,Color.brown,gold],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1.5)).shadow(color:gold.opacity(configuration.isPressed ? 0.6:0.18),radius:5).scaleEffect(configuration.isPressed ? 0.98:1)}}
struct BoardScreen:View {
    @EnvironmentObject var model:AppModel
    @Binding var showMoves:Bool
    @State private var resign=false
    @State private var restart=false
    var body:some View {
        ScrollView {VStack(spacing:8){
            HandView(side:-1)
            BoardView().aspectRatio(1226/1283.0,contentMode:.fit)
            HandView(side:1)
            if model.screen == .match {
                Text(model.session.winner != 0 ? model.session.match.result:!model.session.matchStarted ? "対局を開始してください":model.thinking ? "CPUの手番":"あなたの手番").accessibilityIdentifier("match_status")
                Text("\(model.session.match.moves)手目").accessibilityIdentifier("match_ply")
                if !model.session.matchStarted{Button("対局開始"){model.startMatch()}.buttonStyle(.borderedProminent).accessibilityIdentifier("start_match")}
                HStack {Button("やり直し"){restart=true};Spacer();Button("AI再試行"){model.scheduleCPU()}.disabled(!model.thinking && model.position.side==1);Spacer();Button("投了"){resign=true}.disabled(!model.session.matchStarted || model.session.winner != 0)}.buttonStyle(.bordered).padding(.horizontal)
                Picker("AIの負荷",selection:Binding(get:{let v=UserDefaults.standard.integer(forKey:"ai_nodes");return v==0 ? 50000:v},set:{UserDefaults.standard.set($0,forKey:"ai_nodes")})){Text("低負荷").tag(3000);Text("標準").tag(10000);Text("強い").tag(50000)}.pickerStyle(.segmented).padding()
            }else{AnalysisView(showMoves:$showMoves)}
        }.frame(maxWidth:600).frame(maxWidth:.infinity).padding(.bottom,15)}
        .background(Image(uiImage:AppAssets.image("background")).resizable().scaledToFill().ignoresSafeArea())
        .alert("投了しますか？",isPresented:$resign){Button("キャンセル",role:.cancel){};Button("投了",role:.destructive){model.resign()}}
        .alert("対局をやり直しますか？",isPresented:$restart){Button("キャンセル",role:.cancel){};Button("やり直す",role:.destructive){model.restartMatch()}}
    }
}
struct BoardView:View {
    @EnvironmentObject var model:AppModel
    var body:some View {
        GeometryReader {geo in let w=geo.size.width,h=geo.size.height
            ZStack {Image(uiImage:AppAssets.image("board")).resizable()
                Canvas {context,size in var grid=Path();for i in 0...9{let x=size.width*(0.01+0.98*Double(i)/9),y=size.height*(0.01+0.98*Double(i)/9);grid.move(to:CGPoint(x:x,y:size.height*0.01));grid.addLine(to:CGPoint(x:x,y:size.height*0.99));grid.move(to:CGPoint(x:size.width*0.01,y:y));grid.addLine(to:CGPoint(x:size.width*0.99,y:y))};context.stroke(grid,with:.color(Color(red:0.30,green:0.15,blue:0.03)),lineWidth:0.8)}
                VStack(spacing:0){ForEach(0..<9,id:\.self){r in HStack(spacing:0){ForEach(0..<9,id:\.self){c in let square=r*9+c,piece=model.position.board[square],target=model.targets.contains{$0.destination==square}
                    Button{model.tap(square)}label:{ZStack{if model.selected==square{Color.blue.opacity(0.4)}else if target{Color.yellow.opacity(0.5)};if piece != 0{Image(uiImage:AppAssets.piece(piece)).resizable().scaledToFit().rotationEffect(.degrees(piece<0 ? 180:0)).padding(0.7)}}.frame(maxWidth:.infinity,maxHeight:.infinity).contentShape(Rectangle())}.buttonStyle(.plain).frame(width:w*0.98/9,height:h*0.98/9)
                        .accessibilityIdentifier("cell_\(r)_\(c)").accessibilityLabel("\(9-c)\(Array("一二三四五六七八九")[r]) \(Position.glyphs[abs(piece)])").accessibilityValue(model.selected==square ? "選択":target ? "移動可能":"")
                        .onLongPressGesture {model.togglePromotion(square)}
                }}}}.padding(.horizontal,w*0.01).padding(.vertical,h*0.01)
            }
        }
    }
}
struct HandView:View {
    @EnvironmentObject var model:AppModel
    let side:Int
    var body:some View {
        HStack(spacing:5){VStack(alignment:.leading,spacing:3){Text(model.screen == .match ? (side==1 ? "あなた・先手":"CPU・後手"):(side==1 ? "先手":"後手")).font(.caption);if model.screen == .match{let time=max(0,Int(ceil(model.session.clocks[side==1 ? 0:1])));Text(String(format:"%02d:%02d",time/60,time%60)).font(.system(.title3,design:.monospaced))}else{Text("検討・時計なし").font(.caption2)}}.frame(minWidth:90,alignment:.leading)
            Spacer(minLength:0)
            ForEach(1..<(model.screen == .study && model.freePlacement ? 9:8),id:\.self){p in let n=model.position.hands[side==1 ? 0:1][p];if n>0{Button{model.selectHand(p,side:side)}label:{ZStack(alignment:.bottomTrailing){Image(uiImage:AppAssets.piece(p*side)).resizable().scaledToFit().rotationEffect(.degrees(side<0 ? 180:0));Text("\(n)").font(.caption.bold()).foregroundStyle(.white).padding(2).background(.black.opacity(0.7))}.frame(maxWidth:36,maxHeight:40).background(model.selectedHand==p && model.selectedSide==side ? .yellow.opacity(0.4):.clear)}.buttonStyle(.plain).accessibilityIdentifier("hand_\(side)_\(p)")}}
        }.foregroundStyle(cream).padding(.horizontal,8).frame(height:48).background(.black.opacity(0.72))
    }
}
struct AnalysisView:View {
    @EnvironmentObject var model:AppModel
    @Binding var showMoves:Bool
    var body:some View {VStack(alignment:.leading,spacing:12){
        HStack {Text(model.score.map{String(format:"先手 %.1f%%",$0.percentage)} ?? "先手 ―").foregroundStyle(.blue);Spacer();Text(model.score.map{String(format:"後手 %.1f%%",100-$0.percentage)} ?? "後手 ―").foregroundStyle(.red)}.font(.title3.bold())
        Text("形勢の目安 · 深さ \(model.depth) / \(model.nodes)ノード").font(.caption2).foregroundStyle(.secondary)
        HStack {Menu(model.freePlacement ? "自由配置 ▾":"合法手 ▾"){Button("通常移動（合法手のみ）"){model.setFree(false)};Button("自由配置"){model.setFree(true)}}.accessibilityIdentifier("study_operation");Spacer();Button("解析：\(model.position.side==1 ? "先手":"後手") ▾"){model.changeSide()}}
        if model.candidates.isEmpty{Text(model.analysisStatus).font(.subheadline).accessibilityIdentifier("analysis_status");if !model.analyzing{Button("解析を再試行"){model.refreshAnalysis()}}}
        else{ForEach(Array(model.candidates.enumerated()),id:\.offset){index,c in HStack{Text("\(index+1). \(model.position.notation(c.move))");Spacer();Text(c.score.text)}.font(.subheadline).accessibilityElement(children:.combine).accessibilityIdentifier("candidate_\(index)")};Text(model.analysisStatus).font(.caption2).accessibilityIdentifier("analysis_status")}
        HStack {Text("評価値の推移（先手基準）");Spacer();Text("\(model.session.cursor)手目").accessibilityIdentifier("study_ply")}.font(.caption)
        EvaluationGraph().frame(height:85)
        HStack(spacing:5){ForEach(Array(["初期","前へ","次へ","最終","手順"].enumerated()),id:\.offset){i,label in Button(label){switch i{case 0:model.seek(0);case 1:model.seek(model.session.cursor-1);case 2:model.seek(model.session.cursor+1);case 3:model.seek(model.session.study.moves);default:showMoves=true}}.font(.subheadline).frame(maxWidth:.infinity).buttonStyle(.bordered).accessibilityIdentifier("ply_\(i)")}}
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
struct MoveListView:View {
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    var body:some View{NavigationStack{List(Array(model.session.study.labels.enumerated()),id:\.offset){i,label in Button{model.seek(i);dismiss()}label:{HStack{Text(label);Spacer();if i==model.session.cursor{Image(systemName:"checkmark")}}}.accessibilityIdentifier("move_\(i)")}.navigationTitle(model.session.study.title).toolbar{Button("閉じる"){dismiss()}}}}
}
struct ArchiveView:View {
    @EnvironmentObject var model:AppModel
    var body:some View {ScrollView{VStack(alignment:.leading,spacing:12){
        if model.records.isEmpty{Text("まだ棋譜がありません。取り込みや対局をすると自動保存されます。").padding()}
        if model.damagedRecords>0{Text("読み込めない棋譜が\(model.damagedRecords)件あります。").foregroundStyle(.orange)}
        ForEach(model.records){r in Button{if model.session.study.moves>0{model.archiveOffer=r}else{model.open(r)}}label:{VStack(alignment:.leading,spacing:7){Text("\(r.source=="match" ? "対局":"コピー") · \(r.title)").font(.title3.bold());Text("\(r.sente) 対 \(r.gote)");Text("\(r.moves)手 · \(r.result)");Text(r.created,style:.date).font(.caption)}.frame(maxWidth:.infinity,alignment:.leading).padding(16).foregroundStyle(Color(red:0.25,green:0.14,blue:0.07)).background(LinearGradient(colors:[cream,gold],startPoint:.leading,endPoint:.trailing)).clipShape(RoundedRectangle(cornerRadius:12))}.accessibilityIdentifier("record_\(r.id)")}
        Text("端末内に保存されています。棋譜を選ぶと検討で開きます。").font(.caption).foregroundStyle(.secondary)
    }.padding(16).frame(maxWidth:650).frame(maxWidth:.infinity)}}
}
struct PasteView:View {
    var onLoad:(String)->Void
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    @State private var text=""
    var body:some View{NavigationStack{VStack(alignment:.leading,spacing:18){Text("iOSのプライバシー保護に合わせ、下の貼り付けボタンから棋譜を取り込みます。").font(.subheadline);PasteButton(payloadType:String.self){values in if let first=values.first{text=first}}.accessibilityIdentifier("system_paste");TextEditor(text:$text).accessibilityIdentifier("kif_text").font(.system(.body,design:.monospaced)).overlay(RoundedRectangle(cornerRadius:8).stroke(gold.opacity(0.5)));Button("棋譜を読み込む"){onLoad(text)}.buttonStyle(.borderedProminent).disabled(text.isEmpty).accessibilityIdentifier("load_kif")}.padding().navigationTitle("棋譜を貼り付け").toolbar{Button("閉じる"){dismiss()}}}}
}
struct SettingsView:View {
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    @AppStorage("volume") private var volume=65.0
    @AppStorage("sound") private var sound=true
    @AppStorage("vibration") private var vibration=false
    @State private var licenses=false
    var body:some View{NavigationStack{Form{Section{Toggle("効果音（SE）",isOn:$sound).accessibilityIdentifier("sound");Text("効果音の音量 \(Int(volume))%");Slider(value:$volume,in:0...100,step:1).accessibilityIdentifier("volume");Button("効果音を試す"){model.previewSound()}.disabled(!sound || volume==0);Toggle("着手時のバイブレーション",isOn:$vibration).accessibilityIdentifier("vibration");Button("振動を試す"){UIImpactFeedbackGenerator(style:.light).impactOccurred()}.disabled(!vibration)}header:{Text("音と振動の設定")}footer:{Text("設定は自動保存されます。音量・振動・消音は端末本体の設定にも従います。")};Section{Button("ライセンスとAIについて"){licenses=true}}}.navigationTitle("設定").toolbar{Button("閉じる"){dismiss()}}.sheet(isPresented:$licenses){NavigationStack{ScrollView{Text(licenseText).font(.caption.monospaced()).padding()}.navigationTitle("ライセンス").toolbar{Button("閉じる"){licenses=false}}}}}}
    var licenseText:String {let header="やねうら王 9.80git / つようら王11micro\nエンジン GPL-3.0 / 評価関数 MIT\n対応ソースとビルド手順：https://github.com/555734/shogiKento\n\n";return header+["YaneuraOu-GPL-3.0","TsuyouraOu11micro-MIT"].compactMap{Bundle.main.url(forResource:$0,withExtension:"txt",subdirectory:"GameAssets/licenses").flatMap{try? String(contentsOf:$0,encoding:.utf8)}}.joined(separator:"\n\n")}
}
