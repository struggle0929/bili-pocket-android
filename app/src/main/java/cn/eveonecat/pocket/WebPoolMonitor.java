package cn.eveonecat.pocket;

import android.content.Context;
import android.os.Handler;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;

/** HTTP snapshots and WebSocket deltas share one source/epoch gate and the existing task rules. */
final class WebPoolMonitor {
    final Context context;final Handler main;final ExecutorService worker=Executors.newSingleThreadExecutor();
    final OkHttpClient socketClient=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
    final AtomicInteger pendingFrames=new AtomicInteger();
    boolean closed,fetching,socketOpen;long due,generation,socketStarted,lastFrame;String source="",cachedPage="",cachedSocket="";long discoveredAt;int failures;volatile HttpURLConnection active;WebSocket socket;
    record Read(List<PacketNotice> packets,String socket){}
    WebPoolMonitor(Context context,Handler main){this.context=context.getApplicationContext();this.main=main;}
    void sync(){
        if(closed)return;Prefs p=new Prefs(context);
        if(!p.b("listen",false)||!p.webMode()){if(!source.isEmpty()){source="";generation++;cancelTransport();}return;}
        String selected;try{selected=p.sourceKey();}catch(IllegalArgumentException e){p.put("webStatus",e.getMessage());return;}
        if(!source.equals(selected)){generation++;cancelTransport();source=selected;due=0;failures=0;}
        if(socket!=null){long now=System.currentTimeMillis();if(lastFrame==0&&now-socketStarted>20000)connectionFailed("推送连接或首条数据超时，请检查网站状态或 user/key");else if(lastFrame>0&&now-lastFrame>120000)connectionFailed("推送两分钟未更新，正在重新连接");return;}
        if(!fetching&&System.currentTimeMillis()>=due)fetch(selected,p);
    }
    void refresh(){generation++;cancelTransport();cachedSocket="";due=0;sync();}
    void cancelTransport(){HttpURLConnection c=active;if(c!=null)c.disconnect();if(socket!=null){WebSocket old=socket;socket=null;old.cancel();}socketOpen=false;lastFrame=0;}
    void fetch(String selected,Prefs p){
        fetching=true;long epoch=generation;String page=p.s("webUrl",PoolEndpoint.DEFAULT),mode=p.s("webReader","auto"),schema=p.s("webJsonMapping",GenericJsonParser.EXAMPLE),override=p.s("webSocketUrl","").trim();
        p.put("webStatus","正在识别网页数据入口…");
        worker.execute(()->{
            Read result=null;String error=null;
            try{result=read(page,mode,schema,override);}catch(Exception e){error=error(e);}
            final Read data=result;final String failure=error;
            main.post(()->{fetching=false;if(!valid(epoch,selected))return;Prefs current=new Prefs(context);if(failure!=null){failed(current,failure);return;}if(data.socket()!=null)openSocket(data.socket(),epoch,selected,current);else{save(data.packets(),current,"接口更新");schedule(current);}});
        });
    }
    Read read(String page,String mode,String schema,String override)throws Exception{
        String address=PoolEndpoint.source(page);
        if(mode.equals("json"))return new Read(GenericJsonParser.parseData(download(PoolEndpoint.page(page)),schema,System.currentTimeMillis()),null);
        if(!override.isEmpty())return new Read(null,PoolEndpoint.socket(override));
        if(address.startsWith("wss://"))return new Read(null,PoolEndpoint.socket(address));
        if(cachedPage.equals(address)&&!cachedSocket.isEmpty()&&System.currentTimeMillis()-discoveredAt<300000&&failures<2)return new Read(null,cachedSocket);
        String body=download(address),trimmed=body.trim();
        if(trimmed.startsWith("{")||trimmed.startsWith("[")){
            if(mode.equals("ws"))throw new IllegalArgumentException("该地址返回 JSON；请使用自动识别或填写 WSS 推送地址");
            Object json=new JSONTokener(trimmed).nextValue();
            if(json instanceof JSONObject&&((JSONObject)json).has("redpocket"))return new Read(PoolParser.parseData(body,System.currentTimeMillis()),null);
            return new Read(LotteryParser.parseData(body,System.currentTimeMillis()),null);
        }
        String endpoint=WsDiscovery.socket(body);
        if(endpoint==null)for(String script:WsDiscovery.scripts(PoolEndpoint.page(page),body)){endpoint=WsDiscovery.socket(download(script));if(endpoint!=null)break;}
        if(endpoint==null)throw new IllegalArgumentException("未发现支持的数据入口；可选择自定义 JSON 字段映射，或指定 WSS 地址");
        cachedPage=address;cachedSocket=endpoint;discoveredAt=System.currentTimeMillis();return new Read(null,endpoint);
    }
    void openSocket(String endpoint,long epoch,String selected,Prefs p){
        String user=p.s("webStreamUser",""),key=p.s("webStreamKey","");HttpUrl base=HttpUrl.get(endpoint.replaceFirst("^wss:","https:"));
        String url=base.newBuilder().addQueryParameter("user",user).addQueryParameter("key",key).build().toString().replaceFirst("^https:","wss:");
        Request.Builder request=new Request.Builder().url(url);String page=p.s("webUrl",PoolEndpoint.DEFAULT);if(page.startsWith("https://")){URI u=URI.create(PoolEndpoint.page(page));request.header("Origin",u.getScheme()+"://"+u.getRawAuthority());}
        socketStarted=System.currentTimeMillis();lastFrame=0;p.put("webStatus","正在连接 WebSocket 推送…");
        socket=socketClient.newWebSocket(request.build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){main.post(()->{if(!valid(epoch,selected)||socket!=ws)return;socketOpen=true;new Prefs(context).put("webStatus","推送已连接 · 等待红包数据");});}
            @Override public void onMessage(WebSocket ws,String text){
                if(closed)return;if(text.length()>2*1024*1024){main.post(()->{if(valid(epoch,selected)&&socket==ws)connectionFailed("推送消息超过2MB");});return;}
                if(pendingFrames.incrementAndGet()>20){pendingFrames.decrementAndGet();main.post(()->{if(valid(epoch,selected)&&socket==ws)connectionFailed("推送更新过快，正在重新连接");});return;}
                try{worker.execute(()->{List<PacketNotice> rows=null;String failure=null;try{rows=LotteryParser.parseData(text,System.currentTimeMillis());}catch(Exception e){failure=error(e);}finally{pendingFrames.decrementAndGet();}final List<PacketNotice> packets=rows;final String message=failure;main.post(()->{if(!valid(epoch,selected)||socket!=ws)return;if(message!=null){connectionFailed(message);return;}lastFrame=System.currentTimeMillis();save(packets,new Prefs(context),"推送更新");});});}catch(RejectedExecutionException ignored){pendingFrames.decrementAndGet();}
            }
            @Override public void onFailure(WebSocket ws,Throwable t,Response response){main.post(()->{if(valid(epoch,selected)&&socket==ws)connectionFailed(response!=null?"推送握手 HTTP "+response.code()+(response.code()==401||response.code()==403?"，请检查网站 user/key":""):"推送连接失败 · "+t.getClass().getSimpleName());});}
            @Override public void onClosed(WebSocket ws,int code,String reason){main.post(()->{if(valid(epoch,selected)&&socket==ws)connectionFailed("网站关闭推送（"+code+"）"+(code==1008?"，请检查 user/key":""));});}
            @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,null);}
        });
    }
    boolean valid(long epoch,String selected){if(closed||epoch!=generation)return false;Prefs p=new Prefs(context);try{return p.webMode()&&p.b("listen",false)&&p.sourceKey().equals(selected);}catch(RuntimeException e){return false;}}
    void save(List<PacketNotice> rows,Prefs p,String kind){
        long now=System.currentTimeMillis();try(Repo repo=new Repo(context)){int added=repo.saveNotices(p.sourceKey(),p.sourceName(),rows,p,"",now);failures=0;p.put("webStatus",kind+" "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(now))+" · 本次 "+rows.size()+" 条 · 新增任务 "+added);p.put("webLastSuccess",""+now);if(added>0)repo.log("网页监控",kind+" · 新增 "+added+" 个任务，来源 "+p.sourceName());}catch(RuntimeException e){failed(p,"保存网页消息失败 · "+e.getClass().getSimpleName());}
    }
    void connectionFailed(String message){cancelTransport();failed(new Prefs(context),message);}
    void failed(Prefs p,String message){failures++;p.put("webStatus","读取失败 · "+message+" · 稍后重试");schedule(p);if(failures==1)try(Repo repo=new Repo(context)){repo.log("网页连接失败",message);}}
    void schedule(Prefs p){int interval=Math.max(5,Math.min(300,p.n("webInterval",5)));due=System.currentTimeMillis()+Math.max(interval,failures==0?interval:Math.min(60,5*(1<<Math.min(failures,4))))*1000L;}
    static String error(Exception e){return e instanceof SocketTimeoutException?"网页连接超时":e instanceof JSONException?"响应不符合数据格式，请选择自定义 JSON 映射":e instanceof IllegalArgumentException?e.getMessage():e instanceof IOException&&e.getMessage()!=null&&e.getMessage().startsWith("HTTP ")?e.getMessage():"连接失败 · "+e.getClass().getSimpleName();}
    String download(String endpoint)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();active=c;long start=System.currentTimeMillis();
        try{c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept","application/json,text/html,*/*");c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","PocketLive/0.1.13");int status=c.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2*1024*1024||System.currentTimeMillis()-start>20000)throw new IOException("响应超出读取限制");}return out.toString(StandardCharsets.UTF_8.name());}
        }finally{active=null;c.disconnect();}
    }
    void close(){closed=true;generation++;cancelTransport();worker.shutdownNow();socketClient.dispatcher().executorService().shutdown();socketClient.connectionPool().evictAll();}
}
