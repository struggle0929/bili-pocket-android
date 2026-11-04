package cn.eveonecat.pocket;

import java.util.*;

/** Read-only reservations. A reservation is never evidence that an account entered a room. */
final class DualPlan {
    record Draw(long id,String room,long end) {}
    record Reservation(String room,long enter,long lastDraw,long leave,int account,List<Long> tasks,String state) {}
    static List<Reservation> calculate(List<Draw> draws,long now,long before,long after,boolean backup){
        Map<String,List<Draw>> grouped=new HashMap<>();
        for(Draw d:draws)if(d.end>now)grouped.computeIfAbsent(d.room,k->new ArrayList<>()).add(d);
        List<List<Draw>> rooms=new ArrayList<>(grouped.values());
        for(List<Draw> ds:rooms)ds.sort(Comparator.comparingLong(Draw::end));
        rooms.sort(Comparator.comparingLong(ds->ds.get(0).end));
        long[] free={now,now};List<Reservation> result=new ArrayList<>();
        for(List<Draw> ds:rooms){
            long first=ds.get(0).end,last=ds.get(ds.size()-1).end,enter=Math.max(now,first-Math.max(0,before)),leave=last+Math.max(0,after);
            int account=-1;String state="计划进房";
            if(free[0]<=enter)account=0;
            else if(backup&&free[1]<=enter)account=1;
            else {
                int earliest=backup&&free[1]<free[0]?1:0;
                if(free[earliest]<first){account=earliest;enter=free[earliest];state="冲突排队 · 提前量不足";}
                else state="冲突 · 无空闲账号";
            }
            if(account>=0)free[account]=leave;
            List<Long> ids=new ArrayList<>();for(Draw d:ds)ids.add(d.id);
            result.add(new Reservation(ds.get(0).room,enter,last,leave,account,List.copyOf(ids),state));
        }return result;
    }
}
