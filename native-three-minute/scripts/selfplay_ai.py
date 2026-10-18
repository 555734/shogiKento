import subprocess,pathlib,shogi,json
root=pathlib.Path(__file__).resolve().parents[1];results=[]
for model in ['full','light']:
 for budget in [500,3000]:
  p=subprocess.Popen([str(root/'host-bench'/model),str(root/'app/src/main/assets/ai'/model/'eval.bin'),str(budget),'250'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  b=shogi.Board();clocks=[180000,180000];plies=0;reason=''
  while True:
   if b.is_game_over():reason='game_over';break
   side=b.turn;p.stdin.write(b.sfen()+'\n');p.stdin.flush();line=p.stdout.readline().strip();fields=line.split('|');assert len(fields)==6,line
   m=shogi.Move.from_usi(fields[2]);assert m in b.legal_moves,(b.sfen(),line);assert int(fields[3])<=budget
   b.push(m);plies+=1
   # Synthetic 2s/turn exercises a complete three-minute clock game quickly.
   clocks[side]-=2000
   if clocks[side]<=0:reason='synthetic_timeout';break
  p.stdin.close();p.wait(timeout=5);assert p.returncode==0
  results.append(dict(model=model,node_limit=budget,plies=plies,end=reason))
print('PASS native AI complete selfplay: '+json.dumps(results));(root/'outputs').mkdir(exist_ok=True);(root/'outputs/ai_selfplay.json').write_text(json.dumps(results,indent=2)+'\n')
