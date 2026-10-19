import Foundation

struct MatchOptions: Codable, Equatable {
    static let times=[1,3,5,10,15,30]
    static let nodeBudgets=[100,300,800,2000,5000,10000,20000,50000,100000,250000]
    static let timeBudgets=[50,75,100,150,250,400,600,750,1200,2000]
    var minutes=3
    var humanSide=1
    var level=8
    var nodes:Int {Self.nodeBudgets[max(1,min(10,level))-1]}
    var milliseconds:Int {Self.timeBudgets[max(1,min(10,level))-1]}
    var seconds:Double {Double(minutes*60)}
    var title:String {"\(minutes)分切れ負け・AI Lv.\(level)"}
    static func load()->Self {
        let defaults=UserDefaults.standard
        let minutes=defaults.integer(forKey:"match_minutes"),side=defaults.integer(forKey:"match_human_side"),level=defaults.integer(forKey:"match_level")
        return Self(minutes:times.contains(minutes) ? minutes:3,humanSide:side == -1 ? -1:1,level:(1...10).contains(level) ? level:(defaults.object(forKey:"ai_nodes")==nil ? 8:defaults.integer(forKey:"ai_nodes")<=3000 ? 4:defaults.integer(forKey:"ai_nodes")<=10000 ? 6:8))
    }
    func save(){let d=UserDefaults.standard;d.set(minutes,forKey:"match_minutes");d.set(humanSide,forKey:"match_human_side");d.set(level,forKey:"match_level")}
    func valid()->Bool {Self.times.contains(minutes) && [1,-1].contains(humanSide) && (1...10).contains(level)}
}

extension GameRecord {
    static func match(_ options:MatchOptions)->GameRecord {
        var record=GameRecord();record.matchOptions=options;record.title=options.title
        record.sente=options.humanSide==1 ? "あなた":"CPU Lv.\(options.level)"
        record.gote=options.humanSide == -1 ? "あなた":"CPU Lv.\(options.level)"
        return record
    }
}
