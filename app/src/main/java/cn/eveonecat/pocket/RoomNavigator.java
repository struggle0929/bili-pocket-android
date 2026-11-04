package cn.eveonecat.pocket;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.*;
import java.util.*;

/** Scoped Bilibili UI navigation. No external links and no QQ actions. */
final class RoomNavigator {
    final PocketService service;final String room;final boolean backup;final long deadline;
    int window,other,stage=0,tries=0;Rect bounds;boolean cancelled,submitted,liveTabSelected;
    RoomNavigator(PocketService s,String room,boolean backup,int window,int other,Rect bounds){
        service=s;this.room=room;this.backup=backup;this.window=window;this.other=other;this.bounds=new Rect(bounds);deadline=System.currentTimeMillis()+45000;
    }
    void log(String text){String detail=(backup?"备用":"主")+"账号 · "+text;android.util.Log.i("PocketNav",detail);service.repo.log("房间号进房验证",detail);}
    void cancel(){cancelled=true;}
    void start(){log("在目标B站窗口搜索 "+room);if(bounds.width()<service.getResources().getDisplayMetrics().widthPixels*.45)service.tap(bounds.centerX(),bounds.centerY());later(1800);}
    void later(long ms){service.h.postDelayed(()->{try{step();}catch(RuntimeException e){cancelled=true;log("导航异常已停止："+e.getClass().getSimpleName());}},ms);}
    boolean click(AccessibilityNodeInfo n){
        for(AccessibilityNodeInfo a=n;a!=null;a=a.getParent())if(a.isVisibleToUser()&&a.isClickable())return a.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;
    }
    AccessibilityNodeInfo root(){
        AccessibilityNodeInfo found=null;int fallback=-1;
        for(AccessibilityWindowInfo w:service.getWindows()){
            AccessibilityNodeInfo n=w.getRoot();if(n==null||!"tv.danmaku.bili".contentEquals(n.getPackageName()==null?"":n.getPackageName()))continue;
            if(w.getId()==window){w.getBoundsInScreen(bounds);return n;}
            Rect b=new Rect();w.getBoundsInScreen(b);Rect intersection=new Rect(b);
            if(w.getId()!=other&&intersection.intersect(bounds)&&intersection.width()*(long)intersection.height()>Math.min(b.width()*(long)b.height(),bounds.width()*(long)bounds.height())*.65){
                if(found!=null)return null;found=n;fallback=w.getId();
            }
        }
        if(found!=null){window=fallback;for(AccessibilityWindowInfo w:service.getWindows())if(w.getId()==window)w.getBoundsInScreen(bounds);}
        return found;
    }
    String text(AccessibilityNodeInfo n){return n.getText()==null?"":n.getText().toString().trim();}
    String id(AccessibilityNodeInfo n){return n.getViewIdResourceName()==null?"":n.getViewIdResourceName();}
    void event(AccessibilityEvent e){
        if(cancelled||!submitted||!String.valueOf(e.getClassName()).contains("LiveRoomActivity"))return;
        if(root()==null||e.getWindowId()!=window)return;
        log("目标窗口已打开直播间；请核对昵称和房间号。未点击红包或发送消息。");cancelled=true;service.windowReport();
    }
    void step(){
        if(cancelled||service.running)return;
        if(System.currentTimeMillis()>deadline||++tries>25){log("搜索导航超时，未继续操作。请查看手机停留在哪一步。");cancelled=true;service.windowReport();return;}
        AccessibilityNodeInfo root=root();if(root==null){log("目标窗口身份无法确认，已取消");cancelled=true;return;}
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();service.walk(root,nodes);
        List<AccessibilityNodeInfo> visible=new ArrayList<>();for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser())visible.add(n);
        for(AccessibilityNodeInfo n:visible)if(text(n).matches(".*(人机验证|安全验证|请完成验证|拖动滑块).*")){
            service.capture("verification");service.stop("进房验证遇到人机验证",true);cancelled=true;return;
        }
        if(stage==0){
            for(AccessibilityNodeInfo n:visible)if(n.isEditable()&&id(n).contains("search")){
                Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,room);
                if(!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){log("房间号输入失败");cancelled=true;return;}
                stage=1;log("已输入房间号");later(650);return;
            }
            String[] searchIds={"search","search_layout","search_bar","search_box","search_container","search_text","search_title"};
            for(AccessibilityNodeInfo n:visible){String rid=id(n);String desc=String.valueOf(n.getContentDescription());
                boolean search=desc.equals("搜索")||desc.startsWith("搜索框");for(String key:searchIds)if(rid.endsWith(":"+"id/"+key))search=true;
                if(search&&click(n)){later(1000);return;}
            }
            // Return within a positively identified live-room UI, never global BACK or app exit.
            boolean live=false;for(AccessibilityNodeInfo n:visible)if(id(n).endsWith("/live_send_danmaku"))live=true;
            if(live){
                for(AccessibilityNodeInfo n:visible)if("返回".contentEquals(n.getContentDescription()==null?"":n.getContentDescription())&&click(n)){later(1200);return;}
            }
            if(tries==3){log("未找到B站搜索入口，停止以避免误点；请保持该账号在B站首页后重试");cancelled=true;return;}
            later(1000);return;
        }
        if(stage==1){
            for(AccessibilityNodeInfo n:visible)if(!n.isEditable()&&text(n).equals("搜索")&&click(n)){stage=2;submitted=true;log("已提交搜索");later(1800);return;}
            for(AccessibilityNodeInfo n:visible)if(n.isEditable()&&text(n).equals(room)&&n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())){stage=2;submitted=true;log("已通过键盘提交搜索");later(1800);return;}
            later(700);return;
        }
        if(stage==2){
            for(AccessibilityNodeInfo n:visible)if((text(n).equals("直播")||text(n).startsWith("直播("))&&click(n)){stage=3;liveTabSelected=true;log("已选择直播搜索");later(1500);return;}
            stage=3;later(500);return;
        }
        // Prefer an explicitly labelled room jump. Do not select an arbitrary video/user result.
        for(AccessibilityNodeInfo n:visible){String t=text(n);
            if((t.equals("进入直播间")||t.equals("前往直播间")||t.equals("进入房间"))&&click(n)){stage=4;log("已点击直播间入口，等待目标窗口确认");later(1800);return;}
        }
        if(stage==3){
            LinkedHashMap<String,AccessibilityNodeInfo> covers=new LinkedHashMap<>();
            boolean liveResults=liveTabSelected;for(AccessibilityNodeInfo n:visible)if(text(n).equals("直播")&&n.isSelected())liveResults=true;
            for(AccessibilityNodeInfo n:visible){Rect b=new Rect();n.getBoundsInScreen(b);
                if((id(n).endsWith("/cover")||id(n).endsWith("/live_cover"))&&b.width()>bounds.width()*.25&&b.height()>bounds.height()*.08&&b.top>bounds.top+bounds.height()*.12){
                    for(AccessibilityNodeInfo a=n;a!=null;a=a.getParent())if(a.isClickable()){
                        Rect card=new Rect();a.getBoundsInScreen(card);covers.put(card.toShortString(),a);break;
                    }
                }
            }
            if(liveResults&&covers.size()==1&&click(covers.values().iterator().next())){stage=4;log("仅一个直播搜索结果，已打开候选；需核对房间号");later(1800);return;}
            if(tries>=12){log("无法唯一确认直播间入口，已停止。不会点击视频或猜测多个结果。");cancelled=true;service.windowReport();return;}
        }
        later(1000);
    }
}
