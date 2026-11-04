package cn.eveonecat.pocket;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DualPlanTest {
    @Test public void closeDrawsUseBackupWithoutLeavingPrimaryEarly(){
        var p=DualPlan.calculate(List.of(new DualPlan.Draw(1,"a",100000),new DualPlan.Draw(2,"b",110000)),0,60000,30000,true);
        assertEquals(0,p.get(0).account());assertEquals(1,p.get(1).account());assertEquals(130000,p.get(0).leave());
    }
    @Test public void allKnownDrawsInRoomHoldUntilLastPlusRetention(){
        var p=DualPlan.calculate(List.of(new DualPlan.Draw(1,"a",100000),new DualPlan.Draw(2,"a",200000),new DualPlan.Draw(3,"b",180000)),0,60000,30000,true);
        assertEquals(2,p.size());assertEquals(List.of(1L,2L),p.get(0).tasks());assertEquals(230000,p.get(0).leave());assertEquals(1,p.get(1).account());
    }
    @Test public void releasedPrimaryIsPreferredOverBackup(){
        var p=DualPlan.calculate(List.of(new DualPlan.Draw(1,"a",100000),new DualPlan.Draw(2,"b",200000)),0,60000,30000,true);
        assertEquals(0,p.get(1).account());
    }
    @Test public void disabledBackupReportsConflictAndDoesNotPretendConcurrent(){
        var p=DualPlan.calculate(List.of(new DualPlan.Draw(1,"a",100000),new DualPlan.Draw(2,"b",110000)),0,60000,30000,false);
        assertEquals(-1,p.get(1).account());assertTrue(p.get(1).state().contains("无空闲"));
    }
    @Test public void thirdRoomWaitsOnlyIfItCanStillEnterBeforeDraw(){
        var p=DualPlan.calculate(List.of(new DualPlan.Draw(1,"a",100000),new DualPlan.Draw(2,"b",110000),new DualPlan.Draw(3,"c",140000)),0,60000,30000,true);
        assertEquals(0,p.get(2).account());assertEquals(130000,p.get(2).enter());assertTrue(p.get(2).state().contains("提前量不足"));
    }
}
