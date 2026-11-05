package cn.eveonecat.pocket;

import java.util.*;
import java.util.regex.*;

final class NoticeParser {
    static List<PacketNotice> parse(String text,long sent){
        return TargetRule.defaults().apply(parseData(text,sent));
    }
    static List<PacketNotice> parseData(String text,long sent){
        if(text==null||text.length()>32000||sent<0)return List.of();
        List<PacketNotice> out=new ArrayList<>();List<PacketParser.Packet> allowed=PacketParser.parseData(text,sent);
        Set<String> shared=rooms(text);if(shared.isEmpty())for(PacketParser.Packet p:allowed)shared.add(p.room());
        for(String block:text.split("(?=[\\[【](?:电池红包|礼物红包|天选|无价值天选)[\\]】])")){
            Matcher tag=Pattern.compile("^[\\[【](电池红包|礼物红包|天选|无价值天选)[\\]】]").matcher(block.trim());if(!tag.find())continue;
            String kind=tag.group(1);Set<String> own=rooms(block);if(own.isEmpty()&&shared.size()==1)own.addAll(shared);String room=own.size()==1?own.iterator().next():"";
            Matcher cond=Pattern.compile("条件\\s*[:：]\\s*([^\\r\\n]+)").matcher(block);String condition=cond.find()?cond.group(1).trim():"未知条件";
            Matcher price=Pattern.compile("(?<![0-9])([0-9]{1,6})\\s*电池").matcher(block);int amount=price.find()?Integer.parseInt(price.group(1)):-1;
            Matcher count=Pattern.compile("/\\s*([0-9]{1,6})\\s*份").matcher(block);int shares=count.find()?Integer.parseInt(count.group(1)):0;
            long end=PacketParser.endTime(block,sent);boolean accepted=false;
            for(PacketParser.Packet p:allowed)if(kind.equals("电池红包")&&p.room().equals(room)&&p.amount()==amount&&p.end()==end&&p.condition().equals(condition))accepted=true;
            String reason=accepted?"":room.isEmpty()?"房间号缺失或不明确":!kind.equals("电池红包")?"非电池红包，不参与":!condition.equals("关注")&&!condition.equals("粉丝团")?"仅参与关注或粉丝团条件":amount<=0?"金额不明确":end==0?"开奖时间不明确":"消息未通过校验";
            String anchor="";if(!room.isEmpty()){Matcher name=Pattern.compile("(?m)^([^\\r\\n]+?)\\s+"+Pattern.quote(room)+"\\s*$").matcher(text);if(name.find())anchor=name.group(1).trim();}
            String key=room+":"+end+":"+amount+":"+condition;if(room.isEmpty()||end==0)key=digest(block+":"+sent);
            out.add(new PacketNotice(key,room,anchor,end,amount,shares,condition,kind+" · "+(amount<0?"金额未知":amount+"电池"),kind,reason,block.trim()));
        }return out;
    }
    static Set<String> rooms(String text){Set<String> ids=new HashSet<>();Matcher m=PacketParser.ROOM.matcher(text);while(m.find())ids.add(m.group(1));return ids;}
    static String digest(String text){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
