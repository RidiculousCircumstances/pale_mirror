package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Allocation policy fixtures declare candidates, not manufactured equipment or physical outcomes. */
class SecurityDispatchTest {
    private static final SubjectId HOME=new SubjectId("settlement:dispatch");
    private static final SecurityRules RULES=SecurityRules.initial();
    private static CombatTarget target(long id) { return new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(0,id),"pale_mirror:frontier_graybox"); }
    private static SecurityState.Contact contact(long id,int x,long tick,long revision) {
        return new SecurityState.Contact(HOME,target(id),new BlockPosition(x,63,0),tick,revision);
    }
    private static List<SecurityDispatch.Candidate> guards(int count) {
        var result=new ArrayList<SecurityDispatch.Candidate>();
        for(int i=0;i<count;i++)result.add(new SecurityDispatch.Candidate(new SubjectId("guard-duty:test-"+i),
                SurfaceAnchor.at(i*10,63,0),Optional.empty(),Optional.empty()));
        return List.copyOf(result);
    }
    private static SecurityDispatch.Reinforcement reinforcement(List<SecurityState.Contact> contacts) {
        return new SecurityDispatch.Reinforcement(SurfaceAnchor.at(0,63,0),contacts.stream().map(SecurityState.Contact::target).collect(java.util.stream.Collectors.toSet()));
    }
    private static Map<SubjectId,SecurityState.Response> select(long tick,List<SecurityState.Contact> contacts,
            List<SecurityDispatch.Candidate> guards,Map<SubjectId,SecurityState.Response> previous) {
        return SecurityDispatch.select(RULES,HOME,tick,contacts,guards,reinforcement(contacts),previous);
    }
    @Test void oneThreatGetsNearestBoundedCrewAndLeavesOtherGuardsPosted() {
        var guards=guards(6);var assigned=select(100,List.of(contact(1,1,100,1)),guards,Map.of());
        assertEquals(Set.of(guards.get(0).duty(),guards.get(1).duty()),assigned.keySet());
        assertEquals(4,guards.size()-assigned.size());
        assertEquals(1,select(100,List.of(contact(1,1,100,1)),guards(1),Map.of()).size());
    }
    @Test void nearbyPairCanRespondTogetherWhileDistantPostsAndExpiredSightingsKeepTheirReserve(){
        assertEquals(2,select(100,List.of(contact(1,1,100,1)),guards(2),Map.of()).size());
        var patrol=List.of(guards(6).getFirst(),guards(6).getLast());
        assertEquals(2,select(100,List.of(contact(1,1,100,1)),patrol,Map.of()).size(),"home patrol can reinforce without already standing beside the target");
        assertEquals(1,select(100,List.of(contact(1,100,100,1)),guards(2),Map.of()).size(),"a distant threat cannot empty both posts");
        assertTrue(select(170,List.of(contact(1,1,100,1)),guards(2),Map.of()).isEmpty(),"old information cannot summon a new local pair");
    }
    @Test void independentThreatsReceiveFirstRespondersBeforeReinforcements() {
        var guards=guards(6);var first=contact(1,0,100,1);var second=contact(2,50,100,2);
        var old=select(100,List.of(first),guards,Map.of());
        var assigned=select(101,List.of(first,second),guards,old);
        assertEquals(3,assigned.size());
        assertEquals(2,assigned.values().stream().filter(r -> r.contact().target().equals(first.target())).count());
        assertEquals(second.target(),assigned.get(guards.get(5).duty()).contact().target());
        assertEquals(3,guards.size()-assigned.size());
    }
    @Test void hysteresisRetainsSmallChangesButReassignsToMeaningfullyCloserResponders() {
        var guards=guards(6);var initial=select(100,List.of(contact(1,0,100,1)),guards,Map.of());
        assertEquals(initial,select(101,List.of(contact(1,2,101,2)),guards,initial));
        var moved=select(101,List.of(contact(1,50,101,2)),guards,initial);
        assertEquals(Set.of(guards.get(4).duty(),guards.get(5).duty()),moved.keySet());
        var reversed=new ArrayList<>(guards);Collections.reverse(reversed);
        assertEquals(moved,select(101,List.of(contact(1,50,101,2)),reversed,initial));
    }
    @Test void localDefendersAreNotCappedAndReleaseUnneededDistantReinforcements() {
        var candidates=guards(6);var c=contact(1,0,100,1);
        var old=select(100,List.of(c),candidates,Map.of());
        var local=candidates.stream().map(g -> new SecurityDispatch.Candidate(g.duty(),g.position(),Optional.empty(),
                Optional.of(new CombatEnvelope(c.target(),CombatEnvelope.Kind.AREA_DEFENCE,SurfaceAnchor.at(0,63,0),64)))).toList();
        assertEquals(6,select(100,List.of(c),local,old).size(),"local defence is never a distant reinforcement quota");
        var takeover=new ArrayList<>(candidates);
        for(int i=4;i<6;i++){var g=candidates.get(i);takeover.set(i,local.get(i));}
        assertEquals(Set.of(candidates.get(4).duty(),candidates.get(5).duty()),select(100,List.of(c),takeover,old).keySet());
        var lostOne=takeover.stream().filter(g -> !g.duty().equals(candidates.get(5).duty())).toList();
        var afterLoss=select(101,List.of(c),lostOne,old);
        assertEquals(2,afterLoss.size());assertTrue(afterLoss.containsKey(candidates.get(4).duty()));
        assertTrue(afterLoss.values().stream().anyMatch(r -> r.engagement().kind()==CombatEnvelope.Kind.REINFORCEMENT));
    }
    @Test void staleSightingsRetainOnlyExistingInvestigatorsAndExpireWithoutReplacements() {
        var candidates=guards(4);var contact=contact(1,0,100,1);
        var assigned=select(100,List.of(contact),candidates,Map.of());
        var investigating=candidates.stream().map(c -> new SecurityDispatch.Candidate(c.duty(),c.position(),
                assigned.containsKey(c.duty())?Optional.of(contact.target()):Optional.empty(),Optional.empty())).toList();
        assertEquals(assigned,select(170,List.of(contact),investigating,assigned));
        assertTrue(select(170,List.of(contact),candidates,assigned).isEmpty(),"completed return cannot restart an old search");
        assertTrue(select(501,List.of(contact),investigating,assigned).isEmpty());
        var available=investigating.stream().filter(c -> !assigned.containsKey(c.duty())).toList();
        assertTrue(select(170,List.of(contact),available,assigned).isEmpty(),"unseen threat cannot recruit a replacement");
    }
    @Test void lossOfOneContactCannotEraseAnotherAndObservationRevisionsDoNotRestart() {
        var a=contact(1,0,100,1);var b=contact(2,10,100,2);
        var state=SecurityState.empty().offence(HOME,a.target().entity(),90).offence(HOME,b.target().entity(),90).observed(a).observed(b);
        var candidates=guards(4);var allocated=select(100,List.of(a,b),candidates,Map.of());
        state=state.allocated(HOME,allocated,100,RULES);
        state=state.contactEnded(new SecurityState.ContactKey(HOME,a.target()));
        assertNull(state.contact(HOME,a.target()));assertEquals(b,state.contact(HOME,b.target()));
        assertEquals(1,state.responses().size());
        assertEquals(2,state.observationRevision());
        var finalState=state;
        assertThrows(IllegalArgumentException.class,()->finalState.observed(contact(1,1,101,1)));
        assertEquals(3,state.observed(contact(1,1,101,3)).observationRevision());
    }
    @Test void policyRejectsDuplicateCandidatesAndForeignContactDeclarations() {
        var one=guards(1).getFirst();
        assertThrows(IllegalArgumentException.class,()->select(100,List.of(),List.of(one,one),Map.of()));
        assertThrows(IllegalArgumentException.class,()->SecurityDispatch.select(RULES,new SubjectId("settlement:foreign"),100,
                List.of(contact(1,1,100,1)),guards(4),reinforcement(List.of(contact(1,1,100,1))),Map.of()));
    }
}
