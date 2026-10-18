package com.presentsoft.shogi3minutes;
import java.util.*;
public final class RuleProbe {
 static final String names="PLNSGBRK";static String cell(int r,int c){return ""+(9-c)+(char)('a'+r);}
 public static void main(String[] a){Scanner scanner=new Scanner(System.in);while(scanner.hasNextLine()){
  String[] f=scanner.nextLine().split(" ");ShogiGame g=new ShogiGame();for(int[] line:g.board)Arrays.fill(line,0);
  String[] ranks=f[0].split("/");for(int r=0;r<9;r++){int c=0;boolean pro=false;for(char ch:ranks[r].toCharArray()){
   if(ch=='+'){pro=true;continue;}if(Character.isDigit(ch)){c+=ch-'0';continue;}int n=names.indexOf(Character.toUpperCase(ch))+1;g.board[r][c++]=(Character.isUpperCase(ch)?1:-1)*(n+(pro?8:0));pro=false;}}
  g.turn=f[1].equals("b")?1:-1;int count=0;if(!f[2].equals("-"))for(char ch:f[2].toCharArray()){if(Character.isDigit(ch)){count=count*10+ch-'0';continue;}g.hands[Character.isUpperCase(ch)?0:1][names.indexOf(Character.toUpperCase(ch))+1]=count==0?1:count;count=0;}
  TreeSet<String> moves=new TreeSet<>();for(ShogiGame.Move m:g.legal(g.turn))moves.add((m.drop>0?names.charAt(m.drop-1)+"*":cell(m.fromRow,m.fromCol))+cell(m.row,m.col)+(m.promote?"+":""));System.out.println(String.join(",",moves));System.out.flush();
 }}
}
