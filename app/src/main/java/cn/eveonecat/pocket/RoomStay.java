package cn.eveonecat.pocket;

/** Retention is monotonic across all completed draws in one room. */
final class RoomStay {
    private long until;
    RoomStay(long restored){until=Math.max(0,restored);}
    void include(long end,TaskTiming timing){until=Math.max(until,timing.leaveAt(end));}
    long until(){return until;}
    boolean due(long now){return now>=until;}
    void clear(){until=0;}
}
