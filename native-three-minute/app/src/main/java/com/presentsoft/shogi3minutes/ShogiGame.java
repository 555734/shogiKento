package com.presentsoft.shogi3minutes;
import java.util.*;

public final class ShogiGame implements java.io.Serializable {
  private static final long serialVersionUID=1L;
  public int movesPlayed=0;
  private final List<String> positions=new ArrayList<>();
  private final List<Integer> checkingSides=new ArrayList<>();
  public static final int FU=1,KYO=2,KEI=3,GIN=4,KIN=5,KAKU=6,HISHA=7,OU=8;
  public final int[][] board=new int[9][9];
  public final int[][] hands=new int[2][9];
  public final long[] remaining={180000,180000};
  public int turn=1, winner=0;
  public String result="";
  public static final class Move implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    public final int fromRow,fromCol,row,col,drop;
    public final boolean promote;
    public Move(int sr,int sc,int r,int c,boolean pro,int d){fromRow=sr;fromCol=sc;row=r;col=c;promote=pro;drop=d;}
  }
  public ShogiGame(){restart();}
  public void restart(){
    for(int[] line:board)Arrays.fill(line,0);
    for(int[] hand:hands)Arrays.fill(hand,0);
    int[] back={KYO,KEI,GIN,KIN,OU,KIN,GIN,KEI,KYO};
    for(int c=0;c<9;c++){board[0][c]=-back[c];board[2][c]=-FU;board[6][c]=FU;board[8][c]=back[c];}
    board[1][1]=-HISHA;board[1][7]=-KAKU;board[7][1]=KAKU;board[7][7]=HISHA;
    turn=1;winner=0;result="";remaining[0]=remaining[1]=180000;
    movesPlayed=0;positions.clear();checkingSides.clear();positions.add(positionKey());
  }
  public static int side(int p){return Integer.compare(p,0);}
  public static int base(int n){int a=Math.abs(n);return a>8?a-8:a;}
  static boolean in(int r,int c){return r>=0&&r<9&&c>=0&&c<9;}
  static int slot(int s){return s==1?0:1;}
  static boolean promotionZone(int side,int r){return side==1?r<=2:r>=6;}
  static boolean forced(int s,int t,int r){return ((t==FU||t==KYO)&&(s==1?r==0:r==8))||(t==KEI&&(s==1?r<=1:r>=7));}
  private void addDirection(List<Move> moves,int r,int c,int dr,int dc,boolean slide){
    int src=board[r][c],s=side(src),p=Math.abs(src),t=base(src),nr=r+dr,nc=c+dc;
    while(in(nr,nc)){
      int dest=board[nr][nc];
      if(side(dest)==s||Math.abs(dest)==OU)break;
      boolean can=(p<=8&&t!=KIN&&t!=OU&&(promotionZone(s,r)||promotionZone(s,nr)));
      if(p>8||!forced(s,t,nr))moves.add(new Move(r,c,nr,nc,false,0));
      if(can)moves.add(new Move(r,c,nr,nc,true,0));
      if(!slide||dest!=0)break;
      nr+=dr;nc+=dc;
    }
  }
  private List<Move> pseudo(int s){
    List<Move> out=new ArrayList<>();int f=s==1?-1:1;
    for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(side(board[r][c])==s){
      int raw=Math.abs(board[r][c]),t=base(raw);
      if(t==KIN||(raw>=9&&raw<=12)){
        addDirection(out,r,c,f,0,false);addDirection(out,r,c,f,-1,false);
        addDirection(out,r,c,f,1,false);addDirection(out,r,c,0,-1,false);
        addDirection(out,r,c,0,1,false);addDirection(out,r,c,-f,0,false);
      }else if(t==FU)addDirection(out,r,c,f,0,false);
      else if(t==KYO)addDirection(out,r,c,f,0,true);
      else if(t==KEI){addDirection(out,r,c,2*f,-1,false);addDirection(out,r,c,2*f,1,false);}
      else if(t==GIN){for(int dc=-1;dc<=1;dc++)addDirection(out,r,c,f,dc,false);
        addDirection(out,r,c,-f,-1,false);addDirection(out,r,c,-f,1,false);
      }else if(t==OU){for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++)
        if(dr!=0||dc!=0)addDirection(out,r,c,dr,dc,false);
      }else if(t==KAKU){for(int dr:new int[]{-1,1})for(int dc:new int[]{-1,1})addDirection(out,r,c,dr,dc,true);
        if(raw==14){for(int q:new int[]{-1,1}){addDirection(out,r,c,q,0,false);addDirection(out,r,c,0,q,false);}}
      }else if(t==HISHA){for(int q:new int[]{-1,1}){addDirection(out,r,c,q,0,true);addDirection(out,r,c,0,q,true);}
        if(raw==15)for(int dr:new int[]{-1,1})for(int dc:new int[]{-1,1})addDirection(out,r,c,dr,dc,false);
      }
    }
    for(int p=1;p<=7;p++)if(hands[slot(s)][p]>0){
      for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(board[r][c]==0){
        if(forced(s,p,r))continue;
        if(p==FU){boolean doubled=false;for(int rr=0;rr<9;rr++)if(board[rr][c]==s*FU){doubled=true;break;}
          if(doubled)continue;}
        out.add(new Move(-1,-1,r,c,false,p));
      }
    }
    return out;
  }
  private boolean clearLine(int r,int c,int er,int ec){
    int dr=Integer.compare(er,r),dc=Integer.compare(ec,c);r+=dr;c+=dc;
    while(r!=er||c!=ec){if(board[r][c]!=0)return false;r+=dr;c+=dc;}return true;
  }
  private boolean attacks(int r,int c,int er,int ec){
    int p=board[r][c],s=side(p),t=base(p),v=Math.abs(p);
    int dr=er-r,dc=ec-c,ar=Math.abs(dr),ac=Math.abs(dc),f=s==1?-1:1;
    if(dr==0&&dc==0)return false;
    if(t==OU)return Math.max(ar,ac)==1;
    if(t==KIN||(v>=9&&v<=12))return (dr==f&&ac<=1)||(dr==-f&&dc==0)||(dr==0&&ac==1);
    if(t==FU)return dr==f&&dc==0;
    if(t==KYO)return dc==0&&dr*f>0&&clearLine(r,c,er,ec);
    if(t==KEI)return dr==2*f&&ac==1;
    if(t==GIN)return (dr==f&&ac<=1)||(dr==-f&&ac==1);
    if(t==KAKU)return (ar==ac&&clearLine(r,c,er,ec))||(v==14&&ar+ac==1);
    if(t==HISHA)return ((dr==0||dc==0)&&clearLine(r,c,er,ec))||(v==15&&ar==1&&ac==1);
    return false;
  }
  public boolean checked(int s){
    int kr=-1,kc=-1;
    for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(board[r][c]==s*OU){kr=r;kc=c;}
    if(kr==-1)return true;
    for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(side(board[r][c])==-s&&attacks(r,c,kr,kc))return true;
    return false;
  }
  private void simulate(Move m,int s){
    int v=m.drop>0?s*m.drop:board[m.fromRow][m.fromCol];
    if(m.fromRow>=0)board[m.fromRow][m.fromCol]=0;else hands[slot(s)][m.drop]--;
    int captured=board[m.row][m.col];
    if(captured!=0)hands[slot(s)][base(captured)]++;
    board[m.row][m.col]=m.promote?s*(Math.abs(v)+8):v;
  }
  private void undo(Move m,int s,int src,int dest){
    board[m.row][m.col]=dest;
    if(dest!=0)hands[slot(s)][base(dest)]--;
    if(m.drop>0)hands[slot(s)][m.drop]++;
    else board[m.fromRow][m.fromCol]=src;
  }
  private boolean illegalPawnMate(Move m,int s){
    int krow=-1,kcol=-1;
    for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(board[r][c]==-s*OU){krow=r;kcol=c;}
    if(krow<0||m.col!=kcol||m.row+(s==1?-1:1)!=krow)return false;
    return checked(-s)&&legal(-s,false).isEmpty();
  }
  private List<Move> legal(int s,boolean checkPawnMate){
    ArrayList<Move> ok=new ArrayList<>();
    for(Move m:pseudo(s)){
      int src=m.drop>0?0:board[m.fromRow][m.fromCol],dest=board[m.row][m.col];
      simulate(m,s);
      boolean valid=!checked(s);
      if(valid&&checkPawnMate&&m.drop==FU&&illegalPawnMate(m,s))valid=false;
      undo(m,s,src,dest);
      if(valid)ok.add(m);
    }
    return ok;
  }
  public List<Move> legal(int s){return legal(s,true);}
  public static boolean same(Move a,Move b){return a.fromRow==b.fromRow&&a.fromCol==b.fromCol&&a.row==b.row&&a.col==b.col&&a.drop==b.drop&&a.promote==b.promote;}
  public void apply(Move m,int s){
    if(winner!=0||turn!=s)throw new IllegalStateException("Invalid turn");
    boolean found=false;for(Move candidate:legal(s))if(same(m,candidate)){found=true;break;}
    if(!found)throw new IllegalArgumentException("Illegal move");
    simulate(m,s);turn=-s;movesPlayed++;
    checkingSides.add(checked(turn)?s:0);
    String key=positionKey();positions.add(key);ArrayList<Integer> occurrences=new ArrayList<>();
    for(int i=0;i<positions.size();i++)if(positions.get(i).equals(key))occurrences.add(i);
    if(occurrences.size()>=4){
      int start=occurrences.get(occurrences.size()-4);
      for(int side:new int[]{1,-1}){boolean continuous=true,any=false;
        for(int i=start;i<checkingSides.size();i++){int mover=((movesPlayed-i)%2==0)?turn:-turn;
          if(mover==side){any=true;if(checkingSides.get(i)!=side)continuous=false;}}
        if(continuous&&any){winner=-side;result="連続王手の千日手";return;}
      }
      winner=2;result="千日手・引き分け";return;
    }
    if(legal(turn).isEmpty()){winner=s;result=checked(turn)?"詰み":"合法手なし";}
  }
  private String positionKey(){return Arrays.deepToString(board)+Arrays.deepToString(hands)+turn;}
  public void resign(int s){if(winner==0){winner=-s;result="投了";}}
  public Move cpuChoice(){
    List<Move> list=legal(turn);if(list.isEmpty())return null;
    Random rand=new Random();int[] values={0,100,270,310,400,500,800,950,10000};
    Move best=null;double highest=-1e9;
    for(Move m:list){
      int dest=board[m.row][m.col];
      double score=rand.nextDouble()*95+(dest==0?0:2.6*values[base(dest)])
        +(m.promote?180:0)+(4-Math.abs(m.col-4))*8;
      if(score>highest){highest=score;best=m;}
    }
    return best;
  }
  public void tick(long elapsed){
    if(winner!=0)return;
    int t=slot(turn);remaining[t]=Math.max(0,remaining[t]-Math.max(0,elapsed));
    if(remaining[t]==0){winner=-turn;result="時間切れ";}
  }
}
