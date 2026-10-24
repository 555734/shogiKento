"""Package device build honestly as unsigned; simulator ZIP is a separate product."""
import hashlib,json,os,pathlib,plistlib,shutil,subprocess,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'outputs';OUT.mkdir(exist_ok=True)
app=ROOT/'build/DeviceData/Build/Products/Release-iphoneos/BeniShogi.app'
report={'source_sha':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'device_build':False,'apple_distribution_signed':False,'simulator_tests_passed':False}
if app.is_dir() and '** BUILD SUCCEEDED **' in (OUT/'device-build.txt').read_text(errors='replace'):
 info=plistlib.loads((app/'Info.plist').read_bytes());assert info['CFBundleIdentifier']=='com.presentsoft.shogi3minutes';assert info['CFBundleExecutable']=='BeniShogi';assert info['GADApplicationIdentifier']=='ca-app-pub-6772282664898856~6282999949';report['ads_test_only']=False;report['admob_app_id']=info['GADApplicationIdentifier']
 executable=app/'BeniShogi';architecture=subprocess.check_output(['xcrun','lipo','-archs',str(executable)],text=True).strip();assert architecture=='arm64',architecture
 signed=subprocess.run(['codesign','--display',str(app)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
 # A linker ad-hoc signature, if present, is still not Apple distribution signing.
 assert not (app/'embedded.mobileprovision').exists(),'Unexpected provisioning profile in unsigned build'
 payload=ROOT/'build/Unsigned/Payload';payload.mkdir(parents=True,exist_ok=True);shutil.copytree(app,payload/app.name,dirs_exist_ok=True)
 subprocess.run(['ditto','-c','-k','--keepParent','Payload',str(OUT/'BeniShogi-unsigned.ipa')],cwd=payload.parent,check=True)
 manifest=json.loads((app/'GameAssets/asset-manifest.json').read_text())
 for name,expected in manifest.items():assert hashlib.sha256((app/'GameAssets'/name).read_bytes()).hexdigest()==expected,name
 report.update(device_build=True,device_architecture=architecture,ad_hoc_code_signature=signed,bundled_asset_hashes_verified=len(manifest),ipa_sha256=hashlib.sha256((OUT/'BeniShogi-unsigned.ipa').read_bytes()).hexdigest())
sim=ROOT/'build/DerivedData/Build/Products/Debug-iphonesimulator/BeniShogi.app'
if sim.is_dir():
 sim_info=plistlib.loads((sim/'Info.plist').read_bytes());assert sim_info['GADApplicationIdentifier']=='ca-app-pub-3940256099942544~1458002511';report['simulator_ads_test_only']=True
 subprocess.run(['ditto','-c','-k','--keepParent',str(sim),str(OUT/'BeniShogi-simulator.app.zip')],check=True)
test=OUT/'simulator-tests.txt'
if test.exists():
 text=test.read_text(errors='replace');report['simulator_tests_passed']=text.count('** TEST EXECUTE SUCCEEDED **')==2 and '** TEST EXECUTE FAILED **' not in text
with zipfile.ZipFile(OUT/'BeniShogi-complete-source.zip','w',zipfile.ZIP_DEFLATED) as z:
 for directory,prefix in [(ROOT,'BeniShogi/ios'),(ROOT.parent/'native-three-minute/third_party/yaneuraou','BeniShogi/native-three-minute/third_party/yaneuraou')]:
  if directory.exists():
   for p in directory.rglob('*'):
    relative=p.relative_to(directory)
    if p.is_file() and not any(s in relative.parts for s in ['build','outputs','__pycache__','.git','BeniShogi.xcodeproj']):z.write(p,pathlib.Path(prefix)/relative)
 # Asset preparation is reproducible from the corresponding Android artwork and pinned model recipe.
 for folder in ['scripts','app/src/main/assets/generated','app/src/main/res/raw']:
  base=ROOT.parent/'native-three-minute'/folder
  for p in base.rglob('*'):
   if p.is_file() and '__pycache__' not in p.parts:z.write(p,pathlib.Path('BeniShogi/native-three-minute')/p.relative_to(ROOT.parent/'native-three-minute'))
 z.write(ROOT.parent/'.github/workflows/ios.yml','BeniShogi/.github/workflows/ios.yml')
(OUT/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
(OUT/'README.md').write_text('''# 紅将棋 iOS build

`BeniShogi-unsigned.ipa` は端末向けarm64ビルドです。Appleの配布証明書・プロビジョニングによる署名は含みません。そのままiPhoneへインストールする配布物やTestFlight公開ではありません。

`BeniShogi-simulator.app.zip` はSimulator用です。実機へはインストールできません。

`BeniShogi-complete-source.zip` は固定したGPL-3.0エンジン、MIT評価関数・ライセンス、iOSアプリ・ブリッジ・ビルド手順を含む対応ソースです。

ビルドしたソースSHA、各成果物の状態は `build-info.json`、実行結果はActionsとテストログ・xcresultで確認してください。失敗したrunの成果物を検証済みとして扱わないでください。
''',encoding='utf-8')
print(json.dumps(report))
