package cn.eveonecat.pocket;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class WinRegressionTest {
    @Test public void pinkPrizeMaskExcludesYellowBattery(){
        int[] pixels=new int[100*100];java.util.Arrays.fill(pixels,0xffffffff);
        for(int y=20;y<80;y++)for(int x=10;x<35;x++)pixels[y*100+x]=0xffffcc4c;
        for(int y=10;y<90;y++)for(int x=60;x<80;x++)pixels[y*100+x]=0xfffb2970;
        PrizeDigits.Mask mask=PrizeDigits.isolate(100,100,pixels);assertNotNull(mask);assertEquals(60,mask.left());assertEquals(80,mask.right());assertEquals(10,mask.top());assertEquals(90,mask.bottom());
        assertFalse(PrizeDigits.pink(0xffffcc4c));assertFalse(PrizeDigits.pink(0xfffdf8f8));
    }
    @Test public void actualScreenshotTitleAndThreeBatteryPrize(){
        PanelEvidence p=new PanelEvidence(1200,2670,List.of(
            new PanelEvidence.Box("恭喜你中奖啦",445,1465,753,1520),
            new PanelEvidence.Box("3",615,1660,696,1780),
            new PanelEvidence.Box("开心收下",493,2085,704,2140),
            new PanelEvidence.Box("查看中奖名单",475,2200,745,2250),
            new PanelEvidence.Box("6",445,350,480,400)));
        assertTrue(p.won());assertEquals(3,p.amount());assertFalse(p.text().contains("6\n"));
    }
    @Test public void captionVariantsAndIndependentChatDoNotProveWin(){
        assertTrue(ResultEvidence.winTitle("恭喜你中奖啦"));assertTrue(ResultEvidence.winTitle("恭喜你获奖啦！"));assertFalse(ResultEvidence.winTitle("小明：恭喜你中奖啦"));
        PanelEvidence p=new PanelEvidence(1200,2670,List.of(new PanelEvidence.Box("恭喜你中奖啦",5,1600,280,1650),new PanelEvidence.Box("3",615,1660,696,1780),new PanelEvidence.Box("开心收下",493,2085,704,2140)));
        assertFalse(p.won());
    }
    @Test public void titleWithoutClaimButtonDoesNotProveWin(){assertFalse(new PanelEvidence(1200,2670,List.of(new PanelEvidence.Box("恭喜你中奖啦",445,1465,753,1520),new PanelEvidence.Box("3",615,1660,696,1780))).won());}
    @Test public void matchingCarouselCardWithoutOwnClockIsClaimed(){assertTrue(ClaimDecision.matchingCard(100,100,-1,60000,10000));assertFalse(ClaimDecision.matchingCard(100,20,-1,60000,10000));assertFalse(ClaimDecision.matchingCard(520,100,-1,60000,10000));assertFalse(ClaimDecision.matchingCard(100,100,100,60000,10000));}
    @Test public void historicalCorrectionKeepsOnlyLossesAfterWin(){assertEquals(2,LossStreak.afterLatestWin(List.of("无法确认","未中奖","未中奖","中奖","未中奖","未中奖")));assertEquals(0,LossStreak.afterLatestWin(List.of("中奖","未中奖")));}
}
