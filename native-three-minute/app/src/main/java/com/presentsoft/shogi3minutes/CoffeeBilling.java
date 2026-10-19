package com.presentsoft.shogi3minutes;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.util.Base64;
import com.android.billingclient.api.*;
import org.json.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** One non-consumable unlock. Persist a signed receipt, never a trusted paid boolean. */
final class CoffeeBilling {
 static final String PRODUCT="coffee_support_unlock";
 private static CoffeeBilling instance;
 static CoffeeBilling get(Context context){if(instance==null)instance=new CoffeeBilling(context);return instance;}
 final Context context;final SharedPreferences prefs;final Handler handler=new Handler(Looper.getMainLooper());
 final Set<Runnable> observers=new HashSet<>();BillingClient client;ProductDetails details;
 boolean owned,busy,ready,connecting,pendingPayment,launchRegistered,backgrounded,promptPending,fixture;Boolean debugOwned;
 int revision;String message="ストアを確認しています…",price;
 CoffeeBilling(Context context){this.context=context.getApplicationContext();prefs=context.getSharedPreferences("coffee_purchase",Context.MODE_PRIVATE);owned=verified(prefs.getString("receipt",""),prefs.getString("signature",""),BuildConfig.PLAY_BILLING_PUBLIC_KEY);
  client=BillingClient.newBuilder(this.context).setListener((result,purchases)->handler.post(()->updated(result,purchases)))
   .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).enableAutoServiceReconnection().build();connect();
 }
 boolean unlocked(){return BuildConfig.DEBUG&&debugOwned!=null?debugOwned:owned;}
 int level(int requested){return requested==10&&!unlocked()?9:requested;}
 void observe(Runnable r){observers.add(r);}void remove(Runnable r){observers.remove(r);}void changed(){for(Runnable r:new ArrayList<>(observers))r.run();}
 void registerLaunch(){if(launchRegistered&&!backgrounded)return;launchRegistered=true;backgrounded=false;int count=prefs.getInt("launches",0)+1;prefs.edit().putInt("launches",count).apply();promptPending=count>=2&&!prefs.getBoolean("hide_offer",false);}
 boolean wantsPrompt(){return promptPending&&!unlocked()&&!busy;}
 void hideOffer(boolean hide){prefs.edit().putBoolean("hide_offer",hide).apply();}
 void debugGrant(Boolean value){if(!BuildConfig.DEBUG)throw new IllegalStateException();debugOwned=value;changed();}
 void connect(){if(connecting)return;connecting=true;client.startConnection(new BillingClientStateListener(){public void onBillingSetupFinished(BillingResult result){handler.post(()->{connecting=false;ready=result.getResponseCode()==BillingClient.BillingResponseCode.OK;if(ready){queryProduct();refresh(false);}else{message="ストアに接続できません。接続環境を確認して再試行してください。";changed();}});}public void onBillingServiceDisconnected(){handler.post(()->{connecting=false;ready=false;busy=false;message="ストアとの接続が切れました。再試行できます。";changed();});}});}
 void queryProduct(){client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build())).build(),(result,response)->handler.post(()->{
  details=null;price=null;if(result.getResponseCode()==0)for(ProductDetails product:response.getProductDetailsList())if(PRODUCT.equals(product.getProductId())){details=product;ProductDetails.OneTimePurchaseOfferDetails offer=offer();if(offer!=null)price=offer.getFormattedPrice();}
  message=details==null?"ストアで購入商品の準備が完了していません。日本向けの設定予定価格は200円です。":BuildConfig.PLAY_BILLING_PUBLIC_KEY.isEmpty()?"購入機能は準備中です。現在は料金を請求しません。":"買い切り・追加料金なし。ストアに表示される価格で購入します。";changed();
 }));}
 ProductDetails.OneTimePurchaseOfferDetails offer(){if(details==null)return null;List<ProductDetails.OneTimePurchaseOfferDetails> offers=details.getOneTimePurchaseOfferDetailsList();return offers==null||offers.isEmpty()?null:offers.get(0);}
 boolean canBuy(){return !unlocked()&&!busy&&!pendingPayment&&ready&&price!=null&&!BuildConfig.PLAY_BILLING_PUBLIC_KEY.isEmpty();}
 void buy(Activity activity){if(BuildConfig.DEBUG&&fixture){busy=false;debugGrant(true);message="購入テストが完了しました（実際の請求なし）。";changed();return;}if(!canBuy()){queryOrConnect();return;}busy=true;message="ストアの購入画面で確認してください。";changed();
  ProductDetails.OneTimePurchaseOfferDetails offer=offer();BillingFlowParams.ProductDetailsParams params=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.getOfferToken()).build();
  BillingResult result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(params)).build());if(result.getResponseCode()!=0)updated(result,null);
 }
 void queryOrConnect(){if(ready)queryProduct();else connect();}
 void refresh(boolean manual){if(BuildConfig.DEBUG&&fixture){busy=false;if(manual){debugGrant(true);message="購入情報を復元しました（テスト）。";changed();}return;}if(!ready){if(manual){message="ストアへ接続して再試行してください。";changed();connect();}return;}int snapshot=++revision;if(manual){busy=true;message="購入情報を復元しています…";changed();}
  client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),(result,purchases)->handler.post(()->{
   if(snapshot!=revision){busy=false;changed();return;}if(result.getResponseCode()!=0){busy=false;if(manual)message="復元できませんでした。接続後に再試行してください。";changed();return;}
   boolean found=false;pendingPayment=false;for(Purchase purchase:purchases)if(purchase.getProducts().contains(PRODUCT)&&purchase.getPurchaseState()==Purchase.PurchaseState.PENDING)pendingPayment=true;for(Purchase purchase:purchases)if(purchase.getProducts().contains(PRODUCT)&&purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED){found=true;accept(purchase);}
   if(!found){revision++;owned=false;prefs.edit().remove("receipt").remove("signature").apply();busy=false;if(manual)message=pendingPayment?"お支払いの完了を待っています。完了後に特典を有効にします。":"このGoogle Playアカウントに購入情報はありません。購入したアカウントを確認してください。";changed();}
  }));
 }
 void updated(BillingResult result,List<Purchase> purchases){busy=false;if(result.getResponseCode()==BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED){refresh(true);return;}if(result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED){message="購入をキャンセルしました。";changed();return;}if(result.getResponseCode()!=0){message="購入を完了できませんでした。ストアで再試行してください。";changed();return;}if(purchases!=null)for(Purchase purchase:purchases)if(purchase.getProducts().contains(PRODUCT)){if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING){pendingPayment=true;message="お支払いの完了を待っています。完了後に特典を有効にします。";changed();}else if(purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED)accept(purchase);}}
 void accept(Purchase purchase){pendingPayment=false;if(!verified(purchase.getOriginalJson(),purchase.getSignature(),BuildConfig.PLAY_BILLING_PUBLIC_KEY)){busy=false;message="購入情報を検証できませんでした。購入の復元をお試しください。";changed();return;}int snapshot=++revision;busy=true;changed();Runnable grant=()->{if(snapshot!=revision)return;prefs.edit().putString("receipt",purchase.getOriginalJson()).putString("signature",purchase.getSignature()).commit();owned=true;busy=false;promptPending=false;message="ありがとうございます。広告除去と最強AI Lv.10を解放しました。";changed();};
  if(purchase.isAcknowledged())grant.run();else client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),result->handler.post(()->{if(snapshot!=revision)return;if(result.getResponseCode()==0)grant.run();else{busy=false;message="購入の確認処理を完了できませんでした。接続後に復元してください。";changed();}}));
 }
 static boolean verified(String data,String signature,String key){try{if(key.isEmpty()||data.isEmpty()||signature.isEmpty())return false;Signature check=Signature.getInstance("SHA1withRSA");check.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(key,Base64.DEFAULT))));check.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));if(!check.verify(Base64.decode(signature,Base64.DEFAULT)))return false;JSONObject json=new JSONObject(data);if(!"com.presentsoft.shogi3minutes".equals(json.optString("packageName"))||json.optInt("purchaseState",-1)!=0||json.optString("purchaseToken",json.optString("token")).isEmpty())return false;if(PRODUCT.equals(json.optString("productId")))return true;JSONArray ids=json.optJSONArray("productIds");if(ids!=null)for(int i=0;i<ids.length();i++)if(PRODUCT.equals(ids.optString(i)))return true;return false;}catch(Exception invalid){return false;}}
}
