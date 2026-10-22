import SwiftUI

struct MatchSettingsView:View {
    @EnvironmentObject var model:AppModel
    @EnvironmentObject var coffee:CoffeeStore
    @State private var coffeeSheet=false
    @Environment(\.dismiss) var dismiss
    @State var options:MatchOptions
    var body:some View {
        NavigationStack {
            Form {
                Section("AIの強さ") {
                    Stepper("Lv.\(options.level) / 10",value:$options.level,in:1...10).accessibilityIdentifier("match_level")
                    Text("Lv.10はコーヒー応援の特典です。Lv.が高いほど探索量が増えます。").font(.caption)
                }
                Section("持ち時間（双方・切れ負け）") {
                    Picker("持ち時間",selection:$options.minutes) {
                        ForEach(MatchOptions.times,id:\.self){Text("\($0)分").tag($0)}
                    }.pickerStyle(.menu).accessibilityIdentifier("match_minutes")
                }
                Section("あなたの手番") {
                    Picker("先手／後手",selection:$options.humanSide) {
                        Text("先手（最初に指す）").tag(1)
                        Text("後手（CPUが最初に指す）").tag(-1)
                    }.pickerStyle(.segmented).accessibilityIdentifier("match_side")
                }
                Section {
                    Text("設定を変えると現在の対局は棋譜に保存され、新しい盤面へ適用されます。キャンセルでは変更しません。").font(.caption)
                    Button(options.level==10 && !coffee.owned ? "Lv.10を解放する":"設定を適用"){if options.level==10 && !coffee.owned{coffeeSheet=true}else{model.applyMatchOptions(options);dismiss()}}.accessibilityIdentifier("apply_match_settings")
                }
            }.navigationTitle("対局設定").sheet(isPresented:$coffeeSheet){CoffeeView()}.toolbar{Button("キャンセル"){dismiss()}}
        }
    }
}
