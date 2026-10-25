import Foundation
import Security

/// Keeps the online account on this device: the secret key in the Keychain (never in backups as plain data, never
/// sent anywhere), the rating and history as JSON in UserDefaults. Tests pass their own service name and defaults.
struct OnlineAccountStore {
    let service:String,defaults:UserDefaults
    static let standard=OnlineAccountStore(service:"com.presentsoft.shogi3minutes.online-account",defaults:.standard)
    private var query:[String:Any] {[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"secret"]}
    func loadSecret()->[UInt8]? {
        var q=query;q[kSecReturnData as String]=true;q[kSecMatchLimit as String]=kSecMatchLimitOne;var out:CFTypeRef?
        if SecItemCopyMatching(q as CFDictionary,&out)==errSecSuccess,let data=out as? Data,data.count==32{return [UInt8](data)}
        return defaults.data(forKey:service+".secret").flatMap{$0.count==32 ? [UInt8]($0):nil}
    }
    /// Prefers the Keychain; where it is unavailable (an unsigned test host) the app-private defaults keep the secret.
    @discardableResult func saveSecret(_ secret:[UInt8])->Bool {
        SecItemDelete(query as CFDictionary);var q=query;q[kSecValueData as String]=Data(secret);q[kSecAttrAccessible as String]=kSecAttrAccessibleAfterFirstUnlock
        if SecItemAdd(q as CFDictionary,nil)==errSecSuccess{defaults.removeObject(forKey:service+".secret");return true}
        defaults.set(Data(secret),forKey:service+".secret");return true
    }
    func deleteAll(){SecItemDelete(query as CFDictionary);defaults.removeObject(forKey:service);defaults.removeObject(forKey:service+".secret")}
    func loadAccount()->OnlineAccount? {defaults.data(forKey:service).flatMap{try? JSONDecoder().decode(OnlineAccount.self,from:$0)}}
    func saveAccount(_ a:OnlineAccount){if let data=try? JSONEncoder().encode(a){defaults.set(data,forKey:service)}}
    /// The account key and record, created on first use. A rated game left unfinished last time is settled as a loss.
    func load()->(secret:[UInt8],key:Secp256k1.Key,account:OnlineAccount,abandoned:OnlineAccount.Game?) {
        let key:Secp256k1.Key,secret:[UInt8]
        if let s=loadSecret(),let k=Secp256k1.Key(secret:s){key=k;secret=s}
        else{var g=SystemRandomNumberGenerator();var k:Secp256k1.Key?;var s=[UInt8]();while k==nil{s=(0..<32).map{_ in UInt8.random(in:0...255,using:&g)};k=Secp256k1.Key(secret:s)};saveSecret(s);key=k!;secret=s;defaults.removeObject(forKey:service)}
        var account=loadAccount() ?? OnlineAccount(name:OnlineAccount.defaultName(key));let abandoned=account.resolveAbandoned(now:Int64(Date().timeIntervalSince1970));if abandoned != nil{saveAccount(account)}
        return (secret,key,account,abandoned)
    }
}
