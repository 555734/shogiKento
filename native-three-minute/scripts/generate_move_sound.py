"""Original synthesized wooden piece placement: no third-party audio or runtime synthesis."""
from pathlib import Path
import math,random,struct,wave,zlib
rate=24000;duration=.120;rng=random.Random(20261016);samples=[]
for i in range(round(rate*duration)):
 t=i/rate;attack=1-math.exp(-t/.00018)
 click=rng.uniform(-1,1)*math.exp(-t/.0017)*.75
 body=sum(a*math.sin(2*math.pi*f*t+p)*math.exp(-t/tau) for f,a,tau,p in [(930,.32,.015,0),(1740,.29,.011,.5),(2810,.25,.008,1),(4120,.18,.004,0),(460,.12,.021,.2)])
 grain=rng.uniform(-1,1)*math.exp(-t/.009)*.08
 fade=min(1,(duration-t)/.008);samples.append((click+body+grain)*attack*fade)
peak=max(abs(x) for x in samples);raw=b''.join(struct.pack('<h',round(x/peak*.78*32767)) for x in samples)
out=Path(__file__).resolve().parents[1]/'app/src/main/res/raw/piece_place.wav';out.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(out),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(raw)
print('Generated original placement audio',out.stat().st_size,'bytes')

# ---- Match feel: further original sounds, all synthesized here from sine partials and seeded noise. ----
def render(name,duration,voice,peak_level):
 rng=random.Random(zlib.crc32(name.encode()));data=[]
 for i in range(round(rate*duration)):
  t=i/rate;fade=min(1,(duration-t)/.012,t/.0004 if t<.0004 else 1);data.append(voice(t,rng)*fade)
 peak=max(abs(x) for x in data) or 1;raw=b''.join(struct.pack('<h',round(x/peak*peak_level*32767)) for x in data)
 path=out.parent/(name+'.wav')
 with wave.open(str(path),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(raw)
 print('Generated original',name,path.stat().st_size,'bytes')
def wood(t,rng,low=1.0,decay=1.0):
 click=rng.uniform(-1,1)*math.exp(-t/(.0022*decay))*.9
 body=sum(a*math.sin(2*math.pi*f*low*t+p)*math.exp(-t/(tau*decay)) for f,a,tau,p in [(520,.42,.024,0),(930,.34,.018,.4),(1740,.24,.011,.9),(2810,.16,.007,1.3)])
 return click+body
def partials(t,parts):return sum(a*math.sin(2*math.pi*f*t)*math.exp(-t/tau) for f,a,tau in parts)
# Capture: two hard clacks 16ms apart over a lower, longer body; louder than a placement.
render('piece_capture',.20,lambda t,r:wood(t,r,.82,1.3)+(wood(t-.016,r,.9,1.1)*.8 if t>=.016 else 0)+r.uniform(-1,1)*math.exp(-t/.02)*.12,.98)
# Promotion: a placement followed by a rising two-note chime.
render('piece_promote',.42,lambda t,r:wood(t,r)*.7+(partials(t-.05,[(880,.5,.09),(1760,.12,.05)]) if t>=.05 else 0)+(partials(t-.14,[(1318.5,.55,.12),(2637,.12,.06)]) if t>=.14 else 0),.9)
# Check: two sharp knocks and a short alert tone.
render('alert_check',.40,lambda t,r:wood(t,r,1.15,.8)+(wood(t-.11,r,1.15,.8) if t>=.11 else 0)+(partials(t-.11,[(660,.45,.12),(990,.2,.08)]) if t>=.11 else 0),.95)
# Match start: a deep drum hit with falling pitch and a bright wooden clap.
render('game_start',.75,lambda t,r:.9*math.sin(2*math.pi*(95*t-18*t*t))*math.exp(-t/.18)+r.uniform(-1,1)*math.exp(-t/.03)*.25+(wood(t-.02,r,1.3,.9)*.6 if t>=.02 else 0),.95)
# Match end: an inharmonic gong-like chord with a long decay.
render('game_end',1.6,lambda t,r:partials(t,[(180,.5,.9),(262,.4,.75),(377,.32,.6),(521,.24,.45),(703,.15,.3)])+r.uniform(-1,1)*math.exp(-t/.015)*.15,.9)
# Countdown tick for the last ten seconds.
render('clock_tick',.06,lambda t,r:partials(t,[(1500,.6,.008),(3000,.25,.004)])+r.uniform(-1,1)*math.exp(-t/.002)*.3,.55)
