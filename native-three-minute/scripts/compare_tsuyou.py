"""Host-only fixed-position/node comparison, not a strength or phone benchmark."""
from pathlib import Path
import argparse,subprocess,time,queue,threading,csv,json,statistics
import shogi
p=argparse.ArgumentParser();p.add_argument('--micro',required=True);p.add_argument('--mini',required=True);p.add_argument('--micro-eval',required=True);p.add_argument('--mini-eval',required=True);p.add_argument('--output',required=True);a=p.parse_args()
positions=(Path(__file__).resolve().parents[1]/'tests/ai_positions.txt').read_text().splitlines();rows=[];startup={}
for model,exe,evaldir in [('micro',a.micro,a.micro_eval),('mini',a.mini,a.mini_eval)]:
 proc=subprocess.Popen([exe],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1);q=queue.Queue()
 def read(stream=proc.stdout,out=q):
  for l in stream:out.put(l.strip())
  out.put('__EOF__')
 threading.Thread(target=read,daemon=True).start()
 def send(s):proc.stdin.write(s+'\n');proc.stdin.flush()
 def until(marker):
  lines=[]
  while True:
   l=q.get(timeout=20);lines.append(l)
   if l=='__EOF__':raise RuntimeError(lines[-15:])
   if l==marker:return lines
 send('usi');usi=until('usiok');send('setoption name Threads value 1');send('setoption name USI_Hash value 16');send('setoption name USI_OwnBook value false');send('setoption name BookFile value no_book');send('setoption name EvalDir value '+str(Path(evaldir).resolve()));send('setoption name FV_SCALE value 24');send('setoption name PvInterval value 0');send('isready');init=until('readyok');assert any('progress.bin' in x for x in init);startup[model]=usi+init
 for nodes in [3000,10000,50000]:
  for i,sfen in enumerate(positions):
   send('position sfen '+sfen);start=time.perf_counter();send('go nodes '+str(nodes)+' movetime 750');visited=0;depth=0
   while True:
    line=q.get(timeout=5)
    if line.startswith('info '):
     parts=line.split()
     if 'nodes' in parts:visited=int(parts[parts.index('nodes')+1])
     if 'depth' in parts:depth=int(parts[parts.index('depth')+1])
    if line.startswith('bestmove '):
     elapsed=(time.perf_counter()-start)*1000;move=line.split()[1];assert shogi.Move.from_usi(move) in shogi.Board(sfen).legal_moves;rows.append(dict(model=model,node_limit=nodes,position=i,move=move,actual_nodes=visited,search_ms=elapsed,depth=depth));break
 send('quit');proc.wait(timeout=5)
out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
with (out/'tsuyou_host_comparison.tsv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter='\t');w.writeheader();w.writerows(rows)
summary=[dict(model=m,node_limit=n,positions=len(g),median_ms=statistics.median(r['search_ms'] for r in g),max_ms=max(r['search_ms'] for r in g)) for m in ['micro','mini'] for n in [3000,10000,50000] if (g:=[r for r in rows if r['model']==m and r['node_limit']==n])]
(out/'tsuyou_host_comparison.json').write_text(json.dumps(dict(device='Host x86_64 SSE2; not Motorola g55; not a strength test',legal_moves=len(rows),results=summary),indent=2));(out/'tsuyou_model_startup.json').write_text(json.dumps(startup,indent=2));print(json.dumps(summary,indent=2))
