"""Dismiss only an observed emulator System UI cold-boot ANR; capture must stay strict."""
import pathlib,subprocess,time,xml.etree.ElementTree as ET,re
time.sleep(12)
for attempt in range(3):
 try:
  subprocess.run(['adb','shell','uiautomator','dump','/sdcard/preflight.xml'],check=True,timeout=18,capture_output=True)
  xml=subprocess.check_output(['adb','shell','cat','/sdcard/preflight.xml'],text=True,timeout=5)
 except (subprocess.SubprocessError,OSError):time.sleep(3);continue
 if "System UI isn't responding" not in xml:break
 root=ET.fromstring(xml)
 for node in root.iter('node'):
  if node.attrib.get('text')=='Wait':
   x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
   subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
   print('Emulator preflight: selected Wait on observed cold-boot System UI dialog.')
 time.sleep(5)
print('Emulator preflight complete; instrumentation screenshots require the app to own the active window.')
