package com.presentsoft.shogi3minutes;
import android.content.*;import android.security.keystore.*;import java.security.KeyStore;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
/**
 * Keeps the online account on this device: the secret is encrypted with an Android Keystore AES key (it never leaves
 * the device in readable form; a restored backup without that key starts a new account, recoverable by transfer
 * code), the rating and history are JSON in private preferences. Writes are synchronous so a crash cannot drop
 * the record of a rated game in progress.
 */
final class AccountStore {
 static final String PREFS="online_account",ALIAS="shogikento_online_account";
 final SharedPreferences prefs;
 /** Set when load() settled an unfinished rated game as a loss, so the caller can publish the new profile. */
 boolean settledAbandoned;
 AccountStore(Context c){prefs=c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 static SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);KeyStore.Entry e=ks.getEntry(ALIAS,null);if(e instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)e).getSecretKey();
  KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return g.generateKey();}
 byte[] loadSecret(){
  String sealed=prefs.getString("secret",null);
  if(sealed!=null)try{byte[] in=NetJson.unbase64(sealed);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,in,0,12));byte[] s=c.doFinal(in,12,in.length-12);if(s.length==32)return s;}catch(Exception ignored){}
  String plain=prefs.getString("secret_plain",null);if(plain!=null)try{byte[] s=NetJson.unbase64(plain);if(s.length==32)return s;}catch(RuntimeException ignored){}
  return null;
 }
 /** Prefers the Keystore; devices whose keystore is broken fall back to app-private storage. */
 void saveSecret(byte[] secret){
  try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] iv=c.getIV(),ct=c.doFinal(secret),out=new byte[iv.length+ct.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(ct,0,out,iv.length,ct.length);
   if(iv.length!=12)throw new IllegalStateException("iv");prefs.edit().putString("secret",NetJson.base64(out)).remove("secret_plain").commit();}
  catch(Exception e){prefs.edit().putString("secret_plain",NetJson.base64(secret)).remove("secret").commit();}
 }
 /** Loads (or creates) the account; a rated game left unfinished last time is settled as a loss and saved. */
 OnlineAccount load(){
  byte[] secret=loadSecret();if(secret==null){secret=OnlineAccount.newSecret();saveSecret(secret);prefs.edit().remove("account").commit();}
  OnlineAccount a;try{a=OnlineAccount.load(secret,prefs.getString("account",null));}catch(RuntimeException e){a=OnlineAccount.load(secret,null);}
  settledAbandoned=a.resolveAbandoned(System.currentTimeMillis()/1000)!=null;if(settledAbandoned)save(a);return a;
 }
 void save(OnlineAccount a){prefs.edit().putString("account",a.json()).commit();}
 /** Replaces this device's account with the one behind a transfer code. */
 OnlineAccount replace(byte[] secret){saveSecret(secret);OnlineAccount a=OnlineAccount.load(secret,null);save(a);return a;}
}
