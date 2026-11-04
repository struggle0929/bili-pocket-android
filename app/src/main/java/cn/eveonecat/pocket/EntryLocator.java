package cn.eveonecat.pocket;

/** Finds the red battery icon among header entries; excludes purple/gold lucky bags. */
final class EntryLocator {
    record Box(int left,int top,int right,int bottom){int cx(){return (left+right)/2;}int cy(){return (top+bottom)/2;}}
    static Box find(int width,int height,int[] colors,int offsetX,int offsetY,int screenWidth,int screenHeight){
        boolean[] seen=new boolean[width*height];int[] queue=new int[colors.length];Box best=null;int bestSize=0;
        for(int start=0;start<colors.length;start++){
            if(seen[start]||!red(colors[start]))continue;
            int head=0,tail=0;queue[tail++]=start;seen[start]=true;int l=width,t=height,r=0,b=0;
            while(head<tail){int at=queue[head++],x=at%width,y=at/width;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
                for(int next:new int[]{x>0?at-1:-1,x+1<width?at+1:-1,y>0?at-width:-1,y+1<height?at+width:-1})if(next>=0&&!seen[next]&&red(colors[next])){seen[next]=true;queue[tail++]=next;}
            }
            if(tail>bestSize&&r-l>screenWidth*.02&&r-l<screenWidth*.12&&b-t>screenHeight*.009&&b-t<screenHeight*.07){bestSize=tail;best=new Box(l+offsetX,t+offsetY,r+offsetX,b+offsetY);}
        }return best;
    }
    static boolean red(int pixel){int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;return r>=90&&r-b>=45&&r>g*1.6;}
}
