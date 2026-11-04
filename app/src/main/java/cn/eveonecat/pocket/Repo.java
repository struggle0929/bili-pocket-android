package cn.eveonecat.pocket;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

final class Repo extends SQLiteOpenHelper {
    Repo(Context c){super(c,"pocket.db",null,2);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY,room TEXT, end INTEGER,amount INTEGER,condition TEXT,state TEXT DEFAULT '等待',account TEXT DEFAULT '主账号',evidence TEXT DEFAULT '',UNIQUE(room,end,account))");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY,stamp INTEGER,kind TEXT,detail TEXT)");
        db.execSQL("CREATE TABLE visits(day TEXT,room TEXT,PRIMARY KEY(day,room))");
        db.execSQL("CREATE TABLE messages_seen(fingerprint TEXT PRIMARY KEY,stamp INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int a,int b){if(a<2)db.execSQL("CREATE TABLE IF NOT EXISTS messages_seen(fingerprint TEXT PRIMARY KEY,stamp INTEGER)");}
    void recover(){
        // Process death loses the foreground card identity; a previous click must not be replayed.
        ContentValues v=new ContentValues();v.put("state","无法确认");v.put("evidence","上次服务中断，无法恢复参与证据；为避免重复提交不自动重试");
        int n=getWritableDatabase().update("tasks",v,"state IN ('进房中','已参与','已点击待确认')",null);
        if(n>0)log("中断恢复",n+" 个任务标记为无法确认，不计连续未中奖");
    }
    void log(String kind,String detail){
        ContentValues v=new ContentValues();v.put("stamp",System.currentTimeMillis());v.put("kind",kind);v.put("detail",detail==null?"":detail);long id=getWritableDatabase().insert("events",null,v);
        if(id%100==0)getWritableDatabase().execSQL("DELETE FROM events WHERE id < ?",new Object[]{id-5000});
    }
    boolean visit(String room){ContentValues v=new ContentValues();v.put("day",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());v.put("room",room);return getWritableDatabase().insertWithOnConflict("visits",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;}
    void ingest(String text,long sent,Prefs p){
        if(text==null||text.trim().isEmpty())return;
        if(text.length()>32000){log("消息过滤","通知过长，无法可靠解析");return;}
        String fingerprint;
        try {fingerprint=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest((sent+":"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        ContentValues seen=new ContentValues();seen.put("fingerprint",fingerprint);seen.put("stamp",System.currentTimeMillis());
        if(getWritableDatabase().insertWithOnConflict("messages_seen",null,seen,SQLiteDatabase.CONFLICT_IGNORE)==-1)return;
        getWritableDatabase().delete("messages_seen","stamp < ?",new String[]{""+(System.currentTimeMillis()-86400000)});
        log("QQ消息",text);
        int count=0;
        if(System.currentTimeMillis()-sent>120000||sent-System.currentTimeMillis()>30000){log("消息过滤","通知时间过旧或异常");return;}
        for(PacketParser.Packet x:PacketParser.parse(text,sent)){
            if(x.end()<=System.currentTimeMillis())continue;
            if(PacketParser.blacklisted(p.s("blacklist",""),x.room())){log("黑名单跳过",x.room());continue;}
            try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE room=? AND abs(end-?)<3000",new String[]{x.room(),""+x.end()})){if(c.moveToFirst())continue;}
            ContentValues v=new ContentValues();v.put("room",x.room());v.put("end",x.end());v.put("amount",x.amount());v.put("condition",x.condition());
            if(getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)count++;
        }
        log("解析","新增 "+count+" 个任务；仅关注/粉丝团、超过20电池、有效房间链接和倒计时");
    }
    static class Task {long id,end;String room,state;int amount; Task(Cursor c){id=c.getLong(0);room=c.getString(1);end=c.getLong(2);amount=c.getInt(3);state=c.getString(4);}}
    List<Task> tasks(){List<Task> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,room,end,amount,state FROM tasks ORDER BY CASE WHEN state IN ('中奖','未中奖','未参与','无法确认','已过期','黑名单','进房失败','已跳过') THEN 1 ELSE 0 END,end ASC",null)){while(c.moveToNext())list.add(new Task(c));}return list;}
    void state(long id,String state,String evidence){ContentValues v=new ContentValues();v.put("state",state);v.put("evidence",evidence);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+id});log(state,"任务 "+id+" "+evidence);}
    String events(){StringBuilder b=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT stamp,kind,detail FROM events ORDER BY id DESC LIMIT 100",null)){while(c.moveToNext())b.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(c.getLong(0)))).append(" · ").append(c.getString(1)).append("\n").append(c.getString(2)).append("\n\n");}return b.toString();}
    String csv(){StringBuilder b=new StringBuilder("\ufeff房间号,开奖时间,电池总额,参与条件,状态,账号,证据\r\n");try(Cursor c=getReadableDatabase().rawQuery("SELECT room,end,amount,condition,state,account,evidence FROM tasks ORDER BY end",null)){while(c.moveToNext()){for(int i=0;i<7;i++){if(i>0)b.append(',');String value=c.isNull(i)?"":c.getString(i);if(i==1)value=java.time.Instant.ofEpochMilli(c.getLong(i)).atZone(java.time.ZoneId.of("Asia/Shanghai")).toString();if(value.matches("^[=+@\\-].*"))value="'"+value;b.append('"').append(value.replace("\"","\"\"")).append('"');}b.append("\r\n");}}return b.toString();}
}
