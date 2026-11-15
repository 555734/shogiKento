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
    /// Displays the path to node, then the chosen branch where one was picked, otherwise the main line.
    mutating func focus(_ node:Int) {
        let path=path(to:node)
        for i in path.indices.dropFirst(){nodes[path[i-1]].preferred=path[i]}
        let main=mainChildren();var current=node
        while true {
            var next=main[current]
            if let chosen=nodes[current].chosen,chosen>current,chosen<nodes.count,nodes[chosen].parent==current{next=chosen}
            nodes[current].preferred=next
            guard let id=next else{break};current=id
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
}
