import Foundation
@testable import OnlineCore
var checks=0
func ok(_ c:Bool,_ s:String){checks+=1;if !c{print("FAIL:",s);exit(1)}}
if CommandLine.arguments[1]=="live"{Interop.live(classpath:CommandLine.arguments[2]);exit(0)}
if CommandLine.arguments[1]=="interop"{Interop.run(classpath:CommandLine.arguments[2]);print("PASS swift interop: \(checks) checks");exit(0)}
let csv=try! String(contentsOfFile:CommandLine.arguments[1],encoding:.utf8).components(separatedBy:CharacterSet.newlines).filter{!$0.isEmpty}.dropFirst()
var n=0
for line in csv {let f=line.split(separator:",",omittingEmptySubsequences:false).map(String.init);let pub=Secp256k1.unhex(f[2])!,msg=Secp256k1.unhex(f[4])!,sig=Secp256k1.unhex(f[5])!
 if !f[1].isEmpty{let k=Secp256k1.Key(secret:Secp256k1.unhex(f[1])!)!;ok(k.publicKey==pub,"pub \(f[0])");ok(k.sign(msg,aux:Secp256k1.unhex(f[3])!)==sig,"sig \(f[0])")}
 ok(Secp256k1.verify(msg,publicKey:pub,signature:sig)==(f[6]=="TRUE"),"verify \(f[0]) \(f[7])");n+=1}
ok(n==19,"19 vectors")
let content="line\nbreak \"quote\" back\\slash tab\t cr\r bs\u{08} ff\u{0C} 日本語 \u{01} /slash"
let ser=NetJSON.array([.int(0),.string(String(repeating:"a",count:64)),.int(1700000000),.int(25150),.array([.array([.string("t"),.string("x")]),.array([.string("p"),.string(String(repeating:"b",count:64))])]),.string(content)]).text
ok(Secp256k1.hex(Secp256k1.sha256(Array(ser.utf8)))=="e2b3f13fcefa49af0cb6456931333bfce0d9a3590ed75572950ddf1ad8394d03","NIP-01 id vector matches Java and Python")
let v=try! NetJSON.parse("{\"a\":[1,-2.5,true,false,null,\"\\u65e5\\n\"],\"b\":{}}");ok(v.text=="{\"a\":[1,-2.5,true,false,null,\"日\\n\"],\"b\":{}}","json roundtrip \(v.text)")
for bad in ["{","[1,]","{\"a\" 1}","\"\\x\"","[1] 2","{\"a\":tru}"]{ok((try? NetJSON.parse(bad))==nil,"bad json \(bad)")}
ok((try? NetJSON.parse(String(repeating:"[",count:100)+String(repeating:"]",count:100)))==nil,"depth")
// Accounts and ratings: the same vectors as the Android NetplayTest.accounts().
ok(Rating.delta(1500,games:0,1500,score:1)==20 && Rating.delta(1500,games:0,1500,score:0) == -20 && Rating.delta(1500,games:0,1500,score:0.5)==0,"provisional K=40")
ok(Rating.delta(1500,games:30,1700,score:1)==12 && Rating.delta(1500,games:30,1700,score:0) == -4,"K=16 and floor(x+0.5) rounding")
ok(Rating.delta(1500,games:15,2500,score:1)==24 && Rating.delta(2500,games:15,1500,score:0) == -24 && Rating.next(105,games:50,105,score:0)==100 && Rating.next(3995,games:50,3995,score:1)==4000,"cap, floor, ceiling")
ok(Nip19.decode("nsec","nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5").map(Secp256k1.hex)=="67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa","NIP-19 nsec")
ok(Nip19.encode("npub",Secp256k1.unhex("84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240")!)=="npub1sn0wdenkukak0d9dfczzeacvhkrgz92ak56egt7vdgzn8pv2wfqqhrjdv9","NIP-19 npub")
ok(Nip19.decode("nsec","nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe6")==nil && Nip19.decode("npub","nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")==nil,"bad codes rejected")
ok(OnlineIdentity.cleanName("  将棋\u{202E}太郎\u{07} ")=="将棋太郎" && OnlineIdentity.cleanName("\u{3000}名前\u{3000}")=="名前" && OnlineIdentity.cleanName(String(repeating:"😀",count:17)).unicodeScalars.count==16,"names cleaned like Android")
do{let k=Secp256k1.Key.random(),sid=(0..<32).map{UInt8($0)},card=OnlineIdentity.message(k,sessionId:sid,name:"先手 太郎",rating:1520,games:12)
    ok(OnlineIdentity.parse(card,sessionId:sid)==OnlineIdentity(pubkey:k.publicHex,name:"先手 太郎",rating:1520,games:12),"card verified");ok(OnlineIdentity.parse(card,sessionId:Array(sid.reversed()))==nil && OnlineIdentity.parse(card.replacingOccurrences(of:" 1520 ",with:" 2520 "),sessionId:sid)==nil,"card bound and tamper-proof")
    var a=OnlineAccount(name:"x");a.begin(OnlineIdentity.parse(card,sessionId:sid)!,round:1,now:1);let data=try! JSONEncoder().encode(a);var b=try! JSONDecoder().decode(OnlineAccount.self,from:data)
    ok(b.resolveAbandoned(now:2)?.result == -1 && b.rating==1500+Rating.delta(1500,games:0,1520,score:0) && b.losses==1 && b.pending==nil,"unfinished rated game is a loss");b.record(opponent:k.publicHex,name:"先手 太郎",rating:1520,score:0.5,reason:"千日手",now:3)
    let h=b.headToHead(k.publicHex);ok(h.losses==1 && h.draws==1 && b.games==2,"head-to-head")
    let e=NostrPool.Event(id:String(repeating:"0",count:64),pubkey:k.publicHex,content:Profiles.content(b),createdAt:1,kind:Profiles.kind,tags:Profiles.tags,relay:"x");ok(Profiles.parse(e)?.rating==b.rating,"profile round trip")}
let t0=Date();for _ in 0..<20{let k=Secp256k1.Key.random();let s=k.sign([UInt8](repeating:7,count:32),aux:[UInt8](repeating:1,count:32));ok(Secp256k1.verify([UInt8](repeating:7,count:32),publicKey:k.publicKey,signature:s),"roundtrip")}
print("PASS swift core: \(checks) checks; sign+verify avg \(Int(Date().timeIntervalSince(t0)*1000/20))ms")
