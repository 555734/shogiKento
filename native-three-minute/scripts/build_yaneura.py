"""Unmodified upstream USI executable; Android packaging uses nativeLibraryDir."""
import argparse,pathlib,subprocess,os
ROOT=pathlib.Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--ndk');p.add_argument('--arm64-smoke',action='store_true');a=p.parse_args();source=ROOT/'third_party/yaneuraou/source'
profiles=[('host','g++','SSE2','','')]
if a.ndk:
 tool=pathlib.Path(a.ndk)/'toolchains/llvm/prebuilt/linux-x86_64/bin'
 profiles=[('arm64-v8a',str(tool/'aarch64-linux-android24-clang++'),'ARMV8','-march=armv8-a -fPIE -DUSE_PTHREADS','-pie -static-libstdc++ -Wl,-z,max-page-size=16384'),('x86_64',str(tool/'x86_64-linux-android24-clang++'),'SSE2','-fPIE -DUSE_PTHREADS','-pie -static-libstdc++ -Wl,-z,max-page-size=16384')]
if a.ndk:
 profiles += [(abi+'-compat',compiler,'OTHER',cflags+' -DIS_64BIT',lflags) for abi,compiler,cpu,cflags,lflags in profiles]
for abi,compiler,cpu,cflags,lflags in profiles:
 out=ROOT/'host-bench/yaneuraou' if abi=='host' else ROOT/'app/src/main/jniLibs'/abi.removesuffix('-compat')/('libyaneuraou_compat.so' if abi.endswith('-compat') else 'libyaneuraou.so');out.parent.mkdir(parents=True,exist_ok=True)
 log=ROOT/'outputs'/('build-yaneura-'+abi+'.txt');log.parent.mkdir(exist_ok=True)
 with log.open('w') as f:r=subprocess.run(['make','-j'+str(min(os.cpu_count() or 2,4)),'normal','COMPILER='+compiler,'TARGET_CPU='+cpu,'YANEURAOU_EDITION=YANEURAOU_ENGINE_SFNN_ka2_2048_8_64_progress8','LTOFLAGS=','OBJDIR='+str(ROOT/'host-bench'/('obj-micro-'+abi)),'TARGET='+str(out),'EXTRA_CPPFLAGS='+cflags,'EXTRA_LDFLAGS='+lflags]+(['LDFLAGS='+lflags+' -fuse-ld=lld -Wl,-s'] if a.ndk else []),cwd=source,stdout=f,stderr=subprocess.STDOUT)
 if r.returncode:print(log.read_text()[-16000:]);raise SystemExit(r.returncode)
 out.chmod(0o755);print('Built',abi,out.stat().st_size)

# Re-link exactly the packaged ARM64 objects against static Android bionic so
# CI can execute the phone instruction path with QEMU, without an ARM phone.
if a.ndk and a.arm64_smoke:
 tool=pathlib.Path(a.ndk)/'toolchains/llvm/prebuilt/linux-x86_64/bin'
 out=ROOT/'outputs/yaneuraou-arm64-static'
 log=ROOT/'outputs/build-yaneura-arm64-static.txt'
 with log.open('w') as f:
  r=subprocess.run(['make','-j2','normal','COMPILER='+str(tool/'aarch64-linux-android24-clang++'),'TARGET_CPU=ARMV8','YANEURAOU_EDITION=YANEURAOU_ENGINE_SFNN_ka2_2048_8_64_progress8','LTOFLAGS=','OBJDIR='+str(ROOT/'host-bench/obj-micro-arm64-v8a'),'TARGET='+str(out),'EXTRA_CPPFLAGS=-march=armv8-a -fPIE -DUSE_PTHREADS','LDFLAGS=-static -static-libstdc++ -fuse-ld=lld -Wl,-s -Wl,-z,max-page-size=16384'],cwd=source,stdout=f,stderr=subprocess.STDOUT)
 if r.returncode:print(log.read_text()[-16000:]);raise SystemExit(r.returncode)
 out.chmod(0o755)

