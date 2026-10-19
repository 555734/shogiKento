import SwiftUI
import StoreKit

@MainActor final class CoffeeStore:ObservableObject {
    static let productID="coffee_support_unlock"
    static let shared=CoffeeStore()
    @Published private(set) var owned=false
    @Published private(set) var product:Product?
    @Published private(set) var busy=false
    @Published private(set) var checkedEntitlements=false
    @Published var message="ストアを確認しています…"
    @Published var offerPending=false
    private let defaults:UserDefaults
    private var updates:Task<Void,Never>?
    private var launched=false,backgrounded=false
    private var generation=0
    var fixture=false
    init(defaults:UserDefaults = .standard,start:Bool=true){self.defaults=defaults
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-coffee-reset-offer"){defaults.removeObject(forKey:"coffee_launches");defaults.removeObject(forKey:"coffee_hide_offer")}
        fixture=ProcessInfo.processInfo.arguments.contains("-coffee-fixture")
        if ProcessInfo.processInfo.arguments.contains("-coffee-test-owned"){owned=true;fixture=true}
        #endif
        if fixture{checkedEntitlements=true}
        if start && !fixture {
            observeTransactions()
            Task{await refresh();await loadProduct()}
        }
    }
    deinit{updates?.cancel()}
    var hideOffer:Bool {get{defaults.bool(forKey:"coffee_hide_offer")}set{objectWillChange.send();defaults.set(newValue,forKey:"coffee_hide_offer");if newValue{offerPending=false}}}
    func observeTransactions(){guard updates==nil else{return};updates=Task{[weak self] in for await update in Transaction.updates {guard let self else{return};guard case .verified(let transaction)=update,transaction.productID==Self.productID,transaction.productType == .nonConsumable else{continue};self.generation+=1;self.owned=transaction.revocationDate==nil && !transaction.isUpgraded;if self.owned{self.offerPending=false};await transaction.finish()}}}
    var price:String? {fixture ? "¥200（購入テスト）":product?.displayPrice}
    var canBuy:Bool {!owned && !busy && (product != nil || fixture)}
    func effective(_ options:MatchOptions)->MatchOptions {var next=options;if next.level==10 && !owned{next.level=9};return next}
    func foreground(){if !launched || backgrounded{launched=true;backgrounded=false;let count=defaults.integer(forKey:"coffee_launches")+1;defaults.set(count,forKey:"coffee_launches");offerPending=count>=2 && !hideOffer && !owned}}
    func background(offerVisible:Bool){if !busy && !offerVisible{backgrounded=true}}
    func loadProduct()async{guard !fixture else{return};do{product=try await Product.products(for:[Self.productID]).first;message=product==nil ? "ストアで購入商品の準備が完了していません。日本向けの設定予定価格は200円です。":"買い切り・追加料金なし。ストアに表示される価格で購入します。"}catch{message="ストアへ接続できません。接続後に再試行してください。"}}
    func refresh()async {guard !fixture else{return};generation+=1;let version=generation;var entitled=false
        for await result in Transaction.currentEntitlements {if case .verified(let transaction)=result,transaction.productID==Self.productID,transaction.productType == .nonConsumable,transaction.revocationDate==nil,!transaction.isUpgraded{entitled=true}}
        guard version==generation else{return};owned=entitled;checkedEntitlements=true;if owned{offerPending=false}
    }
    func buy()async {
        guard canBuy else{return}
        #if DEBUG
        if fixture{owned=true;offerPending=false;message="購入テストが完了しました（実際の請求なし）。";return}
        #endif
        guard let product else{return};busy=true;defer{busy=false}
        do {switch try await product.purchase(){
        case .success(let result):guard case .verified(let transaction)=result,transaction.productID==Self.productID,transaction.productType == .nonConsumable,transaction.revocationDate==nil else{message="購入情報を検証できませんでした。購入の復元をお試しください。";return};generation+=1;owned=true;offerPending=false;await transaction.finish();message="ありがとうございます。広告除去と最強AI Lv.10を解放しました。"
        case .pending:message="お支払いの完了を待っています。完了後に特典を有効にします。"
        case .userCancelled:message="購入をキャンセルしました。"
        @unknown default:message="購入を完了できませんでした。再試行してください。"
        }}catch{message="購入を完了できませんでした。ストアで再試行してください。"}
    }
    func restore()async {guard !busy else{return};busy=true;defer{busy=false}
        #if DEBUG
        if fixture{owned=true;offerPending=false;message="購入情報を復元しました（テスト）。";return}
        #endif
        do{try await AppStore.sync();generation+=1;await refresh();message=owned ? "購入情報を復元しました。":"このApp Storeアカウントに購入情報はありません。購入したアカウントを確認してください。"}catch{message="購入情報を復元できませんでした。接続後に再試行してください。"}
    }
}

struct CoffeeView:View {
    @EnvironmentObject var coffee:CoffeeStore
    @Environment(\.dismiss) var dismiss
    var body:some View {NavigationStack{Form{
        Section {Text("コーヒーを1杯奢って、開発を応援").font(.title2.bold());Label("すべての広告を非表示",systemImage:"rectangle.slash");Label("対局AIの最強レベル Lv.10を解放",systemImage:"crown");Text("買い切り・追加料金なし。同じストアのアカウントで復元できます。AndroidとiOSの購入は共通ではありません。").font(.caption)}
        Section {Text(coffee.owned ? "購入済みです。ありがとうございます。":coffee.message).accessibilityIdentifier("coffee_status");Button(coffee.owned ? "購入済み":coffee.price.map{"コーヒーを奢る · \($0)"} ?? "価格を確認中（日本向け予定200円）"){Task{await coffee.buy()}}.disabled(!coffee.canBuy).accessibilityIdentifier("coffee_buy");Button("購入を復元する"){Task{await coffee.restore()}}.disabled(coffee.busy).accessibilityIdentifier("coffee_restore")}
        Section {Toggle("今後、起動時に表示しない",isOn:Binding(get:{coffee.hideOffer},set:{coffee.hideOffer=$0})).accessibilityIdentifier("coffee_hide_offer")}
    }.navigationTitle("☕ コーヒーを奢る").toolbar{Button("閉じる"){dismiss()}.disabled(coffee.busy).accessibilityIdentifier("coffee_close")}.onAppear{coffee.offerPending=false}.task{if !coffee.fixture{await coffee.loadProduct()}}}}
}
