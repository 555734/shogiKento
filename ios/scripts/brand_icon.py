"""Draw the same geometric king icon as Android's vector; no external font needed."""
from PIL import Image, ImageDraw

def icon(size=1024):
    scale=4
    image=Image.new('RGB',(108*scale,108*scale),'#590914')
    draw=ImageDraw.Draw(image)
    def points(values):return [(int(x*scale),int(y*scale)) for x,y in values]
    draw.polygon(points([(54,15),(78,30),(85,88),(23,88),(30,30)]),fill='#F1C76A',outline='#FFF0BB',width=2*scale)
    for line in [[(40,40),(68,40)],[(43,57),(65,57)],[(37,75),(71,75)],[(54,40),(54,75)]]:
        draw.line(points(line),fill='#39200D',width=5*scale)
    return image.resize((size,size),Image.Resampling.LANCZOS)

if __name__=='__main__':
    import sys
    icon(int(sys.argv[2]) if len(sys.argv)>2 else 512).save(sys.argv[1])
