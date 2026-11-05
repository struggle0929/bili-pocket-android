package cn.eveonecat.pocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoomStayTest {
    @Test public void leaveWhenRetentionExpiresEvenWithoutAnotherTask(){RoomStay stay=new RoomStay(0);stay.include(100000,new TaskTiming(60,35));assertFalse(stay.due(134999));assertTrue(stay.due(135000));}
    @Test public void roomUsesLatestRetentionAcrossMultipleDraws(){RoomStay stay=new RoomStay(0);stay.include(100000,new TaskTiming(60,40));stay.include(110000,new TaskTiming(60,30));stay.include(90000,new TaskTiming(60,30));assertEquals(140000,stay.until());stay.include(140000,new TaskTiming(60,35));assertEquals(175000,stay.until());}
    @Test public void restartPreservesHoldButConfirmedExitClearsIt(){RoomStay restored=new RoomStay(175000);assertFalse(restored.due(160000));restored.clear();assertEquals(0,restored.until());assertTrue(restored.due(160000));}
}
