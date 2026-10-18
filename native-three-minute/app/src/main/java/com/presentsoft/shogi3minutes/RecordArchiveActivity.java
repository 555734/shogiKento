package com.presentsoft.shogi3minutes;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import java.text.DateFormat;

/** A separate page pauses the live match while browsing its saved games. */
public final class RecordArchiveActivity extends Activity {
 ListView list;List<SavedRecord> records=Collections.emptyList();TextView status;
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xff400b0c);getWindow().setNavigationBarColor(0xff400b0c);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(8));root.setBackgroundColor(0xff400e12);
  Button back=new Button(this);back.setText("ホームに戻る");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView title=text("過去の棋譜",26,0xffffe3ae);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(18),0,dp(8));root.addView(title);
  status=text("",13,0xffefddd0);status.setPadding(0,0,0,dp(14));root.addView(status);
  list=new ListView(this);list.setDividerHeight(dp(10));list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  TextView note=text("端末内に保存されています。棋譜を選ぶと検討で開きます。",12,0xffe0b8a4);note.setPadding(0,dp(12),0,dp(10));root.addView(note);setContentView(root);
  load();list.setOnItemClickListener((parent,view,index,id)->{setResult(RESULT_OK,new Intent().putExtra("record_id",records.get(index).id));finish();});
 }
 void load(){RecordStore store=new RecordStore(new File(getFilesDir(),"records"));try{records=store.list();status.setText(records.isEmpty()?"まだ棋譜がありません。棋譜の取り込みや対局をすると自動保存されます。":records.size()+"件 · コピーした棋譜とアプリ内の対局");if(store.unreadable>0)status.append("\n読み込めない棋譜が"+store.unreadable+"件あります。ほかの棋譜は開けます。");}catch(IOException e){status.setText("棋譜の一覧を読み込めません："+e.getMessage());}
  list.setAdapter(new BaseAdapter(){public int getCount(){return records.size();}public Object getItem(int position){return records.get(position);}public long getItemId(int position){return position;}
   public View getView(int position,View old,ViewGroup parent){SavedRecord r=records.get(position);LinearLayout row=new LinearLayout(RecordArchiveActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xffffe1a3,0xffe6b66d});bg.setCornerRadius(dp(8));row.setBackground(bg);
    TextView heading=text((r.source.equals("match")?"対局  ·  ":"コピー  ·  ")+r.title,18,0xff412815);heading.setTypeface(null,Typeface.BOLD);row.addView(heading);
    TextView players=text(r.sente+"  対  "+r.gote,14,0xff513222);players.setPadding(0,dp(7),0,dp(4));row.addView(players);
    row.addView(text(r.lastPly()+"手 · "+r.result,14,0xff513222));row.addView(text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(r.created)),12,0xff72513b));
    if(r.firstPly>0)row.addView(text("更新前の棋譜："+r.firstPly+"手目からの記録",12,0xff72513b));return row;}
  });
 }
}
