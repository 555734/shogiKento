package com.presentsoft.shogi3minutes;
import java.util.*;
/** Minimal JSON for Nostr. Serialization follows NIP-01 exactly so event ids hash identically on relays. */
final class NetJson {
 private NetJson(){}
 static String write(Object v){StringBuilder b=new StringBuilder();write(b,v);return b.toString();}
 @SuppressWarnings("unchecked")
 static void write(StringBuilder b,Object v){
  if(v==null)b.append("null");
  else if(v instanceof String)quote(b,(String)v);
  else if(v instanceof Boolean||v instanceof Long||v instanceof Integer)b.append(v);
  else if(v instanceof Number){double d=((Number)v).doubleValue();if(d==Math.rint(d)&&Math.abs(d)<1e15)b.append((long)d);else b.append(d);}
  else if(v instanceof Map){b.append('{');boolean first=true;for(Map.Entry<String,Object> e:((Map<String,Object>)v).entrySet()){if(!first)b.append(',');first=false;quote(b,e.getKey());b.append(':');write(b,e.getValue());}b.append('}');}
  else if(v instanceof List){b.append('[');boolean first=true;for(Object o:(List<Object>)v){if(!first)b.append(',');first=false;write(b,o);}b.append(']');}
  else throw new IllegalArgumentException("JSON type "+v.getClass());
 }
 static void quote(StringBuilder b,String s){b.append('"');
  for(int i=0;i<s.length();i++){char c=s.charAt(i);
   switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;
    case '\t':b.append("\\t");break;case '\b':b.append("\\b");break;case '\f':b.append("\\f");break;
    default:if(c<0x20)b.append(String.format(Locale.US,"\\u%04x",(int)c));else b.append(c);}
  }b.append('"');}
 static final String B64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
 /** RFC 4648 base64 with padding; java.util.Base64 needs API 26 and android.util.Base64 is unavailable to desktop tests. */
 static String base64(byte[] b){StringBuilder o=new StringBuilder((b.length+2)/3*4);
  for(int i=0;i<b.length;i+=3){int n=(b[i]&255)<<16|(i+1<b.length?(b[i+1]&255)<<8:0)|(i+2<b.length?b[i+2]&255:0);
   o.append(B64.charAt(n>>>18)).append(B64.charAt(n>>>12&63)).append(i+1<b.length?B64.charAt(n>>>6&63):'=').append(i+2<b.length?B64.charAt(n&63):'=');}
  return o.toString();}
 static byte[] unbase64(String s){if(s==null||s.length()%4!=0)throw new IllegalArgumentException("base64 length");int pad=s.endsWith("==")?2:s.endsWith("=")?1:0;
  byte[] out=new byte[s.length()/4*3-pad];int j=0;
  for(int i=0;i<s.length();i+=4){int n=0;for(int k=0;k<4;k++){char c=s.charAt(i+k);int v=c=='='&&i+k>=s.length()-pad?0:B64.indexOf(c);if(v<0)throw new IllegalArgumentException("base64 character");n=n<<6|v;}
   for(int k=2;k>=0;k--){if(j+2-k<out.length)out[j+2-k]=(byte)(n>>>(8*k));}j+=3;}
  return out;}
 static Object parse(String s){NetJson.Parser p=new Parser(s);Object v=p.value();p.space();if(p.i!=s.length())throw p.error();return v;}
 static Map<String,Object> object(Object v){if(!(v instanceof Map))throw new IllegalArgumentException("object expected");@SuppressWarnings("unchecked")Map<String,Object> m=(Map<String,Object>)v;return m;}
 static List<Object> array(Object v){if(!(v instanceof List))throw new IllegalArgumentException("array expected");@SuppressWarnings("unchecked")List<Object> l=(List<Object>)v;return l;}
 static String string(Map<String,Object> m,String key){Object v=m.get(key);return v instanceof String?(String)v:null;}
 static long number(Map<String,Object> m,String key,long fallback){Object v=m.get(key);return v instanceof Number?((Number)v).longValue():fallback;}
 static final class Parser {
  final String s;int i,depth;Parser(String s){this.s=s;}
  IllegalArgumentException error(){return new IllegalArgumentException("Invalid JSON at "+i);}
  void space(){while(i<s.length()&&" \t\r\n".indexOf(s.charAt(i))>=0)i++;}
  Object value(){space();if(i>=s.length())throw error();char c=s.charAt(i);
   if(c=='{'){if(++depth>32)throw error();i++;LinkedHashMap<String,Object> m=new LinkedHashMap<>();space();if(peek('}')){depth--;return m;}
    do{space();if(i>=s.length()||s.charAt(i)!='"')throw error();String k=string();space();expect(':');m.put(k,value());space();}while(peek(','));expect('}');depth--;return m;}
   if(c=='['){if(++depth>32)throw error();i++;ArrayList<Object> l=new ArrayList<>();space();if(peek(']')){depth--;return l;}
    do{l.add(value());space();}while(peek(','));expect(']');depth--;return l;}
   if(c=='"')return string();
   if(s.startsWith("true",i)){i+=4;return Boolean.TRUE;}if(s.startsWith("false",i)){i+=5;return Boolean.FALSE;}if(s.startsWith("null",i)){i+=4;return null;}
   int start=i;while(i<s.length()&&"+-0123456789.eE".indexOf(s.charAt(i))>=0)i++;if(start==i)throw error();String n=s.substring(start,i);
   try{if(n.indexOf('.')<0&&n.indexOf('e')<0&&n.indexOf('E')<0)return Long.parseLong(n);return Double.parseDouble(n);}catch(NumberFormatException e){throw error();}
  }
  boolean peek(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
  void expect(char c){if(!peek(c))throw error();}
  String string(){expect('"');StringBuilder b=new StringBuilder();
   while(true){if(i>=s.length())throw error();char c=s.charAt(i++);if(c=='"')return b.toString();
    if(c!='\\'){b.append(c);continue;}if(i>=s.length())throw error();char e=s.charAt(i++);
    switch(e){case '"':b.append('"');break;case '\\':b.append('\\');break;case '/':b.append('/');break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;
     case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;
     case 'u':if(i+4>s.length())throw error();try{b.append((char)Integer.parseInt(s.substring(i,i+4),16));}catch(NumberFormatException x){throw error();}i+=4;break;
     default:throw error();}
   }
  }
 }
}
