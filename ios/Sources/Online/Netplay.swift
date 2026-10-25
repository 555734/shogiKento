import Foundation
#if canImport(CryptoKit)
import CryptoKit
#else
import Crypto
#endif

/// Callbacks arrive on the matchmaking queue, never on the main thread.
protocol NetplayListener:AnyObject {
    func status(_ text:String);func relays(open:Int,total:Int);func network(_ text:String)
    func matched(localSide:Int);func message(_ text:String);func path(direct:Bool,rtt:Int,route:String)
    func peerLeft();func failed(_ reason:String)
}
extension NetplayListener {
    func status(_ text:String){};func relays(open:Int,total:Int){};func network(_ text:String){};func path(direct:Bool,rtt:Int,route:String){}
    func peerLeft(){};func failed(_ reason:String){}
}

/// Serverless matchmaking, message-compatible with Android's Netplay so iPhone and Android players meet in
/// the same lobby. Seekers announce themselves on public Nostr relays; pairing is a three-way
/// offer/accept/confirm handshake where the lower relay key always offers, so every seeker ends in at most
/// one match. Offer and accept carry ephemeral P-256 ECDH keys and encrypted UDP candidates, letting both
/// sides start hole punching immediately. All state lives on one serial queue.
final class Netplay {
    static let quick=0,host=1,guest=2,version:Int64=1
    enum State {case idle,seeking,offered,accepted,matched}
    /// The public 3-minute lobby. Tests use a private tag so they never meet real players.
    static var lobbyTag="shogikento-v1-3m"
    /// Tests point the app at a local relay without internet STUN.
    static var relayOverride:[String]?,stunOverride:[String]?
    static var relays:[String] {relayOverride ?? NostrPool.defaultRelays}
    static var stunServers:[String] {stunOverride ?? UDPEndpoint.defaultSTUN}
    final class Seek {var nonce="",ecdh:String?,role="";var since:Int64=0,lastSeen:Int64=0}
    final class Session {
        let peer,peerNonce:String;let role:Int;let key,id:[UInt8];var confirms=0,live=false,deadline:Int64=0,lastConfirm:Int64=0;var link:P2PLink?,peerCandidates=[String]()
        init(peer:String,peerNonce:String,role:Int,key:[UInt8],id:[UInt8]){self.peer=peer;self.peerNonce=peerNonce;self.role=role;self.key=key;self.id=id}
        var localSide:Int {((id[31]&1)==0)==(role==0) ? 1:-1}
        var shortId:String {Secp256k1.hex(Array(id.prefix(8)))}
    }
    let key=Secp256k1.Key.random();let pool:NostrPool;let endpoint:UDPEndpoint
    private let ecdh=P256.KeyAgreement.PrivateKey();let ecdhText:String;private let stun:[String]
    weak var listener:NetplayListener?
    let loop=DispatchQueue(label:"netplay");private var timer:DispatchSourceTimer?
    private var mode=Netplay.quick,state=State.idle,seeks=0;private var roomTag:String?,nonce="",target:String?,targetNonce:String?
    private var deadline:Int64=0,seekStarted:Int64=0,lastSeek:Int64=0,lastReply:Int64=0,lastHandshake:Int64=0,handshakeSends=0;private var handshake:[(String,NetJSON)]?
    private var session:Session?,pending:Session?;private var seen=[String:Seek](),blocked=[String:Int64]()
    private var relaysOpen=0,relaysTotal=0,lastStatus=""
    /// Recursive: start() holds it while gathering candidates, whose callback checks isClosed on the same thread.
    private let lock=NSRecursiveLock();private var closed=false
    private let born=Date();private var traceLines=[String]()

