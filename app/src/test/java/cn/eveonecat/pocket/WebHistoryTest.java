package cn.eveonecat.pocket;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebHistoryTest {
    @Test public void selectedHistoryMovesToFrontWithoutDuplicateEndpoint(){List<String> urls=WebHistory.remember(List.of("https://one.example/pool","https://two.example/pool"),"https://two.example/pool/");assertEquals(2,urls.size());assertEquals("https://two.example/pool/",urls.get(0));}
    @Test public void deleteRemovesOnlySelectedAddressAndStaysDeletedAfterReload(){List<String> urls=WebHistory.remove(List.of("https://one.example/pool","https://two.example/pool"),"https://one.example/pool/");assertEquals(List.of("https://two.example/pool"),WebHistory.parse(WebHistory.encode(urls)));assertTrue(WebHistory.parse(WebHistory.encode(WebHistory.remove(urls,urls.get(0)))).isEmpty());}
    @Test public void historyIsBoundedAndRoundTrips(){List<String> urls=List.of();for(int i=0;i<30;i++)urls=WebHistory.remember(urls,"https://host"+i+".example/pool");assertEquals(20,urls.size());assertEquals(urls,WebHistory.parse(WebHistory.encode(urls)));}
    @Test public void brokenOrUnsafeHistoryIsIgnored(){assertTrue(WebHistory.parse("not JSON").isEmpty());assertEquals(List.of(PoolEndpoint.DEFAULT),WebHistory.parse("[\"javascript:alert(1)\",\""+PoolEndpoint.DEFAULT+"\"]"));}
}
