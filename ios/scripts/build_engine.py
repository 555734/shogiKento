"""Compile the same pinned NNUE source as a static, in-process Apple library."""
import argparse,concurrent.futures,json,os,pathlib,platform,re,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];SOURCE=ROOT.parent/'native-three-minute/third_party/yaneuraou/source'
ARCHITECTURE='SFNN_ka2_2048_8_64_progress8'
p=argparse.ArgumentParser();p.add_argument('--sdk',choices=['iphoneos','iphonesimulator','macosx'],required=True);a=p.parse_args()
arch='arm64' if a.sdk=='iphoneos' else platform.machine()
sdk=subprocess.check_output(['xcrun','--sdk',a.sdk,'--show-sdk-path'],text=True).strip()
compiler=subprocess.check_output(['xcrun','--sdk',a.sdk,'--find','clang++'],text=True).strip()
target={'iphoneos':'arm64-apple-ios16.0','iphonesimulator':arch+'-apple-ios16.0-simulator','macosx':arch+'-apple-macos13.0'}[a.sdk]
out=ROOT/'build/engine'/a.sdk;out.mkdir(parents=True,exist_ok=True)
subprocess.run([sys.executable,'eval/nnue/architectures/nnue_arch_gen.py',ARCHITECTURE,'eval/nnue/architectures'],cwd=SOURCE,check=True)
makefile=(SOURCE/'Makefile').read_text(encoding='utf-8')
base=re.search(r'^SOURCES  = \\\n(.*?)\n\nCUDA_SOURCES',makefile,re.M|re.S).group(1)
sources=re.findall(r'[A-Za-z0-9_/-]+\.cpp',base)
sources.remove('main.cpp')
sources+=['book/makebook.cpp','book/makebook2025.cpp','eval/nnue/evaluate_nnue.cpp','eval/nnue/nnue_test_command.cpp']
sources+=['eval/nnue/features/'+name+'.cpp' for name in ['k','p','a2','half_kp','half_ka1','half_ka_hm1','half_ka2','half_ka_hm2','half_kp_vm','half_relative_kp','half_kpe9','pe9']]
sources+=['engine/yaneuraou-engine/yaneuraou-search.cpp']
flags=['-std=c++17','-O2','-DNDEBUG','-DNO_EXCEPTIONS','-fno-exceptions','-fno-rtti','-DIS_64BIT','-D_LINUX','-DUNICODE','-DUSE_PTHREADS','-DYANEURAOU_ENGINE_NNUE','-DSFNNwoPSQT','-DNNUE_ARCHITECTURE_HEADER="architectures/'+ARCHITECTURE+'.h"','-DTARGET_CPU="APPLE_EMBEDDED"','-target',target,'-isysroot',sdk,'-I',str(SOURCE),'-I',str(ROOT/'Engine'),'-Wno-unused-parameter']
if arch=='arm64':flags+=['-DUSE_NEON=8']
else:flags+=['-DUSE_SSE2','-msse2']
def compile_one(source):
 source=pathlib.Path(source);source=source if source.is_absolute() else SOURCE/source
 obj=out/(str(source.relative_to(SOURCE)).replace('/','_')+'.o') if source.is_relative_to(SOURCE) else out/'BKEngine.o'
 cmd=[compiler]+flags+['-c',str(source),'-o',str(obj)]
 r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 if r.returncode:raise RuntimeError(str(source)+'\n'+r.stdout)
 return str(obj)
with concurrent.futures.ThreadPoolExecutor(max_workers=min(4,os.cpu_count() or 2)) as pool:objects=list(pool.map(compile_one,sources+[str(ROOT/'Engine/BKEngine.cpp')]))
library=out/'libBKEngine.a';subprocess.run(['xcrun','libtool','-static','-o',str(library)]+objects,check=True)
(out/'build.json').write_text(json.dumps(dict(sdk=a.sdk,target=target,source_commit='c1b80eaa09fe13d5f12b1599d1ae4d53c224de30',architecture=ARCHITECTURE,flags=flags,sources=sources),indent=2))
print('PASS embedded Apple engine',a.sdk,target,library.stat().st_size,'bytes')
