package com.presentsoft.shogi3minutes;
import java.io.*;import java.util.*;

/** App-private, one file per game. A backup survives an interrupted replacement. */
final class RecordStore {
 private final File directory;private final Map<String,String> signatures=new HashMap<>();
 int unreadable;
 RecordStore(File directory){this.directory=directory;}
 private void prepare()throws IOException{if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("棋譜の保存先を作成できません");File[] backups=directory.listFiles((d,n)->n.endsWith(".bak"));if(backups!=null)for(File backup:backups){File target=new File(directory,backup.getName().substring(0,backup.getName().length()-4));if(target.exists())backup.delete();else if(!backup.renameTo(target))throw new IOException("棋譜を復旧できません");}}
 synchronized void save(SavedRecord record)throws IOException{
  record.validate();prepare();String signature=record.signature();File target=new File(directory,record.id+".record");
  if(signature.equals(signatures.get(record.id))&&target.isFile())return;
  if(target.isFile()){try{SavedRecord old=read(record.id);if(signature.equals(old.signature())){signatures.put(record.id,signature);return;}}catch(IOException ignored){}}
  File temp=new File(directory,record.id+".tmp"),backup=new File(directory,record.id+".record.bak");
  try(FileOutputStream stream=new FileOutputStream(temp);ObjectOutputStream out=new ObjectOutputStream(stream)){out.writeObject(record);out.flush();stream.getFD().sync();}
  if(target.exists()&&!target.renameTo(backup)){temp.delete();throw new IOException("以前の棋譜を保護できません");}
  if(!temp.renameTo(target)){if(backup.exists())backup.renameTo(target);throw new IOException("棋譜を書き込めません");}
  backup.delete();signatures.put(record.id,signature);
 }
 synchronized SavedRecord read(String id)throws IOException{
  if(id==null||!id.matches("[a-zA-Z0-9-]{1,100}"))throw new IOException("棋譜のIDが不正です");prepare();File file=new File(directory,id+".record");
  if(file.length()>8*1024*1024)throw new IOException("棋譜ファイルが大きすぎます");
  try(ObjectInputStream in=new ObjectInputStream(new FileInputStream(file))){SavedRecord record=(SavedRecord)in.readObject();record.validate();if(!id.equals(record.id))throw new IOException("棋譜のIDが一致しません");return record;}catch(ClassNotFoundException|RuntimeException e){throw new IOException("保存された棋譜を読み込めません",e);}
 }
 synchronized List<SavedRecord> list()throws IOException{prepare();unreadable=0;ArrayList<SavedRecord> records=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".record"));if(files==null)throw new IOException("棋譜の一覧を読み込めません");for(File file:files)try{records.add(read(file.getName().substring(0,file.getName().length()-7)));}catch(IOException e){unreadable++;}records.sort((a,b)->Long.compare(b.updated,a.updated));return records;}
}
