package cn.eveonecat.pocket;

import java.math.*;
import java.util.*;

/** One rule shared by all sources, persisted notices, queue and foreground claims. */
final class TargetRule {
    final String mode;final BigDecimal threshold;
    private TargetRule(String mode,BigDecimal threshold){this.mode=mode;this.threshold=threshold;}
    static TargetRule defaults(){return new TargetRule("default",BigDecimal.ZERO);}
    static TargetRule invalid(){return new TargetRule("invalid",BigDecimal.ZERO);}
    static TargetRule of(String mode,String value){
        if(mode.equals("default"))return defaults();
        if(!mode.equals("total")&&!mode.equals("share"))throw new IllegalArgumentException("请选择目标红包模式");
        String text=value.trim();if(!text.matches("[0-9]{1,6}(?:\\.[0-9]{1,6})?"))throw new IllegalArgumentException("阈值应为0～999999的数字，最多6位小数");
        BigDecimal n=new BigDecimal(text);if(n.compareTo(new BigDecimal("999999"))>0)throw new IllegalArgumentException("阈值不能超过999999");return new TargetRule(mode,n.stripTrailingZeros());
    }
    String value(){return threshold.toPlainString();}
    String description(){return mode.equals("default")?"默认模式 · 总电池 > 20":mode.equals("total")?"红包电池 ≥ "+value():mode.equals("share")?"红包期望 ≥ "+value()+" 电池/份":"目标红包阈值无效，请重新保存";}
    String reason(int amount,int shares){
        if(mode.equals("invalid"))return description();if(amount<=0)return "红包电池数量无效，不参与";
        if(mode.equals("default"))return amount>20?"":"20电池及以下，不参与";
        if(mode.equals("total"))return BigDecimal.valueOf(amount).compareTo(threshold)>=0?"":"红包总电池 "+amount+" < "+value()+"，不参与";
        if(shares<=0)return "红包份数缺失或无效，无法计算期望，不参与";
        return BigDecimal.valueOf(amount).compareTo(threshold.multiply(BigDecimal.valueOf(shares)))>=0?"":"红包期望 "+average(amount,shares)+" < "+value()+" 电池/份，不参与";
    }
    static String average(int amount,int shares){if(amount<=0||shares<=0)return "待确认";BigDecimal a=BigDecimal.valueOf(amount),s=BigDecimal.valueOf(shares),rounded=a.divide(s,6,RoundingMode.HALF_UP).stripTrailingZeros();return (rounded.multiply(s).compareTo(a)==0?"":"约")+rounded.toPlainString()+" 电池/份";}
    List<PacketNotice> apply(List<PacketNotice> notices){List<PacketNotice> out=new ArrayList<>();for(PacketNotice n:notices)out.add(new PacketNotice(n.key(),n.room(),n.anchor(),n.end(),n.amount(),n.shares(),n.condition(),n.reward(),n.kind(),n.reason().isEmpty()?reason(n.amount(),n.shares()):n.reason(),n.raw()));return out;}
    static String legacyBase(String reason){return reason.equals("20电池及以下，不参与")?"":reason;}
}
