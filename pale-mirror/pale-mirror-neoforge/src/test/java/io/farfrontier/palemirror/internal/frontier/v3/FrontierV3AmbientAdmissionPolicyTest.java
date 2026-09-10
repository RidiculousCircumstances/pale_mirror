package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateSupport;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientAdmissionPolicyTest {
    @Test
    void productionAdmissionPolicyOwnsSelectionAndOneProviderViewPerStateSegment() {
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

            List<SubjectId> scanActors = firstAssault.attackerIds().stream().sorted().limit(3).toList();
            SubjectId firstActor = scanActors.getFirst();
            SubjectId handoffActor = scanActors.get(1);
            SubjectId laterActor = scanActors.get(2);
            before = withPreparedLease(before, handoffActor);
            afterLoss = withPreparedLease(afterLoss, handoffActor);
            FrontierWorldState originalState = before;
            FrontierWorldState replacementState = afterLoss;
            AtomicInteger derivations = new AtomicInteger();
            AtomicInteger providers = new AtomicInteger();
            AtomicInteger beforeDerivations = new AtomicInteger();
            AtomicInteger replacementDerivations = new AtomicInteger();
            AtomicInteger beforeProviders = new AtomicInteger();
            AtomicInteger replacementProviders = new AtomicInteger();
            FrontierSceneAdmission.ProviderCompiler realProvider = FrontierSceneAdmission.providerCompiler();
            FrontierV3AmbientAdmissionPolicy.AdmissionDeriver countedDeriver = state -> {
                derivations.incrementAndGet();
                if (state == originalState) beforeDerivations.incrementAndGet();
                if (state == replacementState) replacementDerivations.incrementAndGet();
                return FrontierSceneAdmission.reservationAdmission(state, providerState -> {
                    providers.incrementAndGet();
                    if (providerState == originalState) beforeProviders.incrementAndGet();
                    if (providerState == replacementState) replacementProviders.incrementAndGet();
                    return realProvider.compile(providerState);
                });
            };
            FrontierV3AmbientAdmissionPolicy.Session policy = FrontierV3AmbientAdmissionPolicy.begin(originalState, countedDeriver);
            List<FrontierV3AmbientAdmissionPolicy.Selection> selected = new ArrayList<>();
            List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = policy.scan(scanActors, () -> originalState, selection -> {
                selected.add(selection);
                assertEquals(handoffActor, selection.actorId(), "only the policy-selected prepared lease may execute the test effect");
                assertEquals(FrontierV3AmbientAdmissionPolicy.Effect.ABANDON_PREPARED, selection.effect());
                return Optional.of(replacementState);
            });

            assertEquals(3, decisions.size());
            assertEquals(originalState, decisions.get(0).state());
            assertEquals(originalState, decisions.get(1).state());
            assertEquals(replacementState, decisions.get(2).state());
            assertEquals(firstActor, decisions.get(0).actorId());
            assertTrue(decisions.get(0).reserved());
            assertTrue(decisions.get(0).selectedEffect().isEmpty(), "the policy rejects a reserved actor with no active lease before the selected hand-off");
            assertEquals(handoffActor, decisions.get(1).actorId());
            assertEquals(FrontierV3AmbientAdmissionPolicy.Effect.ABANDON_PREPARED, decisions.get(1).selectedEffect().orElseThrow());
            assertTrue(decisions.get(1).applied());
            assertEquals(laterActor, decisions.get(2).actorId());
            assertEquals(1, selected.size());
            assertEquals(2, derivations.get(), "one reservation derivation is permitted for each immutable state segment");
            assertEquals(2, providers.get(), "the real provider compiler runs once, independently of reservation derivation, for each segment");
            assertEquals(1, beforeDerivations.get());
            assertEquals(1, replacementDerivations.get());
            assertEquals(1, beforeProviders.get());
            assertEquals(1, replacementProviders.get());
            FrontierSceneAdmission.ReservationAdmission replacement = decisions.get(2).admission();
            assertFalse(replacement.settlementAssaultCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(firstAssault.id())),
                    "a later actor decision must use the changed state contents, not a stale reservation view");
            assertTrue(replacement.settlementAssaultCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(secondAssault.id())),
                    "a later actor decision must retain the other independently admissible assault");

            assertDerivationPerActorIsDetected(before, scanActors, countedDeriver);
            assertProviderPerAssaultIsDetected(before, realProvider);
            assertStaleReplacementIsDetected(before, afterLoss, scanActors, countedDeriver);
            assertSubstituteFloorIsDetected(before, afterLoss, firstAssault, lostAttacker, scanActors, countedDeriver);
    }

    private static void assertDerivationPerActorIsDetected(FrontierWorldState state, List<SubjectId> actors,
                                                           FrontierV3AmbientAdmissionPolicy.AdmissionDeriver deriver) {
        AtomicInteger derivations = new AtomicInteger();
        FrontierV3AmbientAdmissionPolicy.AdmissionDeriver counted = value -> {
            derivations.incrementAndGet();
            return deriver.derive(value);
        };
        for (SubjectId actor : actors) {
            FrontierV3AmbientAdmissionPolicy.begin(state, counted).decide(actor, state, selection -> Optional.empty());
        }
        assertThrows(AssertionError.class, () -> assertEquals(1, derivations.get(),
                "fault control: deriving inside the actor loop must violate the state-segment count"));
    }

    private static void assertProviderPerAssaultIsDetected(FrontierWorldState state, FrontierSceneAdmission.ProviderCompiler realProvider) {
        AtomicInteger providers = new AtomicInteger();
        FrontierSceneAdmission.reservationAdmission(state, providerState -> {
            int activeAssaults = (int) providerState.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT).count();
            FrontierSettlementAssaultBattlefield.ProviderView view = null;
            for (int index = 0; index < activeAssaults; index++) {
                providers.incrementAndGet();
                view = realProvider.compile(providerState);
            }
            return view;
        });
        assertThrows(AssertionError.class, () -> assertEquals(1, providers.get(),
                "fault control: compiling the real provider once per assault must violate the segment count"));
    }

    private static void assertStaleReplacementIsDetected(FrontierWorldState before, FrontierWorldState afterLoss, List<SubjectId> actors,
                                                         FrontierV3AmbientAdmissionPolicy.AdmissionDeriver realDeriver) {
        FrontierV3AmbientAdmissionPolicy.Session stalePolicy = FrontierV3AmbientAdmissionPolicy.begin(before,
                ignored -> realDeriver.derive(before));
        List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = stalePolicy.scan(actors, () -> before, selection -> Optional.of(afterLoss));
        assertThrows(AssertionError.class, () -> assertFalse(decisions.get(2).admission().settlementAssaultCandidates().stream()
                        .anyMatch(candidate -> candidate.assaultId().value().contains("first")),
                "fault control: stale replacement contents must retain the lost assault"));
    }

    private static void assertSubstituteFloorIsDetected(FrontierWorldState before, FrontierWorldState afterLoss, SettlementAssault firstAssault,
                                                        SubjectId lostAttacker, List<SubjectId> actors,
                                                        FrontierV3AmbientAdmissionPolicy.AdmissionDeriver deriver) {
        FrontierWorldState substituted = withSubstituteFloor(afterLoss, firstAssault, lostAttacker);
        FrontierV3AmbientAdmissionPolicy.Session policy = FrontierV3AmbientAdmissionPolicy.begin(before, deriver);
        List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = policy.scan(actors, () -> before, selection -> Optional.of(substituted));
        assertThrows(AssertionError.class, () -> assertFalse(decisions.get(2).admission().settlementAssaultCandidates().stream()
                        .anyMatch(candidate -> candidate.assaultId().equals(firstAssault.id())),
                "fault control: a replacement that substitutes another attacker floor must violate the exact-support loss oracle"));
    }

    private static FrontierWorldState withPreparedLease(FrontierWorldState state, SubjectId actorId) {
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases());
        BodyPosition body = state.actorLocations().get(actorId).body();
        leases.put(actorId, new AmbientActorLease(actorId, body, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO,
                1L, AmbientLeaseStatus.PREPARED, AmbientGoalKind.GUARD, body));
        return state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases));
    }

    private static FrontierWorldState withSubstituteFloor(FrontierWorldState state, SettlementAssault assault, SubjectId attacker) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), assault.settlementId());
        Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells();
        java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> occupied = java.util.stream.Stream.concat(assault.attackerIds().stream(), assault.defenderIds().stream())
                .filter(actor -> !actor.equals(attacker)).map(actor -> state.actorLocations().get(actor).supportingSurface().support())
                .collect(java.util.stream.Collectors.toSet());
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition substitute = SettlementResidentIngressPlan.compile(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                        state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).perimeterSurfaces().stream().map(SurfaceAnchor::support)
                .filter(position -> !occupied.contains(position)).filter(position -> {
                    GrayboxCell cell = cells.get(position);
                    return cell != null && (cell.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || cell.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE)
                            && !cells.containsKey(position.offset(0, 1, 0)) && !cells.containsKey(position.offset(0, 2, 0));
                }).findFirst().orElseThrow();
        Map<SubjectId, ActorLocation> locations = new LinkedHashMap<>(state.actorLocations());
        ActorLocation original = locations.get(attacker);
        locations.put(attacker, new ActorLocation(BodyPosition.above(new SurfaceAnchor(substitute)), original.condition()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
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
