package cn.eveonecat.pocket;
import android.content.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import java.util.Arrays;

final class PermissionStatus {
    static ComponentName service(Context c){return new ComponentName(c,PocketService.class);}
    static boolean accessibilityEnabled(Context c){String s=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return s!=null&&Arrays.asList(s.split(":" )).contains(service(c).flattenToString());}
    static String summary(Context c){
        return "无障碍权限："+(accessibilityEnabled(c)?"已开启":"未开启")+" · 服务："+(PocketService.instance==null?"未连接":"已连接")+"\nQQ 通知权限："+(notificationEnabled(c)?"已开启":"未开启")+" · 监听："+(QQListener.connected?"已连接":"未连接");
    }
    static boolean notificationEnabled(Context c){String s=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");return s!=null&&s.contains(new ComponentName(c,QQListener.class).flattenToString());}
    static void accessibility(Context c){
        Intent direct=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME,service(c).flattenToString());
        try{c.startActivity(direct);}catch(ActivityNotFoundException|SecurityException e){c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
    }
    static void notifications(Context c){
        NotificationListenerService.requestRebind(new ComponentName(c,QQListener.class));
        try{c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(c,QQListener.class).flattenToString()));}
        catch(ActivityNotFoundException|SecurityException e){c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    }
}
