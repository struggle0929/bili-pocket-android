package cn.eveonecat.pocket;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Inspect advertised endpoints as text; never execute downloaded JavaScript. */
final class WsDiscovery {
    static List<String> scripts(String page,String html){
        URI base=URI.create(PoolEndpoint.page(page));List<String> result=new ArrayList<>();Matcher m=Pattern.compile("<script\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",Pattern.CASE_INSENSITIVE).matcher(html);
        while(m.find()&&result.size()<3)try{URI u=base.resolve(m.group(1));if(u.getHost()!=null&&u.getHost().equalsIgnoreCase(base.getHost())&&u.getPort()==base.getPort()&&u.getScheme().equals("https"))result.add(PoolEndpoint.page(u.toString()));}catch(RuntimeException ignored){}
        return result;
    }
    static String socket(String script){
        Set<String> found=new LinkedHashSet<>();
        Matcher literal=Pattern.compile("new\\s+WebSocket\\s*\\(\\s*[\"'`](wss://[^\"'`]+)[\"'`]").matcher(script);
        while(literal.find())try{found.add(PoolEndpoint.socket(literal.group(1)));}catch(RuntimeException ignored){}
        Matcher transformed=Pattern.compile("new\\s+URL\\s*\\([^,]{1,80},\\s*[\"'`](https://[^\"'`]+)[\"'`]\\.replace\\(\\s*/\\^http/\\s*,\\s*[\"'`]ws[\"'`]\\s*\\)").matcher(script);
        if(script.contains("WebSocket")&&Pattern.compile("[\"'`]/ws[\"'`]").matcher(script).find())while(transformed.find())try{URI base=URI.create(PoolEndpoint.page(transformed.group(1)));found.add(PoolEndpoint.socket(new URI("wss",null,base.getHost(),base.getPort(),"/ws",null,null).toString()));}catch(Exception ignored){}
        if(found.size()>1)throw new IllegalArgumentException("网页有多个推送地址，请在读取设置中指定 WSS 地址");
        return found.isEmpty()?null:found.iterator().next();
    }
}
