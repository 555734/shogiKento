package com.presentsoft.shogi3minutes;
import android.content.Context;import android.media.*;
/** Short, preloaded original sounds (scripts/generate_move_sound.py). Only committed moves and game events request playback. */
final class MoveSound implements AutoCloseable {
 /** Placement, capture, promotion, check, match start, match end and the last-ten-seconds tick. */
 enum Kind {PLACE(R.raw.piece_place),CAPTURE(R.raw.piece_capture),PROMOTE(R.raw.piece_promote),CHECK(R.raw.alert_check),START(R.raw.game_start),END(R.raw.game_end),TICK(R.raw.clock_tick);
  final int res;Kind(int res){this.res=res;}}
 final AppSettings settings;float lastVolume;final SoundPool pool;final int[] samples=new int[Kind.values().length];final boolean[] loaded=new boolean[Kind.values().length];
 volatile boolean ready;boolean closed;volatile int playCount,lastStream;volatile Kind lastKind;final int[] kindCounts=new int[Kind.values().length];
 MoveSound(Context context){settings=new AppSettings(context);pool=new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
  pool.setOnLoadCompleteListener((p,id,status)->{if(closed||status!=0)return;for(int i=0;i<samples.length;i++)if(samples[i]==id)loaded[i]=true;ready=loaded[Kind.PLACE.ordinal()];});
  for(Kind k:Kind.values())samples[k.ordinal()]=pool.load(context,k.res,1);}
 void play(){play(Kind.PLACE);}
 void play(Kind kind){if(closed||!loaded[kind.ordinal()]||!settings.sound()||settings.volume()==0)return;lastVolume=settings.volume()/100f;lastStream=pool.play(samples[kind.ordinal()],lastVolume,lastVolume,1,0,1);if(lastStream!=0){playCount++;kindCounts[kind.ordinal()]++;lastKind=kind;}}
 void pause(){if(!closed)pool.autoPause();}
 @Override public void close(){if(!closed){closed=true;ready=false;pool.release();}}
}
