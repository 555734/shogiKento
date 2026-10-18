"""Optional QEMU diagnostic. A failure is recorded as UNVERIFIED, never PASS."""
import hashlib,json,os,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1];exe=root/'.ai-cache/android-arm64-static'
subprocess.run(['git','fetch','origin','build/native-shogi-38990128561'],cwd=root,check=True)
with exe.open('wb') as out:
 subprocess.run(['git','show','FETCH_HEAD:native-three-minute/outputs/yaneuraou-arm64-static'],cwd=root,stdout=out,check=True)
data=exe.read_bytes();assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()=='87a9ae0cfdb05adb3f598de22f96a94f8f529601';exe.chmod(0o755)
try:
 r=subprocess.run(['qemu-aarch64','-cpu','cortex-a53',str(exe)],input='usi\nquit\n',text=True,errors='replace',capture_output=True,timeout=12)
 result=dict(status='STARTUP_ONLY' if r.returncode==0 and 'usiok' in r.stdout else 'UNVERIFIED',exit=r.returncode,stdout=r.stdout,stderr=r.stderr)
except subprocess.TimeoutExpired as e:result=dict(status='UNVERIFIED',reason='QEMU startup timed out')
result['elf']=subprocess.run(['readelf','-l',str(exe)],text=True,capture_output=True).stdout
(root/'outputs/arm64_diagnostic.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
