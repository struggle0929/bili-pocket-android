package cn.eveonecat.pocket;

import java.util.*;
import java.util.regex.*;

/** Pure geometry/text logic, independently testable without a device or Android stubs. */
final class PanelEvidence {
    record Box(String text,int left,int top,int right,int bottom) {
        int cx(){return (left+right)/2;} int cy(){return (top+bottom)/2;}
    }
    final int width,height; final List<Box> boxes; Box anchor;
    PanelEvidence(int width,int height,List<Box> boxes){
        this.width=width;this.height=height;this.boxes=boxes;
        for(Box b:boxes){
            String text=b.text.replaceAll("\\s","");
            if(b.cx()<width*.30||b.cx()>width*.70||b.cy()<height*.30||b.cy()>height*.88)continue;
            if(text.endsWith("发的红包")||ResultEvidence.winTitle(text)){
                if(anchor==null||b.top<anchor.top)anchor=b;
            }
        }
    }
    boolean contains(int left,int top,int right,int bottom){
        if(anchor==null)return false;
        double x=(left+right)/2.0,y=(top+bottom)/2.0;
        return x>=width*.22&&x<=width*.78&&y>=anchor.top-height*.015&&y<=height*.96;
    }
    String text(){
        if(anchor==null)return "";
        StringBuilder out=new StringBuilder();Set<String> seen=new HashSet<>();
        for(Box b:boxes)if(contains(b.left,b.top,b.right,b.bottom)&&seen.add(b.text))out.append(b.text).append('\n');
        return out.toString();
    }
    boolean won(){
        if(anchor==null||!ResultEvidence.winTitle(anchor.text))return false;
        for(Box b:boxes)if(contains(b.left,b.top,b.right,b.bottom)&&b.text.replaceAll("[\\s>›》]","").matches("开心收下|查看中奖名单|收下奖励"))return true;
        return false;
    }
    int amount(){Box b=amountBox();if(b==null)return -1;return parseAmount(b.text);}
    static int parseAmount(String value){
        Matcher explicit=Pattern.compile("(?:总价值)?(?:电池)?[⚡]?([0-9]{1,6})(?:电池)?").matcher(value.replaceAll("\\s",""));
        return explicit.matches()?Integer.parseInt(explicit.group(1)):-1;
    }
    Box amountBox(){
        if(anchor==null)return null;
        Map<Integer,Box> values=new HashMap<>();
        for(Box b:boxes){
            if(b.cx()<width*.35||b.cx()>width*.65||b.top<=anchor.bottom||b.cy()>Math.min(height*.90,anchor.bottom+height*.17))continue;
            String value=b.text.replaceAll("\\s","");
            Matcher explicit=Pattern.compile("(?:总价值)?(?:电池)?[⚡]?([0-9]{1,6})(?:电池)?").matcher(value);
            if(!explicit.matches())continue;
            try{
                int n=Integer.parseInt(explicit.group(1));
                Box old=values.get(n);if(old==null||b.bottom-b.top>old.bottom-old.top)values.put(n,b);
            }catch(NumberFormatException ignored){}
        }
        if(values.size()==1)return values.values().iterator().next();
        // Multiple distinct numbers (e.g. two cards or a chat overlay) are not trustworthy.
        return null;
    }
}
