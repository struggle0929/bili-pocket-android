package cn.eveonecat.pocket;

import android.accessibilityservice.*;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.os.*;
import android.net.Uri;
import java.util.*;

public class PocketService extends AccessibilityService {
    public static PocketService instance;
    RoomNavigator roomNavigator;
    final Handler h=new Handler(Looper.getMainLooper());
    Prefs p;Repo repo; boolean running,busy,firstVisit,confirmed,submitted,visitSent,pendingFinish;
    long generation=0,nextAction=0,entered=0,lastResultRefresh=0,lastDrawLog=0;int enterAttempts=0,scans=0;String status="待机",room="";
    boolean liveActivity=false,visitRecorded=false,entryConfirmed=false;
    Repo.Task current; final Set<Long> roundConfirmed=new HashSet<>(),roundSubmitted=new HashSet<>();
    int screenWidth,screenHeight,packetX=-1,packetY=-1;
    long lossHintTask=-1;String lossHint="";int winAmountChecks;
    RoomStay stay=new RoomStay(0);boolean leaving,homeReady,mainActivityObserved;long exitStarted;int exitAttempts;Repo.Task retryEntry;
    final Map<Long,TaskTiming> timings=new HashMap<>();
    WebPoolMonitor web;
    WindowManager wm;View overlay;TextView overlayText;PowerManager.WakeLock wake;
    final Runnable tick=new Runnable(){public void run(){try{if(web!=null)web.sync();step();}catch(Exception e){stop("运行异常："+e.getClass().getSimpleName()+" "+e.getMessage(),true);}h.postDelayed(this,running&&current!=null?500:1000);}};
    @Override protected void onServiceConnected(){instance=this;p=new Prefs(this);repo=new Repo(this);repo.recover();room=p.s("activeRoom","");stay=new RoomStay(p.l("roomLeaveAt",0));wm=(WindowManager)getSystemService(WINDOW_SERVICE);web=new WebPoolMonitor(this,h);h.post(tick);repo.log("无障碍连接","服务已连接；运行时不要同时使用会独占无障碍的界面抓取工具");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(roomNavigator!=null)roomNavigator.event(e);
        if(running&&current!=null&&"tv.danmaku.bili".contentEquals(e.getPackageName()==null?"":e.getPackageName())&&ResultEvidence.inWindow(System.currentTimeMillis(),current.end,confirmed)){
            for(CharSequence label:e.getText())if(ResultEvidence.loss(String.valueOf(label))){lossHintTask=current.id;lossHint=label.toString();repo.log("未中奖提示",lossHint);nextAction=0;break;}
        }
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&"tv.danmaku.bili".contentEquals(e.getPackageName()==null?"":e.getPackageName())){
            String cls=String.valueOf(e.getClassName());if(cls.contains("LiveRoomActivity")){liveActivity=true;if(current==null&&!leaving)homeReady=false;}else if(cls.contains("MainActivity")){liveActivity=false;if(leaving)mainActivityObserved=true;}
        }
    }
    @Override public void onInterrupt(){stop("无障碍中断",false);}
    @Override public void onDestroy(){stop("服务关闭",false);if(web!=null)web.close();h.removeCallbacksAndMessages(null);if(instance==this)instance=null;if(repo!=null)repo.close();super.onDestroy();}
    boolean blocked(){return !p.s("verificationLock","").isEmpty();}
    public void start(){
        if(running)return;
        if(roomNavigator!=null)roomNavigator.cancel();
        if(blocked()){status="验证锁定：请在设置确认处理后解除";return;}
        if(!p.webMode()&&!notificationEnabled()){status="请到设置授权 QQ 通知读取";return;}
        try{p.sourceKey();}catch(IllegalArgumentException e){status=e.getMessage();return;}
        p.put("listen",true);running=true;generation++;busy=false;nextAction=0;current=null;retryEntry=null;leaving=false;homeReady=false;pendingFinish=false;status=(p.webMode()?"监控网页":"监听 QQ")+" · 等待任务";
        if(p.s("joinActions","emoji:EveOneCat2").equals("emoji:EveOneCat2"))p.put("joinActions","text:来了~");
        showOverlay();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"pocket:runner");wake.acquire(4*60*60*1000L);
        repo.log("启动",p.sourceName()+"；保持手机解锁及屏幕亮起");if(p.webMode()){if(web!=null)web.refresh();}else if(QQListener.instance!=null)QQListener.instance.refresh();
    }
    boolean notificationEnabled(){String v=android.provider.Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return v!=null&&v.contains(getPackageName());}
    public void stop(String reason,boolean lock){
        if(roomNavigator!=null)roomNavigator.cancel();
        running=false;busy=false;pendingFinish=false;leaving=false;homeReady=false;generation++;status=reason;if(p!=null){p.put("listen",false);p.put("sleepUntil","0");if(lock)p.put("verificationLock",reason);}
        if(repo!=null){repo.log("停止",reason);if(current!=null){repo.state(current.id,"无法确认",reason);current=null;}if(retryEntry!=null){repo.state(retryEntry.id,"无法确认",reason);retryEntry=null;}}if(overlay!=null){wm.removeView(overlay);overlay=null;}if(wake!=null&&wake.isHeld())wake.release();
    }
    void showOverlay(){
        if(overlay!=null)return;
        LinearLayout box=new LinearLayout(this);box.setPadding(16,10,10,10);box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xee251c40,0xee32304c});bg.setCornerRadius(22);box.setBackground(bg);
        overlayText=new TextView(this);overlayText.setTextColor(0xffe7b5ff);overlayText.setTextSize(12);box.addView(overlayText);
        TextView close=new TextView(this);close.setText("  ×  ");close.setTextSize(24);close.setTextColor(0xffffb4c9);box.addView(close);close.setOnClickListener(v->stop("悬浮窗手动停止",false));
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;lp.x=16;lp.y=140;
        final float[] drag=new float[4];overlayText.setOnTouchListener((v,e)->{if(e.getAction()==0){drag[0]=e.getRawX();drag[1]=e.getRawY();drag[2]=lp.x;drag[3]=lp.y;}else if(e.getAction()==2){lp.x=(int)(drag[2]+e.getRawX()-drag[0]);lp.y=(int)(drag[3]+e.getRawY()-drag[1]);wm.updateViewLayout(box,lp);}return true;});
        overlay=box;wm.addView(box,lp);
    }
    void step(){
        if(!running)return;long now=System.currentTimeMillis();
        if(overlayText!=null){overlayText.setText(status+"\n"+(room.isEmpty()?"等待红包":room)+" · 连续未中 "+p.n("losses",0));overlayText.setTextColor(new int[]{0xffe7b5ff,0xff8fe5ff,0xffffb4c9}[(int)((now/2000)%3)]);}
        if(busy||now<nextAction)return;
        if(pendingFinish&&current!=null){pendingFinish=false;finishRound();return;}
        if(!((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){status="屏幕已关闭 · 等待解锁";return;}
        android.app.KeyguardManager kg=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);if(kg.isKeyguardLocked()){status="手机锁定 · 等待解锁";return;}
        if(leaving){checkHomeExit(now);return;}
        if(current==null&&!room.isEmpty()){
            if(!stay.due(now)){status="留存至 "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(stay.until()));return;}
            beginHomeExit("留存结束");return;
        }
        // Leaving a completed room must run even while the loss-limit rest timer is active.
        if(p.until()>now){status="休眠至 "+new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(p.until()));return;}
        if(p.until()!=0){p.clearLosses();status="休眠结束 · 继续监听";}
        List<Repo.Task> tasks=repo.tasksForSource(p.sourceKey());
        if(current==null){
            if(p.webMode()&&now-p.l("webLastSuccess",0)>120000){status="等待网页最新数据 · 暂不进房";return;}
            for(Repo.Task t:tasks)if(t.state.equals("等待")){
                if(PacketParser.blacklisted(p.s("blacklist",""),t.room)){repo.state(t.id,"黑名单","");continue;}
                if(t.end<=now){repo.state(t.id,"已过期","");continue;}
                if(now>=timing(t).enterAt(t.end)){if(!homeReady){beginHomeExit("进房前清理页面");return;}enter(t);return;}
            }
            status=room.isEmpty()?(p.webMode()?"监控网页":"监听 QQ")+" · 等待进房时间":"留在直播间 · 等待下一任务";return;
        }
        if(choosePrimaryApp()){nextAction=now+650;return;}
        for(AccessibilityWindowInfo win:getWindows()){AccessibilityNodeInfo chooser=win.getRoot();if(chooser!=null&&"com.miui.securitycore".contentEquals(chooser.getPackageName()==null?"":chooser.getPackageName())){status="等待系统应用选择 · 不重复启动房间链接";if(now-entered>90000){stop("无法确认主应用选择，请人工处理系统选择框",false);}return;}}
        // Only manipulate Bilibili. Other foreground apps pause the workflow.
        AccessibilityNodeInfo root=biliRoot();String pkg=root==null?"":String.valueOf(root.getPackageName());
        boolean controls=false;if(root!=null){List<AccessibilityNodeInfo> found=new ArrayList<>();walk(root,found);for(AccessibilityNodeInfo n:found){String id=n.getViewIdResourceName();if(id!=null&&(id.endsWith("/live_send_danmaku")||id.endsWith("/bt_emotion_show")))controls=true;}}
        if(controls)entryConfirmed=true;
        if(entryConfirmed&&!pkg.equals("tv.danmaku.bili")){status="任务已参与进房流程 · 等待切回B站";return;}
        if(!pkg.equals("tv.danmaku.bili")||!entryConfirmed){
            if(now-entered<15000){status="等待 B站前台";return;}
            if(enterAttempts<2){retryRoomEntry();return;}
            repo.state(current.id,"进房失败","两次失败，今天跳过此房间");p.put("failed."+java.time.LocalDate.now()+"."+current.room,true);current=null;status="两次进房失败 · 跳过";return;
        }
        if(!visitRecorded){firstVisit=repo.visit(room);visitRecorded=true;}
        int biliWindows=0;for(AccessibilityWindowInfo win:getWindows()){AccessibilityNodeInfo node=win.getRoot();if(node!=null&&"tv.danmaku.bili".contentEquals(node.getPackageName()==null?"":node.getPackageName()))biliWindows++;}
        if(biliWindows>1){status="检测到双小窗口 · 请先完成多窗口路由验证";return;}
        Rect display=wm.getMaximumWindowMetrics().getBounds();
        Screen nativeScreen=Screen.nativePanel(nodes(),display.width(),display.height());
        boolean nativeAction=nativeScreen.panelExact("抢","抢红包","立即参与")!=null||nativeScreen.panelHas("已参与")||nativeScreen.panelHas("开奖")&&(submitted||confirmed)||nativeScreen.amount()<=20;
        if(!nativeScreen.won()&&(nativeScreen.panelHas("发的红包")&&nativeScreen.amount()>0&&nativeAction||nativeScreen.labels.stream().anyMatch(l->ResultEvidence.loss(l.text)))){process(nativeScreen);return;}
        busy=true;long g=generation;
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult result){
                Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                Bitmap bitmap=hardware==null?null:hardware.copy(Bitmap.Config.ARGB_8888,false);
                if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();
                if(bitmap==null){busy=false;stop("截图读取失败",true);return;}
                screenWidth=bitmap.getWidth();screenHeight=bitmap.getHeight();
                if(!running||g!=generation){bitmap.recycle();busy=false;return;}
                Screen.read(bitmap,s->{busy=false;if(running&&g==generation)process(s);},e->{busy=false;stop("文字识别失败："+e.getMessage(),true);});
            }
            public void onFailure(int code){busy=false;if(code==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT){nextAction=System.currentTimeMillis()+400;return;}stop("截图失败 "+code+"，检查无障碍权限或安全窗口",true);}
        });
    }
    void enter(Repo.Task t){
        if(p.b("failed."+java.time.LocalDate.now()+"."+t.room,false)){repo.state(t.id,"已跳过","同日两次进房失败");return;}
        timing(t);
        if(!room.equals(t.room)&&(!room.isEmpty()||!homeReady)){stop("未确认旧直播间退出，暂停进房以避免页面叠加",false);return;}
        boolean newRoom=!room.equals(t.room);current=t;
        if(newRoom){visitRecorded=false;firstVisit=false;packetX=-1;packetY=-1;room=t.room;p.put("activeRoom",room);homeReady=false;openRoom(t.room);if(!running)return;}
        current=t;entered=System.currentTimeMillis();enterAttempts=1;scans=0;winAmountChecks=0;lastResultRefresh=0;lossHintTask=-1;lossHint="";confirmed=roundConfirmed.contains(t.id);submitted=roundSubmitted.contains(t.id);visitSent=false;
        repo.state(t.id,"进房中","");status="进入 "+room;nextAction=entered+1200;
    }
    void openRoom(String id){
        liveActivity=false;entryConfirmed=false;Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("bilibili://live/"+id));
        // Resolve the installed Bili handler. HyperOS may still show its clone chooser,
        // which choosePrimaryApp handles using the user-confirmed left/main choice.
        i.setPackage("tv.danmaku.bili");android.content.pm.ResolveInfo target=getPackageManager().resolveActivity(i,0);
        if(target==null||target.activityInfo==null||!"tv.danmaku.bili".equals(target.activityInfo.packageName)){stop("无法确认B站房间链接处理入口",false);return;}
        i.setComponent(new ComponentName(target.activityInfo.packageName,target.activityInfo.name));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
        repo.log("房间直达",id+" · "+target.activityInfo.name);
    }
    void process(Screen s){
        if(current==null)return;
        long now=System.currentTimeMillis();
        if(s.entry!=null){packetX=s.entry.centerX();packetY=s.entry.centerY();}
        // Only visible dialog labels in the central region trigger the safety latch.
        for(Screen.Label l:s.labels)if(l.rect.centerY()>s.height*.18&&l.rect.centerY()<s.height*.85&&l.text.matches(".*(人机验证|安全验证|请完成验证|拖动滑块|操作频繁|账号异常|验证后继续).*")){
            String reason="检测到验证/限制："+l.text;stop(reason,true);return;
        }
        if(s.has("无法进入直播间")||s.has("你已被拉黑")||s.has("直播间不存在")){
            if(enterAttempts<2){retryRoomEntry();return;}
            p.put("failed."+java.time.LocalDate.now()+"."+room,true);repo.state(current.id,"进房失败",s.panelText());current=null;return;
        }
        if(s.has("点个关注吧")||s.has("关注主播不迷路")){if(!clickNode("关闭")){if(p.s("followClose","").isEmpty()){stop("关注弹窗需要标定关闭按钮",false);return;}tapRelative(p.s("followClose",""));}nextAction=now+1500;return;}
        for(Screen.Label l:s.labels)if(l.rect.centerX()>s.width*.15&&l.rect.centerX()<s.width*.85&&l.rect.centerY()>s.height*.20&&l.text.replaceAll("\\s","").matches("^(确认支付|立即支付|去充值|充值|余额不足|确认购买|开通大航海)$")){
            stop("遇到其他付费/充值确认："+l.text,true);return;
        }
        if(s.won()&&confirmed&&now>=current.end-3000){
            Integer battery=s.prizeBattery();if(battery==null&&++winAmountChecks<4){status="中奖 · 等待奖品数额加载";nextAction=now+500;return;}
            if(repo.win(current.id,battery,battery==null?"":"电池",s.panelText()+"\n独立奖品识别："+(battery==null?"数额待确认":battery+"电池"))){
                p.put("losses","0");p.put("sleepUntil","0");status="中奖 "+(battery==null?"（金额待确认）":battery+"电池")+" · 连续未中已清零";
                long g=generation;busy=true;Screen.Label accept=s.panelExact("开心收下","收下奖励");
                h.postDelayed(()->{if(!running||g!=generation)return;if(accept!=null)tap(accept.rect.centerX(),accept.rect.centerY());h.postDelayed(()->{if(!running||g!=generation)return;busy=false;if(firstVisit)sendActions("win",accept!=null);finishRound();},500);},300);
                return;
            }finishRound();return;
        }
        if(now>=current.end){
            if(now-lastDrawLog>2500){lastDrawLog=now;repo.log("开奖面板",s.panelText().isEmpty()?"没有可确认的红包文字":s.panelText());}
            if(s.panelHas("未参与")){repo.state(current.id,"未参与",s.panelText());finishRound();return;}
            boolean loss=lossHintTask==current.id;
            if(ResultEvidence.inWindow(now,current.end,confirmed)){
                for(Screen.Label l:s.labels)if(ResultEvidence.loss(l.text)&&l.rect.centerX()>s.width*.30&&l.rect.centerX()<s.width*.70&&l.rect.centerY()>s.height*.15&&l.rect.centerY()<s.height*.95){loss=true;lossHint=l.text;}
                for(AccessibilityNodeInfo n:nodes()){String id=n.getViewIdResourceName();if(!n.isEditable()&&n.isVisibleToUser()&&(id==null||!id.equals("tv.danmaku.bili:id/text"))&&ResultEvidence.loss(String.valueOf(n.getText()))){loss=true;lossHint=String.valueOf(n.getText());}}
            }
            if(loss||s.panelHas("未中奖")||s.panelHas("很遗憾")||s.panelHas("未能中奖")){
                repo.state(current.id,confirmed?"未中奖":"未参与",s.panelText()+"\n结果提示："+lossHint);
            if(confirmed){int losses=p.n("losses",0)+1;p.put("losses",""+losses);if(firstVisit)sendActions("lose");if(losses>=Math.max(1,p.n("lossLimit",10))){
                    long wakeAt=now+Math.max(1,p.n("restMinutes",60))*60000L;p.put("sleepUntil",""+wakeAt);repo.log("自动休眠","连续未中奖达到上限，唤醒时间 "+wakeAt);
                }}finishRound();return;
            }
            if(now>current.end+Math.max(20,p.n("after",30))*1000L){repo.state(current.id,"无法确认","没有明确中奖或未中奖证据，不计连续未中奖");finishRound();return;}
            if(s.panelHas("发的红包")||s.panelHas("开奖")){
                if(now>=current.end+3000&&now-lastResultRefresh>7000){lastResultRefresh=now;closePacket();h.postDelayed(()->{if(running&&current!=null)openPacket();},800);repo.log("开奖复查","重新打开红包读取结果");}
                status="等待开奖结果";nextAction=now+2200;return;
            }
            openPacket();nextAction=now+2200;return;
        }
        if(confirmed){status="已参与 · 等待开奖";if(!s.panelHas("发的红包")&&!s.panelHas("开奖")){openPacket();nextAction=now+3000;}return;}
        if(s.panelHas("已参与")||(submitted&&s.panelHas("开奖")&&!s.panelHas("未参与")&&s.panelExact("抢")==null)){
            alignConfirmedRound(s,now);
            confirmed=true;roundConfirmed.add(current.id);repo.state(current.id,"已参与",s.panelText());status="已参与 · 保持红包面板";
            if(firstVisit&&!visitSent){visitSent=true;sendActions("join");}return;
        }
        if(s.panelHas("发的红包")||s.panelHas("共")&&s.panelHas("开奖")){
            int amount=s.amount();
            if(amount<=20&&amount>=0){repo.log("跳过20电池","房间 "+room);swipe();nextAction=now+1800;if(++scans>8){repo.state(current.id,"已跳过","未找到超过20电池的匹配红包");finishRound();}return;}
            // Only this card's own countdown can identify its draw; the header can show another card.
            int secs=s.panelSeconds();
            if(!ClaimDecision.matchingCard(current.amount,amount,secs,current.end,now)){status="核对当前红包卡片";if(++scans<=8){swipe();nextAction=now+450;}else {repo.state(current.id,"无法确认","面板金额或时间无法与任务匹配，未提交");finishRound();}return;}
            Screen.Label grab=s.panelExact("抢","抢红包","立即参与");
            if(grab!=null&&!submitted){
                String panel=s.panelText().replaceAll("\\s","");
                if(panel.contains("需入团")){
                    if(!panel.contains("需入团（1电池）")&&!panel.contains("需入团(1电池)")){stop("入团费用不是明确的1电池，停止",true);return;}
                    if(!p.b("allowOneBattery",false)){repo.state(current.id,"已跳过","需花费1电池入团，未授权付费");finishRound();return;}
                    if(p.b("membershipSpent."+room,false)){repo.state(current.id,"已跳过","此房间已尝试过1电池入团，不再次付费");finishRound();return;}
                    p.put("membershipSpent."+room,true);repo.log("入团授权使用","房间 "+room+"，本房间仅此次，1电池上限");
                }
                submitted=true;roundSubmitted.add(current.id);tap(grab.rect.centerX(),grab.rect.centerY());repo.state(current.id,"已点击待确认",s.panelText());nextAction=now+650;status="已点击 · 核对参与状态";return;
            }
            status=submitted?"提交后未确认 · 不重复点击":"未发现可用的抢按钮";return;
        }
        openPacket();nextAction=now+500;
        if(++scans>12){repo.state(current.id,"无法确认","未找到红包面板");finishRound();}
    }
    void alignConfirmedRound(Screen screen,long now){
        int seconds=screen.panelSeconds();if(seconds<0||seconds>86400)return;long end=now+seconds*1000L;
        if(Math.abs(end-current.end)<12000)return;
        for(Repo.Task task:repo.tasksForSource(p.sourceKey()))if(task.id!=current.id&&task.room.equals(current.room)&&task.amount==screen.amount()&&Math.abs(task.end-end)<12000&&task.state.equals("等待")){
            long old=current.id;repo.state(old,"等待","已参与卡片对应同房另一开奖任务，原任务继续排队");roundSubmitted.remove(old);current=task;roundSubmitted.add(task.id);return;
        }repo.alignEnd(current,end);
    }
    void finishRound(){
        if(current==null)return;
        if(busy){pendingFinish=true;return;}
        long now=System.currentTimeMillis();String doneRoom=current.room;stay.include(current.end,timing(current));p.put("roomLeaveAt",""+stay.until());current=null;
        if(p.until()<=now)for(Repo.Task t:repo.tasksForSource(p.sourceKey()))if(t.room.equals(doneRoom)&&t.state.equals("等待")&&t.end>now&&!PacketParser.blacklisted(p.s("blacklist",""),t.room)){
            enter(t);if(!running)return;visitSent=true;swipe();nextAction=now+500;status="切换同房下一个红包";return;
        }
        nextAction=0;status="本轮结束 · 留存后退出直播间";
    }
    TaskTiming timing(Repo.Task task){
        TaskTiming value=timings.get(task.id);if(value==null){value=p.taskTiming(task.id);timings.put(task.id,value);repo.log("任务时间",task.room+" · 任务 "+task.id+" · 提前 "+value.beforeSeconds()+" 秒，开奖后留存 "+value.holdSeconds()+" 秒");}return value;
    }
    void retryRoomEntry(){
        retryEntry=current;current=null;stay.clear();p.put("roomLeaveAt","0");beginHomeExit("第二次进房前清理页面");
    }
    void beginHomeExit(String reason){
        int windows=0;for(AccessibilityWindowInfo win:getWindows()){AccessibilityNodeInfo n=win.getRoot();if(n!=null&&"tv.danmaku.bili".contentEquals(n.getPackageName()==null?"":n.getPackageName()))windows++;}
        if(windows>1){stop("检测到多个B站窗口，暂停单账号退出以免影响另一个账号",false);return;}
        for(AccessibilityNodeInfo n:nodes())if(n.isVisibleToUser()&&String.valueOf(n.getText()).matches(".*(人机验证|安全验证|请完成验证|拖动滑块|账号异常|操作频繁).*")){stop("退出前检测到验证/限制，请人工处理",true);return;}
        leaving=true;homeReady=false;exitAttempts=0;repo.log("准备退出直播间",(room.isEmpty()?"启动清理":room)+" · "+reason);launchHome();
    }
    void launchHome(){
        Intent home=getPackageManager().getLaunchIntentForPackage("tv.danmaku.bili");
        if(home==null){stop("没有找到B站首页入口，未继续叠加直播间",false);return;}
        // Reset the Bili task to its exported launcher. Never use HOME, force-stop or blind BACK.
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);mainActivityObserved=false;exitStarted=System.currentTimeMillis();exitAttempts++;status="正在退出直播间 · 确认B站首页";
        try{startActivity(home);nextAction=exitStarted+800;}catch(RuntimeException e){stop("返回B站首页失败："+e.getClass().getSimpleName(),false);}
    }
    void checkHomeExit(long now){
        if(choosePrimaryApp()){nextAction=now+650;return;}
        AccessibilityNodeInfo root=biliRoot();List<AccessibilityNodeInfo> all=new ArrayList<>();walk(root,all);List<String> labels=new ArrayList<>();boolean liveControls=false;
        for(AccessibilityNodeInfo n:all)if(n.isVisibleToUser()){
            String id=n.getViewIdResourceName();if(id!=null&&(id.endsWith("/live_send_danmaku")||id.endsWith("/bt_emotion_show")))liveControls=true;
            String value=String.valueOf(n.getText()).trim();labels.add(value);
            if(value.matches(".*(人机验证|安全验证|请完成验证|拖动滑块|账号异常|操作频繁).*")){stop("退出时检测到验证/限制，请人工处理",true);return;}
        }
        if(HomeEvidence.confirmed(root!=null,liveControls,mainActivityObserved,labels)){
            repo.log("已退出直播间",room.isEmpty()?"已确认B站首页，允许进房":room+" · 已确认B站首页，旧页面栈已清理");
            room="";p.put("activeRoom","");stay.clear();p.put("roomLeaveAt","0");leaving=false;homeReady=true;entryConfirmed=false;liveActivity=false;visitRecorded=false;packetX=-1;packetY=-1;nextAction=0;status="已退出直播间 · 等待下一任务";
            if(retryEntry!=null){Repo.Task task=retryEntry;retryEntry=null;enter(task);enterAttempts=2;}return;
        }
        if(now-exitStarted>=8000){if(exitAttempts<2){repo.log("退出复查","未确认B站首页，仅重试返回首页，不进新房间");launchHome();}else stop("两次返回首页后仍未确认退出，已暂停，避免直播间页面叠加",false);}
    }
    void openPacket(){if(packetX>=0&&packetY>=0)tap(packetX,packetY);else tapRelative(p.s("packetPoint","0.08,0.17"));}
    void closePacket(){tapRelative(p.s("packetClose","0.782,0.479"));}
    void swipe(){Path path=new Path();Rect d=wm.getMaximumWindowMetrics().getBounds();path.moveTo(d.width()*.82f,d.height()*.72f);path.lineTo(d.width()*.22f,d.height()*.72f);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,280)).build(),null,null);}
    void tapRelative(String value){try{String[] a=value.split(",");Rect display=((WindowManager)getSystemService(WINDOW_SERVICE)).getMaximumWindowMetrics().getBounds();int width=screenWidth>0?screenWidth:display.width(),height=screenHeight>0?screenHeight:display.height();tap((int)(Float.parseFloat(a[0])*width),(int)(Float.parseFloat(a[1])*height));}catch(Exception e){stop("坐标设置无效",true);}}
    void tap(int x,int y){Path path=new Path();path.moveTo(x,y);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build(),null,null);}
    AccessibilityNodeInfo biliRoot(){for(AccessibilityWindowInfo win:getWindows())if(win.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){AccessibilityNodeInfo root=win.getRoot();if(root!=null)return "tv.danmaku.bili".contentEquals(root.getPackageName()==null?"":root.getPackageName())?root:null;}return null;}
    boolean choosePrimaryApp(){
        for(AccessibilityWindowInfo win:getWindows()){
            AccessibilityNodeInfo root=win.getRoot();if(root==null||!"com.miui.securitycore".contentEquals(root.getPackageName()==null?"":root.getPackageName()))continue;
            List<AccessibilityNodeInfo> all=new ArrayList<>();walk(root,all);boolean title=false;List<AccessibilityNodeInfo> apps=new ArrayList<>();
            for(AccessibilityNodeInfo n:all){String text=String.valueOf(n.getText());if(text.equals("请选择要使用的应用"))title=true;if(text.equals("哔哩哔哩"))apps.add(n);}
            // HyperOS presents the owner app on the left and the badged clone on the right.
            if(!title)return false;
            if(apps.size()!=2){
                for(AccessibilityNodeInfo n:all)if("请选择要使用的应用".contentEquals(n.getText()==null?"":n.getText())){
                    Rect titleRect=new Rect();n.getBoundsInScreen(titleRect);
                    android.util.DisplayMetrics d=getResources().getDisplayMetrics();
                    // User-confirmed HyperOS layout: unbadged owner app on the left.
                    tap(d.widthPixels/3,titleRect.bottom+(int)(d.heightPixels*.055));entered=System.currentTimeMillis();
                    status="已点击左侧主哔哩哔哩";repo.log("应用选择","按已确认的系统选择框布局点击左侧主应用");return true;
                }return false;
            }
            apps.sort(Comparator.comparingInt(n->{Rect r=new Rect();n.getBoundsInScreen(r);return r.centerX();}));
            Rect left=new Rect(),right=new Rect();apps.get(0).getBoundsInScreen(left);apps.get(1).getBoundsInScreen(right);
            if(left.centerX()>=getResources().getDisplayMetrics().widthPixels*.5||right.centerX()<=getResources().getDisplayMetrics().widthPixels*.5)return false;
            tap(left.centerX(),left.centerY());entered=System.currentTimeMillis();status="已选择主哔哩哔哩";repo.log("应用选择","HyperOS 主应用（左侧），不选择双开分身");return true;
        }return false;
    }
    List<AccessibilityNodeInfo> nodes(){List<AccessibilityNodeInfo> list=new ArrayList<>();walk(biliRoot(),list);return list;}
    void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out){walk(n,out,0);}
    void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out,int depth){if(n==null||depth>70||out.size()>=3000)return;out.add(n);for(int i=0;i<n.getChildCount()&&out.size()<3000;i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null&&!child.equals(n))walk(child,out,depth+1);}}
    boolean clickNode(String text){for(AccessibilityNodeInfo n:nodes()){String t=String.valueOf(n.getText())+" "+String.valueOf(n.getContentDescription());if(t.contains(text)&&n.isVisibleToUser()){AccessibilityNodeInfo a=n;for(int depth=0;a!=null&&depth<8;depth++){if(a.isClickable()&&a.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;AccessibilityNodeInfo parent=a.getParent();if(a.equals(parent))break;a=parent;}}}return false;}
    boolean clickId(String id){for(AccessibilityNodeInfo n:nodes())if(id.equals(n.getViewIdResourceName())&&n.isVisibleToUser()){Rect b=new Rect();n.getBoundsInScreen(b);if(b.width()>0&&b.height()>0){tap(b.centerX(),b.centerY());return true;}}return false;}
    void sendActions(String event){sendActions(event,false);}
    void sendActions(String event,boolean panelClosed){
        if(!p.b("messages",true))return;
        String raw=p.s(event+"Actions",event.equals("join")?"text:来了~":event.equals("win")?"text:谢谢老板":"");
        List<String> actions=new ArrayList<>();for(String a:raw.split("\n"))if(!a.trim().isEmpty())actions.add(a.trim());
        if(actions.isEmpty())return;
        long g=generation;busy=true;
        // Close the packet using a configured close target, never Android BACK.
        if(!panelClosed)closePacket();h.postDelayed(()->action(actions,0,g),panelClosed?200:500);
    }
    void action(List<String> actions,int i,long g){
        if(!running||g!=generation){busy=false;return;}
        if(i>=actions.size()){dismissComposer(g,()->{busy=false;if(pendingFinish){pendingFinish=false;finishRound();}else openPacket();},0);return;}
        for(AccessibilityNodeInfo node:nodes()){String label=String.valueOf(node.getText());if(label.matches(".*(人机验证|安全验证|请完成验证|拖动滑块).*")){stop("消息阶段检测到验证",true);return;}}
        String a=actions.get(i);
        if(a.startsWith("text:")){
            String[] pool=a.substring(5).trim().split("\\|");String text=pool[new Random().nextInt(pool.length)];
            if(!clickId("tv.danmaku.bili:id/live_send_danmaku")){repo.log("消息未发送","没有找到直播弹幕入口");h.postDelayed(()->action(actions,i+1,g),500);return;}
            h.postDelayed(()->fillComposer(text,actions,i,g,0),700);
        }else if(a.startsWith("emoji:")){
            // Sticker sequences are explicitly recorded by the user in app settings.
            String sequence=p.s("emoji."+a.substring(6).trim(),"");String[] candidates=sequence.split(";");
            if(sequence.isEmpty()){repo.log("表情未发送",a+" 尚未标定点击路径");h.postDelayed(()->action(actions,i+1,g),200);return;}
            String chosen=candidates[new Random().nextInt(candidates.length)];String[] points=chosen.split(" ");gestureSequence(points,0,()->action(actions,i+1,g),g);
        }else {repo.log("动作忽略","未知格式："+a);h.postDelayed(()->action(actions,i+1,g),200);}
    }
    void fillComposer(String text,List<String> actions,int index,long g,int attempt){
        if(!running||g!=generation){busy=false;return;}
        AccessibilityNodeInfo edit=null;for(AccessibilityNodeInfo n:nodes())if("tv.danmaku.bili:id/danmu_input".equals(n.getViewIdResourceName())&&n.isVisibleToUser())edit=n;
        if(edit==null){if(attempt<8){h.postDelayed(()->fillComposer(text,actions,index,g,attempt+1),400);return;}repo.log("消息未发送","弹幕输入框在4秒内未出现");action(actions,index+1,g);return;}
        Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        if(!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){repo.log("消息未发送","输入失败");action(actions,index+1,g);return;}
        h.postDelayed(()->{
            if(!running||g!=generation){busy=false;return;}
            if(!clickId("tv.danmaku.bili:id/send")){repo.log("消息未发送","发送按钮未找到");action(actions,index+1,g);return;}
            h.postDelayed(()->{
                if(!running||g!=generation){busy=false;return;}
                boolean remaining=false;for(AccessibilityNodeInfo n:nodes())if("tv.danmaku.bili:id/danmu_input".equals(n.getViewIdResourceName())&&!n.isShowingHintText()&&text.contentEquals(n.getText()==null?"":n.getText()))remaining=true;
                repo.log(remaining?"消息未发送":"弹幕提交",remaining?"点击发送后内容仍在输入框，未重复发送":text+" · 已点击发送且输入框已清空/关闭，送达以直播间为准");action(actions,index+1,g);
            },1000);
        },500);
    }
    void gestureSequence(String[] points,int i,Runnable done,long g){if(!running||g!=generation){busy=false;return;}if(i>=points.length){done.run();return;}tapRelative(points[i]);h.postDelayed(()->gestureSequence(points,i+1,done,g),850);}
    void dismissComposer(long g,Runnable done,int count){
        if(!running||g!=generation){busy=false;return;}
        boolean input=false;for(AccessibilityNodeInfo n:nodes())if("tv.danmaku.bili:id/danmu_input".equals(n.getViewIdResourceName()))input=true;
        if(!input){done.run();return;}if(count>=2){repo.log("输入框未关闭","暂停消息动作，避免误退直播间");busy=false;return;}
        performGlobalAction(GLOBAL_ACTION_BACK);h.postDelayed(()->dismissComposer(g,done,count+1),650);
    }
    public void recordPoint(String key){
        stop("标定按钮",false);
        Intent launch=getPackageManager().getLaunchIntentForPackage("tv.danmaku.bili");if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);}
        h.postDelayed(()->{
            TextView shield=new TextView(this);shield.setText("请点击要标定的按钮 · 本次只记录，不执行点击");shield.setTextColor(0xffffb4e2);shield.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);shield.setPadding(10,200,10,0);shield.setBackgroundColor(0x22332044);
            WindowManager.LayoutParams full=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
            shield.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){android.util.DisplayMetrics d=getResources().getDisplayMetrics();p.put(key,String.format(Locale.US,"%.4f,%.4f",e.getRawX()/d.widthPixels,e.getRawY()/d.heightPixels));wm.removeView(shield);repo.log("标定保存",key); }return true;});wm.addView(shield,full);
        },1200);
    }
    public String windowReport(){
        StringBuilder report=new StringBuilder("可读取窗口（非账号身份识别）：\n");int count=0;
        for(AccessibilityWindowInfo w:getWindows()){
            AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
            Rect bounds=new Rect();w.getBoundsInScreen(bounds);
            String pkg=String.valueOf(root.getPackageName());
            report.append("ID ").append(w.getId()).append(" · ").append(pkg).append("\n").append(bounds.toShortString()).append(w.isActive()?" · 活动":"").append("\n");
            if(pkg.equals("tv.danmaku.bili"))count++;
        }
        report.append("B站可读取窗口数：").append(count);
        for(AccessibilityWindowInfo w:getWindows()){
            AccessibilityNodeInfo root=w.getRoot();if(root==null||!"tv.danmaku.bili".contentEquals(root.getPackageName()==null?"":root.getPackageName()))continue;
            List<AccessibilityNodeInfo> all=new ArrayList<>();walk(root,all);
            for(AccessibilityNodeInfo n:all){String id=n.getViewIdResourceName();Rect b=new Rect();n.getBoundsInScreen(b);
                if(n.isVisibleToUser()&&id!=null&&(id.matches(".*(danmu_input|/send|emotion.*|.*emoji.*|iv_emotion_switch|tab_icon|live_send_danmaku|live_.*close.*|.*packet.*)$")||n.isClickable()&&b.top>getResources().getDisplayMetrics().heightPixels*.58&&b.width()<500&&b.height()<500))
                    report.append("\n").append(id).append(' ').append(n.getText()).append(' ').append(n.getContentDescription()).append(' ').append(b.toShortString());
            }
        }
        android.util.Log.i("PocketWindows",report.toString());repo.log("多窗口诊断",report.toString());return report.toString();
    }
    public void previewFloatingWindow(boolean left){
        if(running){Toast.makeText(this,"请先停止任务，再测试双开窗口",Toast.LENGTH_LONG).show();return;}
        java.util.ArrayList<Rect> windows=new java.util.ArrayList<>();
        for(AccessibilityWindowInfo win:getWindows()){
            AccessibilityNodeInfo node=win.getRoot();if(node==null||!"tv.danmaku.bili".contentEquals(node.getPackageName()==null?"":node.getPackageName()))continue;
            Rect rect=new Rect();win.getBoundsInScreen(rect);windows.add(rect);
        }
        if(windows.size()!=2){repo.log("小窗放大测试","需要恰好两个可读取的B站窗口，实际 "+windows.size());return;}
        windows.sort(java.util.Comparator.comparingInt(Rect::centerX));Rect target=windows.get(left?0:1);
        repo.log("小窗放大测试",(left?"左":"右")+target.toShortString());
        tap(target.centerX(),target.centerY());h.postDelayed(()->windowReport(),1800);
    }
    java.util.Map<Integer,Rect> floatingRects(){
        java.util.Map<Integer,Rect> result=new java.util.HashMap<>();
        for(AccessibilityWindowInfo win:getWindows()){
            AccessibilityNodeInfo n=win.getRoot();if(n==null||!"tv.danmaku.bili".contentEquals(n.getPackageName()==null?"":n.getPackageName()))continue;
            Rect b=new Rect();win.getBoundsInScreen(b);result.put(win.getId(),b);
        }return result;
    }
    public void previewRoomRoute(String target,boolean backup){
        if(running){Toast.makeText(this,"请先停止任务",Toast.LENGTH_LONG).show();return;}
        if(!target.matches("[1-9][0-9]{0,14}")||PacketParser.blacklisted(p.s("blacklist",""),target)){repo.log("进房验证取消","房间号无效或在黑名单");return;}
        Map<Integer,Rect> before=floatingRects();
        if(before.size()!=2){repo.log("进房验证取消","需要两个可读取的B站窗口");return;}
        List<Integer> ids=new ArrayList<>(before.keySet());ids.sort(Comparator.comparingInt(id->before.get(id).centerX()));
        int primary=ids.get(p.b("primaryLeft",true)?0:1),secondary=ids.get(p.b("primaryLeft",true)?1:0);
        int expected=backup?secondary:primary,other=backup?primary:secondary;
        repo.log("进房路由验证",(backup?"备用":"主")+"账号，目标窗口 "+expected+"，房间 "+target);
        roomNavigator=new RoomNavigator(this,target,backup,expected,other,before.get(expected));
        roomNavigator.start();
    }
    public void previewFloatingCycle(boolean left){
        if(running){Toast.makeText(this,"请先停止任务",Toast.LENGTH_LONG).show();return;}
        java.util.Map<Integer,Rect> before=floatingRects();
        if(before.size()!=2){repo.log("窗口切换失败","需要两个可读取的B站窗口");return;}
        java.util.List<Integer> ids=new java.util.ArrayList<>(before.keySet());ids.sort(java.util.Comparator.comparingInt(id->before.get(id).centerX()));
        int selected=ids.get(left?0:1),other=ids.get(left?1:0);Rect mini=before.get(selected);
        if(mini.width()>getResources().getDisplayMetrics().widthPixels*.45){repo.log("窗口切换失败","请先把两个窗口恢复为迷你窗");return;}
        tap(mini.centerX(),mini.centerY());
        h.postDelayed(()->{
            java.util.Map<Integer,Rect> expanded=floatingRects();Rect large=expanded.get(selected);
            if(large==null||large.width()<mini.width()*1.4||!expanded.containsKey(other)){repo.log("窗口切换失败","未确认目标放大或另一窗口已不可读");windowReport();return;}
            repo.log("窗口已放大",selected+" "+large.toShortString());
            // This phone shrinks by holding the opposite bottom corner and dragging diagonally.
            // Use a continued stroke so the initial hold really happens before movement.
            float inset=Math.max(2,Math.min(12,large.width()*.015f));
            float startX=left?large.right-inset:large.left+inset,startY=large.bottom-inset;
            android.util.DisplayMetrics d=getResources().getDisplayMetrics();
            float endX=d.widthPixels*(left?.12f:.88f),endY=d.heightPixels*.16f;
            Path holdPath=new Path();holdPath.moveTo(startX,startY);
            GestureDescription.StrokeDescription hold=new GestureDescription.StrokeDescription(holdPath,0,400,true);
            Path dragPath=new Path();dragPath.moveTo(startX,startY);dragPath.lineTo(endX,endY);
            Runnable verify=()->h.postDelayed(()->{
                java.util.Map<Integer,Rect> after=floatingRects();Rect small=after.get(selected);
                if(small!=null&&small.width()<=mini.width()*1.25&&after.containsKey(other)){
                    p.put(left?"leftCycleVerified":"rightCycleVerified",true);repo.log("窗口循环成功",selected+" 放大→缩小，另一窗口仍可读取 "+small.toShortString());
                }else{repo.log("窗口缩小待标定","已执行下角长按斜拖，但没有确认缩小");}
                windowReport();
            },2000);
            h.postDelayed(()->{
                if(running){repo.log("窗口测试取消","任务已开始运行");return;}
                repo.log("窗口缩小手势",(left?"右下角→左上":"左下角→右上")+" · 长按400ms后拖动900ms");
                boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(hold).build(),new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription gesture){
                        Path movement=dragPath;
                        if(running){movement=new Path();movement.moveTo(startX,startY);}
                        GestureDescription.StrokeDescription drag=hold.continueStroke(movement,0,running?1:900,false);
                        if(!dispatchGesture(new GestureDescription.Builder().addStroke(drag).build(),new GestureResultCallback(){
                            @Override public void onCompleted(GestureDescription g){verify.run();}
                            @Override public void onCancelled(GestureDescription g){repo.log("窗口缩小失败","拖动被系统取消");}
                        },null))repo.log("窗口缩小失败","系统拒绝后续拖动");
                    }
                    @Override public void onCancelled(GestureDescription gesture){repo.log("窗口缩小失败","长按被系统取消");}
                },null);
                if(!accepted){repo.log("窗口缩小失败","系统未接受手势");return;}
            },2500);
        },2000);
    }
    public void recordEmoji(){
        stop("表情路径录制",false);
        Intent launch=getPackageManager().getLaunchIntentForPackage("tv.danmaku.bili");if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);}
        final java.util.ArrayList<String> steps=new java.util.ArrayList<>();
        LinearLayout tools=new LinearLayout(this);tools.setBackgroundColor(0xee30233f);
        TextView help=new TextView(this);help.setText("录制：点击屏幕记录并执行\n先进入直播间，再点击开始");help.setTextColor(0xffe7b5ff);help.setPadding(10,10,10,10);tools.addView(help);
        Button record=new Button(this);record.setText("开始");tools.addView(record);
        Button done=new Button(this);done.setText("保存");tools.addView(done);
        Button cancel=new Button(this);cancel.setText("取消");tools.addView(cancel);
        WindowManager.LayoutParams toolbar=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);toolbar.gravity=Gravity.TOP|Gravity.LEFT;toolbar.y=100;
        final View[] shield={null};final Runnable[] arm={null};
        arm[0]=()->{
            if(shield[0]!=null)return;
            View glass=new View(this);glass.setBackgroundColor(0x018800ff);
            WindowManager.LayoutParams full=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
            glass.setOnTouchListener((v,e)->{
                if(e.getAction()==android.view.MotionEvent.ACTION_UP){float x=e.getRawX(),y=e.getRawY();android.util.DisplayMetrics d=getResources().getDisplayMetrics();steps.add(String.format(java.util.Locale.US,"%.4f,%.4f",x/d.widthPixels,y/d.heightPixels));wm.removeView(glass);shield[0]=null;tap((int)x,(int)y);help.setText("已记录 "+steps.size()+" 步 · 保存前确认已点击发送");h.postDelayed(arm[0],1200);}return true;
            });shield[0]=glass;wm.addView(glass,full);wm.removeView(tools);wm.addView(tools,toolbar);
        };
        record.setOnClickListener(v->{record.setEnabled(false);arm[0].run();});
        final boolean[] finished={false};
        Runnable clean=()->{finished[0]=true;if(shield[0]!=null){wm.removeView(shield[0]);shield[0]=null;}wm.removeView(tools);};
        Runnable oldArm=arm[0];arm[0]=()->{if(!finished[0])oldArm.run();};
        done.setOnClickListener(v->{if(!steps.isEmpty()){String old=p.s("emoji.EveOneCat2","");p.put("emoji.EveOneCat2",old+(old.isEmpty()?"":";")+String.join(" ",steps));repo.log("表情路径保存","EveOneCat2 · "+steps.size()+" 步");}clean.run();});
        cancel.setOnClickListener(v->clean.run());wm.addView(tools,toolbar);
    }
    public void inspectPacket(){
        if(running){Toast.makeText(this,"先停止自动运行，再做只读检查",Toast.LENGTH_LONG).show();return;}
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult s){
                Bitmap hw=Bitmap.wrapHardwareBuffer(s.getHardwareBuffer(),s.getColorSpace());Bitmap copy=hw==null?null:hw.copy(Bitmap.Config.ARGB_8888,false);
                if(hw!=null)hw.recycle();s.getHardwareBuffer().close();if(copy==null)return;
                Screen.read(copy,screen->{String detail="面板金额="+screen.amount()+"，已参与="+screen.panelHas("已参与")+"\n"+screen.panelText();android.util.Log.i("PocketPacket",detail);repo.log("红包只读检查",detail);},error->{repo.log("红包只读检查失败",error.getMessage());android.util.Log.e("PocketPacket","read failed",error);});
            }
            public void onFailure(int code){repo.log("红包只读检查失败","截图错误 "+code);}
        });
    }
    void readNow(java.util.function.Consumer<Screen> done,java.util.function.Consumer<Exception> failed){
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){public void onSuccess(ScreenshotResult s){
            Bitmap hw=Bitmap.wrapHardwareBuffer(s.getHardwareBuffer(),s.getColorSpace());Bitmap copy=hw==null?null:hw.copy(Bitmap.Config.ARGB_8888,false);if(hw!=null)hw.recycle();s.getHardwareBuffer().close();
            if(copy==null){failed.accept(new IllegalStateException("截图读取失败"));return;}Screen.read(copy,done,failed);
        }public void onFailure(int code){failed.accept(new IllegalStateException("截图失败 "+code));}});
    }
}
