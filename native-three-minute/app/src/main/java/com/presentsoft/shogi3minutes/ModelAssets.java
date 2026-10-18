package com.presentsoft.shogi3minutes;
import android.content.Context;import org.json.*;import java.io.*;import java.security.*;import java.util.*;
/** Installs both progress-aware model files and retires obsolete private copies. */
final class ModelAssets {
 static JSONObject provenance(Context c)throws Exception{try(InputStream in=c.getAssets().open("ai/provenance.json");ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] a=new byte[4096];int n;while((n=in.read(a))!=-1)b.write(a,0,n);return new JSONObject(b.toString("UTF-8"));}}
 static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
 static File prepare(Context c)throws Exception{
  JSONObject m=provenance(c).getJSONObject("model"),files=m.getJSONObject("files");String model=m.getString("runtime_dir");
  if(!model.equals("tsuyou11micro"))throw new IOException("Unexpected model path");File root=new File(c.getFilesDir(),"ai"),dir=new File(root,model);
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("AI保存先を作成できません");
  for(String name:new String[]{"nn.bin","progress.bin","eval_options.txt","tag.json"}){JSONObject expected=files.getJSONObject(name);File data=new File(dir,name),temp=new File(dir,name+".tmp");
   if(data.length()!=expected.getLong("bytes")||!sha(data).equals(expected.getString("sha256"))){
    try(InputStream in=c.getAssets().open("ai/"+model+"/"+name);FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
    if(temp.length()!=expected.getLong("bytes")||!sha(temp).equals(expected.getString("sha256"))){temp.delete();throw new IOException("AIデータの検証に失敗: "+name);}
    if(data.exists()&&!data.delete()||!temp.renameTo(data))throw new IOException("AIデータを保存できません: "+name);
   }else if(temp.exists()&&!temp.delete())throw new IOException("AI一時ファイルを削除できません");
  }
  for(String name:new String[]{"suisho5","full","light"})remove(new File(root,name));return dir;
 }
 static void remove(File f)throws IOException{if(!f.exists())return;File[] children=f.listFiles();if(children!=null)for(File x:children)remove(x);if(!f.delete())throw new IOException("旧AIファイルを削除できません: "+f.getName());}
}
