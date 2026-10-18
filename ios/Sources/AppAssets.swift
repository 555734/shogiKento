import UIKit
enum AppAssets {
    private static let cache=NSCache<NSString,UIImage>()
    static func image(_ name:String)->UIImage {
        if let image=cache.object(forKey:name as NSString){return image}
        guard let url=Bundle.main.url(forResource:name,withExtension:"png",subdirectory:"GameAssets"),let image=UIImage(contentsOfFile:url.path) else{return UIImage()}
        cache.setObject(image,forKey:name as NSString);return image
    }
    static func piece(_ value:Int)->UIImage {
        let index: Int;if abs(value)<=8{index=abs(value)-1}else{index=[9:9,10:10,11:11,12:12,14:13,15:14][abs(value)] ?? 0}
        let key="piece-\(index)";if let cached=cache.object(forKey:key as NSString){return cached}
        let rects=[[49,35,295,303],[353,35,600,303],[653,35,901,303],[956,35,1205,303],[48,341,297,609],[351,340,602,609],[652,341,901,609],[955,340,1206,608],[48,646,298,915],[349,645,603,915],[652,645,901,915],[954,645,1206,915],[48,948,298,1214],[350,948,603,1214],[651,948,902,1214]]
        let b=rects[index];guard let cg=image("pieces").cgImage?.cropping(to:CGRect(x:b[0]-3,y:b[1]-3,width:b[2]-b[0]+6,height:b[3]-b[1]+6)) else{return UIImage()};let result=UIImage(cgImage:cg);cache.setObject(result,forKey:key as NSString);return result
    }
}
