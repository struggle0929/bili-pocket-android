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
    long l(String key,long def){try{return Long.parseLong(s(key,""+def));}catch(Exception e){return def;}}
    long until(){return l("sleepUntil",0);}
    TaskTiming taskTiming(long id){
        String key="taskTiming."+id;TaskTiming timing=TaskTiming.decode(s(key,""));
        if(timing==null){timing=TaskTiming.sample(n("before",60),n("beforeJitter",10),n("after",30),n("afterExtra",10),new java.util.Random());put(key,timing.encoded());}
        return timing;
    }
    void clearLosses(){p.edit().putString("losses","0").putString("sleepUntil","0").apply();}
    boolean webMode(){return s("sourceMode","qq").equals("web");}
    String sourceKey(){return webMode()?"web:"+PoolEndpoint.api(s("webUrl",PoolEndpoint.DEFAULT)):"qq";}
    String sourceName(){if(!webMode())return "QQ · "+s("group","B站直播区游戏群");try{return "网页 · "+java.net.URI.create(PoolEndpoint.api(s("webUrl",PoolEndpoint.DEFAULT))).getAuthority();}catch(RuntimeException e){return "网页 · 地址待检查";}}
    java.util.List<String> webHistory(){if(!p.contains("webHistory")){java.util.List<String> seed=java.util.List.of(s("webUrl",PoolEndpoint.DEFAULT));put("webHistory",WebHistory.encode(seed));}return WebHistory.parse(s("webHistory","[]"));}
    void rememberWebUrl(String value){put("webHistory",WebHistory.encode(WebHistory.remember(webHistory(),value)));}
    void removeWebHistory(String value){put("webHistory",WebHistory.encode(WebHistory.remove(webHistory(),value)));}
}
