"""Pinned GPL engine and MIT TsuyouraOu11micro parameters; offline at runtime."""
import hashlib,json,pathlib,shutil,subprocess,tarfile
import py7zr
ROOT=pathlib.Path(__file__).resolve().parents[1]
ENGINE=dict(version='9.80git',date='2026-09-14',commit='c1b80eaa09fe13d5f12b1599d1ae4d53c224de30',url='https://codeload.github.com/yaneurao/YaneuraOu/tar.gz/c1b80eaa09fe13d5f12b1599d1ae4d53c224de30',sha='f0b655fc89fb4b5e58c6c9122d3bcc8ea8d9f46e6ba7229679309fff005de92b',license='GPL-3.0')
MODEL=dict(name='TsuyouraOu11micro',display_name='つようら王11micro',release_date='2026-10-14',arch='SFNN_ka2_2048_8_64_progress8',url='https://storage.yaneu.com/yaneuraou/TuyouraOu11micro-SFNN_ka2_2048_8_64_progress8-epoch0010-20261001.7z',sha='d1326e9a620c30d40ae553259dd0b6828d2f4e9fca3b6ab9440566579ab0e8c2',source_url='https://yaneurao.fanbox.cc/posts/12761272',license='MIT',fv_scale=24,runtime_dir='tsuyou11micro',files={
 'nn.bin':dict(bytes=3864610,sha256='7f9545dbf73b87acf312146daabf59c4f17d0845c87d587fa19caed51707dddb'),
 'progress.bin':dict(bytes=1003104,sha256='a2d18f0564639c945b5d6469e9b437cd64a526ad4fbcef02ab1d8f547d08e5f1'),
 'eval_options.txt':dict(bytes=12,sha256='7c34b2222d30e2dae828d583dce5973b4a96dc4af427ec2ff174e1df63a57ed7'),
 'tag.json':dict(bytes=376,sha256='988dbdd539435837c2686b9fcdb71d5f68ef44587ad4657ba865871689877108')})
def obtain(spec,name):
 p=ROOT/'.ai-cache'/name;p.parent.mkdir(exist_ok=True)
 if not p.exists():subprocess.run(['curl','-fL','--retry','3',spec['url'],'-o',str(p)],check=True)
 assert hashlib.sha256(p.read_bytes()).hexdigest()==spec['sha'],name
 return p
engine=ROOT/'third_party/yaneuraou';engine.mkdir(parents=True,exist_ok=True)
with tarfile.open(obtain(ENGINE,'yaneura-20260914.tar.gz')) as t:
 for m in t.getmembers():
  parts=pathlib.PurePosixPath(m.name).parts[1:]
  if parts and '..' not in parts and m.isfile():
   p=engine.joinpath(*parts);p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(t.extractfile(m).read())
model_dir=ROOT/'app/src/main/assets/ai'/MODEL['runtime_dir'];model_dir.mkdir(parents=True,exist_ok=True)
with py7zr.SevenZipFile(obtain(MODEL,'tsuyou11micro.7z')) as z:z.extract(path=model_dir,targets=list(MODEL['files']))
for name,spec in MODEL['files'].items():
 data=(model_dir/name).read_bytes();assert len(data)==spec['bytes'] and hashlib.sha256(data).hexdigest()==spec['sha256'],name
assert json.loads((model_dir/'tag.json').read_text())['arch']==MODEL['arch']
for old in ['suisho5','full','light']:shutil.rmtree(ROOT/'app/src/main/assets/ai'/old,ignore_errors=True)
for old in ['suisho5','sunfish4']:shutil.rmtree(ROOT/'third_party'/old,ignore_errors=True)
licenses=ROOT/'app/src/main/assets/licenses';licenses.mkdir(exist_ok=True)
shutil.copy(engine/'LICENSE',licenses/'YaneuraOu-GPL-3.0.txt')
for old in ['Suisho5-GPL-3.0.txt','Suisho5-source-README.md','Sunfish4-full-MIT.txt','Sunfish4-light-MIT.txt']:(licenses/old).unlink(missing_ok=True)
(ROOT/'app/src/main/assets/ai/provenance.json').write_text(json.dumps(dict(engine=ENGINE,model=MODEL),ensure_ascii=False,indent=2)+'\n')
print('PASS pinned GPL engine, MIT micro nn.bin/progress.bin, metadata hashes; obsolete packaged models removed')
