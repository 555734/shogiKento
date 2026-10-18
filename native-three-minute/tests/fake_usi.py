#!/usr/bin/env python3
"""Controllable USI peer for process-level regressions; never used by the app."""
import os,sys
waiting=False
def result():
 for rank,move in enumerate(['7g7f','2g2f','5g5f'],1):
  print(f'info depth 1 multipv {rank} score cp {20-rank} nodes 30 pv {move}',flush=True)
 print('bestmove 7g7f',flush=True)
for command in sys.stdin:
 command=command.strip()
 if command=='usi':print('usiok',flush=True)
 elif command=='isready':print('readyok',flush=True)
 elif command.startswith('go '):
  if '999999' in command:waiting=True
  else:result()
 elif command=='stop' and waiting:result();waiting=False
 elif command=='crash':os._exit(9)
 elif command=='quit':break
