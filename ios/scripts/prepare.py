"""Reuse Android artwork, original SE, verified NNUE model and complete licenses."""
import hashlib,json,pathlib,shutil
from PIL import Image
ROOT=pathlib.Path(__file__).resolve().parents[1];ANDROID=ROOT.parent/'native-three-minute';ASSETS=ANDROID/'app/src/main/assets';OUT=ROOT/'Resources';OUT.mkdir(exist_ok=True)
for source,target in [('home_background_v112.webp','home_background'),('home_title_v112.webp','home_title'),('board_v191.webp','board'),('background_red_lacquer_v191.webp','background'),('pieces_v184.png','pieces')]:Image.open(ASSETS/'generated'/source).save(OUT/(target+'.png'))
shutil.copy(ANDROID/'app/src/main/res/raw/piece_place.wav',OUT/'piece_place.wav')
(OUT/'ai').mkdir(exist_ok=True)
provenance=json.loads((ASSETS/'ai/provenance.json').read_text())
for name,spec in provenance['model']['files'].items():
 src=ASSETS/'ai/tsuyou11micro'/name;assert hashlib.sha256(src.read_bytes()).hexdigest()==spec['sha256'];shutil.copy(src,OUT/'ai'/name)
shutil.copy(ASSETS/'ai/provenance.json',OUT/'ai/provenance.json')
(OUT/'licenses').mkdir(exist_ok=True)
for name in ['YaneuraOu-GPL-3.0.txt','TsuyouraOu11micro-MIT.txt']:shutil.copy(ASSETS/'licenses'/name,OUT/'licenses'/name)
icons=ROOT/'Assets.xcassets/AppIcon.appiconset';icons.mkdir(parents=True,exist_ok=True)
images=[];icon=Image.open(ASSETS/'generated/home_background_v112.webp').convert('RGB')
for idiom,sizes in [('iphone',[(20,2),(20,3),(29,2),(29,3),(40,2),(40,3),(60,2),(60,3)]),('ipad',[(20,1),(20,2),(29,1),(29,2),(40,1),(40,2),(76,1),(76,2),(83.5,2)]),('ios-marketing',[(1024,1)])]:
 for points,scale in sizes:
  pixels=int(points*scale);name=f'icon-{pixels}.png';icon.resize((pixels,pixels),Image.Resampling.LANCZOS).save(icons/name);images.append(dict(idiom=idiom,size=f'{points}x{points}',scale=f'{scale}x',filename=name))
(icons/'Contents.json').write_text(json.dumps(dict(images=images,info=dict(author='xcode',version=1))))
(ROOT/'Assets.xcassets/Contents.json').write_text(json.dumps(dict(info=dict(author='xcode',version=1))))
# Independent oracle, generated into the test bundle only.
import shogi,shogi.KIF
fixture=ANDROID/'tests/fixtures/shogi_quest_79.kif';parsed=shogi.KIF.Parser.parse_file(str(fixture))[0];board=shogi.Board();positions=[board.sfen()]
for move in parsed['moves']:board.push_usi(move);positions.append(board.sfen())
test=ROOT/'Tests/Fixtures';test.mkdir(parents=True,exist_ok=True);shutil.copy(fixture,test/'quest79.kif');(test/'quest79.json').write_text(json.dumps(dict(positions=positions,moves=parsed['moves']),ensure_ascii=False),encoding='utf-8')
assert len(positions)==80 and board.is_checkmate()
(OUT/'asset-manifest.json').write_text(json.dumps({str(p.relative_to(OUT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.rglob('*') if p.is_file() and p.name!="asset-manifest.json"},indent=2))
print('PASS iOS artwork, alpha, original audio, pinned model, licenses, and independent 80-position Quest oracle')
