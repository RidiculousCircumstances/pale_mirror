package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StrategicPlanGraphValidationTest {
    private static final SubjectId OWNER = new SubjectId("settlement:graph-owner");

    @Test void retainedGraphsRemainImmutableAcrossUnrelatedPlanRevisions() {
        var objective = objective("objective:graph", Optional.empty());
        var draft = new LinkedHashMap<>(Map.of(objective.id(), objective));
        var original = new StrategicPlanState(draft, Map.of(), Map.of());
        draft.clear();
        var next = original.withHiveDoctrine(HiveDoctrineState.initial());
        assertEquals(Map.of(objective.id(), objective), next.objectives());
        assertThrows(UnsupportedOperationException.class, () -> next.objectives().clear());
        var reconstructed = new StrategicPlanState(original.objectives(), original.tasks(), original.routePatrols());
        assertSame(original.objectives(), reconstructed.objectives());
        assertSame(original.tasks(), reconstructed.tasks());
        assertEquals(original, ImmutableInvariantCheck.full(() ->
                new StrategicPlanState(original.objectives(), original.tasks(), original.routePatrols())));
    }

    @Test void activeMembershipRetainsOwnerLaneAndExactFieldAndRejectsChangedDuplicates() {
        var first = objective("objective:first", Optional.of(new SubjectId("site:one")));
        var second = objective("objective:second", Optional.of(new SubjectId("site:two")));
        var state = new StrategicPlanState(Map.of(first.id(), first, second.id(), second), Map.of(), Map.of());
        assertTrue(state.hasActiveObjective(OWNER, first.lane()));
        assertTrue(state.hasActiveObjective(OWNER, first.lane(), first.resourceSiteTarget()));
        assertFalse(state.hasActiveObjective(OWNER, first.lane(), Optional.of(new SubjectId("site:missing"))));
        var changed = new LinkedHashMap<>(state.objectives());
        changed.put(first.id(), first.withStatus(StrategicObjectiveStatus.COMPLETED));
        var terminal = new StrategicPlanState(changed, state.tasks(), Map.of());
        assertFalse(terminal.hasActiveObjective(OWNER, first.lane(), first.resourceSiteTarget()));
        assertTrue(terminal.hasActiveObjective(OWNER, first.lane()));
        var duplicate = objective("objective:duplicate", second.resourceSiteTarget());
        changed.put(duplicate.id(), duplicate);
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(changed, state.tasks(), Map.of()));
        assertTrue(state.hasActiveObjective(OWNER, first.lane(), first.resourceSiteTarget()));
    }

    @Test void acceptedGraphCannotAuthorizeChangedDependenciesAuthorityOrTerminalParent() {
        var objective = objective("objective:graph", Optional.empty());
        var a = task("task:a", objective, List.of());
        var b = task("task:b", objective, List.of(a.id()));
        var state = new StrategicPlanState(Map.of(objective.id(), objective), Map.of(a.id(), a, b.id(), b), Map.of());
        var cycle = task("task:a", objective, List.of(b.id()));
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(state.objectives(),
                Map.of(a.id(), cycle, b.id(), b), Map.of()));
        var missing = task("task:a", objective, List.of(new SubjectId("task:missing")));
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(state.objectives(), Map.of(a.id(), missing), Map.of()));
        var stale = new StrategicTask(a.id(), a.objectiveId(), a.ownerId(), a.kind(), a.infectionTarget(),
                a.resourceSiteTarget(), a.requirements(), a.dependencies(), a.status(), a.authorityId(), 1L);
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(state.objectives(), Map.of(a.id(), stale), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(
                Map.of(objective.id(), objective.withStatus(StrategicObjectiveStatus.COMPLETED)), state.tasks(), Map.of()));
        assertEquals(state, ImmutableInvariantCheck.full(() ->
                new StrategicPlanState(state.objectives(), state.tasks(), Map.of())));
    }

    @Test void reusedActiveIndexStillRejectsChangedAuthorityCommitmentsAndForeignOwner() {
        var world = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:graph-authority"), 125L));
        var initial = world.strategicPlans();
        var home = world.bootstrap().settlements().getFirst().id();
        var objective = new StrategicObjective(new SubjectId("objective:local"), home,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var valid = initial.addObjective(objective);
        valid.validate(world.bootstrap(), world.routeTopology(), world.humanPopulation(), world.resourceSites());
        var missingCommitment = new StrategicPlanState(valid.objectives(), valid.tasks(), valid.routePatrols(),
                valid.infectionKnowledge(), valid.hiveTerritoryKnowledge(), valid.hiveSettlementKnowledge(),
                valid.hiveDoctrine(), valid.settlementAssaults(), initial.decisionAuthorities());
        assertThrows(IllegalArgumentException.class, () -> missingCommitment.validate(
                world.bootstrap(), world.routeTopology(), world.humanPopulation(), world.resourceSites()));
        var foreign = objective("objective:foreign", Optional.empty());
        var invalid = new StrategicPlanState(Map.of(foreign.id(), foreign), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> invalid.validate(
                world.bootstrap(), world.routeTopology(), world.humanPopulation(), world.resourceSites()));
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            valid.validate(world.bootstrap(), world.routeTopology(), world.humanPopulation(), world.resourceSites());
            return null;
        }));
    }

    private static StrategicObjective objective(String id, Optional<SubjectId> field) {
        return new StrategicObjective(new SubjectId(id), OWNER, field.isPresent()
                ? StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE : StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD,
                Optional.empty(), field, 1, StrategicObjectiveStatus.ACTIVE);
    }
    private static StrategicTask task(String id, StrategicObjective objective, List<SubjectId> dependencies) {
        return new StrategicTask(new SubjectId(id), objective.id(), objective.ownerId(), StrategicTaskKind.PRODUCE_AT_STATION,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_PRODUCTION_FACILITY,
                StrategicTaskRequirement.AVAILABLE_PRODUCTION_INPUT), dependencies, StrategicTaskStatus.PENDING);
    }
}
