package cn.eveonecat.pocket;

import java.util.*;
import org.json.*;

final class WebHistory {
    static List<String> parse(String json){List<String> urls=new ArrayList<>();try{JSONArray data=new JSONArray(json);for(int i=data.length()-1;i>=0;i--){String value=data.optString(i,"");try{urls=remember(urls,value);}catch(IllegalArgumentException ignored){}}}catch(JSONException ignored){}return urls;}
    static List<String> remember(List<String> previous,String value){String endpoint=PoolEndpoint.source(value);List<String> urls=new ArrayList<>();urls.add(value.trim());for(String old:previous)if(!PoolEndpoint.source(old).equals(endpoint)&&urls.size()<20)urls.add(old);return urls;}
    static List<String> remove(List<String> previous,String value){String endpoint=PoolEndpoint.source(value);List<String> urls=new ArrayList<>();for(String old:previous)if(!PoolEndpoint.source(old).equals(endpoint))urls.add(old);return urls;}
    static String encode(List<String> urls){return new JSONArray(urls).toString();}
}
