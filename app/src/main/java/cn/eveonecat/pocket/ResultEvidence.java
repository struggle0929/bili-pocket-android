package cn.eveonecat.pocket;

final class ResultEvidence {
    static boolean winTitle(String text){
        if(text==null)return false;String t=text.replaceAll("[\\s,，。!！—－-]","");
        return t.equals("恭喜你中奖啦")||t.equals("恭喜你获奖啦")||t.equals("恭喜中奖")||t.equals("恭喜中奖啦");
    }
    static boolean loss(String text){
        if(text==null)return false;
        String t=text.replaceAll("[\\s,，。!！]","");
        return t.equals("未中奖")||t.equals("很遗憾未中奖")||t.equals("未能中奖")||t.equals("很遗憾未能中奖");
    }
    static boolean inWindow(long now,long end,boolean confirmed){return confirmed&&now>=end-3000&&now<=end+45000;}
}
