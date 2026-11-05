package cn.eveonecat.pocket;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WaitingTapPlanTest {
    @Test public void waitingOnlyRequiresConfirmedMatchingVisibleCardAndStopsBeforeDraw(){
        assertTrue(WaitingTapPlan.allowed(true,true,true,true,10000,20000,10000));
        assertFalse(WaitingTapPlan.allowed(false,true,true,true,10000,20000,0));
        assertFalse(WaitingTapPlan.allowed(true,false,true,true,10000,20000,0));
        assertFalse(WaitingTapPlan.allowed(true,true,false,true,10000,20000,0));
        assertFalse(WaitingTapPlan.allowed(true,true,true,false,10000,20000,0));
        assertFalse(WaitingTapPlan.allowed(true,true,true,true,17000,20000,0));
        assertFalse(WaitingTapPlan.allowed(true,true,true,true,10000,20000,10001));
    }
    @Test public void threeToEightSeparateFingersStayWithinDecorativeRegion(){
        Set<Integer> counts=new HashSet<>();Random random=new Random(123);
        for(int i=0;i<1000;i++){
            WaitingTapPlan.Plan p=WaitingTapPlan.create(1200,2670,1560,1720,1810,10,random);assertNotNull(p);counts.add(p.points().size());
            assertTrue(p.points().size()>=3&&p.points().size()<=8);assertEquals(3,p.rounds());assertTrue(p.duration()+p.pause()<200);
            for(var point:p.points()){assertTrue(point.x()>1200*.30&&point.x()<1200*.70);assertTrue(point.y()>1626&&point.y()<1836);}
            for(int a=0;a<p.points().size();a++)for(int b=a+1;b<p.points().size();b++){
                var x=p.points().get(a);var y=p.points().get(b);assertTrue(Math.hypot(x.x()-y.x(),x.y()-y.y())>25);
            }
        }assertEquals(Set.of(3,4,5,6,7,8),counts);
    }
    @Test public void invalidLayoutOrUnsupportedMultitouchDoesNotGuessCoordinates(){
        Random random=new Random(2);
        assertNull(WaitingTapPlan.create(1200,2670,1560,1720,1810,2,random));
        assertNull(WaitingTapPlan.create(1200,2670,0,1720,1810,10,random));
        assertNull(WaitingTapPlan.create(1200,2670,1800,1720,1810,10,random));
        assertNull(WaitingTapPlan.create(1200,2670,1560,1800,1720,10,random));
        for(int i=0;i<30;i++)assertEquals(3,WaitingTapPlan.create(1200,2670,1560,1720,1810,3,random).points().size());
    }
}
