import Foundation
#if canImport(CryptoKit)
import CryptoKit
#else
import Crypto
#endif

/// Encrypted, ordered and reliable messages between two matched players, packet-compatible with Android's P2PLink.
/// Messages take the direct UDP path once a hole-punched candidate answers; until then, and whenever a packet
/// goes unacknowledged, the same AES-GCM packet also travels through the Nostr relays. Duplicates are discarded.
/// All link state lives on one serial queue; callbacks arrive in order on a second one.
final class P2PLink {
    static let probe=1,probeAck=2,ping=3,pong=4,candidatesType=5,data=16,ack=17,bye=18
    static let header=20,maxText=900
    private final class Outgoing {let seq:Int,body:[UInt8],first:Int64;var lastSent:Int64=0,lastRelay:Int64=0,attempts=0;init(_ seq:Int,_ body:[UInt8],_ now:Int64){self.seq=seq;self.body=body;first=now}}
    let endpoint:UDPEndpoint;let role:Int;private let tag:[UInt8];private let key:SymmetricKey
    private let relay:([UInt8])->Void,onMessage:(String)->Void,onPath:(Bool,Int,String)->Void,onPeerClosed:()->Void
    private let q=DispatchQueue(label:"p2p.link"),events=DispatchQueue(label:"p2p.events"),relayQueue=DispatchQueue(label:"p2p.relay");private var timer:DispatchSourceTimer?
    private var remote=[UDPAddress](),pathRtt=[UDPAddress:Int](),unacked=[Outgoing](),early=[Int:String](),seenCounters=Set<Int64>(),seenOrder=[Int64]()
    private var highestCounter:Int64 = -1,counter:Int64=0,startedAt:Int64,probeBurst:Int64,lastProbe:Int64=0,lastPing:Int64=0,lastRelayPing:Int64=0,lastDirectReceive:Int64=0,lastPeerReceive:Int64,lastCandidates:Int64=0
    private var nextSeq=1,expected=1,srtt = -1,relayRtt = -1,probeId=0,candidateShares=0,active:UDPAddress?,direct=false,closed=false
    private var udpSent=0,udpReceived=0,relaySent=0,relayReceived=0,rejected=0

    init(endpoint:UDPEndpoint,secret:[UInt8],sessionId:[UInt8],role:Int,relay:@escaping ([UInt8])->Void,onMessage:@escaping (String)->Void,onPath:@escaping (Bool,Int,String)->Void,onPeerClosed:@escaping ()->Void){
        self.endpoint=endpoint;self.role=role;tag=Array(sessionId.prefix(8));key=SymmetricKey(data:secret);self.relay=relay;self.onMessage=onMessage;self.onPath=onPath;self.onPeerClosed=onPeerClosed
        startedAt=Self.now();probeBurst=startedAt;lastPeerReceive=startedAt
        endpoint.setHandler(owner:self){[weak self] data,from in guard let self else{return};let packet=[UInt8](data);self.q.async{self.receive(packet,from:from,viaRelay:false)}}
        let t=DispatchSource.makeTimerSource(queue:q);t.schedule(deadline:.now() + .milliseconds(20),repeating:.milliseconds(20),leeway:.milliseconds(4));t.setEventHandler{[weak self] in self?.tick()};t.resume();timer=t
    }
    deinit{timer?.cancel()}
    static func now()->Int64 {Int64(DispatchTime.now().uptimeNanoseconds/1_000_000)}

    // ---- packet encryption ----
    static func be32(_ v:Int)->[UInt8] {(0..<4).map{UInt8(truncatingIfNeeded:UInt32(truncatingIfNeeded:v)>>UInt32(24-8*$0))}}
    static func be64(_ v:Int64)->[UInt8] {(0..<8).map{UInt8(truncatingIfNeeded:UInt64(bitPattern:v)>>UInt64(56-8*$0))}}
    static func int32(_ b:[UInt8],_ at:Int)->Int {Int(Int32(bitPattern:(0..<4).reduce(UInt32(0)){$0<<8|UInt32(b[at+$1])}))}
    static func int64(_ b:[UInt8],_ at:Int)->Int64 {Int64(bitPattern:(0..<8).reduce(UInt64(0)){$0<<8|UInt64(b[at+$1])})}
    static func nonce(_ role:Int,_ c:Int64)->[UInt8] {[0,0,0,UInt8(role)]+be64(c)}
    private func seal(_ type:Int,_ body:[UInt8])->[UInt8] {
        counter+=1;let c=counter;let header=[0x53,0x4B,1,UInt8(role)]+tag+Self.be64(c)
        guard let box=try? AES.GCM.seal([UInt8(type)]+body,using:key,nonce:AES.GCM.Nonce(data:Self.nonce(role,c)),authenticating:header) else{return []}
        return header+Array(box.ciphertext)+Array(box.tag)
    }
    /// Returns type+body, or nil for foreign, forged or replayed packets.
    private func open(_ p:[UInt8])->[UInt8]? {
        guard p.count>=Self.header+17,p[0]==0x53,p[1]==0x4B,p[2]==1,Int(p[3])==1-role,Array(p[4..<12])==tag else{return nil}
        let c=Self.int64(p,12);if c<=highestCounter-4096 || seenCounters.contains(c){return nil}
        guard let box=try? AES.GCM.SealedBox(nonce:AES.GCM.Nonce(data:Self.nonce(1-role,c)),ciphertext:Array(p[Self.header..<(p.count-16)]),tag:Array(p[(p.count-16)...])),
              let plain=try? AES.GCM.open(box,using:key,authenticating:Array(p[0..<Self.header])) else{rejected+=1;return nil}
        guard !plain.isEmpty else{return nil}
        seenCounters.insert(c);seenOrder.append(c);if c>highestCounter{highestCounter=c};if seenOrder.count>4096{seenCounters.remove(seenOrder.removeFirst())}
        return [UInt8](plain)
    }

