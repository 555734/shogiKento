package com.presentsoft.shogi3minutes;
/** Exact native engine boundary; outputs SFEN and matches engine output to Java legal moves. */
final class UsiPosition {
 static final String NAMES="PLNSGBRK";
 static String square(int r,int c){return ""+(9-c)+(char)('a'+r);}
 static String move(ShogiGame.Move m){return (m.drop>0?NAMES.charAt(m.drop-1)+"*":square(m.fromRow,m.fromCol))+square(m.row,m.col)+(m.promote?"+":"");}
 static ShogiGame.Move legalMove(ShogiGame game,String usi){for(ShogiGame.Move m:game.legal(game.turn))if(move(m).equals(usi))return m;return null;}
 /** Decode only board and hands, including freely edited positions with kings in hand. */
 static ShogiGame studyPosition(String key){
  String[] fields=key.split(" ",-1);if(fields.length!=2)throw new IllegalArgumentException("position fields");
  ShogiGame g=new ShogiGame();for(int[] row:g.board)java.util.Arrays.fill(row,0);String[] ranks=fields[0].split("/",-1);if(ranks.length!=9)throw new IllegalArgumentException("rank count");
  for(int r=0;r<9;r++){int c=0;boolean promote=false;for(char ch:ranks[r].toCharArray()){
   if(ch=='+'){if(promote)throw new IllegalArgumentException("promotion");promote=true;continue;}
   if(ch>='1'&&ch<='9'){if(promote)throw new IllegalArgumentException("empty promotion");c+=ch-'0';}
   else{int type=NAMES.indexOf(Character.toUpperCase(ch))+1;if(type==0||c>=9||promote&&(type==5||type==8))throw new IllegalArgumentException("piece");g.board[r][c++]=(Character.isUpperCase(ch)?1:-1)*(type+(promote?8:0));promote=false;}
   if(c>9)throw new IllegalArgumentException("rank width");
  }if(c!=9||promote)throw new IllegalArgumentException("incomplete rank");}
  if(!fields[1].equals("-")){int count=0;for(char ch:fields[1].toCharArray()){
   if(ch>='0'&&ch<='9'){count=count*10+ch-'0';if(count>99)throw new IllegalArgumentException("hand count");continue;}
   int type=NAMES.indexOf(Character.toUpperCase(ch))+1,side=Character.isUpperCase(ch)?0:1;if(type==0||g.hands[side][type]!=0)throw new IllegalArgumentException("hand piece");g.hands[side][type]=count==0?1:count;count=0;
  }if(count!=0)throw new IllegalArgumentException("incomplete hand");}
  if(!EvaluationHistory.key(g).equals(key))throw new IllegalArgumentException("noncanonical position");return g;
 }
 static String sfen(ShogiGame g){StringBuilder out=new StringBuilder();
  for(int r=0;r<9;r++){if(r>0)out.append('/');int empty=0;for(int c=0;c<9;c++){int p=g.board[r][c];if(p==0){empty++;continue;}
   if(empty>0){out.append(empty);empty=0;}if(Math.abs(p)>8)out.append('+');char ch=NAMES.charAt(ShogiGame.base(p)-1);out.append(p>0?ch:Character.toLowerCase(ch));}if(empty>0)out.append(empty);}
  out.append(g.turn==1?" b ":" w ");boolean has=false;
  for(int side=0;side<2;side++)for(int p:new int[]{7,6,5,4,3,2,1}){int n=g.hands[side][p];if(n>0){has=true;if(n>1)out.append(n);char ch=NAMES.charAt(p-1);out.append(side==0?ch:Character.toLowerCase(ch));}}
  if(!has)out.append('-');return out.append(' ').append(g.movesPlayed+1).toString();
 }
}
