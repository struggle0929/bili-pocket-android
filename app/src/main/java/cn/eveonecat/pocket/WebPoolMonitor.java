package cn.eveonecat.pocket;

import android.content.Context;
import android.os.Handler;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Network work never runs on the accessibility/UI thread. Only the active source can commit. */
final class WebPoolMonitor {
    final Context context;final Handler main;final ExecutorService worker=Executors.newSingleThreadExecutor();
    boolean closed,fetching;long due,generation;String source="";int failures;volatile HttpURLConnection active;
    WebPoolMonitor(Context context,Handler main){this.context=context.getApplicationContext();this.main=main;}
    void sync(){
        if(closed)return;Prefs p=new Prefs(context);
        if(!p.b("listen",false)||!p.webMode()){if(!source.isEmpty()){source="";generation++;HttpURLConnection c=active;if(c!=null)c.disconnect();}return;}
        String selected;try{selected=p.sourceKey();}catch(IllegalArgumentException e){p.put("webStatus",e.getMessage());return;}
        if(!source.equals(selected)){source=selected;generation++;due=0;failures=0;}
        if(!fetching&&System.currentTimeMillis()>=due)fetch(selected,p.s("webUrl",PoolEndpoint.DEFAULT));
    }
    void refresh(){due=0;sync();}
    void fetch(String selected,String page){
        fetching=true;long epoch=generation;new Prefs(context).put("webStatus","正在读取网页红包…");
        worker.execute(()->{
            List<PacketNotice> rows=null;String error=null;
            try{rows=PoolParser.parse(download(PoolEndpoint.api(page)),System.currentTimeMillis());}catch(Exception e){error=e instanceof java.net.SocketTimeoutException?"网页连接超时":e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final List<PacketNotice> data=rows;final String failure=error;
            main.post(()->{
                fetching=false;if(closed)return;Prefs p=new Prefs(context);String key;try{key=p.sourceKey();}catch(IllegalArgumentException e){return;}
                if(epoch!=generation||!p.webMode()||!p.b("listen",false)||!key.equals(selected))return;
                int interval=Math.max(5,Math.min(300,p.n("webInterval",5)));long now=System.currentTimeMillis();
                if(failure==null){
                    try(Repo repo=new Repo(context)){
                        int added=repo.saveNotices(selected,p.sourceName(),data,p,"",now);failures=0;
                        p.put("webStatus","更新 "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(now))+" · "+data.size()+" 条红包 · 新增任务 "+added);p.put("webLastSuccess",""+now);
                        if(added>0)repo.log("网页监控","新增 "+added+" 个任务，来源 "+page);
                    }catch(RuntimeException e){failed(p,"保存网页消息失败："+e.getClass().getSimpleName());}
                }else failed(p,failure);
                due=now+Math.max(interval,failures==0?interval:Math.min(60,5*(1<<Math.min(failures,4))))*1000L;
            });
        });
    }
    void failed(Prefs p,String error){failures++;String message=error.length()>180?error.substring(0,180):error;p.put("webStatus","读取失败 · "+message+" · 稍后重试");if(failures==1)try(Repo repo=new Repo(context)){repo.log("网页连接失败",message);}}
    String download(String endpoint)throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();active=c;
        try{
            c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","PocketLive/0.1.12");
            int status=c.getResponseCode();if(status!=200)throw new IOException("网页接口 HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2*1024*1024)throw new IOException("网页数据超过2MB，已停止读取");}return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{active=null;c.disconnect();}
    }
    void close(){closed=true;generation++;HttpURLConnection c=active;if(c!=null)c.disconnect();worker.shutdownNow();}
}
