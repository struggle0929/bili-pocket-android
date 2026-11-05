package cn.eveonecat.pocket;

/** One displayed packet, including packets that cannot be scheduled. */
record PacketNotice(String key,String room,String anchor,long end,int amount,int shares,String condition,String reward,String kind,String reason,String raw) {
    boolean eligible(){return reason.isEmpty();}
}
