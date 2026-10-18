"""After APK compilation, release toolchains unused by the emulator stage."""
import pathlib,shutil,sys
sdk=pathlib.Path(sys.argv[1]).resolve();assert (sdk/'platform-tools/adb').exists(),sdk
for path in [sdk/'ndk',pathlib.Path('/usr/share/dotnet')]:
 if path.is_dir() and not path.is_symlink():
  print('Releasing completed-build toolchain:',path,flush=True);shutil.rmtree(path)
print('Free disk GiB:',shutil.disk_usage('/').free/1024**3)
