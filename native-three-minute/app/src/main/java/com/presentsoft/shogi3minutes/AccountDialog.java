package com.presentsoft.shogi3minutes;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.text.InputType;import android.view.*;import android.widget.*;import java.text.SimpleDateFormat;import java.util.*;
/**
 * The online account: name, player ID, rating with its history, recent rated games, the public ranking and the
 * transfer code for moving the account to another device. Same contents as the iOS account screen.
 */
final class AccountDialog {
 static final int GOLD=0xffffdea0,CREAM=0xfffff1d6,DIM=0xffc9bba5;
 static TextView label(Context c,String text,float size,int color){TextView t=new TextView(c);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
 /** The rating after each rated game, drawn as a line. */
 static final class Graph extends View {final int[] points;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Graph(Context c,int[] p){super(c);points=p;setContentDescription("レートの推移");}
  @Override protected void onDraw(Canvas c){c.drawColor(0x40000000);if(points.length<2)return;int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;for(int p:points){lo=Math.min(lo,p);hi=Math.max(hi,p);}
   float span=Math.max(40,hi-lo),base=lo-(span-(hi-lo))/2f;Path path=new Path();for(int i=0;i<points.length;i++){float x=getWidth()*i/(float)(points.length-1),y=getHeight()*(1-(points[i]-base)/span);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(getResources().getDisplayMetrics().density*2);paint.setColor(GOLD);c.drawPath(path,paint);}}
 static AlertDialog create(MainActivity activity){
  MainActivity.Online online=MainActivity.online;OnlineAccount a=online.account;float density=activity.getResources().getDisplayMetrics().density;int pad=Math.round(18*density);
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad/2,pad,pad/2);
  TextView title=label(activity,"アカウント",22,GOLD);title.setTypeface(null,Typeface.BOLD);title.setPadding(pad,pad,pad,pad/3);
  TextView name=label(activity,a.name,20,CREAM);name.setTag("account_name");name.setTypeface(null,Typeface.BOLD);body.addView(name);
  TextView id=label(activity,"ID "+a.shortId(),11,DIM);id.setTextIsSelectable(true);body.addView(id);
  TextView rating=label(activity,"レート "+a.rating+(a.provisional()?"（暫定 "+a.games+"/"+Rating.PROVISIONAL_GAMES+"局）":""),26,GOLD);rating.setTag("account_rating");rating.setPadding(0,pad/2,0,0);body.addView(rating);
  TextView record=label(activity,a.games+"局 · "+a.wins+"勝 "+a.losses+"敗 "+a.draws+"分",15,CREAM);record.setTag("account_record");body.addView(record);
  if(a.history.size()>=2){int[] pts=new int[a.history.size()+1];pts[0]=a.history.get(0).before;for(int i=0;i<a.history.size();i++)pts[i+1]=a.history.get(i).after;
   Graph g=new Graph(activity,pts);g.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Math.round(80*density)));body.addView(g);}
  Button rename=new Button(activity);rename.setText("名前を変更");rename.setTag("account_rename");body.addView(rename);
  body.addView(label(activity,"ランダム対戦がレート戦です（Elo方式・初期1500・最初の10局は変動大）。ルーム対戦はレートが変わりません。対局中の退出・アプリの終了は負けになります。",11,DIM));
  TextView recent=label(activity,"最近のレート戦",15,GOLD);recent.setPadding(0,pad,0,pad/4);body.addView(recent);
  if(a.history.isEmpty())body.addView(label(activity,"まだレート戦の記録はありません",13,DIM));
  SimpleDateFormat date=new SimpleDateFormat("M/d HH:mm",Locale.JAPAN);
  for(int i=a.history.size()-1;i>=Math.max(0,a.history.size()-30);i--){OnlineAccount.Game g=a.history.get(i);int d=g.after-g.before;
   TextView row=label(activity,(g.result>0?"勝":g.result<0?"負":"分")+"  "+g.opponentName+"（"+g.opponentRating+"） "+g.after+"（"+(d>=0?"+":"")+d+"）\n      "+g.reason+" · "+date.format(new Date(g.time*1000)),13,g.result>0?0xff9be59b:g.result<0?0xffffb9b3:CREAM);row.setPadding(0,pad/6,0,pad/6);body.addView(row);}
  TextView rankTitle=label(activity,"ランキング",15,GOLD);rankTitle.setPadding(0,pad,0,pad/4);body.addView(rankTitle);
  Button rankButton=new Button(activity);rankButton.setText("ランキングを読み込む");rankButton.setTag("account_ranking");body.addView(rankButton);
  TextView rankStatus=label(activity,"公開リレーに置かれたプロフィールを集めた一覧です。値は各プレイヤーの端末が計算したもので、運営者による確認はありません。",11,DIM);rankStatus.setTag("ranking_status");body.addView(rankStatus);
  LinearLayout rankList=new LinearLayout(activity);rankList.setOrientation(LinearLayout.VERTICAL);body.addView(rankList);
  TextView transfer=label(activity,"引き継ぎ",15,GOLD);transfer.setPadding(0,pad,0,pad/4);body.addView(transfer);
  Switch publish=new Switch(activity);publish.setText("プロフィールを公開（ランキング・引き継ぎ用）");publish.setTextColor(CREAM);publish.setTag("account_publish");publish.setChecked(a.publish);body.addView(publish);
  Button show=new Button(activity);show.setText("引き継ぎコードを表示");show.setTag("account_show_code");body.addView(show);
  Button restore=new Button(activity);restore.setText("引き継ぎコードで復元");restore.setTag("account_restore");restore.setEnabled(online.game==null);body.addView(restore);
  body.addView(label(activity,"アカウントは登録不要で、この端末の鍵がIDになります。機種変更の前に引き継ぎコードを控えてください（端末のバックアップには含まれません）。コードを知っている人はアカウントを使えるため、他人に見せないでください。",11,DIM));
  ScrollView scroll=new ScrollView(activity);scroll.addView(body);
  AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setPositiveButton("閉じる",null).create();
  dialog.setOnShowListener(d->{GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff471519,0xff20120f});background.setCornerRadius(pad);background.setStroke(1,0xffb58a47);dialog.getWindow().setBackgroundDrawable(background);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD);});
  rename.setOnClickListener(v->{EditText field=new EditText(activity);field.setTag("account_name_field");field.setSingleLine(true);field.setText(online.account.name);field.setSelectAllOnFocus(true);
   new AlertDialog.Builder(activity).setTitle("名前を変更").setMessage("対局相手とランキングに表示されます（16文字まで）").setView(field).setNegativeButton("キャンセル",null).setPositiveButton("変更",(x,k)->{OnlineAccount acc=online.account;
    if(acc.rename(field.getText().toString())){acc.updated=System.currentTimeMillis()/1000;online.accounts.save(acc);MainActivity.publishProfile();name.setText(acc.name);activity.view.invalidate();}else Toast.makeText(activity,"名前を入力してください",Toast.LENGTH_SHORT).show();}).show();});
  publish.setOnCheckedChangeListener((b,checked)->{online.account.publish=checked;online.accounts.save(online.account);if(checked)MainActivity.publishProfile();});
  show.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("引き継ぎコード").setMessage(online.account.transferCode()+"\n\n新しい端末の「引き継ぎコードで復元」に入力します。他人には絶対に教えないでください。")
   .setPositiveButton("コピー",(x,k)->((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("引き継ぎコード",online.account.transferCode()))).setNegativeButton("閉じる",null).show());
  restore.setOnClickListener(v->{EditText field=new EditText(activity);field.setTag("account_code_field");field.setSingleLine(true);field.setHint("nsec1…");field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
   new AlertDialog.Builder(activity).setTitle("引き継ぎコードで復元").setMessage("別の端末で表示した引き継ぎコードを入力してください").setView(field).setNegativeButton("キャンセル",null).setPositiveButton("次へ",(x,k)->{byte[] secret=Nip19.decode("nsec",field.getText().toString());
    if(secret==null||online.game!=null){Toast.makeText(activity,"引き継ぎコードが正しくありません",Toast.LENGTH_LONG).show();return;}
    new AlertDialog.Builder(activity).setTitle("この端末のアカウントを置き換えますか？").setMessage("現在のアカウント（"+online.account.name+"・レート"+online.account.rating+"）は、引き継ぎコードを控えていない場合は元に戻せません。").setNegativeButton("キャンセル",null)
     .setPositiveButton("置き換える",(y,j)->{OnlineAccount moved=online.accounts.replace(secret);online.account=moved;dialog.dismiss();Toast.makeText(activity,"プロフィールを探しています…",Toast.LENGTH_SHORT).show();List<String> relays=Netplay.relays();
      new Thread(()->{Profiles.Profile p=null;try{p=Profiles.fetch(relays,javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),Profiles.authorFilter(moved.key.publicHex),8000).get(moved.key.publicHex);}catch(InterruptedException ignored){}
       final Profiles.Profile found=p;activity.runOnUiThread(()->{if(online.account!=moved)return;moved.adopt(found);online.accounts.save(moved);activity.view.invalidate();Toast.makeText(activity,found==null?"公開プロフィールが見つかりません。レートは1500から始まります":"レート"+moved.rating+"を復元しました",Toast.LENGTH_LONG).show();});},"profile-restore").start();}).show();}).show();});
  rankButton.setOnClickListener(v->{rankStatus.setText("ランキングを読み込んでいます…");rankButton.setEnabled(false);List<String> relays=Netplay.relays();String me=online.account.key.publicHex;
   new Thread(()->{List<Profiles.Profile> top=new ArrayList<>();try{top=Profiles.ranking(Profiles.fetch(relays,javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),Profiles.rankingFilter(300),8000),1);}catch(InterruptedException ignored){}
    final List<Profiles.Profile> list=top;activity.runOnUiThread(()->{rankButton.setEnabled(true);rankList.removeAllViews();rankStatus.setText(list.isEmpty()?"公開プロフィールが見つかりませんでした":"公開プロフィール"+list.size()+"件（各端末が計算した値）");
     for(int i=0;i<list.size()&&i<100;i++){Profiles.Profile p=list.get(i);TextView row=label(activity,(i+1)+".  "+MainActivity.shortName(p.name,12)+"   "+p.rating+"   "+p.games+"局",14,p.pubkey.equals(me)?GOLD:CREAM);if(p.pubkey.equals(me))row.setTypeface(null,Typeface.BOLD);rankList.addView(row);}});},"profile-ranking").start();});
  dialog.setOnDismissListener(d->activity.view.invalidate());
  return dialog;
 }
}
