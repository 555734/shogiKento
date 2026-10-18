"""Execute Android bionic + ARMv8/NEON objects used in the shipped engine.
This verifies the instruction path; it is not a physical Android-device test.
"""
import pathlib, subprocess, tempfile, shogi
from usi_client import Engine
root=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as d:
 wrapper=pathlib.Path(d)/'android-arm64-usi'
 wrapper.write_text('#!/bin/sh\nexec qemu-aarch64 -cpu cortex-a53 "'+str(root/'outputs/yaneuraou-arm64-static')+'"\n');wrapper.chmod(0o755)
 e=Engine(wrapper,root/'app/src/main/assets/ai/tsuyou11micro')
 try:
  for sfen in ['lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1','lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1','lnsgkgsnl/1r5b1/pppppp1pp/6p2/7P1/9/PPPPPPP1P/1B5R1/LNSGKGSNL w - 1']:
   e.clear();e.send('setoption name MultiPV value 3');e.send('position sfen '+sfen);e.send('go nodes 3000 movetime 2000');rows={}
   while True:
    line=e.read();a=line.split()
    if line.startswith('info ') and 'pv' in a and 'score' in a:
     rank=int(a[a.index('multipv')+1]) if 'multipv' in a else 1
     rows[rank]=a[a.index('pv')+1]
    if line.startswith('bestmove '):
     board=shogi.Board(sfen);assert shogi.Move.from_usi(a[1]) in board.legal_moves,line
     assert len(rows)==3,rows
     for move in rows.values():assert shogi.Move.from_usi(move) in board.legal_moves,move
     print('PASS Android ARM64 NEON / 3 legal candidates:',sfen,a[1]);break
 finally:e.close()
