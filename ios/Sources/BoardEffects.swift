import SwiftUI

/// Visual-only effect of the latest move, shared by CPU matches and online games. The position is always updated
/// at once; the effect only changes how the move is drawn, so input is never delayed by an animation.
struct MoveEffect:Equatable {
    var id=UUID()
    var piece:Int,to:Int,from:Int?,mover:Int,dropPiece:Int,captured:Int,promoted:Bool
    var start=Date()
    static let tween=0.150,land=0.140,ring=0.250
    static var total:Double {tween+max(land,ring)}
    /// The move from before to after (one ply), derived from the two positions.
    static func between(_ before:Position,_ after:Position)->MoveEffect? {
        let mover=before.side;var from:Int?,to:Int?
        for s in 0..<81 {
            let b=before.board[s],a=after.board[s]
            if b*mover>0 && a==0 {from=s}
            if a*mover>0 && b != a && b*mover<=0 {to=s}
        }
        guard let to else{return nil}
        let piece=after.board[to],dropPiece=from==nil ? Position.base(piece):0
        let promoted=from.map{abs(before.board[$0])<=8 && abs(piece)>8} ?? false
        return MoveEffect(piece:piece,to:to,from:from,mover:mover,dropPiece:dropPiece,captured:before.board[to]*mover<0 ? before.board[to]:0,promoted:promoted)
    }
    static func easeOut(_ t:Double)->Double {let u=1-min(1,max(0,t));return 1-u*u*u}
    /// 1.0 → 1.12 → 1.0 over the landing.
    static func landingScale(_ t:Double)->Double {1+0.12*sin(Double.pi*min(1,max(0,t)))}
}

/// Board-space geometry for an anchor-resolved board rect, matching BoardView's 1% inset grid.
struct BoardGeometry {
    let rect:CGRect,viewSide:Int
    var cell:CGSize {CGSize(width:rect.width*0.98/9,height:rect.height*0.98/9)}
    func center(_ square:Int)->CGPoint {let d=viewSide == -1 ? 80-square:square
        return CGPoint(x:rect.minX+rect.width*(0.01+0.98*(CGFloat(d%9)+0.5)/9),y:rect.minY+rect.height*(0.01+0.98*(CGFloat(d/9)+0.5)/9))}
}

/// Draws the moving piece, its landing pulse and the white ring above the board and both komadai.
struct MoveEffectLayer:View {
    let effect:MoveEffect,board:BoardGeometry,hands:[String:CGRect]
    func source()->(CGPoint,CGFloat) {
        if let from=effect.from{return (board.center(from),board.cell.height)}
        let r=hands["hand_\(effect.mover)_\(effect.dropPiece)"] ?? hands["handbar_\(effect.mover)"]
        if let r{return (CGPoint(x:r.midX,y:r.midY),min(r.height,board.cell.height))}
        let edge=effect.mover==board.viewSide ? board.rect.maxY:board.rect.minY
        return (CGPoint(x:board.rect.midX,y:edge),board.cell.height)
    }
    var body:some View {
        TimelineView(.animation){timeline in
            Canvas{context,_ in
                let t=timeline.date.timeIntervalSince(effect.start),to=board.center(effect.to),cell=board.cell
                let image=context.resolve(Image(uiImage:AppAssets.piece(effect.piece)))
                func piece(_ p:CGPoint,_ height:CGFloat){
                    var c=context;c.translateBy(x:p.x,y:p.y);if effect.piece*board.viewSide<0{c.rotate(by:.degrees(180))}
                    let w=height*cell.width/cell.height;c.draw(image,in:CGRect(x:-w/2,y:-height/2,width:w,height:height))
                }
                if t<MoveEffect.tween {
                    let (from,h0)=source(),k=MoveEffect.easeOut(t/MoveEffect.tween)
                    piece(CGPoint(x:from.x+(to.x-from.x)*k,y:from.y+(to.y-from.y)*k),h0+(cell.height-h0)*k);return
                }
                let landed=t-MoveEffect.tween
                if landed<MoveEffect.land{piece(to,cell.height*MoveEffect.landingScale(landed/MoveEffect.land))}
                if landed<MoveEffect.ring {
                    let r=landed/MoveEffect.ring,radius=min(cell.width,cell.height)*(0.45+0.55*MoveEffect.easeOut(r))
                    context.stroke(Path(ellipseIn:CGRect(x:to.x-radius,y:to.y-radius,width:radius*2,height:radius*2)),with:.color(.white.opacity(0.9*(1-r))),lineWidth:2.4*(1-r)+0.6)
                }
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

extension AppModel {
    var reducedEffects:Bool {UserDefaults.standard.bool(forKey:"reduced_effects")}
    /// Starts the effect for the move from before to the current position (CPU match and online only).
    func beginMoveEffect(from before:Position){
        guard screen == .match || screen == .online,!reducedEffects,let effect=MoveEffect.between(before,position) else{moveEffect=nil;effectHiddenSquare=nil;return}
        moveEffect=effect;effectHiddenSquare=effect.to;let id=effect.id
        Task{@MainActor [weak self] in
            try? await Task.sleep(nanoseconds:UInt64((MoveEffect.tween+MoveEffect.land)*1e9));if self?.moveEffect?.id==id{self?.effectHiddenSquare=nil}
            try? await Task.sleep(nanoseconds:UInt64((MoveEffect.total-MoveEffect.tween-MoveEffect.land)*1e9));if self?.moveEffect?.id==id{self?.moveEffect=nil}
        }
    }
    func clearMoveEffect(){moveEffect=nil;effectHiddenSquare=nil}
}

/// Hosts the move effect over a board and komadai that publish ArrowAnchorKey bounds.
struct MoveEffectOverlay:View {
    @EnvironmentObject var model:AppModel
    let anchors:[String:Anchor<CGRect>],proxy:GeometryProxy
    var body:some View {
        if let effect=model.moveEffect,let board=anchors["board"] {
            MoveEffectLayer(effect:effect,board:BoardGeometry(rect:proxy[board],viewSide:model.viewSide),hands:anchors.filter{$0.key.hasPrefix("hand")}.mapValues{proxy[$0]})
        }
    }
}
