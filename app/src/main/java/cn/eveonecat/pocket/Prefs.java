package cn.eveonecat.pocket;
import android.content.*;
public final class Prefs {
    final android.content.SharedPreferences p;
    Prefs(Context c){p=c.getSharedPreferences("pocket",0);}
    String s(String k,String d){return p.getString(k,d);}
    int n(String k,int d){try{return Integer.parseInt(s(k,""+d));}catch(Exception e){return d;}}
    boolean b(String k,boolean d){return p.getBoolean(k,d);}
    void put(String k,String v){p.edit().putString(k,v).apply();}
    void put(String k,boolean v){p.edit().putBoolean(k,v).apply();}
    long until(){try{return Long.parseLong(s("sleepUntil","0"));}catch(Exception e){return 0;}}
}
