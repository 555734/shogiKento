package com.presentsoft.shogi3minutes;
import android.app.*;import android.content.res.ColorStateList;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;

/** Native, accessible controls; changes take effect immediately and persist on the device. */
final class FeedbackSettingsDialog {
 static final int GOLD=0xffffdea0,CREAM=0xfffff1d6;
 static AlertDialog create(MainActivity activity){
  AppSettings settings=activity.settings;int pad=Math.round(22*activity.getResources().getDisplayMetrics().density);
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad/2,pad,pad/2);
  TextView title=new TextView(activity);title.setText("音と振動の設定");title.setTextSize(22);title.setTextColor(GOLD);title.setTypeface(null,Typeface.BOLD);title.setPadding(pad,pad,pad,pad/2);
  Switch sound=new Switch(activity);sound.setTag("setting_sound");sound.setText("効果音（SE）");sound.setTextColor(CREAM);sound.setTextSize(17);sound.setMinHeight(pad*2+8);sound.setChecked(settings.sound());body.addView(sound);
  TextView volume=new TextView(activity);volume.setTag("setting_volume_label");volume.setText("効果音の音量  "+settings.volume()+"%");volume.setTextColor(CREAM);volume.setTextSize(15);volume.setPadding(0,pad/2,0,pad/3);body.addView(volume);
  SeekBar slider=new SeekBar(activity);slider.setTag("setting_volume");slider.setContentDescription("効果音の音量");slider.setMax(100);slider.setProgress(settings.volume());slider.setProgressTintList(ColorStateList.valueOf(GOLD));slider.setThumbTintList(ColorStateList.valueOf(GOLD));body.addView(slider,new LinearLayout.LayoutParams(-1,pad*2+8));
  Button soundTest=new Button(activity);soundTest.setTag("setting_sound_test");soundTest.setText("効果音を試す");soundTest.setEnabled(settings.sound()&&settings.volume()>0);soundTest.setOnClickListener(v->activity.view.sounds.play());body.addView(soundTest);
  Switch vibration=new Switch(activity);vibration.setTag("setting_vibration");vibration.setText("着手時のバイブレーション");vibration.setTextSize(17);vibration.setTextColor(CREAM);vibration.setMinHeight(pad*2+8);vibration.setChecked(settings.vibration());body.addView(vibration);
  Button vibrationTest=new Button(activity);vibrationTest.setTag("setting_vibration_test");vibrationTest.setText("振動を試す");vibrationTest.setEnabled(settings.vibration());vibrationTest.setOnClickListener(v->v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY));body.addView(vibrationTest);
  TextView note=new TextView(activity);note.setText("設定は自動保存されます。音量と振動は端末本体の設定にも従います。");note.setTextColor(0xffd2bba1);note.setTextSize(12);note.setPadding(0,pad/2,0,0);body.addView(note);
  sound.setOnCheckedChangeListener((button,checked)->{settings.sound(checked);soundTest.setEnabled(checked&&settings.volume()>0);});
  vibration.setOnCheckedChangeListener((button,checked)->{settings.vibration(checked);vibrationTest.setEnabled(checked);});
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){settings.volume(value);volume.setText("効果音の音量  "+value+"%");soundTest.setEnabled(settings.sound()&&value>0);}});
  ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(body);
  AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setPositiveButton("閉じる",null).create();
  dialog.setOnShowListener(d->{GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff471519,0xff20120f});background.setCornerRadius(pad);background.setStroke(1,0xffb58a47);dialog.getWindow().setBackgroundDrawable(background);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD);});return dialog;
 }
}
