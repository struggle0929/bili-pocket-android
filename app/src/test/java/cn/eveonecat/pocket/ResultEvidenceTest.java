package cn.eveonecat.pocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResultEvidenceTest {
    @Test public void requiresExplicitResultNotChatOrParticipation(){assertTrue(ResultEvidence.loss("未中奖"));assertTrue(ResultEvidence.loss("很遗憾，未中奖！"));assertFalse(ResultEvidence.loss("未参与"));assertFalse(ResultEvidence.loss("小明:未中奖"));assertFalse(ResultEvidence.loss("发的红包"));}
    @Test public void requiresConfirmedRoundAndDrawWindow(){assertTrue(ResultEvidence.inWindow(10000,10000,true));assertFalse(ResultEvidence.inWindow(10000,10000,false));assertFalse(ResultEvidence.inWindow(6000,10000,true));assertFalse(ResultEvidence.inWindow(56000,10000,true));}
}
