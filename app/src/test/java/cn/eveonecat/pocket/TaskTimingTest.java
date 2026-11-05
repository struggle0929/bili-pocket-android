package cn.eveonecat.pocket;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class TaskTimingTest {
    @Test public void defaultEntryRangeAndRetentionMinimum(){
        Random r=new Random(42);boolean first=false,last=false;
        for(int i=0;i<5000;i++){TaskTiming t=TaskTiming.sample(60,10,30,10,r);assertTrue(t.beforeSeconds()>=50&&t.beforeSeconds()<=70);assertTrue(t.holdSeconds()>=30&&t.holdSeconds()<=40);first|=t.beforeSeconds()==50;last|=t.beforeSeconds()==70;}
        assertTrue(first);assertTrue(last);
    }
    @Test public void zeroVariationPreservesExactSettings(){TaskTiming t=TaskTiming.sample(120,0,15,0,new Random(3));assertEquals(120,t.beforeSeconds());assertEquals(15,t.holdSeconds());assertEquals(880000,t.enterAt(1000000));assertEquals(1015000,t.leaveAt(1000000));}
    @Test public void variationNeverCreatesNegativeOrUnboundedTimes(){for(int i=0;i<100;i++){TaskTiming t=TaskTiming.sample(-10,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,new Random(i));assertTrue(t.beforeSeconds()>=0&&t.beforeSeconds()<=86400);assertEquals(86400,t.holdSeconds());}}
    @Test public void storedPlanSurvivesRepeatedReadsAndChangedDefaults(){TaskTiming t=TaskTiming.sample(60,10,30,10,new Random(7));String stored=t.encoded();for(int i=0;i<50;i++)assertEquals(t,TaskTiming.decode(stored));assertNotEquals(t,TaskTiming.sample(500,0,500,0,new Random(7)));}
    @Test public void invalidStoredPlanIsNotUsed(){assertNull(TaskTiming.decode(""));assertNull(TaskTiming.decode("-1,30"));assertNull(TaskTiming.decode("60,86401"));assertNull(TaskTiming.decode("60,30,40"));assertNull(TaskTiming.decode("abc,30"));}
}
