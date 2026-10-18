package com.presentsoft.shogi3minutes;
public final class MatchClock {
  private final ShogiGame game;private long anchor;private boolean running;
  public MatchClock(ShogiGame game){this.game=game;}
  public void resume(long now,boolean started){anchor=now;running=started;}
  public void settle(long now){if(running)game.tick(Math.max(0,now-anchor));anchor=now;}
  public void pause(long now){settle(now);running=false;}
}
