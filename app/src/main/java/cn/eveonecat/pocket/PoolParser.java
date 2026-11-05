package cn.eveonecat.pocket;

import org.json.*;
import java.util.*;

/** Adapter for the pool page's observed /pool/api/state schema. */
final class PoolParser {
    static List<PacketNotice> parse(String json,long now)throws JSONException {
        if(json.length()>2*1024*1024)throw new IllegalArgumentException("网页数据过大");
        JSONObject root=new JSONObject(json),pool=root.optJSONObject("redpocket");
        long server=number(root,"now",-1);
        if(pool==null||!(pool.opt("pop") instanceof JSONArray)||!(pool.opt("gift") instanceof JSONArray))throw new IllegalArgumentException("网页数据格式已变化，未导入任务");
        long updated=number(pool,"updated",-1);
        if(server<0||Math.abs(now-server)>120000||updated<0||updated>Long.MAX_VALUE/1000||now-updated*1000>120000||updated*1000-now>30000)throw new IllegalArgumentException("网页数据过期或时间异常，未导入任务");
        List<PacketNotice> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String section:new String[]{"pop","gift"}){
            JSONArray rows=pool.getJSONArray(section);if(rows.length()>1000)throw new IllegalArgumentException("网页红包条目过多");
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.optJSONObject(i);if(r==null)continue;
                String room=shortText(r.optString("room_id","")),kind=shortText(r.optString("kind","")),award=shortText(r.optString("award_name",""));
                long seconds=number(r,"end_time",-1),end=seconds>0&&seconds<Long.MAX_VALUE/1000?seconds*1000:0,total=number(r,"total_price",-1);
                long requirement=number(r,"join_requirement",-1);
                int amount=total>=0&&total<=99999900&&total%100==0?(int)(total/100):-1,require=requirement>=0&&requirement<=3?(int)requirement:-1;
                String condition=require==1?"关注":require==2?"粉丝团":require==0?"无门槛":shortText(r.optString("require_text","未知条件"));
                long guard=r.isNull("guard")?0:number(r,"guard",-1);if(guard!=0)condition="上舰/大航海";
                String reason="";
                if(!room.matches("[1-9][0-9]{0,14}"))reason="房间号无效";
                else if(!section.equals("pop")||!kind.equals("popularity_red_pocket")||!award.equals("电池红包"))reason="非电池红包，不参与";
                else if(guard!=0)reason="需要上舰，不参与";
                else if(require!=1&&require!=2)reason="仅参与关注或粉丝团条件";
                else if(total<0||total>99999900||amount<0)reason="金额格式不明确，不参与";
                else if(amount<=20)reason="20电池及以下，不参与";
                else if(end<=now)reason="已开奖";
                else if(end-now>86400000)reason="开奖时间异常";
                long lid=number(r,"lid",0);String key=kind+":"+(lid>0?""+lid:room+":"+end+":"+award);
                if(section.equals("gift"))amount=total>=0&&total<=999999?(int)total:-1;
                String value=total<0?"金额未知":new java.math.BigDecimal(total).movePointLeft(2).stripTrailingZeros().toPlainString()+"电池";
                if(section.equals("gift"))value=total<0?"金额未知":total+"电池";
                if(seen.add(key))out.add(new PacketNotice(key,room,shortText(r.optString("uname","")),end,amount,(int)Math.max(0,Math.min(1000000,number(r,"award_num",0))),condition,award+" · "+value,kind,reason,r.toString()));
            }
        }return out;
    }
    static String shortText(String value){return value.length()>180?value.substring(0,180):value;}
    static long number(JSONObject object,String key,long fallback){try{Object v=object.opt(key);if(v==null)return fallback;String s=v.toString();return s.matches("[0-9]{1,18}")?Long.parseLong(s):fallback;}catch(RuntimeException e){return fallback;}}
}
