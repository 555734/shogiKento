"""Restore this repository's exact, provenance-pinned native engine binaries."""
import gzip,hashlib,json,pathlib,sys
root=pathlib.Path(__file__).resolve().parents[1]
recipe=(root/'scripts/build_yaneura.py').read_bytes()+(root/'scripts/prepare_yaneura.py').read_bytes()
manifest=json.loads((root/'native-cache/manifest.json').read_text())
if hashlib.sha256(recipe).hexdigest()!=manifest['recipe_sha256']:
 print('Native recipe changed; compile from source instead.');sys.exit(3)
records=[]
for spec in manifest['files']:
 raw=(root/spec['path']).read_bytes();assert hashlib.sha256(raw).hexdigest()==spec['gzip_sha256']
 data=gzip.decompress(raw);assert len(data)==spec['bytes'] and hashlib.sha256(data).hexdigest()==spec['sha256']
 rel=pathlib.PurePosixPath(spec['path']);abi=rel.parts[-2];name=rel.name[:-3];dest=root/'app/src/main/jniLibs'/abi/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);dest.chmod(0o755)
 records.append(dict(abi=abi,engine=name,sha256=spec['sha256']))
assert len(records)==4
for n in ['Android-NDK-NOTICE.txt','Android-NDK-NOTICE.toolchain.txt']:assert (root/'app/src/main/assets/licenses'/n).is_file()
(root/'outputs').mkdir(exist_ok=True)
(root/'outputs/native_cache.json').write_text(json.dumps(dict(source_run=manifest['source_run'],source_apk_sha256=manifest['source_apk_sha256'],recipe_sha256=manifest['recipe_sha256'],native_objects=records,repository_local_cache=True),indent=2))
print('PASS repository-local pinned native cache restored; no access to the previous private repository is needed.')