    init(relays:[String]=Netplay.relays,stun:[String]=Netplay.stunServers,factory:@escaping WebSocketFactory)throws {
        self.stun=stun;ecdhText=ecdh.publicKey.derRepresentation.base64EncodedString();endpoint=try UDPEndpoint()
        pool=NostrPool(urls:relays,key:key,factory:factory,deliver:loop);relaysTotal=relays.count
        pool.onEvent={[weak self] e in self?.incoming(e)}
        pool.onRelays={[weak self] open,total in guard let self else{return};self.relaysOpen=open;self.relaysTotal=total;self.listener?.relays(open:open,total:total)}
        endpoint.candidateListener={[weak self] _ in self?.run{guard let self else{return};(self.session ?? self.pending)?.link?.shareCandidates();self.networkSummary()}}
    }
    private var isClosed:Bool {lock.lock();defer{lock.unlock()};return closed}
    /// May race with close() from another thread; a closed instance simply does not start.
    func start(){
        lock.lock();defer{lock.unlock()};guard !closed,timer==nil else{return}
        pool.subscribe("direct",Self.filter("#p",key.publicHex));pool.start();endpoint.gather(stun)
        let t=DispatchSource.makeTimerSource(queue:loop);t.schedule(deadline:.now()+0.5,repeating:0.5,leeway:.milliseconds(50));t.setEventHandler{[weak self] in self?.tick()};t.resume();timer=t
        run{[weak self] in self?.networkSummary()}
    }
    /// Work queued before close() still runs (close waits for it), so a final RESIGN reaches the peer before BYE.
    func run(_ block:@escaping ()->Void){guard !isClosed else{return};loop.async(execute:block)}
    static func nowMillis()->Int64 {Int64(Date().timeIntervalSince1970*1000)}
    static func filter(_ tag:String,_ value:String)->NetJSON {.object([("kinds",.array([.int(Int64(NostrPool.kind))])),(tag,.array([.string(value)])),("since",.int(nowMillis()/1000-30))])}
    static func roomTag(_ code:String)->String {"shogikento-v1-room-"+String(Secp256k1.hex(Secp256k1.sha256(Array("shogikento-room:\(code)".utf8))).prefix(24))}
    static func newRoomCode()->String {String(format:"%06d",Int.random(in:0..<1_000_000))}
    /// Recent handshake steps for diagnostics (relative ms, short peer ids); never contains candidates or keys.
    private func trace(_ what:String){lock.lock();traceLines.append("\(Int(Date().timeIntervalSince(born)*1000))ms \(what)");if traceLines.count>80{traceLines.removeFirst()};lock.unlock()}
    static func shortKey(_ k:String?)->String {k.map{String($0.prefix(6))} ?? "-"}

    // ---- public controls (any thread) ----
    func seek(_ mode:Int,code:String?){run{[self] in closeSession();self.mode=mode;roomTag=mode==Self.quick ? Self.lobbyTag:Self.roomTag(code ?? "");nonce=Self.randomHex(16);state = .seeking;seekStarted=Self.nowMillis();lastSeek=0;seeks=0;seen.removeAll();blocked.removeAll()
        pool.subscribe("lobby",Self.filter("#t",roomTag!));tick()}}
    func stopSeeking(){run{[self] in state = .idle;pool.unsubscribe("lobby");closeSession();status("")}}
    /// Ordered with all other session work and never on the caller's (UI) thread.
    func send(_ text:String){run{[self] in session?.link?.send(text)}}
    var link:P2PLink? {loop.sync{session?.link}}
    static func randomHex(_ bytes:Int)->String {var g=SystemRandomNumberGenerator();return Secp256k1.hex((0..<bytes).map{_ in UInt8.random(in:0...255,using:&g)})}

