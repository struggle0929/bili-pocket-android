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
    Prefs p; Repo repo; LinearLayout content,base,taskRows,messageRows,recordRows; TextView live,logView,messageState,setupLink,statNote;Button receiveButton,pauseButton,startButton,stopButton;String page="任务",folder="",taskSignature="",messageSignature="";boolean taskHistory;TextView[] recordMetrics;int recordLimit=40;String recordSignature="";AutoCompleteTextView urlInput;UrlHistoryAdapter historyAdapter;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable refresh=new Runnable(){public void run(){if(live!=null)live.setText(state());if(setupLink!=null)setupLink.setVisibility(needsSetup()?View.VISIBLE:View.GONE);if(startButton!=null){boolean running=PocketService.instance!=null&&PocketService.instance.running;startButton.setEnabled(!running);stopButton.setEnabled(running);}if(taskRows!=null)renderTasks();if(messageRows!=null)renderMessages();if(messageState!=null)messageState.setText(feedState());if(receiveButton!=null)receiveButton.setText(p.b("listen",false)?"立即刷新":"开始接收");if(pauseButton!=null)pauseButton.setEnabled(p.b("listen",false));if(logView!=null)logView.setText(repo.events());if(recordRows!=null)renderRecords();handler.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);p=new Prefs(this);repo=new Repo(this);repo.refilter(p);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);show("任务");handler.post(refresh);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);repo.close();super.onDestroy();}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    LinearLayout card(String title){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(16));box.setBackground(bg(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));content.addView(box,lp);box.addView(text(title,19,ACCENT));return box;}
    LinearLayout settingSection(String title){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(6),dp(18),dp(6));box.setBackground(bg(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));content.addView(box,lp);
        String key="settings.expanded."+title;TextView header=text("",17,ACCENT);header.setGravity(Gravity.CENTER_VERTICAL);header.setMinHeight(dp(52));header.setFocusable(true);box.addView(header);
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,0,0,dp(12));box.addView(body);
        Runnable paint=()->{boolean open=p.b(key,false);header.setText((open?"▾  ":"▸  ")+title);header.setContentDescription(title+(open?"，已展开，点击收起":"，已收起，点击展开"));body.setVisibility(open?View.VISIBLE:View.GONE);};
        header.setOnClickListener(v->{boolean open=p.b(key,false);if(open){View focused=getCurrentFocus();if(focused!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}p.put(key,!open);paint.run();});paint.run();return body;
    }
    void note(LinearLayout l,String s){l.addView(text(s,14,MUTED));}
    void button(LinearLayout l,String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(15);b.setBackground(bg(0xff47335e,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);l.addView(b,lp);b.setOnClickListener(v->action.run());}
    void show(String name){
        page=name;live=null;logView=null;statNote=null;recordRows=null;recordMetrics=null;recordSignature="";urlInput=null;historyAdapter=null;messageState=null;setupLink=null;receiveButton=null;pauseButton=null;startButton=null;stopButton=null;taskRows=null;messageRows=null;taskSignature="";messageSignature="";base=new LinearLayout(this);base.setOrientation(1);base.setBackgroundColor(BG);
        base.setPadding(dp(16),dp(16),dp(16),dp(8));base.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(16)+bars.left,dp(16)+bars.top,dp(16)+bars.right,dp(8)+bars.bottom);return insets;});setContentView(base);base.requestApplyInsets();
        TextView title=text("口袋 · 红包助手",22,INK);base.addView(title);base.addView(text("红包任务  /  QQ · 网页",12,MUTED));
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);for(String tab:new String[]{"任务","消息","记录","设置"}){TextView t=text(tab,16,tab.equals(name)?ACCENT:MUTED);t.setGravity(Gravity.CENTER);nav.addView(t,new LinearLayout.LayoutParams(0,dp(50),1));t.setOnClickListener(v->show(tab));}base.addView(nav);
        switch(name){case "任务":home();break;case "消息":messages();break;case "记录":records();break;default:settings();}
    }
    String state(){PocketService service=PocketService.instance;return (service==null?"未运行 · 权限与连接请到设置检查":(service.running?"运行中 · ":"已停止 · ")+service.status)+"\n连续未中奖 "+p.n("losses",0)+" / "+p.n("lossLimit",10);}
    String sourceKey(){try{return p.sourceKey();}catch(IllegalArgumentException e){return "web:invalid";}}
    boolean needsSetup(){return PocketService.instance==null||!p.webMode()&&!PermissionStatus.notificationEnabled(this)||!p.s("verificationLock","").isEmpty();}
    void home(){
        LinearLayout top=card("当前任务");note(top,(p.webMode()?"消息来源 · 网页监控":"消息来源 · QQ 群通知")+"\n目标 · "+p.target().description());live=text(state(),16,INK);top.addView(live);
        Button[] actions=controls(top,"开始运行",this::startRunner,"停止运行",()->{if(PocketService.instance!=null)PocketService.instance.stop("用户手动停止",false);if(live!=null)live.setText(state());});startButton=actions[0];stopButton=actions[1];stopButton.setEnabled(PocketService.instance!=null&&PocketService.instance.running);
        link(top,"清零连续未中奖",this::clearLosses);
        setupLink=link(top,"前往设置检查权限 / 停止原因",()->show("设置"));setupLink.setVisibility(needsSetup()?View.VISIBLE:View.GONE);
        LinearLayout tasks=card("任务队列 · 开奖时间升序");taskRows=new LinearLayout(this);taskRows.setOrientation(1);tasks.addView(taskRows);renderTasks();
        link(tasks,taskHistory?"收起已结束任务":"查看已结束任务",()->{taskHistory=!taskHistory;show("任务");});
    }
    void startRunner(){PocketService service=PocketService.instance;if(service==null){toast("请在设置开启无障碍并确认服务连接");show("设置");return;}service.start();if(live!=null)live.setText(state());}
    void renderTasks(){
        long now=System.currentTimeMillis();List<Repo.Task> tasks=new ArrayList<>();StringBuilder signature=new StringBuilder();
        List<Repo.Task> ordered=repo.tasksForSource(sourceKey());ordered.sort((a,b)->{boolean aa=a.state.matches("进房中|已参与|已点击待确认")||a.state.equals("等待")&&a.end>now,bb=b.state.matches("进房中|已参与|已点击待确认")||b.state.equals("等待")&&b.end>now;return aa!=bb?aa?-1:1:aa?Long.compare(a.end,b.end):Long.compare(b.end,a.end);});
        for(Repo.Task t:ordered){boolean active=t.state.matches("进房中|已参与|已点击待确认")||t.state.equals("等待")&&t.end>now;if(active||taskHistory){tasks.add(t);signature.append(t.id).append(t.end).append(t.state).append(active);if(tasks.size()==30)break;}}
        if(taskSignature.equals(signature.toString())&&taskRows.getChildCount()>0)return;taskSignature=signature.toString();taskRows.removeAllViews();
        if(tasks.isEmpty()){note(taskRows,"暂无待执行任务，去消息页选择来源并接收红包。");return;}
        for(Repo.Task t:tasks){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackground(bg(BG,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);taskRows.addView(row,lp);TextView roomTitle=text("房间 "+t.room+" · "+t.amount+" 电池  ›",15,ACCENT);roomTitle.setContentDescription("房间 "+t.room+"，点击复制或打开");roomTitle.setOnClickListener(v->roomMenu(t.room));row.addView(roomTitle);note(row,new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(t.end))+" 开奖 · "+(t.state.equals("等待")&&t.end<=now?"已过期":t.state));}
    }
    Button compactButton(String title,Runnable action,int color){Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(INK);button.setBackground(bg(color,12));button.setOnClickListener(v->action.run());return button;}
    Button[] controls(LinearLayout parent,String first,Runnable a,String second,Runnable b){LinearLayout row=new LinearLayout(this);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=dp(8);parent.addView(row,rowLp);Button left=compactButton(first,a,0xff624388),right=compactButton(second,b,0xff37313f);LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,dp(44),1);x.rightMargin=dp(8);row.addView(left,x);row.addView(right,new LinearLayout.LayoutParams(0,dp(44),1));return new Button[]{left,right};}
    TextView link(LinearLayout parent,String caption,Runnable action){TextView view=text(caption,13,ACCENT);view.setPadding(0,dp(12),0,dp(8));view.setOnClickListener(v->action.run());parent.addView(view);return view;}
    void clearLosses(){
        p.clearLosses();repo.log("手动清零","连续未中奖已清零，连续未中休眠已取消；历史记录及验证锁定保留");
        if(live!=null)live.setText(state());toast("连续未中奖已清零，休眠已取消");
    }
    void messages(){
        LinearLayout source=card("消息来源");RadioGroup modes=new RadioGroup(this);modes.setOrientation(LinearLayout.HORIZONTAL);source.addView(modes);RadioButton qq=new RadioButton(this),web=new RadioButton(this);qq.setId(View.generateViewId());web.setId(View.generateViewId());qq.setText("QQ 群通知");web.setText("网页监控");qq.setTextColor(INK);web.setTextColor(INK);modes.addView(qq,new RadioGroup.LayoutParams(0,-2,1));modes.addView(web,new RadioGroup.LayoutParams(0,-2,1));modes.check(p.webMode()?web.getId():qq.getId());
        final boolean[] changing={false};modes.setOnCheckedChangeListener((group,id)->{if(changing[0])return;PocketService service=PocketService.instance;if(service!=null&&service.running){toast("请先停止任务再切换消息来源");changing[0]=true;group.check(p.webMode()?web.getId():qq.getId());changing[0]=false;return;}p.put("listen",false);p.put("sourceMode",id==web.getId()?"web":"qq");show("消息");});
        if(p.webMode()){
            webAddress(source);
        }else note(source,"目标群 · "+p.s("group","B站直播区游戏群"));
        messageState=text(feedState(),13,ACCENT);source.addView(messageState);Button[] buttons=controls(source,p.b("listen",false)?"立即刷新":"开始接收",this::receive,"暂停接收",()->{p.put("listen",false);if(PocketService.instance!=null&&PocketService.instance.web!=null)PocketService.instance.web.sync();messageState.setText(feedState());});receiveButton=buttons[0];pauseButton=buttons[1];pauseButton.setEnabled(p.b("listen",false));
        note(source,"目标 · "+p.target().description()+"\n自动进房请在任务页点击“开始运行”。");
        messageRows=new LinearLayout(this);messageRows.setOrientation(1);content.addView(messageRows);renderMessages();
    }
    String feedState(){if(!p.b("listen",false))return "接收已暂停";if(p.webMode())return PocketService.instance==null?"网页后台服务未连接，请到设置检查无障碍":p.s("webStatus","等待网页更新");return QQListener.connected?"正在接收 QQ 群消息":"QQ通知服务未连接，请到设置检查";}
    void receive(){
        if(p.webMode()){
            if(urlInput!=null&&!applyWebUrl(urlInput.getText().toString(),true))return;
            try{p.sourceKey();}catch(IllegalArgumentException e){toast(e.getMessage());return;}
            PocketService service=PocketService.instance;if(service==null){toast("请在设置开启无障碍，连接后台监控服务");show("设置");return;}p.put("listen",true);service.web.refresh();
        }else{if(!PermissionStatus.notificationEnabled(this)){toast("请在设置授权QQ通知读取");show("设置");return;}p.put("listen",true);if(QQListener.instance!=null)QQListener.instance.refresh();}
        if(messageState!=null)messageState.setText(feedState());
    }
    void renderMessages(){
        List<Repo.NoticeRow> rows=repo.notices(sourceKey());StringBuilder signature=new StringBuilder();long now=System.currentTimeMillis();for(Repo.NoticeRow row:rows)signature.append(row.id()).append(':').append(row.received()).append(row.state()).append(row.reason()).append(row.end()>now);
        if(messageSignature.equals(signature.toString())&&messageRows.getChildCount()>0)return;messageSignature=signature.toString();messageRows.removeAllViews();
        if(rows.isEmpty()){LinearLayout empty=messageCard("等待红包消息");note(empty,"点击开始接收后，新红包会逐条显示在这里。未满足条件的红包也会显示原因。");return;}
        for(Repo.NoticeRow row:rows){
            String status=row.state().matches("中奖|未中奖|未参与")?row.state():!row.reason().isEmpty()?row.reason():row.end()<=now&&row.state().equals("等待")?"已开奖":row.state().isEmpty()?"可参与":row.state();
            String heading=row.kind().equals("gift_anchor")||row.kind().contains("天选")?"天选福袋 · 不参与":row.amount()>0?row.amount()+" 电池"+(row.shares()>0?" · "+row.shares()+" 份":""):"红包消息";
            LinearLayout card=messageCard(heading);
            fieldLine(card,"消息来源",row.sourceName());fieldLine(card,"条件",row.condition());LinearLayout roomLine=fieldLine(card,"直播间信息",(row.room().isEmpty()?"房间号待确认":"房间 "+row.room()+"  ›")+(row.anchor().isEmpty()?"":" · "+row.anchor()));if(row.room().matches("[1-9][0-9]{0,14}")){TextView roomText=(TextView)roomLine.getChildAt(1);roomText.setOnClickListener(v->roomMenu(row.room()));roomText.setContentDescription("房间 "+row.room()+"，点击复制或打开");roomText.setTextColor(ACCENT);}
            fieldLine(card,"红包信息",row.reward()+(row.shares()>0?" / "+row.shares()+"份":"")+"\n"+(row.end()>0?new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(row.end()))+" 开奖":"开奖时间待确认"));
            if(row.reward().startsWith("电池红包"))fieldLine(card,"红包期望",TargetRule.average(row.amount(),row.shares()));
            card.addView(text(status,13,row.reason().isEmpty()?0xff98d5bc:MUTED));link(card,"查看原文",()->showText("红包原文",row.raw()));
        }
    }
    LinearLayout messageCard(String title){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(bg(CARD,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);messageRows.addView(box,lp);box.addView(text(title,17,ACCENT));return box;}
    LinearLayout fieldLine(LinearLayout parent,String label,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);row.addView(text(label,12,MUTED),new LinearLayout.LayoutParams(dp(80),-2));row.addView(text(value,14,INK),new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);return row;}
    void roomMenu(String room){
        if(!room.matches("[1-9][0-9]{0,14}")){toast("没有有效房间号");return;}
        boolean running=PocketService.instance!=null&&PocketService.instance.running;
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(12),dp(20),dp(12));box.setBackground(bg(CARD,20));box.addView(text("房间 "+room,20,ACCENT));note(box,"选择手动操作");
        menuDivider(box);menuOption(box,"复制房间号",()->{dialog.dismiss();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("B站房间号",room));toast("已复制房间号");});
        menuDivider(box);menuOption(box,"打开直播间"+(running?"\n将先停止自动任务":""),()->{dialog.dismiss();manualOpenRoom(room);});
        menuDivider(box);menuOption(box,"取消",dialog::dismiss);
        dialog.setContentView(box);Window window=dialog.getWindow();if(window!=null)window.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();if(window!=null)window.setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(40)),WindowManager.LayoutParams.WRAP_CONTENT);
    }
    void menuDivider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(0xff493f55);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    void menuOption(LinearLayout parent,String title,Runnable action){TextView option=text(title,16,INK);option.setPadding(dp(10),dp(14),dp(10),dp(14));option.setMinHeight(dp(54));option.setGravity(Gravity.CENTER_VERTICAL);option.setFocusable(true);option.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337e58a7),bg(CARD,10),null));option.setOnClickListener(v->action.run());parent.addView(option,new LinearLayout.LayoutParams(-1,-2));}
    void manualOpenRoom(String room){Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("bilibili://live/"+room)).setPackage("tv.danmaku.bili");android.content.pm.ResolveInfo target=getPackageManager().resolveActivity(intent,0);if(target==null||target.activityInfo==null){toast("未找到B站直播间入口，请检查是否已安装主应用");return;}intent.setComponent(new ComponentName(target.activityInfo.packageName,target.activityInfo.name));if(PocketService.instance!=null&&PocketService.instance.running)PocketService.instance.stop("手动打开房间 "+room,false);try{startActivity(intent);repo.log("手动打开直播间",room+"；不标记已参与，不自动点击红包");}catch(RuntimeException e){toast("无法打开直播间："+e.getClass().getSimpleName());}}
    void showText(String title,String value){ScrollView scroll=new ScrollView(this);TextView text=text(value,13,Color.DKGRAY);text.setPadding(dp(18),dp(12),dp(18),dp(12));text.setTextIsSelectable(true);scroll.addView(text);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();}
    void webAddress(LinearLayout parent){
        urlInput=new AutoCompleteTextView(this);urlInput.setTextColor(INK);urlInput.setHintTextColor(MUTED);urlInput.setTextSize(14);urlInput.setSingleLine();urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);urlInput.setImeActionLabel("使用",android.view.inputmethod.EditorInfo.IME_ACTION_GO);urlInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);urlInput.setText(p.s("webUrl",PoolEndpoint.DEFAULT));urlInput.setThreshold(0);urlInput.setDropDownBackgroundDrawable(bg(CARD,12));parent.addView(urlInput);
        historyAdapter=new UrlHistoryAdapter();urlInput.setAdapter(historyAdapter);
        urlInput.setOnClickListener(v->{historyAdapter.reload();urlInput.showDropDown();});urlInput.setOnFocusChangeListener((v,focus)->{if(focus)urlInput.post(()->{if(urlInput!=null){historyAdapter.reload();urlInput.showDropDown();}});});
        urlInput.setOnItemClickListener((list,view,position,id)->useHistory(historyAdapter.getItem(position)));
        urlInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO||action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){if(applyWebUrl(urlInput.getText().toString(),true)){urlInput.dismissDropDown();hideKeyboard();toast("已使用网址并保存到本地历史");}return true;}return false;});
        note(parent,"新网址按键盘确认，或点击开始接收。");
    }
    boolean applyWebUrl(String text,boolean remember){
        String value=text.trim(),endpoint;try{endpoint=PoolEndpoint.source(value);}catch(IllegalArgumentException e){toast(e.getMessage());return false;}
        String old;try{old=PoolEndpoint.source(p.s("webUrl",PoolEndpoint.DEFAULT));}catch(IllegalArgumentException e){old="";}
        PocketService service=PocketService.instance;boolean changed=!endpoint.equals(old),listening=p.b("listen",false);
        if(changed&&service!=null&&service.running){toast("请先停止任务再切换网页来源");return false;}
        if(changed){p.put("listen",false);if(service!=null&&service.web!=null)service.web.sync();p.put("webLastSuccess","0");p.put("webStatus","等待新网址更新");}
        p.put("webUrl",value);if(remember)p.rememberWebUrl(value);if(historyAdapter!=null)historyAdapter.reload();
        if(changed&&listening&&service!=null&&service.web!=null){p.put("listen",true);service.web.refresh();}return true;
    }
    void useHistory(String value){if(!applyWebUrl(value,true))return;urlInput.setText(value,false);urlInput.dismissDropDown();hideKeyboard();if(messageState!=null)messageState.setText(feedState());}
    void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(base.getWindowToken(),0);if(urlInput!=null)urlInput.clearFocus();}
    final class UrlHistoryAdapter extends BaseAdapter implements Filterable {
        List<String> rows=new ArrayList<>(p.webHistory());
        void reload(){rows=new ArrayList<>(p.webHistory());notifyDataSetChanged();}
        public int getCount(){return rows.size();}public String getItem(int position){return rows.get(position);}public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){String value=getItem(position);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(4),0);TextView title=text(value,13,INK);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);row.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));ImageButton delete=new ImageButton(MainActivity.this);delete.setImageResource(android.R.drawable.ic_menu_delete);delete.setColorFilter(MUTED);delete.setBackgroundColor(Color.TRANSPARENT);delete.setContentDescription("删除网址历史");delete.setFocusable(false);row.addView(delete,new LinearLayout.LayoutParams(dp(48),dp(48)));delete.setOnClickListener(v->{urlInput.dismissDropDown();new AlertDialog.Builder(MainActivity.this).setTitle("删除网址历史？").setMessage(value+"\n仅删除历史，不停止当前监控。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{p.removeWebHistory(value);reload();urlInput.post(()->urlInput.showDropDown());}).show();});return row;}
        public Filter getFilter(){return new Filter(){protected FilterResults performFiltering(CharSequence input){FilterResults result=new FilterResults();List<String> data=p.webHistory();result.values=data;result.count=data.size();return result;}@SuppressWarnings("unchecked") protected void publishResults(CharSequence input,FilterResults result){rows=new ArrayList<>((List<String>)result.values);notifyDataSetChanged();}public CharSequence convertResultToString(Object value){return String.valueOf(value);}};}
    }
    void records(){
        LinearLayout stats=card("参与概览");recordMetrics=new TextView[6];String[] labels={"已确认参与","中奖次数","累计电池","单次最高","已结算中奖率","连续未中"};
        for(int row=0;row<3;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<2;col++){int i=row*2+col;LinearLayout metric=new LinearLayout(this);metric.setOrientation(1);metric.setPadding(dp(10),dp(6),dp(10),dp(6));metric.addView(text(labels[i],12,MUTED));recordMetrics[i]=text("—",22,ACCENT);metric.addView(recordMetrics[i]);line.addView(metric,new LinearLayout.LayoutParams(0,-2,1));}stats.addView(line);}
        statNote=text("",12,MUTED);stats.addView(statNote);controls(stats,"导出 CSV",this::exportRecords,"选择导出目录",()->pick("exports"));
        recordRows=new LinearLayout(this);recordRows.setOrientation(1);content.addView(recordRows);renderRecords();link(content,"加载更多记录",()->{recordLimit=Math.min(500,recordLimit+40);recordSignature="";renderRecords();});
    }
    void exportRecords(){try{String path=FilesOut.write(this,"exports","红包记录-"+System.currentTimeMillis()+".csv",repo.csv().getBytes(StandardCharsets.UTF_8),"text/csv");toast("已导出："+path);repo.log("导出",path);}catch(Exception e){toast("导出失败："+e.getMessage());}}
    void renderRecords(){
        Repo.Summary stats=repo.summary();String[] values={""+stats.participated(),""+stats.wins(),""+stats.batteries(),""+stats.maximum(),stats.rate(),""+p.n("losses",0)};for(int i=0;i<values.length;i++)recordMetrics[i].setText(values[i]);statNote.setText("电池仅计已识别的实际所得 · 数额待确认 "+stats.unknownPrize()+" 次\n旧版无法确认的记录不推定为已参与。");
        List<Repo.RecordRow> rows=repo.records(recordLimit);StringBuilder signature=new StringBuilder();for(Repo.RecordRow r:rows)signature.append(r.id()).append(r.state()).append(r.quantity()).append(r.participated());if(recordSignature.equals(signature.toString())&&recordRows.getChildCount()>0)return;recordSignature=signature.toString();recordRows.removeAllViews();
        if(rows.isEmpty()){recordRows.addView(text("还没有已执行的红包记录。",14,MUTED));return;}
        for(Repo.RecordRow r:rows){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(bg(CARD,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);recordRows.addView(box,lp);
            String prize=r.state().equals("中奖")?(r.quantity()==null?"数额待确认":r.quantity()+r.unit()):"";box.addView(text(r.state()+(prize.isEmpty()?"":" · "+prize),17,r.state().equals("中奖")?ACCENT:INK));
            fieldLine(box,"消息来源",r.source());fieldLine(box,"直播间信息","房间 "+r.room()+(r.anchor().isEmpty()?"":" · "+r.anchor()));fieldLine(box,"红包信息",r.amount()+"电池 · "+r.condition()+"\n"+new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(r.end()))+" 开奖");fieldLine(box,"参与结果",(r.participated()?"已确认参与":"未确认参与")+" · "+r.account()+(prize.isEmpty()?"":"\n实际所得："+prize));link(box,"查看详情",()->showText("红包记录详情","房间 "+r.room()+"\n"+r.state()+"\n"+r.evidence()));
        }
    }
    void settings(){
        LinearLayout a=settingSection("权限与消息源");button(a,"① 开启无障碍服务",()->PermissionStatus.accessibility(this));button(a,"② 开启 QQ 通知读取",()->PermissionStatus.notifications(this));button(a,"③ 电池与后台运行设置",()->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        note(a,PermissionStatus.summary(this));button(a,"应用详情 / 后台运行设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        note(a,"小米手机还需允许自启动、后台无限制；安装来源限制可能要求在应用详情里允许受限设置。无需 Root。");
        field(a,"目标群通知标题关键词（群名称，不是群号）","group","B站直播区游戏群",false);
        link(a,"检查当前消息来源",()->show("消息"));
        button(a,"只读识别红包（3秒后）",()->{PocketService service=PocketService.instance;if(service==null){toast("请先连接无障碍");return;}moveTaskToBack(true);handler.postDelayed(service::inspectPacket,3000);});
        link(a,"查看运行日志",()->showText("运行日志",repo.events()));
        LinearLayout network=settingSection("网页监控设置");
        link(network,"监控网址与历史记录",()->show("消息"));
        link(network,"读取方式："+readerLabel(),this::chooseReader);
        link(network,"WebSocket 地址与认证（可选）",this::socketSettings);
        link(network,"JSON 字段映射（高级）",this::jsonSettings);
        field(network,"接口刷新 / 重连基准间隔（5～300秒）","webInterval","5",false);note(network,"自动识别支持原红包池接口与 bili-lottery 推送页面。其他JSON接口可手动映射字段、金额单位和绝对开奖时间。推送网站如需user/key，请填写该网站自己的凭据；不会使用B站账号密码。");
        link(network,"打开原网页",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(PoolEndpoint.source(p.s("webUrl",PoolEndpoint.DEFAULT)).replaceFirst("/api/state$",""))));}catch(RuntimeException e){toast("网页地址无效");}});
        targetSettings();
        LinearLayout b=settingSection("黑名单");field(b,"房间号（逗号或换行分隔）","blacklist","",true);note(b,"黑名单在接收消息和进房前都会检查。");
        LinearLayout timing=settingSection("任务与自动休眠");field(timing,"提前进房基准 / 秒","before","60",false);field(timing,"进房时间随机波动 ±秒（0关闭）","beforeJitter","10",false);field(timing,"开奖后最少留存 / 秒","after","30",false);field(timing,"留存额外随机增加 0～秒（0关闭）","afterExtra","10",false);
        note(timing,"默认提前50～70秒进房，开奖后留存30～40秒。同一任务只抽取一次；设置修改影响尚未排定时间的新任务。时间波动不保证避免平台识别。");
        field(timing,"连续多少个明确未中奖后休眠","lossLimit","10",false);field(timing,"多久后自动恢复 / 分钟","restMinutes","60",false);button(timing,"清零连续未中奖",this::clearLosses);
        Switch club=new Switch(this);club.setText("每个房间首次允许1电池入团");club.setTextColor(INK);club.setChecked(p.b("allowOneBattery",false));club.setOnCheckedChangeListener((v,on)->p.put("allowOneBattery",on));timing.addView(club);note(timing,"默认关闭。仅限明确标注需入团（1电池）的红包；每个房间只尝试一次。其他付费弹窗或超过1电池时停止。");
        note(timing,"已参与后停留在红包面板等开奖。同房多个红包按最长留存处理。留存到点后返回B站首页并确认退出，才进入新房间；休眠期间也会先完成退出。参与金额与期望按“目标红包”筛选。同日再次进房不发消息。进房失败两次当天跳过。");
        LinearLayout mystic=settingSection("玄学抢红包");
        Switch multi=new Switch(this);multi.setText("在抢红包页面多指点击红包");multi.setTextColor(INK);multi.setChecked(p.b("mysticMultiTap",false));multi.setOnCheckedChangeListener((v,on)->p.put("mysticMultiTap",on));mystic.addView(multi);
        note(mystic,"默认关闭。确认已参与后，在等待开奖时以3～8指快速点按当前红包卡片的装饰区域，每轮3次短按，约每1.8秒一轮。避开抢、领取和关闭按钮；开奖前3秒停止。不改变抽奖结果，也没有证据能提高中奖率。系统或页面无法确认时不执行。");
        LinearLayout msg=settingSection("互动消息 · 可选");Switch enabled=new Switch(this);enabled.setText("启用已配置的互动消息");enabled.setTextColor(INK);enabled.setChecked(p.b("messages",true));enabled.setOnCheckedChangeListener((v,on)->p.put("messages",on));msg.addView(enabled);
        note(msg,"每行执行一个动作，行的顺序即发送顺序。text: 来啦！|好耶~ 表示随机选一句；emoji:EveOneCat2 使用已录制表情路径。未找到发送按钮不会声称发送成功。");
        field(msg,"确认参与后","joinActions","text:来了~",true);field(msg,"明确中奖后","winActions","text:谢谢老板",true);field(msg,"明确未中奖后","loseActions","",true);
        button(msg,"录制 EveOneCat2 表情点击路径",()->{PocketService s=PocketService.instance;if(s==null){toast("先开启无障碍");return;}s.recordEmoji();});
        field(msg,"EveOneCat2 表情路径（高级）","emoji.EveOneCat2","",true);note(msg,"录制不同表情可追加多条路径，发送时随机选择。路径包含打开输入框/表情列表、选择表情、发送的完整步骤。必须在当前手机和B站界面完成标定。");
        LinearLayout coords=settingSection("界面标定 · 高级");note(coords,"坐标格式为屏幕宽高比例 x,y（0~1）。不同手机布局不同，默认坐标需实机确认。标定前先打开需要操作的B站界面。");field(coords,"红包入口","packetPoint","0.08,0.17",false);field(coords,"红包面板关闭","packetClose","",false);field(coords,"关注弹窗关闭","followClose","",false);
        for(String[] point:new String[][]{{"packetPoint","红包入口"},{"packetClose","红包关闭"},{"followClose","关注弹窗关闭"}})button(coords,"点选标定："+point[1],()->{if(PocketService.instance!=null)PocketService.instance.recordPoint(point[0]);else toast("先开启无障碍");});
        LinearLayout dual=settingSection("应用双开 · 实验功能");note(dual,"已确认本机支持两个B站小窗口同时显示，两个直播间可能同时驻留。手势仍依次执行；需要检查无障碍能否分别读取两个窗口，并按各自边界识别、点击和核对账号。当前预览版仍按单账号调度，多窗口路由待验证。");
        button(dual,"检测 B站小窗口",()->{if(PocketService.instance==null){toast("请先开启无障碍");return;}toast("5秒后检测，请立即切回两个B站小窗口；结果在消息页查看");handler.postDelayed(()->{if(PocketService.instance!=null){PocketService.instance.windowReport();toast("检测已记录，请在消息页查看");}},5000);});
        note(dual,"按轮流放大方式适配。以下测试只点击迷你窗中央，不抢红包、不发送消息；请先停止运行。测试结果记录在消息页。");
        for(boolean left:new boolean[]{true,false})button(dual,"双开校准：放大"+(left?"左":"右")+"侧小窗",()->{PocketService service=PocketService.instance;if(service==null){toast("请先连接无障碍");return;}if(service.running){toast("请先停止运行");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewFloatingWindow(left),2000);});
        note(dual,"循环测试：左窗长按右下角向左上拖；右窗长按左下角向右上拖，并检查窗口尺寸；失败只记录日志，不会显示为成功。测试前先将两个窗口恢复为迷你窗。");
        for(boolean left:new boolean[]{true,false})button(dual,"循环测试："+(left?"左":"右")+"窗放大后自动缩小",()->{PocketService service=PocketService.instance;if(service==null){toast("请先开启无障碍");return;}if(service.running){toast("请先停止任务");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewFloatingCycle(left),2000);});
        note(dual,"本机约定：左侧主账号，右侧备用账号。进房路由需单独验证；主账号与备用账号分别定位窗口，两个账号均在各自B站窗口内按房间号搜索进房。两项均不会点击红包或发送消息。");
        field(dual,"进房验证房间号（两个账号统一使用B站内搜索）","routeRoom","",false);
        for(boolean backup:new boolean[]{false,true})button(dual,backup?"按房间号验证备用账号进房":"验证主账号进房",()->{PocketService service=PocketService.instance;if(service==null){toast("请先开启无障碍");return;}if(service.running){toast("请先停止任务");return;}String target=p.s("routeRoom","");if(!target.matches("[1-9][0-9]{0,14}")){toast("先填写验证房间号");return;}moveTaskToBack(true);handler.postDelayed(()->service.previewRoomRoute(target,backup),2000);});
        LinearLayout safe=settingSection("异常停止");note(safe,"当前验证锁定："+p.s("verificationLock","无"));button(safe,"已人工处理验证，解除本地锁定",()->new AlertDialog.Builder(this).setTitle("解除本地锁定").setMessage("请先在B站中手动完成验证并确认账号状态正常。解除只允许再次启动，不代表平台限制已解除。").setPositiveButton("确认已处理",(d,w)->{p.put("verificationLock","");show("设置");}).setNegativeButton("取消",null).show());
        LinearLayout about=settingSection("关于与更新");note(about,"0.1.14 · 目标红包 / 深色房间菜单\n任务与通知记录保存在手机；不保存截图文件。\n单账号流程；双开调度暂未启用。网址不代表统一数据格式，未识别的站点需要选择读取方式并映射字段。");button(about,"GitHub 下载与更新",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/struggle0929/bili-pocket-android/releases"))));
    }
    void targetSettings(){
        LinearLayout box=settingSection("目标红包");note(box,"当前 · "+p.target().description());RadioGroup modes=new RadioGroup(this);modes.setOrientation(1);box.addView(modes);
        String[] values={"default","total","share"},labels={"模式一 · 默认（总电池 > 20）","模式二 · 红包电池 ≥","模式三 · 红包期望 ≥"};int[] ids=new int[3];
        for(int i=0;i<3;i++){RadioButton radio=new RadioButton(this);radio.setId(View.generateViewId());ids[i]=radio.getId();radio.setText(labels[i]);radio.setTextSize(15);radio.setTextColor(INK);radio.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));modes.addView(radio);}
        int selected=Arrays.asList(values).indexOf(p.s("targetMode","default"));modes.check(ids[Math.max(0,selected)]);
        LinearLayout totalBox=new LinearLayout(this);totalBox.setOrientation(1);note(totalBox,"红包电池总量下限（电池）");EditText total=targetInput(totalBox,p.s("targetTotal","100"));box.addView(totalBox);
        LinearLayout shareBox=new LinearLayout(this);shareBox.setOrientation(1);note(shareBox,"红包期望下限（电池/份）");EditText share=targetInput(shareBox,p.s("targetShare","10"));box.addView(shareBox);
        Runnable paint=()->{totalBox.setVisibility(modes.getCheckedRadioButtonId()==ids[1]?View.VISIBLE:View.GONE);shareBox.setVisibility(modes.getCheckedRadioButtonId()==ids[2]?View.VISIBLE:View.GONE);};modes.setOnCheckedChangeListener((g,id)->paint.run());paint.run();
        note(box,"期望 = 红包总电池 ÷ 份数，例如100电池/5份 = 20电池/份。支持小数，边界含等于。自定义模式以填写阈值为准，可包含20电池；期望模式下份数未知则不参与。关注/粉丝团、黑名单与其他条件继续生效。");
        button(box,"应用目标红包设置",()->{
            if(PocketService.instance!=null&&PocketService.instance.running){toast("请先停止自动任务再修改目标红包");return;}
            String mode=modes.getCheckedRadioButtonId()==ids[1]?"total":modes.getCheckedRadioButtonId()==ids[2]?"share":"default",totalValue=total.getText().toString().trim(),shareValue=share.getText().toString().trim();
            try{TargetRule rule=TargetRule.of(mode,mode.equals("share")?shareValue:totalValue);String oldMode=p.s("targetMode","default"),oldTotal=p.s("targetTotal","100"),oldShare=p.s("targetShare","10");
                p.p.edit().putString("targetMode",mode).putString("targetTotal",totalValue).putString("targetShare",shareValue).apply();
                try{repo.refilter(p);}catch(RuntimeException e){p.p.edit().putString("targetMode",oldMode).putString("targetTotal",oldTotal).putString("targetShare",oldShare).apply();throw e;}
                repo.log("目标红包",rule.description()+"；已重新筛选缓存消息与待执行队列");hideKeyboard();show("设置");toast("目标红包已应用，消息与队列已同步更新");
            }catch(IllegalArgumentException e){toast(e.getMessage());}catch(RuntimeException e){toast("保存失败："+e.getClass().getSimpleName());}
        });
    }
    EditText targetInput(LinearLayout box,String value){EditText edit=new EditText(this);edit.setText(value);edit.setTextColor(INK);edit.setTextSize(16);edit.setSingleLine();edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;}
    String readerLabel(){return p.s("webReader","auto").equals("ws")?"WebSocket 推送":p.s("webReader","auto").equals("json")?"自定义 JSON 接口":"自动识别（推荐）";}
    boolean changeReader(Runnable update){PocketService service=PocketService.instance;if(service!=null&&service.running){toast("请先停止任务再修改读取方式");return false;}boolean listening=p.b("listen",false);p.put("listen",false);if(service!=null&&service.web!=null)service.web.sync();update.run();p.put("webLastSuccess","0");p.put("webStatus","读取设置已更新");if(listening&&service!=null&&service.web!=null){p.put("listen",true);service.web.refresh();}show("设置");return true;}
    void chooseReader(){String[] values={"auto","ws","json"},labels={"自动识别：接口或网页声明的推送","WebSocket：推送地址或可识别页面","自定义 JSON：直接接口与字段映射"};int chosen=Arrays.asList(values).indexOf(p.s("webReader","auto"));final int[] selected={Math.max(0,chosen)};new AlertDialog.Builder(this).setTitle("网页读取方式").setSingleChoiceItems(labels,selected[0],(d,i)->selected[0]=i).setNegativeButton("取消",null).setPositiveButton("应用",(d,w)->changeReader(()->p.put("webReader",values[selected[0]]))).show();}
    EditText dialogField(LinearLayout box,String label,String value,boolean secret){TextView caption=text(label,13,Color.DKGRAY);box.addView(caption);EditText edit=new EditText(this);edit.setSingleLine();edit.setTextSize(14);edit.setText(value);if(secret)edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(edit);return edit;}
    void socketSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));EditText address=dialogField(box,"WSS地址（留空则从网页识别）",p.s("webSocketUrl",""),false),user=dialogField(box,"网站user（可留空）",p.s("webStreamUser",""),false),key=dialogField(box,"网站key（可留空）",p.s("webStreamKey",""),true);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("推送地址与网站认证").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{String endpoint=address.getText().toString().trim();if(!endpoint.isEmpty())PoolEndpoint.socket(endpoint);String u=user.getText().toString().trim(),k=key.getText().toString().trim();if(u.length()>1024||k.length()>1024)throw new IllegalArgumentException("认证信息过长");if(changeReader(()->{p.put("webSocketUrl",endpoint);p.put("webStreamUser",u);p.put("webStreamKey",k);}))dialog.dismiss();}catch(IllegalArgumentException e){toast(e.getMessage());}}));dialog.show();
    }
    void jsonSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(8),dp(18),dp(8));TextView help=text("在消息页输入直接JSON接口。路径用点号，根数组用$。amountUnit：gold或battery；timeUnit：seconds、milliseconds或iso。onlyBattery=true仅用于已确认只含电池红包的接口。",12,Color.DKGRAY);box.addView(help);EditText schema=new EditText(this);schema.setTextSize(12);schema.setSingleLine(false);schema.setMinLines(7);schema.setMaxLines(12);schema.setText(p.s("webJsonMapping",GenericJsonParser.EXAMPLE));box.addView(schema);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自定义JSON字段映射").setView(box).setNegativeButton("取消",null).setPositiveButton("保存并使用",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{String value=schema.getText().toString().trim();GenericJsonParser.mapping(value);if(changeReader(()->{p.put("webJsonMapping",value);p.put("webReader","json");}))dialog.dismiss();}catch(Exception e){toast(e instanceof org.json.JSONException?"字段映射不是有效JSON":e.getMessage());}}));dialog.show();
    }
    void field(LinearLayout box,String title,String key,String def,boolean multiline){
        box.addView(text(title,14,MUTED));EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setText(p.s(key,def));e.setSingleLine(!multiline);e.setTextSize(15);box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        e.setOnFocusChangeListener((v,focus)->{if(!focus)p.put(key,e.getText().toString().trim());});
        e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){p.put(key,s.toString().trim());}public void afterTextChanged(android.text.Editable x){}});
    }
    void pick(String key){folder=key;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,42);}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null)return;if(r==42){Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));p.put(folder,u.toString());show(page);}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
