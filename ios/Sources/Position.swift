import Foundation

enum ShogiError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
struct Move: Hashable {
    let usi: String
    var chars: [Character] { Array(usi) }
    var drop: Int { chars.count >= 4 && chars[1] == "*" ? Position.names.firstIndex(of: chars[0]).map { $0+1 } ?? 0 : 0 }
    var destination: Int { let a=chars; return Int(String(a[2])).map { (Int(a[3].asciiValue!)-97)*9+9-$0 } ?? -1 }
    var source: Int? { guard drop == 0, chars.count >= 4, let f=Int(String(chars[0])), let r=chars[1].asciiValue else{return nil};return (Int(r)-97)*9+9-f }
    var promotes: Bool { usi.hasSuffix("+") }
    static func square(_ index: Int) -> String { "\(9-index%9)\(Character(UnicodeScalar(97+index/9)!))" }
}
struct Position: Codable, Equatable {
    static let initialSFEN="lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
    static let names=Array("PLNSGBRK")
    static let glyphs=["","歩","香","桂","銀","金","角","飛","玉","と","成香","成桂","成銀","","馬","龍"]
    var board: [Int], hands: [[Int]], side: Int, ply: Int
    init(_ sfen: String = Position.initialSFEN) throws {
        let f=sfen.split(separator:" ");guard f.count==4, f[1]=="b" || f[1]=="w", let p=Int(f[3]), p>0 else {throw ShogiError.message("局面の形式が不正です")}
        board=Array(repeating:0,count:81);hands=Array(repeating:Array(repeating:0,count:9),count:2);side=f[1]=="b" ? 1 : -1;ply=p-1
        let ranks=f[0].split(separator:"/",omittingEmptySubsequences:false);guard ranks.count==9 else{throw ShogiError.message("盤面は9段必要です")}
        for (r,rank) in ranks.enumerated(){var c=0,pro=false;for ch in rank {
            if ch=="+" {guard !pro else{throw ShogiError.message("成り表記が不正です")};pro=true;continue}
            if let n=ch.wholeNumberValue {guard n>0 && n<10 && !pro else{throw ShogiError.message("空きマスの数が不正です")};c+=n}
            else {guard c<9,let t=Self.names.firstIndex(of:Character(ch.uppercased())), !pro || t != 4 && t != 7 else{throw ShogiError.message("駒の表記が不正です")};board[r*9+c]=(ch.isUppercase ? 1 : -1)*(t+1+(pro ? 8:0));c+=1;pro=false}
            guard c<=9 else{throw ShogiError.message("盤面の幅が不正です")}
        };guard c==9 && !pro else{throw ShogiError.message("盤面の幅が不正です")}}
        if f[2] != "-" {var n=0;for ch in f[2]{if let v=ch.wholeNumberValue{n=n*10+v;guard n<=99 else{throw ShogiError.message("持ち駒が多すぎます")}}else{guard let t=Self.names.firstIndex(of:Character(ch.uppercased())) else{throw ShogiError.message("持ち駒が不正です")};let s=ch.isUppercase ? 0:1;guard hands[s][t+1]==0 else{throw ShogiError.message("持ち駒の重複")};hands[s][t+1]=max(1,n);n=0}};guard n==0 else{throw ShogiError.message("持ち駒の数が不正です")}}
    }
    var sfen: String {
        var ranks=[String]();for r in 0..<9{var line="",empty=0;for c in 0..<9{let v=board[r*9+c];if v==0{empty+=1;continue};if empty>0{line+="\(empty)";empty=0};if abs(v)>8{line+="+"};let ch=String(Self.names[Self.base(v)-1]);line+=v>0 ? ch:ch.lowercased()};if empty>0{line+="\(empty)"};ranks.append(line)}
        var h="";for s in 0..<2{for t in [7,6,5,4,3,2,1,8]{let n=hands[s][t];if n>0{if n>1{h+="\(n)"};let ch=String(Self.names[t-1]);h+=s==0 ? ch:ch.lowercased()}}};return ranks.joined(separator:"/")+" \(side==1 ? "b":"w") \(h.isEmpty ? "-":h) \(ply+1)"
    }
    var key: String { sfen.split(separator:" ").prefix(3).joined(separator:" ") }
    static func base(_ piece: Int)->Int {abs(piece)>8 ? abs(piece)-8:abs(piece)}
    func notation(_ move: Move)->String {let t=move.drop>0 ? move.drop:abs(board[move.source!]);return "\(side==1 ? "▲":"△")\(9-move.destination%9)\(Array("一二三四五六七八九")[move.destination/9])\(Self.glyphs[t])\(move.drop>0 ? "打":move.promotes ? "成":"")"}
    var analysisProblem: String? {
        guard board.filter({$0==8}).count==1,board.filter({$0 == -8}).count==1,hands[0][8]==0,hands[1][8]==0 else{return "盤上に先手・後手の玉を1枚ずつ配置してください"}
        let king=board.firstIndex(of:8)!,enemy=board.firstIndex(of:-8)!;if abs(king/9-enemy/9)<=1 && abs(king%9-enemy%9)<=1{return "玉が隣接しています"}
        let counts=(1...8).map{t in board.filter{Self.base($0)==t}.count+hands[0][t]+hands[1][t]};if zip(counts,[18,4,4,4,4,2,2,2]).contains(where:{$0.0>$0.1}){return "駒の枚数が多すぎます"}
        for s in [1,-1]{for c in 0..<9{if (0..<9).filter({board[$0*9+c]==s}).count>1{return "二歩の局面です"}};for i in 0..<81 where board[i]*s>0 {let t=abs(board[i]),r=i/9;if (t==1 || t==2) && r==(s==1 ? 0:8) || t==3 && (s==1 ? r<2:r>6){return "行き先のない駒があります"}}};return nil
    }
    mutating func freeMove(source: Int?,drop: Int,side mover: Int,to: Int,promote: Bool=false)->Bool {
        guard (0..<81).contains(to),mover==1 || mover == -1 else{return false};let slot=mover==1 ? 0:1
        if let from=source {guard (0..<81).contains(from),from != to,board[from] != 0,board[to]*board[from]<=0 else{return false};let v=board[from],captured=board[to];if captured != 0{hands[v>0 ? 0:1][Self.base(captured)]+=1};board[from]=0;board[to]=promote && abs(v)<=8 && Self.base(v) != 5 && Self.base(v) != 8 ? (v>0 ? 1 : -1)*(abs(v)+8):v;side=v>0 ? -1:1}
        else {guard (1...8).contains(drop),hands[slot][drop]>0,board[to]==0 else{return false};hands[slot][drop]-=1;board[to]=mover*drop;side = -mover};ply+=1;return true
    }
}
enum Rules {
    static func text(_ pointer: UnsafeMutablePointer<CChar>?) throws->String {guard let p=pointer else{throw ShogiError.message("局面または指し手が不正です")};defer{BKFree(p)};return String(cString:p)}
    static func legal(_ p: Position) throws->[Move] {if let error=p.analysisProblem{throw ShogiError.message(error)};return try text(BKLegal(p.sfen)).split(separator:" ").map{Move(usi:String($0))}}
    static func apply(_ p: Position,_ m: Move)throws->Position {try Position(text(BKApply(p.sfen,m.usi)))}
    static func checked(_ p: Position)->Bool {BKChecked(p.sfen)==1}
}
