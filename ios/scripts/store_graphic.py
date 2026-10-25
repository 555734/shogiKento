"""Generate a code-native Play feature graphic matching the red/gold app brand."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from brand_icon import icon
import math, sys

out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path(sys.argv[2])
image=Image.new('RGB',(1024,500));draw=ImageDraw.Draw(image)
for y in range(500):
    amount=math.sin(math.pi*y/500)
    draw.line((0,y,1024,y),fill=(int(65+35*amount),9,int(19+3*amount)))
for x in range(660,1100,54):
    for y in range(-40,600,54):
        draw.arc((x-56,y-56,x+56,y+56),180,360,fill='#79502D',width=1)
draw.rounded_rectangle((22,22,1002,478),radius=20,outline='#CAA363',width=2)
mark=icon(238)
image.paste(mark,(66,131))
draw.text((342,134),'AI将棋検討',font=ImageFont.truetype(str(font_path),91),fill='#FFE6A3',stroke_width=1)
draw.line((350,270,942,270),fill='#C69A52',width=2)
draw.text((350,308),'棋譜を解析・CPUと対局・振り返り',font=ImageFont.truetype(str(font_path),31),fill='#FFF0CB')
image.save(out/'play-feature-1024x500.png')
icon(512).save(out/'play-icon-512.png')
print('Created 1024x500 feature graphic and 512x512 icon')
