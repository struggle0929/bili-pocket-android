package cn.eveonecat.pocket;
import android.graphics.*;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.common.InputImage;
import java.util.*;

final class Screen {
    static class Label {String text;Rect rect;Label(String s,Rect r){text=s;rect=r;}}
    final List<Label> labels=new ArrayList<>(); String text=""; int width,height;
    static final TextRecognizer OCR=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    static void read(Bitmap bitmap,java.util.function.Consumer<Screen> done,java.util.function.Consumer<Exception> fail){
        OCR.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(t->{
            Screen s=new Screen();s.width=bitmap.getWidth();s.height=bitmap.getHeight();s.text=t.getText();
            for(Text.TextBlock block:t.getTextBlocks())for(Text.Line line:block.getLines()){
                if(line.getBoundingBox()!=null)s.labels.add(new Label(line.getText(),line.getBoundingBox()));
                for(Text.Element e:line.getElements())if(e.getBoundingBox()!=null)s.labels.add(new Label(e.getText(),e.getBoundingBox()));
            }bitmap.recycle();done.accept(s);
        }).addOnFailureListener(e->{bitmap.recycle();fail.accept(e);});
    }
    Label exact(String... values){for(Label l:labels)for(String v:values)if(l.text.replaceAll("\\s","").equals(v))return l;return null;}
    boolean has(String s){return text.contains(s);}
    private PanelEvidence evidence(){
        List<PanelEvidence.Box> boxes=new ArrayList<>();
        for(Label l:labels)boxes.add(new PanelEvidence.Box(l.text,l.rect.left,l.rect.top,l.rect.right,l.rect.bottom));
        return new PanelEvidence(width,height,boxes);
    }
    String panelText(){return evidence().text();}
    boolean panelHas(String value){return panelText().contains(value);}
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
}
