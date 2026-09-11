package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
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
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
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
import io.farfrontier.palemirror.frontier.v3.model.StructureCondition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientAdmissionPolicyTest {
    @Test
    void projectionOwnedSnapshotIsTheOnlyBoundedProviderForProductionAssaultAdmission() {
        FrontierWorldState state = twoEligibleAssaults();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            assertTrue(FrontierV3AmbientActorExecutor.admissionPolicy(runtime, state).admissionFor(state)
                            .settlementAssaultCandidates().isEmpty(),
                    "without an earlier compatible projection, assault admission must defer instead of compiling graybox");

            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierSceneAdmission.ReservationAdmission initial = FrontierV3AmbientActorExecutor.admissionPolicy(runtime, state).admissionFor(state);
            assertEquals(2, initial.settlementAssaultCandidates().size(),
                    "the actor path must receive the snapshot produced by the preceding projector");

            FrontierSettlementAssaultBattlefield.Provider snapshot = FrontierV3GrayboxExecutor.admissionProvider(runtime, state).orElseThrow();
            AtomicInteger queries = new AtomicInteger();
            FrontierSceneAdmission.ReservationAdmission counted = FrontierSceneAdmission.reservationAdmission(state, ignored -> Optional.of(position -> {
                queries.incrementAndGet();
                return snapshot.cellAt(position);
            }));
            int members = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                    .mapToInt(assault -> assault.attackerIds().size() + assault.defenderIds().size()).sum();
            assertEquals(members * 3, queries.get(),
                    "admission may query only each named support and its two headroom cells through the projection provider");
            assertEquals(initial.settlementAssaultCandidates(), counted.settlementAssaultCandidates(),
                    "the counted production provider must preserve exact candidate identities and floors");

            SubjectId leasedCandidate = firstCandidateActor(initial);
            FrontierWorldState leaseOnly = withPreparedLease(state, leasedCandidate);
            assertEquals(initial.settlementAssaultCandidates(), FrontierV3AmbientActorExecutor.admissionPolicy(runtime, leaseOnly)
                    .admissionFor(leaseOnly).settlementAssaultCandidates(),
                    "a lease-only replacement must reuse the compatible structural snapshot");

            SubjectId unrelated = state.actorLocations().keySet().stream()
                    .filter(actor -> !state.hiveColony().bioformLifecycles().containsKey(actor))
                    .filter(actor -> state.strategicPlans().settlementAssaults().values().stream()
                            .noneMatch(assault -> assault.attackerIds().contains(actor) || assault.defenderIds().contains(actor))).findFirst().orElseThrow();
            FrontierWorldState unrelatedReplacement = withPreparedLease(state, unrelated);
            assertEquals(initial.settlementAssaultCandidates(), FrontierV3AmbientActorExecutor.admissionPolicy(runtime, unrelatedReplacement)
                    .admissionFor(unrelatedReplacement).settlementAssaultCandidates(),
                    "an unrelated canonical replacement must not invalidate compatible structural provider truth");

            var firstCandidate = initial.settlementAssaultCandidates().getFirst();
            var lostSupport = firstCandidate.memberPositions().values().iterator().next();
            GrayboxCell lostCell = snapshot.cellAt(lostSupport).orElseThrow();
            FrontierWorldState withLoss = withExactSupportLoss(state, lostSupport, lostCell);
            assertFalse(FrontierV3AmbientActorExecutor.admissionPolicy(runtime, withLoss).admissionFor(withLoss)
                            .settlementAssaultCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(firstCandidate.assaultId())),
                    "one current physical loss must be masked by its exact provider position lookup");

            Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> requested = initial.settlementAssaultCandidates().stream()
                    .flatMap(candidate -> candidate.memberPositions().values().stream())
                    .flatMap(position -> java.util.stream.Stream.of(position, position.offset(0, 1, 0), position.offset(0, 2, 0)))
                    .collect(java.util.stream.Collectors.toSet());
            var unrelatedCell = FrontierGrayboxPlan.compile(state).cells().entrySet().stream()
                    .filter(entry -> !requested.contains(entry.getKey())).findFirst().orElseThrow();
            FrontierWorldState unrelatedLoss = withExactSupportLoss(state, unrelatedCell.getKey(), unrelatedCell.getValue());
            AtomicInteger unrelatedQueries = new AtomicInteger();
            FrontierSceneAdmission.ReservationAdmission afterUnrelatedLoss = FrontierSceneAdmission.reservationAdmission(unrelatedLoss,
                    ignored -> FrontierV3GrayboxExecutor.admissionProvider(runtime, unrelatedLoss).map(provider -> position -> {
                        unrelatedQueries.incrementAndGet(); return provider.cellAt(position);
                    }));
            assertEquals(initial.settlementAssaultCandidates(), afterUnrelatedLoss.settlementAssaultCandidates(),
                    "an unrelated physical loss must not alter exact assault admission");
            assertEquals(members * 3, unrelatedQueries.get(),
                    "unrelated physical geometry cannot add provider work beyond named support/headroom lookups");

            Settlement firstSettlement = state.bootstrap().settlements().getFirst();
            Map<SubjectId, StructureCondition> changedConditions = new LinkedHashMap<>(state.structureConditions());
            changedConditions.put(firstSettlement.structures().getFirst().id(), StructureCondition.DESTROYED);
            FrontierWorldState structurallyChanged = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(changedConditions));
            assertTrue(FrontierV3AmbientActorExecutor.admissionPolicy(runtime, structurallyChanged).admissionFor(structurallyChanged)
                            .settlementAssaultCandidates().isEmpty(),
                    "a structural-input replacement must fail closed until a projection refreshes its snapshot");

            FrontierV3GrayboxExecutor.refresh(new FullyLoadedPhysicalWorld(), runtime, structurallyChanged);
            assertFalse(FrontierV3AmbientActorExecutor.admissionPolicy(runtime, structurallyChanged).admissionFor(structurallyChanged)
                            .settlementAssaultCandidates().isEmpty(),
                    "the registered projection owner alone refreshes the same runtime's structural admission authority");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void productionSnapshotBoundsAdmissionAndJoinRecognitionBeforePointQueries() {
        FrontierWorldState base = twoEligibleAssaults();
        SubjectId ambientResident = base.actorLocations().keySet().stream()
                .filter(actor -> !base.hiveColony().bioformLifecycles().containsKey(actor))
                .filter(actor -> base.strategicPlans().settlementAssaults().values().stream()
                        .noneMatch(assault -> assault.attackerIds().contains(actor) || assault.defenderIds().contains(actor)))
                .findFirst().orElseThrow();
        FrontierWorldState state = withPreparedLease(base, ambientResident);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierSettlementAssaultBattlefield.Provider snapshot = FrontierV3GrayboxExecutor.admissionProvider(runtime, state).orElseThrow();
            AtomicInteger queries = new AtomicInteger();
            AtomicInteger snapshotRequests = new AtomicInteger();
            FrontierSceneAdmission.ProviderSource countedSnapshot = ignored -> {
                snapshotRequests.incrementAndGet();
                return Optional.of(position -> {
                queries.incrementAndGet();
                return snapshot.cellAt(position);
                });
            };
            FrontierV3AmbientActorExecutor.ManagedCarrier carrier = new FrontierV3AmbientActorExecutor.ManagedCarrier(
                    FrontierV3AmbientActorExecutor.entityId(state, ambientResident), ambientResident.value(), false, false, "RESIDENT");

            assertTrue(FrontierV3AmbientActorExecutor.recognizes(runtime, carrier, countedSnapshot),
                    "the actual join-recognition decision must retain an exact ambient resident through the projection registry");
            int members = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                    .mapToInt(assault -> assault.attackerIds().size() + assault.defenderIds().size()).sum();
            assertEquals(members * 3, queries.get(),
                    "the complete recognition callback may perform only named support/headroom point queries after its fixed snapshot check");
            assertEquals(1, snapshotRequests.get(),
                    "the complete callback obtains one registered immutable view; compatibility is checked by the cursor's fixed identity fields");

            assertFalse(FrontierV3AmbientActorExecutor.recognizes(runtime, carrier, ignored -> Optional.empty()),
                    "a missing or stale derived provider must fail closed instead of reaching the default full compiler");

            FrontierWorldState unrelated = withPreparedLease(state, ambientResident);
            assertTrue(FrontierV3GrayboxExecutor.admissionProvider(runtime, unrelated).isPresent(),
                    "an unrelated canonical replacement preserves the projection-owned structural snapshot by identity");
            assertTrue(FrontierV3AmbientActorExecutor.recognizes(unrelated, carrier,
                    current -> FrontierV3GrayboxExecutor.admissionProvider(runtime, current)),
                    "the unchanged structural contributors retain ordinary ambient identity and recognition");

            Settlement settlement = state.bootstrap().settlements().getFirst();
            Map<SubjectId, StructureCondition> changed = new LinkedHashMap<>(state.structureConditions());
            changed.put(settlement.structures().getFirst().id(), StructureCondition.DESTROYED);
            FrontierWorldState structuralReplacement = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(changed));
            assertFalse(FrontierV3AmbientActorExecutor.recognizes(structuralReplacement, carrier,
                    current -> FrontierV3GrayboxExecutor.admissionProvider(runtime, current)),
                    "a relevant structural replacement fences join recognition until the same projection owner refreshes");
            FrontierV3GrayboxExecutor.refresh(new FullyLoadedPhysicalWorld(), runtime, structuralReplacement);
            assertTrue(FrontierV3AmbientActorExecutor.recognizes(structuralReplacement, carrier,
                    current -> FrontierV3GrayboxExecutor.admissionProvider(runtime, current)),
                    "the refreshed same-runtime snapshot restores the unchanged ambient identity without any join-path compiler");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void productionAdmissionPolicyOwnsSelectionAndOneProviderViewPerStateSegment() {
            FrontierWorldState base = twoEligibleAssaults();
            List<io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate> initialCandidates =
                    FrontierSceneAdmission.reservationAdmission(base).settlementAssaultCandidates();
            assertEquals(2, initialCandidates.size(), "the production fixture must expose two independent active assault candidates");
            SettlementAssault firstAssault = base.strategicPlans().settlementAssaults().values().stream().sorted(java.util.Comparator.comparing(SettlementAssault::id)).findFirst().orElseThrow();
            SettlementAssault secondAssault = base.strategicPlans().settlementAssaults().values().stream().sorted(java.util.Comparator.comparing(SettlementAssault::id)).skip(1).findFirst().orElseThrow();
            var firstCandidate = initialCandidates.stream().filter(candidate -> candidate.assaultId().equals(firstAssault.id())).findFirst().orElseThrow();
            var secondCandidate = initialCandidates.stream().filter(candidate -> candidate.assaultId().equals(secondAssault.id())).findFirst().orElseThrow();
            SubjectId lostAttacker = firstAssault.attackerIds().stream().filter(firstCandidate.memberPositions()::containsKey).findFirst().orElseThrow();
            var lostSupport = firstCandidate.memberPositions().get(lostAttacker);
            GrayboxCell provider = FrontierGrayboxPlan.compile(base).cells().get(lostSupport);
            assertNotNull(provider, "the named attacker must stand on one declared physical-provider surface");
            assertTrue(provider.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || provider.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                    "the named attacker loss must bind its declared perimeter support rather than an inferred floor");

            FrontierWorldState afterLoss = withExactSupportLoss(base, lostSupport, provider);
            assertFalse(afterLoss.coldSettlementAssaultSceneCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(firstAssault.id())),
                    "loss of the named attacker's exact surface must not select an alternate/member/defender floor");
            assertTrue(afterLoss.coldSettlementAssaultSceneCandidates().stream().anyMatch(candidate -> candidate.assaultId().equals(secondAssault.id())),
                    "the intact independent assault must remain eligible after the other assault loses its support");

            List<SubjectId> attackers = firstAssault.attackerIds().stream().sorted().toList();
            SubjectId firstActor = attackers.getFirst();
            SubjectId handoffActor = attackers.get(1);
            SubjectId candidateDependentDefender = firstAssault.defenderIds().stream().sorted().findFirst().orElseThrow();
            List<SubjectId> scanActors = List.of(firstActor, handoffActor, candidateDependentDefender).stream().sorted().toList();
            assertEquals(List.of(firstActor, handoffActor, candidateDependentDefender), scanActors,
                    "the ordinary scan must release one reserved attacker before observing the candidate-dependent defender");
            FrontierWorldState before = withPreparedLease(withPreparedLease(base, handoffActor), candidateDependentDefender);
            afterLoss = withExactSupportLoss(before, lostSupport, provider);
            FrontierWorldState originalState = before;
            AtomicReference<FrontierWorldState> replacementState = new AtomicReference<>();
            AtomicInteger derivations = new AtomicInteger();
            AtomicInteger providers = new AtomicInteger();
            AtomicInteger beforeDerivations = new AtomicInteger();
            AtomicInteger replacementDerivations = new AtomicInteger();
            AtomicInteger beforeProviders = new AtomicInteger();
            AtomicInteger replacementProviders = new AtomicInteger();
            FrontierSceneAdmission.ProviderSource realProvider = FrontierSceneAdmission.providerSource();
            FrontierV3AmbientAdmissionPolicy.AdmissionDeriver countedDeriver = state -> {
                derivations.incrementAndGet();
                if (state == originalState) beforeDerivations.incrementAndGet();
                if (state == replacementState.get()) replacementDerivations.incrementAndGet();
                return FrontierSceneAdmission.reservationAdmission(state, providerState -> {
                    providers.incrementAndGet();
                    if (providerState == originalState) beforeProviders.incrementAndGet();
                    if (providerState == replacementState.get()) replacementProviders.incrementAndGet();
                    return realProvider.provider(providerState);
                });
            };
            FrontierV3AmbientAdmissionPolicy.Session policy = FrontierV3AmbientAdmissionPolicy.begin(originalState, countedDeriver);
            List<FrontierV3AmbientAdmissionPolicy.Selection> selected = new ArrayList<>();
            List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = policy.scan(scanActors, () -> originalState, selection -> {
                selected.add(selection);
                assertEquals(handoffActor, selection.actorId(), "only the policy-selected prepared lease may execute the test effect");
                assertEquals(FrontierV3AmbientAdmissionPolicy.Effect.ABANDON_PREPARED, selection.effect());
                FrontierWorldState draining = AmbientLeaseStateProcess.transition(selection.state(), selection.actorId(), AmbientLeaseStatus.DRAINING);
                assertLeaseLineage(selection.state(), draining, selection.actorId(), AmbientLeaseStatus.DRAINING);
                AmbientActorLease lease = selection.state().ambientLeases().get(selection.actorId());
                FrontierWorldState released = AmbientLeaseStateProcess.release(draining,
                        new AmbientLeaseReleased(selection.actorId(), lease.handoffBody(), draining.actorLocations().get(selection.actorId()).condition().health()));
                assertLeaseLineage(selection.state(), released, selection.actorId(), AmbientLeaseStatus.CLOSED);
                FrontierWorldState next = withExactSupportLoss(released, lostSupport, provider);
                replacementState.set(next);
                return Optional.of(new FrontierV3AmbientAdmissionPolicy.EffectResult(draining, next));
            });

            assertEquals(3, decisions.size());
            assertEquals(originalState, decisions.get(0).state());
            assertEquals(originalState, decisions.get(1).state());
            assertEquals(replacementState.get(), decisions.get(2).state());
            assertEquals(firstActor, decisions.get(0).actorId());
            assertTrue(decisions.get(0).reserved());
            assertTrue(decisions.get(0).selectedEffect().isEmpty(), "the policy rejects a reserved actor with no active lease before the selected hand-off");
            assertEquals(handoffActor, decisions.get(1).actorId());
            assertEquals(FrontierV3AmbientAdmissionPolicy.Effect.ABANDON_PREPARED, decisions.get(1).selectedEffect().orElseThrow());
            assertTrue(decisions.get(1).applied());
            assertEquals(candidateDependentDefender, decisions.get(2).actorId());
            assertFalse(decisions.get(2).reserved(), "the later defender reservation must disappear with its lost assault candidate");
            assertTrue(decisions.get(2).selectedEffect().isEmpty(), "the later defender must not execute after the candidate-dependent reservation closes");
            assertEquals(AmbientLeaseStatus.CLOSED, replacementState.get().ambientLeases().get(handoffActor).status());
            assertEquals(before.ambientLeases().get(handoffActor).handoffBody(), replacementState.get().actorLocations().get(handoffActor).body());
            assertEquals(before.actorLocations().get(handoffActor).condition().health(), replacementState.get().actorLocations().get(handoffActor).condition().health());
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

            FrontierV3AmbientAdmissionPolicy.Decision rejected = FrontierV3AmbientAdmissionPolicy.begin(originalState, countedDeriver)
                    .decide(handoffActor, originalState, selection -> Optional.of(withChangedLeaseRevision(selection)));
            assertFalse(rejected.applied(), "a plausible hand-off with changed lease lineage must fail closed");

            assertDerivationPerActorIsDetected(before, scanActors, countedDeriver);
            assertProviderPerAssaultIsDetected(before, realProvider);
            assertStaleReplacementIsDetected(before, replacementState.get(), scanActors, countedDeriver);
            assertSubstituteFloorIsDetected(before, replacementState.get(), firstAssault, lostAttacker, scanActors, countedDeriver);
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

    private static void assertProviderPerAssaultIsDetected(FrontierWorldState state, FrontierSceneAdmission.ProviderSource realProvider) {
        AtomicInteger providers = new AtomicInteger();
        FrontierSceneAdmission.reservationAdmission(state, providerState -> {
            int activeAssaults = (int) providerState.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT).count();
            FrontierSettlementAssaultBattlefield.Provider provider = null;
            for (int index = 0; index < activeAssaults; index++) {
                providers.incrementAndGet();
                provider = realProvider.provider(providerState).orElseThrow();
            }
            return Optional.ofNullable(provider);
        });
        assertThrows(AssertionError.class, () -> assertEquals(1, providers.get(),
                "fault control: compiling the real provider once per assault must violate the segment count"));
    }

    private static void assertStaleReplacementIsDetected(FrontierWorldState before, FrontierWorldState afterLoss, List<SubjectId> actors,
                                                         FrontierV3AmbientAdmissionPolicy.AdmissionDeriver realDeriver) {
        FrontierV3AmbientAdmissionPolicy.Session stalePolicy = FrontierV3AmbientAdmissionPolicy.begin(before,
                ignored -> realDeriver.derive(before));
        List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = stalePolicy.scan(actors, () -> before,
                selection -> Optional.of(canonicalEffectResult(selection, afterLoss)));
        assertThrows(AssertionError.class, () -> assertFalse(decisions.get(2).admission().settlementAssaultCandidates().stream()
                        .anyMatch(candidate -> candidate.assaultId().value().contains("first")),
                "fault control: stale replacement contents must retain the lost assault"));
    }

    private static void assertSubstituteFloorIsDetected(FrontierWorldState before, FrontierWorldState afterLoss, SettlementAssault firstAssault,
                                                        SubjectId lostAttacker, List<SubjectId> actors,
                                                        FrontierV3AmbientAdmissionPolicy.AdmissionDeriver deriver) {
        FrontierWorldState substituted = withSubstituteFloor(afterLoss, firstAssault, lostAttacker);
        FrontierV3AmbientAdmissionPolicy.Session policy = FrontierV3AmbientAdmissionPolicy.begin(before, deriver);
        List<FrontierV3AmbientAdmissionPolicy.Decision> decisions = policy.scan(actors, () -> before,
                selection -> Optional.of(canonicalEffectResult(selection, substituted)));
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

    private static SubjectId firstCandidateActor(FrontierSceneAdmission.ReservationAdmission admission) {
        return admission.settlementAssaultCandidates().getFirst().memberPositions().keySet().stream().sorted().findFirst().orElseThrow();
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(
                base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static final class FullyLoadedPhysicalWorld implements FrontierV3AftermathPhysicalWorld {
        private final FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.inMemory();
        @Override public boolean naturallyLoaded(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return true; }
        @Override public boolean isAir(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return true; }
        @Override public boolean hasMaterial(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position, io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial material) { return false; }
        @Override public boolean placeMaterial(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position, io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial material) { return true; }
        @Override public boolean clear(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return true; }
        @Override public FrontierV3GrayboxLedger ledger() { return ledger; }
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("admission test does not compact"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("admission test does not compact");
        }
    }

    private static FrontierWorldState withExactSupportLoss(FrontierWorldState state,
                                                            io.farfrontier.palemirror.frontier.v3.model.BlockPosition lostSupport,
                                                            GrayboxCell provider) {
        Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, PhysicalDelta> losses = new LinkedHashMap<>(state.physicalDeltas());
        losses.put(lostSupport, new PhysicalDelta(lostSupport, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(provider.ownerId()), Optional.of(provider.semanticPart()), "test:admission-policy-named-attacker-loss"));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(losses));
    }

    private static void assertLeaseLineage(FrontierWorldState initial, FrontierWorldState candidate,
                                           SubjectId actorId, AmbientLeaseStatus expectedStatus) {
        AmbientActorLease expected = initial.ambientLeases().get(actorId);
        AmbientActorLease actual = candidate.ambientLeases().get(actorId);
        assertEquals(expected.actorId(), actual.actorId());
        assertEquals(expected.handoffBody(), actual.handoffBody());
        assertEquals(expected.handoffInstant(), actual.handoffInstant());
        assertEquals(expected.revision(), actual.revision());
        assertEquals(expected.goal(), actual.goal());
        assertEquals(expected.goalBody(), actual.goalBody());
        assertEquals(expectedStatus, actual.status());
    }

    private static FrontierV3AmbientAdmissionPolicy.EffectResult canonicalEffectResult(FrontierV3AmbientAdmissionPolicy.Selection selection,
                                                                                          FrontierWorldState resultingState) {
        return new FrontierV3AmbientAdmissionPolicy.EffectResult(
                AmbientLeaseStateProcess.transition(selection.state(), selection.actorId(), AmbientLeaseStatus.DRAINING), resultingState);
    }

    private static FrontierV3AmbientAdmissionPolicy.EffectResult withChangedLeaseRevision(FrontierV3AmbientAdmissionPolicy.Selection selection) {
        FrontierWorldState draining = AmbientLeaseStateProcess.transition(selection.state(), selection.actorId(), AmbientLeaseStatus.DRAINING);
        AmbientActorLease lease = selection.state().ambientLeases().get(selection.actorId());
        FrontierWorldState released = AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(selection.actorId(), lease.handoffBody(), draining.actorLocations().get(selection.actorId()).condition().health()));
        return new FrontierV3AmbientAdmissionPolicy.EffectResult(withChangedLeaseRevision(draining, selection.actorId()),
                withChangedLeaseRevision(released, selection.actorId()));
    }

    private static FrontierWorldState withChangedLeaseRevision(FrontierWorldState state, SubjectId actorId) {
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases());
        AmbientActorLease lease = leases.get(actorId);
        leases.put(actorId, new AmbientActorLease(lease.actorId(), lease.handoffBody(), lease.handoffInstant(), lease.revision() + 1L,
                lease.status(), lease.goal(), lease.goalBody()));
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
