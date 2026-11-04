package cn.eveonecat.pocket;

import java.util.*;
import java.util.regex.*;
import java.time.*;

public final class PacketParser {
    public record Packet(String room,long end,int amount,String condition) {}
    static final Pattern ROOM=Pattern.compile("https?://live\\.bilibili\\.com/([1-9]\\d{0,14})(?!\\d)");
    public static List<Packet> parse(String text,long sent) {
        if(text==null||text.length()>32000||sent<0)return List.of();
        List<Packet> out=new ArrayList<>(); Set<String> shared=new HashSet<>(); Matcher rm=ROOM.matcher(text);
        while(rm.find())shared.add(rm.group(1));
        // QQ can truncate a long multi-packet notice inside the final URL. The bot's
        // explicit room-number line remains intact; accept only this exact structure.
        if(shared.isEmpty()){
            Matcher shortened=Pattern.compile("(?m)^([^\\r\\n]+?)\\s+([1-9]\\d{0,14})\\s*\\r?\\n\\s*(https?://[^\\s]*)\\s*$").matcher(text.trim());
            while(shortened.find()){
                String url=shortened.group(3);
                if(url.length()>=10&&"https://live.bilibili.com/".startsWith(url))shared.add(shortened.group(2));
            }
        }
        for(String b:text.split("(?=\\[电池红包\\]|【电池红包】)")) {
            if(!b.contains("电池红包"))continue;
            Set<String> rooms=new HashSet<>(); rm=ROOM.matcher(b); while(rm.find())rooms.add(rm.group(1));
            if(rooms.isEmpty()&&shared.size()==1)rooms.addAll(shared); if(rooms.size()!=1)continue;
            Matcher cond=Pattern.compile("条件\\s*[:：]\\s*([^\\r\\n]+)").matcher(b);
            String c=cond.find()?cond.group(1).trim():""; if(!c.equals("关注")&&!c.equals("粉丝团"))continue;
            Matcher amt=Pattern.compile("(?<![0-9])([0-9]{1,6})\\s*电池").matcher(b); if(!amt.find())continue; int amount=Integer.parseInt(amt.group(1));
            if(amount<=20)continue;
            long end=0; Matcher rel=Pattern.compile("剩余\\s*[:：]?\\s*(?:([0-9]{1,4})\\s*分)?\\s*([0-9]{1,2})\\s*秒").matcher(b);
            if(rel.find()){
                int seconds=Integer.parseInt(rel.group(2));int minutes=rel.group(1)==null?0:Integer.parseInt(rel.group(1));
                if(seconds>=60||minutes>1440)continue;
                end=sent+1000L*(seconds+60*minutes);
            }
            // Bot wall-clock end is authoritative if present: Android summary notifications can
            // re-post older text with a newer timestamp, which must not postpone a draw.
            Matcher clock=Pattern.compile("[（(]\\s*(\\d{1,2}):(\\d{2}):(\\d{2})\\s*[)）]").matcher(b);
            if(clock.find())try {
                    ZonedDateTime z=Instant.ofEpochMilli(sent).atZone(ZoneId.of("Asia/Shanghai"));
                    long closest=Long.MAX_VALUE;
                    for(int day=-1;day<=1;day++){
                        long candidate=z.plusDays(day).withHour(Integer.parseInt(clock.group(1))).withMinute(Integer.parseInt(clock.group(2))).withSecond(Integer.parseInt(clock.group(3))).withNano(0).toInstant().toEpochMilli();
                        if(Math.abs(candidate-sent)<Math.abs(closest-sent))closest=candidate;
                    }if(end!=0&&Math.abs(end-closest)>120000)continue;end=closest;
                }catch(DateTimeException ignored){continue;}
            if(end>sent&&end-sent<=86400000)out.add(new Packet(rooms.iterator().next(),end,amount,c));
        }return out;
    }
    public static boolean blacklisted(String values,String room){return Arrays.asList(values.trim().split("[\\s,，;；]+")).contains(room);}
}
