package com.presentsoft.shogi3minutes;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.FrameLayout;

/** Keep the full board inside system bars and cutouts with Android 16 edge-to-edge. */
final class SafeWindow {
 static void setContent(Activity activity,View content){
  FrameLayout frame=new FrameLayout(activity);frame.setBackgroundColor(0xff400b0c);
  frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
  if(Build.VERSION.SDK_INT>=35){
   frame.setOnApplyWindowInsetsListener((view,insets)->{
    Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
    view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
   });
  }
  activity.setContentView(frame);frame.requestApplyInsets();
 }
}
