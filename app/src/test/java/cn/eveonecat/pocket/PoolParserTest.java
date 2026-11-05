package cn.eveonecat.pocket;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PoolParserTest {
    static final long NOW=1793847330000L;
    JSONObject packet()throws Exception{return new JSONObject().put("lid",123).put("room_id","26072880").put("uname","测试主播").put("kind","popularity_red_pocket").put("award_name","电池红包").put("end_time",NOW/1000+120).put("total_price",10000).put("award_num",10).put("guard",0).put("join_requirement",2).put("require_text","粉丝团");}
    JSONObject payload(JSONObject... packets)throws Exception{return new JSONObject().put("now",NOW).put("redpocket",new JSONObject().put("updated",NOW/1000).put("pop",new JSONArray(Arrays.asList(packets))).put("gift",new JSONArray()));}
    PacketNotice parse(JSONObject row)throws Exception{return PoolParser.parse(payload(row).toString(),NOW).get(0);}
    @Test public void actualPoolUnitsAndFieldsAreMapped()throws Exception{PacketNotice p=parse(packet());assertTrue(p.eligible());assertEquals(100,p.amount());assertEquals(10,p.shares());assertEquals("26072880",p.room());assertEquals("测试主播",p.anchor());assertEquals("粉丝团",p.condition());assertEquals(NOW+120000,p.end());}
    @Test public void bothAllowedRequirementsUseStructuredCode()throws Exception{assertEquals("关注",parse(packet().put("join_requirement","1").put("require_text","无")).condition());assertFalse(parse(packet().put("join_requirement",0)).eligible());assertFalse(parse(packet().put("join_requirement",3)).eligible());assertFalse(parse(packet().put("join_requirement",4294967297L)).eligible());}
    @Test public void guardsGiftsAndTwentyBatteriesAreVisibleButNotScheduled()throws Exception{assertFalse(parse(packet().put("guard",1)).eligible());assertFalse(parse(packet().put("award_name","棒棒糖")).eligible());assertFalse(parse(packet().put("total_price",2000)).eligible());assertFalse(parse(packet().put("kind","gift_anchor")).eligible());}
    @Test public void noRoundingOfFractionalOrUntrustedAmounts()throws Exception{assertFalse(parse(packet().put("total_price",2050)).eligible());assertFalse(parse(packet().put("total_price","NaN")).eligible());assertFalse(parse(packet().put("total_price",999999999999L)).eligible());}
    @Test public void invalidRoomAndExpiredTimeDoNotBecomeTasks()throws Exception{assertFalse(parse(packet().put("room_id","12javascript" )).eligible());assertFalse(parse(packet().put("end_time",NOW/1000)).eligible());assertFalse(parse(packet().put("end_time",NOW)).eligible());assertFalse(parse(packet().put("end_time",Long.MAX_VALUE)).eligible());}
    @Test public void repeatedPacketIdDoesNotDuplicateCards()throws Exception{JSONObject row=packet();assertEquals(1,PoolParser.parse(payload(row,row).toString(),NOW).size());}
    @Test public void staleCollectorOrServerIsRejected()throws Exception{JSONObject d=payload(packet());d.getJSONObject("redpocket").put("updated",NOW/1000-121);try{PoolParser.parse(d.toString(),NOW);fail();}catch(IllegalArgumentException expected){}try{PoolParser.parse(payload(packet()).toString(),NOW+121000);fail();}catch(IllegalArgumentException expected){}}
    @Test public void schemaChangeDoesNotMasqueradeAsEmptyPool()throws Exception{try{PoolParser.parse("{\"now\":"+NOW+",\"redpocket\":{}}",NOW);fail();}catch(IllegalArgumentException expected){}assertTrue(PoolParser.parse(payload().toString(),NOW).isEmpty());}
}
