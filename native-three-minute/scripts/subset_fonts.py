"""Bundled typefaces (all SIL Open Font License 1.1), subset to the characters the app draws.

Sources are the Google Fonts repository files pinned by SHA-256. Every Java/Swift string literal, the kana,
ASCII and common punctuation are kept, so fixed UI text renders in the bundled faces; any other character
(for example a player's own name) falls back to the system font on both platforms.
"""
import hashlib,pathlib,re,sys,urllib.request
from fontTools import subset
ROOT=pathlib.Path(__file__).resolve().parents[1];SRC=ROOT/'fonts-src';OUT=ROOT/'app/src/main/assets/fonts';LIC=ROOT/'app/src/main/assets/licenses'
BASE='https://raw.githubusercontent.com/google/fonts/main/ofl/'
FONTS={
 'ZenKakuGothicNew-Medium.ttf':('zenkakugothicnew','ZEN_SHA_MEDIUM'),
 'ZenKakuGothicNew-Bold.ttf':('zenkakugothicnew','ZEN_SHA_BOLD'),
 'ShipporiMincho-ExtraBold.ttf':('shipporimincho','SHIPPORI_SHA'),
 'YujiSyuku-Regular.ttf':('yujisyuku','YUJI_SHA'),
}
SHA={'ZenKakuGothicNew-Medium.ttf': '651a3f7280b7f36262601ee76d8388a8dc4372dcc67aff025a608939a562b525', 'ZenKakuGothicNew-Bold.ttf': '0081cedabc4921982fcd061f845a005664ac7fb642af2dd34b4007bc63ccd235', 'ShipporiMincho-ExtraBold.ttf': 'bdb787644b4b347e9a7efdd576f0d16ee4528cc9b5c86d23e06fa1a14ae0444c', 'YujiSyuku-Regular.ttf': '82728ebafc8c97391e2dab633414a806f344b8e4e2227d307179f07b548fca61'}
def fetch(name,family):
 path=SRC/name
 if not path.exists():SRC.mkdir(exist_ok=True);path.write_bytes(urllib.request.urlopen(BASE+family+'/'+name).read())
 digest=hashlib.sha256(path.read_bytes()).hexdigest()
 if digest!=SHA[name]:sys.exit(f'{name}: sha256 {digest} does not match the pinned {SHA[name]}')
 return path
def characters():
 text=''
 for f in list((ROOT/'app/src/main/java').rglob('*.java'))+list((ROOT.parent/'ios/Sources').rglob('*.swift')):
  for lit in re.findall(r'"((?:[^"\\\\]|\\\\.)*)"',f.read_text(encoding='utf-8')):text+=lit
 text+=''.join(chr(c) for c in range(0x20,0x7f))+''.join(chr(c) for c in range(0x3041,0x3097))+''.join(chr(c) for c in range(0x30a1,0x30fb))
 text+='、。・ー「」『』（）［］【】〜！？：／％＋－×▲△▼▶◆●○◇☆★…　'+'一二三四五六七八九十百千'
 return ''.join(sorted(set(text)))
def main():
 chars=characters();OUT.mkdir(parents=True,exist_ok=True)
 for name,(family,_) in FONTS.items():
  options=subset.Options();options.layout_features=['*'];options.name_IDs=['*'];options.notdef_outline=True;options.hinting=False
  font=subset.load_font(str(fetch(name,family)),options);s=subset.Subsetter(options);s.populate(text=chars);s.subset(font);subset.save_font(font,str(OUT/name),options)
  print(f'{name}: {len(chars)} characters, {(OUT/name).stat().st_size} bytes')
 for family in sorted({f for f,_ in FONTS.values()}):
  (LIC/f'{family}-OFL.txt').write_bytes(urllib.request.urlopen(BASE+family+'/OFL.txt').read())
if __name__=='__main__':main()
