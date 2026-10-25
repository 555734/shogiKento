import Foundation

/// Minimal JSON with ordered objects. Serialization follows NIP-01 exactly (same as the Android NetJson),
/// so Nostr event ids hash identically on relays and on every client.
indirect enum NetJSON {
    case string(String),int(Int64),double(Double),bool(Bool),null,array([NetJSON]),object([(String,NetJSON)])
    subscript(key:String)->NetJSON? {if case .object(let pairs)=self{return pairs.first{$0.0==key}?.1};return nil}
    var string:String? {if case .string(let s)=self{return s};return nil}
    var int:Int64? {switch self{case .int(let v):return v;case .double(let d) where d==d.rounded() && abs(d)<1e15:return Int64(d);default:return nil}}
    var array:[NetJSON]? {if case .array(let a)=self{return a};return nil}
    static func object(_ pairs:[(String,Any?)])->NetJSON {.object(pairs.map{($0.0,wrap($0.1))})}
    static func wrap(_ v:Any?)->NetJSON {
        switch v {case nil:return .null;case let j as NetJSON:return j;case let s as String:return .string(s);case let b as Bool:return .bool(b);case let i as Int:return .int(Int64(i));case let i as Int64:return .int(i)
        case let d as Double:return .double(d);case let a as [String]:return .array(a.map{.string($0)});case let a as [[String]]:return .array(a.map{.array($0.map{.string($0)})});case let a as [NetJSON]:return .array(a);default:return .null}
    }
    var text:String {var out="";write(&out);return out}
    func write(_ out:inout String){
        switch self {
        case .string(let s):Self.quote(s,&out)
        case .int(let v):out+=String(v)
        case .double(let d):if d==d.rounded() && abs(d)<1e15{out+=String(Int64(d))}else{out+=String(d)}
        case .bool(let b):out+=b ? "true":"false"
        case .null:out+="null"
        case .array(let a):out+="[";for (i,v) in a.enumerated(){if i>0{out+=","};v.write(&out)};out+="]"
        case .object(let pairs):out+="{";for (i,p) in pairs.enumerated(){if i>0{out+=","};Self.quote(p.0,&out);out+=":";p.1.write(&out)};out+="}"
        }
    }
    static func quote(_ s:String,_ out:inout String){
        out+="\""
        for u in s.unicodeScalars {
            switch u {case "\"":out+="\\\"";case "\\":out+="\\\\";case "\n":out+="\\n";case "\r":out+="\\r";case "\t":out+="\\t";case "\u{08}":out+="\\b";case "\u{0C}":out+="\\f"
            default:if u.value<0x20{out+=String(format:"\\u%04x",u.value)}else{out.unicodeScalars.append(u)}}
        }
        out+="\""
    }
    struct ParseError:Error {let offset:Int}
    static func parse(_ text:String)throws->NetJSON {var p=Parser(Array(text.utf16));let v=try p.value();p.space();guard p.i==p.s.count else{throw ParseError(offset:p.i)};return v}
    private struct Parser {
        let s:[UInt16];var i=0,depth=0
        init(_ s:[UInt16]){self.s=s}
        mutating func space(){while i<s.count,[0x20,0x09,0x0D,0x0A].contains(s[i]){i+=1}}
        mutating func peek(_ c:UInt16)->Bool {if i<s.count && s[i]==c{i+=1;return true};return false}
        mutating func expect(_ c:UInt16)throws {if !peek(c){throw ParseError(offset:i)}}
        func starts(_ word:String)->Bool {let w=Array(word.utf16);return i+w.count<=s.count && Array(s[i..<i+w.count])==w}
        mutating func value()throws->NetJSON {
            space();guard i<s.count else{throw ParseError(offset:i)};let c=s[i]
            if c==0x7B {depth+=1;guard depth<=32 else{throw ParseError(offset:i)};i+=1;var pairs=[(String,NetJSON)]();space();if peek(0x7D){depth-=1;return .object(pairs)}
                repeat{space();guard i<s.count,s[i]==0x22 else{throw ParseError(offset:i)};let k=try string();space();try expect(0x3A);pairs.append((k,try value()));space()}while peek(0x2C)
                try expect(0x7D);depth-=1;return .object(pairs)}
            if c==0x5B {depth+=1;guard depth<=32 else{throw ParseError(offset:i)};i+=1;var items=[NetJSON]();space();if peek(0x5D){depth-=1;return .array(items)}
                repeat{items.append(try value());space()}while peek(0x2C)
                try expect(0x5D);depth-=1;return .array(items)}
            if c==0x22 {return .string(try string())}
            if starts("true"){i+=4;return .bool(true)};if starts("false"){i+=5;return .bool(false)};if starts("null"){i+=4;return .null}
            let start=i;while i<s.count,let ch=Unicode.Scalar(s[i]),"+-0123456789.eE".unicodeScalars.contains(ch){i+=1}
            guard start<i else{throw ParseError(offset:i)};let n=String(decoding:s[start..<i],as:UTF16.self)
            if !n.contains("."),!n.contains("e"),!n.contains("E"){guard let v=Int64(n) else{throw ParseError(offset:start)};return .int(v)}
            guard let d=Double(n) else{throw ParseError(offset:start)};return .double(d)
        }
        mutating func string()throws->String {
            try expect(0x22);var out=[UInt16]()
            while true {
                guard i<s.count else{throw ParseError(offset:i)};let c=s[i];i+=1
                if c==0x22 {return String(decoding:out,as:UTF16.self)}
                if c != 0x5C {out.append(c);continue}
                guard i<s.count else{throw ParseError(offset:i)};let e=s[i];i+=1
                switch e {case 0x22:out.append(0x22);case 0x5C:out.append(0x5C);case 0x2F:out.append(0x2F);case 0x62:out.append(0x08);case 0x66:out.append(0x0C);case 0x6E:out.append(0x0A);case 0x72:out.append(0x0D);case 0x74:out.append(0x09)
                case 0x75:guard i+4<=s.count,let v=UInt16(String(decoding:s[i..<i+4],as:UTF16.self),radix:16) else{throw ParseError(offset:i)};out.append(v);i+=4
                default:throw ParseError(offset:i)}
            }
        }
    }
}
