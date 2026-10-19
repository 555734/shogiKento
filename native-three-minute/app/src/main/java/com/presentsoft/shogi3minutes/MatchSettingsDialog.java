package com.presentsoft.shogi3minutes;
import android.app.*;import android.widget.*;

final class MatchSettingsDialog {
 static AlertDialog create(MainActivity activity){
  MainActivity.BoardView view=activity.view;MatchOptions current=view.matchOptions();
  LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
  TextView levelText=new TextView(activity);form.addView(levelText);
  SeekBar level=new SeekBar(activity);level.setTag("match_level");level.setMax(9);level.setProgress(current.level-1);form.addView(level);
  level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){levelText.setText("AIの強さ：Lv."+(progress+1)+" / 10");}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});levelText.setText("AIの強さ：Lv."+current.level+" / 10");
  TextView timeLabel=new TextView(activity);timeLabel.setText("持ち時間（双方・切れ負け）");form.addView(timeLabel);
  Spinner minutes=new Spinner(activity);minutes.setTag("match_minutes");String[] times=new String[MatchOptions.MINUTES.length];for(int i=0;i<times.length;i++)times[i]=MatchOptions.MINUTES[i]+"分";minutes.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,times));for(int i=0;i<times.length;i++)if(MatchOptions.MINUTES[i]==current.minutes)minutes.setSelection(i);form.addView(minutes);
  TextView sideLabel=new TextView(activity);sideLabel.setText("あなたの手番");form.addView(sideLabel);
  Spinner side=new Spinner(activity);side.setTag("match_side");side.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"先手（最初に指す）","後手（CPUが最初に指す）"}));side.setSelection(current.humanSide==1?0:1);form.addView(side);
  TextView note=new TextView(activity);note.setText("Lv.が高いほど探索量が増えます。\n設定を変えると現在の対局は棋譜に保存され、新しい盤面へ適用されます。");form.addView(note);
  ScrollView scroll=new ScrollView(activity);scroll.addView(form);
  return new AlertDialog.Builder(activity).setTitle("対局設定").setView(scroll).setNegativeButton("キャンセル",null).setNeutralButton("ライセンス",(d,k)->view.aiLicenses()).setPositiveButton("設定を適用",(d,k)->view.applyMatchOptions(new MatchOptions(MatchOptions.MINUTES[minutes.getSelectedItemPosition()],side.getSelectedItemPosition()==0?1:-1,level.getProgress()+1))).create();
 }
}
