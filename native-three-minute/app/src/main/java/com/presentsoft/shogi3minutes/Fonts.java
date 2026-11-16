package com.presentsoft.shogi3minutes;
import android.content.res.AssetManager;import android.graphics.Typeface;

/**
 * The app's one typeface family, Zen Kaku Gothic New (SIL OFL 1.1, assets/fonts, license in assets/licenses):
 * Medium for text, Bold for labels, Black for titles, big buttons and the match banners. Subset to the app's own
 * strings by scripts/subset_fonts.py; any other character falls back to the system font.
 */
final class Fonts {
 static Typeface medium=Typeface.DEFAULT,bold=Typeface.DEFAULT_BOLD,black=Typeface.DEFAULT_BOLD;
 static boolean loaded;
 static synchronized void load(AssetManager a){if(loaded)return;
  medium=face(a,"ZenKakuGothicNew-Medium.ttf",medium);bold=face(a,"ZenKakuGothicNew-Bold.ttf",bold);black=face(a,"ZenKakuGothicNew-Black.ttf",black);loaded=true;}
 private static Typeface face(AssetManager a,String file,Typeface fallback){try{return Typeface.createFromAsset(a,"fonts/"+file);}catch(RuntimeException e){return fallback;}}
}
