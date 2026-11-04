package cn.eveonecat.pocket;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class PacketParserRobustnessTest {
    private final long sent=Instant.parse("2026-11-04T07:00:00Z").toEpochMilli();
    private String message(String amount,String time){return "[电池红包] "+amount+"电池 / 10份\n条件: 关注\n剩余: "+time+"\nhttps://live.bilibili.com/123";}
    @Test public void hugeAmountDoesNotThrowOrPartiallyParse(){assertTrue(PacketParser.parse(message("999999999999999999999","1分00秒"),sent).isEmpty());}
    @Test public void invalidCountdownDoesNotThrow(){assertTrue(PacketParser.parse(message("100","999999999999999分99秒"),sent).isEmpty());assertTrue(PacketParser.parse(message("100","1分60秒"),sent).isEmpty());}
    @Test public void botClockPreventsSummaryNotificationPostponingEnd(){
        String m=message("100","1分00秒(15:01:00)");
        assertEquals(sent+60000,PacketParser.parse(m,sent+10000).get(0).end());
    }
    @Test public void inconsistentClockAndCountdownAreRejected(){assertTrue(PacketParser.parse(message("100","1分00秒(16:00:00)"),sent).isEmpty());}
    @Test public void expiredExplicitClockDoesNotBecomeFuture(){assertTrue(PacketParser.parse(message("100","1分00秒(14:59:50)"),sent).isEmpty());}
    @Test public void nullAndMalformedInputAreSafe(){assertTrue(PacketParser.parse(null,sent).isEmpty());assertTrue(PacketParser.parse(message("100","(99:99:99)"),sent).isEmpty());}
}
