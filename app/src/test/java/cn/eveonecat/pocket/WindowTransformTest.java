package cn.eveonecat.pocket;
import org.junit.Test;
import static org.junit.Assert.*;

public class WindowTransformTest {
    @Test public void expandedHyperOsClippedRootUsesUniformHeightScale(){
        var b=WindowTransform.map(new WindowTransform.Box(20,164,1200,2084),new WindowTransform.Box(20,164,860,1508),new WindowTransform.Box(230,317,894,415));
        assertEquals(new WindowTransform.Box(167,271,632,340),b);
    }
    @Test public void rightMiniWindowDoesNotTapBeyondDisplay(){
        var b=WindowTransform.map(new WindowTransform.Box(856,164,1200,2084),new WindowTransform.Box(856,164,1156,644),new WindowTransform.Box(956,364,1156,564));
        assertEquals(new WindowTransform.Box(881,214,931,264),b);
    }
    @Test public void alreadyPhysicalNodesRemainUnchanged(){var box=new WindowTransform.Box(20,164,860,1508);assertEquals(new WindowTransform.Box(50,500,300,600),WindowTransform.map(box,box,new WindowTransform.Box(50,500,300,600)));}
    @Test(expected=IllegalArgumentException.class) public void emptyRootCannotProduceTap(){WindowTransform.map(new WindowTransform.Box(0,0,0,0),new WindowTransform.Box(0,0,10,10),new WindowTransform.Box(1,1,2,2));}
}
