package cn.eveonecat.pocket;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;

final class FilesOut {
    static String write(Context c,String folder,String name,byte[] bytes,String mime)throws Exception{
        String selected=new Prefs(c).s(folder,"");
        if(!selected.isEmpty()){
            Uri tree=Uri.parse(selected);
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            Uri file=DocumentsContract.createDocument(c.getContentResolver(),parent,mime,name);
            if(file==null)throw new IOException("目录不可写，请重新选择");
            try(OutputStream o=c.getContentResolver().openOutputStream(file)){o.write(bytes);}return file.toString();
        }
        File d=new File(c.getExternalFilesDir(null),folder);if(!d.exists()&&!d.mkdirs())throw new IOException("无法创建目录");
        File file=new File(d,name);try(OutputStream o=new FileOutputStream(file)){o.write(bytes);}return file.getAbsolutePath();
    }
}
