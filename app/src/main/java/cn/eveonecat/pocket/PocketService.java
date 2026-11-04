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
import java.io.*;

public class PocketService extends AccessibilityService {
    public static PocketService instance;
    final Handler h=new Handler(Looper.getMainLooper());
    Prefs p;Repo repo; boolean running,busy,firstVisit,confirmed,submitted,visitSent,pendingFinish;
    long generation=0,nextAction=0,entered=0,lastEvidence=0;int enterAttempts=0,scans=0;String status="待机",room="";
    boolean liveActivity=false,visitRecorded=false;
    Repo.Task current; final Set<Long> roundConfirmed=new HashSet<>(),roundSubmitted=new HashSet<>();
    WindowManager wm;View overlay;TextView overlayText;PowerManager.WakeLock wake;
    final Runnable tick=new Runnable(){public void run(){try{step();}catch(Exception e){stop("运行异常："+e.getClass().getSimpleName()+" "+e.getMessage(),true);}h.postDelayed(this,1800);}};
    @Override protected void onServiceConnected(){instance=this;p=new Prefs(this);repo=new Repo(this);repo.recover();wm=(WindowManager)getSystemService(WINDOW_SERVICE);h.post(tick);}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&"tv.danmaku.bili".contentEquals(e.getPackageName()==null?"":e.getPackageName())){
            String cls=String.valueOf(e.getClassName());if(cls.contains("LiveRoomActivity"))liveActivity=true;else if(cls.contains("MainActivity"))liveActivity=false;
        }
    }
    @Override public void onInterrupt(){stop("无障碍中断",false);}
    @Override public void onDestroy(){stop("服务关闭",false);h.removeCallbacksAndMessages(null);instance=null;if(repo!=null)repo.close();super.onDestroy();}
    boolean blocked(){return !p.s("verificationLock","").isEmpty();}
    public void start(){
        if(running)return;
        if(blocked()){status="验证锁定：请在设置确认处理后解除";return;}
        if(!notificationEnabled()){status="请先授权 QQ 通知读取";return;}
        p.put("listen",true);running=true;generation++;busy=false;nextAction=0;current=null;room="";status="监听 QQ · 等待任务";
        showOverlay();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"pocket:runner");wake.acquire(4*60*60*1000L);
        repo.log("启动","独立手机模式，QQ 通知监听；保持手机解锁及屏幕亮起");
    }
    boolean notificationEnabled(){String v=android.provider.Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return v!=null&&v.contains(getPackageName());}
    public void stop(String reason,boolean lock){
        running=false;busy=false;pendingFinish=false;generation++;status=reason;if(p!=null){p.put("listen",false);p.put("sleepUntil","0");if(lock)p.put("verificationLock",reason);}
        if(repo!=null){repo.log("停止",reason);if(current!=null){repo.state(current.id,"无法确认",reason);current=null;}}if(overlay!=null){wm.removeView(overlay);overlay=null;}if(wake!=null&&wake.isHeld())wake.release();
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
        if(p.until()>now){status="休眠至 "+new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(p.until()));return;}
        if(p.until()!=0){p.put("sleepUntil","0");p.put("losses","0");status="休眠结束 · 继续监听";}
        if(busy||now<nextAction)return;
        if(pendingFinish&&current!=null){pendingFinish=false;finishRound();return;}
        if(!((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){status="屏幕已关闭 · 等待解锁";return;}
        android.app.KeyguardManager kg=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);if(kg.isKeyguardLocked()){status="手机锁定 · 等待解锁";return;}
        List<Repo.Task> tasks=repo.tasks();
        if(current==null){
            for(Repo.Task t:tasks)if(t.state.equals("等待")){
                if(PacketParser.blacklisted(p.s("blacklist",""),t.room)){repo.state(t.id,"黑名单","");continue;}
                if(t.end<=now){repo.state(t.id,"已过期","");continue;}
                if(t.end-now<=p.n("before",60)*1000L){enter(t);return;}
            }
            status=room.isEmpty()?"监听 QQ · 等待进房时间":"留在直播间 · 等待下一任务";return;
        }
        // Only manipulate Bilibili. Other foreground apps pause the workflow.
        AccessibilityNodeInfo root=getRootInActiveWindow();String pkg=root==null?"":String.valueOf(root.getPackageName());
        if(!pkg.equals("tv.danmaku.bili")||!liveActivity){
            if(now-entered<15000){status="等待 B站前台";return;}
            if(enterAttempts<2){enterAttempts++;openRoom(current.room);entered=now;return;}
            repo.state(current.id,"进房失败","两次失败，今天跳过此房间");p.put("failed."+java.time.LocalDate.now()+"."+current.room,true);current=null;status="两次进房失败 · 跳过";return;
        }
        if(!visitRecorded){firstVisit=repo.visit(room);visitRecorded=true;}
        int biliWindows=0;for(AccessibilityWindowInfo win:getWindows()){AccessibilityNodeInfo node=win.getRoot();if(node!=null&&"tv.danmaku.bili".contentEquals(node.getPackageName()==null?"":node.getPackageName()))biliWindows++;}
        if(biliWindows>1){status="检测到双小窗口 · 请先完成多窗口路由验证";return;}
        busy=true;long g=generation;
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult result){
                Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                Bitmap bitmap=hardware==null?null:hardware.copy(Bitmap.Config.ARGB_8888,false);
                if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();
                if(bitmap==null){busy=false;stop("截图读取失败",true);return;}
                if(!running||g!=generation){bitmap.recycle();busy=false;return;}
                Screen.read(bitmap,s->{busy=false;if(running&&g==generation)process(s);},e->{busy=false;stop("文字识别失败："+e.getMessage(),true);});
            }
            public void onFailure(int code){busy=false;stop("截图失败 "+code+"，检查无障碍权限或安全窗口",true);}
        });
    }
    void enter(Repo.Task t){
        if(p.b("failed."+java.time.LocalDate.now()+"."+t.room,false)){repo.state(t.id,"已跳过","同日两次进房失败");return;}
        if(!room.equals(t.room)){visitRecorded=false;firstVisit=false;openRoom(t.room);room=t.room;}
        current=t;entered=System.currentTimeMillis();enterAttempts=1;scans=0;confirmed=roundConfirmed.contains(t.id);submitted=roundSubmitted.contains(t.id);visitSent=false;
        repo.state(t.id,"进房中","");status="进入 "+room;nextAction=entered+5000;
    }
    void openRoom(String id){Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("bilibili://live/"+id));i.setPackage("tv.danmaku.bili");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
    void process(Screen s){
        if(current==null)return;
        long now=System.currentTimeMillis();
        // Only visible dialog labels in the central region trigger the safety latch.
        for(Screen.Label l:s.labels)if(l.rect.centerY()>s.height*.18&&l.rect.centerY()<s.height*.85&&l.text.matches(".*(人机验证|安全验证|请完成验证|拖动滑块|操作频繁|账号异常|验证后继续).*")){
            String reason="检测到验证/限制："+l.text;capture("verification");stop(reason,true);return;
        }
        if(s.has("无法进入直播间")||s.has("你已被拉黑")||s.has("直播间不存在")){
            if(enterAttempts<2){enterAttempts++;openRoom(room);entered=now;nextAction=now+6000;return;}
            p.put("failed."+java.time.LocalDate.now()+"."+room,true);repo.state(current.id,"进房失败",s.panelText());current=null;return;
        }
        if(s.has("点个关注吧")||s.has("关注主播不迷路")){if(!clickNode("关闭")){if(p.s("followClose","").isEmpty()){stop("关注弹窗需要标定关闭按钮",false);return;}tapRelative(p.s("followClose",""));}nextAction=now+1500;return;}
        if(now>=current.end){
            if(s.panelHas("恭喜你获奖啦")||s.panelHas("恭喜中奖")){
                capture("win");repo.state(current.id,"中奖",s.panelText());p.put("losses","0");
                if(firstVisit)sendActions("win");finishRound();return;
            }
            if(s.panelHas("未参与")){repo.state(current.id,"未参与",s.panelText());finishRound();return;}
            if(s.panelHas("未中奖")||s.panelHas("很遗憾")||s.panelHas("未能中奖")){
                repo.state(current.id,confirmed?"未中奖":"未参与",s.panelText());
                if(confirmed){int losses=p.n("losses",0)+1;p.put("losses",""+losses);if(firstVisit)sendActions("lose");if(losses>=Math.max(1,p.n("lossLimit",10))){
                    long wakeAt=now+Math.max(1,p.n("restMinutes",60))*60000L;p.put("sleepUntil",""+wakeAt);repo.log("自动休眠","连续未中奖达到上限，唤醒时间 "+wakeAt);
                }}finishRound();return;
            }
            if(now>current.end+Math.max(20,p.n("after",30))*1000L){repo.state(current.id,"无法确认","没有明确中奖或未中奖证据，不计连续未中奖");capture("unknown");finishRound();return;}
            if(s.panelHas("发的红包")||s.panelHas("开奖")){status="等待开奖结果";nextAction=now+2200;return;}
            openPacket();nextAction=now+2200;return;
        }
        if(confirmed){status="已参与 · 等待开奖";return;}
        if(s.panelHas("已参与")||(submitted&&s.panelHas("开奖")&&!s.panelHas("未参与")&&s.panelExact("抢")==null)){
            confirmed=true;roundConfirmed.add(current.id);repo.state(current.id,"已参与",s.panelText());status="已参与 · 保持红包面板";
            if(firstVisit&&!visitSent){visitSent=true;sendActions("join");}return;
        }
        if(s.panelHas("发的红包")||s.panelHas("共")&&s.panelHas("开奖")){
            int amount=s.amount();
            if(amount<=20&&amount>=0){repo.log("跳过20电池","房间 "+room);swipe();nextAction=now+1800;if(++scans>8){repo.state(current.id,"已跳过","未找到超过20电池的匹配红包");finishRound();}return;}
            // Match the displayed countdown to this task before any submission.
            java.util.regex.Matcher time=java.util.regex.Pattern.compile("(?:(\\d+)\\s*分)?\\s*(\\d+)\\s*秒").matcher(s.panelText());
            boolean matches=false;if(time.find()){long secs=Integer.parseInt(time.group(2))+(time.group(1)==null?0:60*Integer.parseInt(time.group(1)));matches=Math.abs(now+secs*1000-current.end)<12000;}
            if(amount!=current.amount||!matches){status="核对红包金额/倒计时";if(++scans<=8){swipe();nextAction=now+1800;}else {repo.state(current.id,"无法确认","面板金额或时间无法与任务匹配，未提交");finishRound();}return;}
            Screen.Label grab=s.panelExact("抢","抢红包","立即参与");
            if(grab!=null&&!submitted){submitted=true;roundSubmitted.add(current.id);tap(grab.rect.centerX(),grab.rect.centerY());repo.state(current.id,"已点击待确认",s.panelText());nextAction=now+2500;status="已点击 · 核对参与状态";return;}
            status=submitted?"提交后未确认 · 不重复点击":"未发现可用的抢按钮";return;
        }
        openPacket();nextAction=now+2000;
        if(++scans>12){repo.state(current.id,"无法确认","未找到红包面板");finishRound();}
    }
    void finishRound(){
        if(busy){pendingFinish=true;return;}
        long now=System.currentTimeMillis();String doneRoom=current.room;current=null;
        if(p.until()>now){status="连续未中奖休眠";return;}
        for(Repo.Task t:repo.tasks())if(t.room.equals(doneRoom)&&t.state.equals("等待")&&t.end>now){current=t;confirmed=roundConfirmed.contains(t.id);submitted=roundSubmitted.contains(t.id);scans=0;visitSent=true;if(!busy)swipe();nextAction=now+2000;status="切换同房下一个红包";return;}
        // Keep the room until a subsequent task's entry time. No HOME/force-stop.
        nextAction=now+Math.max(0,p.n("after",30))*1000L;status="本轮结束 · 留存中";
    }
    void openPacket(){if(!clickNode("红包"))tapRelative(p.s("packetPoint","0.08,0.17"));}
    void swipe(){Path path=new Path();android.util.DisplayMetrics d=getResources().getDisplayMetrics();path.moveTo(d.widthPixels*.82f,d.heightPixels*.72f);path.lineTo(d.widthPixels*.22f,d.heightPixels*.72f);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,550)).build(),null,null);}
    void tapRelative(String value){try{String[] a=value.split(",");android.util.DisplayMetrics d=getResources().getDisplayMetrics();tap((int)(Float.parseFloat(a[0])*d.widthPixels),(int)(Float.parseFloat(a[1])*d.heightPixels));}catch(Exception e){stop("坐标设置无效",true);}}
    void tap(int x,int y){Path path=new Path();path.moveTo(x,y);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build(),null,null);}
    List<AccessibilityNodeInfo> nodes(){List<AccessibilityNodeInfo> list=new ArrayList<>();walk(getRootInActiveWindow(),list);return list;}
    void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out){if(n==null)return;out.add(n);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out);}
    boolean clickNode(String text){for(AccessibilityNodeInfo n:nodes()){String t=String.valueOf(n.getText())+" "+String.valueOf(n.getContentDescription());if(t.contains(text)&&n.isVisibleToUser()){for(AccessibilityNodeInfo a=n;a!=null;a=a.getParent())if(a.isClickable()&&a.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}}return false;}
    boolean clickId(String id){for(AccessibilityNodeInfo n:nodes())if(id.equals(n.getViewIdResourceName())&&n.isVisibleToUser())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;}
    void sendActions(String event){
        if(!p.b("messages",true))return;
        if(p.s("packetClose","").isEmpty()){repo.log("消息未发送","请先标定红包关闭按钮");return;}
        String raw=p.s(event+"Actions",event.equals("join")?"emoji:EveOneCat2":event.equals("win")?"text:谢谢老板":"");
        List<String> actions=new ArrayList<>();for(String a:raw.split("\n"))if(!a.trim().isEmpty())actions.add(a.trim());
        if(actions.isEmpty())return;
        long g=generation;busy=true;
        // Close the packet using a configured close target, never Android BACK.
        tapRelative(p.s("packetClose","0.67,0.64"));h.postDelayed(()->action(actions,0,g),1000);
    }
    void action(List<String> actions,int i,long g){
        if(!running||g!=generation){busy=false;return;}
        if(i>=actions.size()){busy=false;if(pendingFinish){pendingFinish=false;finishRound();}else openPacket();return;}
        for(AccessibilityNodeInfo node:nodes()){String label=String.valueOf(node.getText());if(label.matches(".*(人机验证|安全验证|请完成验证|拖动滑块).*")){capture("verification");stop("消息阶段检测到验证",true);return;}}
        String a=actions.get(i);
        if(a.startsWith("text:")){
            String[] pool=a.substring(5).trim().split("\\|");String text=pool[new Random().nextInt(pool.length)];
            clickId("tv.danmaku.bili:id/live_send_danmaku");
            h.postDelayed(()->{
                if(!running||g!=generation){busy=false;return;}
                AccessibilityNodeInfo edit=null;for(AccessibilityNodeInfo n:nodes())if("tv.danmaku.bili:id/danmu_input".equals(n.getViewIdResourceName()))edit=n;
                if(edit==null){repo.log("消息未发送","找不到弹幕输入框");busy=false;return;}
                Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
                if(!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){repo.log("消息未发送","输入失败");busy=false;return;}
                h.postDelayed(()->{
                    if(!running||g!=generation){busy=false;return;}
                    if(clickId("tv.danmaku.bili:id/send"))repo.log("弹幕提交",text+"（仅提交，不等于服务端送达）");else repo.log("消息未发送","发送按钮未找到");
                    h.postDelayed(()->action(actions,i+1,g),1200);
                },500);
            },650);
        }else if(a.startsWith("emoji:")){
            // Sticker sequences are explicitly recorded by the user in app settings.
            String sequence=p.s("emoji."+a.substring(6).trim(),"");String[] candidates=sequence.split(";");
            if(sequence.isEmpty()){repo.log("表情未发送",a+" 尚未标定点击路径");h.postDelayed(()->action(actions,i+1,g),200);return;}
            String chosen=candidates[new Random().nextInt(candidates.length)];String[] points=chosen.split(" ");gestureSequence(points,0,()->action(actions,i+1,g),g);
        }else {repo.log("动作忽略","未知格式："+a);h.postDelayed(()->action(actions,i+1,g),200);}
    }
    void gestureSequence(String[] points,int i,Runnable done,long g){if(!running||g!=generation){busy=false;return;}if(i>=points.length){done.run();return;}tapRelative(points[i]);h.postDelayed(()->gestureSequence(points,i+1,done,g),850);}
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
    public void capture(String type){
        if(System.currentTimeMillis()-lastEvidence<1500)return;lastEvidence=System.currentTimeMillis();
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){public void onSuccess(ScreenshotResult s){
            try {Bitmap b=Bitmap.wrapHardwareBuffer(s.getHardwareBuffer(),s.getColorSpace());ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);String path=FilesOut.write(PocketService.this,"screenshots",type+"-"+System.currentTimeMillis()+".png",out.toByteArray(),"image/png");repo.log("截图",path);b.recycle();}catch(Exception e){repo.log("截图失败",e.toString());}finally{s.getHardwareBuffer().close();}
        }public void onFailure(int code){repo.log("截图失败","错误码 "+code);}});
    }
}
