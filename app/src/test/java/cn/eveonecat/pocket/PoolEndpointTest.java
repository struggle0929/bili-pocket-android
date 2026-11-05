package cn.eveonecat.pocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class PoolEndpointTest {
    @Test public void defaultAddressKeepsHttpsAndCustomPort(){assertEquals("https://txhb.tangsong0v0.icu:8443/pool/api/state",PoolEndpoint.api(PoolEndpoint.DEFAULT));}
    @Test public void trailingSlashAndDirectApiAreNormalized(){assertEquals(PoolEndpoint.api(PoolEndpoint.DEFAULT),PoolEndpoint.api(PoolEndpoint.DEFAULT+"/"));assertEquals(PoolEndpoint.api(PoolEndpoint.DEFAULT),PoolEndpoint.api(PoolEndpoint.DEFAULT+"/api/state"));}
    @Test public void unsupportedInsecureOrCredentialUrlsAreRejected(){for(String u:new String[]{"http://example.com/pool","javascript:alert(1)","https://user:password@example.com/pool","https://example.com/pool?token=x","https://example.com/pool#x","not a url"})try{PoolEndpoint.api(u);fail(u);}catch(IllegalArgumentException expected){}}
}
