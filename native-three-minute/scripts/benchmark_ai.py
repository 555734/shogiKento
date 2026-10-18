"""Same positions, models, node budgets; fresh process per profile. Not a phone benchmark."""
import json,pathlib,statistics,subprocess,shogi,platform
root=pathlib.Path(__file__).resolve().parents[1]
positions=(root/'tests/ai_positions.txt').read_text().splitlines();results=[]
for model in ['full','light']:
 for nodes in [500,3000,10000]:
  p=subprocess.run([str(root/'host-bench'/model),str(root/'app/src/main/assets/ai'/model/'eval.bin'),str(nodes),'5000'],input='\n'.join(positions)+'\n',text=True,capture_output=True,check=True)
  rows=[]
  for s,line in zip(positions,p.stdout.splitlines()):
   init,rss,move,count,ms,depth=line.split('|');assert shogi.Move.from_usi(move) in shogi.Board(s).legal_moves,(model,nodes,s,line)
   assert int(count)<=nodes,line
   rows.append(dict(move=move,nodes=int(count),ms=float(ms),depth=int(depth)))
  assert len(rows)==len(positions)
  results.append(dict(model=model,node_limit=nodes,positions=len(rows),init_ms=float(init),peak_rss_kib=int(rss),median_search_ms=statistics.median(x['ms'] for x in rows),max_search_ms=max(x['ms'] for x in rows),rows=rows))
result={'platform':platform.platform()+' '+platform.machine()+'; NOT Motorola g55','time_limit_ms':5000,'results':results}
(root/'outputs').mkdir(exist_ok=True)
(root/'outputs/ai_host_benchmark.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps([{k:v for k,v in r.items() if k!='rows'} for r in results],indent=2))
