import SwiftUI
@main struct BeniShogiApp: App {
    @StateObject private var model=AppModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {WindowGroup{RootView().environmentObject(model).preferredColorScheme(.dark).onChange(of:phase){model.sceneActive($0 == .active)}}}
}
