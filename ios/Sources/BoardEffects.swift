import SwiftUI

/// Visual-only effect of the latest move, shared by CPU matches and online games. The position is always updated
/// at once; the effect only changes how the move is drawn, so input is never delayed by an animation.
struct MoveEffect:Equatable {
    var id=UUID()
    var piece:Int,pieceBefore:Int,to:Int,from:Int?,mover:Int,dropPiece:Int,captured:Int,promoted:Bool
    /// The checked king's square after the move, if the move gives check.
    var checkKing:Int?=nil
    var reduced=false
    var start=Date()
    static let tween=0.150,land=0.140,ring=0.250,flip=0.220,glow=0.320,fly=0.300,pop=0.160,check=0.600
    var landing:Double {reduced ? 0:promoted ? Self.flip:Self.land}
    var total:Double {
        if reduced{return checkKing != nil ? Self.tween+Self.check:0}
        return Self.tween+max(max(promoted ? max(Self.flip,Self.glow):max(Self.land,Self.ring),captured != 0 ? Self.fly+Self.pop:0),checkKing != nil ? Self.check:0)
    }
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
        let pieceBefore=from.map{before.board[$0]} ?? piece
        let promoted=from.map{abs(before.board[$0])<=8 && abs(piece)>8} ?? false
        var effect=MoveEffect(piece:piece,pieceBefore:pieceBefore,to:to,from:from,mover:mover,dropPiece:dropPiece,captured:before.board[to]*mover<0 ? before.board[to]:0,promoted:promoted)
        if Rules.checked(after){effect.checkKing=(0..<81).first{after.board[$0]==after.side*8}}
        return effect
    }
    static func easeOut(_ t:Double)->Double {let u=1-min(1,max(0,t));return 1-u*u*u}
    /// 1.0 → 1.12 → 1.0 over the landing.
    static func landingScale(_ t:Double)->Double {1+0.12*sin(Double.pi*min(1,max(0,t)))}
    /// Horizontal squeeze of a flip: 1 → 0 (old face) → 1 (new face).
    static func flipScale(_ t:Double)->Double {abs(cos(Double.pi*min(1,max(0,t))))}
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
    /// Where the captured piece lands: the mover's komadai slot for its type, else the komadai bar.
    func captureTarget()->CGPoint? {
        let r=hands["hand_\(effect.mover)_\(Position.base(effect.captured))"] ?? hands["handbar_\(effect.mover)"]
        return r.map{CGPoint(x:$0.midX,y:$0.midY)}
    }
    var body:some View {
        TimelineView(.animation){timeline in
            Canvas{context,_ in
                let t=timeline.date.timeIntervalSince(effect.start),to=board.center(effect.to),cell=board.cell
                func piece(_ value:Int,_ p:CGPoint,_ height:CGFloat,scaleX:CGFloat=1){
                    var c=context;c.translateBy(x:p.x,y:p.y);if value*board.viewSide<0{c.rotate(by:.degrees(180))};c.scaleBy(x:scaleX,y:1)
                    let w=height*cell.width/cell.height;c.draw(c.resolve(Image(uiImage:AppAssets.piece(value))),in:CGRect(x:-w/2,y:-height/2,width:w,height:height))
                }
                if let king=effect.checkKing,t<MoveEffect.tween+MoveEffect.check {
                    let a=effect.reduced ? 0.6:abs(sin(Double.pi*3*t/(MoveEffect.tween+MoveEffect.check))),k=board.center(king)
                    let r=CGRect(x:k.x-cell.width/2,y:k.y-cell.height/2,width:cell.width,height:cell.height)
                    context.fill(Path(r),with:.color(Color(red:0.9,green:0.12,blue:0.12).opacity(0.67*a)));context.stroke(Path(r),with:.color(Color(red:1,green:0.35,blue:0.3).opacity(a)),lineWidth:1.6)
                }
                func banner(){
                    guard effect.checkKing != nil else{return};let b=t-MoveEffect.tween/2;guard b>=0,b<MoveEffect.check else{return}
                    let k=b/MoveEffect.check,alpha=k<0.15 ? k/0.15:k>0.75 ? (1-k)/0.25:1,size=effect.reduced ? 54:54*(0.8+0.2*MoveEffect.easeOut(min(1,k/0.25)))
                    let c=CGPoint(x:board.rect.midX,y:board.rect.midY)
                    context.fill(Path(roundedRect:CGRect(x:c.x-size*1.35,y:c.y-size*0.62,width:size*2.7,height:size*1.24),cornerRadius:12),with:.color(Color(red:0.16,green:0,blue:0).opacity(0.6*alpha)))
                    context.draw(Text("王手").font(.system(size:size,weight:.black,design:.serif)).foregroundColor(Color(red:0.85,green:0.1,blue:0.1).opacity(alpha)),at:c)
                }
                if effect.reduced{banner();return}
                if t<MoveEffect.tween {
                    let (from,h0)=source(),k=MoveEffect.easeOut(t/MoveEffect.tween)
                    if effect.captured != 0{piece(effect.captured,to,cell.height)}
                    piece(effect.pieceBefore,CGPoint(x:from.x+(to.x-from.x)*k,y:from.y+(to.y-from.y)*k),h0+(cell.height-h0)*k);banner();return
                }
                let landed=t-MoveEffect.tween
                if effect.captured != 0,landed<MoveEffect.fly,let target=captureTarget() {
                    let k=MoveEffect.easeOut(landed/MoveEffect.fly);piece(effect.captured,CGPoint(x:to.x+(target.x-to.x)*k,y:to.y+(target.y-to.y)*k),cell.height*(1-0.55*k))
                }
                if effect.promoted {
                    if landed<MoveEffect.glow {
                        let r=landed/MoveEffect.glow,radius=max(cell.width,cell.height)*(0.55+0.5*r)
                        context.fill(Path(ellipseIn:CGRect(x:to.x-radius,y:to.y-radius,width:radius*2,height:radius*2)),with:.radialGradient(Gradient(colors:[Color(red:1,green:0.24,blue:0.16).opacity(0.78*(1-r)),.clear]),center:to,startRadius:0,endRadius:radius))
                    }
                    if landed<MoveEffect.flip{let f=landed/MoveEffect.flip;piece(f<0.5 ? effect.pieceBefore:effect.piece,to,cell.height,scaleX:max(0.02,MoveEffect.flipScale(f)))}
                }else{
                    if landed<MoveEffect.land{piece(effect.piece,to,cell.height*MoveEffect.landingScale(landed/MoveEffect.land))}
                    if landed<MoveEffect.ring {
                        let r=landed/MoveEffect.ring,radius=min(cell.width,cell.height)*(0.45+0.55*MoveEffect.easeOut(r))
                        context.stroke(Path(ellipseIn:CGRect(x:to.x-radius,y:to.y-radius,width:radius*2,height:radius*2)),with:.color(.white.opacity(0.9*(1-r))),lineWidth:2.4*(1-r)+0.6)
                    }
                }
                banner()
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// A komadai slot: the side whose hand it is and the piece type.
struct HandSlot:Equatable {let side:Int,piece:Int}

extension AppModel {
    var reducedEffects:Bool {UserDefaults.standard.bool(forKey:"reduced_effects")}
    /// Starts the effect for the move from before to the current position (CPU match and online only).
    func beginMoveEffect(from before:Position){
        guard screen == .match || screen == .online,var effect=MoveEffect.between(before,position) else{clearMoveEffect();return}
        effect.reduced=reducedEffects;if effect.reduced && effect.checkKing==nil{clearMoveEffect();return}
        moveEffect=effect;let id=effect.id
        effectHiddenSquare=effect.reduced ? nil:effect.to
        handPending=effect.reduced || effect.captured==0 ? nil:HandSlot(side:effect.mover,piece:Position.base(effect.captured));handPop=nil
        func after(_ seconds:Double,_ action:@escaping @MainActor (AppModel)->Void){Task{@MainActor [weak self] in try? await Task.sleep(nanoseconds:UInt64(seconds*1e9));if let self,self.moveEffect?.id==id{action(self)}}}
        after(MoveEffect.tween+effect.landing){$0.effectHiddenSquare=nil}
        if let slot=handPending{after(MoveEffect.tween+MoveEffect.fly){m in m.handPending=nil;withAnimation(.easeOut(duration:MoveEffect.pop/2)){m.handPop=slot}}
            after(MoveEffect.tween+MoveEffect.fly+MoveEffect.pop/2){m in withAnimation(.easeIn(duration:MoveEffect.pop/2)){m.handPop=nil}}}
        after(effect.total){$0.moveEffect=nil}
    }
    func clearMoveEffect(){moveEffect=nil;effectHiddenSquare=nil;handPending=nil;handPop=nil}
    /// Komadai count shown while the captured piece is still flying there.
    func shownHand(_ side:Int,_ piece:Int)->Int {position.hands[side==1 ? 0:1][piece]-(handPending==HandSlot(side:side,piece:piece) ? 1:0)}
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

/// Original synthesized sounds (native-three-minute/scripts/generate_move_sound.py), copied into GameAssets.
enum SoundKind:String,CaseIterable {
    case place="piece_place",capture="piece_capture",promote="piece_promote",check="alert_check",start="game_start",end="game_end",tick="clock_tick"
    /// Check > promotion > capture > placement, so every move plays exactly one sound.
    static func forMove(_ before:Position,_ after:Position)->SoundKind {
        if Rules.checked(after){return .check}
        guard let effect=MoveEffect.between(before,after) else{return .place}
        return effect.promoted ? .promote:effect.captured != 0 ? .capture:.place
    }
}

/// Match start (white flash, growing 対局開始 and both sides) and end (勝ち／負け／引き分け, confetti on a win).
struct GameBanner:Equatable {
    enum Kind:Equatable {case start(String),end(outcome:Int,detail:String)}
    var kind:Kind,start=Date(),reduced=false
    static let startDuration=1.0,flash=0.26,endText=0.5,confetti=2.6,buttonsAfter=0.9
    var animating:Bool {let t=Date().timeIntervalSince(start);switch kind{case .start:return t<Self.startDuration;case .end(let o,_):return t<(o>0 && !reduced ? Self.confetti:Self.buttonsAfter)}}
}

struct GameBannerLayer:View {
    let banner:GameBanner
    private static let colors:[Color]=[Color(red:1,green:0.82,blue:0.35),Color(red:0.89,green:0.25,blue:0.23),.white,Color(red:0.25,green:0.56,blue:0.88),Color(red:0.31,green:0.77,blue:0.42)]
    var body:some View {
        TimelineView(.animation(paused:!banner.animating)){timeline in
            Canvas{context,size in
                let t=timeline.date.timeIntervalSince(banner.start),c=CGPoint(x:size.width/2,y:size.height/2)
                switch banner.kind {
                case .start(let sub):
                    guard t<GameBanner.startDuration else{return};let k=t/GameBanner.startDuration
                    if !banner.reduced && t<GameBanner.flash{context.fill(Path(CGRect(origin:.zero,size:size)),with:.color(.white.opacity(0.86*(1-t/GameBanner.flash))))}
                    let alpha=k<0.12 ? k/0.12:k>0.7 ? (1-k)/0.3:1,font=banner.reduced ? 44:44*(0.6+0.55*MoveEffect.easeOut(min(1,k/0.45)))
                    context.fill(Path(roundedRect:CGRect(x:c.x-150,y:c.y-font*1.05,width:300,height:font*1.8+26),cornerRadius:14),with:.color(Color(red:0.12,green:0.02,blue:0.02).opacity(0.67*alpha)))
                    context.draw(Text("対局開始").font(.system(size:font,weight:.black,design:.serif)).foregroundColor(Color(red:1,green:0.87,blue:0.55).opacity(alpha)),at:CGPoint(x:c.x,y:c.y-font*0.1))
                    context.draw(Text(sub).font(.system(size:14,weight:.bold)).foregroundColor(.white.opacity(alpha)),at:CGPoint(x:c.x,y:c.y+font*0.55+8))
                case .end(let outcome,let detail):
                    if outcome>0 && !banner.reduced && t<GameBanner.confetti {
                        var rng=SeededRandom(seed:UInt64(banner.start.timeIntervalSince1970*1000))
                        for _ in 0..<90 {
                            let x0=rng.next()*size.width,y0=(-rng.next()*0.5)*size.height,vx=(rng.next()-0.5)*60,vy=120+rng.next()*160,rot=rng.next()*360,color=Self.colors[Int(rng.next()*5)%5]
                            let x=x0+vx*t,y=y0+vy*t+60*t*t;guard y<size.height else{continue}
                            var p=context;p.translateBy(x:x,y:y);p.rotate(by:.degrees(rot+t*240));p.opacity=max(0,1-t/GameBanner.confetti)
                            p.fill(Path(CGRect(x:-3.5,y:-2,width:7,height:4)),with:.color(color))
                        }
                    }
                    let k=banner.reduced ? 1:MoveEffect.easeOut(min(1,t/GameBanner.endText)),font=64*(banner.reduced ? 1:0.7+0.3*k),cy=c.y-40
                    let word=outcome>0 ? "勝ち":outcome<0 ? "負け":"引き分け",color=outcome>0 ? Color(red:1,green:0.82,blue:0.35):outcome<0 ? Color(red:0.66,green:0.78,blue:1):.white
                    context.fill(Path(roundedRect:CGRect(x:c.x-150,y:cy-font*0.95,width:300,height:font*1.45+30),cornerRadius:16),with:.color(Color(red:0.09,green:0.02,blue:0.02).opacity(0.78*k)))
                    context.draw(Text(word).font(.system(size:font,weight:.black,design:.serif)).foregroundColor(color.opacity(k)),at:CGPoint(x:c.x,y:cy-font*0.1))
                    context.draw(Text(detail).font(.system(size:14,weight:.bold)).foregroundColor(.white.opacity(k)),at:CGPoint(x:c.x,y:cy+font*0.5+8))
                }
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// Deterministic confetti so every frame draws the same particles.
struct SeededRandom {
    var state:UInt64
    init(seed:UInt64){state=seed &+ 0x9E3779B97F4A7C15}
    mutating func next()->CGFloat {state=state &* 6364136223846793005 &+ 1442695040888963407;return CGFloat((state>>33)%1_000_000)/1_000_000}
}

/// もう一局 / 検討する / ホームへ under the result of a CPU match.
struct MatchEndButtons:View {
    @EnvironmentObject var model:AppModel
    var body:some View {
        HStack(spacing:6){
            button("もう一局",Color(red:0.75,green:0.22,blue:0.17),"end_again"){model.restartMatch();model.startMatch()}
            button("検討する",Color(red:0.16,green:0.34,blue:0.5),"end_study"){if model.session.study.moves>0{model.archiveOffer=model.session.match}else{model.open(model.session.match)}}
            button("ホームへ",Color(red:0.29,green:0.23,blue:0.17),"end_home"){model.enter(.home)}
        }.padding(.horizontal,8)
    }
    func button(_ title:String,_ fill:Color,_ id:String,_ action:@escaping ()->Void)->some View {
        Button(action:action){Text(title).font(.system(size:15,weight:.bold)).foregroundStyle(.white).frame(maxWidth:.infinity,minHeight:42)
            .background(RoundedRectangle(cornerRadius:10).fill(fill)).overlay(RoundedRectangle(cornerRadius:10).stroke(Color(red:1,green:0.82,blue:0.48),lineWidth:1.4))}.buttonStyle(.plain).accessibilityIdentifier(id)
    }
}
