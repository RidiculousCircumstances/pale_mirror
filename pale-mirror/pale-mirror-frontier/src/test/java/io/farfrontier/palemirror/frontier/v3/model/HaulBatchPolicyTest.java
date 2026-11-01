package io.farfrontier.palemirror.frontier.v3.model;
import org.junit.jupiter.api.Test;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HaulBatchPolicyTest {
    @Test void distanceEncouragesLoadsButDeadlinePressureAndRealNeedReleasePartials(){
        var wait=HaulBatchPolicy.decide(new HaulBatchPolicy.Request(1,64,800,100,200,40,false,false));
        assertFalse(wait.dispatch());assertTrue(wait.usefulLoad()>16);
        assertEquals(1,HaulBatchPolicy.decide(new HaulBatchPolicy.Request(1,64,800,100,1700,40,false,false)).quantity());
        assertEquals(1,HaulBatchPolicy.decide(new HaulBatchPolicy.Request(1,64,800,100,200,40,true,false)).quantity());
        assertEquals(1,HaulBatchPolicy.decide(new HaulBatchPolicy.Request(1,64,800,100,200,40,false,true)).quantity());
        assertEquals(64,HaulBatchPolicy.decide(new HaulBatchPolicy.Request(200,64,800,100,200,40,false,false)).quantity());
    }
    @Test void waitingAgeIsSourceScopedPersistsAcrossQuantityChangesAndRetiresWithTheSource(){
        var home=new SubjectId("settlement:1");var key=new HaulAccumulation.Key(home,new SubjectId("custody:test"),"minecraft:coal");
        var seen=HaulAccumulation.empty().reviewed(home,Set.of(key),100).reviewed(home,Set.of(key),500);
        assertEquals(100L,seen.firstAvailable().get(key));assertTrue(seen.reviewed(home,Set.of(),501).firstAvailable().isEmpty());
        assertThrows(IllegalArgumentException.class,()->seen.reviewed(new SubjectId("settlement:2"),Set.of(key),502));
    }
}
