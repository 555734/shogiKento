#if canImport(Glibc)
import Foundation
import Glibc
@testable import OnlineCore

/// Test-only ws:// client over a POSIX socket (Linux Foundation has no WebSocket support).
final class PlainWebSocket:WebSocketTransport {
    private var fd:Int32 = -1;private let lock=NSLock();private var finished=false
    private let onOpen:(Int64?)->Void,onMessage:(String)->Void,onClose:(String)->Void
    static let factory:WebSocketFactory={url,open,message,close in PlainWebSocket(url,open,message,close)}
    init(_ url:URL,_ onOpen:@escaping (Int64?)->Void,_ onMessage:@escaping (String)->Void,_ onClose:@escaping (String)->Void){
        self.onOpen=onOpen;self.onMessage=onMessage;self.onClose=onClose
        Thread{[self] in run(url)}.start()
    }
    private func run(_ url:URL){
        guard let host=url.host,let port=url.port else{finish("bad url");return}
        let s=socket(AF_INET,Int32(SOCK_STREAM.rawValue),0);var a=sockaddr_in();a.sin_family=sa_family_t(AF_INET);a.sin_port=UInt16(port).bigEndian;inet_pton(AF_INET,host,&a.sin_addr)
        guard withUnsafePointer(to:&a,{$0.withMemoryRebound(to:sockaddr.self,capacity:1){connect(s,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}})==0 else{Glibc.close(s);finish("connect failed");return}
        lock.lock();fd=s;lock.unlock()
        write(Array("GET \(url.path.isEmpty ? "/":url.path) HTTP/1.1\r\nHost: \(host):\(port)\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n".utf8))
        var buffer=[UInt8]();var head=""
        while true {guard let chunk=read() else{finish("closed during upgrade");return};buffer+=chunk
            if let r=String(decoding:buffer,as:UTF8.self).range(of:"\r\n\r\n"){head=String(String(decoding:buffer,as:UTF8.self)[..<r.lowerBound]);buffer=Array(buffer[(head.utf8.count+4)...]);break}}
        guard head.hasPrefix("HTTP/1.1 101") else{finish("HTTP "+head.prefix(12).dropFirst(9));return}
        onOpen(nil)
        while true {
            while let (op,payload,used)=Self.frame(buffer){buffer.removeFirst(used)
                switch op {case 1:onMessage(String(decoding:payload,as:UTF8.self));case 8:finish("closed by relay");return;case 9:sendFrame(10,payload);default:break}}
            guard let chunk=read() else{finish("closed");return};buffer+=chunk
        }
    }
    static func frame(_ b:[UInt8])->(Int,[UInt8],Int)? {
        guard b.count>=2 else{return nil};let op=Int(b[0]&15);var len=Int(b[1]&127),at=2;let masked=b[1]&128 != 0
        if len==126 {guard b.count>=4 else{return nil};len=Int(b[2])<<8|Int(b[3]);at=4}else if len==127{guard b.count>=10 else{return nil};len=(2..<10).reduce(0){$0<<8|Int(b[$1])};at=10}
        var mask=[UInt8]();if masked{guard b.count>=at+4 else{return nil};mask=Array(b[at..<at+4]);at+=4}
        guard b.count>=at+len else{return nil};var p=Array(b[at..<at+len]);if masked{for i in 0..<p.count{p[i]^=mask[i%4]}};return (op,p,at+len)
    }
    private func read()->[UInt8]? {var b=[UInt8](repeating:0,count:65536);lock.lock();let s=fd;lock.unlock();guard s>=0 else{return nil};let n=recv(s,&b,b.count,0);return n>0 ? Array(b[0..<n]):nil}
    private func write(_ data:[UInt8]){lock.lock();defer{lock.unlock()};guard fd>=0 else{return};var off=0;while off<data.count{let n=data[off...].withUnsafeBytes{Glibc.send(fd,$0.baseAddress,$0.count,Int32(MSG_NOSIGNAL))};if n<=0{return};off+=n}}
    private func sendFrame(_ op:UInt8,_ p:[UInt8]){var f:[UInt8]=[0x80|op];if p.count<126{f.append(0x80|UInt8(p.count))}else{f+=[UInt8(0x80|126),UInt8(p.count>>8),UInt8(p.count&255)]}
        let mask:[UInt8]=(0..<4).map{_ in UInt8.random(in:0...255)};f+=mask;f+=p.enumerated().map{$0.element^mask[$0.offset%4]};write(f)}
    func send(_ text:String){sendFrame(1,Array(text.utf8))}
    func ping(){sendFrame(9,[])}
    func close(){lock.lock();let s=fd;fd = -1;lock.unlock();if s>=0{shutdown(s,Int32(SHUT_RDWR));Glibc.close(s)};finish("closed locally")}
    private func finish(_ reason:String){lock.lock();let first = !finished;finished=true;lock.unlock();if first{onClose(reason)}}
}
#endif
