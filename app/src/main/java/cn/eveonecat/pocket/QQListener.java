package cn.eveonecat.pocket;
import android.service.notification.*;
import android.app.Notification;
import android.os.Bundle;
import java.util.*;

public class QQListener extends NotificationListenerService {
    public static volatile boolean connected;
    public static QQListener instance;
    final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable poll=new Runnable(){public void run(){if(connected&&new Prefs(QQListener.this).b("listen",false))refresh(false);handler.postDelayed(this,5000);}};
    @Override public void onListenerConnected(){instance=this;connected=true;handler.removeCallbacks(poll);handler.post(poll);try(Repo r=new Repo(this)){r.log("QQ通知监听","系统通知监听已连接；每5秒补查活动通知，兼容系统分身 QQ");}}
    public void refresh(){refresh(true);}
    void refresh(boolean report){
        int qq=0,matched=0;StringBuilder profiles=new StringBuilder();
        try{for(StatusBarNotification n:getActiveNotifications())if(n.getPackageName().equals("com.tencent.mobileqq")){
            qq++;profiles.append(n.getUser()).append(' ');
            String title=String.valueOf(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE,""));
            if(title.contains(new Prefs(this).s("group","B站直播区游戏群")))matched++;
            onNotificationPosted(n);
        }}catch(RuntimeException e){try(Repo r=new Repo(this)){r.log("QQ通知检查失败",e.toString());}return;}
        if(report)try(Repo r=new Repo(this)){r.log("QQ通知检查","可读取 QQ 通知 "+qq+"，目标群 "+matched+"，所属空间 "+profiles);}
    }
    @Override public void onListenerDisconnected(){connected=false;handler.removeCallbacks(poll);if(instance==this)instance=null;requestRebind(new android.content.ComponentName(this,QQListener.class));}
    @Override public void onDestroy(){handler.removeCallbacks(poll);if(instance==this){instance=null;connected=false;}super.onDestroy();}
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
            r.ingest(text.toString(),sbn.getPostTime(),p);
        }catch(RuntimeException e){
            // A malformed third-party notification must not kill the listener service.
            try(Repo r=new Repo(this)){r.log("QQ通知解析失败",e.getClass().getSimpleName());}
        }
    }
}
