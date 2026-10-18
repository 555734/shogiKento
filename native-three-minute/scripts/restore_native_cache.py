"""Reuse only our exact pinned, previously CI-built native engine objects."""
import hashlib,json,os,pathlib,subprocess,sys,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
recipe=(root/'scripts/build_yaneura.py').read_bytes()+(root/'scripts/prepare_yaneura.py').read_bytes()
if hashlib.sha256(recipe).hexdigest()!='1ad312d1a6271f7878f02e30943fb393f5858d7a38d617e3ce92072f2570192f':
 print('Native recipe changed; compile from source instead.');sys.exit(3)
cache=root/'.ai-cache/ci-native.apk';cache.parent.mkdir(exist_ok=True)
subprocess.run(['git','fetch','origin','build/native-shogi-39040500303'],cwd=root,check=True)
with cache.open('wb') as out:
 subprocess.run(['git','show','FETCH_HEAD:native-three-minute/outputs/Shogi_3min.apk'],cwd=root,stdout=out,check=True)
assert hashlib.sha256(cache.read_bytes()).hexdigest()=='d33141217ffbe29c95b34b0ab5a279a1cc33df10e5510070ea2a7d142f9a242a'
records=[]
with zipfile.ZipFile(cache) as z:
 for abi in ['arm64-v8a','x86_64']:
  for name in ['libyaneuraou.so','libyaneuraou_compat.so']:
   data=z.read('lib/'+abi+'/'+name);dest=root/'app/src/main/jniLibs'/abi/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);dest.chmod(0o755)
   records.append(dict(abi=abi,engine=name,sha256=hashlib.sha256(data).hexdigest()))
 for name in z.namelist():
  if name.startswith('assets/licenses/Android-NDK-NOTICE'):
   (root/'app/src/main/assets/licenses'/pathlib.PurePosixPath(name).name).write_bytes(z.read(name))
(root/'outputs/native_cache.json').write_text(json.dumps(dict(source_run=39040500303,recipe_sha256=hashlib.sha256(recipe).hexdigest(),native_objects=records),indent=2))
print('PASS exact previously built native objects and NDK notices restored; application Java will be rebuilt.')

