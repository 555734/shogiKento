import SwiftUI
import UIKit
import GoogleMobileAds

/// A test-only build: both IDs are Google's public sample IDs. There is no production switch.
@MainActor final class TestAdModel:NSObject,ObservableObject,NativeAdLoaderDelegate {
    static let unit="ca-app-pub-3940256099942544/3986624511"
    @Published var nativeAd:NativeAd?
    @Published var state="loading"
    @Published var message="公式テスト広告を読み込み中…"
    private var loader:AdLoader?
    private var alive=true
    private var started=false
    let fixture=ProcessInfo.processInfo.arguments.contains("-ads-fixture")
    func load(){guard !started else{return};started=true;alive=true
        if fixture{let failed=ProcessInfo.processInfo.arguments.contains("-ads-failure");state=failed ? "failed":"fixture";message=failed ? "広告を取得できません。アプリはそのまま使えます。":"表示テスト用サンプル（配信広告ではありません）";return}
        MobileAds.shared.start { [weak self] _ in Task{@MainActor in self?.request()} }
    }
    private func request(){guard alive else{return};let video=VideoOptions();video.shouldStartMuted=true
        loader=AdLoader(adUnitID:Self.unit,rootViewController:nil,adTypes:[.native],options:[video]);loader?.delegate=self
        let request=Request(),extras=Extras();extras.additionalParameters=["npa":"1"];request.register(extras);loader?.load(request)
    }
    func adLoader(_ adLoader:AdLoader,didReceive nativeAd:NativeAd){guard alive else{return};self.nativeAd=nativeAd;state="loaded";message="Google公式テスト広告 · 収益は発生しません"}
    func adLoader(_ adLoader:AdLoader,didFailToReceiveAdWithError error:Error){guard alive else{return};state="failed";message="広告を取得できません。アプリはそのまま使えます。"}
    func close(){alive=false;loader?.delegate=nil;loader=nil;nativeAd=nil;started=false}
}

struct TestAdSlot:View {
    let placement:String
    @EnvironmentObject var coffee:CoffeeStore
    @StateObject private var model=TestAdModel()
    var body:some View {if !coffee.owned {VStack(alignment:.leading,spacing:8){
        Text("広告 · テスト広告").font(.caption.bold()).foregroundStyle(.black)
        Text(model.message).font(.caption).foregroundStyle(.black).accessibilityIdentifier("ad_status_"+placement).accessibilityValue(model.state)
        if let ad=model.nativeAd{NativeTestAdContainer(ad:ad)}
        else if model.fixture{VStack(alignment:.leading,spacing:18){Image(systemName:"square.grid.3x3.fill").font(.system(size:48));Text("棋力を伸ばす毎日の一手。").font(.title3.bold());Text("広告と盤面の操作は独立しています。\n通信を行わないレイアウト検証用です。").font(.caption)}.foregroundStyle(.brown).frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.center)}
        else{Spacer(minLength:0)}
    }.padding(12).frame(height:360).frame(maxWidth:.infinity).background(LinearGradient(colors:[Color(red:1,green:0.94,blue:0.81),Color(red:0.91,green:0.78,blue:0.55)],startPoint:.leading,endPoint:.trailing)).clipShape(RoundedRectangle(cornerRadius:10)).overlay(RoundedRectangle(cornerRadius:10).stroke(.brown)).accessibilityElement(children:.contain).accessibilityIdentifier("ad_"+placement).onAppear{model.load()}.onDisappear{model.close()}}}
}

struct NativeTestAdContainer:UIViewRepresentable {
    let ad:NativeAd
    func makeUIView(context:Context)->NativeAdView {
        let root=NativeAdView(),stack=UIStackView();stack.axis = .vertical;stack.spacing=5;stack.translatesAutoresizingMaskIntoConstraints=false;root.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:root.leadingAnchor),stack.trailingAnchor.constraint(equalTo:root.trailingAnchor),stack.topAnchor.constraint(equalTo:root.topAnchor),stack.bottomAnchor.constraint(lessThanOrEqualTo:root.bottomAnchor)])
        let media=MediaView();media.contentMode = .scaleAspectFit;media.heightAnchor.constraint(equalToConstant:120).isActive=true;stack.addArrangedSubview(media);root.mediaView=media
        func label(_ size:CGFloat)->UILabel {let v=UILabel();v.numberOfLines=0;v.textColor = .black;v.font = .systemFont(ofSize:size);stack.addArrangedSubview(v);return v}
        root.headlineView=label(16);root.bodyView=label(12);root.advertiserView=label(11)
        let action=UIButton(type:.system);action.setTitleColor(.white,for:.normal);action.backgroundColor=UIColor(red:0.35,green:0.08,blue:0.07,alpha:1);action.layer.cornerRadius=8;action.heightAnchor.constraint(equalToConstant:44).isActive=true;action.isUserInteractionEnabled=false;stack.addArrangedSubview(action);root.callToActionView=action
        return root
    }
    func updateUIView(_ root:NativeAdView,context:Context){(root.headlineView as? UILabel)?.text=ad.headline;(root.bodyView as? UILabel)?.text=ad.body;root.bodyView?.isHidden=ad.body==nil;(root.advertiserView as? UILabel)?.text=ad.advertiser;root.advertiserView?.isHidden=ad.advertiser==nil;(root.callToActionView as? UIButton)?.setTitle(ad.callToAction,for:.normal);root.callToActionView?.isHidden=ad.callToAction==nil;root.mediaView?.mediaContent=ad.mediaContent;root.nativeAd=ad}
    static func dismantleUIView(_ uiView:NativeAdView,coordinator:()){uiView.nativeAd=nil}
}
