"""Pinned, hash-checked upstream sources/evaluation data. No runtime downloads."""
import hashlib,io,json,pathlib,shutil,subprocess,tarfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SPECS={
 'full':dict(commit='91cff975e68ccfcb89c13d4433bf0620a83f3adb',tag='v0.1.3',archive='sunfish4_v0.1.3_win64.zip',source_sha='4060eaf24a340b0138886acb58c1ef952a870aec213319a4963ae48728abc35c',release_sha='7c03f2313fe70ffce79d38e43f5e71f217c6ff64ae34db77f89fc64d52303dbf',eval_sha='3d547b73a0bee8a1b49dc76d1c278ee1fd7ac56ce33c233bfdc21f2d71ec1bea'),
 'light':dict(commit='8bbc6ac6fa65fff812fdde374223785fc597e79d',tag='v0.1.0-light',archive='sunfish4_v0.1.0_light_win64.zip',source_sha='aaf3ff6bbb15f124b121e9e0a18fb88fe20166b6da206da61d7edb6795ba1cd8',release_sha='b29b54863b80e9bec0d7a31ef69aeedd3a4e19c5d45e5c553b9d5f37ac5f2ab0',eval_sha='452f8cbd5e31bf427aa62bf10cef7a40f80975906d7b7800a4405a9c3df6ec4e')}
# codeload tar format depends on the ref spelling. Use the exact audited refs.
SPECS['light']['source_url']='https://codeload.github.com/sunfish-shogi/sunfish4/tar.gz/refs/tags/v0.1.0-light'
SPECS['full']['source_url']='https://codeload.github.com/sunfish-shogi/sunfish4/tar.gz/91cff975e68ccfcb89c13d4433bf0620a83f3adb'
def digest(data):return hashlib.sha256(data).hexdigest()
def obtain(url,sha,filename):
 cache=ROOT/'.ai-cache'/filename;cache.parent.mkdir(exist_ok=True)
 if not cache.exists():subprocess.run(['curl','-fL','--retry','3',url,'-o',str(cache)],check=True)
 data=cache.read_bytes();assert digest(data)==sha,(filename,digest(data),sha);return data
for variant,spec in SPECS.items():
 source=obtain(spec['source_url'],spec['source_sha'],variant+'-source.tar.gz')
 target=ROOT/'third_party'/('sunfish-'+variant);shutil.rmtree(target,ignore_errors=True);target.mkdir(parents=True)
 with tarfile.open(fileobj=io.BytesIO(source)) as t:
  for member in t.getmembers():
   parts=pathlib.PurePosixPath(member.name).parts[1:]
   if not parts or '..' in parts:continue
   dest=target.joinpath(*parts)
   if member.isdir():dest.mkdir(parents=True,exist_ok=True)
   elif member.isfile():dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(t.extractfile(member).read())
 subprocess.run(['python3',str(ROOT/'scripts/patch_sunfish.py'),str(target)],check=True)
 spec['release_url']='https://github.com/sunfish-shogi/sunfish4/releases/download/'+spec['tag']+'/'+spec['archive']
 release=obtain(spec['release_url'],spec['release_sha'],variant+'-release.zip')
 with zipfile.ZipFile(io.BytesIO(release)) as z:
  data=z.read(next(n for n in z.namelist() if n.endswith('/eval.bin')))
 assert digest(data)==spec['eval_sha'];spec['eval_bytes']=len(data)
 path=ROOT/'app/src/main/assets/ai'/variant/'eval.bin';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 notices=ROOT/'app/src/main/assets/licenses';notices.mkdir(exist_ok=True)
 shutil.copy(target/'LICENSE.txt',notices/('Sunfish4-'+variant+'-MIT.txt'))
(ROOT/'app/src/main/assets/ai/provenance.json').write_text(json.dumps(SPECS,indent=2)+'\n')
print('Verified two MIT Sunfish sources and evaluation files')
