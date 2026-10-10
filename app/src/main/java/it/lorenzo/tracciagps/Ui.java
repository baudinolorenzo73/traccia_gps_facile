package it.lorenzo.tracciagps;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
/** Shared native design tokens; no web renderer or external icon dependencies. */
public final class Ui {
    private Ui(){}
    public static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static boolean dark(Context c){return c.getSharedPreferences("ui",0).getBoolean("dark",true);}
    public static int bg(Context c){return Color.parseColor(dark(c)?"#0C171F":"#F3F7F5");}
    public static int panel(Context c){return Color.parseColor(dark(c)?"#182832":"#FFFFFF");}
    public static int text(Context c){return Color.parseColor(dark(c)?"#EFF7F4":"#183A31");}
    public static int muted(Context c){return Color.parseColor(dark(c)?"#9AB1BD":"#56766D");}
    public static int accent(Context c){return Color.parseColor(dark(c)?"#75D9B5":"#146F55");}
    public static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static void card(View v,int radius){Context c=v.getContext();v.setBackground(shape(c,panel(c),radius));v.setElevation(dp(c,2));}
    public static TextView label(Context c,String value,int size,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextColor(text(c));t.setTextSize(size);t.setFontFeatureSettings("tnum");if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    public static Button action(Context c,String value,boolean primary,Runnable run){Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setPadding(dp(c,12),0,dp(c,12),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setTextColor(primary?(dark(c)?Color.parseColor("#09281F"):Color.WHITE):text(c));int base=primary?accent(c):panel(c);StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(c,panel(c),14));states.addState(new int[]{},shape(c,base,14));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted(c),primary?(dark(c)?Color.parseColor("#09281F"):Color.WHITE):text(c)}));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x304fbc91),states,null));b.setOnClickListener(v->run.run());return b;}
    public static LinearLayout vertical(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
}
