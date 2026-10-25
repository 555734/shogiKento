package com.presentsoft.shogi3minutes;
import java.nio.charset.StandardCharsets;import java.security.SecureRandom;
/**
 * The account card two matched players exchange over their encrypted link, right after HELLO:
 * "ID <account pubkey> <rating> <games> <name, base64url> <BIP-340 signature>". The signature covers the session
 * id, so a card cannot be replayed into another game, and proves the sender holds the account key.
 */
final class OnlineIdentity {
 static final int NAME_MAX=16;
 final String pubkey,name;final int rating,games;
 OnlineIdentity(String pubkey,String name,int rating,int games){this.pubkey=pubkey;this.name=name;this.rating=rating;this.games=games;}
 static String statement(byte[] sessionId,String pubkey,int rating,int games,String name){return "shogikento-id-v1|"+Schnorr.hex(sessionId)+"|"+pubkey+"|"+rating+"|"+games+"|"+name;}
 static String message(Schnorr.Key key,byte[] sessionId,String name,int rating,int games,SecureRandom random){
  byte[] aux=new byte[32];random.nextBytes(aux);byte[] sig=key.sign(Schnorr.sha256(statement(sessionId,key.publicHex,rating,games,name).getBytes(StandardCharsets.UTF_8)),aux);
  return "ID "+key.publicHex+" "+rating+" "+games+" "+base64url(name.getBytes(StandardCharsets.UTF_8))+" "+Schnorr.hex(sig);
 }
 /** Returns the verified card, or null for anything malformed, out of range, renamed in transit or signed for another session. */
 static OnlineIdentity parse(String text,byte[] sessionId){
  try{String[] f=text.trim().split(" ");if(f.length!=6||!f[0].equals("ID")||f[1].length()!=64||f[5].length()!=128||f[4].length()>200)return null;
   String pub=f[1].toLowerCase(java.util.Locale.US);int rating=Integer.parseInt(f[2]),games=Integer.parseInt(f[3]);if(!Rating.valid(rating)||games<0||games>1000000)return null;
   String name=new String(unbase64url(f[4]),StandardCharsets.UTF_8);if(!name.equals(cleanName(name)))return null;
   byte[] digest=Schnorr.sha256(statement(sessionId,pub,rating,games,name).getBytes(StandardCharsets.UTF_8));
   return Schnorr.verify(digest,Schnorr.unhex(pub),Schnorr.unhex(f[5]))?new OnlineIdentity(pub,name,rating,games):null;
  }catch(RuntimeException e){return null;}
 }
 /**
  * The same on both platforms: drops control and bidirectional-override characters, trims ASCII and ideographic
  * spaces, and keeps at most 16 code points. Returns "" when nothing printable is left.
  */
 static String cleanName(String raw){if(raw==null)return "";StringBuilder b=new StringBuilder();int count=0;
  for(int i=0;i<raw.length();){int cp=raw.codePointAt(i);i+=Character.charCount(cp);
   if(Character.isISOControl(cp)||cp>=0x202A&&cp<=0x202E||cp>=0x2066&&cp<=0x2069||cp==0x200E||cp==0x200F||cp==0xFEFF)continue;b.appendCodePoint(cp);}
  String s=b.toString();int start=0,end=s.length();while(start<end&&(s.charAt(start)<=' '||s.charAt(start)=='　'))start++;while(end>start&&(s.charAt(end-1)<=' '||s.charAt(end-1)=='　'))end--;s=s.substring(start,end);
  StringBuilder out=new StringBuilder();for(int i=0;i<s.length()&&count<NAME_MAX;){int cp=s.codePointAt(i);i+=Character.charCount(cp);out.appendCodePoint(cp);count++;}
  String r=out.toString();int e=r.length();while(e>0&&(r.charAt(e-1)<=' '||r.charAt(e-1)=='　'))e--;return r.substring(0,e);
 }
 static String base64url(byte[] b){String s=NetJson.base64(b).replace('+','-').replace('/','_');int e=s.length();while(e>0&&s.charAt(e-1)=='=')e--;return s.substring(0,e);}
 static byte[] unbase64url(String s){if(!s.matches("[A-Za-z0-9_-]*"))throw new IllegalArgumentException("base64url");StringBuilder b=new StringBuilder(s.replace('-','+').replace('_','/'));while(b.length()%4!=0)b.append('=');return NetJson.unbase64(b.toString());}
}
