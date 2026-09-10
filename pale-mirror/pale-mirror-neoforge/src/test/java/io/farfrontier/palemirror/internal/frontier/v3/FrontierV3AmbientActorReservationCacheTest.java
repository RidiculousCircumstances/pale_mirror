package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultAttacker;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.SettlementResidentIngressPlan;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjective;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicPlanState;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskRequirement;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientActorReservationCacheTest {
    @Test
    void productionAdmissionPolicySharesOneLossMaskedProviderViewThenRebuildsOnlyForTheReplacementState() {
            FrontierWorldState before = twoEligibleAssaults();
            List<io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate> initialCandidates =
                    FrontierSceneAdmission.reservationAdmission(before).settlementAssaultCandidates();
            assertEquals(2, initialCandidates.size(), "the production fixture must expose two independent active assault candidates");
            SettlementAssault firstAssault = before.strategicPlans().settlementAssaults().values().stream().sorted(java.util.Comparator.comparing(SettlementAssault::id)).findFirst().orElseThrow();
            SettlementAssault secondAssault = before.strategicPlans().settlementAssaults().values().stream().sorted(java.util.Comparator.comparing(SettlementAssault::id)).skip(1).findFirst().orElseThrow();
            var firstCandidate = initialCandidates.stream().filter(candidate -> candidate.assaultId().equals(firstAssault.id())).findFirst().orElseThrow();
            var secondCandidate = initialCandidates.stream().filter(candidate -> candidate.assaultId().equals(secondAssault.id())).findFirst().orElseThrow();
            SubjectId lostAttacker = firstAssault.attackerIds().stream().filter(firstCandidate.memberPositions()::containsKey).findFirst().orElseThrow();
            var lostSupport = firstCandidate.memberPositions().get(lostAttacker);
            GrayboxCell provider = FrontierGrayboxPlan.compile(before).cells().get(lostSupport);
            assertNotNull(provider, "the named attacker must stand on one declared physical-provider surface");
            assertTrue(provider.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || provider.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                    "the named attacker loss must bind its declared perimeter support rather than an inferred floor");

            Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, PhysicalDelta> losses = new LinkedHashMap<>(before.physicalDeltas());
            losses.put(lostSupport, new PhysicalDelta(lostSupport, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(provider.ownerId()), Optional.of(provider.semanticPart()), "test:admission-policy-named-attacker-loss"));
            FrontierWorldState afterLoss = before.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(losses));
            assertFalse(afterLoss.coldSettlementAssaultSceneCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(firstAssault.id())),
                    "loss of the named attacker's exact surface must not select an alternate/member/defender floor");
            assertTrue(afterLoss.coldSettlementAssaultSceneCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(secondAssault.id())),
                    "the intact independent assault must remain eligible after the other assault loses its support");

            AtomicReference<FrontierWorldState> current = new AtomicReference<>(before);
            List<FrontierSceneAdmission.ReservationAdmission> observed = new ArrayList<>();
            int[] compilations = {0};
            FrontierV3AmbientAdmissionPolicy.Session policy = FrontierV3AmbientAdmissionPolicy.begin(before, state -> {
                compilations[0]++;
                return FrontierSceneAdmission.reservationAdmission(state);
            });
            policy.scan(before.actorLocations().keySet(), current::get, (actorId, state, admission) -> {
                observed.add(admission);
                if (state == before) current.set(afterLoss);
                return false;
            });

            assertEquals(2, compilations[0], "one real reservation derivation and its one loss-masked provider compilation are permitted per immutable state segment");
            assertEquals(2, observed.stream().distinct().count(), "the scan must replace its one shared admission only after the canonical state object changes");
            FrontierSceneAdmission.ReservationAdmission replacement = observed.stream().filter(admission -> !admission.settlementAssaultCandidates().contains(firstCandidate)).findFirst().orElseThrow();
            assertFalse(replacement.settlementAssaultCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(firstAssault.id())),
                    "a later actor decision must use the changed state contents, not a stale reservation view");
            assertTrue(replacement.settlementAssaultCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(secondAssault.id())),
                    "a later actor decision must retain the other independently admissible assault");
    }

    private static FrontierWorldState twoEligibleAssaults() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:admission-policy"), 89L));
        Settlement firstSettlement = state.bootstrap().settlements().getFirst();
        Settlement secondSettlement = state.bootstrap().settlements().get(1);
        List<SubjectId> actors = state.bootstrap().hive().bioforms().stream().map(bioform -> bioform.id()).limit(6).toList();
        assertEquals(6, actors.size(), "fixture must retain two independent bounded attacker sets");
        SubjectId scout = state.bootstrap().hive().bioforms().stream().filter(bioform -> bioform.isScout()).findFirst().orElseThrow().id();
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(state.hiveColony().bioformLifecycles());
        List<SubjectId> deployed = new ArrayList<>(actors); deployed.add(scout);
        deployed.forEach(id -> lifecycles.computeIfPresent(id, (ignored, lifecycle) -> lifecycle.phase().occupiesCocoon() ? lifecycle.waking().active() : lifecycle));
        state = state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(state.hiveColony().withBioformLifecycles(lifecycles)));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:admission-policy"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:admission-policy"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        HiveSettlementKnowledge.Sighting firstSighting = new HiveSettlementKnowledge.Sighting(firstSettlement.id(), scout, firstSettlement.anchor(), 100L);
        HiveSettlementKnowledge.Sighting secondSighting = new HiveSettlementKnowledge.Sighting(secondSettlement.id(), scout, secondSettlement.anchor(), 100L);
        StrategicPlanState plans = StrategicPlanState.empty().withHiveSettlementKnowledge(new HiveSettlementKnowledge(Map.of(
                firstSettlement.id(), firstSighting, secondSettlement.id(), secondSighting))).addObjective(objective).addTask(task);
        SettlementAssault first = assault(new SubjectId("assault:admission-policy-first"), task, firstSighting, actors.subList(0, 3), firstSettlement);
        SettlementAssault second = assault(new SubjectId("assault:admission-policy-second"), task, secondSighting, actors.subList(3, 6), secondSettlement);
        Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells();
        Map<SubjectId, ActorLocation> locations = new LinkedHashMap<>(state.actorLocations());
        place(locations, cells, state, firstSettlement, first);
        place(locations, cells, state, secondSettlement, second);
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations).strategicPlans(
                plans.startSettlementAssault(first).startSettlementAssault(second)));
    }

    private static SettlementAssault assault(SubjectId id, StrategicTask task, HiveSettlementKnowledge.Sighting sighting,
                                             List<SubjectId> attackers, Settlement settlement) {
        List<SubjectId> defenders = settlement.residents().stream().map(resident -> resident.id()).limit(3).toList();
        return new SettlementAssault(id, task.id(), task.ownerId(), sighting, attackers.getFirst(),
                attackers.stream().map(actor -> new SettlementAssaultAttacker(actor, List.of(settlement.anchor()), 0)).toList(), defenders,
                SettlementAssaultStatus.COLD_COMBAT, 0, Optional.empty());
    }

    private static void place(Map<SubjectId, ActorLocation> locations, Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GrayboxCell> cells,
                              FrontierWorldState state, Settlement settlement, SettlementAssault assault) {
        List<SubjectId> members = new ArrayList<>(assault.attackerIds());
        members.addAll(assault.defenderIds());
        List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> floors = cells.entrySet().stream()
                .filter(entry -> entry.getValue().semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || entry.getValue().semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE)
                .map(Map.Entry::getKey).filter(position -> SettlementResidentIngressPlan.compile(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                        state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).perimeterSurfaces().contains(new SurfaceAnchor(position)))
                .filter(position -> !cells.containsKey(position.offset(0, 1, 0)) && !cells.containsKey(position.offset(0, 2, 0))).limit(members.size()).toList();
        assertEquals(members.size(), floors.size(), "fixture must retain declared perimeter floors for every named assault member");
        for (int index = 0; index < members.size(); index++) {
            SubjectId actor = members.get(index);
            locations.put(actor, new ActorLocation(BodyPosition.above(new SurfaceAnchor(floors.get(index))), locations.get(actor).condition()));
        }
    }
}
