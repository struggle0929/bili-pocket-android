package cn.eveonecat.pocket;

/** The entry clock belongs to the carousel, not necessarily its selected card. */
final class ClaimDecision {
    static boolean matchingCard(int expected,int visible,int cardSeconds,long end,long now){
        if(visible<=0||visible!=expected)return false;
        return cardSeconds<0||Math.abs(now+cardSeconds*1000L-end)<12000;
    }
}
