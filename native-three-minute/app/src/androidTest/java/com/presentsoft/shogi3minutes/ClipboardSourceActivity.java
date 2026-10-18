package com.presentsoft.shogi3minutes;
import android.app.Activity;import android.content.*;import android.os.*;import android.widget.TextView;
/** Separate test-APK foreground app: exercises an actual OS clipboard across app focus changes. */
public final class ClipboardSourceActivity extends Activity {
 private boolean copied;
 @Override public void onCreate(Bundle state){super.onCreate(state);TextView label=new TextView(this);label.setText("Clipboard source fixture");setContentView(label);}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused||copied)return;copied=true;
  ClipData clip=ClipData.newPlainText("KIF test fixture",getIntent().getStringExtra("text"));
  if(getIntent().getBooleanExtra("sensitive",false)){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}
  ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);new Handler(Looper.getMainLooper()).postDelayed(this::finish,400);
 }
 @Override protected void onDestroy(){super.onDestroy();if(copied)sendBroadcast(new Intent("com.presentsoft.shogi3minutes.TEST_CLIPBOARD_FINISHED").setPackage(getIntent().getStringExtra("return_package")));}
}
