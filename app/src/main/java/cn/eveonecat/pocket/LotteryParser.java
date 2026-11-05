package cn.eveonecat.pocket;

import java.util.*;
import org.json.*;

/** Array snapshot or single-room delta used by bili-lottery's WebSocket. */
final class LotteryParser {
    static List<PacketNotice> parse(String text,long now)throws JSONException{
        Object data=new JSONTokener(text).nextValue();JSONArray rooms;
        if(data instanceof JSONArray)rooms=(JSONArray)data;else if(data instanceof JSONObject)rooms=new JSONArray().put(data);else throw new IllegalArgumentException("推送不是红包数据");
        if(rooms.length()>2000)throw new IllegalArgumentException("推送房间过多");List<PacketNotice> out=new ArrayList<>();Set<String> seen=new HashSet<>();int recognized=0;
        for(int i=0;i<rooms.length();i++){
            JSONObject room=rooms.optJSONObject(i);if(room==null||!room.has("room_id")||!room.has("redPocketList")&&!room.has("anchorLot"))continue;recognized++;
            String id=room.optString("room_id",""),anchor=PoolParser.shortText(room.optString("name",""));JSONArray packets=room.optJSONArray("redPocketList");if(packets==null)continue;if(packets.length()>500)throw new IllegalArgumentException("单房推送红包过多");
            for(int j=0;j<packets.length();j++){
                JSONObject p=packets.optJSONObject(j);if(p==null)continue;long price=PoolParser.number(p,"total_price",-1),seconds=PoolParser.number(p,"end_time",-1),type=PoolParser.number(p,"rp_type",-1),require=PoolParser.number(p,"join_requirement",-1);
                int amount=price>=0&&price<=99999900&&price%100==0?(int)(price/100):-1;long end=seconds>0&&seconds<Long.MAX_VALUE/1000?seconds*1000:0;
                String condition=require==1?"关注":require==2?"粉丝团":require==3?"大航海":require==0?"无门槛":"未知条件";long receive=PoolParser.number(p,"receive_type",-1);
                boolean shared=receive!=0,exclusive=!p.optString("receive_uname","").isEmpty();if(shared)condition+=" + 分享/特殊条件";if(exclusive)condition+=" + 专属领取";
                String reason=!id.matches("[1-9][0-9]{0,14}")?"房间号无效":type!=3&&type!=5?"非电池红包，不参与":shared||exclusive?"附加领取条件，不参与":require!=1&&require!=2?"仅参与关注或粉丝团条件":amount<0?"金额格式不明确":amount<=20?"20电池及以下，不参与":end<=now?"已开奖":end-now>86400000?"开奖时间异常":"";
                int count=0;JSONArray awards=p.optJSONArray("awards");if(awards!=null&&awards.length()>0){long sum=0;for(int k=0;k<awards.length();k++)if(awards.optJSONObject(k)!=null)sum+=Math.min(1000000,PoolParser.number(awards.optJSONObject(k),"num",0));count=(int)Math.min(1000000,sum);}else count=(int)Math.min(1000000,PoolParser.number(p,"win_num",0));
                long lot=PoolParser.number(p,"lot_id",0);String key="lottery:"+(lot>0?""+lot:id+":"+end+":"+amount);if(!seen.add(key))continue;
                String reward=(type==3||type==5?"电池红包":"其他红包")+" · "+(price<0?"金额未知":new java.math.BigDecimal(price).movePointLeft(2).stripTrailingZeros().toPlainString()+"电池");
                out.add(new PacketNotice(key,id,anchor,end,amount,count,condition,reward,"popularity_red_pocket",reason,p.toString()));
            }
        }
        if(recognized==0&&rooms.length()>0)throw new IllegalArgumentException("推送格式未识别；如网站要求 user/key，请到读取设置填写");return out;
    }
}
