"""Retain notices for the statically linked NDK C++ runtime/compiler support."""
import pathlib,shutil,sys
ndk=pathlib.Path(sys.argv[1]);out=pathlib.Path('app/src/main/assets/licenses');out.mkdir(parents=True,exist_ok=True)
found=[]
for name in ['NOTICE','NOTICE.toolchain']:
 p=ndk/name
 if p.is_file():
  assert p.stat().st_size>100
  target=out/('Android-NDK-'+name+'.txt');shutil.copyfile(p,target);found.append(target.name)
assert found,'Missing NDK distribution notices: '+str(ndk)
print('Bundled NDK notices:',','.join(found))
