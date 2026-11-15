import Foundation

/// Nodes use integer parents so long records serialize without recursive object graphs.
struct StudyTree: Codable {
    struct Node: Codable {
        /// preferred is the displayed continuation; chosen is set only when the user picks a branch from the move list.
        var position:String,label:String,parent:Int?,preferred:Int?,score:Evaluation?,chosen:Int?
    }
    struct Row: Identifiable {
        var id:Int,ply:Int,indent:Int,branch:Int,total:Int,label:String,current:Bool
    }
    /// One move of the displayed line. branch 0 is the main line; total is the number of alternatives at this ply.
    struct LineRow: Identifiable {
        var id:Int {ply}
        var ply:Int,node:Int,label:String,branch:Int,total:Int,offMain:Bool,current:Bool
    }
    /// One alternative at a fork, with how many moves its continuation shows.
    struct Alternative: Identifiable {
        var id:Int {node}
        var node:Int,branch:Int,label:String,following:Int,shown:Bool
    }
    static func branchName(_ branch:Int)->String {branch==0 ? "本譜":"変化\(branch)"}
    var nodes:[Node]
    init(record:GameRecord,scores:[Int:Evaluation]) {
        nodes=record.positions.indices.map { i in
            Node(position:record.positions[i],label:record.labels[i],parent:i==0 ? nil:i-1,
                 preferred:i+1<record.positions.count ? i+1:nil,score:scores[i],chosen:nil)
        }
    }
    var activePath:[Int] {
        guard !nodes.isEmpty else{return []};var result=[0]
        while let next=nodes[result.last!].preferred {result.append(next)}
        return result
    }
    func path(to node:Int)->[Int] {
        var path=[Int](),next:Int?=node
        while let id=next {path.append(id);next=nodes[id].parent}
        return Array(path.reversed())
    }
    /// Earliest child of each node: the original record move, or the first move played there.
    private func mainChildren()->[Int?] {
        var main=[Int?](repeating:nil,count:nodes.count)
        for i in nodes.indices.dropFirst().reversed(){if let parent=nodes[i].parent{main[parent]=i}}
        return main
    }
    private func next(_ node:Int,main:[Int?])->Int? {
        if let chosen=nodes[node].chosen,chosen>node,chosen<nodes.count,nodes[chosen].parent==node{return chosen}
        return main[node]
    }
    private func children()->[[Int]] {
        var children=Array(repeating:[Int](),count:nodes.count)
        for i in nodes.indices.dropFirst(){if let parent=nodes[i].parent{children[parent].append(i)}}
        return children
    }
    /// The displayed line only, like a normal record, with fork information.
    func line(current:Int)->[LineRow] {
        let children=children();var offMain=false
        return activePath.enumerated().map{ply,id in
            let siblings=nodes[id].parent.map{children[$0]} ?? [id],branch=siblings.firstIndex(of:id) ?? 0
            offMain = offMain || branch>0
            return LineRow(ply:ply,node:id,label:nodes[id].label,branch:branch,total:siblings.count,offMain:offMain,current:id==current)
        }
    }
    /// Alternatives at the ply of node, main line first.
    func alternatives(of node:Int)->[Alternative] {
        guard node>=0,node<nodes.count else{return []}
        let children=children(),main=mainChildren(),shown=Set(activePath)
        let siblings=nodes[node].parent.map{children[$0]} ?? [node]
        return siblings.enumerated().map{branch,id in
            var following=0,cursor=next(id,main:main)
            while let n=cursor,following<nodes.count {following+=1;cursor=next(n,main:main)}
            return Alternative(node:id,branch:branch,label:nodes[id].label,following:following,shown:shown.contains(id))
        }
    }
    /// Displays the path to node, then the chosen branch where one was picked, otherwise the main line.
    mutating func focus(_ node:Int) {
        let path=path(to:node)
        for i in path.indices.dropFirst(){nodes[path[i-1]].preferred=path[i]}
        let main=mainChildren();var current=node
        while true {
            let following=next(current,main:main)
            nodes[current].preferred=following
            guard let id=following else{break};current=id
        }
    }
    /// Explicit branch selection from the move list; navigation keeps following this branch.
    mutating func select(_ node:Int) {
        let path=path(to:node)
        for i in path.indices.dropFirst(){nodes[path[i-1]].chosen=path[i]}
        focus(node)
    }
    mutating func append(_ position:String,label:String,from parent:Int)->Int {
        if let existing=nodes.indices.first(where:{nodes[$0].parent==parent && nodes[$0].position==position}){focus(existing);return existing}
        let id=nodes.count;nodes.append(Node(position:position,label:label,parent:parent,preferred:nil,score:nil,chosen:nil));focus(id);return id
    }
    func rows(current:Int)->[Row] {
        guard !nodes.isEmpty else{return []}
        var children=Array(repeating:[Int](),count:nodes.count)
        for i in nodes.indices.dropFirst(){children[nodes[i].parent!].append(i)}
        var stack=[(0,0,0)],result=[Row]()
        while let (id,ply,indent)=stack.popLast() {
            let siblings=nodes[id].parent.map{children[$0]} ?? [0]
            result.append(Row(id:id,ply:ply,indent:indent,branch:siblings.firstIndex(of:id)!+1,total:siblings.count,label:nodes[id].label,current:id==current))
            for child in children[id].reversed(){stack.append((child,ply+1,indent+(children[id].count>1 ? 1:0)))}
        }
        return result
    }
    func validate()throws {
        guard !nodes.isEmpty,nodes[0].parent==nil else{throw ShogiError.message("分岐の開始局面が不正です")}
        for i in nodes.indices {
            if i>0 {guard let parent=nodes[i].parent,parent>=0,parent<i else{throw ShogiError.message("分岐の接続が不正です")}}
            for next in [nodes[i].preferred,nodes[i].chosen].compactMap({$0}) {guard next>i,next<nodes.count,nodes[next].parent==i else{throw ShogiError.message("分岐の続きが不正です")}}
            _ = try Position(nodes[i].position)
        }
    }
}