    // ---- candidates and probing ----
    func addCandidates(_ candidates:[String]){q.async{self.add(candidates)}}
    private func add(_ candidates:[String]){var added=false;for c in candidates{if let a=UDPAddress.parse(c),!remote.contains(a),remote.count<16{remote.append(a);added=true}};if added{probeBurst=Self.now();lastProbe=0}}
    static func encodeCandidates(_ list:[String])->String {list.joined(separator:",")}
    static func decodeCandidates(_ s:String?)->[String] {guard let s,!s.isEmpty else{return []};return Array(s.split(separator:",",maxSplits:16,omittingEmptySubsequences:false).map(String.init).filter{!$0.isEmpty}.prefix(16))}
    func shareCandidates(){q.async{self.share()}}
    private func share(){guard !closed else{return};let body=Array(Self.encodeCandidates(endpoint.candidates).utf8);sendRelay(seal(Self.candidatesType,body));lastCandidates=Self.now();candidateShares+=1;if direct,let a=active{udp(seal(Self.candidatesType,body),a)}}
    private func udp(_ packet:[UInt8],_ to:UDPAddress){udpSent+=1;endpoint.send(Data(packet),to:to)}
    /// Relay packets are signed as Nostr events (a few ms of CPU) off the link queue, in order.
    private func sendRelay(_ packet:[UInt8]){if closed{return};relaySent+=1;relayQueue.async{self.relay(packet)}}
    static func stamp(_ id:Int,_ time:Int64)->[UInt8] {be32(id)+be64(time)}
    private func tick(){
        guard !closed else{return};let t=Self.now()
        if direct && t-lastDirectReceive>3500 {direct=false;active=nil;probeBurst=t;announcePath()}
        let sinceBurst=t-probeBurst,interval:Int64=sinceBurst<2500 ? 50:sinceBurst<12000 ? 250:1000
        if t-lastProbe>=interval && (!direct || t-startedAt<4000) {lastProbe=t;probeId+=1;let probe=seal(Self.probe,Self.stamp(probeId,t));for a in remote{udp(probe,a)}}
        if direct,t-lastPing>=1000,let a=active {lastPing=t;udp(seal(Self.ping,Self.stamp(0,t)),a)}
        // Relay traffic is rationed: keepalive pings only fill silences, candidates are re-sent on a decaying schedule while not direct.
        if !direct && t-lastRelayPing>=6000 && t-lastPeerReceive>=4000 {lastRelayPing=t;sendRelay(seal(Self.ping,Self.stamp(1,t)))}
        if candidateShares==0 || !direct && t-lastCandidates>=(candidateShares<3 ? 2500*Int64(candidateShares):30000) {share()}
        let rto=Int64(direct ? max(120,min(800,(srtt<0 ? 100:srtt)*2+40)):max(1200,min(4000,relayRtt<0 ? 1500:relayRtt*2)))
        for o in unacked where t-o.lastSent>=rto {o.lastSent=t;o.attempts+=1;let packet=seal(Self.data,o.body);if direct,let a=active{udp(packet,a)}
            if t-o.lastRelay>=1200 {o.lastRelay=t;sendRelay(packet)}}
    }

