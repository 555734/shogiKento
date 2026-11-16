package com.presentsoft.shogi3minutes;
import android.content.res.AssetManager;import android.graphics.Typeface;

/**
 * Bundled typefaces (SIL OFL 1.1, assets/fonts, licenses in assets/licenses): Zen Kaku Gothic New for UI text,
 * Shippori Mincho for titles, Yuji Syuku (brush) for the match banners. Subset to the app's own strings by
 * scripts/subset_fonts.py; any other character falls back to the system font.
 */
final class Fonts {
 static Typeface medium=Typeface.DEFAULT,bold=Typeface.DEFAULT_BOLD,mincho=Typeface.create(Typeface.SERIF,Typeface.BOLD),brush=mincho;
 static boolean loaded;
 static synchronized void load(AssetManager a){if(loaded)return;
  medium=face(a,"ZenKakuGothicNew-Medium.ttf",medium);bold=face(a,"ZenKakuGothicNew-Bold.ttf",bold);mincho=face(a,"ShipporiMincho-ExtraBold.ttf",mincho);brush=face(a,"YujiSyuku-Regular.ttf",brush);loaded=true;}
 private static Typeface face(AssetManager a,String file,Typeface fallback){try{return Typeface.createFromAsset(a,"fonts/"+file);}catch(RuntimeException e){return fallback;}}
}
