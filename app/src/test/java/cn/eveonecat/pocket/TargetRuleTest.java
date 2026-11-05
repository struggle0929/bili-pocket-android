package cn.eveonecat.pocket;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class TargetRuleTest {
    static final long NOW=1793858000000L;
    PacketNotice first(List<PacketNotice> rows,TargetRule rule){return rule.apply(rows).get(0);}
    @Test public void defaultsStillExcludeTwentyAndInvalidAmounts(){TargetRule rule=TargetRule.defaults();for(int amount:new int[]{-1,0,1,19,20})assertFalse(rule.reason(amount,5).isEmpty());assertTrue(rule.reason(21,0).isEmpty());}
    @Test public void totalThresholdIsInclusiveAndReplacesOldTwentyLimit(){TargetRule rule=TargetRule.of("total","20");assertTrue(rule.reason(20,0).isEmpty());assertFalse(rule.reason(19,5).isEmpty());assertTrue(rule.reason(100,5).isEmpty());assertFalse(TargetRule.of("total","100.01").reason(100,5).isEmpty());assertTrue(TargetRule.of("total","0").reason(1,0).isEmpty());}
    @Test public void averageThresholdUsesSharesAndExactDecimalComparison(){TargetRule rule=TargetRule.of("share","20");assertTrue(rule.reason(100,5).isEmpty());assertFalse(rule.reason(100,10).isEmpty());assertTrue(rule.reason(20,1).isEmpty());assertTrue(TargetRule.of("share","0.333333").reason(1,3).isEmpty());assertFalse(TargetRule.of("share","0.333334").reason(1,3).isEmpty());assertFalse(TargetRule.of("share","3.333334").reason(10,3).isEmpty());assertFalse(TargetRule.of("share","0").reason(100,0).isEmpty());}
    @Test public void malformedOrNonfiniteThresholdDoesNotEnableParticipation(){for(String value:new String[]{"","-1","NaN","Infinity","1e3","999999.1","1.1234567"})try{TargetRule.of("share",value);fail(value);}catch(IllegalArgumentException expected){}assertFalse(TargetRule.invalid().reason(520,5).isEmpty());assertEquals("20 电池/份",TargetRule.average(100,5));assertTrue(TargetRule.average(10,3).startsWith("约"));assertEquals("待确认",TargetRule.average(100,0));}
    @Test public void qqSharesSupportCustomRuleWithoutDiscardingLowValueCards(){
        String text="[电池红包] 20电池 / 1份\n条件: 关注\n剩余: 1分00秒\n主播 12345\nhttps://live.bilibili.com/12345";
        List<PacketNotice> data=NoticeParser.parseData(text,NOW);assertEquals(1,data.get(0).shares());assertTrue(first(data,TargetRule.of("share","20")).eligible());assertFalse(first(data,TargetRule.defaults()).eligible());
        List<PacketNotice> invalid=NoticeParser.parseData(text.replace("剩余: 1分00秒","剩余: 未知"),NOW);assertFalse(first(invalid,TargetRule.of("total","1")).eligible());
    }
    @Test public void poolSharesAndPrizeSafetyArePreserved()throws Exception{
        PoolParserTest fixture=new PoolParserTest();JSONObject packet=fixture.packet().put("total_price",2000).put("award_num",1);String payload=fixture.payload(packet).toString();
        assertTrue(first(PoolParser.parseData(payload,PoolParserTest.NOW),TargetRule.of("share","20")).eligible());
        packet.put("guard",1);assertFalse(first(PoolParser.parseData(fixture.payload(packet).toString(),PoolParserTest.NOW),TargetRule.of("total","1")).eligible());
    }
    @Test public void websocketSharesAndSpecialRequirementsRemainAuthoritative()throws Exception{
        WebReadersTest fixture=new WebReadersTest();JSONObject packet=fixture.packet().put("total_price",2000).put("awards",new JSONArray().put(new JSONObject().put("num",1)));
        assertTrue(first(LotteryParser.parseData(fixture.room(packet).toString(),WebReadersTest.NOW),TargetRule.of("share","20")).eligible());
        packet.put("receive_type",1);assertFalse(first(LotteryParser.parseData(fixture.room(packet).toString(),WebReadersTest.NOW),TargetRule.of("total","1")).eligible());
    }
    @Test public void customJsonUsesMappedSharesAndDoesNotRelaxConditions()throws Exception{
        WebReadersTest fixture=new WebReadersTest();JSONObject row=fixture.jsonRow().put("total_price",2000).put("award_num",1);String schema=fixture.schema().toString();
        assertTrue(first(GenericJsonParser.parseData(fixture.rows(row),schema,WebReadersTest.NOW),TargetRule.of("share","20")).eligible());
        row.put("join_requirement",3);assertFalse(first(GenericJsonParser.parseData(fixture.rows(row),schema,WebReadersTest.NOW),TargetRule.of("total","0")).eligible());
    }
    @Test public void foregroundIdentityMatchingNoLongerSilentlyRejectsCustomTwenty(){assertTrue(ClaimDecision.matchingCard(20,20,-1,NOW+60000,NOW));assertFalse(ClaimDecision.matchingCard(100,20,-1,NOW+60000,NOW));assertFalse(ClaimDecision.matchingCard(0,0,-1,NOW+60000,NOW));}
    @Test public void migrationOnlyRemovesLegacyAmountFilterNotStructuralReasons(){assertEquals("",TargetRule.legacyBase("20电池及以下，不参与"));for(String reason:List.of("非电池红包，不参与","需要上舰，不参与","通知时间过旧或异常","黑名单房间，不参与"))assertEquals(reason,TargetRule.legacyBase(reason));}
}
