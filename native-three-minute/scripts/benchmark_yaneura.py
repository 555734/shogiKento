import json,pathlib,statistics,shogi,platform
from usi_client import Engine
root=pathlib.Path(__file__).resolve().parents[1];positions=(root/'tests/ai_positions.txt').read_text().splitlines();results=[]
for budget in [500,3000,10000,50000]:
 e=Engine(root/'host-bench/yaneuraou',root/'app/src/main/assets/ai/suisho5');rows=[]
 for sfen in positions:
  e.clear();r=e.search(sfen,budget);assert shogi.Move.from_usi(r['move']) in shogi.Board(sfen).legal_moves,(sfen,r)
  assert 0<r['nodes']<=budget+max(1024,budget//100),r;assert r['ms']<2000,r;rows.append(r)
 e.close();results.append(dict(model='YaneuraOu V9.00 + Suisho5 HalfKP256',node_limit=budget,positions=len(rows),init_ms=e.init_ms,peak_rss_kib=e.rss(),median_search_ms=statistics.median(x['ms'] for x in rows),max_search_ms=max(x['ms'] for x in rows),rows=rows))
result=dict(platform=platform.platform()+' '+platform.machine()+'; NOT Motorola g55',time_limit_ms=750,node_limit_note='Upstream cooperative node/time checks; measured actual nodes included',results=results)
(root/'outputs/yaneura_host_benchmark.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps([{k:v for k,v in r.items() if k!='rows'} for r in results],indent=2))
