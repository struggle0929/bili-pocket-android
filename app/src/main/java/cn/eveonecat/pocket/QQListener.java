package cn.eveonecat.pocket;
import android.service.notification.*;
import android.app.Notification;
import android.os.Bundle;
import java.util.*;

public class QQListener extends NotificationListenerService {
    public static volatile boolean connected;
    @Override public void onListenerConnected(){connected=true;try(Repo r=new Repo(this)){r.log("QQ通知监听","系统通知监听已连接；只读取匹配群名的 QQ 通知");}}
    @Override public void onListenerDisconnected(){connected=false;requestRebind(new android.content.ComponentName(this,QQListener.class));}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!sbn.getPackageName().equals("com.tencent.mobileqq"))return;
        Prefs p=new Prefs(this); if(!p.b("listen",false))return;
        Bundle b=sbn.getNotification().extras;
        String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
        String conversation=String.valueOf(b.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE,""));
        String subtitle=String.valueOf(b.getCharSequence(Notification.EXTRA_SUB_TEXT,""));
        String group=p.s("group","B站直播区游戏群").trim();
        // Match only notification metadata, never a group name quoted inside a private message body.
        if(group.isEmpty()||!(title.contains(group)||conversation.contains(group)||subtitle.contains(group)))return;
        try(Repo r=new Repo(this)){
            android.os.Parcelable[] entries=b.getParcelableArray(Notification.EXTRA_MESSAGES);
            if(entries!=null&&entries.length>0){for(int i=Math.max(0,entries.length-30);i<entries.length;i++)if(entries[i] instanceof Bundle){Bundle m=(Bundle)entries[i];CharSequence t=m.getCharSequence("text");if(t!=null)r.ingest(t.toString(),m.getLong("time",sbn.getPostTime()),p);}return;}
            CharSequence big=b.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence text=big!=null?big:b.getCharSequence(Notification.EXTRA_TEXT,"");
            // InboxStyle can preserve bot message lines beyond the compact notification preview.
            CharSequence[] lines=b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if(lines!=null&&lines.length>0){StringBuilder all=new StringBuilder();for(CharSequence line:lines)all.append(line).append('\n');if(all.length()>text.length())text=all;}
            r.ingest(text.toString(),sbn.getNotification().when>0?sbn.getNotification().when:sbn.getPostTime(),p);
        }catch(RuntimeException e){
            // A malformed third-party notification must not kill the listener service.
            try(Repo r=new Repo(this)){r.log("QQ通知解析失败",e.getClass().getSimpleName());}
        }
    }
}
