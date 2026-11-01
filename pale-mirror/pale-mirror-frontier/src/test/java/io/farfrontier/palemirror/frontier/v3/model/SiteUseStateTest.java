package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.land.SiteUseState;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SiteUseStateTest {
    @Test void knowledgeDoesNotGrantUseAndExactRevisionPreventsCompetingClaims(){
        var one=new SubjectId("settlement:1");var two=new SubjectId("settlement:2");var id=new SubjectId("area:coal");
        var position=new BlockPosition(10,64,10);
        var area=new SiteUseState.Area(id,SiteUseState.Kind.EXTRACTION,new BlockVolume(position,position.offset(4,4,4)),Set.of(one,two));
        var operation=new SiteUseState.Operation(area.kind(),new SubjectId("operation:mine"));
        var free=new SiteUseState(0,Map.of(id,area),Map.of());assertFalse(free.authorizes(operation,one));
        var held=free.acquire(id,area.kind(),one,0);assertFalse(held.authorizes(operation,one),"a claim is not a declared operating relationship");assertFalse(held.available(id,area.kind(),two));
        assertThrows(IllegalArgumentException.class,()->held.acquire(id,area.kind(),two,0));
        assertThrows(IllegalArgumentException.class,()->free.acquire(id,SiteUseState.Kind.CULTIVATION,one,0));
        var released=held.release(held.grants().get(id),held.revision());
        assertEquals(area,released.areas().get(id));assertTrue(released.acquire(id,area.kind(),two,released.revision()).bind(operation,
                released.acquire(id,area.kind(),two,released.revision()).grants().get(id)).permits(operation,two,position));
        var bound=held.bind(operation,held.grants().get(id));
        assertTrue(bound.permits(operation,one,position));
        assertThrows(IllegalArgumentException.class,()->bound.release(bound.grants().get(id),bound.revision()));
        var forged=new SiteUseState.Operation(SiteUseState.Kind.FORESTRY,operation.id());
        assertFalse(bound.permits(forged,one,position));
        assertThrows(IllegalArgumentException.class,()->bound.bind(forged,bound.grants().get(id)));
    }    @Test void aWorldCannotHydrateAnOperatingFieldWithoutItsNominalRight() {
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:missing-land-right"),41,FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var rights=state.siteUses();
        var missing=new SiteUseState(rights.revision(),rights.areas(),rights.grants());
        assertThrows(IllegalArgumentException.class,()->state.withChanges(FrontierWorldStateUpdate.begin().siteUses(missing)));
        var first=state.resourceSiteDescriptors().values().iterator().next();
        var forged=new SiteUseState.Operation(SiteUseState.Kind.EXTRACTION,first.id());
        assertFalse(rights.authorizes(forged,first.settlementId()));
    }

}
