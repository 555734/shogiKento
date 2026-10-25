import SwiftUI
import UIKit

private let gold=Color(red:1,green:0.86,blue:0.59),cream=Color(red:1,green:0.94,blue:0.81)
/// The online account: name, player ID, rating with its history, recent rated games, the public ranking and the
/// transfer code for moving the account to another device.
struct AccountView:View {
    @EnvironmentObject var model:AppModel
    @Environment(\.dismiss) var dismiss
    @State private var renaming=false
    @State private var newName=""
    @State private var showCode=false
    @State private var restoring=false
    @State private var code=""
    @State private var confirmRestore=false
    @State private var message:String?
    var body:some View {NavigationStack{List{profile;recent;rankingSection;transfer}.navigationTitle("アカウント").toolbar{Button("閉じる"){dismiss()}.accessibilityIdentifier("account_close")}}
        .alert("名前を変更",isPresented:$renaming){TextField("16文字まで",text:$newName).accessibilityIdentifier("account_name_field");Button("キャンセル",role:.cancel){};Button("変更"){message=model.renameAccount(newName) ? nil:"名前を入力してください"}.accessibilityIdentifier("account_name_save")}message:{Text("対局相手とランキングに表示されます")}
        .alert("引き継ぎコード",isPresented:$showCode){Button("コピー"){UIPasteboard.general.string=model.transferCode};Button("閉じる",role:.cancel){}}message:{Text(model.transferCode+"\n\n新しい端末の「引き継ぎコードで復元」に入力します。他人には絶対に教えないでください。")}
        .alert("引き継ぎコードで復元",isPresented:$restoring){TextField("nsec1…",text:$code).textInputAutocapitalization(.never).autocorrectionDisabled().accessibilityIdentifier("account_code_field");Button("キャンセル",role:.cancel){};Button("次へ"){confirmRestore=true}}message:{Text("別の端末で表示した引き継ぎコードを入力してください")}
        .alert("この端末のアカウントを置き換えますか？",isPresented:$confirmRestore){Button("キャンセル",role:.cancel){};Button("置き換える",role:.destructive){message=model.restoreAccount(code) ? nil:"引き継ぎコードが正しくありません"}}message:{Text("現在のアカウント（\(model.account.name)・レート\(model.account.rating)）は、引き継ぎコードを控えていない場合は元に戻せません。")}
    }
    private var shortId:String {String(Nip19.encode("npub",model.accountKey.publicKey).prefix(16))+"…"}
    private var graphPoints:[Int] {guard let first=model.account.history.first else{return []};return [first.before]+model.account.history.map(\.after)}
    private var profile:some View {
        Section {
            HStack {VStack(alignment:.leading,spacing:4){Text(model.account.name).font(.title2.bold()).accessibilityIdentifier("account_name");Text(shortId).font(.caption.monospaced()).foregroundStyle(.secondary).textSelection(.enabled)}
                Spacer();Button("名前を変更"){newName=model.account.name;renaming=true}.buttonStyle(.bordered).accessibilityIdentifier("account_rename")}
            HStack(alignment:.firstTextBaseline){Text("レート").foregroundStyle(.secondary);Text("\(model.account.rating)").font(.system(size:40,weight:.bold,design:.rounded)).foregroundStyle(gold).accessibilityIdentifier("account_rating");if model.account.provisional{Text("暫定（\(model.account.games)/\(Rating.provisionalGames)局）").font(.caption).foregroundStyle(.orange)}}
            Text("\(model.account.games)局 · \(model.account.wins)勝 \(model.account.losses)敗 \(model.account.draws)分").accessibilityIdentifier("account_record")
            if graphPoints.count>=3{RatingGraph(points:graphPoints).frame(height:90).accessibilityLabel("レートの推移")}
        }header:{Text("プロフィール")}footer:{Text("ランダム対戦がレート戦です（Elo方式・初期1500・最初の10局は変動大）。ルーム対戦はレートが変わりません。対局中の退出・アプリの終了は負けになります。")}
    }
    private var recent:some View {
        Section {
            if model.account.history.isEmpty{Text("まだレート戦の記録はありません").foregroundStyle(.secondary)}
            ForEach(Array(model.account.history.suffix(30).reversed())){g in GameRow(game:g)}
        }header:{Text("最近のレート戦")}
    }
    private var rankingSection:some View {
        Section {
            Button("ランキングを読み込む"){model.loadRanking()}.accessibilityIdentifier("account_ranking")
            if !model.rankingStatus.isEmpty{Text(model.rankingStatus).font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("ranking_status")}
            ForEach(Array(model.ranking.enumerated()),id:\.element.pubkey){i,p in RankRow(rank:i+1,profile:p,mine:p.pubkey==model.accountKey.publicHex)}
        }header:{Text("ランキング")}footer:{Text("公開リレーに置かれたプロフィールを集めた一覧です。値は各プレイヤーの端末が計算したもので、運営者による確認はありません。")}
    }
    private var transfer:some View {
        Section {
            Toggle("プロフィールを公開（ランキング・引き継ぎ用）",isOn:Binding(get:{model.account.publish},set:{model.setProfilePublic($0)})).accessibilityIdentifier("account_publish")
            Button("引き継ぎコードを表示"){showCode=true}.accessibilityIdentifier("account_show_code")
            Button("引き継ぎコードで復元"){code="";restoring=true}.disabled(model.online.game != nil).accessibilityIdentifier("account_restore")
            if let message{Text(message).font(.caption).foregroundStyle(.orange)}
        }header:{Text("引き継ぎ")}footer:{Text("アカウントは登録不要で、この端末の鍵がIDになります。機種変更の前に引き継ぎコードを控えてください。コードを知っている人はアカウントを使えるため、他人に見せないでください。")}
    }
}
private struct GameRow:View {
    let game:OnlineAccount.Game
    private var change:Int {game.after-game.before}
    private var mark:String {game.result>0 ? "勝":game.result<0 ? "負":"分"}
    private var color:Color {game.result>0 ? .green:game.result<0 ? .red:.secondary}
    private var date:Date {Date(timeIntervalSince1970:TimeInterval(game.time))}
    var body:some View {HStack{
        Text(mark).font(.headline).foregroundStyle(color).frame(width:24)
        VStack(alignment:.leading,spacing:2){Text("\(game.opponentName)（\(game.opponentRating)）");Text(game.reason+" · "+date.formatted(date:.abbreviated,time:.shortened)).font(.caption).foregroundStyle(.secondary)}
        Spacer();Text("\(game.after)").monospacedDigit();Text(change>=0 ? "+\(change)":"\(change)").font(.caption.monospacedDigit()).foregroundStyle(change>=0 ? Color.green:Color.red).frame(width:36,alignment:.trailing)
    }.accessibilityElement(children:.combine)}
}
private struct RankRow:View {
    let rank:Int,profile:Profiles.Profile,mine:Bool
    var body:some View {HStack{Text("\(rank)").monospacedDigit().frame(width:30,alignment:.trailing);Text(profile.name).bold(mine);Spacer();Text("\(profile.rating)").monospacedDigit().foregroundStyle(gold);Text("\(profile.games)局").font(.caption).foregroundStyle(.secondary)}.accessibilityElement(children:.combine)}
}
struct RatingGraph:View {
    let points:[Int]
    var body:some View {Canvas{context,size in
        guard points.count>=2,let lo=points.min(),let hi=points.max() else{return};let span=Double(max(40,hi-lo)),base=Double(lo)-(span-Double(hi-lo))/2
        var line=Path();for (i,p) in points.enumerated(){let pt=CGPoint(x:size.width*Double(i)/Double(points.count-1),y:size.height*(1-(Double(p)-base)/span));if i==0{line.move(to:pt)}else{line.addLine(to:pt)}}
        context.stroke(line,with:.color(gold),lineWidth:2)
    }.background(Color.black.opacity(0.25)).clipShape(RoundedRectangle(cornerRadius:6))}
}
