package cn.eveonecat.pocket;

/** Isolate the pink personal-prize digits; the adjacent yellow battery is not a digit. */
final class PrizeDigits {
    record Mask(int left,int top,int right,int bottom,int[] pixels){}
    static boolean pink(int color){int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;return r>185&&g<145&&b>40&&b<205&&r-g>85&&r-b>35;}
    static Mask isolate(int width,int height,int[] source){
        int left=width,top=height,right=0,bottom=0,count=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(pink(source[y*width+x])){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);count++;}
        if(count<20||right-left<4||bottom-top<8)return null;
        int w=right-left+32,h=bottom-top+32;int[] mask=new int[w*h];java.util.Arrays.fill(mask,0xffffffff);
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(pink(source[y*width+x]))mask[(y-top+16)*w+x-left+16]=0xff000000;
        return new Mask(left,top,right,bottom,mask);
    }
}
