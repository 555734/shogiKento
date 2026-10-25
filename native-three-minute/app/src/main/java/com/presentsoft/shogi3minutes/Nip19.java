package com.presentsoft.shogi3minutes;
import java.io.ByteArrayOutputStream;import java.util.Locale;
/** Bech32 keys (NIP-19): the account's transfer code is its secret as "nsec1…", its public ID is "npub1…". */
final class Nip19 {
 static final String CHARSET="qpzry9x8gf2tvdw0s3jn54khce6mua7l";
 private Nip19(){}
 static int polymod(int[] values){int chk=1;int[] g={0x3b6a57b2,0x26508e6d,0x1ea119fa,0x3d4233dd,0x2a1462b3};
  for(int v:values){int top=chk>>>25;chk=(chk&0x1ffffff)<<5^v;for(int i=0;i<5;i++)if((top>>>i&1)!=0)chk^=g[i];}return chk;}
 static int[] expand(String hrp){int[] r=new int[hrp.length()*2+1];for(int i=0;i<hrp.length();i++){r[i]=hrp.charAt(i)>>5;r[i+hrp.length()+1]=hrp.charAt(i)&31;}return r;}
 static int[] concat(int[] a,int[] b){int[] r=new int[a.length+b.length];System.arraycopy(a,0,r,0,a.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
 static int[] convert(byte[] data){int acc=0,bits=0;int[] out=new int[(data.length*8+4)/5];int n=0;for(byte x:data){acc=acc<<8|(x&255);bits+=8;while(bits>=5){bits-=5;out[n++]=acc>>>bits&31;}}if(bits>0)out[n++]=acc<<(5-bits)&31;return out;}
 static String encode(String hrp,byte[] data){int[] values=convert(data),chk=new int[6];int mod=polymod(concat(concat(expand(hrp),values),new int[6]))^1;for(int i=0;i<6;i++)chk[i]=mod>>>5*(5-i)&31;
  StringBuilder b=new StringBuilder(hrp).append('1');for(int v:values)b.append(CHARSET.charAt(v));for(int v:chk)b.append(CHARSET.charAt(v));return b.toString();}
 /** Returns the 32-byte payload for this prefix, or null for anything else (wrong prefix, checksum, length or padding). */
 static byte[] decode(String hrp,String text){
  if(text==null)return null;String s=text.trim().toLowerCase(Locale.US);if(s.length()>120||!s.startsWith(hrp+"1"))return null;String body=s.substring(hrp.length()+1);if(body.length()<7)return null;
  int[] values=new int[body.length()];for(int i=0;i<body.length();i++){int v=CHARSET.indexOf(body.charAt(i));if(v<0)return null;values[i]=v;}
  if(polymod(concat(expand(hrp),values))!=1)return null;
  int acc=0,bits=0;ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<values.length-6;i++){acc=acc<<5|values[i];bits+=5;if(bits>=8){bits-=8;out.write(acc>>>bits&255);}}
  if(bits>=5||(acc&((1<<bits)-1))!=0)return null;byte[] r=out.toByteArray();return r.length==32?r:null;
 }
}