    // ---- reliable messages ----
    @discardableResult func send(_ text:String)->Bool {
        let utf=Array(text.utf8);guard utf.count<=Self.maxText else{return false}
        q.async{guard !self.closed else{return};let t=Self.now(),seq=self.nextSeq;self.nextSeq+=1;let o=Outgoing(seq,Self.be32(seq)+utf,t);o.lastSent=t;self.unacked.append(o)
            let packet=self.seal(Self.data,o.body);if self.direct,let a=self.active{self.udp(packet,a)}else{o.lastRelay=t;self.sendRelay(packet)}}
        return true
    }
    func receiveRelay(_ packet:[UInt8]){q.async{self.receive(packet,from:nil,viaRelay:true)}}
    private func deliver(_ block:@escaping ()->Void){events.async(execute:block)}
    private func receive(_ p:[UInt8],from:UDPAddress?,viaRelay:Bool){
        guard !closed,let plain=open(p) else{return};let type=Int(plain[0]),body=Array(plain[1...]);let t=Self.now();lastPeerReceive=t
        if viaRelay {relayReceived+=1}
        else if let from {udpReceived+=1;if !remote.contains(from) && remote.count<16{remote.append(from)}
            if direct && from==active {lastDirectReceive=t}
            else if direct && t-lastDirectReceive>1500 && type != Self.probe {active=from;lastDirectReceive=t;announcePath()}}
        switch type {
        case Self.probe:if let from,!viaRelay,body.count>=12{udp(seal(Self.probeAck,Array(body[0..<12])),from)}
        case Self.probeAck:if let from,!viaRelay,body.count>=12{let rtt=Int(max(0,t-Self.int64(body,4)));pathRtt[from]=min(pathRtt[from] ?? rtt,rtt)
            let activeRtt=active.flatMap{pathRtt[$0]} ?? rtt
            if !direct || from != active && rtt+3<Int(Double(activeRtt)*0.7) {direct=true;active=from;lastDirectReceive=t;sample(rtt);announcePath()}}
        case Self.ping:if body.count>=12{let pong=seal(Self.pong,Array(body[0..<12]));if viaRelay{sendRelay(pong)}else if let from{udp(pong,from)}}
        case Self.pong:if body.count>=12{let rtt=Int(max(0,t-Self.int64(body,4)));if viaRelay{relayRtt=relayRtt<0 ? rtt:(relayRtt*3+rtt)/4}else{sample(rtt)};announcePath()}
        case Self.candidatesType:add(Self.decodeCandidates(String(decoding:body,as:UTF8.self)))
        case Self.data:if body.count>=4{let seq=Self.int32(body,0)
            if seq>=expected && seq<expected+1024 && early[seq]==nil {early[seq]=String(decoding:body[4...],as:UTF8.self)}
            while let text=early.removeValue(forKey:expected){expected+=1;deliver{self.onMessage(text)}}
            let ack=seal(Self.ack,Self.be32(expected-1));if viaRelay{sendRelay(ack);if direct,let a=active{udp(ack,a)}}else if let from{udp(ack,from)}}
        case Self.ack:if body.count>=4{let upTo=Self.int32(body,0)
            while let o=unacked.first,o.seq<=upTo {unacked.removeFirst();let elapsed=Int(t-o.first)
                if o.attempts==0 && o.lastRelay==0 && !viaRelay{sample(elapsed)}else if o.attempts==0 && o.lastRelay != 0 && viaRelay{relayRtt=relayRtt<0 ? elapsed:(relayRtt*3+elapsed)/4}}}
        case Self.bye:closed=true;deliver{self.onPeerClosed()}
        default:break
        }
    }
    private func sample(_ rtt:Int){srtt=srtt<0 ? rtt:(srtt*7+rtt)/8}
    private func announcePath(){let d=direct,rtt=d ? srtt:relayRtt,route=d ? active?.text ?? "relay":"relay";deliver{self.onPath(d,rtt,route)}}
    var isDirect:Bool {q.sync{direct}}
    var heard:Bool {q.sync{udpReceived+relayReceived>0}}
    var rtt:Int {q.sync{direct ? srtt:relayRtt}}
    var silenceMillis:Int64 {q.sync{Self.now()-lastPeerReceive}}
    var pending:Int {q.sync{unacked.count}}
    var stats:String {q.sync{"direct=\(direct) route=\(active?.text ?? "relay") rtt=\(srtt) relayRtt=\(relayRtt) udp=\(udpSent)/\(udpReceived) relay=\(relaySent)/\(relayReceived) candidates=\(remote.count) rejected=\(rejected)"}}
    /// Says goodbye on both paths; must not be called on the link's own queues.
    func close(){
        q.sync{guard !closed else{return};let bye=seal(Self.bye,[]);if direct,let a=active{for _ in 0..<3{udp(bye,a)}};closed=true;relaySent+=1;relayQueue.async{self.relay(bye)}}
        timer?.cancel();endpoint.clearHandler(owner:self)
    }
}
