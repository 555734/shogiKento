import Foundation
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

/// A literal UDP address. Candidates are never host names, so a peer can never make us resolve names.
struct UDPAddress:Hashable {
    let host:String,port:UInt16,v6:Bool
    var text:String {v6 ? "[\(host)]:\(port)":"\(host):\(port)"}
    static func parse(_ candidate:String)->UDPAddress? {
        guard candidate.count<=64 else{return nil};var host:Substring,port:Substring,v6=false
        if candidate.hasPrefix("["){guard let end=candidate.range(of:"]:") else{return nil};host=candidate[candidate.index(after:candidate.startIndex)..<end.lowerBound];port=candidate[end.upperBound...];v6=true
            guard !host.isEmpty,host.allSatisfy({$0.isHexDigit || $0==":" || $0=="."}) else{return nil}}
        else{guard let colon=candidate.lastIndex(of:":") else{return nil};host=candidate[..<colon];port=candidate[candidate.index(after:colon)...];let parts=host.split(separator:".",omittingEmptySubsequences:false)
            guard parts.count==4,parts.allSatisfy({!$0.isEmpty && $0.count<=3 && $0.allSatisfy(\.isNumber)}) else{return nil}}
        guard let p=UInt16(port),p>0,let canonical=canonical(String(host),v6:v6) else{return nil};let a=UDPAddress(host:canonical,port:p,v6:v6);return a.usable ? a:nil
    }
    static func canonical(_ host:String,v6:Bool)->String? {
        if v6 {var a=in6_addr();guard inet_pton(AF_INET6,host,&a)==1 else{return nil};var buf=[CChar](repeating:0,count:Int(INET6_ADDRSTRLEN));guard inet_ntop(AF_INET6,&a,&buf,socklen_t(buf.count)) != nil else{return nil};return String(cString:buf)}
        var a=in_addr();guard inet_pton(AF_INET,host,&a)==1 else{return nil};var buf=[CChar](repeating:0,count:Int(INET_ADDRSTRLEN));guard inet_ntop(AF_INET,&a,&buf,socklen_t(buf.count)) != nil else{return nil};return String(cString:buf)
    }
    var octets:[UInt8] {if v6{var a=in6_addr();_=inet_pton(AF_INET6,host,&a);return withUnsafeBytes(of:&a){Array($0)}};var a=in_addr();_=inet_pton(AF_INET,host,&a);return withUnsafeBytes(of:&a){Array($0)}}
    var loopback:Bool {let o=octets;return v6 ? o==[UInt8](repeating:0,count:15)+[1]:o[0]==127}
    /// LAN ranges. iOS asks the user for local-network access the first time an app sends to them, so the
    /// online mode does not use them: same-network players connect via their public or IPv6 address, or the relay.
    var localNetwork:Bool {let o=octets
        if v6 {return o[0]&0xfe==0xfc || o[0]==0xfe && o[1]&0xc0==0x80 || o[0]==0xfe && o[1]&0xc0==0xc0}
        return o[0]==10 || o[0]==172 && o[1]&0xf0==16 || o[0]==192 && o[1]==168 || o[0]==169 && o[1]==254 || o[0]==100 && o[1]&0xc0==64}
    var usable:Bool {let o=octets
        if loopback{return UDPEndpoint.allowLoopback}
        if v6 ? o.allSatisfy({$0==0}) || o[0]==0xff:o[0]==0 || o[0]>=224 || o==[255,255,255,255]{return false}
        return !(UDPEndpoint.avoidLocalNetwork && localNetwork)
    }
}

