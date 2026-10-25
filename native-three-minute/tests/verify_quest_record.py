"""Compare every imported board/hand with an independent KIF parser and rules engine."""
import pathlib, sys, shogi, shogi.KIF
fixture=pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
actual=pathlib.Path(sys.argv[2]).read_text(encoding='utf-8').splitlines()
record=shogi.KIF.Parser.parse_str(fixture)[0]
board=shogi.Board(record['sfen'])
expected=[]
for index in range(len(record['moves'])+1):
    fields=board.sfen().split()
    expected.append(fields[0]+' '+fields[2])
    if index<len(record['moves']):
        move=shogi.Move.from_usi(record['moves'][index])
        assert move in board.legal_moves, (index+1,move.usi())
        board.push(move)
assert actual==expected, next((i,a,e) for i,(a,e) in enumerate(zip(actual,expected)) if a!=e)
assert len(actual)==80 and board.is_checkmate()
print('PASS independent python-shogi: all 80 board/hand snapshots, 79 legal moves, final checkmate')
