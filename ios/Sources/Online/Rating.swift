import Foundation

/// Elo ratings for online random matches, identical to Android's Rating: start 1500, K 40 for the first 10
/// (provisional) games, 24 until 30 games, then 16; differences beyond 800 count as 800; floor(x+0.5) rounding.
enum Rating {
    static let initial=1500,floor=100,ceiling=4000,provisionalGames=10
    static func k(_ games:Int)->Int {games<provisionalGames ? 40:games<30 ? 24:16}
    static func expected(_ mine:Int,_ theirs:Int)->Double {let d=max(-800,min(800,theirs-mine));return 1/(1+pow(10,Double(d)/400))}
    /// score: 1 for a win, 0.5 for a draw, 0 for a loss.
    static func delta(_ mine:Int,games:Int,_ theirs:Int,score:Double)->Int {Int((Double(k(games))*(score-expected(mine,theirs))+0.5).rounded(.down))}
    static func next(_ mine:Int,games:Int,_ theirs:Int,score:Double)->Int {max(floor,min(ceiling,mine+delta(mine,games:games,theirs,score:score)))}
    static func valid(_ rating:Int)->Bool {(floor...ceiling).contains(rating)}
}

/// Bech32 keys (NIP-19): the account's transfer code is its secret as "nsec1…", its public ID is "npub1…".
enum Nip19 {
    private static let charset=Array("qpzry9x8gf2tvdw0s3jn54khce6mua7l")
    private static func polymod(_ values:[Int])->Int {let g=[0x3b6a57b2,0x26508e6d,0x1ea119fa,0x3d4233dd,0x2a1462b3];var chk=1
        for v in values{let top=chk>>25;chk=(chk&0x1ffffff)<<5^v;for i in 0..<5 where (top>>i)&1 != 0{chk^=g[i]}};return chk}
    private static func expand(_ hrp:String)->[Int] {let c=hrp.unicodeScalars.map{Int($0.value)};return c.map{$0>>5}+[0]+c.map{$0&31}}
    static func encode(_ hrp:String,_ data:[UInt8])->String {
        var values=[Int](),acc=0,bits=0;for x in data{acc=acc<<8|Int(x);bits+=8;while bits>=5{bits-=5;values.append((acc>>bits)&31)}};if bits>0{values.append((acc<<(5-bits))&31)}
        let mod=polymod(expand(hrp)+values+[0,0,0,0,0,0])^1;let chk=(0..<6).map{(mod>>(5*(5-$0)))&31}
        return hrp+"1"+String((values+chk).map{charset[$0]})
    }
    /// The 32-byte payload for this prefix, or nil (wrong prefix, checksum, length or padding).
    static func decode(_ hrp:String,_ text:String?)->[UInt8]? {
        guard let s=text?.trimmingCharacters(in:.whitespacesAndNewlines).lowercased(),s.count<=120,s.hasPrefix(hrp+"1") else{return nil};let body=Array(s.dropFirst(hrp.count+1));guard body.count>=7 else{return nil}
        var values=[Int]();for ch in body{guard let v=charset.firstIndex(of:ch) else{return nil};values.append(v)};guard polymod(expand(hrp)+values)==1 else{return nil}
        var acc=0,bits=0,out=[UInt8]();for v in values.dropLast(6){acc=acc<<5|v;bits+=5;if bits>=8{bits-=8;out.append(UInt8((acc>>bits)&255))}}
        guard bits<5,acc&((1<<bits)-1)==0,out.count==32 else{return nil};return out
    }
}

/// The account card two matched players exchange over their encrypted link right after HELLO, as on Android:
/// "ID <account pubkey> <rating> <games> <name, base64url> <BIP-340 signature over the session-bound statement>".
struct OnlineIdentity:Equatable {
    static let nameMax=16
    let pubkey,name:String;let rating,games:Int
    static func statement(_ sessionId:[UInt8],_ pubkey:String,_ rating:Int,_ games:Int,_ name:String)->String {"shogikento-id-v1|\(Secp256k1.hex(sessionId))|\(pubkey)|\(rating)|\(games)|\(name)"}
    static func message(_ key:Secp256k1.Key,sessionId:[UInt8],name:String,rating:Int,games:Int)->String {
        var g=SystemRandomNumberGenerator();let aux=(0..<32).map{_ in UInt8.random(in:0...255,using:&g)}
        let sig=key.sign(Secp256k1.sha256(Array(statement(sessionId,key.publicHex,rating,games,name).utf8)),aux:aux)
        return "ID \(key.publicHex) \(rating) \(games) \(base64url(Array(name.utf8))) \(Secp256k1.hex(sig))"
    }
    /// The verified card, or nil for anything malformed, out of range, renamed in transit or signed for another session.
    static func parse(_ text:String,sessionId:[UInt8])->OnlineIdentity? {
        let f=text.trimmingCharacters(in:.whitespaces).components(separatedBy:" ");guard f.count==6,f[0]=="ID",f[1].count==64,f[5].count==128,f[4].count<=200,let rating=Int(f[2]),let games=Int(f[3]),Rating.valid(rating),(0...1_000_000).contains(games),
              let bytes=unbase64url(f[4]),let pub=Secp256k1.unhex(f[1].lowercased()),let sig=Secp256k1.unhex(f[5]) else{return nil}
        let name=String(decoding:bytes,as:UTF8.self);guard name==cleanName(name) else{return nil}
        let digest=Secp256k1.sha256(Array(statement(sessionId,f[1].lowercased(),rating,games,name).utf8))
        return Secp256k1.verify(digest,publicKey:pub,signature:sig) ? OnlineIdentity(pubkey:f[1].lowercased(),name:name,rating:rating,games:games):nil
    }
    /// Same as Android: drops control and bidirectional-override characters, trims ASCII and ideographic spaces,
    /// keeps at most 16 code points. Returns "" when nothing printable is left.
    static func cleanName(_ raw:String)->String {
        let blank:(Unicode.Scalar)->Bool={$0.value<=0x20 || $0.value==0x3000}
        var s=raw.unicodeScalars.filter{v in let c=v.value;return !(c<=0x1F || (0x7F...0x9F).contains(c) || (0x202A...0x202E).contains(c) || (0x2066...0x2069).contains(c) || c==0x200E || c==0x200F || c==0xFEFF)}
        while let f=s.first,blank(f){s.removeFirst()};while let l=s.last,blank(l){s.removeLast()}
        var out=Array(s.prefix(nameMax));while let l=out.last,blank(l){out.removeLast()}
        var r=String.UnicodeScalarView();r.append(contentsOf:out);return String(r)
    }
    static func base64url(_ b:[UInt8])->String {var s=Data(b).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_");while s.hasSuffix("="){s.removeLast()};return s}
    static func unbase64url(_ s:String)->[UInt8]? {guard s.allSatisfy({$0.isASCII && ($0.isLetter || $0.isNumber || $0=="-" || $0=="_")}) else{return nil};var t=s.replacingOccurrences(of:"-",with:"+").replacingOccurrences(of:"_",with:"/");while t.count%4 != 0{t+="="};return Data(base64Encoded:t).map{[UInt8]($0)}}
}
