package com.presentsoft.shogi3minutes;

import android.app.Activity;
import com.google.android.ump.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** All users receive the same restricted, child-directed advertising treatment. */
final class AdPrivacy {
 static boolean ready,updating;
 static final ArrayList<Consumer<Boolean>> pending=new ArrayList<>();
 static void gather(Activity activity,Consumer<Boolean> callback){
  if(BuildConfig.DEBUG){callback.accept(true);return;}
  if(ready){callback.accept(true);return;}
  pending.add(callback);if(updating)return;updating=true;
  ConsentInformation info=UserMessagingPlatform.getConsentInformation(activity);
  ConsentRequestParameters params=new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build();
  info.requestConsentInfoUpdate(activity,params,
   ()->UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity,error->finish(info.canRequestAds())),
   error->finish(info.canRequestAds()));
 }
 static void finish(boolean allowed){ready=allowed;updating=false;ArrayList<Consumer<Boolean>> callbacks=new ArrayList<>(pending);pending.clear();for(Consumer<Boolean> callback:callbacks)callback.accept(allowed);}
 static boolean optionsRequired(Activity activity){return !BuildConfig.DEBUG&&UserMessagingPlatform.getConsentInformation(activity).getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;}
 static void showOptions(Activity activity){UserMessagingPlatform.showPrivacyOptionsForm(activity,error->{ready=UserMessagingPlatform.getConsentInformation(activity).canRequestAds();});}
 static String unit(String placement){
  if(BuildConfig.DEBUG)return TestAdCard.UNIT;
  switch(placement){case "home":return "ca-app-pub-6772282664898856/4850498772";case "study":return "ca-app-pub-6772282664898856/4854368203";case "match":return "ca-app-pub-6772282664898856/7652096944";case "archive":return "ca-app-pub-6772282664898856/8051552199";default:throw new IllegalArgumentException("Unknown ad placement");}
 }
}
