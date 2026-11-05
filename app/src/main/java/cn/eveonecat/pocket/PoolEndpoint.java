package cn.eveonecat.pocket;

import java.net.URI;

final class PoolEndpoint {
    static final String DEFAULT="https://txhb.tangsong0v0.icu:8443/pool";
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