    // ---- matchmaking ----
    private func publish(_ peer:String?,_ content:[(String,NetJSON)]){pool.publish([[peer==nil ? "t":"p",peer ?? roomTag ?? ""]],NetJSON.object(content+[("v",.int(Self.version))]).text)}
    private func message(_ op:String)->[(String,NetJSON)] {[("op",.string(op)),("n",.string(nonce))]}
    private func tick(){
        guard !isClosed else{return};let now=Self.nowMillis();seen=seen.filter{now-$0.value.lastSeen<=9000};blocked=blocked.filter{$0.value>=now}
        // The second announcement covers peers whose lobby subscription reached the relays just after our first one.
        if state == .seeking {if now-lastSeek>=(seeks==1 ? 700:3000){announce(now)}
            offerIfPossible(now);status((mode==Self.quick ? "対戦相手を探しています":mode==Self.host ? "相手の参加を待っています":"ルームに接続しています")+"（\((now-seekStarted)/1000)秒）")}
        else if (state == .offered || state == .accepted) && now>deadline {trace("\(state == .offered ? "offer":"accept") timed out \(Self.shortKey(target))");if let target{blocked[target]=now+3000};closePending();state = .seeking}
        // A lost offer or accept costs one resend interval rather than a full timeout.
        else if state == .offered,let handshake,handshakeSends<4,now-lastHandshake>=1200 {handshakeSends+=1;lastHandshake=now;trace("resend offer \(Self.shortKey(target))");publish(target,handshake)}
        else if state == .matched,let s=session,!s.live {if let link=s.link,link.heard{s.live=true}
            else if now>s.deadline {closeSession();state = .seeking;fail("相手の応答がありません。もう一度探します")}
            else if s.role==0 && s.confirms<4 && now-s.lastConfirm>=800 {s.confirms+=1;s.lastConfirm=now;publish(s.peer,confirm(s))}}
    }
    private func announce(_ now:Int64){lastSeek=now;seeks+=1;publish(nil,message("seek")+[("e",.string(ecdhText)),("w",.int(seekStarted/1000)),("r",.string(mode==Self.host ? "host":mode==Self.guest ? "guest":"")),("tc",.string("3m"))])}
    private func confirm(_ s:Session)->[(String,NetJSON)] {message("confirm")+[("to",.string(s.peerNonce))]}
    private func decline(_ peer:String,_ n:String){publish(peer,message("decline")+[("to",.string(n))])}
    private func mayOffer(_ s:Seek,_ pubkey:String)->Bool {mode==Self.quick ? pubkey>key.publicHex && s.role.isEmpty:mode==Self.host && s.role=="guest"}
    private func offerIfPossible(_ now:Int64){
        var best:String?,pick:Seek?
        for (k,s) in seen where blocked[k]==nil && mayOffer(s,k) {if pick==nil || s.since<pick!.since || s.since==pick!.since && k<best!{pick=s;best=k}}
        guard let pick,let best else{return};guard let s=derive(best,pick.nonce,pick.ecdh,0) else{blocked[best]=now+60000;return}
        target=best;targetNonce=pick.nonce;deadline=now+4000;state = .offered;closePending();pending=s
        let m=message("offer")+[("to",.string(pick.nonce)),("e",.string(ecdhText)),("c",.string(seal(s.key,P2PLink.encodeCandidates(endpoint.candidates))))];handshake=m;handshakeSends=1;lastHandshake=now;trace("offer \(Self.shortKey(best))");publish(best,m)
    }
    private func incoming(_ e:NostrPool.Event){
        guard !isClosed,let m=try? NetJSON.parse(e.content),m["v"]?.int==Self.version,let op=m["op"]?.string,let n=m["n"]?.string,n.utf16.count<=64 else{return}
        let to=m["to"]?.string;if op != "seek" && key.publicHex != e.tag("p"){return}
        let now=Self.nowMillis()
        switch op {
        case "seek":
            guard state != .idle,let roomTag,roomTag==e.tag("t"),m["tc"]?.string=="3m" else{return}
            var s=seen[e.pubkey];let fresh=s==nil || s!.nonce != n
            if fresh {let x=Seek();x.nonce=n;x.ecdh=m["e"]?.string;x.since=m["w"]?.int ?? now/1000;x.role=m["r"]?.string ?? "";s=x}
            let seek=s!;seek.lastSeen=now;if seek.ecdh != nil{seen[e.pubkey]=seek};guard state == .seeking else{return};offerIfPossible(now)
            // A newcomer that must offer to us learns about us now instead of at our next periodic announcement.
            if fresh && state == .seeking && !mayOffer(seek,e.pubkey) && now-lastReply>=1000 {lastReply=now;announce(now)}
        case "offer":
            guard nonce==to else{return};trace("recv offer \(Self.shortKey(e.pubkey)) via \(e.relay)")
            if state == .accepted && e.pubkey==target && n==targetNonce,let handshake{publish(e.pubkey,handshake);return}
            if state == .matched,let s=session,e.pubkey==s.peer && n==s.peerNonce{return}
            let allowed=mode==Self.quick ? e.pubkey<key.publicHex:mode==Self.guest
            guard allowed && (state == .seeking || state == .offered) else{decline(e.pubkey,n);return}
            guard let s=derive(e.pubkey,n,m["e"]?.string,1),let cand=Self.open(s.key,m["c"]?.string) else{return}
            closePending();s.peerCandidates=cand;pending=s;target=e.pubkey;targetNonce=n;deadline=now+5000;state = .accepted;startLink(s)
            let a=message("accept")+[("to",.string(n)),("c",.string(seal(s.key,P2PLink.encodeCandidates(endpoint.candidates)))),("e",.string(ecdhText))];handshake=a;trace("accept \(Self.shortKey(e.pubkey))");publish(e.pubkey,a);status("対戦相手が見つかりました・接続中…")
        case "accept":
            guard nonce==to else{return};trace("recv accept \(Self.shortKey(e.pubkey)) via \(e.relay)")
            if state == .matched,let s=session,e.pubkey==s.peer && n==s.peerNonce{publish(e.pubkey,confirm(s));return}
            guard state == .offered,e.pubkey==target,n==targetNonce,let s=pending else{decline(e.pubkey,n);return}
            guard let cand=Self.open(s.key,m["c"]?.string) else{return};pending=nil;s.peerCandidates=cand;matched(s,now);s.confirms=1;s.lastConfirm=now;publish(s.peer,confirm(s))
        case "confirm":
            guard nonce==to else{return};trace("recv confirm \(Self.shortKey(e.pubkey)) via \(e.relay)")
            if state == .accepted && e.pubkey==target && n==targetNonce,let s=pending{pending=nil;matched(s,now)}
        case "decline":
            guard nonce==to else{return};trace("recv decline \(Self.shortKey(e.pubkey))")
            if (state == .offered || state == .accepted) && e.pubkey==target {blocked[e.pubkey]=now+10000;closePending();state = .seeking}
            else if state == .matched,let s=session,!s.live,e.pubkey==s.peer {closeSession();state = .seeking}
        case "pkt":
            let s=session.flatMap{e.pubkey==$0.peer ? $0:nil} ?? pending.flatMap{e.pubkey==$0.peer ? $0:nil}
            guard let s,let link=s.link,m["s"]?.string==s.shortId,let data=m["d"]?.string,let packet=Data(base64Encoded:data) else{return}
            if s===pending && state == .accepted{pending=nil;matched(s,now)};link.receiveRelay([UInt8](packet))
        default:break
        }
    }
    private func matched(_ s:Session,_ now:Int64){trace("matched \(Self.shortKey(s.peer)) role \(s.role)");handshake=nil;state = .matched;session=s;s.deadline=now+10000;pool.unsubscribe("lobby");if s.link==nil{startLink(s)}
        status("接続しました");listener?.matched(localSide:s.localSide)}
    private func startLink(_ s:Session){
        let pkt=message("pkt")+[("s",.string(s.shortId))],peer=s.peer
        s.link=P2PLink(endpoint:endpoint,secret:s.key,sessionId:s.id,role:s.role,relay:{[weak self] packet in self?.publish(peer,pkt+[("d",.string(Data(packet).base64EncodedString()))])},
            onMessage:{[weak self] text in self?.run{guard let self else{return};self.implicitConfirm(s);if self.session===s{s.live=true;self.listener?.message(text)}}},
            onPath:{[weak self] direct,rtt,route in self?.run{guard let self else{return};if direct{self.implicitConfirm(s)};if self.session===s{s.live=s.live || direct;self.listener?.path(direct:direct,rtt:rtt,route:route)}}},
            onPeerClosed:{[weak self] in self?.run{guard let self else{return};if self.session===s{self.listener?.peerLeft()}}})
        s.link?.addCandidates(s.peerCandidates)
    }
    /// The offerer only creates its link after our accept, so any authenticated message or answered probe from that
    /// link proves the match even if the relayed confirm is slower than direct UDP (or lost).
    private func implicitConfirm(_ s:Session){if pending===s && state == .accepted{pending=nil;matched(s,Self.nowMillis())}}
    /// Session secret = HKDF(ECDH, salt=id). The id binds both nonces and both public keys in offer order.
    private func derive(_ peer:String,_ peerNonce:String,_ peerEcdh:String?,_ role:Int)->Session? {
        guard let peerEcdh,peerEcdh.utf16.count<=256,let der=Data(base64Encoded:peerEcdh),let theirs=try? P256.KeyAgreement.PublicKey(derRepresentation:der),
              let shared=try? ecdh.sharedSecretFromKeyAgreement(with:theirs) else{return nil}
        let first=role==0 ? nonce+ecdhText:peerNonce+peerEcdh,second=role==0 ? peerNonce+peerEcdh:nonce+ecdhText
        let id=Secp256k1.sha256(Array("shogikento-session-v1|\(first)|\(second)".utf8))
        return Session(peer:peer,peerNonce:peerNonce,role:role,key:Self.hkdf(shared.withUnsafeBytes{Array($0)},salt:id,info:"shogikento p2p v1"),id:id)
    }
    static func hkdf(_ ikm:[UInt8],salt:[UInt8],info:String)->[UInt8] {HKDF<SHA256>.deriveKey(inputKeyMaterial:SymmetricKey(data:ikm),salt:salt,info:Array(info.utf8),outputByteCount:32).withUnsafeBytes{Array($0)}}
    private func seal(_ key:[UInt8],_ text:String)->String {
        var g=SystemRandomNumberGenerator();let iv=(0..<12).map{_ in UInt8.random(in:0...255,using:&g)}
        guard let box=try? AES.GCM.seal(Array(text.utf8),using:SymmetricKey(data:key),nonce:AES.GCM.Nonce(data:iv),authenticating:Array("candidates".utf8)) else{return ""}
        return Data(iv+Array(box.ciphertext)+Array(box.tag)).base64EncodedString()
    }
    static func open(_ key:[UInt8],_ sealed:String?)->[String]? {
        guard let sealed,sealed.utf16.count<=4096,let data=Data(base64Encoded:sealed),data.count>=28 else{return nil};let b=[UInt8](data)
        guard let box=try? AES.GCM.SealedBox(nonce:AES.GCM.Nonce(data:Array(b[0..<12])),ciphertext:Array(b[12..<(b.count-16)]),tag:Array(b[(b.count-16)...])),
              let plain=try? AES.GCM.open(box,using:SymmetricKey(data:key),authenticating:Array("candidates".utf8)) else{return nil}
        return P2PLink.decodeCandidates(String(decoding:plain,as:UTF8.self))
    }
    private func closePending(){pending?.link?.close();pending=nil}
    private func closeSession(){closePending();session?.link?.close();session=nil}
    private func status(_ text:String){guard text != lastStatus else{return};lastStatus=text;listener?.status(text)}
    private func fail(_ reason:String){listener?.failed(reason)}
    private func networkSummary(){let c=endpoint.candidates,v6=c.filter{$0.hasPrefix("[")}.count,v4=c.count-v6
        listener?.network("UDP候補 IPv4×\(v4) / IPv6×\(v6)"+(endpoint.stunReplies>0 ? (endpoint.symmetricNat ? "・NAT: 対称型（リレー経由の可能性）":"・NAT: 直結可能型"):"・STUN応答待ち"))}
    /// Call off the matchmaking queue.
    var diagnostics:String {
        let (s,state,open,total)=loop.sync{(session,self.state,relaysOpen,relaysTotal)};lock.lock();let steps=traceLines.joined(separator:" | ");lock.unlock()
        return "handshake: \(steps)\nstate=\(state) relays=\(open)/\(total) candidates=\(endpoint.candidates) symmetric=\(endpoint.symmetricNat)\(s?.link.map{" link: "+$0.stats} ?? "")\n\(pool.diagnostics)"
    }
    /// Blocks up to about 1.7 s so the goodbye packet leaves; call off the main thread.
    func close(){
        lock.lock();let first = !closed;closed=true;lock.unlock();guard first else{return}
        let done=DispatchSemaphore(value:0);loop.async{self.timer?.cancel();self.closeSession();done.signal()};_=done.wait(timeout:.now()+1.5)
        Thread.sleep(forTimeInterval:0.15);pool.close();endpoint.close()
    }
}
