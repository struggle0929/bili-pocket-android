package cn.eveonecat.pocket;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PanelEvidenceTest {
    private PanelEvidence.Box b(String text,int x,int y,int w,int h){return new PanelEvidence.Box(text,x,y,x+w,y+h);}
    @Test public void amountMustBeBelowCentralPacketTitle(){
        PanelEvidence panel=new PanelEvidence(1000,2000,List.of(
            b("520",450,500,100,45),b("甲发的红包",390,1300,220,40),
            b("100",450,1460,100,60),b("共10份",450,1770,100,30),
            b("0分19秒后开奖",420,1650,160,30),b("20",10,1480,60,40)));
        assertEquals(100,panel.amount());
        assertTrue(panel.text().contains("共10份"));
        assertFalse(panel.text().contains("520"));
    }
    @Test public void twentyIsNotMistakenForDifferentAmountBehindPanel(){
        PanelEvidence panel=new PanelEvidence(1000,2000,List.of(
            b("100",450,800,100,60),b("甲发的红包",390,1300,220,40),b("20",460,1460,80,60)));
        assertEquals(20,panel.amount());
    }
    @Test public void unknownOrTwoAmountsFailClosed(){
        PanelEvidence empty=new PanelEvidence(1000,2000,List.of(b("100",450,1460,100,60)));
        assertEquals(-1,empty.amount());assertEquals("",empty.text());
        PanelEvidence ambiguous=new PanelEvidence(1000,2000,List.of(
            b("甲发的红包",390,1300,220,40),b("20",460,1450,80,60),b("100",450,1540,100,50)));
        assertEquals(-1,ambiguous.amount());
    }
    @Test public void chatTextOutsidePanelCannotProveWinning(){
        PanelEvidence panel=new PanelEvidence(1000,2000,List.of(
            b("恭喜你获奖啦",30,1100,180,40),b("甲发的红包",390,1300,220,40),b("未参与",440,1650,120,40)));
        assertFalse(panel.text().contains("恭喜你获奖啦"));assertTrue(panel.text().contains("未参与"));
    }
}
