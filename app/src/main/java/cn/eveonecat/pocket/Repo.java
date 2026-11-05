package cn.eveonecat.pocket;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

final class Repo extends SQLiteOpenHelper {
    final Context context;
    Repo(Context c){super(c,"pocket.db",null,6);context=c.getApplicationContext();}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY,room TEXT, end INTEGER,amount INTEGER,shares INTEGER NOT NULL DEFAULT 0,condition TEXT,state TEXT DEFAULT '等待',account TEXT DEFAULT '主账号',evidence TEXT DEFAULT '',prize_quantity INTEGER,prize_unit TEXT DEFAULT '',participated INTEGER NOT NULL DEFAULT 0,UNIQUE(room,end,account))");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY,stamp INTEGER,kind TEXT,detail TEXT)");
        db.execSQL("CREATE TABLE visits(day TEXT,room TEXT,PRIMARY KEY(day,room))");
        db.execSQL("CREATE TABLE messages_seen(fingerprint TEXT PRIMARY KEY,stamp INTEGER)");
        createFeedTables(db);
    }
    public void onUpgrade(SQLiteDatabase db,int a,int b){if(a<2)db.execSQL("CREATE TABLE IF NOT EXISTS messages_seen(fingerprint TEXT PRIMARY KEY,stamp INTEGER)");if(a<3){db.execSQL("ALTER TABLE tasks ADD COLUMN prize_quantity INTEGER");db.execSQL("ALTER TABLE tasks ADD COLUMN prize_unit TEXT DEFAULT ''");}if(a<4){createFeedTables(db);db.execSQL("INSERT OR IGNORE INTO task_sources(task_id,source) SELECT id,'qq' FROM tasks");}if(a<5){db.execSQL("ALTER TABLE tasks ADD COLUMN participated INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE tasks SET participated=1 WHERE state IN ('已参与','中奖','未中奖')");}if(a<6){
        db.execSQL("ALTER TABLE tasks ADD COLUMN shares INTEGER NOT NULL DEFAULT 0");
        if(a>=4)db.execSQL("ALTER TABLE notices ADD COLUMN base_reason TEXT NOT NULL DEFAULT ''");
        db.execSQL("UPDATE notices SET base_reason=CASE WHEN reason='20电池及以下，不参与' THEN '' ELSE coalesce(reason,'') END");
        db.execSQL("UPDATE tasks SET shares=coalesce((SELECT n.shares FROM notices n WHERE n.task_id=tasks.id ORDER BY n.received DESC LIMIT 1),0)");
    }}
    static void createFeedTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE task_sources(task_id INTEGER NOT NULL,source TEXT NOT NULL,PRIMARY KEY(task_id,source))");
        db.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY,source TEXT NOT NULL,source_name TEXT,external_id TEXT NOT NULL,received INTEGER,end INTEGER,room TEXT,anchor TEXT,amount INTEGER,shares INTEGER,condition TEXT,reward TEXT,kind TEXT,base_reason TEXT NOT NULL DEFAULT '',reason TEXT,raw TEXT,task_id INTEGER,UNIQUE(source,external_id))");
        db.execSQL("CREATE INDEX notices_source_time ON notices(source,end)");
        db.execSQL("CREATE INDEX IF NOT EXISTS notices_task ON notices(task_id,received)");
    }
    void recover(){
        // Process death loses the foreground card identity; a previous click must not be replayed.
        ContentValues v=new ContentValues();v.put("state","无法确认");v.put("evidence","上次服务中断，无法恢复参与证据；为避免重复提交不自动重试");
        int n=getWritableDatabase().update("tasks",v,"state IN ('进房中','已参与','已点击待确认')",null);
        if(n>0)log("中断恢复",n+" 个任务标记为无法确认，不计连续未中奖");
    }
    void log(String kind,String detail){
        android.util.Log.i("PocketRun",kind+" · "+(detail==null?"":detail));
        ContentValues v=new ContentValues();v.put("stamp",System.currentTimeMillis());v.put("kind",kind);v.put("detail",detail==null?"":detail);long id=getWritableDatabase().insert("events",null,v);
        if(id%100==0)getWritableDatabase().execSQL("DELETE FROM events WHERE id < ?",new Object[]{id-5000});
    }
    boolean visit(String room){ContentValues v=new ContentValues();v.put("day",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());v.put("room",room);return getWritableDatabase().insertWithOnConflict("visits",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;}
    void ingest(String text,long sent,Prefs p){ingest(text,sent,p,p.sourceName());}
    void ingest(String text,long sent,Prefs p,String sourceName){
        if(text==null||text.trim().isEmpty()||text.length()>32000)return;
        String fingerprint=NoticeParser.digest(sent+":"+text);ContentValues seen=new ContentValues();seen.put("fingerprint",fingerprint);seen.put("stamp",System.currentTimeMillis());
        if(getWritableDatabase().insertWithOnConflict("messages_seen",null,seen,SQLiteDatabase.CONFLICT_IGNORE)==-1)return;
        getWritableDatabase().delete("messages_seen","stamp < ?",new String[]{""+(System.currentTimeMillis()-86400000)});log("QQ消息",text);
        long now=System.currentTimeMillis();String stale=now-sent>120000||sent-now>30000?"通知时间过旧或异常":"";
        int count=saveNotices("qq",sourceName,NoticeParser.parseData(text,sent),p,stale,now);
        log("解析","新增 "+count+" 个任务；消息页按红包分块展示");
    }
    int saveNotices(String source,String sourceName,List<PacketNotice> notices,Prefs p,String override,long now){
        SQLiteDatabase db=getWritableDatabase();int added=0;db.beginTransaction();
        try{for(PacketNotice n:notices)added+=storeNotice(db,source,sourceName,n,p,override,now,now);
            db.execSQL("DELETE FROM notices WHERE id NOT IN (SELECT id FROM notices ORDER BY received DESC,id DESC LIMIT 500)");db.setTransactionSuccessful();
        }finally{db.endTransaction();}return added;
    }
    int storeNotice(SQLiteDatabase db,String source,String sourceName,PacketNotice n,Prefs p,String override,long received,long now){
        String base=!override.isEmpty()?override:n.reason(),reason=base;
        if(PacketParser.blacklisted(p.s("blacklist",""),n.room()))reason="黑名单房间，不参与";
        if(reason.isEmpty()){if(n.end()<=now)reason="已开奖";else if(n.end()-now>86400000)reason="开奖时间异常";else reason=p.target().reason(n.amount(),n.shares());}
        Long task=null;int added=0;
        try(Cursor previous=db.rawQuery("SELECT task_id FROM notices WHERE source=? AND external_id=?",new String[]{source,n.key()})){if(previous.moveToFirst()&&!previous.isNull(0))task=previous.getLong(0);}
        if(reason.isEmpty()){
            try(Cursor c=db.rawQuery("SELECT id FROM tasks WHERE room=? AND amount=? AND condition=? AND abs(end-?)<3000",new String[]{n.room(),""+n.amount(),n.condition(),""+n.end()})){task=c.moveToFirst()?c.getLong(0):null;}
            if(task==null){ContentValues t=new ContentValues();t.put("room",n.room());t.put("end",n.end());t.put("amount",n.amount());t.put("shares",n.shares());t.put("condition",n.condition());long id=db.insertWithOnConflict("tasks",null,t,SQLiteDatabase.CONFLICT_IGNORE);if(id!=-1){task=id;added++;}else reason="同房同秒红包冲突，未加入队列";}
            if(task!=null){ContentValues link=new ContentValues();link.put("task_id",task);link.put("source",source);db.insertWithOnConflict("task_sources",null,link,SQLiteDatabase.CONFLICT_IGNORE);ContentValues count=new ContentValues();count.put("shares",n.shares());db.update("tasks",count,"id=? AND state='等待'",new String[]{""+task});}
        }
        ContentValues v=new ContentValues();v.put("source",source);v.put("source_name",sourceName);v.put("external_id",n.key());v.put("received",received);v.put("end",n.end());v.put("room",n.room());v.put("anchor",n.anchor());v.put("amount",n.amount());v.put("shares",n.shares());v.put("condition",n.condition());v.put("reward",n.reward());v.put("kind",n.kind());v.put("base_reason",base);v.put("reason",reason);v.put("raw",n.raw());if(task==null)v.putNull("task_id");else v.put("task_id",task);
        if(db.update("notices",v,"source=? AND external_id=?",new String[]{source,n.key()})==0)db.insertOrThrow("notices",null,v);return added;
    }
    record SavedNotice(String source,String sourceName,long received,PacketNotice notice){}
    void refilter(Prefs p){
        SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();List<SavedNotice> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT source,source_name,received,external_id,room,anchor,end,amount,shares,condition,reward,kind,base_reason,raw FROM notices ORDER BY received,id",null)){
            while(c.moveToNext())rows.add(new SavedNotice(c.getString(0),c.getString(1),c.getLong(2),new PacketNotice(c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getInt(7),c.getInt(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getString(13))));
        }
        db.beginTransaction();try{for(SavedNotice row:rows)storeNotice(db,row.source(),row.sourceName(),row.notice(),p,"",row.received(),now);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    record NoticeRow(long id,String sourceName,long received,long end,String room,String anchor,int amount,int shares,String condition,String reward,String reason,String raw,String state,String kind){}
    List<NoticeRow> notices(String source){
        List<NoticeRow> rows=new ArrayList<>();long now=System.currentTimeMillis();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT n.id,n.source_name,n.received,n.end,n.room,n.anchor,n.amount,n.shares,n.condition,n.reward,n.reason,n.raw,coalesce(t.state,''),n.kind FROM notices n LEFT JOIN tasks t ON t.id=n.task_id WHERE n.source=? ORDER BY CASE WHEN n.end>? THEN 0 ELSE 1 END,CASE WHEN n.end>? THEN n.end END ASC,n.received DESC,n.id DESC LIMIT 60",new String[]{source,""+now,""+now})){
            while(c.moveToNext())rows.add(new NoticeRow(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5),c.getInt(6),c.getInt(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getString(13)));
        }return rows;
    }
    static class Task {long id,end;String room,state;int amount,shares; Task(Cursor c){id=c.getLong(0);room=c.getString(1);end=c.getLong(2);amount=c.getInt(3);state=c.getString(4);shares=c.getInt(5);}}
    List<Task> tasks(){List<Task> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,room,end,amount,state,shares FROM tasks ORDER BY CASE WHEN state IN ('中奖','未中奖','未参与','无法确认','已过期','黑名单','进房失败','已跳过') THEN 1 ELSE 0 END,end ASC",null)){while(c.moveToNext())list.add(new Task(c));}return list;}
    List<Task> tasksForSource(String source){List<Task> list=new ArrayList<>();TargetRule target=new Prefs(context).target();try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.room,t.end,t.amount,t.state,t.shares FROM tasks t JOIN task_sources s ON s.task_id=t.id WHERE s.source=? AND (t.state<>'等待' OR (s.source='qq' AND NOT EXISTS (SELECT 1 FROM notices old WHERE old.source=s.source AND old.task_id=t.id)) OR EXISTS (SELECT 1 FROM notices n WHERE n.source=s.source AND n.task_id=t.id AND n.reason='')) ORDER BY CASE WHEN t.state IN ('中奖','未中奖','未参与','无法确认','已过期','黑名单','进房失败','已跳过') THEN 1 ELSE 0 END,t.end ASC",new String[]{source})){while(c.moveToNext()){Task task=new Task(c);if(!task.state.equals("等待")||target.reason(task.amount,task.shares).isEmpty())list.add(task);}}return list;}
    void state(long id,String state,String evidence){ContentValues v=new ContentValues();v.put("state",state);v.put("evidence",evidence);if(state.equals("已参与"))v.put("participated",1);int updated=getWritableDatabase().update("tasks",v,"id=? AND state NOT IN ('中奖','未中奖','未参与')",new String[]{""+id});if(updated>0)log(state,"任务 "+id+" "+evidence);}
    boolean win(long id,Integer quantity,String unit,String evidence){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT state FROM tasks WHERE id=?",new String[]{""+id})){
            if(!c.moveToFirst()||c.getString(0).equals("中奖"))return false;
            ContentValues v=new ContentValues();v.put("state","中奖");v.put("participated",1);if(quantity==null)v.putNull("prize_quantity");else v.put("prize_quantity",quantity);v.put("prize_unit",unit);v.put("evidence",evidence);db.update("tasks",v,"id=?",new String[]{""+id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        log("中奖","任务 "+id+" · 实际所得 "+(quantity==null?"未识别数额":quantity+unit)+" · "+evidence);return true;
    }
    int lossStreak(){List<String> states=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT state FROM tasks WHERE state IN ('中奖','未中奖') ORDER BY end DESC,id DESC",null)){while(c.moveToNext())states.add(c.getString(0));}return LossStreak.afterLatestWin(states);}
    record Summary(long participated,long wins,long losses,long batteries,long maximum,long unknownPrize){String rate(){long resolved=wins+losses;return resolved==0?"—":String.format(java.util.Locale.CHINA,"%.1f%%",100.0*wins/resolved);}}
    Summary summary(){try(Cursor c=getReadableDatabase().rawQuery("SELECT coalesce(sum(participated),0),coalesce(sum(CASE WHEN state='中奖' THEN 1 ELSE 0 END),0),coalesce(sum(CASE WHEN state='未中奖' THEN 1 ELSE 0 END),0),coalesce(sum(CASE WHEN state='中奖' AND prize_unit='电池' THEN prize_quantity ELSE 0 END),0),coalesce(max(CASE WHEN state='中奖' AND prize_unit='电池' THEN prize_quantity ELSE 0 END),0),coalesce(sum(CASE WHEN state='中奖' AND prize_quantity IS NULL THEN 1 ELSE 0 END),0) FROM tasks",null)){c.moveToFirst();return new Summary(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4),c.getLong(5));}}
    record RecordRow(long id,String room,long end,int amount,String condition,String state,String account,Integer quantity,String unit,boolean participated,String evidence,String source,String anchor){}
    List<RecordRow> records(int limit){List<RecordRow> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.room,t.end,t.amount,t.condition,t.state,t.account,t.prize_quantity,t.prize_unit,t.participated,t.evidence,coalesce((SELECT n.source_name FROM notices n WHERE n.task_id=t.id ORDER BY n.received DESC LIMIT 1),'QQ · 旧版记录'),coalesce((SELECT n.anchor FROM notices n WHERE n.task_id=t.id ORDER BY n.received DESC LIMIT 1),'') FROM tasks t WHERE t.state<>'等待' ORDER BY t.end DESC,t.id DESC LIMIT ?",new String[]{""+Math.min(500,Math.max(1,limit))})){while(c.moveToNext())rows.add(new RecordRow(c.getLong(0),c.getString(1),c.getLong(2),c.getInt(3),c.getString(4),c.getString(5),c.getString(6),c.isNull(7)?null:c.getInt(7),c.getString(8),c.getInt(9)==1,c.getString(10),c.getString(11),c.getString(12)));}return rows;}
    void alignEnd(Task task,long observedEnd){
        if(Math.abs(task.end-observedEnd)<12000)return;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE room=? AND id<>? AND abs(end-?)<5000",new String[]{task.room,""+task.id,""+observedEnd})){if(c.moveToFirst())return;}
        ContentValues v=new ContentValues();v.put("end",observedEnd);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+task.id});log("面板时间校准","任务 "+task.id+" · "+task.end+" → "+observedEnd);task.end=observedEnd;
    }
    String events(){StringBuilder b=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT stamp,kind,detail FROM events ORDER BY id DESC LIMIT 100",null)){while(c.moveToNext())b.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(c.getLong(0)))).append(" · ").append(c.getString(1)).append("\n").append(c.getString(2)).append("\n\n");}return b.toString();}
    String csv(){StringBuilder b=new StringBuilder("\ufeff房间号,开奖时间,电池总额,参与条件,状态,账号,实际获奖数量,奖品单位,证据,已确认参与\r\n");try(Cursor c=getReadableDatabase().rawQuery("SELECT room,end,amount,condition,state,account,prize_quantity,prize_unit,evidence,participated FROM tasks ORDER BY end",null)){while(c.moveToNext()){for(int i=0;i<10;i++){if(i>0)b.append(',');String value=c.isNull(i)?"":c.getString(i);if(i==1)value=java.time.Instant.ofEpochMilli(c.getLong(i)).atZone(java.time.ZoneId.of("Asia/Shanghai")).toString();if(value.matches("^[=+@\\-].*"))value="'"+value;b.append('"').append(value.replace("\"","\"\"")).append('"');}b.append("\r\n");}}return b.toString();}
}
