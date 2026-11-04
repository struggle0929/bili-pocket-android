package cn.eveonecat.pocket;

import org.junit.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;

public class PacketParserTest {
    private static final long SENT = Instant.parse("2026-11-04T07:00:00Z").toEpochMilli();
    private String packet(int amount,String condition,String time,String room) {
        return "[电池红包] "+amount+"电池 / 10份\n条件: "+condition+"\n剩余: "+time+"\nhttps://live.bilibili.com/"+room;
    }
    @Test public void countdownUsesNotificationTimestamp() {
        List<PacketParser.Packet> out=PacketParser.parse(packet(100,"粉丝团","04分45秒","123"),SENT);
        assertEquals(1,out.size());
        assertEquals("123",out.get(0).room());
        assertEquals(SENT+285000,out.get(0).end());
        assertEquals(100,out.get(0).amount());
    }
    @Test public void onlyAllowedConditionsAreAccepted() {
        for(String condition:new String[]{"", "送礼", "关注并送礼", "粉丝团 1级"})
            assertTrue(PacketParser.parse(packet(100,condition,"1分00秒","123"),SENT).isEmpty());
        assertEquals(1,PacketParser.parse(packet(100,"关注","1分00秒","123"),SENT).size());
    }
    @Test public void twentyBatteryAndLowerAreExcluded() {
        for(int amount:new int[]{0,1,19,20})assertTrue(PacketParser.parse(packet(amount,"关注","1分00秒","123"),SENT).isEmpty());
        assertEquals(1,PacketParser.parse(packet(21,"关注","1分00秒","123"),SENT).size());
    }
    @Test public void ambiguousRoomLinksAreRejected() {
        String text=packet(100,"关注","1分00秒","123")+"\nhttps://live.bilibili.com/456";
        assertTrue(PacketParser.parse(text,SENT).isEmpty());
    }
    @Test public void multiplePacketsCanShareOneFinalRoomLink() {
        String text="[电池红包] 100电池\n条件：关注\n剩余：01分00秒\n"+
                "[电池红包] 520电池\n条件：粉丝团\n剩余：02分00秒\nhttps://live.bilibili.com/123";
        List<PacketParser.Packet> out=PacketParser.parse(text,SENT);
        assertEquals(2,out.size());assertEquals("123",out.get(0).room());assertEquals("123",out.get(1).room());
        assertEquals(SENT+120000,out.get(1).end());
    }
    @Test public void zeroCountdownAndMissingTimeAreRejected() {
        assertTrue(PacketParser.parse(packet(100,"关注","00分00秒","123"),SENT).isEmpty());
        assertTrue(PacketParser.parse(packet(100,"关注","未知","123"),SENT).isEmpty());
    }
    @Test public void clockRolloverUsesShanghaiCalendar() {
        long midnight=Instant.parse("2026-11-04T15:59:50Z").toEpochMilli();
        String text=packet(100,"关注","(00:00:10)","123");
        assertEquals(midnight+20000,PacketParser.parse(text,midnight).get(0).end());
    }
    @Test public void expiredClockDoesNotBecomeTomorrow() {
        String text=packet(100,"关注","(14:59:50)","123");
        assertTrue(PacketParser.parse(text,SENT).isEmpty());
    }
    @Test public void blacklistMatchesWholeRoomIdentifiers() {
        assertTrue(PacketParser.blacklisted("123，456\n789","456"));
        assertFalse(PacketParser.blacklisted("1234,5678","123"));
        assertFalse(PacketParser.blacklisted("","123"));
    }
}