/// One dual-stack UDP socket per online session (same role as Android's UdpEndpoint): host candidates,
/// server-reflexive candidates from free public STUN servers, and encrypted game packets for a P2PLink.
final class UDPEndpoint {
    static let defaultSTUN=["stun.l.google.com:19302","stun1.l.google.com:19302","stun.cloudflare.com:3478"]
    static var allowLoopback=false,avoidLocalNetwork=true
    private let fd:Int32;let port:UInt16;private let dualStack:Bool
    private let lock=NSLock();private var closed=false,host=[UDPAddress](),reflexive=[UDPAddress](),pendingStun=Set<[UInt8]>(),mappedPorts=[String:Set<UInt16>]()
    private(set) var stunReplies=0
    var dropPercent=0
    private var handlerStore:((Data,UDPAddress)->Void)?,handlerOwner:ObjectIdentifier?;private var candidateStore:(([String])->Void)?
    /// The newest link receives the packets; a link that closes later must not detach its successor.
    func setHandler(owner:AnyObject,_ handler:@escaping (Data,UDPAddress)->Void){lock.lock();handlerStore=handler;handlerOwner=ObjectIdentifier(owner);lock.unlock()}
    func clearHandler(owner:AnyObject){lock.lock();if handlerOwner==ObjectIdentifier(owner){handlerStore=nil;handlerOwner=nil};lock.unlock()}
    private var handler:((Data,UDPAddress)->Void)? {lock.lock();defer{lock.unlock()};return handlerStore}
    var candidateListener:(([String])->Void)? {get{lock.lock();defer{lock.unlock()};return candidateStore}set{lock.lock();candidateStore=newValue;lock.unlock()}}
    private let timer=DispatchQueue(label:"udp.timer")
    init()throws {
        #if canImport(Darwin)
        let dgram=SOCK_DGRAM
        #else
        let dgram=Int32(SOCK_DGRAM.rawValue)
        #endif
        var s=socket(AF_INET6,dgram,Int32(IPPROTO_UDP)),v6=true
        if s>=0 {var off:Int32=0;setsockopt(s,Int32(IPPROTO_IPV6),IPV6_V6ONLY,&off,socklen_t(MemoryLayout<Int32>.size));var a=sockaddr_in6();a.sin6_family=sa_family_t(AF_INET6);a.sin6_addr=in6addr_any
            #if canImport(Darwin)
            a.sin6_len=UInt8(MemoryLayout<sockaddr_in6>.size)
            #endif
            if withUnsafePointer(to:&a,{$0.withMemoryRebound(to:sockaddr.self,capacity:1){bind(s,$0,socklen_t(MemoryLayout<sockaddr_in6>.size))}}) != 0{Self.closeSocket(s);s = -1}}
        if s<0 {v6=false;s=socket(AF_INET,dgram,Int32(IPPROTO_UDP));guard s>=0 else{throw ShogiNetError("UDPソケットを作成できません")};var a=sockaddr_in();a.sin_family=sa_family_t(AF_INET)
            #if canImport(Darwin)
            a.sin_len=UInt8(MemoryLayout<sockaddr_in>.size)
            #endif
            guard withUnsafePointer(to:&a,{$0.withMemoryRebound(to:sockaddr.self,capacity:1){bind(s,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}})==0 else{Self.closeSocket(s);throw ShogiNetError("UDPポートを確保できません")}}
        var timeout=timeval(tv_sec:0,tv_usec:250000);setsockopt(s,SOL_SOCKET,SO_RCVTIMEO,&timeout,socklen_t(MemoryLayout<timeval>.size))
        var storage=sockaddr_storage(),length=socklen_t(MemoryLayout<sockaddr_storage>.size);_=withUnsafeMutablePointer(to:&storage){$0.withMemoryRebound(to:sockaddr.self,capacity:1){getsockname(s,$0,&length)}}
        fd=s;dualStack=v6;port=Self.address(storage)?.port ?? 0
        let thread=Thread{[weak self] in self?.receiveLoop()};thread.name="udp-recv";thread.start()
    }
    deinit{close()}
    /// Host candidates first (they need no STUN), then reflexive ones as replies arrive.
    var candidates:[String] {lock.lock();defer{lock.unlock()};var all=host.map(\.text);for r in reflexive where !all.contains(r.text){all.append(r.text)};return all}
    /// True when STUN servers saw different public ports: an endpoint-dependent (symmetric) NAT that usually needs the relay.
    var symmetricNat:Bool {lock.lock();defer{lock.unlock()};return mappedPorts.values.contains{$0.count>1}}
    static func hostAddresses(port:UInt16)->[UDPAddress] {
        var out=[UDPAddress]();var list:UnsafeMutablePointer<ifaddrs>?;guard getifaddrs(&list)==0 else{return out};defer{freeifaddrs(list)}
        var p=list;while let i=p{p=i.pointee.ifa_next;guard let sa=i.pointee.ifa_addr,(i.pointee.ifa_flags&UInt32(IFF_UP)) != 0 else{continue}
            var host=[CChar](repeating:0,count:Int(INET6_ADDRSTRLEN));var v6=false
            if sa.pointee.sa_family==sa_family_t(AF_INET){sa.withMemoryRebound(to:sockaddr_in.self,capacity:1){var a=$0.pointee.sin_addr;_=inet_ntop(AF_INET,&a,&host,socklen_t(host.count))}}
            else if sa.pointee.sa_family==sa_family_t(AF_INET6){v6=true;sa.withMemoryRebound(to:sockaddr_in6.self,capacity:1){var a=$0.pointee.sin6_addr;_=inet_ntop(AF_INET6,&a,&host,socklen_t(host.count))}}else{continue}
            let a=UDPAddress(host:String(cString:host),port:port,v6:v6);if a.usable && !out.contains(a){out.append(a)};if out.count>=8{break}}
        return out
    }
    func gather(_ stunServers:[String]){
        let found=dualStack ? Self.hostAddresses(port:port):Self.hostAddresses(port:port).filter{!$0.v6};lock.lock();host=found;lock.unlock();announce()
        timer.async{[weak self] in for server in stunServers{guard let self,!self.isClosed,let colon=server.lastIndex(of:":") else{return};let name=String(server[..<colon]),service=String(server[server.index(after:colon)...])
            for target in Self.resolve(name,service) where self.dualStack || !target.v6{for delay in [0.0,0.25,0.8,2.0]{self.timer.asyncAfter(deadline:.now()+delay){[weak self] in self?.stun(target)}}}}}
    }
    static func resolve(_ name:String,_ service:String)->[UDPAddress] {
        var hints=addrinfo();hints.ai_family=AF_UNSPEC
        #if canImport(Darwin)
        hints.ai_socktype=SOCK_DGRAM
        #else
        hints.ai_socktype=Int32(SOCK_DGRAM.rawValue)
        #endif
        var result:UnsafeMutablePointer<addrinfo>?;guard getaddrinfo(name,service,&hints,&result)==0 else{return []};defer{freeaddrinfo(result)}
        var out=[UDPAddress]();var p=result;while let i=p{p=i.pointee.ai_next;if let sa=i.pointee.ai_addr{var storage=sockaddr_storage();memcpy(&storage,sa,Int(i.pointee.ai_addrlen));if let a=address(storage),!out.contains(a){out.append(a)}}};return out
    }
    private var isClosed:Bool {lock.lock();defer{lock.unlock()};return closed}
    private func announce(){if let l=candidateListener{l(candidates)}}
    private func stun(_ server:UDPAddress){var tx=[UInt8](repeating:0,count:12);var g=SystemRandomNumberGenerator();for i in 0..<12{tx[i]=UInt8.random(in:0...255,using:&g)}
        lock.lock();pendingStun.insert(tx);lock.unlock();send(Data([0,1,0,0,0x21,0x12,0xA4,0x42]+tx),to:server)}
    /// Returns ip:port from a STUN Binding success response (XOR-MAPPED-ADDRESS, legacy MAPPED-ADDRESS accepted).
    static func parseStun(_ p:[UInt8],pending:inout Set<[UInt8]>,requireTransaction:Bool=true)->String? {
        guard p.count>=20,p[0]==1,p[1]==1,p[4]==0x21,p[5]==0x12,p[6]==0xA4,p[7]==0x42 else{return nil};let tx=Array(p[8..<20])
        if requireTransaction{guard pending.remove(tx) != nil else{return nil}}
        let len=min(p.count-20,Int(p[2])<<8|Int(p[3]));var i=20,mapped:String?
        while i+4<=20+len {let type=Int(p[i])<<8|Int(p[i+1]),alen=Int(p[i+2])<<8|Int(p[i+3]),v=i+4;guard v+alen<=p.count else{break}
            let xor=type==0x0020 || type==0x8020
            if (xor || type==0x0001) && alen>=8 {let family=p[v+1];var port=UInt16(p[v+2])<<8|UInt16(p[v+3]);if xor{port^=0x2112}
                if family==2 && alen<20{break};var addr=Array(p[(v+4)..<(v+4+(family==2 ? 16:4))]);if xor{for k in 0..<addr.count{addr[k]^=k<4 ? p[4+k]:p[8+k-4]}}
                var buf=[CChar](repeating:0,count:Int(INET6_ADDRSTRLEN));let ok=addr.withUnsafeBytes{inet_ntop(family==2 ? AF_INET6:AF_INET,$0.baseAddress,&buf,socklen_t(buf.count))} != nil
                if ok {let a=UDPAddress(host:String(cString:buf),port:port,v6:family==2);if xor || mapped==nil{mapped=a.text}}}
            i=v+((alen+3) & ~3)}
        return mapped
    }
    /// Never sends to LAN addresses on iOS (see UDPAddress.localNetwork), even when a peer's packet came from one.
    func send(_ data:Data,to target:UDPAddress){
        if isClosed || !target.usable || dropPercent>0 && Int.random(in:0..<100)<dropPercent{return}
        if dualStack {var a=sockaddr_in6();a.sin6_family=sa_family_t(AF_INET6);a.sin6_port=target.port.bigEndian
            #if canImport(Darwin)
            a.sin6_len=UInt8(MemoryLayout<sockaddr_in6>.size)
            #endif
            guard inet_pton(AF_INET6,target.v6 ? target.host:"::ffff:"+target.host,&a.sin6_addr)==1 else{return}
            _=data.withUnsafeBytes{bytes in withUnsafePointer(to:&a){$0.withMemoryRebound(to:sockaddr.self,capacity:1){sendto(fd,bytes.baseAddress,data.count,0,$0,socklen_t(MemoryLayout<sockaddr_in6>.size))}}}}
        else {guard !target.v6 else{return};var a=sockaddr_in();a.sin_family=sa_family_t(AF_INET);a.sin_port=target.port.bigEndian
            #if canImport(Darwin)
            a.sin_len=UInt8(MemoryLayout<sockaddr_in>.size)
            #endif
            guard inet_pton(AF_INET,target.host,&a.sin_addr)==1 else{return}
            _=data.withUnsafeBytes{bytes in withUnsafePointer(to:&a){$0.withMemoryRebound(to:sockaddr.self,capacity:1){sendto(fd,bytes.baseAddress,data.count,0,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}}}}
    }
    static func address(_ s:sockaddr_storage)->UDPAddress? {
        var storage=s;var buf=[CChar](repeating:0,count:Int(INET6_ADDRSTRLEN))
        if storage.ss_family==sa_family_t(AF_INET6){return withUnsafePointer(to:&storage){$0.withMemoryRebound(to:sockaddr_in6.self,capacity:1){p in var a=p.pointee.sin6_addr;let bytes=withUnsafeBytes(of:&a){Array($0)}
            if bytes[0..<10].allSatisfy({$0==0}) && bytes[10]==0xff && bytes[11]==0xff{return UDPAddress(host:bytes[12..<16].map(String.init).joined(separator:"."),port:UInt16(bigEndian:p.pointee.sin6_port),v6:false)}
            guard inet_ntop(AF_INET6,&a,&buf,socklen_t(buf.count)) != nil else{return nil};return UDPAddress(host:String(cString:buf),port:UInt16(bigEndian:p.pointee.sin6_port),v6:true)}}}
        if storage.ss_family==sa_family_t(AF_INET){return withUnsafePointer(to:&storage){$0.withMemoryRebound(to:sockaddr_in.self,capacity:1){p in var a=p.pointee.sin_addr;guard inet_ntop(AF_INET,&a,&buf,socklen_t(buf.count)) != nil else{return nil};return UDPAddress(host:String(cString:buf),port:UInt16(bigEndian:p.pointee.sin_port),v6:false)}}}
        return nil
    }
    private func receiveLoop(){
        var buffer=[UInt8](repeating:0,count:2048)
        while !isClosed {var storage=sockaddr_storage(),length=socklen_t(MemoryLayout<sockaddr_storage>.size)
            let n=buffer.withUnsafeMutableBytes{b in withUnsafeMutablePointer(to:&storage){$0.withMemoryRebound(to:sockaddr.self,capacity:1){recvfrom(fd,b.baseAddress,b.count,0,$0,&length)}}}
            // A receive timeout is EAGAIN. Other errors (iOS can reclaim a suspended app's socket) must not spin the CPU;
            // the game then continues over the relay.
            if n<0 {let e=errno;if e != EAGAIN && e != EWOULDBLOCK && e != EINTR{usleep(100_000)};continue}
            guard n>0,let from=Self.address(storage) else{continue};let packet=Array(buffer[0..<n])
            if n>=20 && packet[0]==1 && packet[1]==1 {lock.lock();let c=Self.parseStun(packet,pending:&pendingStun);if let c,let m=UDPAddress.parse(c){stunReplies+=1;mappedPorts[m.host,default:[]].insert(m.port);let added = !reflexive.contains(m);if added{reflexive.append(m)};lock.unlock();if added{announce()}}else{lock.unlock()};continue}
            handler?(Data(packet),from)
        }
    }
    func close(){lock.lock();let first = !closed;closed=true;handlerStore=nil;candidateStore=nil;lock.unlock();if first{Self.closeSocket(fd)}}
    private static func closeSocket(_ s:Int32){
        #if canImport(Darwin)
        _=Darwin.close(s)
        #else
        _=Glibc.close(s)
        #endif
    }
}
struct ShogiNetError:LocalizedError {let message:String;init(_ m:String){message=m};var errorDescription:String? {message}}
