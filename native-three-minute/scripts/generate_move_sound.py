"""Original synthesized wooden piece placement: no third-party audio or runtime synthesis."""
from pathlib import Path
import math,random,struct,wave
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
