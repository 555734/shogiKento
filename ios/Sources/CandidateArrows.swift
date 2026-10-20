import SwiftUI

enum CandidateArrowGeometry {
    static let widths:[CGFloat]=[0.18,0.125,0.08]
    static let opacities:[Double]=[225.0/255,160.0/255,100.0/255]
    static func center(_ square:Int,size:CGSize)->CGPoint {
        CGPoint(x:size.width*(0.01+0.98*(CGFloat(square%9)+0.5)/9),y:size.height*(0.01+0.98*(CGFloat(square/9)+0.5)/9))
    }
    static func path(source:CGPoint?,target:CGPoint,unit:CGFloat,rank:Int,side:Int)->Path {
        var start=source ?? CGPoint(x:target.x,y:target.y+CGFloat(side)*unit*0.43),end=target
        let dx=end.x-start.x,dy=end.y-start.y
        var length=hypot(dx,dy)
        guard length>0.01 else{return Path()}
        let ux=dx/length,uy=dy/length,nx = -uy,ny=ux
        if source != nil{start.x+=ux*unit*0.18;start.y+=uy*unit*0.18;end.x-=ux*unit*0.10;end.y-=uy*unit*0.10;length-=unit*0.28}
        let half=unit*widths[max(0,min(2,rank-1))]/2,head=min(unit*0.32,length*0.55),wing=half*2.15
        let base=CGPoint(x:end.x-ux*head,y:end.y-uy*head)
        var path=Path();path.move(to:CGPoint(x:start.x+nx*half,y:start.y+ny*half))
        path.addLine(to:CGPoint(x:base.x+nx*half,y:base.y+ny*half));path.addLine(to:CGPoint(x:base.x+nx*wing,y:base.y+ny*wing));path.addLine(to:end)
        path.addLine(to:CGPoint(x:base.x-nx*wing,y:base.y-ny*wing));path.addLine(to:CGPoint(x:base.x-nx*half,y:base.y-ny*half));path.addLine(to:CGPoint(x:start.x-nx*half,y:start.y-ny*half));path.closeSubpath();return path
    }
}

struct CandidateArrows:View {
    let candidates:[Candidate]
    let side:Int
    var body:some View {
        Canvas{context,size in
            let unit=min(size.width,size.height)*0.98/9
            for index in candidates.indices.reversed(){
                let move=candidates[index].move,target=CandidateArrowGeometry.center(move.destination,size:size)
                let source=move.source.map{CandidateArrowGeometry.center($0,size:size)}
                let path=CandidateArrowGeometry.path(source:source,target:target,unit:unit,rank:index+1,side:side)
                let alpha=CandidateArrowGeometry.opacities[index]
                context.fill(path,with:.color(Color(red:1,green:91.0/255,blue:24.0/255).opacity(alpha)))
                context.stroke(path,with:.color(Color(red:1,green:225.0/255,blue:159.0/255).opacity(alpha)),lineWidth:unit*0.018)
                if move.drop>0{context.draw(Text("打").font(.system(size:unit*0.23,weight:.bold)).foregroundColor(Color(red:139.0/255,green:45.0/255,blue:7.0/255).opacity(alpha)),at:CGPoint(x:target.x+unit*0.34,y:target.y-unit*0.32))}
            }
        }.clipped().allowsHitTesting(false)
            .accessibilityElement(children:.ignore).accessibilityLabel("候補手の矢印")
            .accessibilityValue("\(candidates.count)件").accessibilityIdentifier("candidate_arrows")
    }
}
