package cn.eveonecat.pocket;

import java.util.*;

/** Decorative, bounded taps. No claim/result/close controls belong to this region. */
final class WaitingTapPlan {
    record Point(float x,float y){}
    record Plan(List<Point> points,int rounds,int duration,int pause){}
    static boolean allowed(boolean enabled,boolean confirmed,boolean matching,boolean panelVisible,long now,long end,long due){return enabled&&confirmed&&matching&&panelVisible&&now>=due&&end-now>3000;}
    static Plan create(int width,int height,int titleBottom,int amountTop,int amountBottom,int maxStrokes,Random random){
        int top=Math.max(titleBottom+Math.max(8,(int)(height*.012)),amountTop-(int)(height*.035));
        int bottom=Math.min((int)(height*.88),amountBottom+(int)(height*.01));
        if(width<200||height<400||titleBottom<=height*.3||amountTop<=titleBottom||amountBottom<=amountTop||bottom-top<height*.025||maxStrokes<3)return null;
        int count=3+random.nextInt(Math.min(8,maxStrokes)-2);List<Integer> cells=new ArrayList<>();for(int i=0;i<8;i++)cells.add(i);Collections.shuffle(cells,random);List<Point> points=new ArrayList<>();
        for(int i=0;i<count;i++){int cell=cells.get(i);float x=width*(.30f+((cell%4)+.2f+random.nextFloat()*.6f)*.10f);float y=top+((cell/4)+.2f+random.nextFloat()*.6f)*(bottom-top)/2f;points.add(new Point(x,y));}
        return new Plan(List.copyOf(points),3,55,85);
    }
}
