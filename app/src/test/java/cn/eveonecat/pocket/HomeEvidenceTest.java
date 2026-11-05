package cn.eveonecat.pocket;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeEvidenceTest {
    @Test public void homeActivityNeedsForegroundBiliAndNoLiveControls(){assertTrue(HomeEvidence.confirmed(true,false,true,List.of()));assertFalse(HomeEvidence.confirmed(false,false,true,List.of()));assertFalse(HomeEvidence.confirmed(true,true,true,List.of()));}
    @Test public void navigationCanConfirmHomeWithoutActivityEvent(){assertTrue(HomeEvidence.confirmed(true,false,false,List.of("首页","动态","我的")));assertTrue(HomeEvidence.confirmed(true,false,false,List.of("首页","会员购","我的")));}
    @Test public void MissingLiveControlsAloneNeverProvesExit(){assertFalse(HomeEvidence.confirmed(true,false,false,List.of()));assertFalse(HomeEvidence.confirmed(true,false,false,List.of("首页")));assertFalse(HomeEvidence.confirmed(true,false,false,List.of("我的","关注")));}
    @Test public void livePanelOrAnotherAppCannotMasqueradeAsHome(){assertFalse(HomeEvidence.confirmed(true,true,false,List.of("首页","动态","我的")));assertFalse(HomeEvidence.confirmed(false,false,false,List.of("首页","动态","我的")));}
}
