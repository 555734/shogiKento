package com.presentsoft.shogi3minutes;
import android.content.Context;import android.media.*;
/** A short, preloaded original wooden click. Only committed moves request playback. */
final class MoveSound implements AutoCloseable {
 final SoundPool pool;final int sample;volatile boolean ready;boolean closed;volatile int playCount,lastStream;
 MoveSound(Context context){pool=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();pool.setOnLoadCompleteListener((p,id,status)->{if(!closed)ready=status==0;});sample=pool.load(context,R.raw.piece_place,1);}
 void play(){if(closed||!ready)return;lastStream=pool.play(sample,.65f,.65f,1,0,1);if(lastStream!=0)playCount++;}
 void pause(){if(!closed)pool.autoPause();}
 @Override public void close(){if(!closed){closed=true;ready=false;pool.release();}}
}
