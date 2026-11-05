package cn.eveonecat.pocket;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class WebReadersTest {
    static final long NOW=1793858000000L;
    JSONObject packet()throws Exception{return new JSONObject().put("lot_id",42).put("total_price",10000).put("end_time",NOW/1000+120).put("rp_type",3).put("join_requirement",1).put("receive_type",0).put("awards",new JSONArray().put(new JSONObject().put("num",5)));}
    JSONObject room(JSONObject... packets)throws Exception{return new JSONObject().put("room_id",5817906).put("name","测试主播").put("redPocketList",new JSONArray(Arrays.asList(packets)));}
    PacketNotice notice(JSONObject packet)throws Exception{return LotteryParser.parse(room(packet).toString(),NOW).get(0);}
    JSONObject schema()throws Exception{return new JSONObject(GenericJsonParser.EXAMPLE);}
    String rows(JSONObject... rows)throws Exception{return new JSONObject().put("data",new JSONObject().put("list",new JSONArray(Arrays.asList(rows)))).toString();}
    JSONObject jsonRow()throws Exception{return new JSONObject().put("id",42).put("room_id",5817906).put("uname","测试主播").put("total_price",10000).put("end_time",NOW/1000+120).put("join_requirement",1).put("award_num",5);}

    @Test public void genericAddressesAreNotForcedToLegacyPool(){
        assertEquals("https://bili-lottery.netlify.app/",PoolEndpoint.source("https://bili-lottery.netlify.app"));
        assertEquals("https://example.org/data.json",PoolEndpoint.source("https://example.org/data.json"));
        assertEquals("wss://example.org/ws",PoolEndpoint.source("wss://example.org/ws"));
        assertEquals("https://example.org/pool/api/state",PoolEndpoint.source("https://example.org/pool"));
    }
    @Test public void discoversAdvertisedWsBaseWithoutExecutingScript(){
        String js="Z=(e,t)=>{let n=new URL(e,`https://backend.example.org`.replace(/^http/,`ws`));return n.toString()};Q={ws:(e,t)=>new WebSocket(Z(`/ws`,{user:e,key:t}))}";
        assertEquals("wss://backend.example.org/ws",WsDiscovery.socket(js));
        assertEquals("wss://example.org:8443/live",WsDiscovery.socket("new WebSocket('wss://example.org:8443/live')"));
        assertNull(WsDiscovery.socket("fetch('/api/pool')"));
    }
    @Test public void scansOnlySameOriginScriptsAndRejectsAmbiguousSockets(){
        assertEquals(List.of("https://example.org/assets/main.js"),WsDiscovery.scripts("https://example.org/","<script src='/assets/main.js'></script><script src='https://foreign.example/x.js'></script><script src='http://example.org/x.js'></script>"));
        try{WsDiscovery.socket("new WebSocket('wss://a.example/ws');new WebSocket('wss://b.example/ws')");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void snapshotAndDeltaMapRealLotteryFields()throws Exception{
        PacketNotice p=notice(packet());assertTrue(p.eligible());assertEquals(100,p.amount());assertEquals(5,p.shares());assertEquals("关注",p.condition());assertEquals("5817906",p.room());assertEquals(NOW+120000,p.end());
        assertEquals(1,LotteryParser.parse(new JSONArray().put(room(packet(),packet())).toString(),NOW).size());
        assertEquals("粉丝团",notice(packet().put("rp_type",5).put("join_requirement",2)).condition());
    }
    @Test public void unsupportedPrizesAndExtraRequirementsNeverBecomeTasks()throws Exception{
        for(int type:new int[]{0,1,2,4,99})assertFalse(notice(packet().put("rp_type",type)).eligible());
        for(int condition:new int[]{0,3,99})assertFalse(notice(packet().put("join_requirement",condition)).eligible());
        assertFalse(notice(packet().put("receive_type",1)).eligible());assertFalse(notice(packet().put("receive_uname","指定用户")).eligible());
        JSONObject missing=packet();missing.remove("receive_type");assertFalse(notice(missing).eligible());
    }
    @Test public void lotteryAmountsAndDatesFailClosed()throws Exception{
        for(Object price:new Object[]{2000,2050,"NaN",99999999999L})assertFalse(notice(packet().put("total_price",price)).eligible());
        for(long end:new long[]{NOW/1000,NOW,NOW/1000+86401,Long.MAX_VALUE})assertFalse(notice(packet().put("end_time",end)).eligible());
        assertFalse(LotteryParser.parse(room(packet()).put("room_id","javascript:1").toString(),NOW).get(0).eligible());
    }
    @Test public void emptyPoolIsValidButAuthenticationOrUnknownObjectsAreNot()throws Exception{
        assertTrue(LotteryParser.parse("[]",NOW).isEmpty());assertTrue(LotteryParser.parse("{\"room_id\":123,\"anchorLot\":null}",NOW).isEmpty());
        try{LotteryParser.parse("{\"error\":\"unauthorized\"}",NOW);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void explicitJsonMappingConvertsUnitsAndDeduplicates()throws Exception{
        PacketNotice p=GenericJsonParser.parse(rows(jsonRow(),jsonRow()),schema().toString(),NOW).get(0);
        assertTrue(p.eligible());assertEquals(100,p.amount());assertEquals(NOW+120000,p.end());assertEquals(5,p.shares());
        assertEquals(1,GenericJsonParser.parse(rows(jsonRow(),jsonRow()),schema().toString(),NOW).size());
    }
    @Test public void supportsRootArraysNestedFieldsAndExplicitMilliseconds()throws Exception{
        JSONObject m=schema().put("list","$").put("room","live.id").put("amountUnit","battery").put("timeUnit","milliseconds");
        JSONObject row=jsonRow().put("live",new JSONObject().put("id",123)).put("total_price",100).put("end_time",NOW+120000);
        PacketNotice p=GenericJsonParser.parse(new JSONArray().put(row).toString(),m.toString(),NOW).get(0);assertTrue(p.eligible());assertEquals("123",p.room());assertEquals(100,p.amount());
    }
    @Test public void isoTimestampsAndNamedConditionsMustBeExplicit()throws Exception{
        JSONObject m=schema().put("timeUnit","iso").put("followValue","follow").put("fanclubValue","fanclub");
        JSONObject row=jsonRow().put("end_time",java.time.Instant.ofEpochMilli(NOW+120000).toString()).put("join_requirement","fanclub");
        assertTrue(GenericJsonParser.parse(rows(row),m.toString(),NOW).get(0).eligible());
        row.put("join_requirement","follow+pay");assertFalse(GenericJsonParser.parse(rows(row),m.toString(),NOW).get(0).eligible());
    }
    @Test public void jsonGuardsAndKindsRejectMissingOrNonmatchingValues()throws Exception{
        JSONObject m=schema().put("kind","type").put("batteryValues",new JSONArray().put(3).put(5)).put("guard","extra");
        JSONObject row=jsonRow().put("type",3).put("extra",0);assertTrue(GenericJsonParser.parse(rows(row),m.toString(),NOW).get(0).eligible());
        row.remove("extra");assertFalse(GenericJsonParser.parse(rows(row),m.toString(),NOW).get(0).eligible());
        row.put("extra",0).put("type",1);assertFalse(GenericJsonParser.parse(rows(row),m.toString(),NOW).get(0).eligible());
    }
    @Test public void jsonDoesNotRoundOrGuessUnitsOrFreshness()throws Exception{
        for(Object amount:new Object[]{2000,2050,"NaN",999999999999L})assertFalse(GenericJsonParser.parse(rows(jsonRow().put("total_price",amount)),schema().toString(),NOW).get(0).eligible());
        for(String key:new String[]{"amountUnit","timeUnit","onlyBattery"}){JSONObject m=schema();m.remove(key);try{GenericJsonParser.mapping(m.toString());fail(key);}catch(IllegalArgumentException expected){}}
        JSONObject m=schema().put("updated","updated").put("updatedUnit","seconds");JSONObject payload=new JSONObject(rows(jsonRow())).put("updated",NOW/1000-121);
        try{GenericJsonParser.parse(payload.toString(),m.toString(),NOW);fail();}catch(IllegalArgumentException expected){}
        try{GenericJsonParser.parse("{\"error\":\"bad\"}",schema().toString(),NOW);fail();}catch(IllegalArgumentException expected){}
    }
}
