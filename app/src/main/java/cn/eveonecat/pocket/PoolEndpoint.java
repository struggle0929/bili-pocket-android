package cn.eveonecat.pocket;

import java.net.URI;

final class PoolEndpoint {
    static final String DEFAULT="https://txhb.tangsong0v0.icu:8443/pool";
    static String source(String value){
        String normalized=address(value,true);URI uri=URI.create(normalized);String path=uri.getPath();
        if(uri.getScheme().equals("https")&&(path.equals("/pool")||path.equals("/pool/api/state")))return api(normalized);
        return normalized;
    }
    static String page(String value){return address(value,false);}
    static String socket(String value){String result=address(value,true);if(!result.startsWith("wss://"))throw new IllegalArgumentException("推送地址必须以 wss:// 开头");return result;}
    private static String address(String value,boolean socket){
        try{URI u=new URI(value.trim());String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(java.util.Locale.ROOT),path=u.getPath();
            if(value.length()>2048||!(scheme.equals("https")||socket&&scheme.equals("wss"))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new IllegalArgumentException();
            if(path==null||path.isEmpty())path="/";while(path.length()>1&&path.endsWith("/"))path=path.substring(0,path.length()-1);
            return new URI(scheme,null,u.getHost().toLowerCase(java.util.Locale.ROOT),u.getPort(),path,null,null).toASCIIString();
        }catch(Exception e){throw new IllegalArgumentException("请填写 HTTPS 网页/接口，或 WSS 推送地址；不含查询参数和账号密钥");}
    }
    static String api(String value){
        try{
            URI uri=new URI(value.trim());String path=uri.getPath();
            if(value.length()>2048||!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()==0||uri.getPort()>65535)throw new IllegalArgumentException();
            if(path==null||path.isEmpty()||path.equals("/"))path="/pool";
            while(path.endsWith("/"))path=path.substring(0,path.length()-1);
            if(!path.endsWith("/api/state"))path+="/api/state";
            return new URI("https",null,uri.getHost().toLowerCase(java.util.Locale.ROOT),uri.getPort(),path,null,null).toASCIIString();
        }catch(Exception e){throw new IllegalArgumentException("请填写有效的 HTTPS 红包池地址（不含查询参数）");}
    }
}
