package cn.eveonecat.pocket;

import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoticeParserTest {
    final long sent=Instant.parse("2026-11-05T01:00:00Z").toEpochMilli();
    String msg(String amount,String condition){return "[电池红包] "+amount+"电池 / 10份\n条件: "+condition+"\n剩余: 01分00秒(09:01:00)\n主播名字 12345\nhttps://live.bilibili.com/12345";}
    @Test public void acceptedQqPacketBecomesStructuredCard(){PacketNotice p=NoticeParser.parse("包bot: "+msg("100","关注"),sent).get(0);assertTrue(p.eligible());assertEquals("12345",p.room());assertEquals("主播名字",p.anchor());assertEquals(10,p.shares());assertEquals(sent+60000,p.end());}
    @Test public void separatePacketsKeepTheirOwnConditionAndAmount(){List<PacketNotice> p=NoticeParser.parse(msg("100","关注")+"\n"+msg("520","粉丝团"),sent);assertEquals(2,p.size());assertEquals(100,p.get(0).amount());assertEquals(520,p.get(1).amount());assertEquals("粉丝团",p.get(1).condition());assertTrue(p.get(0).eligible());assertTrue(p.get(1).eligible());}
    @Test public void filteredPacketsRemainVisibleWithReasons(){PacketNotice twenty=NoticeParser.parse(msg("20","关注"),sent).get(0),special=NoticeParser.parse(msg("100","粉丝牌[10级]"),sent).get(0);assertFalse(twenty.eligible());assertTrue(twenty.reason().contains("20"));assertFalse(special.eligible());assertEquals("粉丝牌[10级]",special.condition());}
    @Test public void ambiguousRoomNeverEnqueues(){PacketNotice p=NoticeParser.parse(msg("100","关注")+"\nhttps://live.bilibili.com/67890",sent).get(0);assertFalse(p.eligible());assertTrue(p.room().isEmpty());}
    @Test public void truncatedUrlStillUsesValidatedExplicitRoomLine(){PacketNotice p=NoticeParser.parse(msg("100","关注").replace("https://live.bilibili.com/12345","https://live.bili"),sent).get(0);assertTrue(p.eligible());assertEquals("12345",p.room());}
    @Test public void nonPacketTextIsNotInventedIntoACard(){assertTrue(NoticeParser.parse("你好，今天有直播",sent).isEmpty());assertTrue(NoticeParser.parse(null,sent).isEmpty());}
}
