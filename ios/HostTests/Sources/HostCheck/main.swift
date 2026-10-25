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
let t0=Date();for _ in 0..<20{let k=Secp256k1.Key.random();let s=k.sign([UInt8](repeating:7,count:32),aux:[UInt8](repeating:1,count:32));ok(Secp256k1.verify([UInt8](repeating:7,count:32),publicKey:k.publicKey,signature:s),"roundtrip")}
print("PASS swift core: \(checks) checks; sign+verify avg \(Int(Date().timeIntervalSince(t0)*1000/20))ms")
