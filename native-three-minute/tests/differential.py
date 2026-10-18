import shogi,random,subprocess,sys
random.seed(319)
p=subprocess.Popen(['java','-cp',sys.argv[1],'com.presentsoft.shogi3minutes.RuleProbe'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
checked=0
for game in range(4):
 b=shogi.Board()
 for ply in range(160):
  p.stdin.write(b.sfen()+'\n');p.stdin.flush();actual=set(filter(None,p.stdout.readline().strip().split(',')));expected={m.usi() for m in b.legal_moves}
  if actual!=expected:raise AssertionError((b.sfen(), sorted(actual-expected),sorted(expected-actual)))
  checked+=1
  if not expected:break
  b.push(shogi.Move.from_usi(random.choice(sorted(expected))))
p.stdin.close();p.wait();print('PASS independent python-shogi comparison:',checked,'positions')
