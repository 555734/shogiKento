#!/usr/bin/env python3
"""Controllable USI peer for process-level regressions; never used by the app."""
import os,sys,threading,time
waiting=False
stop_event=threading.Event()
thread=None
def iteration(depth):
 for rank,move in enumerate(['7g7f','2g2f','5g5f'],1):
  print(f'info depth {depth} multipv {rank} score cp {20-rank+depth} nodes {30*depth} pv {move}',flush=True)
def result():
 iteration(1)
 print('bestmove 7g7f',flush=True)
def continuous():
 depth=1
 while not stop_event.is_set():
  iteration(depth);depth+=1;stop_event.wait(.04)
for command in sys.stdin:
 command=command.strip()
 if command=='usi':print('usiok',flush=True)
 elif command=='isready':print('readyok',flush=True)
 elif command=='go infinite':
  stop_event.clear();thread=threading.Thread(target=continuous,daemon=True);thread.start()
 elif command.startswith('go '):
  if '999999' in command:waiting=True
  else:result()
 elif command=='stop':
  if thread is not None:
   stop_event.set();thread.join();thread=None;print('bestmove 7g7f',flush=True)
  elif waiting:result();waiting=False
 elif command=='crash':os._exit(9)
 elif command=='quit':break
