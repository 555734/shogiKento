import SwiftUI
@main struct BeniShogiApp: App {
    @StateObject private var model=AppModel()
    @StateObject private var coffee=CoffeeStore.shared
    @Environment(\.scenePhase) private var phase
    var body: some Scene {WindowGroup{RootView().environmentObject(model).environmentObject(coffee).preferredColorScheme(.dark).onChange(of:phase){model.sceneActive($0 == .active)}}}
}
