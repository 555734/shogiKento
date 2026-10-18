package com.presentsoft.shogi3minutes;
/** Exact native engine boundary; outputs SFEN and matches engine output to Java legal moves. */
final class UsiPosition {
 static final String NAMES="PLNSGBRK";
 static String square(int r,int c){return ""+(9-c)+(char)('a'+r);}
 static String move(ShogiGame.Move m){return (m.drop>0?NAMES.charAt(m.drop-1)+"*":square(m.fromRow,m.fromCol))+square(m.row,m.col)+(m.promote?"+":"");}
 static ShogiGame.Move legalMove(ShogiGame game,String usi){for(ShogiGame.Move m:game.legal(game.turn))if(move(m).equals(usi))return m;return null;}
 static String sfen(ShogiGame g){StringBuilder out=new StringBuilder();
  for(int r=0;r<9;r++){if(r>0)out.append('/');int empty=0;for(int c=0;c<9;c++){int p=g.board[r][c];if(p==0){empty++;continue;}
   if(empty>0){out.append(empty);empty=0;}if(Math.abs(p)>8)out.append('+');char ch=NAMES.charAt(ShogiGame.base(p)-1);out.append(p>0?ch:Character.toLowerCase(ch));}if(empty>0)out.append(empty);}
  out.append(g.turn==1?" b ":" w ");boolean has=false;
  for(int side=0;side<2;side++)for(int p:new int[]{7,6,5,4,3,2,1}){int n=g.hands[side][p];if(n>0){has=true;if(n>1)out.append(n);char ch=NAMES.charAt(p-1);out.append(side==0?ch:Character.toLowerCase(ch));}}
  if(!has)out.append('-');return out.append(' ').append(g.movesPlayed+1).toString();
 }
}
