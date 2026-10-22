import UIKit
import Combine
import UserMessagingPlatform

/// Every user gets restricted advertising; no age or personal profile is collected.
@MainActor final class AdPrivacy:ObservableObject {
    static let shared=AdPrivacy()
    @Published private(set) var optionsRequired=false
    private var ready=false,updating=false
    private var pending=[(Bool)->Void]()
    func gather(_ completion:@escaping (Bool)->Void){
        #if DEBUG
        completion(true);return
        #else
        if ready{completion(true);return};pending.append(completion);guard !updating else{return};updating=true
        let parameters=RequestParameters();parameters.isTaggedForUnderAgeOfConsent=true
        ConsentInformation.shared.requestConsentInfoUpdate(with:parameters){[weak self] error in Task{@MainActor in
            guard let self else{return}
            self.optionsRequired=ConsentInformation.shared.privacyOptionsRequirementStatus == .required
            if error != nil{self.finish();return}
            ConsentForm.loadAndPresentIfRequired(from:nil){[weak self] _ in Task{@MainActor in self?.finish()}}
        }}
        #endif
    }
    private func finish(){ready=ConsentInformation.shared.canRequestAds;updating=false;let callbacks=pending;pending=[];for callback in callbacks{callback(ready)}}
    func showOptions(){ConsentForm.presentPrivacyOptionsForm(from:nil){[weak self] _ in Task{@MainActor in self?.ready=ConsentInformation.shared.canRequestAds}}}
    static var testing:Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }
    static func unit(_ placement:String)->String {
        if testing{return "ca-app-pub-3940256099942544/3986624511"}
        switch placement{case "home":return "ca-app-pub-6772282664898856/2954129026";case "study":return "ca-app-pub-6772282664898856/1071955731";case "match":return "ca-app-pub-6772282664898856/1245636542";case "archive":return "ca-app-pub-6772282664898856/6442782044";default:preconditionFailure("Unknown ad placement")}
    }
}
