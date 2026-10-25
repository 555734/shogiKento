package com.presentsoft.shogi3minutes;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.EditText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Reads direct text only while resumed and focused. Never reads URIs or logs clipboard text. */
final class ClipboardKifImport {
 private final MainActivity activity;private final ClipboardManager clipboard;private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private boolean active,closed,parsing;private KifRecord pending;private String pendingError;
 private final Runnable check=this::check;private final ClipboardManager.OnPrimaryClipChangedListener changed=this::schedule;
 ClipboardKifImport(MainActivity activity){this.activity=activity;clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);}
 void resume(){if(!active&&clipboard!=null)clipboard.addPrimaryClipChangedListener(changed);active=true;schedule();}
 void pause(){active=false;handler.removeCallbacks(check);if(clipboard!=null)clipboard.removePrimaryClipChangedListener(changed);}
 void close(){closed=true;pause();worker.shutdownNow();}
 void schedule(){handler.removeCallbacks(check);if(active&&!closed)handler.postDelayed(check,200);}
 private void check(){
  // Importing switches to study mode, so an online match (or a seek that may match any moment) is never interrupted.
  if(closed||!active||!activity.hasWindowFocus()||activity.choiceDialog!=null||parsing||activity.view!=null&&activity.view.mode==MainActivity.ONLINE)return;
  if(pending!=null||pendingError!=null){KifRecord record=pending;String error=pendingError;pending=null;pendingError=null;if(error!=null)error(error);else offerReplacement(record);return;}
  if(clipboard==null)return;
  try{
   ClipDescription description=clipboard.getPrimaryClipDescription();
   if(description==null||!description.hasMimeType("text/*")){remember("");return;}
   if(description.getExtras()!=null&&description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE",false)){remember("sensitive");return;}
   ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0){remember("");return;}
   CharSequence raw=clip.getItemAt(0).getText();if(raw==null){remember("");return;}
   if(raw.length()>KifRecord.MAX_CHARS){remember("oversized");return;}String text=raw.toString(),fingerprint=fingerprint(text);
   if(fingerprint.equals(activity.getPreferences(Context.MODE_PRIVATE).getString("clipboard_kif_seen","")))return;
   remember(fingerprint);if(!KifRecord.looksLike(text))return;
   activity.showChoice(new AlertDialog.Builder(activity).setTitle("棋譜の取り込み").setMessage("コピーされた棋譜を検討しますか？").setNegativeButton("キャンセル",null).setPositiveButton("検討する",(d,k)->parse(text)).create());
  }catch(SecurityException|IllegalStateException ignored){/* OS may deny a foreground clipboard read. Native paste remains available. */}
 }
 private void remember(String hash){activity.getPreferences(Context.MODE_PRIVATE).edit().putString("clipboard_kif_seen",hash).apply();}
 private static String fingerprint(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
 private void parse(String text){
  if(parsing||closed)return;parsing=true;
  worker.execute(()->{KifRecord record=null;String error=null;try{record=KifRecord.parse(text);}catch(IllegalArgumentException ex){error=ex.getMessage();}final KifRecord result=record;final String failure=error;
   handler.post(()->{parsing=false;if(closed||activity.isFinishing()||activity.isDestroyed())return;pending=result;pendingError=failure;schedule();});});
 }
 private void offerReplacement(KifRecord record){
  MainActivity.BoardView v=activity.view;
  boolean occupied=v.evalHistory.points.size()>1||v.evalHistory.imported||!EvaluationHistory.key(v.study.game).equals(EvaluationHistory.key(new ShogiGame()));
  if(occupied)activity.showChoice(new AlertDialog.Builder(activity).setTitle("検討棋譜の置き換え").setMessage("現在の検討内容を新しい棋譜で置き換えますか？").setNegativeButton("キャンセル",null).setPositiveButton("置き換える",(d,k)->v.importRecord(record)).create());
  else v.importRecord(record);
 }
 private void error(String message){activity.showChoice(new AlertDialog.Builder(activity).setTitle("棋譜を読み込めません").setMessage(message).setPositiveButton("閉じる",null).create());}
 void paste(){
  EditText input=new EditText(activity);input.setMinLines(5);input.setMaxLines(12);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setHint("ここを長押しして棋譜を貼り付け");
  activity.showChoice(new AlertDialog.Builder(activity).setTitle("棋譜を貼り付け").setView(input).setNegativeButton("キャンセル",null).setPositiveButton("検討する",(d,k)->parse(input.getText().toString())).create());
 }
}
