package cn.eveonecat.pocket;

import java.util.Collection;

final class HomeEvidence {
    static boolean confirmed(boolean biliForeground,boolean liveControls,boolean mainActivityObserved,Collection<String> labels){
        if(!biliForeground||liveControls)return false;
        return mainActivityObserved||labels.contains("首页")&&labels.contains("我的")&&(labels.contains("动态")||labels.contains("会员购")||labels.contains("关注"));
    }
}
