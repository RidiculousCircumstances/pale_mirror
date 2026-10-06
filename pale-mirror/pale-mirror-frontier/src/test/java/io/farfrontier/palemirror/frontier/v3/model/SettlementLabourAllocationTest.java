package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SettlementLabourAllocationTest {
    @Test void missionCannotTakeEveryAuthorizedLocalWorkerRegardlessOfProfession() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var policy = SettlementWorkPolicy.permissions(state, home);
        var bakers = policy.workers(ResidentWorkKind.BAKING).stream().sorted().toList();
        assertTrue(SettlementLabourAllocation.canCommitToMission(state, home, ResidentWorkKind.LOGISTICS, List.of(bakers.getFirst())));
        assertFalse(SettlementLabourAllocation.canCommitToMission(state, home, ResidentWorkKind.LOGISTICS, bakers));
        assertThrows(IllegalArgumentException.class, () -> SettlementLabourAllocation.requireMissionCommitment(
                state, home, ResidentWorkKind.LOGISTICS, bakers));
        assertFalse(SettlementLabourAllocation.canCommitToMission(state, home, ResidentWorkKind.LOGISTICS, List.of(bakers.getFirst(), bakers.getFirst())));
        assertFalse(SettlementLabourAllocation.canCommitToMission(state, home, ResidentWorkKind.BAKING,
                policy.workers(ResidentWorkKind.AGRICULTURE).stream().sorted().limit(1).toList()));
    }
    @Test void explicitPriorityPrecedesSkillAndSurvivesSnapshot() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var candidates = SettlementWorkforce.candidates(state, home, ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS);
        var preferred = candidates.getLast().id(); var skilled = candidates.getFirst().id();
        var old = SettlementWorkPolicy.permissions(state, home);
        var priorities = new java.util.EnumMap<ResidentWorkKind, Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Integer>>(ResidentWorkKind.class);
        priorities.putAll(old.priorities()); priorities.put(ResidentWorkKind.LOGISTICS, Map.of(preferred, 1, skilled, 2));
        var changed = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home,
                new ResidentWorkPermissions(priorities, old.minimumLocalStaff())));
        assertEquals(preferred, SettlementWorkforce.candidates(changed, home, ResidentWorkKind.LOGISTICS,
                HumanCapability.LOGISTICS).getFirst().id());
        var decoded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed));
        assertEquals(changed, decoded);
        assertEquals(1, SettlementWorkPolicy.permissions(decoded, home).priority(ResidentWorkKind.LOGISTICS, preferred));
        assertEquals(1, SettlementWorkPolicy.permissions(decoded, home).localReserve(ResidentWorkKind.BAKING));
    }
    @Test void malformedPolicyCannotCreateAnUnboundedOrUnfulfillableReserve() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> new ResidentWorkPermissions(
                Map.of(ResidentWorkKind.LOGISTICS, Map.of(actor, 0)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResidentWorkPermissions(
                Map.of(ResidentWorkKind.LOGISTICS, Map.of(actor, 1)), Map.of(ResidentWorkKind.LOGISTICS, 2)));
    }
    @Test void unavailableHigherPriorityWorkDoesNotPreventAnExecutableAlternative() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var actor = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.BAKING)
                .stream().sorted().findFirst().orElseThrow();
        var policy = new ResidentWorkPermissions(Map.of(ResidentWorkKind.BAKING, Map.of(actor, 2),
                ResidentWorkKind.LOGISTICS, Map.of(actor, 1)), Map.of());
        var changed = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, policy));
        var ready = new java.util.concurrent.atomic.AtomicBoolean(false);
        var selector = new ResidentWorkSelection(List.of(new ResidentWorkAvailabilityPort() {
            public ResidentWorkKind kind() { return ResidentWorkKind.LOGISTICS; }
            public HumanCapability capability() { return HumanCapability.LOGISTICS; }
            public boolean available(FrontierWorldState current, ResidentProfile resident, long tick) { return ready.get(); }
        }));
        assertEquals(List.of(actor), selector.eligible(changed, home, ResidentWorkKind.BAKING,
                HumanCapability.INDUSTRY, 200).stream().map(ResidentProfile::id).toList());
        ready.set(true);
        assertTrue(selector.eligible(changed, home, ResidentWorkKind.BAKING, HumanCapability.INDUSTRY, 200).isEmpty());
        assertTrue(HumanAssignmentProjection.compile(changed).idle(actor), "a preference probe never acquires an assignment");
    }
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:labour-policy"), 71));
    }
}
