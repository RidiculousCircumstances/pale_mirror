package io.farfrontier.palemirror.frontier.v3.model;

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
                SurfaceAnchor.at(i*10,63,0),Optional.empty()));
        return List.copyOf(result);
    }
    @Test void oneThreatGetsNearestBoundedCrewAndLeavesOtherGuardsPosted() {
        var guards=guards(6);var assigned=SecurityDispatch.select(RULES,HOME,100,List.of(contact(1,1,100,1)),guards,Map.of());
        assertEquals(Set.of(guards.get(0).duty(),guards.get(1).duty()),assigned.keySet());
        assertEquals(4,guards.size()-assigned.size());
        assertEquals(1,SecurityDispatch.select(RULES,HOME,100,List.of(contact(1,1,100,1)),guards(1),Map.of()).size());
    }
    @Test void independentThreatsReceiveFirstRespondersBeforeReinforcements() {
        var guards=guards(6);var first=contact(1,0,100,1);var second=contact(2,50,100,2);
        var old=SecurityDispatch.select(RULES,HOME,100,List.of(first),guards,Map.of());
        var assigned=SecurityDispatch.select(RULES,HOME,101,List.of(first,second),guards,old);
        assertEquals(3,assigned.size());
        assertEquals(2,assigned.values().stream().filter(r -> r.contact().target().equals(first.target())).count());
        assertEquals(second.target(),assigned.get(guards.get(5).duty()).contact().target());
        assertEquals(3,guards.size()-assigned.size());
    }
    @Test void aMovingTargetDoesNotContinuouslyReplaceItsRetainedResponders() {
        var guards=guards(6);var initial=SecurityDispatch.select(RULES,HOME,100,List.of(contact(1,0,100,1)),guards,Map.of());
        var moved=SecurityDispatch.select(RULES,HOME,101,List.of(contact(1,50,101,2)),guards,initial);
        assertEquals(initial,moved);
        var reversed=new ArrayList<>(guards);Collections.reverse(reversed);
        assertEquals(initial,SecurityDispatch.select(RULES,HOME,101,List.of(contact(1,50,101,2)),reversed,initial));
    }
    @Test void staleSightingsRetainOnlyExistingInvestigatorsAndExpireWithoutReplacements() {
        var candidates=guards(4);var contact=contact(1,0,100,1);
        var assigned=SecurityDispatch.select(RULES,HOME,100,List.of(contact),candidates,Map.of());
        var investigating=candidates.stream().map(c -> new SecurityDispatch.Candidate(c.duty(),c.position(),
                assigned.containsKey(c.duty())?Optional.of(contact.target()):Optional.empty())).toList();
        assertEquals(assigned,SecurityDispatch.select(RULES,HOME,170,List.of(contact),investigating,assigned));
        assertTrue(SecurityDispatch.select(RULES,HOME,170,List.of(contact),candidates,assigned).isEmpty(),"completed return cannot restart an old search");
        assertTrue(SecurityDispatch.select(RULES,HOME,501,List.of(contact),investigating,assigned).isEmpty());
        var available=investigating.stream().filter(c -> !assigned.containsKey(c.duty())).toList();
        assertTrue(SecurityDispatch.select(RULES,HOME,170,List.of(contact),available,assigned).isEmpty(),"unseen threat cannot recruit a replacement");
    }
    @Test void lossOfOneContactCannotEraseAnotherAndObservationRevisionsDoNotRestart() {
        var a=contact(1,0,100,1);var b=contact(2,10,100,2);
        var state=SecurityState.empty().offence(HOME,a.target().entity(),90).offence(HOME,b.target().entity(),90).observed(a).observed(b);
        var candidates=guards(4);var allocated=SecurityDispatch.select(RULES,HOME,100,List.of(a,b),candidates,Map.of());
        state=state.allocated(HOME,allocated,100,RULES.investigationTicks());
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
        assertThrows(IllegalArgumentException.class,()->SecurityDispatch.select(RULES,HOME,100,List.of(),List.of(one,one),Map.of()));
        assertThrows(IllegalArgumentException.class,()->SecurityDispatch.select(RULES,new SubjectId("settlement:foreign"),100,
                List.of(contact(1,1,100,1)),guards(4),Map.of()));
    }
}
