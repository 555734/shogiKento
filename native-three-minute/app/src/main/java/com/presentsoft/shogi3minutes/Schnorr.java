package com.presentsoft.shogi3minutes;
import java.math.BigInteger;import java.security.*;import java.util.Arrays;
/** BIP-340 Schnorr signatures over secp256k1, used only to sign throwaway Nostr signaling keys. */
final class Schnorr {
 private Schnorr(){}
 static final BigInteger P=new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",16);
 static final BigInteger N=new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",16);
 static final BigInteger[] G={new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",16),new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8",16)};
 static final BigInteger SEVEN=BigInteger.valueOf(7),TWO=BigInteger.valueOf(2),THREE=BigInteger.valueOf(3);
 // Jacobian point {X,Y,Z}; Z==0 is infinity.
 static BigInteger[] jacobian(BigInteger[] a){return a==null?new BigInteger[]{BigInteger.ONE,BigInteger.ONE,BigInteger.ZERO}:new BigInteger[]{a[0],a[1],BigInteger.ONE};}
 static BigInteger[] affine(BigInteger[] j){if(j[2].signum()==0)return null;BigInteger z=j[2].modInverse(P),z2=z.multiply(z).mod(P);return new BigInteger[]{j[0].multiply(z2).mod(P),j[1].multiply(z2).multiply(z).mod(P)};}
 static BigInteger[] twice(BigInteger[] p){
  if(p[2].signum()==0||p[1].signum()==0)return jacobian(null);
  BigInteger y2=p[1].multiply(p[1]).mod(P),s=p[0].multiply(y2).shiftLeft(2).mod(P),m=p[0].multiply(p[0]).multiply(THREE).mod(P);
  BigInteger x=m.multiply(m).subtract(s.shiftLeft(1)).mod(P),y=m.multiply(s.subtract(x)).subtract(y2.multiply(y2).shiftLeft(3)).mod(P);
  return new BigInteger[]{x,y,p[1].multiply(p[2]).shiftLeft(1).mod(P)};
 }
 static BigInteger[] add(BigInteger[] p,BigInteger[] q){
  if(p[2].signum()==0)return q;if(q[2].signum()==0)return p;
  BigInteger z1=p[2].multiply(p[2]).mod(P),z2=q[2].multiply(q[2]).mod(P);
  BigInteger u1=p[0].multiply(z2).mod(P),u2=q[0].multiply(z1).mod(P),s1=p[1].multiply(z2).multiply(q[2]).mod(P),s2=q[1].multiply(z1).multiply(p[2]).mod(P);
  if(u1.equals(u2))return s1.equals(s2)?twice(p):jacobian(null);
  BigInteger h=u2.subtract(u1).mod(P),r=s2.subtract(s1).mod(P),h2=h.multiply(h).mod(P),h3=h2.multiply(h).mod(P),v=u1.multiply(h2).mod(P);
  BigInteger x=r.multiply(r).subtract(h3).subtract(v.shiftLeft(1)).mod(P),y=r.multiply(v.subtract(x)).subtract(s1.multiply(h3)).mod(P);
  return new BigInteger[]{x,y,p[2].multiply(q[2]).multiply(h).mod(P)};
 }
 private static BigInteger[][][] table;
 // 4-bit fixed-base windows for G: table[w][j]=j*16^w*G, so k*G needs at most 64 additions.
 static synchronized BigInteger[][][] table(){
  if(table!=null)return table;BigInteger[][][] t=new BigInteger[64][16][];BigInteger[] base=jacobian(G);
  for(int w=0;w<64;w++){BigInteger[] sum=jacobian(null);for(int j=1;j<16;j++){sum=add(sum,base);t[w][j]=jacobian(affine(sum));}base=twice(twice(twice(twice(base))));}
  return table=t;
 }
 static BigInteger[] multiplyG(BigInteger k){BigInteger[][][] t=table();BigInteger[] result=jacobian(null);
  for(int w=0;w<64;w++){int j=0;for(int b=0;b<4;b++)if(k.testBit(4*w+b))j|=1<<b;if(j!=0)result=add(result,t[w][j]);}return affine(result);}
 static BigInteger[] multiply(BigInteger[] point,BigInteger k){
  if(point==G&&k.bitLength()<=256)return multiplyG(k);
  BigInteger[] result=jacobian(null),base=jacobian(point);
  for(int i=k.bitLength()-1;i>=0;i--){result=twice(result);if(k.testBit(i))result=add(result,base);}
  return affine(result);
 }
 static BigInteger[] liftX(BigInteger x){
  if(x.compareTo(P)>=0)return null;BigInteger c=x.pow(3).add(SEVEN).mod(P),y=c.modPow(P.add(BigInteger.ONE).shiftRight(2),P);
  if(!y.multiply(y).mod(P).equals(c))return null;return new BigInteger[]{x,y.testBit(0)?P.subtract(y):y};
 }
 static byte[] bytes(BigInteger v){byte[] raw=v.toByteArray(),out=new byte[32];int n=Math.min(32,raw.length);System.arraycopy(raw,raw.length-n,out,32-n,n);return out;}
 static BigInteger number(byte[] b,int offset){return new BigInteger(1,Arrays.copyOfRange(b,offset,offset+32));}
 static byte[] sha256(byte[]... parts){try{MessageDigest d=MessageDigest.getInstance("SHA-256");for(byte[] p:parts)d.update(p);return d.digest();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 static byte[] tagged(String tag,byte[]... parts){byte[] t=sha256(tag.getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[][] all=new byte[parts.length+2][];all[0]=t;all[1]=t;System.arraycopy(parts,0,all,2,parts.length);return sha256(all);}
 static byte[] publicKey(byte[] secret){BigInteger d=number(secret,0);if(d.signum()==0||d.compareTo(N)>=0)throw new IllegalArgumentException("secret key range");return bytes(multiply(G,d)[0]);}
 static byte[] newSecret(SecureRandom random){byte[] k=new byte[32];do random.nextBytes(k);while(number(k,0).signum()==0||number(k,0).compareTo(N)>=0);return k;}
 /** A signing key with its x-only public key and BIP-340 even-Y secret computed once. */
 static final class Key {
  final byte[] publicKey;final BigInteger d;final String publicHex;
  Key(byte[] secret){BigInteger d0=number(secret,0);if(d0.signum()==0||d0.compareTo(N)>=0)throw new IllegalArgumentException("secret key range");
   BigInteger[] pub=multiply(G,d0);d=pub[1].testBit(0)?N.subtract(d0):d0;publicKey=bytes(pub[0]);publicHex=hex(publicKey);}
  byte[] sign(byte[] message,byte[] aux){return Schnorr.sign(message,d,publicKey,aux);}
 }
 static byte[] sign(byte[] message,byte[] secret,byte[] aux){return new Key(secret).sign(message,aux);}
 static byte[] sign(byte[] message,BigInteger d,byte[] px,byte[] aux){
  byte[] t=bytes(d),h=tagged("BIP0340/aux",aux);for(int i=0;i<32;i++)t[i]^=h[i];
  BigInteger k0=number(tagged("BIP0340/nonce",t,px,message),0).mod(N);if(k0.signum()==0)throw new IllegalStateException("nonce");
  BigInteger[] r=multiply(G,k0);BigInteger k=r[1].testBit(0)?N.subtract(k0):k0;byte[] rx=bytes(r[0]);
  BigInteger e=number(tagged("BIP0340/challenge",rx,px,message),0).mod(N);
  byte[] sig=new byte[64];System.arraycopy(rx,0,sig,0,32);System.arraycopy(bytes(k.add(e.multiply(d)).mod(N)),0,sig,32,32);return sig;
 }
 static boolean verify(byte[] message,byte[] publicKey,byte[] sig){
  if(publicKey.length!=32||sig.length!=64)return false;BigInteger[] pub=liftX(number(publicKey,0));if(pub==null)return false;
  BigInteger r=number(sig,0),s=number(sig,32);if(r.compareTo(P)>=0||s.compareTo(N)>=0)return false;
  BigInteger e=number(tagged("BIP0340/challenge",Arrays.copyOfRange(sig,0,32),publicKey,message),0).mod(N);
  BigInteger[] sum=affine(add(jacobian(multiply(G,s)),jacobian(multiply(pub,N.subtract(e)))));
  return sum!=null&&!sum[1].testBit(0)&&sum[0].equals(r);
 }
 static String hex(byte[] b){StringBuilder s=new StringBuilder(b.length*2);for(byte x:b)s.append(Character.forDigit((x>>4)&15,16)).append(Character.forDigit(x&15,16));return s.toString();}
 static byte[] unhex(String s){if(s==null||(s.length()&1)!=0)throw new IllegalArgumentException("hex");byte[] b=new byte[s.length()/2];
  for(int i=0;i<b.length;i++){int hi=Character.digit(s.charAt(2*i),16),lo=Character.digit(s.charAt(2*i+1),16);if(hi<0||lo<0)throw new IllegalArgumentException("hex");b[i]=(byte)(hi*16+lo);}return b;}
}
