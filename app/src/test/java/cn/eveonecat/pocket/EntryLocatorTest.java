package cn.eveonecat.pocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class EntryLocatorTest {
    void fill(int[] p,int width,int x,int y,int w,int h,int color){for(int j=y;j<y+h;j++)for(int i=x;i<x+w;i++)p[j*width+i]=color;}
    @Test public void choosesRedPacketAfterPurpleBag(){int[] p=new int[400*160];fill(p,400,30,30,50,70,0xffae559f);fill(p,400,150,30,50,70,0xfffb4d63);EntryLocator.Box b=EntryLocator.find(400,160,p,0,380,1200,2670);assertNotNull(b);assertEquals(174,b.cx());}
    @Test public void findsDimmedPacketWhilePanelOpen(){int[] p=new int[400*160];fill(p,400,60,30,50,70,0xff7d2631);assertNotNull(EntryLocator.find(400,160,p,0,380,1200,2670));}
    @Test public void doesNotTreatGoldBagOrTinyRedTextAsPacket(){int[] p=new int[400*160];fill(p,400,30,30,50,70,0xfff1bd39);fill(p,400,150,30,4,6,0xfffb4d63);assertNull(EntryLocator.find(400,160,p,0,380,1200,2670));}
}
