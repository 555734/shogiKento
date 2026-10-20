import Foundation
import SwiftUI
import AVFoundation
import UIKit

@MainActor final class AppModel: ObservableObject {
    enum Screen: String {case home,study,match,archive}
    @Published var screen=Screen.home
    @Published var session=CurrentSession()
    @Published var position=try! Position()
    @Published var selected: Int?
    @Published var selectedHand: Int?
    @Published var selectedSide=1
    @Published var targets=[Move]()
    @Published var promotion=[Move]()
    @Published var candidates=[Candidate]()
    @Published private(set) var candidatePosition=""
    var boardCandidates:[Candidate] {screen == .study && candidatePosition==position.sfen ? Array(candidates.prefix(3)):[]}
    @Published var analysisStatus="局面を解析します"
    @Published var depth=0
    @Published var nodes=0
    @Published var analyzing=false
    @Published var thinking=false
    @Published var error:String?
    @Published var notice:String?
    @Published var importOffer:GameRecord?
    @Published var archiveOffer:GameRecord?
    @Published var records=[GameRecord]()
    @Published var damagedRecords=0
    @Published var freePlacement=UserDefaults.standard.bool(forKey:"free_placement")
    private(set) var store:RecordStore?
    private var active=true,lastTick:Double?,audio:AVAudioPlayer?
    private var matchSettingsOpen=false
    private(set) var soundPlayCount=0
    private let premium:@MainActor ()->Bool
    var matchOptions:MatchOptions {var options=session.match.matchOptions ?? MatchOptions();if options.level==10 && !premium(){options.level=9};return options}
    var humanSide:Int {matchOptions.humanSide}
    var cpuSide:Int {-humanSide}
    var currentRecord:GameRecord {screen == .match ? session.match:session.study}
    var ply:Int {screen == .match ? session.match.moves:session.cursor}
    var score:Evaluation? {session.scores[session.cursor]}
    var humanInput:Bool {active && !matchSettingsOpen && (screen == .study || screen == .match && session.matchStarted && session.winner==0 && position.side==humanSide && !thinking)}
    init(store testStore:RecordStore?=nil,premium:(@MainActor ()->Bool)? = nil){
        self.premium=premium ?? {CoffeeStore.shared.owned}
        do {let store=try testStore ?? RecordStore();self.store=store;if FileManager.default.fileExists(atPath:store.root.appendingPathComponent("current.json").path){session=try store.current()}}catch{self.error="保存データを読み込めません：\(error.localizedDescription)"}
        if let file=Bundle.main.url(forResource:"piece_place",withExtension:"wav",subdirectory:"GameAssets"){audio=try? AVAudioPlayer(contentsOf:file);audio?.prepareToPlay()}
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        if !session.matchStarted && session.match.moves==0 && session.match.matchOptions==nil{let options=MatchOptions.load();session.match = .match(options);session.clocks=[options.seconds,options.seconds]}
        NativeEngine.shared.warmUp()
    }
    func save(){do{guard let store else{throw ShogiError.message("保存先を利用できません")};if session.matchStarted || session.match.moves>0 || session.winner != 0{try store.save(session.match)};try store.saveCurrent(session)}catch{self.error="棋譜を保存できません：\(error.localizedDescription)"}}
    func enter(_ next:Screen){if screen == .match{tick()};NativeEngine.shared.cancel();thinking=false;analyzing=false;clearSelection();lastTick=nil;save();screen=next
        do {if next == .study{position=try Position(session.study.positions[session.cursor]);refreshAnalysis()}else if next == .match{position=try Position(session.match.positions.last!);if active{lastTick=ProcessInfo.processInfo.systemUptime;scheduleCPU()}}else if next == .archive{guard let store else{throw ShogiError.message("保存先を利用できません")};records=try store.list();damagedRecords=store.damaged}}catch{self.error=error.localizedDescription}
    }
    func sceneActive(_ value:Bool){if !value{tick()};active=value;lastTick=nil;if !value{NativeEngine.shared.cancel();thinking=false;analyzing=false;audio?.stop();save()}else if screen == .study{refreshAnalysis()}else if screen == .match{lastTick=ProcessInfo.processInfo.systemUptime;scheduleCPU()}}
    func tick(){guard active,!matchSettingsOpen,screen == .match,session.matchStarted,session.winner==0 else{lastTick=nil;return};let now=ProcessInfo.processInfo.systemUptime;defer{lastTick=now};guard let before=lastTick else{return};let side=position.side==1 ? 0:1;session.clocks[side]=max(0,session.clocks[side]-(now-before));if session.clocks[side]==0{finish(winner:-position.side,result:"時間切れ")}}
    func clearSelection(){selected=nil;selectedHand=nil;targets=[];promotion=[]}
    func selectHand(_ piece:Int,side:Int){guard humanInput,screen == .study && freePlacement || side==position.side else{clearSelection();return};if selectedHand==piece && selectedSide==side{clearSelection();return};selected=nil;selectedHand=piece;selectedSide=side;computeTargets()}
    func computeTargets(){
        if screen == .study && freePlacement{targets=(0..<81).compactMap{to in if let from=selected {guard from != to,position.board[to]*position.board[from]<=0 else{return nil};return Move(usi:Move.square(from)+Move.square(to))};guard let hand=selectedHand,position.board[to]==0 else{return nil};return Move(usi:String(Position.names[hand-1])+"*"+Move.square(to))};return}
        targets=((try? Rules.legal(position)) ?? []).filter{selectedHand != nil ? $0.drop==selectedHand:$0.source==selected && $0.drop==0}
    }
    func tap(_ square:Int){guard humanInput else{return};if selected==square{clearSelection();return}
        let moves=targets.filter{$0.destination==square}
        if !moves.isEmpty {
            if screen == .study && freePlacement {let from=selected,drop=selectedHand ?? 0,mover=selectedSide;var next=position;if next.freeMove(source:from,drop:drop,side:mover,to:square){appendStudy(next,label:position.notation(moves[0]));feedback(human:true)};clearSelection();return}
            if moves.count>1{promotion=moves;return};commit(moves[0]);return
        }
        let v=position.board[square];if v != 0 && (screen == .study && freePlacement || v*position.side>0){selected=square;selectedHand=nil;selectedSide=v>0 ? 1:-1;computeTargets()}else{clearSelection()}
    }
    func commit(_ move:Move){tick();guard humanInput else{return};do {let next=try Rules.apply(position,move);if screen == .match{appendMatch(next,label:position.notation(move),human:true);scheduleCPU()}else{appendStudy(next,label:position.notation(move));feedback(human:true)};clearSelection()}catch{self.error=error.localizedDescription}}
    func togglePromotion(_ square:Int){guard screen == .study,freePlacement else{return};let p=position.board[square],base=Position.base(p);guard p != 0,base != 5,base != 8 else{return};var next=position;next.board[square]=(p>0 ? 1:-1)*(abs(p)>8 ? base:base+8);next.ply+=1;appendStudy(next,label:"駒の成りを変更");clearSelection()}
    func setFree(_ value:Bool){freePlacement=value;UserDefaults.standard.set(value,forKey:"free_placement");clearSelection();refreshAnalysis()}
    func changeSide(){guard screen == .study else{return};position.side = -position.side;session.study.positions[session.cursor]=position.sfen;clearSelection();save();refreshAnalysis()}
    func appendStudy(_ next:Position,label:String){NativeEngine.shared.cancel();session.study.positions=Array(session.study.positions.prefix(session.cursor+1));session.study.labels=Array(session.study.labels.prefix(session.cursor+1));session.scores=session.scores.filter{$0.key<=session.cursor};session.study.positions.append(next.sfen);session.study.labels.append("\(session.cursor+1)手目  "+label);session.cursor+=1;position=next;save();refreshAnalysis()}
    func seek(_ index:Int){guard screen == .study,index != session.cursor,index>=0,index<session.study.positions.count else{return};do{let next=try Position(session.study.positions[index]);NativeEngine.shared.cancel();session.cursor=index;position=next;clearSelection();feedback(human:false);save();refreshAnalysis()}catch{self.error=error.localizedDescription}}
    func refreshAnalysis(){
        guard screen == .study else{return};NativeEngine.shared.cancel();candidates=[];candidatePosition="";depth=0;nodes=0;analyzing=false
        do {let legal=try Rules.legal(position);if legal.isEmpty{analysisStatus=Rules.checked(position) ? "\(position.side==1 ? "先手":"後手")の玉が詰みです・候補手なし":"合法手がありません";if Rules.checked(position){session.scores[session.cursor]=Evaluation(value:-position.side,mate:true);save()};return}
            guard active else{return};analyzing=true;analysisStatus="解析中…";let key=position.sfen,cursor=session.cursor
            NativeEngine.shared.search(position,continuous:true){[weak self] result in guard let self,self.active,self.screen == .study,self.position.sfen==key,self.session.cursor==cursor else{return};switch result{case .failure(let e):self.candidatePosition="";self.analyzing=false;self.analysisStatus="AIエラー："+e.localizedDescription;case .success(let update):guard update.candidates.allSatisfy({legal.contains($0.move)}) else{self.candidatePosition="";self.analyzing=false;self.analysisStatus="AI候補手を検証できません";NativeEngine.shared.cancel();return};self.candidates=update.candidates;self.candidatePosition=key;self.depth=update.depth;self.nodes=update.nodes;self.analyzing=true;self.analysisStatus="探索中";if let first=update.candidates.first{self.session.scores[cursor]=first.score}}}
        }catch{analysisStatus=error.localizedDescription}
    }
    var canShowCoffeeOffer:Bool {!matchSettingsOpen && promotion.isEmpty}
    func setMatchSettingsOpen(_ value:Bool){if value{tick();NativeEngine.shared.cancel();thinking=false};matchSettingsOpen=value;lastTick=nil;if !value && active && screen == .match{lastTick=ProcessInfo.processInfo.systemUptime;scheduleCPU()}}
    func applyMatchOptions(_ options:MatchOptions){guard options.valid(),options.level<10 || premium(),options != matchOptions else{return};options.save();restartMatch()}
    func membershipChanged(){if screen == .match{NativeEngine.shared.cancel();thinking=false;scheduleCPU()}}
    func startMatch(){guard screen == .match,!session.matchStarted else{return};session.matchStarted=true;lastTick=ProcessInfo.processInfo.systemUptime;save();scheduleCPU()}
    func restartMatch(){save();NativeEngine.shared.cancel();let options=MatchOptions.load();session.match = .match(options);session.clocks=[options.seconds,options.seconds];session.matchStarted=false;session.winner=0;session.checkingSides=[];position=try! Position();thinking=false;lastTick=nil;clearSelection();save()}
    func appendMatch(_ next:Position,label:String,human:Bool){let mover=position.side;session.match.positions.append(next.sfen);session.match.labels.append("\(session.match.moves)手目  "+label);session.match.updated=Date();position=next;session.checkingSides.append(Rules.checked(next) ? mover:0);feedback(human:human)
        if let legal=try? Rules.legal(next),legal.isEmpty{finish(winner:-next.side,result:Rules.checked(next) ? "詰み":"合法手なし");return}
        let keys=session.match.positions.compactMap{try? Position($0).key};let repetitions=keys.indices.filter{keys[$0]==next.key}
        if repetitions.count>=4{let start=repetitions[repetitions.count-4];for side in [1,-1]{let indices=(start..<session.checkingSides.count).filter{($0%2==0 ? 1:-1)==side};if !indices.isEmpty && indices.allSatisfy({session.checkingSides[$0]==side}){finish(winner:-side,result:"連続王手の千日手");return}};finish(winner:2,result:"千日手・引き分け");return};save()
    }
    func finish(winner:Int,result:String){session.winner=winner;session.match.result=(winner==2 ? "引き分け":winner==1 ? "先手勝ち":"後手勝ち")+"・"+result;session.match.updated=Date();thinking=false;NativeEngine.shared.cancel();clearSelection();save()}
    func resign(){tick();if session.winner==0 && session.matchStarted{finish(winner:-humanSide,result:"投了")}}
    func scheduleCPU(){guard screen == .match,active,!matchSettingsOpen,session.matchStarted,session.winner==0,position.side == cpuSide,!thinking else{return};thinking=true;let key=position.sfen
        NativeEngine.shared.search(position,continuous:false,nodes:matchOptions.nodes,milliseconds:max(1,min(matchOptions.milliseconds,Int(session.clocks[cpuSide==1 ? 0:1]*1000)))){[weak self] result in guard let self,self.active,self.screen == .match,self.position.sfen==key,self.session.winner==0 else{return};self.tick();guard self.session.winner==0 else{return};switch result{case .failure(let e):self.thinking=false;self.error="AIエラー："+e.localizedDescription;case .success(let update):guard let best=update.best else{return};self.thinking=false;if best=="resign"{self.finish(winner:self.humanSide,result:"投了");return};do{let move=Move(usi:best),next=try Rules.apply(self.position,move);self.appendMatch(next,label:self.position.notation(move),human:false)}catch{self.error="AIの指し手が不正です："+error.localizedDescription}}}
    }
    func parseClipboard(_ text:String){Task{do{let record=try await Task.detached(priority:.userInitiated){try KifParser.parse(text)}.value;importOffer=record}catch{self.error=error.localizedDescription}}}
    func acceptImport(_ record:GameRecord){do{guard let store else{throw ShogiError.message("保存先を利用できません")};try store.save(record);open(record)}catch{self.error="棋譜を保存できません："+error.localizedDescription}}
    func open(_ record:GameRecord){do{try record.validate();let next=try Position(record.positions.last!);if screen == .match{tick()};NativeEngine.shared.cancel();save();session.study=record;session.cursor=record.moves;session.scores=[:];position=next;screen = .study;freePlacement=false;UserDefaults.standard.set(false,forKey:"free_placement");clearSelection();save();refreshAnalysis()}catch{self.error="棋譜を開けません："+error.localizedDescription}}
    func feedback(human:Bool){guard active else{return};if UserDefaults.standard.object(forKey:"sound")==nil || UserDefaults.standard.bool(forKey:"sound"){let gain=UserDefaults.standard.object(forKey:"volume")==nil ? 65:UserDefaults.standard.double(forKey:"volume");if gain>0{audio?.volume=Float(gain/100);audio?.currentTime=0;if audio?.play()==true{soundPlayCount+=1}}};if human && UserDefaults.standard.bool(forKey:"vibration"){UIImpactFeedbackGenerator(style:.light).impactOccurred()}}
    func previewSound(){feedback(human:false)}
}