extension CurrentSession {
    mutating func captureStudyTree() {
        if studyTree==nil{studyTree=StudyTree(record:study,scores:scores)}
        let path=studyTree!.activePath
        for (ply,id) in path.enumerated(){studyTree!.nodes[id].position=study.positions[ply];studyTree!.nodes[id].label=study.labels[ply];studyTree!.nodes[id].score=scores[ply]}
    }
    private mutating func projectStudyTree(at node:Int) {
        let tree=studyTree!,path=tree.activePath
        study.positions=path.map{tree.nodes[$0].position};study.labels=path.map{tree.nodes[$0].label}
        scores=Dictionary(uniqueKeysWithValues:path.enumerated().compactMap{ply,id in tree.nodes[id].score.map{(ply,$0)}})
        cursor=tree.path(to:node).count-1
    }
    mutating func appendStudyPosition(_ position:String,label:String) {
        captureStudyTree();let parent=studyTree!.activePath[cursor]
        let node=studyTree!.append(position,label:label,from:parent);projectStudyTree(at:node)
    }
    mutating func selectStudyNode(_ node:Int)->Bool {
        captureStudyTree();guard node>=0,node<studyTree!.nodes.count else{return false}
        studyTree!.select(node);projectStudyTree(at:node);return true
    }
    /// Steps within the displayed line; an unchosen fork resumes the main line afterwards.
    mutating func focusStudyPly(_ index:Int)->Bool {
        captureStudyTree();let path=studyTree!.activePath;guard index>=0,index<path.count else{return false}
        studyTree!.focus(path[index]);projectStudyTree(at:path[index]);return true
    }
    var studyNode:Int {studyTree?.activePath[cursor] ?? cursor}
    var studyRows:[StudyTree.Row] {(studyTree ?? StudyTree(record:study,scores:scores)).rows(current:studyNode)}
    var studyLine:[StudyTree.LineRow] {(studyTree ?? StudyTree(record:study,scores:scores)).line(current:studyNode)}
    func studyAlternatives(_ node:Int)->[StudyTree.Alternative] {(studyTree ?? StudyTree(record:study,scores:scores)).alternatives(of:node)}
    /// Number of alternatives for the next move after the cursor, or 0 when there is no fork there.
    var studyBranchesAfterCursor:Int {let line=studyLine;guard cursor+1<line.count,line[cursor+1].total>1 else{return 0};return line[cursor+1].total}
}
