package cn.eveonecat.pocket;

import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.json.*;

/** Explicit field/unit mapping, rather than guessing prize values or timestamps. */
final class GenericJsonParser {
    static final String EXAMPLE="{\n  \"list\": \"data.list\",\n  \"room\": \"room_id\",\n  \"anchor\": \"uname\",\n  \"amount\": \"total_price\",\n  \"amountUnit\": \"gold\",\n  \"end\": \"end_time\",\n  \"timeUnit\": \"seconds\",\n  \"condition\": \"join_requirement\",\n  \"followValue\": \"1\",\n  \"fanclubValue\": \"2\",\n  \"onlyBattery\": true,\n  \"id\": \"id\",\n  \"shares\": \"award_num\"\n}";
    static JSONObject mapping(String json)throws JSONException {
        if(json.length()>8192)throw new IllegalArgumentException("字段映射过长");JSONObject m=new JSONObject(json);
        for(String name:new String[]{"list","room","amount","end","condition"})if(m.optString(name,"").trim().isEmpty())throw new IllegalArgumentException("字段映射缺少 "+name);
        if(!List.of("gold","battery").contains(m.optString("amountUnit")))throw new IllegalArgumentException("amountUnit 必须指定 gold（金瓜子）或 battery（电池）");
        if(!List.of("seconds","milliseconds","iso").contains(m.optString("timeUnit")))throw new IllegalArgumentException("timeUnit 必须指定 seconds、milliseconds 或 iso");
        if(m.optString("kind","").isEmpty()&&!m.optBoolean("onlyBattery",false))throw new IllegalArgumentException("请映射 kind，或明确接口仅含电池红包：onlyBattery=true");
        if(m.optString("followValue","关注").equals(m.optString("fanclubValue","粉丝团")))throw new IllegalArgumentException("关注和粉丝团条件值不能相同");return m;
    }
    static List<PacketNotice> parse(String json,String schema,long now)throws JSONException {
        return TargetRule.defaults().apply(parseData(json,schema,now));
    }
    static List<PacketNotice> parseData(String json,String schema,long now)throws JSONException {
        JSONObject m=mapping(schema);Object root=new JSONTokener(json).nextValue(),data=path(root,m.getString("list"));
        if(!(data instanceof JSONArray))throw new IllegalArgumentException("JSON 列表路径未找到，请检查 list 映射");JSONArray rows=(JSONArray)data;if(rows.length()>1000)throw new IllegalArgumentException("接口条目过多");
        String updated=m.optString("updated","");if(!updated.isEmpty()){long time=timestamp(value(root,updated),m.optString("updatedUnit",m.getString("timeUnit")));if(time<=0||now-time>120000||time-now>30000)throw new IllegalArgumentException("接口更新时间过旧或异常");}
        List<PacketNotice> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;String room=value(row,m.getString("room")),rawCondition=value(row,m.getString("condition"));long end=timestamp(value(row,m.getString("end")),m.getString("timeUnit"));int amount=amount(value(row,m.getString("amount")),m.getString("amountUnit"));
            String condition=rawCondition.equals(m.optString("followValue","关注"))?"关注":rawCondition.equals(m.optString("fanclubValue","粉丝团"))?"粉丝团":PoolParser.shortText(rawCondition.isEmpty()?"未知条件":rawCondition);
            boolean battery=m.optString("kind","").isEmpty()?m.optBoolean("onlyBattery",false):matches(value(row,m.optString("kind")),m);
            String guard=m.optString("guard","");boolean blocked=!guard.isEmpty()&&!value(row,guard).equals(m.optString("guardAllowed","0"));
            String reason=!room.matches("[1-9][0-9]{0,14}")?"房间号无效":!battery?"非电池红包或类型不明确":blocked?"附加领取条件，不参与":!condition.equals("关注")&&!condition.equals("粉丝团")?"仅参与关注或粉丝团条件":amount<0?"金额格式不明确":end<=now?"已开奖或时间无效":end-now>86400000?"开奖时间异常":"";
            String external=value(row,m.optString("id",""));String key="json:"+(external.isEmpty()?room+":"+end+":"+amount+":"+condition:PoolParser.shortText(external));if(!seen.add(key))continue;
            int shares=amount(value(row,m.optString("shares","")),"battery");out.add(new PacketNotice(key,room,PoolParser.shortText(value(row,m.optString("anchor",""))),end,amount,Math.max(0,shares),condition,(battery?"电池红包":"其他红包")+" · "+(amount<0?"金额未知":amount+"电池"),"popularity_red_pocket",reason,row.toString()));
        }return out;
    }
    static boolean matches(String value,JSONObject mapping){JSONArray options=mapping.optJSONArray("batteryValues");if(options!=null){for(int i=0;i<options.length();i++)if(value.equals(options.optString(i)))return true;return false;}return value.equals(mapping.optString("batteryValue","battery"));}
    static Object path(Object root,String path){if(path.equals("$"))return root;if(path.isEmpty())return null;if(path.startsWith("$."))path=path.substring(2);Object result=root;for(String key:path.split("\\.")){if(result instanceof JSONObject)result=((JSONObject)result).opt(key);else if(result instanceof JSONArray&&key.matches("[0-9]{1,4}"))result=((JSONArray)result).opt(Integer.parseInt(key));else return null;}return result==JSONObject.NULL?null:result;}
    static String value(Object root,String path){Object v=path(root,path);return v==null||v instanceof JSONObject||v instanceof JSONArray?"":v.toString();}
    static int amount(String text,String unit){try{if(text.length()>40)return -1;BigDecimal n=new BigDecimal(text);if(unit.equals("gold"))n=n.movePointLeft(2);int value=n.intValueExact();return value>=0&&value<=999999?value:-1;}catch(RuntimeException e){return -1;}}
    static long timestamp(String text,String unit){try{if(unit.equals("iso"))return Instant.parse(text).toEpochMilli();if(!text.matches("[0-9]{1,18}"))return 0;long n=Long.parseLong(text);return unit.equals("seconds")&&n<Long.MAX_VALUE/1000?n*1000:unit.equals("milliseconds")?n:0;}catch(RuntimeException e){return 0;}}
}
