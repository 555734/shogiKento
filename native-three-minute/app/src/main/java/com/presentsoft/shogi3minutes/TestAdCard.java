package com.presentsoft.shogi3minutes;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.nativead.*;

/** Test-only integration: no production ID or production switch exists in this build. */
final class TestAdCard extends FrameLayout {
 static final String UNIT="ca-app-pub-3940256099942544/2247696110";
 static final String VIDEO_UNIT="ca-app-pub-3940256099942544/1044960115";
 final Activity activity;final String placement;final boolean fixture;
 String state="loading",lastError="",requestedUnit=UNIT;int requests;boolean disposed;NativeAd ad;
 final TextView status;final NativeAdView nativeView;
 final TextView headline,body,advertiser;final Button action;final MediaView media;
 final android.os.Handler deadlineHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 final Runnable timeout=()->{if(!disposed&&state.equals("loading")){lastError="Ad request timed out after 30 seconds";state("failed","広告の読み込みがタイムアウトしました。アプリはそのまま使えます。");}};
 TestAdCard(Activity activity,String placement,boolean fixture){super(activity);this.activity=activity;this.placement=placement;this.fixture=fixture;
  setTag("ad_"+placement);setPadding(dp(12),dp(8),dp(12),dp(8));GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xffffefcf,0xffe9c88d});bg.setCornerRadius(dp(10));bg.setStroke(dp(1),0xffdbb56b);setBackground(bg);
  LinearLayout column=new LinearLayout(activity);column.setOrientation(1);addView(column,new FrameLayout.LayoutParams(-1,-1));
  TextView badge=label("広告 · テスト広告",12);badge.setTypeface(null,Typeface.BOLD);column.addView(badge,new LinearLayout.LayoutParams(-1,dp(26)));
  status=label("公式テスト広告を読み込み中…",13);status.setContentDescription("ad_status_"+placement+":loading");column.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
  nativeView=new NativeAdView(activity);column.addView(nativeView,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout assets=new LinearLayout(activity);assets.setOrientation(1);nativeView.addView(assets,new FrameLayout.LayoutParams(-1,-1));
  media=new MediaView(activity);assets.addView(media,new LinearLayout.LayoutParams(-1,dp(120)));nativeView.setMediaView(media);
  headline=label("",16);headline.setTypeface(null,Typeface.BOLD);assets.addView(headline,new LinearLayout.LayoutParams(-1,-2));nativeView.setHeadlineView(headline);
  body=label("",12);assets.addView(body,new LinearLayout.LayoutParams(-1,-2));nativeView.setBodyView(body);
  advertiser=label("",11);assets.addView(advertiser,new LinearLayout.LayoutParams(-1,-2));nativeView.setAdvertiserView(advertiser);
  action=new Button(activity);assets.addView(action,new LinearLayout.LayoutParams(-1,dp(48)));nativeView.setCallToActionView(action);
  nativeView.setVisibility(GONE);setContentDescription("広告枠 "+placement+" テスト専用");
 }
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(0xff422615);return t;}
 void state(String next,String message){if(disposed)return;state=next;status.setText(message);status.setContentDescription("ad_status_"+placement+":"+next);}
 void load(){if(disposed||requests>0)return;if(fixture){requests++;fixtureState(true);return;}
  deadlineHandler.postDelayed(timeout,30000);
  MobileAds.setRequestConfiguration(new RequestConfiguration.Builder().setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE).setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build());
  MobileAds.initialize(activity,result->activity.runOnUiThread(()->request(UNIT)));
 }
 void request(String unit){if(disposed||!state.equals("loading"))return;requests++;requestedUnit=unit;
   Bundle extras=new Bundle();extras.putString("npa","1");
   new AdLoader.Builder(activity,unit).forNativeAd(loaded->{if(disposed||!state.equals("loading")){loaded.destroy();return;}deadlineHandler.removeCallbacks(timeout);if(ad!=null)ad.destroy();ad=loaded;
    headline.setText(loaded.getHeadline());body.setText(loaded.getBody());body.setVisibility(loaded.getBody()==null?GONE:VISIBLE);advertiser.setText(loaded.getAdvertiser());advertiser.setVisibility(loaded.getAdvertiser()==null?GONE:VISIBLE);action.setText(loaded.getCallToAction());action.setVisibility(loaded.getCallToAction()==null?GONE:VISIBLE);media.setMediaContent(loaded.getMediaContent());nativeView.setNativeAd(loaded);nativeView.setVisibility(VISIBLE);state("loaded","Google公式テスト広告 · 収益は発生しません");
   }).withAdListener(new AdListener(){@Override public void onAdFailedToLoad(LoadAdError error){if(disposed)return;lastError=error.toString();android.util.Log.w("TestAds",lastError);if(error.getCode()==0&&unit.equals(UNIT)){state("loading","別の公式テスト素材を読み込み中…");request(VIDEO_UNIT);return;}state("failed","広告を取得できません（"+error.getCode()+"）。アプリはそのまま使えます。");}})
    .withNativeAdOptions(new NativeAdOptions.Builder().setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build()).build()).build()
    .loadAd(new AdRequest.Builder().addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter.class,extras).build());
 }
 void fixtureState(boolean success){if(!fixture||disposed)return;nativeView.setVisibility(GONE);state(success?"fixture":"failed",success?"表示テスト用サンプル（配信広告ではありません）\n棋力を伸ばす毎日の一手。\n広告と盤面の操作は独立しています。":"広告を取得できません。アプリはそのまま使えます。");}
 void close(){disposed=true;deadlineHandler.removeCallbacks(timeout);if(ad!=null){ad.destroy();ad=null;}nativeView.destroy();}
}
