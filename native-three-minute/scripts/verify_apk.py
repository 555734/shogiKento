import zipfile,json,hashlib,sys,pathlib,wave,io,struct
root=pathlib.Path(__file__).resolve().parents[1];apk=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(apk) as z:
 names=z.namelist();assert b'ca-app-pub-3940256099942544/2247696110' in b''.join(z.read(n) for n in names if n.endswith('.dex')), 'Official native test ad ID missing';manifest=json.loads((root/'asset_manifest.json').read_text())
 for row in manifest:
  key='assets/original/'+row['file'];assert key in names,key
  assert hashlib.sha256(z.read(key)).hexdigest()==row['sha256'],key+' changed'
 assert len([n for n in names if n.startswith('assets/original/') and n.endswith('.png')])==37
 legacy=root/'design/generated/legacy'
 generated=json.loads((legacy/'pieces_v15.json').read_text())
 assert hashlib.sha256((legacy/'pieces_v15.png').read_bytes()).hexdigest()==generated['sha256'],'archived generated atlas changed'
 assert len(generated['tiles'])==16 and all(t['height_to_width']>1.30 for t in generated['tiles']),'tall pieces missing'
 current=json.loads(z.read('assets/generated/pieces_v184.json'))
 assert hashlib.sha256(z.read('assets/generated/pieces_v184.png')).hexdigest()==current['sha256'],'reference atlas changed'
 assert len(current['tiles'])==15 and all(1.04<t['height_to_width']<1.12 for t in current['tiles']),'broad reference pieces missing'
 assert [t['glyph_color'] for t in current['tiles']]==['black']*9+['red']*6,'promoted glyphs must be red'
 wood=json.loads((legacy/'board_v184.json').read_text())
 assert hashlib.sha256((legacy/'board_v184.webp').read_bytes()).hexdigest()==wood['sha256'] and wood['lossless'] is True,'archived generated board changed'
 assert not any(n.startswith(('assets/generated/pieces_v15.','assets/generated/board_v184.')) for n in names),'unused generated artwork packaged'
 for stem in ['background_red_lacquer_v191','board_v191']:
  art=json.loads(z.read('assets/generated/'+stem+'.json'))
  assert art['file']==stem+'.webp' and art['width']>0 and art['height']>0
  assert art['lossless'] is True and hashlib.sha256(z.read('assets/generated/'+art['file'])).hexdigest()==art['sha256'],'lacquer design asset changed: '+stem
 for art in json.loads(z.read('assets/generated/home_v112.json'))['assets']:
  assert hashlib.sha256(z.read('assets/generated/'+art['file'])).hexdigest()==art['sha256'],'home artwork mismatch'
 assert not any(n.lower().endswith(('.html','.htm','.js','.jsx','.tsx')) for n in names)
 # The chess UI remains native; the official ad SDK legitimately includes WebView.
 for p in (root/'app/src/main/java').rglob('*.java'):
  assert 'android.webkit.WebView' not in p.read_text(), 'App board must remain native'
 assert any(n.startswith('assets/licenses/Android-NDK-NOTICE') for n in names),'NDK C++ runtime notices missing'
 provenance=json.loads(z.read('assets/ai/provenance.json'))
 model=provenance['model'];assert model['name']=='TsuyouraOu11micro' and model['arch']=='SFNN_ka2_2048_8_64_progress8'
 for name,spec in model['files'].items():
  data=z.read('assets/ai/'+model['runtime_dir']+'/'+name);assert len(data)==spec['bytes'] and hashlib.sha256(data).hexdigest()==spec['sha256']
 assert not any(n.startswith(('assets/ai/suisho5/','assets/ai/full/','assets/ai/light/','assets/ai/tsuyou11mini/')) for n in names),'obsolete or unselected model in APK'
 assert b'GNU GENERAL PUBLIC LICENSE' in z.read('assets/licenses/YaneuraOu-GPL-3.0.txt')
 assert b'MIT License' in z.read('assets/licenses/TsuyouraOu11micro-MIT.txt')
 sound=z.read('res/raw/piece_place.wav')
 with wave.open(io.BytesIO(sound)) as w:
  assert w.getnchannels()==1 and w.getsampwidth()==2 and w.getframerate()==24000 and w.getnframes()==2880
  pcm=struct.unpack('<2880h',w.readframes(w.getnframes()));assert 20000<max(abs(x) for x in pcm)<32767
 assert apk.stat().st_size<65000000,'Unexpected APK size with official ads SDK'
 for abi in ['arm64-v8a','x86_64']:
  data=z.read('lib/'+abi+'/libyaneuraou_compat.so');assert data[:4]==b'\x7fELF';assert int.from_bytes(data[18:20],'little')==(183 if abi=='arm64-v8a' else 62)
  data=z.read('lib/'+abi+'/libyaneuraou.so');assert data[:4]==b'\x7fELF';assert int.from_bytes(data[18:20],'little')==(183 if abi=='arm64-v8a' else 62)
 assert 'AndroidManifest.xml'  in names and 'classes.dex' in names
 result={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'original_pngs':len(manifest),'all_original_hashes_match':True,'legacy_generated_artwork_preserved_in_source':True,'reference_piece_atlas_verified':True,'archived_reference_board_verified':True,'vermilion_lacquer_artwork_verified':True,'native_board_no_app_webview':True,'official_test_ads_sdk':True,'micro_model_progress_and_licenses_verified':True,'legacy_model_absent':True,'original_placement_audio_verified':True,'model_parameters_bytes':4867714,'previous_apk_bytes':47017269,'apk_reduction_percent':(1-apk.stat().st_size/47017269)*100,'native_abis':['arm64-v8a','x86_64'],'apk_entries':names}
 pathlib.Path('outputs/verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='apk_entries'}))


