import SwiftUI
import CoreText

/// Bundled typefaces (SIL OFL 1.1; GameAssets/fonts, licenses in GameAssets/licenses), shared with Android:
/// Zen Kaku Gothic New for UI text, Shippori Mincho for titles, Yuji Syuku (brush) for the match banners.
/// Subset to the app's own strings; other characters fall back to the system font.
enum AppFonts {
    static let medium="ZenKakuGothicNew-Medium",bold="ZenKakuGothicNew-Bold",minchoName="ShipporiMincho-ExtraBold",brushName="YujiSyuku-Regular"
    static func register(){
        for name in [medium,bold,minchoName,brushName]{
            if let url=Bundle.main.url(forResource:name,withExtension:"ttf",subdirectory:"GameAssets/fonts"){_=CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)}
        }
    }
    /// UI text at a fixed size (replaces `.system(size:)`).
    static func ui(_ size:CGFloat,bold heavy:Bool=false)->Font {.custom(heavy ? bold:medium,fixedSize:size)}
    /// UI text that follows Dynamic Type like the system text style it replaces.
    static func style(_ size:CGFloat,_ style:Font.TextStyle,bold heavy:Bool=false)->Font {.custom(heavy ? bold:medium,size:size,relativeTo:style)}
    static func mincho(_ size:CGFloat)->Font {.custom(minchoName,fixedSize:size)}
    static func brush(_ size:CGFloat)->Font {.custom(brushName,fixedSize:size)}
}

/// Shared lacquer palette for panels and buttons.
enum Lacquer {
    static let inkTop=Color(red:0.10,green:0.043,blue:0.031).opacity(0.92),inkBottom=Color(red:0.055,green:0.02,blue:0.016).opacity(0.92)
    static let hairline=Color(red:0.85,green:0.71,blue:0.42).opacity(0.4),hairlineLit=Color(red:0.86,green:0.72,blue:0.42)
    static let cream=Color(red:0.96,green:0.92,blue:0.82),vermilionTop=Color(red:0.72,green:0.25,blue:0.16),vermilionBottom=Color(red:0.54,green:0.14,blue:0.09)
    static var ink:LinearGradient {LinearGradient(colors:[inkTop,inkBottom],startPoint:.top,endPoint:.bottom)}
}

/// Rounded ink card with a gold hairline; a lit card gets a brighter edge and an inner rule.
struct InkCard:ViewModifier {
    var radius:CGFloat=8,lit=false
    func body(content:Content)->some View {
        content.background(RoundedRectangle(cornerRadius:radius).fill(Lacquer.ink))
            .overlay(RoundedRectangle(cornerRadius:radius).strokeBorder(lit ? Lacquer.hairlineLit:Lacquer.hairline,lineWidth:lit ? 1.3:0.9))
            .overlay{if lit{RoundedRectangle(cornerRadius:max(0,radius-3)).strokeBorder(Lacquer.hairlineLit.opacity(0.35),lineWidth:0.6).padding(3.5)}}
    }
}
extension View {func inkCard(radius:CGFloat=8,lit:Bool=false)->some View {modifier(InkCard(radius:radius,lit:lit))}}

/// Buttons: primary vermilion lacquer, secondary ink, danger an outlined vermilion; all with a hairline.
struct LacquerButtonStyle:ButtonStyle {
    enum Kind {case primary,secondary,danger}
    var kind:Kind = .secondary,size:CGFloat=15,minHeight:CGFloat=40
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration:Configuration)->some View {
        let shape=RoundedRectangle(cornerRadius:7)
        configuration.label.font(AppFonts.ui(size,bold:true)).kerning(size*0.06)
            .foregroundStyle(kind == .danger ? Color(red:1,green:0.77,blue:0.71):Lacquer.cream)
            .padding(.horizontal,12).frame(minHeight:minHeight)
            .background{switch kind{
                case .primary:shape.fill(LinearGradient(colors:[Lacquer.vermilionTop,Lacquer.vermilionBottom],startPoint:.top,endPoint:.bottom))
                case .secondary:shape.fill(Color(red:0.07,green:0.024,blue:0.02).opacity(0.7))
                case .danger:shape.fill(Color(red:0.66,green:0.2,blue:0.12).opacity(0.25))}}
            .overlay(shape.strokeBorder(kind == .primary ? Color(red:0.94,green:0.76,blue:0.48).opacity(0.7):kind == .danger ? Color(red:0.81,green:0.35,blue:0.25):Lacquer.hairline.opacity(1.5),lineWidth:kind == .danger ? 1.2:0.9))
            .opacity(enabled ? (configuration.isPressed ? 0.75:1):0.45)
    }
}
