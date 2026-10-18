"""Run BOTH exact APK ARM64 PIE executables with pinned Android 16 bionic.
QEMU user mode validates ARM64 code/protocol, not a physical phone or Android UI.
The old static-only diagnostic could not load; this uses the shipped format.
"""
import base64, hashlib, json, pathlib, shlex, shutil, subprocess, zipfile
import shogi
from usi_client import Engine
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'outputs';work=root/'.ai-cache/arm64-dynamic';work.mkdir(parents=True,exist_ok=True)
runtime_commit='224a13857d4487d49ebd0456f990bd18735f3dae'
runtime_sha='83bf0dce249728dae48149b80d28b48115c54adad95a352120d58a6ac669d1fc'
apex=work/'runtime.apex'
if not apex.exists():
 data=subprocess.check_output(['curl','-fLsS','--retry','3','--max-time','120',f'https://android.googlesource.com/platform/prebuilts/runtime/+/{runtime_commit}/mainline/runtime/apex/com.android.runtime-arm64.apex?format=TEXT'])
 apex.write_bytes(base64.b64decode(data))
assert hashlib.sha256(apex.read_bytes()).hexdigest()==runtime_sha
with zipfile.ZipFile(apex) as z:(work/'runtime.img').write_bytes(z.read('apex_payload.img'))
runtime=work/'runtime';runtime.mkdir(exist_ok=True)
for src,name in [('/bin/linker64','linker64'),('/lib64/bionic/libc.so','libc.so'),('/lib64/bionic/libdl.so','libdl.so'),('/lib64/bionic/libm.so','libm.so')]:
 subprocess.run(['debugfs','-R',f'dump {src} {runtime/name}',str(work/'runtime.img')],check=True,stdout=subprocess.DEVNULL)
(runtime/'linker64').chmod(0o755)
apk=root/'outputs/Shogi_3min.apk'
with zipfile.ZipFile(apk) as z:
 for n in z.namelist():
  if n.startswith('assets/ai/tsuyou11micro/'):
   p=work/'eval'/pathlib.PurePosixPath(n).name;p.parent.mkdir(exist_ok=True);p.write_bytes(z.read(n))
 for name in ['libyaneuraou.so','libyaneuraou_compat.so']:
  (work/name).write_bytes(z.read('lib/arm64-v8a/'+name));(work/name).chmod(0o755)
positions=['lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1','lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1','lnsgkgsnl/1r5b1/pppppp1pp/6p2/7P1/9/PPPPPPP1P/1B5R1/LNSGKGSNL w - 1']
records=[]
for cpu in ['cortex-a53','max']:
 for name in ['libyaneuraou.so','libyaneuraou_compat.so']:
  wrapper=work/'run-engine'
  args=[shutil.which('qemu-aarch64'),'-cpu',cpu,'-E','LD_LIBRARY_PATH='+str(runtime),str(runtime/'linker64'),str(work/name)]
  wrapper.write_text('#!/bin/sh\nexec '+shlex.join(args)+'\n');wrapper.chmod(0o755)
  e=Engine(wrapper,work/'eval')
  try:
   for repeat in range(3):
    for sfen in positions:
     # Exact app sequence: another ready barrier, then MultiPV/position/go.
     e.send('isready');e.until('readyok');e.send('setoption name MultiPV value 3');e.send('position sfen '+sfen);e.send('go nodes 3000 movetime 750');rows={}
     while True:
      line=e.read();a=line.split()
      if line.startswith('info ') and 'pv' in a and 'score' in a:
       rank=int(a[a.index('multipv')+1]) if 'multipv' in a else 1
       rows[rank]=a[a.index('pv')+1]
      if line.startswith('bestmove '):
       board=shogi.Board(sfen);assert shogi.Move.from_usi(a[1]) in board.legal_moves,line
       assert len(rows)==3,rows
       for move in rows.values():assert shogi.Move.from_usi(move) in board.legal_moves,move
       records.append(dict(cpu=cpu,engine=name,repeat=repeat,sfen=sfen,bestmove=a[1],candidates=rows));break
  finally:e.close()
  assert e.p.returncode==0,e.p.returncode
result=dict(status='PASS',android_runtime_commit=runtime_commit,android_runtime_sha256=runtime_sha,apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),native_sha256={n:hashlib.sha256((work/n).read_bytes()).hexdigest() for n in ['libyaneuraou.so','libyaneuraou_compat.so']},legal_searches=len(records),physical_phone_verified=False,original_phone_crash_reproduced=False,records=records)
(out/'arm64_dynamic.json').write_text(json.dumps(result,indent=2));print('PASS exact APK ARM64 dynamic PIE + Android 16 bionic:',len(records),'searches, 3 independently legal candidates each, 2 CPU profiles, normal + scalar engines. Physical phone remains unverified.')
