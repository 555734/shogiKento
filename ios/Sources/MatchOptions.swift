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
    /// CPU persona per level: name, rank label (a guide, not a measured strength) and fallback emblem glyph/colour.
    static let cpuNames=["からくり丸","ちょうちん坊","たぬ吉","こん狐","ふくろう先生","鶴之丞","鎧からくり","木彫りの龍","能面の軍師","金鳳凰"]
    static let cpuRanks=["10級","8級","6級","4級","2級","初段","二段","三段","四段","五段"]
    static let cpuGlyphs=["丸","灯","狸","狐","梟","鶴","鎧","龍","面","凰"]
    static let cpuColors:[UInt32]=[0x6f8f4a,0xc77d2a,0x8a6a3a,0xd0702a,0x5c6f8a,0x7f8fa0,0x6d3f2a,0x4a2f1e,0x7a1e2a,0xc9962e]
    var cpuName:String {Self.cpuNames[max(1,min(10,level))-1]}
    var cpuRank:String {Self.cpuRanks[max(1,min(10,level))-1]}
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
