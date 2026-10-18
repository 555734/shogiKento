import subprocess,threading,queue,time,pathlib,tempfile
class Engine:
 def __init__(self,executable,model):
  start=time.perf_counter();self.mem=tempfile.NamedTemporaryFile(prefix='shogi-memory-',delete=False).name if pathlib.Path('/usr/bin/time').exists() else None
  command=['/usr/bin/time','-f','%M','-o',self.mem,str(executable)] if self.mem else [str(executable)]
  self.p=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1);self.q=queue.Queue()
  def read():
   for line in self.p.stdout:self.q.put(line.strip())
   self.q.put('__EOF__')
  threading.Thread(target=read,daemon=True).start();self.send('usi');self.until('usiok')
  for k,v in dict(Threads=1,USI_Hash=16,USI_Ponder='false',EvalDir=str(model),FV_SCALE=24,BookFile='no_book',USI_OwnBook='false',EnteringKingRule='NoEnteringKing',PvInterval=0).items():self.send('setoption name '+k+' value '+str(v))
  self.clear();self.init_ms=(time.perf_counter()-start)*1000
 def send(self,s):self.p.stdin.write(s+'\n');self.p.stdin.flush()
 def read(self):
  s=self.q.get(timeout=10)
  if s=='__EOF__':raise RuntimeError('engine exited')
  return s
 def until(self,marker):
  while self.read()!=marker:pass
 def clear(self):self.send('usinewgame');self.send('isready');self.until('readyok')
 def search(self,sfen,nodes,millis=750):
  start=time.perf_counter();self.send('position sfen '+sfen);self.send('go nodes '+str(nodes)+' movetime '+str(millis));count=depth=0
  while True:
   s=self.read();a=s.split()
   if s.startswith('info '):
    if 'nodes' in a:count=int(a[a.index('nodes')+1])
    if 'depth' in a:depth=int(a[a.index('depth')+1])
   if s.startswith('bestmove '):return dict(move=a[1],nodes=count,ms=(time.perf_counter()-start)*1000,depth=depth)
 def rss(self):return int(pathlib.Path(self.mem).read_text().strip()) if self.mem else None
 def close(self):self.send('quit');self.p.wait(timeout=3)
