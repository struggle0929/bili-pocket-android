package cn.eveonecat.pocket;
import android.graphics.*;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.common.InputImage;
import java.util.*;

final class Screen {
    static class Label {String text;Rect rect;Label(String s,Rect r){text=s;rect=r;}}
    final List<Label> labels=new ArrayList<>(); String text=""; int width,height;Rect entry;boolean batteryGlyph;Integer prizeQuantity;
    static final java.util.concurrent.ExecutorService WORK=java.util.concurrent.Executors.newSingleThreadExecutor();
    static TextRecognizer OCR;
    static void read(Bitmap bitmap,java.util.function.Consumer<Screen> done,java.util.function.Consumer<Exception> fail){
        final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        final java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        long started=android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("PocketVision","OCR requested "+bitmap.getWidth()+"x"+bitmap.getHeight());
        Runnable timeout=()->{if(finished.compareAndSet(false,true)){android.util.Log.e("PocketVision","OCR timeout");fail.accept(new IllegalStateException("文字识别超时，已停止操作"));}};
        main.postDelayed(timeout,25000);
        java.util.function.Consumer<Screen> complete=s->{bitmap.recycle();if(finished.compareAndSet(false,true)){main.removeCallbacks(timeout);android.util.Log.i("PocketVision","OCR completed in "+(android.os.SystemClock.elapsedRealtime()-started)+"ms, chars="+s.text.length());done.accept(s);}};
        WORK.execute(()->{try{
        if(OCR==null)OCR=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        OCR.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(t->{
            if(finished.get()){bitmap.recycle();return;}
            Screen s=new Screen();s.width=bitmap.getWidth();s.height=bitmap.getHeight();s.text=t.getText();
            int left=0,top=(int)(s.height*.145),w=(int)(s.width*.38),h=(int)(s.height*.055);int[] colors=new int[w*h];bitmap.getPixels(colors,0,w,left,top,w,h);
            EntryLocator.Box packet=EntryLocator.find(w,h,colors,left,top,s.width,s.height);if(packet!=null)s.entry=new Rect(packet.left(),packet.top(),packet.right(),packet.bottom());
            for(Text.TextBlock block:t.getTextBlocks())for(Text.Line line:block.getLines()){
                if(line.getBoundingBox()!=null)s.labels.add(new Label(line.getText(),line.getBoundingBox()));
                for(Text.Element e:line.getElements())if(e.getBoundingBox()!=null)s.labels.add(new Label(e.getText(),e.getBoundingBox()));
            }
            if(s.won()){
                PanelEvidence.Box number=s.evidence().amountBox();
                if(number!=null){
                    int x0=Math.max(0,number.left()-8),y0=Math.max(0,number.top()-8),w0=Math.min(s.width,number.right()+8)-x0,h0=Math.min(s.height,number.bottom()+8)-y0;
                    int[] source=new int[w0*h0];bitmap.getPixels(source,0,w0,x0,y0,w0,h0);PrizeDigits.Mask mask=PrizeDigits.isolate(w0,h0,source);
                    if(mask!=null){
                        int yellow=0,numberLeft=x0+mask.left(),top0=y0+mask.top(),bottom0=y0+mask.bottom();
                        for(int y=Math.max(0,top0-25);y<Math.min(s.height,bottom0+25);y+=2)for(int x=Math.max(0,numberLeft-(int)(s.width*.15));x<numberLeft;x+=2){int color=bitmap.getPixel(x,y),red=Color.red(color),green=Color.green(color),blue=Color.blue(color);if(red>190&&green>130&&blue<150&&red>green*1.07)yellow++;}
                        s.batteryGlyph=yellow>30;
                        int mw=mask.right()-mask.left()+32,mh=mask.bottom()-mask.top()+32;
                        Bitmap digits=Bitmap.createBitmap(mask.pixels(),mw,mh,Bitmap.Config.ARGB_8888);
                        OCR.process(InputImage.fromBitmap(digits,0)).addOnSuccessListener(d->{
                            String value=d.getText().replaceAll("\\s","");if(value.matches("[0-9]{1,6}"))s.prizeQuantity=Integer.valueOf(value);
                            android.util.Log.i("PocketVision","isolated prize="+value+", battery="+s.batteryGlyph);digits.recycle();complete.accept(s);
                        }).addOnFailureListener(e->{digits.recycle();complete.accept(s);});return;
                    }
                }
            }
            complete.accept(s);
        }).addOnFailureListener(e->{bitmap.recycle();if(finished.compareAndSet(false,true)){main.removeCallbacks(timeout);android.util.Log.e("PocketVision","OCR failed",e);fail.accept(e);}});
        }catch(Exception e){bitmap.recycle();if(finished.compareAndSet(false,true)){main.removeCallbacks(timeout);main.post(()->fail.accept(e));}}});
    }
    Label exact(String... values){for(Label l:labels)for(String v:values)if(l.text.replaceAll("\\s","").equals(v))return l;return null;}
    boolean has(String s){return text.contains(s);}
    int roundSeconds(){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:(\\d+)\\s*分)?\\s*(\\d+)\\s*秒").matcher(panelText());
        if(m.find())return Integer.parseInt(m.group(2))+(m.group(1)==null?0:60*Integer.parseInt(m.group(1)));
        // Before submission this Bilibili version only shows the clock on the packet entry.
        for(Label l:labels)if(l.rect.centerX()<width*(entry==null?.20:.38)&&l.rect.centerY()>height*.12&&l.rect.centerY()<height*.24){
            m=java.util.regex.Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(l.text.replaceAll("\\s","").replace('：',':'));
            if(m.matches()&&Integer.parseInt(m.group(2))<60&&(entry==null||Math.abs(l.rect.centerX()-entry.centerX())<width*.055))return Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2));
        }return -1;
    }
    private PanelEvidence evidence(){
        List<PanelEvidence.Box> boxes=new ArrayList<>();
        for(Label l:labels)boxes.add(new PanelEvidence.Box(l.text,l.rect.left,l.rect.top,l.rect.right,l.rect.bottom));
        return new PanelEvidence(width,height,boxes);
    }
    String panelText(){return evidence().text();}
    boolean panelHas(String value){return panelText().contains(value);}
    boolean won(){return evidence().won();}
    Integer prizeBattery(){return won()&&prizeQuantity!=null&&prizeQuantity>0&&(batteryGlyph||panelText().contains("电池"))?prizeQuantity:null;}
    int panelSeconds(){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:(\\d+)\\s*分)?\\s*(\\d+)\\s*秒").matcher(panelText());
        return m.find()?Integer.parseInt(m.group(2))+(m.group(1)==null?0:60*Integer.parseInt(m.group(1))):-1;
    }
    static Screen nativePanel(java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes,int width,int height){
        Screen s=new Screen();s.width=width;s.height=height;StringBuilder text=new StringBuilder();
        for(android.view.accessibility.AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()){
            CharSequence value=n.getText();if(value==null||value.length()==0)value=n.getContentDescription();if(value==null||value.length()==0)continue;
            Rect r=new Rect();n.getBoundsInScreen(r);s.labels.add(new Label(value.toString(),r));text.append(value).append('\n');
        }s.text=text.toString();return s;
    }
    Label panelExact(String... values){
        PanelEvidence e=evidence();
        for(Label l:labels)if(e.contains(l.rect.left,l.rect.top,l.rect.right,l.rect.bottom))
            for(String value:values)if(l.text.replaceAll("\\s","").equals(value))return l;
        return null;
    }
    // Fail closed unless a central packet title anchors a single unambiguous amount.
    int amount(){
        return evidence().amount();
    }
    WaitingTapPlan.Plan waitingTapPlan(int maxStrokes,java.util.Random random){PanelEvidence e=evidence();PanelEvidence.Box amount=e.amountBox();return e.anchor==null||amount==null?null:WaitingTapPlan.create(width,height,e.anchor.bottom(),amount.top(),amount.bottom(),maxStrokes,random);}
}
