package com.presentsoft.shogi3minutes;
import android.app.*;import android.widget.*;

final class CoffeeDialog {
 static AlertDialog create(Activity activity){CoffeeBilling coffee=CoffeeBilling.get(activity);int pad=(int)(20*activity.getResources().getDisplayMetrics().density);LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(pad,pad,pad,pad);
  TextView title=new TextView(activity);title.setText("コーヒーを1杯奢って、開発を応援\n\n・すべての広告を非表示\n・対局AIの最強レベル Lv.10を解放\n\n買い切りです。同じストアのアカウントで購入を復元できます。AndroidとiOSの購入は共通ではありません。");title.setTextSize(16);box.addView(title);
  TextView status=new TextView(activity);status.setTag("coffee_status");box.addView(status);
  Button buy=new Button(activity);buy.setTag("coffee_buy");box.addView(buy);Button restore=new Button(activity);restore.setTag("coffee_restore");restore.setText("購入を復元する");box.addView(restore);
  CheckBox hide=new CheckBox(activity);hide.setTag("coffee_hide_offer");hide.setText("今後、起動時に表示しない");hide.setChecked(coffee.prefs.getBoolean("hide_offer",false));hide.setOnCheckedChangeListener((b,value)->coffee.hideOffer(value));box.addView(hide);
  ScrollView scroll=new ScrollView(activity);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("☕ コーヒーを奢る").setView(scroll).setPositiveButton("閉じる",null).create();
  Runnable update=()->{status.setText(coffee.unlocked()?"購入済みです。ありがとうございます。":coffee.message);buy.setText(coffee.unlocked()?"購入済み":coffee.price==null?"価格を確認中（日本向け予定200円）":"コーヒーを奢る · "+coffee.price);buy.setEnabled(!coffee.unlocked()&&(!coffee.busy&&(coffee.canBuy()||coffee.fixture)));restore.setEnabled(!coffee.busy);};
  buy.setOnClickListener(v->coffee.buy(activity));restore.setOnClickListener(v->coffee.refresh(true));coffee.observe(update);update.run();box.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(android.view.View v){}public void onViewDetachedFromWindow(android.view.View v){coffee.remove(update);}});return dialog;
 }
}
