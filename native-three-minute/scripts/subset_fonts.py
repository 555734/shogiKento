"""The one bundled typeface family, Zen Kaku Gothic New in three weights (SIL Open Font License 1.1), subset to the characters the app draws.

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
 'ZenKakuGothicNew-Black.ttf':('zenkakugothicnew','ZEN_SHA_BLACK'),
}
SHA={'ZenKakuGothicNew-Medium.ttf': '651a3f7280b7f36262601ee76d8388a8dc4372dcc67aff025a608939a562b525', 'ZenKakuGothicNew-Bold.ttf': '0081cedabc4921982fcd061f845a005664ac7fb642af2dd34b4007bc63ccd235', 'ZenKakuGothicNew-Black.ttf': '795819a979184981842994d8f4eb9e14ce443d687bd5e731d6ca67ded8f92261'}
def fetch(name,family):
 path=SRC/name
 if not path.exists():SRC.mkdir(exist_ok=True);path.write_bytes(urllib.request.urlopen(BASE+family+'/'+name).read())
 digest=hashlib.sha256(path.read_bytes()).hexdigest()
 if digest!=SHA[name]:sys.exit(f'{name}: sha256 {digest} does not match the pinned {SHA[name]}')
 return path
def characters():
 text=''
 for f in list((ROOT/'app/src/main/java').rglob('*.java'))+list((ROOT.parent/'ios/Sources').rglob('*.swift')):
  # Every non-ASCII character of the sources (string literals and comments alike): a small superset.
  text+=''.join(ch for ch in f.read_text(encoding='utf-8') if ord(ch)>127)
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
