package cn.eveonecat.pocket;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    final int BG=0xff17151e,CARD=0xff26222f,INK=0xfff3edf9,MUTED=0xffc1b6ce,ACCENT=0xffbd91ff;
    Prefs p; Repo repo; LinearLayout content,base; TextView live,queue,logView,prizes;String page="任务",folder="";
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable refresh=new Runnable(){public void run(){if(live!=null)live.setText(state());if(queue!=null)queue.setText(queueText());if(logView!=null)logView.setText(repo.events());if(prizes!=null)prizes.setText(repo.prizeSummary());handler.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);p=new Prefs(this);repo=new Repo(this);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);show("任务");handler.post(refresh);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);repo.close();super.onDestroy();}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    LinearLayout card(String title){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(16));box.setBackground(bg(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));content.addView(box,lp);box.addView(text(title,19,ACCENT));return box;}
    void note(LinearLayout l,String s){l.addView(text(s,14,MUTED));}
    void button(LinearLayout l,String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(15);b.setBackground(bg(0xff47335e,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);l.addView(b,lp);b.setOnClickListener(v->action.run());}
    void show(String name){
        page=name;live=null;queue=null;logView=null;prizes=null;base=new LinearLayout(this);base.setOrientation(1);base.setBackgroundColor(BG);
        base.setPadding(dp(16),dp(36),dp(16),dp(28));setContentView(base);
        TextView title=text("口袋 · 红包助手",26,INK);base.addView(title);base.addView(text("独立手机运行  /  QQ 通知 → B站 App",12,MUTED));
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);for(String tab:new String[]{"任务","消息","记录","设置"}){TextView t=text(tab,16,tab.equals(name)?ACCENT:MUTED);t.setGravity(Gravity.CENTER);nav.addView(t,new LinearLayout.LayoutParams(0,dp(50),1));t.setOnClickListener(v->show(tab));}base.addView(nav);
        switch(name){case "任务":home();break;case "消息":messages();break;case "记录":records();break;default:settings();}
    }
    String state(){PocketService s=PocketService.instance;return PermissionStatus.summary(this)+"\n"+(s==null?"无障碍尚未连接":s.status+"\n"+(s.running?"运行中":"已停止")+" · 连续未中奖 "+p.n("losses",0)+" / "+p.n("lossLimit",10));}
    void home(){
        LinearLayout top=card("当前状态");live=text(state(),18,INK);top.addView(live);
        button(top,"无障碍授权 / 修复直达",()->PermissionStatus.accessibility(this));
        button(top,"QQ 通知授权直达",()->PermissionStatus.notifications(this));
        if(!p.s("verificationLock","").isEmpty())button(top,"查看 / 解除本地停止锁",()->new AlertDialog.Builder(this).setTitle("本地停止原因").setMessage(p.s("verificationLock","")+"\n确认已经人工处理后才恢复运行。").setPositiveButton("确认已处理",(d,w)->{p.put("verificationLock","");show("任务");}).setNegativeButton("取消",null).show());
        button(top,"检查 QQ 消息接收",()->{p.put("listen",true);if(QQListener.instance!=null)QQListener.instance.refresh();else toast("通知服务未连接，请重新授权");show("消息");});
        button(top,"只读检查红包（3秒后截图识别）",()->{PocketService service=PocketService.instance;if(service==null){toast("请先连接无障碍");return;}moveTaskToBack(true);handler.postDelayed(service::inspectPacket,3000);});
        button(top,"开始运行",()->{PocketService s=PocketService.instance;if(s==null){toast(PermissionStatus.accessibilityEnabled(this)?"权限已开启但服务未连接；请检查系统详情或后台设置":"请开启无障碍权限");PermissionStatus.accessibility(this);return;}s.start();live.setText(state());});
        button(top,"停止运行",()->{if(PocketService.instance!=null)PocketService.instance.stop("用户手动停止",false);live.setText(state());});
        note(top,"保持屏幕亮起且解锁。悬浮彩字显示状态，点击 × 立即停止。验证码锁定不会定时重启。");
        LinearLayout tasks=card("任务队列 · 越早开奖越靠前");button(tasks,"刷新任务",()->show("任务"));
        List<DualPlan.Draw> planned=new ArrayList<>();
        for(Repo.Task t:repo.tasks())if(t.state.equals("等待"))planned.add(new DualPlan.Draw(t.id,t.room,t.end));
        if(p.b("dualExperimental",false)&&!planned.isEmpty()){
            note(tasks,"双账号分配预览（尚不代表已进入房间；自动路由待验证）");
            for(DualPlan.Reservation r:DualPlan.calculate(planned,System.currentTimeMillis(),Math.max(0,p.n("before",60))*1000L,Math.max(0,p.n("after",30))*1000L,p.b("dualPlan",true)))
                note(tasks,r.room()+" → "+(r.account()<0?"无空闲账号":r.account()==0?"左侧主账号":"右侧备用账号")+" · "+r.state()+" · 红包数 "+r.tasks().size());
        }
        queue=text(queueText(),15,INK);tasks.addView(queue);
    }
    String queueText(){StringBuilder out=new StringBuilder();for(Repo.Task t:repo.tasks())out.append(t.room).append(" · ").append(t.amount).append("电池\n").append(new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(t.end))).append(" · ").append(t.state).append("\n\n");return out.length()==0?"等待目标 QQ 群的新红包通知。":out.toString();}
    void messages(){LinearLayout c=card("QQ 消息自动接收");button(c,"仅监听 QQ 消息（不操作 B站）",()->{p.put("listen",true);toast("已开始监听匹配目标群的QQ通知");});button(c,"停止 QQ 消息监听",()->{p.put("listen",false);toast("已停止QQ通知接收");});note(c,"目标群标题关键词："+p.s("group","B站直播区游戏群"));note(c,"只读取 QQ 通知，不连接电脑。群免打扰、通知内容隐藏、QQ 合并或截断通知都会影响任务获取。通知缺少链接或时间时不会执行。");button(c,"打开通知读取授权",()->PermissionStatus.notifications(this));button(c,"刷新消息与解析结果",()->show("消息"));logView=text(repo.events(),13,INK);c.addView(logView);}
    void records(){LinearLayout c=card("参与记录与获奖证据");prizes=text(repo.prizeSummary(),18,ACCENT);c.addView(prizes);note(c,"实际获奖数量单独保存；红包总额不是个人所得。截图补记会按中奖时间重新计算其后的连续未中，不发送历史感谢消息。");button(c,"从截图补记中奖",()->{if(PocketService.instance!=null&&PocketService.instance.running){toast("请先停止运行，再修正历史记录");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);i.putExtra("android.provider.extra.INITIAL_URI",Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"));startActivityForResult(i,44);});button(c,"导出 CSV",()->{try{String path=FilesOut.write(this,"exports","红包记录-"+System.currentTimeMillis()+".csv",repo.csv().getBytes(StandardCharsets.UTF_8),"text/csv");toast("已导出："+path);repo.log("导出",path);}catch(Exception e){toast("导出失败："+e.getMessage());}});button(c,"选择导出目录",()->pick("exports"));button(c,"选择截图目录",()->pick("screenshots"));note(c,"导出目录："+p.s("exports","应用专属目录/exports")+"\n截图目录："+p.s("screenshots","应用专属目录/screenshots"));button(c,"截图留证",()->{if(PocketService.instance!=null)PocketService.instance.capture("manual");else toast("请先连接无障碍服务");});logView=text(repo.events(),13,INK);c.addView(logView);}
    void inspectWinImage(Uri uri){
        try{
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,options);}
            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1600||((long)options.outWidth*options.outHeight)/(options.inSampleSize*options.inSampleSize)>8000000)options.inSampleSize*=2;options.inJustDecodeBounds=false;
            android.graphics.Bitmap image;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){image=android.graphics.BitmapFactory.decodeStream(in,null,options);}if(image==null)throw new IllegalArgumentException("图片无法读取");
            toast("正在识别中奖截图");
            Screen.read(image,screen->{
                if(!screen.won()){toast("没有识别到中奖标题和领取按钮，未改动记录");repo.log("截图检查","未识别到完整中奖面板");return;}
                Integer battery=screen.prizeBattery();if(battery==null){toast("已识别中奖，但无法确认电池数量，未改动记录");return;}
                String detected="";java.util.regex.Matcher room=java.util.regex.Pattern.compile("([1-9][0-9]{4,14})[·•]?连续未中").matcher(screen.text.replaceAll("\\s",""));if(room.find())detected=room.group(1);
                String saved="";try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1){bytes.write(data,0,n);if(bytes.size()>20000000)throw new java.io.IOException("图片过大");}saved=FilesOut.write(this,"screenshots","recovered-win-"+System.currentTimeMillis()+".jpg",bytes.toByteArray(),"image/jpeg");}catch(Exception e){toast("截图保存失败，未改动记录");return;}
                repo.log("中奖截图识别","房间 "+detected+" · 实际 "+battery+"电池 · "+screen.panelText());
                repairWin(detected,battery,saved,screen.panelText());
            },e->toast("截图识别失败："+e.getMessage()));
        }catch(Exception e){toast("读取失败："+e.getMessage());}
    }
    void repairWin(String detected,int battery,String proof,String raw){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(dp(22),dp(8),dp(22),dp(8));fields.addView(text("已识别中奖 "+battery+" 电池，请核对房间号",16,Color.DKGRAY));EditText room=new EditText(this);room.setSingleLine();room.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);room.setText(detected);fields.addView(room);
        new AlertDialog.Builder(this).setTitle("补记中奖截图").setView(fields).setNegativeButton("取消",null).setPositiveButton("查找红包任务",(dialog,which)->{
            List<Repo.Task> matches=new ArrayList<>();long now=System.currentTimeMillis();for(Repo.Task t:repo.tasks())if(t.room.equals(room.getText().toString().trim())&&t.end<=now&&!t.state.matches("黑名单|已过期|已跳过|进房失败|等待"))matches.add(t);
            matches.sort((a,b)->Long.compare(b.end,a.end));if(matches.isEmpty()){toast("没有找到该房间已执行的任务，未改动记录");return;}
            String[] labels=new String[matches.size()];for(int i=0;i<labels.length;i++){Repo.Task t=matches.get(i);labels[i]=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(t.end))+" · 原状态 "+t.state;}
            new AlertDialog.Builder(this).setTitle("选择截图对应的开奖时间").setItems(labels,(list,index)->{Repo.Task task=matches.get(index);new AlertDialog.Builder(this).setTitle("确认补记 "+battery+" 电池").setMessage(task.room+"\n"+labels[index]+"\n仅修正此条记录并重算连续未中。").setNegativeButton("取消",null).setPositiveButton("保存修正",(yes,ok)->{repo.win(task.id,battery,"电池",raw+"\n截图："+proof,true);int streak=repo.lossStreak();p.put("losses",""+streak);if(streak<p.n("lossLimit",10))p.put("sleepUntil","0");repo.log("连续未中重算","按最新中奖时间计算："+streak);show("记录");toast("已补记，连续未中为 "+streak);}).show();}).setNegativeButton("取消",null).show();
        }).show();
    }
    void settings(){
        LinearLayout a=card("权限与消息源");button(a,"① 开启无障碍服务",()->PermissionStatus.accessibility(this));button(a,"② 开启 QQ 通知读取",()->PermissionStatus.notifications(this));button(a,"③ 电池与后台运行设置",()->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        note(a,PermissionStatus.summary(this));button(a,"应用详情 / 后台运行设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        note(a,"小米手机还需允许自启动、后台无限制；安装来源限制可能要求在应用详情里允许受限设置。无需 Root。");
        field(a,"目标群通知标题关键词（群名称，不是群号）","group","B站直播区游戏群",false);
        LinearLayout b=card("黑名单");field(b,"房间号（逗号或换行分隔）","blacklist","",true);note(b,"黑名单在接收消息和进房前都会检查。");
        LinearLayout timing=card("任务与自动休眠");field(timing,"提前进房 / 秒","before","60",false);field(timing,"开奖后最少留存 / 秒","after","30",false);field(timing,"连续多少个明确未中奖后休眠","lossLimit","10",false);field(timing,"多久后自动恢复 / 分钟","restMinutes","60",false);
        Switch club=new Switch(this);club.setText("每个房间首次允许1电池入团");club.setTextColor(INK);club.setChecked(p.b("allowOneBattery",false));club.setOnCheckedChangeListener((v,on)->p.put("allowOneBattery",on));timing.addView(club);note(timing,"默认关闭。仅限明确标注需入团（1电池）的红包；每个房间只尝试一次。其他付费弹窗或超过1电池时停止。");
        note(timing,"已参与后停留在红包面板等开奖。同房多个红包向左滑动；20电池不参加。同一天第二次进该房间时，所有互动消息均不发送。进房失败两次当天跳过。");
        LinearLayout msg=card("互动消息 · 可选");Switch enabled=new Switch(this);enabled.setText("启用已配置的互动消息");enabled.setTextColor(INK);enabled.setChecked(p.b("messages",true));enabled.setOnCheckedChangeListener((v,on)->p.put("messages",on));msg.addView(enabled);
        note(msg,"每行执行一个动作，行的顺序即发送顺序。text: 来啦！|好耶~ 表示随机选一句；emoji:EveOneCat2 使用已录制表情路径。未找到发送按钮不会声称发送成功。");
        field(msg,"确认参与后","joinActions","text:来了~",true);field(msg,"明确中奖后","winActions","text:谢谢老板",true);field(msg,"明确未中奖后","loseActions","",true);
        button(msg,"录制 EveOneCat2 表情点击路径",()->{PocketService s=PocketService.instance;if(s==null){toast("先开启无障碍");return;}s.recordEmoji();});
        field(msg,"EveOneCat2 表情路径（高级）","emoji.EveOneCat2","",true);note(msg,"录制不同表情可追加多条路径，发送时随机选择。路径包含打开输入框/表情列表、选择表情、发送的完整步骤。必须在当前手机和B站界面完成标定。");
        LinearLayout coords=card("界面标定 · 高级");note(coords,"坐标格式为屏幕宽高比例 x,y（0~1）。不同手机布局不同，默认坐标需实机确认。标定前先打开需要操作的B站界面。");field(coords,"红包入口","packetPoint","0.08,0.17",false);field(coords,"红包面板关闭","packetClose","",false);field(coords,"关注弹窗关闭","followClose","",false);
        for(String[] point:new String[][]{{"packetPoint","红包入口"},{"packetClose","红包关闭"},{"followClose","关注弹窗关闭"}})button(coords,"点选标定："+point[1],()->{if(PocketService.instance!=null)PocketService.instance.recordPoint(point[0]);else toast("先开启无障碍");});
        LinearLayout dual=card("应用双开 · 实验功能");note(dual,"已确认本机支持两个B站小窗口同时显示，两个直播间可能同时驻留。手势仍依次执行；需要检查无障碍能否分别读取两个窗口，并按各自边界识别、点击和核对账号。当前预览版仍按单账号调度，多窗口路由待验证。");
        button(dual,"检测 B站小窗口",()->{if(PocketService.instance==null){toast("请先开启无障碍");return;}toast("5秒后检测，请立即切回两个B站小窗口；结果在消息页查看");handler.postDelayed(()->{if(PocketService.instance!=null){PocketService.instance.windowReport();toast("检测已记录，请在消息页查看");}},5000);});
        note(dual,"按轮流放大方式适配。以下测试只点击迷你窗中央，不抢红包、不发送消息；请先停止运行。测试结果记录在消息页。");
        for(boolean left:new boolean[]{true,false})button(dual,"双开校准：放大"+(left?"左":"右")+"侧小窗",()->{PocketService service=PocketService.instance;if(service==null){toast("请先连接无障碍");return;}if(service.running){toast("请先停止运行");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewFloatingWindow(left),2000);});
        note(dual,"循环测试：左窗长按右下角向左上拖；右窗长按左下角向右上拖，并检查窗口尺寸；失败只记录日志，不会显示为成功。测试前先将两个窗口恢复为迷你窗。");
        for(boolean left:new boolean[]{true,false})button(dual,"循环测试："+(left?"左":"右")+"窗放大后自动缩小",()->{PocketService service=PocketService.instance;if(service==null){toast("请先开启无障碍");return;}if(service.running){toast("请先停止任务");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewFloatingCycle(left),2000);});
        note(dual,"本机约定：左侧主账号，右侧备用账号。进房路由需单独验证；主账号与备用账号分别定位窗口，两个账号均在各自B站窗口内按房间号搜索进房。两项均不会点击红包或发送消息。");
        field(dual,"进房验证房间号（两个账号统一使用B站内搜索）","routeRoom","",false);
        for(boolean backup:new boolean[]{false,true})button(dual,backup?"按房间号验证备用账号进房":"验证主账号进房",()->{PocketService service=PocketService.instance;if(service==null){toast("请先开启无障碍");return;}if(service.running){toast("请先停止任务");return;}String target=p.s("routeRoom","");if(!target.matches("[1-9][0-9]{0,14}")){toast("先填写验证房间号");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewRoomRoute(target,backup),2000);});
        LinearLayout safe=card("异常停止");note(safe,"当前验证锁定："+p.s("verificationLock","无"));button(safe,"已人工处理验证，解除本地锁定",()->new AlertDialog.Builder(this).setTitle("解除本地锁定").setMessage("请先在B站中手动完成验证并确认账号状态正常。解除只允许再次启动，不代表平台限制已解除。").setPositiveButton("确认已处理",(d,w)->{p.put("verificationLock","");show("设置");}).setNegativeButton("取消",null).show());
        LinearLayout about=card("关于与更新");note(about,"0.1.10 · 中奖记录与多红包修复\n所有任务、通知原文和截图只保存在手机；不上传账号凭证。\n中奖面板独立识别奖品数字，可从截图补记并重算连续未中。单账号流程；双开调度暂未启用。");button(about,"GitHub 下载与更新",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/struggle0929/bili-pocket-android/releases"))));
    }
    void field(LinearLayout box,String title,String key,String def,boolean multiline){
        box.addView(text(title,14,MUTED));EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setText(p.s(key,def));e.setSingleLine(!multiline);e.setTextSize(15);box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        e.setOnFocusChangeListener((v,focus)->{if(!focus)p.put(key,e.getText().toString().trim());});
        e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){p.put(key,s.toString().trim());}public void afterTextChanged(android.text.Editable x){}});
    }
    void pick(String key){folder=key;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,42);}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null)return;if(r==44){inspectWinImage(data.getData());return;}if(r==42){Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));p.put(folder,u.toString());show(page);}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
