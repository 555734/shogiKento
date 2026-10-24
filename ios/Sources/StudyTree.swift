import Foundation

/// Nodes use integer parents so long records serialize without recursive object graphs.
struct StudyTree: Codable {
    struct Node: Codable {
        var position:String,label:String,parent:Int?,preferred:Int?,score:Evaluation?
    }
    struct Row: Identifiable {
        var id:Int,ply:Int,indent:Int,branch:Int,total:Int,label:String,current:Bool
    }
    var nodes:[Node]
    init(record:GameRecord,scores:[Int:Evaluation]) {
        nodes=record.positions.indices.map { i in
            Node(position:record.positions[i],label:record.labels[i],parent:i==0 ? nil:i-1,
                 preferred:i+1<record.positions.count ? i+1:nil,score:scores[i])
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
    mutating func select(_ node:Int) {
        let path=path(to:node)
        for i in path.indices.dropFirst(){nodes[path[i-1]].preferred=path[i]}
    }
    mutating func append(_ position:String,label:String,from parent:Int)->Int {
        if let existing=nodes.indices.first(where:{nodes[$0].parent==parent && nodes[$0].position==position}){select(existing);return existing}
        let id=nodes.count;nodes.append(Node(position:position,label:label,parent:parent));nodes[parent].preferred=id;select(id);return id
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
            if let next=nodes[i].preferred {guard next>i,next<nodes.count,nodes[next].parent==i else{throw ShogiError.message("分岐の続きが不正です")}}
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
    var studyNode:Int {studyTree?.activePath[cursor] ?? cursor}
    var studyRows:[StudyTree.Row] {(studyTree ?? StudyTree(record:study,scores:scores)).rows(current:studyNode)}
}
