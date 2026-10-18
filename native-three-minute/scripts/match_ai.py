"""Paired opening smoke comparison; no Elo estimate."""
import json,pathlib,subprocess,random,zipfile,shogi
from usi_client import Engine
root=pathlib.Path(__file__).resolve().parents[1];model=root/'host-bench/sunfish-eval.bin'
if not model.exists():
 with zipfile.ZipFile(root/'.ai-cache/full-release.zip') as z:model.write_bytes(z.read(next(n for n in z.namelist() if n.endswith('/eval.bin'))))
results=[]
for opening in range(6):
 b=shogi.Board();rng=random.Random(194+opening)
 for i in range(8):b.push(rng.choice(list(b.legal_moves)))
 initial=b.sfen()
 for color in [0,1]:
  b=shogi.Board(initial);e=Engine(root/'host-bench/yaneuraou',root/'app/src/main/assets/ai/suisho5');old=subprocess.Popen([str(root/'host-bench/full'),str(model),'10000','750'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  reason='ply_limit';winner=None;moves=[]
  for ply in range(256):
   if b.is_game_over():reason='game_over';winner=1-b.turn;break
   if b.turn==color:usi=e.search(b.sfen(),10000)['move']
   else:old.stdin.write(b.sfen()+'\n');old.stdin.flush();usi=old.stdout.readline().split('|')[2]
   m=shogi.Move.from_usi(usi);assert m in b.legal_moves,(b.sfen(),usi);moves.append(usi);b.push(m)
   if b.is_fourfold_repetition():reason='repetition';break
  old.stdin.close();old.wait(timeout=3);e.close();r=dict(opening=opening,initial_sfen=initial,modern_color=color,plies=len(moves),end=reason,winner='draw' if winner is None else 'YaneuraOu' if winner==color else 'Sunfish4',moves=moves);results.append(r);print({k:v for k,v in r.items() if k not in ['moves','initial_sfen']},flush=True)
summary=dict(games=len(results),same_node_budget=10000,limit_ms=750,modern_wins=sum(r['winner']=='YaneuraOu' for r in results),old_wins=sum(r['winner']=='Sunfish4' for r in results),draws=sum(r['winner']=='draw' for r in results),note='12 paired games with six seeded 8-ply openings; small smoke sample, no Elo claim.',results=results)
(root/'outputs/ai_match.json').write_text(json.dumps(summary,indent=2)+'\n');print('PASS paired AI match', {k:v for k,v in summary.items() if k!='results'})
