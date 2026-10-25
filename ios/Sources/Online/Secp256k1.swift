import Foundation
#if canImport(CryptoKit)
import CryptoKit
#else
import Crypto
#endif

/// BIP-340 Schnorr signatures over secp256k1 for throwaway Nostr signaling keys (same as Android's Schnorr.java).
/// Numbers are 4 little-endian 64-bit limbs; both moduli are 2^256-c, so products reduce by folding with c.
enum Secp256k1 {
    typealias U=[UInt64]
    static let P:U=[0xFFFFFFFEFFFFFC2F,0xFFFFFFFFFFFFFFFF,0xFFFFFFFFFFFFFFFF,0xFFFFFFFFFFFFFFFF],PC:U=[0x00000001000003D1]
    static let N:U=[0xBFD25E8CD0364141,0xBAAEDCE6AF48A03B,0xFFFFFFFFFFFFFFFE,0xFFFFFFFFFFFFFFFF],NC:U=[0x402DA1732FC9BEBF,0x4551231950B75FC4,0x0000000000000001]
    static let G=Point(x:[0x59F2815B16F81798,0x029BFCDB2DCE28D9,0x55A06295CE870B07,0x79BE667EF9DCBBAC],y:[0x9C47D08FFB10D4B8,0xFD17B448A6855419,0x5DA4FBFC0E1108A8,0x483ADA7726A3C465])
    static let zero:U=[0,0,0,0],one:U=[1,0,0,0]
    struct Point {var x:U,y:U}
    struct Jacobian {var x:U,y:U,z:U;static let infinity=Jacobian(x:one,y:one,z:zero);var isInfinity:Bool {isZero(z)}}
    // ---- multi-limb arithmetic ----
    static func isZero(_ a:U)->Bool {a.allSatisfy{$0==0}}
    static func compare(_ a:U,_ b:U)->Int {for i in stride(from:3,through:0,by:-1){if a[i] != b[i]{return a[i]<b[i] ? -1:1}};return 0}
    static func add(_ a:U,_ b:U)->(U,Bool) {var r=zero,carry:UInt64=0;for i in 0..<4{let (s1,o1)=a[i].addingReportingOverflow(i<b.count ? b[i]:0);let (s2,o2)=s1.addingReportingOverflow(carry);r[i]=s2;carry=(o1 ? 1:0)+(o2 ? 1:0)};return (r,carry != 0)}
    static func sub(_ a:U,_ b:U)->(U,Bool) {var r=zero,borrow:UInt64=0;for i in 0..<4{let (d1,o1)=a[i].subtractingReportingOverflow(i<b.count ? b[i]:0);let (d2,o2)=d1.subtractingReportingOverflow(borrow);r[i]=d2;borrow=(o1 ? 1:0)+(o2 ? 1:0)};return (r,borrow != 0)}
    /// Arbitrary-length product (little-endian limbs).
    static func mul(_ a:[UInt64],_ b:[UInt64])->[UInt64] {
        var r=[UInt64](repeating:0,count:a.count+b.count)
        for i in 0..<a.count {var carry:UInt64=0;for j in 0..<b.count {let (hi,lo)=a[i].multipliedFullWidth(by:b[j]);let (s1,o1)=r[i+j].addingReportingOverflow(lo);let (s2,o2)=s1.addingReportingOverflow(carry);r[i+j]=s2;carry=hi&+(o1 ? 1:0)&+(o2 ? 1:0)};var k=i+b.count;while carry != 0{let (s,o)=r[k].addingReportingOverflow(carry);r[k]=s;carry=o ? 1:0;k+=1}}
        return r
    }
    static func addLong(_ a:[UInt64],_ b:[UInt64])->[UInt64] {let n=max(a.count,b.count)+1;var r=[UInt64](repeating:0,count:n),carry:UInt64=0;for i in 0..<n{let x=i<a.count ? a[i]:0,y=i<b.count ? b[i]:0;let (s1,o1)=x.addingReportingOverflow(y);let (s2,o2)=s1.addingReportingOverflow(carry);r[i]=s2;carry=(o1 ? 1:0)+(o2 ? 1:0)};return r}
    /// x mod m where m=2^256-c: x = hi*2^256+lo ≡ hi*c+lo, repeated until it fits, then at most a few subtractions.
    static func reduce(_ wide:[UInt64],_ m:U,_ c:U)->U {
        var x=wide
        while x.count>4 && x[4...].contains(where:{$0 != 0}) {x=addLong(Array(x[0..<4]),mul(Array(x[4...]),c))}
        var r=Array(x.prefix(4));while r.count<4{r.append(0)};while compare(r,m)>=0{r=sub(r,m).0};return r
    }
    static func modAdd(_ a:U,_ b:U,_ m:U,_ c:U)->U {let (s,carry)=add(a,b);if carry{return add(s,c).0};return compare(s,m)>=0 ? sub(s,m).0:s}
    static func modSub(_ a:U,_ b:U,_ m:U)->U {let (d,borrow)=sub(a,b);return borrow ? add(d,m).0:d}
    static func modMul(_ a:U,_ b:U,_ m:U,_ c:U)->U {reduce(mul(a,b),m,c)}
    static func modPow(_ base:U,_ e:U,_ m:U,_ c:U)->U {var r=one;for i in stride(from:255,through:0,by:-1){r=modMul(r,r,m,c);if bit(e,i){r=modMul(r,base,m,c)}};return r}
    static func bit(_ a:U,_ i:Int)->Bool {(a[i/64]>>UInt64(i%64))&1==1}
    static func shiftRight2(_ a:U)->U {var r=zero;for i in 0..<4{r[i]=a[i]>>2|(i<3 ? a[i+1]<<62:0)};return r}
    static let pMinus2=sub(P,[2,0,0,0]).0,sqrtExponent=shiftRight2(add(P,one).0)
    static func f(_ a:U,_ b:U)->U {modMul(a,b,P,PC)}
    static func fa(_ a:U,_ b:U)->U {modAdd(a,b,P,PC)}
    static func fs(_ a:U,_ b:U)->U {modSub(a,b,P)}
    // ---- points (Jacobian, a=0) ----
    static func jacobian(_ p:Point?)->Jacobian {p.map{Jacobian(x:$0.x,y:$0.y,z:one)} ?? .infinity}
    static func affine(_ j:Jacobian)->Point? {if j.isInfinity{return nil};let zi=modPow(j.z,pMinus2,P,PC),z2=f(zi,zi);return Point(x:f(j.x,z2),y:f(f(j.y,z2),zi))}
    static func twice(_ p:Jacobian)->Jacobian {
        if p.isInfinity || isZero(p.y){return .infinity}
        let y2=f(p.y,p.y),s=f(f(p.x,y2),[4,0,0,0]),x2=f(p.x,p.x),m=fa(fa(x2,x2),x2)
        let x=fs(f(m,m),fa(s,s)),y8=f(f(y2,y2),[8,0,0,0]),y=fs(f(m,fs(s,x)),y8)
        return Jacobian(x:x,y:y,z:f(fa(p.y,p.y),p.z))
    }
    static func add(_ p:Jacobian,_ q:Jacobian)->Jacobian {
        if p.isInfinity{return q};if q.isInfinity{return p}
        let z1=f(p.z,p.z),z2=f(q.z,q.z),u1=f(p.x,z2),u2=f(q.x,z1),s1=f(f(p.y,z2),q.z),s2=f(f(q.y,z1),p.z)
        if compare(u1,u2)==0{return compare(s1,s2)==0 ? twice(p):.infinity}
        let h=fs(u2,u1),r=fs(s2,s1),h2=f(h,h),h3=f(h2,h),v=f(u1,h2)
        let x=fs(fs(f(r,r),h3),fa(v,v)),y=fs(f(r,fs(v,x)),f(s1,h3))
        return Jacobian(x:x,y:y,z:f(f(p.z,q.z),h))
    }
    /// 4-bit fixed-base windows for G: table[w][j]=j*16^w*G, so k*G needs at most 64 additions.
    static let table:[[Jacobian]]={var t=[[Jacobian]]();var base=jacobian(G);for _ in 0..<64{var row=[Jacobian.infinity],sum=Jacobian.infinity;for _ in 1..<16{sum=add(sum,base);row.append(sum)};t.append(row);base=twice(twice(twice(twice(base))))};return t}()
    static func multiplyG(_ k:U)->Point? {var r=Jacobian.infinity;for w in 0..<64{var j=0;for b in 0..<4 where bit(k,4*w+b){j|=1<<b};if j != 0{r=add(r,table[w][j])}};return affine(r)}
    static func multiply(_ p:Point,_ k:U)->Point? {var r=Jacobian.infinity;let base=jacobian(p);for i in stride(from:255,through:0,by:-1){r=twice(r);if bit(k,i){r=add(r,base)}};return affine(r)}
    static func liftX(_ x:U)->Point? {
        guard compare(x,P)<0 else{return nil};let c=fa(f(f(x,x),x),[7,0,0,0]),y=modPow(c,sqrtExponent,P,PC)
        guard compare(f(y,y),c)==0 else{return nil};return Point(x:x,y:y[0]&1==1 ? fs(zero,y):y)
    }
    // ---- bytes and hashes ----
    static func number(_ b:[UInt8])->U {var r=zero;for i in 0..<32{r[3-i/8]|=UInt64(b[i])<<UInt64(56-8*(i%8))};return r}
    static func bytes(_ a:U)->[UInt8] {(0..<32).map{UInt8(truncatingIfNeeded:a[3-$0/8]>>UInt64(56-8*($0%8)))}}
    static func sha256(_ parts:[UInt8]...)->[UInt8] {var h=SHA256();for p in parts{h.update(data:p)};return Array(h.finalize())}
    static func tagged(_ tag:String,_ parts:[UInt8]...)->[UInt8] {let t=sha256(Array(tag.utf8));var h=SHA256();h.update(data:t);h.update(data:t);for p in parts{h.update(data:p)};return Array(h.finalize())}
    static func modN(_ a:U)->U {compare(a,N)>=0 ? sub(a,N).0:a}
    static func validScalar(_ a:U)->Bool {!isZero(a) && compare(a,N)<0}
    /// A signing key with its x-only public key and BIP-340 even-Y secret computed once.
    struct Key {
        let d:U,publicKey:[UInt8]
        var publicHex:String {hex(publicKey)}
        init?(secret:[UInt8]){guard secret.count==32 else{return nil};let d0=number(secret);guard validScalar(d0),let p=multiplyG(d0) else{return nil};d=p.y[0]&1==1 ? sub(N,d0).0:d0;publicKey=bytes(p.x)}
        static func random()->Key {var g=SystemRandomNumberGenerator();while true{let s=(0..<32).map{_ in UInt8.random(in:0...255,using:&g)};if let k=Key(secret:s){return k}}}
        func sign(_ message:[UInt8],aux:[UInt8])->[UInt8] {
            var t=bytes(d);let h=tagged("BIP0340/aux",aux);for i in 0..<32{t[i]^=h[i]}
            let k0=modN(number(tagged("BIP0340/nonce",t,publicKey,message)));let r=multiplyG(k0)!;let k=r.y[0]&1==1 ? sub(N,k0).0:k0;let rx=bytes(r.x)
            let e=modN(number(tagged("BIP0340/challenge",rx,publicKey,message)));let s=modAdd(k,modMul(e,d,N,NC),N,NC)
            return rx+bytes(s)
        }
    }
    static func verify(_ message:[UInt8],publicKey:[UInt8],signature sig:[UInt8])->Bool {
        guard publicKey.count==32,sig.count==64,let p=liftX(number(publicKey)) else{return false}
        let r=number(Array(sig[0..<32])),s=number(Array(sig[32..<64]));guard compare(r,P)<0,compare(s,N)<0 else{return false}
        let e=modN(number(tagged("BIP0340/challenge",Array(sig[0..<32]),publicKey,message)))
        let sg=jacobian(multiplyG(s)),pe=jacobian(isZero(e) ? nil:multiply(p,sub(N,e).0))
        guard let sum=affine(add(sg,pe)) else{return false};return sum.y[0]&1==0 && compare(sum.x,r)==0
    }
    static func hex(_ b:[UInt8])->String {b.map{String(format:"%02x",$0)}.joined()}
    static func unhex(_ s:String)->[UInt8]? {let c=Array(s.utf8);guard c.count%2==0 else{return nil};var out=[UInt8]();out.reserveCapacity(c.count/2)
        func v(_ x:UInt8)->UInt8? {switch x{case 48...57:return x-48;case 97...102:return x-87;case 65...70:return x-55;default:return nil}}
        for i in stride(from:0,to:c.count,by:2){guard let h=v(c[i]),let l=v(c[i+1]) else{return nil};out.append(h<<4|l)};return out}
}
