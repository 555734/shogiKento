#if canImport(Darwin)
import Foundation

/// Relay transport on Apple platforms: system TLS, certificate validation and proxies via URLSessionWebSocketTask.
final class URLSessionWebSocket:NSObject,WebSocketTransport,URLSessionWebSocketDelegate {
    private var session:URLSession!;private var task:URLSessionWebSocketTask!
    private let onOpen:(Int64?)->Void,onMessage:(String)->Void,onClose:(String)->Void;private let lock=NSLock();private var finished=false
    static let factory:WebSocketFactory={url,open,message,close in URLSessionWebSocket(url:url,onOpen:open,onMessage:message,onClose:close)}
    init(url:URL,onOpen:@escaping (Int64?)->Void,onMessage:@escaping (String)->Void,onClose:@escaping (String)->Void){
        self.onOpen=onOpen;self.onMessage=onMessage;self.onClose=onClose;super.init()
        let config=URLSessionConfiguration.ephemeral;config.timeoutIntervalForRequest=8;config.waitsForConnectivity=false
        session=URLSession(configuration:config,delegate:self,delegateQueue:nil)
        var request=URLRequest(url:url,timeoutInterval:8);request.setValue("ShogiKento-iOS/1.0",forHTTPHeaderField:"User-Agent")
        task=session.webSocketTask(with:request);task.maximumMessageSize=256*1024;task.resume();receive()
    }
    private func receive(){task.receive{[weak self] result in guard let self else{return}
        switch result{case .success(.string(let text)):self.onMessage(text);self.receive();case .success(.data):self.receive();case .success:self.receive();case .failure(let error):self.finish(self.reason(error))}}}
    private func reason(_ error:Error)->String {if let http=task.response as? HTTPURLResponse,http.statusCode != 101{return "HTTP \(http.statusCode)"};return error.localizedDescription}
    private func finish(_ reason:String){lock.lock();let first = !finished;finished=true;lock.unlock();guard first else{return};onClose(reason);session.invalidateAndCancel()}
    func urlSession(_ session:URLSession,webSocketTask:URLSessionWebSocketTask,didOpenWithProtocol protocol:String?){
        var offset:Int64?
        if let http=webSocketTask.response as? HTTPURLResponse,let date=http.value(forHTTPHeaderField:"Date"){let f=DateFormatter();f.locale=Locale(identifier:"en_US_POSIX");f.timeZone=TimeZone(identifier:"GMT");f.dateFormat="EEE, dd MMM yyyy HH:mm:ss zzz";if let d=f.date(from:date){offset=Int64(d.timeIntervalSince1970)-Int64(Date().timeIntervalSince1970)}}
        onOpen(offset)
    }
    func urlSession(_ session:URLSession,webSocketTask:URLSessionWebSocketTask,didCloseWith closeCode:URLSessionWebSocketTask.CloseCode,reason:Data?){finish("closed by relay")}
    func urlSession(_ session:URLSession,task:URLSessionTask,didCompleteWithError error:Error?){finish(error.map{reason($0)} ?? "closed")}
    func send(_ text:String){task.send(.string(text)){[weak self] error in if let error{self?.finish(error.localizedDescription)}}}
    func ping(){task.sendPing{_ in}}
    func close(){task.cancel(with:.normalClosure,reason:nil);finish("closed locally")}
}
#endif
