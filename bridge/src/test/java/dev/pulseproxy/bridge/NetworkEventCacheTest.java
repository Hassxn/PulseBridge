package dev.pulseproxy.bridge;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class NetworkEventCacheTest {
 private NetworkEventCache.Event event(String server,String type,String stage,long at){return new NetworkEventCache.Event(server,type,stage,at);}
 @Test public void countsDownAndNeverShowsNegativeTime(){varCompat();}
 private void varCompat(){NetworkEventCache.Event e=event("hcf","SOTW","COUNTDOWN",2001);assertEquals("00:00:02",e.remaining(1000));assertEquals("00:00:00",e.remaining(3000));assertFalse(e.visible(2001,true));}
 @Test public void emptySnapshotClearsAndOldSnapshotsCannotResurrect(){NetworkEventCache c=new NetworkEventCache();c.accept("p1","lobby",1,Arrays.asList(event("hcf","SOTW","COUNTDOWN",10000)),0);assertEquals(1,c.active(1,15000,true,false).size());c.accept("p1","lobby",2,Collections.<NetworkEventCache.Event>emptyList(),2);assertFalse(c.accept("p1","lobby",1,Arrays.asList(event("hcf","SOTW","COUNTDOWN",10000)),3));assertTrue(c.active(3,15000,true,false).isEmpty());}
 @Test public void missingHeartbeatHidesTheTimers(){NetworkEventCache c=new NetworkEventCache();c.accept("p","lobby",1,Arrays.asList(event("hcf","SOTW","COUNTDOWN",999999)),100);assertEquals(1,c.active(15099,15000,true,false).size());assertTrue(c.active(15100,15000,true,false).isEmpty());}
 @Test public void localScopeAndWaitingBehavior(){NetworkEventCache c=new NetworkEventCache();c.accept("p","lobby",1,Arrays.asList(event("hcf","RESTART","WAITING",0),event("lobby","SOTW","COUNTDOWN",10000)),0);assertEquals(1,c.active(1,15000,true,true).size());assertEquals(2,c.active(1,15000,true,false).size());assertEquals(1,c.active(1,15000,false,false).size());}
 @Test(expected=IllegalArgumentException.class)public void rejectsInvalidBackendNames(){event("hcf\nadmin","SOTW","COUNTDOWN",10);}
 @Test public void liveAndReadyAreNotCountdowns(){assertFalse(event("hcf","SOTW","LIVE",10000).visible(1,true));assertFalse(event("hcf","RESTART","READY",10000).visible(1,true));}
}
