from pathlib import Path
import json,hashlib
import zipfile
out=Path('outputs');
with zipfile.ZipFile(out/'verification_logs.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(out.iterdir()):
  if p.is_file() and p.suffix in ('.txt','.json','.tsv'):z.write(p,p.name)
parts=out/'download_parts';parts.mkdir(exist_ok=True);items=[]
for p in [out/'Shogi_3min.apk',out/'Shogi_3min_complete_source.zip',out/'verification_logs.zip',*sorted((out/'screenshots').glob('*.png'))]:
 if not p.exists():continue
 data=p.read_bytes();names=[]
 for i,offset in enumerate(range(0,len(data),600000)):
  name=p.name+'.part'+str(i).zfill(3);(parts/name).write_bytes(data[offset:offset+600000]);names.append(name)
 items.append(dict(file=p.name,size=len(data),sha256=hashlib.sha256(data).hexdigest(),parts=names))
(parts/'manifest.json').write_text(json.dumps(items,indent=2))
