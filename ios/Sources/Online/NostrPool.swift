import Foundation

/// A text WebSocket. The app uses URLSessionWebSocketTask; host tests may plug in a plain ws:// client.
protocol WebSocketTransport:AnyObject {func send(_ text:String);func ping();func close()}
/// Opens a socket. onOpen receives the server clock minus the local clock (seconds) when a Date header was sent.
typealias WebSocketFactory=(_ url:URL,_ onOpen:@escaping (Int64?)->Void,_ onMessage:@escaping (String)->Void,_ onClose:@escaping (String)->Void)->WebSocketTransport

/// Free signaling over several public Nostr relays at once, wire-compatible with Android's NostrRelayPool.
/// Events are ephemeral (kind 20000-29999) so relays forward them without storing them; ids and BIP-340
/// signatures are verified on receipt. All relay state lives on one serial queue.
final class NostrPool {
    static let kind=25150
    static let defaultRelays=["wss://yabu.me","wss://relay.snort.social","wss://nostr-pub.wellorder.net","wss://relay.primal.net","wss://nos.lol","wss://nostr.mom","wss://nostr.oxtr.dev","wss://relay.damus.io"]
    struct Event {let id,pubkey,content:String;let createdAt:Int64;let kind:Int;let tags:[[String]];let relay:String
        func tag(_ name:String)->String? {tags.first{$0.count>=2 && $0[0]==name}?[1]}}
    final class Relay {
        let url:String;var socket:WebSocketTransport?;var open=false,connecting=false,refusesWrites=false,gaveUp=false
        var failures=0,strikes=0,accepted=0,rejected=0,received=0;var lastProblem="",offset:Int64?,pausedUntil=Date.distantPast
        init(_ url:String){self.url=url}
        var writable:Bool {open && !refusesWrites && Date()>=pausedUntil}
    }
    let key:Secp256k1.Key;let relays:[Relay];private let factory:WebSocketFactory;private let deliver:DispatchQueue
    private let q=DispatchQueue(label:"nostr.pool");private var subscriptions=[String:NetJSON]();private var seen=[String:Int](),seenOrder=[String]();private var closed=false
    private let clockLock=NSLock();private var clockOffset:Int64=0
    var onEvent:((Event)->Void)?;var onRelays:((Int,Int)->Void)?
    /// A relay finished sending its stored events for a subscription (EOSE): (subscription, relay URL).
    var onEndOfStored:((String,String)->Void)?
    init(urls:[String],key:Secp256k1.Key,factory:@escaping WebSocketFactory,deliver:DispatchQueue){self.key=key;relays=urls.map(Relay.init);self.factory=factory;self.deliver=deliver}
    func start(){q.async{for r in self.relays{self.connect(r)}};pingLater()}
    private func pingLater(){q.asyncAfter(deadline:.now()+25){[weak self] in guard let self,!self.closed else{return};for r in self.relays where r.open{r.socket?.ping()};self.pingLater()}}
    private func connect(_ r:Relay){
        guard !closed,!r.open,!r.connecting,!r.gaveUp,let url=URL(string:r.url) else{return};r.connecting=true
        r.socket=factory(url,{[weak self] offset in guard let self else{return};self.q.async{self.opened(r,offset)}},{[weak self] text in guard let self else{return};self.q.async{self.incoming(r,text)}},{[weak self] reason in guard let self else{return};self.q.async{self.closedSocket(r,reason)}})
    }
    private func opened(_ r:Relay,_ offset:Int64?){guard !closed else{r.socket?.close();return};r.open=true;r.connecting=false;r.failures=0;r.offset=offset;updateClock()
        for (id,filter) in subscriptions{r.socket?.send(NetJSON.array([.string("REQ"),.string(id),filter]).text)};notifyRelays()}
    private func closedSocket(_ r:Relay,_ reason:String){let was=r.open;r.open=false;r.connecting=false;r.socket=nil;r.lastProblem=reason;if reason.hasPrefix("HTTP 4"){r.gaveUp=true};if was{notifyRelays()};updateClock()
        guard !closed,!r.gaveUp else{return};let delay=min(30.0,Double(1<<min(5,r.failures)));r.failures+=1;q.asyncAfter(deadline:.now()+delay){[weak self] in self?.connect(r)}}
    private func notifyRelays(){let open=relays.filter(\.open).count,total=relays.count;deliver.async{self.onRelays?(open,total)}}
    var openCount:Int {q.sync{relays.filter(\.open).count}}
    var writableCount:Int {q.sync{relays.filter(\.writable).count}}
    var acceptedCount:Int {q.sync{relays.reduce(0){$0+$1.accepted}}}
    func subscribe(_ id:String,_ filter:NetJSON){q.async{self.subscriptions[id]=filter;let text=NetJSON.array([.string("REQ"),.string(id),filter]).text;for r in self.relays where r.open{r.socket?.send(text)}}}
    func unsubscribe(_ id:String){q.async{guard self.subscriptions.removeValue(forKey:id) != nil else{return};let text=NetJSON.array([.string("CLOSE"),.string(id)]).text;for r in self.relays where r.open{r.socket?.send(text)}}}
    /// Device clocks may be wrong; relays reject ephemeral events older than about a minute. Use the relays' median clock.
    private func updateClock(){let offsets=relays.filter{$0.open}.compactMap(\.offset).sorted();clockLock.lock();clockOffset=offsets.isEmpty ? 0:abs(offsets[offsets.count/2])>20 ? offsets[offsets.count/2]:0;clockLock.unlock()}
    func now()->Int64 {clockLock.lock();defer{clockLock.unlock()};return Int64(Date().timeIntervalSince1970)+clockOffset}
    static func serialized(_ pubkey:String,_ createdAt:Int64,_ kind:Int,_ tags:[[String]],_ content:String)->String {NetJSON.array([.int(0),.string(pubkey),.int(createdAt),.int(Int64(kind)),.wrap(tags),.string(content)]).text}
    static func eventId(_ pubkey:String,_ createdAt:Int64,_ kind:Int,_ tags:[[String]],_ content:String)->String {Secp256k1.hex(Secp256k1.sha256(Array(serialized(pubkey,createdAt,kind,tags,content).utf8)))}
    func sign(_ tags:[[String]],_ content:String,kind:Int=NostrPool.kind)->NetJSON {
        let created=now(),id=Self.eventId(key.publicHex,created,kind,tags,content);var g=SystemRandomNumberGenerator();let aux=(0..<32).map{_ in UInt8.random(in:0...255,using:&g)}
        let sig=key.sign(Secp256k1.unhex(id)!,aux:aux)
        return .object([("id",.string(id)),("pubkey",.string(key.publicHex)),("created_at",.int(created)),("kind",.int(Int64(kind))),("tags",.wrap(tags)),("content",.string(content)),("sig",.string(Secp256k1.hex(sig)))])
    }
    /// Signs on the caller's thread and hands the event to every relay that currently accepts our writes.
    func publish(_ tags:[[String]],_ content:String){publish(kind:Self.kind,tags,content)}
    func publish(kind:Int,_ tags:[[String]],_ content:String){let e=sign(tags,content,kind:kind),text=NetJSON.array([.string("EVENT"),e]).text,id=e["id"]?.string ?? ""
        q.async{guard !self.closed else{return};self.remember(id);for r in self.relays where r.writable{r.socket?.send(text)}}}
    private func remember(_ id:String){if seen[id] == nil{seen[id]=1;seenOrder.append(id);if seenOrder.count>4096{seen.removeValue(forKey:seenOrder.removeFirst())}}}
    static func parseEvent(_ raw:NetJSON,relay:String)->(Event,String)? {
        guard let id=raw["id"]?.string,let pubkey=raw["pubkey"]?.string,let sig=raw["sig"]?.string,let content=raw["content"]?.string,id.count==64,pubkey.count==64,sig.count==128,content.utf16.count<=16384,
              let created=raw["created_at"]?.int,let kind=raw["kind"]?.int,created>=0,(0...65535).contains(kind),let rows=raw["tags"]?.array,rows.count<=32 else{return nil}
        var tags=[[String]]();for row in rows{guard let items=row.array else{return nil};var tag=[String]();for x in items{guard let s=x.string else{return nil};tag.append(s)};tags.append(tag)}
        guard eventId(pubkey,created,Int(kind),tags,content)==id.lowercased() else{return nil}
        return (Event(id:id.lowercased(),pubkey:pubkey.lowercased(),content:content,createdAt:created,kind:Int(kind),tags:tags,relay:relay),sig)
    }
    static func verified(_ e:Event,_ sig:String)->Bool {guard let m=Secp256k1.unhex(e.id),let p=Secp256k1.unhex(e.pubkey),let s=Secp256k1.unhex(sig) else{return false};return Secp256k1.verify(m,publicKey:p,signature:s)}
    /// Rate limits pause our writes there with exponential backoff; policy refusals (web of trust, auth, PoW, payment)
    /// stop writes to that relay for this session. Reading continues and the other relays carry on.
    static func refusalKind(_ message:String)->Int {let m=message.lowercased()
        if m.hasPrefix("duplicate"){return 0}
        if m.contains("rate") && m.contains("limit") || m.contains("slow down") || m.contains("too many") || m.contains("banned"){return 1}
        if m.hasPrefix("blocked") || m.hasPrefix("restricted") || m.hasPrefix("auth-required") || m.hasPrefix("pow") || ["trust","policy","payment","paid","whitelist","not allowed","kind"].contains(where:m.contains){return 2}
        return 0}
    private func incoming(_ r:Relay,_ text:String){
        guard let msg=try? NetJSON.parse(text),let a=msg.array,let type=a.first?.string else{return}
        if type=="EVENT",a.count>=3 {guard let (e,sig)=Self.parseEvent(a[2],relay:r.url),e.pubkey != key.publicHex,seen[e.id]==nil,Self.verified(e,sig) else{return};remember(e.id);r.received+=1;deliver.async{self.onEvent?(e)}}
        else if type=="OK",a.count>=3 {if case .bool(true)=a[2]{r.accepted+=1}else{r.rejected+=1;r.lastProblem=a.count>3 ? (a[3].string ?? "rejected"):"rejected";switch Self.refusalKind(r.lastProblem){case 1:r.pausedUntil=Date().addingTimeInterval(min(600,30*Double(1<<min(5,r.strikes))));r.strikes+=1;case 2:r.refusesWrites=true;default:break}}}
        else if type=="EOSE",a.count>=2,let sub=a[1].string {let url=r.url;deliver.async{self.onEndOfStored?(sub,url)}}
        else if (type=="NOTICE" || type=="CLOSED"),a.count>=2 {r.lastProblem=a.last?.string ?? r.lastProblem}
    }
    var diagnostics:String {q.sync{relays.map{r in "\(r.url) \(r.open ? "open":"closed") ok=\(r.accepted) rejected=\(r.rejected) received=\(r.received) clock=\(r.offset.map{"\($0)s"} ?? "no Date header")\(r.refusesWrites ? " writes=refused":Date()<r.pausedUntil ? " writes=paused":"")\(r.lastProblem.isEmpty ? "":" last="+r.lastProblem)"}.joined(separator:"\n")}}
    func close(){q.sync{closed=true;for r in relays{r.socket?.close();r.socket=nil;r.open=false}}}
}
