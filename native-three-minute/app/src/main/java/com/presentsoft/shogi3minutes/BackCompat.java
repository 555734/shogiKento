package com.presentsoft.shogi3minutes;

import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Android 13+ back handling. Android 16 apps targeting API 36 never receive onBackPressed. Only touched on API 33+. */
final class BackCompat {
 private BackCompat(){}
 static Object register(Activity activity,Runnable action){
  if(Build.VERSION.SDK_INT<33)return null;
  OnBackInvokedCallback callback=action::run;activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);return callback;
 }
 static void unregister(Activity activity,Object callback){
  if(Build.VERSION.SDK_INT<33||callback==null)return;
  activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback)callback);
 }
}
