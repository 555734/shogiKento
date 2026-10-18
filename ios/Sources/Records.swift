import Foundation
import CryptoKit

struct GameRecord: Codable, Identifiable, Equatable {
    var id=UUID().uuidString,source="match",title="3分切れ負け",sente="あなた",gote="CPU",result="対局途中",original=""
    var created=Date(),updated=Date(),positions=[Position.initialSFEN],labels=["開始局面"]
    var moves: Int { positions.count-1 }
    static func fingerprint(_ text: String)->String {SHA256.hash(data:Data(text.utf8)).map{String(format:"%02x",$0)}.joined()}
    func validate() throws {guard !positions.isEmpty,positions.count<=20000,positions.count==labels.count,id.range(of:"^[a-zA-Z0-9-]{1,100}$",options:.regularExpression) != nil else{throw ShogiError.message("保存された棋譜の形式が不正です")};for s in positions{_ = try Position(s)}}
}
final class RecordStore {
    let root: URL
    private(set) var damaged=0
    init(root: URL?=nil)throws {
        self.root=try root ?? FileManager.default.url(for:.applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true).appendingPathComponent("BeniShogi",isDirectory:true)
        try FileManager.default.createDirectory(at:self.root,withIntermediateDirectories:true)
        var url=self.root;var values=URLResourceValues();values.isExcludedFromBackup=true;try url.setResourceValues(values)
    }
    func recordURL(_ id:String)throws->URL {guard id.range(of:"^[a-zA-Z0-9-]{1,100}$",options:.regularExpression) != nil else{throw ShogiError.message("棋譜IDが不正です")};return root.appendingPathComponent(id+".record.json")}
    func save(_ record: GameRecord)throws {try record.validate();let url=try recordURL(record.id);if let old=try? read(record.id), old.positions==record.positions && old.labels==record.labels && old.result==record.result && old.original==record.original{return};let data=try JSONEncoder().encode(record);try data.write(to:url,options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])}
    func read(_ id:String)throws->GameRecord {let url=try recordURL(id);let size=(try url.resourceValues(forKeys:[.fileSizeKey])).fileSize ?? 0;guard size<8*1024*1024 else{throw ShogiError.message("棋譜ファイルが大きすぎます")};let record=try JSONDecoder().decode(GameRecord.self,from:Data(contentsOf:url));try record.validate();guard record.id==id else{throw ShogiError.message("棋譜IDが一致しません")};return record}
    func list()throws->[GameRecord] {damaged=0;return try FileManager.default.contentsOfDirectory(at:root,includingPropertiesForKeys:nil).filter{$0.lastPathComponent.hasSuffix(".record.json")}.compactMap{url in do{return try read(String(url.lastPathComponent.dropLast(12)))}catch{damaged+=1;return nil}}.sorted{$0.updated>$1.updated}}
    func saveCurrent(_ current: CurrentSession)throws {try JSONEncoder().encode(current).write(to:root.appendingPathComponent("current.json"),options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])}
    func current()throws->CurrentSession {let current=try JSONDecoder().decode(CurrentSession.self,from:Data(contentsOf:root.appendingPathComponent("current.json")));try current.match.validate();try current.study.validate();guard current.cursor>=0,current.cursor<current.study.positions.count,current.clocks.count==2 else{throw ShogiError.message("保存された局面が不正です")};return current}
}
struct CurrentSession: Codable {
    var match=GameRecord(),study: GameRecord={var r=GameRecord();r.source="study";r.title="検討の手順";return r}()
    var cursor=0,matchStarted=false,winner=0
    var clocks=[180.0,180.0]
    var checkingSides=[Int]()
    var scores=[Int:Evaluation]()
}
enum KifParser {
    static let ranks=Array("一二三四五六七八九"),names=["歩":1,"香":2,"桂":3,"銀":4,"金":5,"角":6,"飛":7,"玉":8,"王":8,"と":9,"成香":10,"杏":10,"成桂":11,"圭":11,"成銀":12,"全":12,"馬":14,"龍":15,"竜":15]
    static func groups(_ pattern:String,_ text:String)->[String]? {guard let regex=try? NSRegularExpression(pattern:pattern),let hit=regex.firstMatch(in:text,range:NSRange(text.startIndex...,in:text)) else{return nil};return (0..<hit.numberOfRanges).map{Range(hit.range(at:$0),in:text).map{String(text[$0])} ?? ""}}
    static func parse(_ original:String)throws->GameRecord {
        guard original.count<=262144 else{throw ShogiError.message("棋譜が大きすぎます（最大256K文字）")}
        let text=original.precomposedStringWithCompatibilityMapping.replacingOccurrences(of:"\r\n",with:"\n").replacingOccurrences(of:"\r",with:"\n").replacingOccurrences(of:"\u{feff}",with:"").replacingOccurrences(of:"\\\\[ \\t]*(?=\\n|$)",with:"",options:.regularExpression)
        var record=GameRecord();record.id="kif-"+GameRecord.fingerprint(text);record.source="import";record.title="コピーした棋譜";record.sente="先手";record.gote="後手";record.result="記載なし";record.original=text
        var position=try Position(),previous: Int?,next=1,ended=false
        for raw in text.components(separatedBy:.newlines) {
            let line=raw.trimmingCharacters(in:.whitespacesAndNewlines);if line.isEmpty || line.hasPrefix("#") || line.hasPrefix("*") || line.hasPrefix("手数") || line.hasPrefix("まで"){continue}
            if ["変化","後手番","先手の持駒","後手の持駒","|"].contains(where:line.hasPrefix){throw ShogiError.message("平手の本譜のみ対応しています（変化・途中配置は未対応）")}
            if line.first?.isNumber != true,let header=groups("^([^:]+)\\s*:\\s*(.*)$",line){switch header[1].trimmingCharacters(in:.whitespaces){case "手合割":guard header[2]=="平手" else{throw ShogiError.message("手合割は平手のみ対応しています")};case "棋戦":record.title=header[2];case "先手":record.sente=header[2];case "後手":record.gote=header[2];default:break};continue}
            guard let row=groups("^(\\d+)\\s+(.+)$",line),let number=Int(row[1]),number==next else{throw ShogiError.message("\(next)手目：手数または棋譜の行が不正です")}
            guard !ended else{throw ShogiError.message("\(number)手目：終局後に指し手があります")}
            let body=row[2].replacingOccurrences(of:"\\s*\\(\\s*\\d+\\s*:.*$",with:"",options:.regularExpression).replacingOccurrences(of:"\\s+",with:"",options:.regularExpression)
            if ["投了","中断","千日手","持将棋","詰み","切れ負け","時間切れ","反則負け","反則勝ち","入玉勝ち","不戦勝","不戦敗","トライ"].contains(body){record.result=body;ended=true;next+=1;continue}
            guard number<=4096 else{throw ShogiError.message("最大4096手まで対応しています")}
            var moveBody=body;if let first=moveBody.first,["▲","△","☗","☖"].contains(String(first)){let side=["▲","☗"].contains(String(first)) ? 1 : -1;guard side==position.side else{throw ShogiError.message("\(number)手目：先後の記号が手番と一致しません")};moveBody.removeFirst()}
            guard let m=groups("^([1-9][一二三四五六七八九1-9]|同)(成香|成桂|成銀|歩|香|桂|銀|金|角|飛|玉|王|と|杏|圭|全|馬|龍|竜)(不成|成|打)?(?:\\(([0-9])([0-9])\\))?$",moveBody),let type=names[m[2]] else{throw ShogiError.message("\(number)手目：対応していない指し手です")}
            let dest: Int;if m[1]=="同"{guard let p=previous else{throw ShogiError.message("\(number)手目：「同」の直前の着手がありません")};dest=p}else{let a=Array(m[1]);let r=a[1].wholeNumberValue.map{$0-1} ?? ranks.firstIndex(of:a[1])!;dest=r*9+9-a[0].wholeNumberValue!}
            var source: Int?,drop=m[3]=="打";if !m[4].isEmpty{let f=Int(m[4])!,r=Int(m[5])!;if f==0 && r==0{drop=true}else{guard f>0,r>0 else{throw ShogiError.message("\(number)手目：移動元が不正です")};source=(r-1)*9+9-f}}
            guard !(drop && (source != nil || type>7 || m[3]=="成" || m[3]=="不成")) else{throw ShogiError.message("\(number)手目：駒打ちの表記が不正です")}
            if !drop && !m[3].isEmpty && (type>8 || type==5 || type==8){throw ShogiError.message("\(number)手目：成りの表記が不正です")}
            let candidates=try Rules.legal(position).filter{candidate in candidate.destination==dest && candidate.promotes==(m[3]=="成") && (drop ? candidate.drop==type:candidate.drop==0 && candidate.source.map{abs(position.board[$0])==type && (source==nil || source==$0)}==true)}
            guard candidates.count==1 else{throw ShogiError.message("\(number)手目：\(candidates.isEmpty ? "合法な指し手ではありません":"移動元を特定できません")：\(moveBody)")}
            position=try Rules.apply(position,candidates[0]);record.positions.append(position.sfen);record.labels.append("\(number)手目  \(moveBody)");previous=dest;next+=1
        }
        guard record.moves>0 else{throw ShogiError.message("読み込める指し手がありません")};if try Rules.legal(position).isEmpty,Rules.checked(position){record.result="詰み"};return record
    }
}
