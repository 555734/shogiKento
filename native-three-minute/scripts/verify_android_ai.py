import csv,json,pathlib,statistics,shogi
root=pathlib.Path(__file__).resolve().parents[1];positions=(root/'tests/ai_positions.txt').read_text().splitlines()
rows=list(csv.DictReader((root/'outputs/ai_benchmark.tsv').open(),delimiter='\t'));assert len(rows)==40,len(rows)
for r in rows:
 assert shogi.Move.from_usi(r['move']) in shogi.Board(positions[int(r['position'])]).legal_moves,r
 assert 0<int(r['actual_nodes'])<=int(r['node_limit'])+max(1024,int(r['node_limit'])//100),r
 # Time limits are cooperative: allow scheduler noise, reject a hung engine.
 assert float(r['search_ms'])<2000,r
summary=[]
for model in ['yaneura-tsuyou11micro']:
 for nodes in [500,3000,10000,50000]:
  group=[r for r in rows if r['model']==model and int(r['node_limit'])==nodes]
  summary.append(dict(model=model,node_limit=nodes,positions=len(group),median_ms=statistics.median(float(r['search_ms']) for r in group),max_ms=max(float(r['search_ms']) for r in group)))
(root/'outputs/android_ai_summary.json').write_text(json.dumps({'device':'Android API35 x86_64 emulator; NOT Motorola g55','legal_moves_checked':len(rows),'results':summary},indent=2)+'\n')
print('PASS Android native USI AI: 40 legal moves, cooperative node budgets, duration checks');print(json.dumps(summary,indent=2))
