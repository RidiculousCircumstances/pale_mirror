package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
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
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultAttacker;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
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
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientAdmissionPolicyTest {
    @Test
    void startupPrimingCompilesOnlyTheImmutableBaselineBeforeAnyPhysicalTurn() {
        FrontierWorldState state = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(
                new WorldId("frontier:startup-structural-prime"), 47L).initialState();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            assertTrue(FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).isEmpty(),
                    "a fresh runtime has not yet published a structural baseline");

            FrontierV3GrayboxExecutor.primeStructuralBaseline(runtime);

            assertTrue(FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).isPresent(),
                    "startup must publish the immutable read index before a player-facing turn");
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot work = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, work.freshnessConstructions(), "priming constructs one immutable cursor");
            assertEquals(1, work.planCompilations(), "priming compiles one stable baseline");
            assertEquals(0, work.compatibilityChecks(), "priming is not an ordinary projection turn");
            assertEquals(0, work.providerAcquisitions(), "priming cannot acquire a physical admission provider");
            assertEquals(0, work.pointQueries(), "priming cannot inspect a physical surface");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void activeWorksiteOverlayRefreshDoesNotRecompileTheWorldWideBaseline() {
        FrontierWorldState state = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(
                new WorldId("frontier:projection-worksite-overlay"), 41L).initialState();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            var retainedBaseline = FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).orElseThrow();
            var project = state.routeConstructions().values().iterator().next();
            var projects = new LinkedHashMap<>(state.routeConstructions());
            projects.put(project.id(), new io.farfrontier.palemirror.frontier.v3.model.RouteConstruction(project.id(), project.settlementId(),
                    project.waypoints(), project.workCells(), project.confirmedCells(), project.status(), project.cargoId(),
                    project.team(), project.assembly()));
            FrontierWorldState advanced = state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(projects));
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            FrontierV3GrayboxExecutor.refresh(new FullyLoadedPhysicalWorld(), runtime, advanced);
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot work = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, work.freshnessConstructions(), "one construction edge refreshes its bounded overlay once");
            assertEquals(0, work.planCompilations(), "worksite progress must not rebuild the complete global graybox plan");
            assertEquals(1, work.cursorPath().calls(), "the reusable adapter must account for its one refresh path");
            assertEquals(1, work.deferredProjectionPath().calls(), "the reusable adapter must account for its bounded deferred pass");
            assertEquals(0, work.firstVisibilityPath().calls(), "a non-Minecraft adapter cannot manufacture player-ingress work");
            assertTrue(work.cursorPath().maxNanos() >= 0 && work.deferredProjectionPath().maxNanos() >= 0,
                    "the read-only high-water account must tolerate any monotonic clock result");
            assertTrue(FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).isPresent(),
                    "the original stable baseline remains available to later bounded admissions");
            assertTrue(retainedBaseline == FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).orElseThrow(),
                    "a worksite refresh must retain the immutable baseline instead of reconstructing the world-wide cursor");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void addedHiveOrganRefreshRetainsTheStaticCursorAndUsesOnlyTheDynamicPointView() {
        FrontierWorldState state = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(
                new WorldId("frontier:projection-dynamic-hive-overlay"), 41L).initialState();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierGrayboxPlan retainedBaseline = FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).orElseThrow();
            io.farfrontier.palemirror.frontier.v3.model.HiveOrgan organ = new io.farfrontier.palemirror.frontier.v3.model.HiveOrgan(
                    new SubjectId("organ:projection-overlay-relay"), state.bootstrap().hive().id(), new SubjectId("nest:seed-east"),
                    io.farfrontier.palemirror.frontier.v3.model.HiveOrganKind.RELAY,
                    new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(420, 64, 432), Optional.empty());
            FrontierWorldState advanced = state.addHiveOrgan(organ);
            FrontierGrayboxPlan overlay = FrontierGrayboxPlan.compileDynamicHiveOverlay(advanced);
            GrayboxCell dynamicCell = overlay.cells().values().iterator().next();
            assertFalse(retainedBaseline.cells().containsKey(dynamicCell.position()),
                    "the new organ is absent from the immutable static baseline before its overlay refresh");

            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            FrontierV3GrayboxExecutor.refresh(new FullyLoadedPhysicalWorld(), runtime, advanced);

            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot work = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, work.freshnessConstructions(), "one growth edge refreshes its bounded hive overlay once");
            assertEquals(0, work.planCompilations(), "a new organ must not recompile the settlement and route baseline");
            assertEquals("DYNAMIC_HIVE_OVERLAY", work.lastDisposition());
            assertTrue(retainedBaseline == FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).orElseThrow(),
                    "the static cursor/index remains the same object on a dynamic organ edge");
            assertEquals(dynamicCell, FrontierV3GrayboxExecutor.admissionProvider(runtime, advanced).orElseThrow()
                    .cellAt(dynamicCell.position()).orElseThrow(),
                    "named admission queries compose the current dynamic organ without a world-wide merged plan");
            assertTrue(FrontierV3GrayboxExecutor.publishedStructuralCeilings(runtime).orElseThrow()
                    .get(FrontierV3GrayboxExecutor.columnKey(dynamicCell.position().x(), dynamicCell.position().z())) >= dynamicCell.position().y(),
                    "the sparse overlay retains its declared column ceiling before loaded projection catches up");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void registeredAssaultAdmissionUsesOneProjectionProviderThroughPreparedReducer() {
        FrontierWorldState state = twoEligibleAssaults();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            assertFalse(FrontierV3SettlementAssaultSceneExecutor.tick(runtime, assaultTurn(
                    (candidate, provider, lease) -> { throw new AssertionError("an absent projection must not construct a lease"); })),
                    "the registered scene transaction must defer without a preceding projection");
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot absent = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, absent.compatibilityChecks());
            assertEquals(0, absent.freshnessConstructions());
            assertEquals(0, absent.planCompilations());
            assertEquals(0, absent.providerAcquisitions());
            assertEquals(0, absent.pointQueries());

            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            AtomicReference<SceneLease> prepared = new AtomicReference<>();
            AtomicReference<io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate> selected = new AtomicReference<>();
            AtomicInteger rejectedSubmits = new AtomicInteger();
            assertTrue(FrontierV3SettlementAssaultSceneExecutor.tick(runtime, assaultTurn((candidate, provider, lease) -> {
                selected.set(candidate);
                prepared.set(lease);
                assertTrue(FrontierV3SettlementAssaultSceneExecutor.submitPrepared(candidate, provider, lease,
                        () -> submit(runtime, state.bootstrap().worldId(), new SettlementAssaultSceneLeasePrepared(lease),
                                "command:projection-owned-scene-prepare")));
            })), "the compatible projection must admit one exact assault through its actual reducer command");
            assertEquals(SceneLeaseStatus.PREPARED, runtime.decodedState().orElseThrow().sceneLeases().get(prepared.get().id()).status());
            int members = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                    .mapToInt(assault -> assault.attackerIds().size() + assault.defenderIds().size()).sum();
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot preparedWork = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, preparedWork.compatibilityChecks());
            assertEquals(0, preparedWork.freshnessConstructions());
            assertEquals(0, preparedWork.planCompilations());
            assertEquals(1, preparedWork.providerAcquisitions());
            assertEquals((members + prepared.get().members().size()) * 3, preparedWork.pointQueries(),
                    "candidate selection and final trusted-command binding must use only named provider cells");
            io.farfrontier.palemirror.frontier.v3.model.BlockPosition lostFloor = prepared.get().memberBody(state.actorLocations(), prepared.get().members().getFirst().actorId())
                    .supportingSurface().support();
            GrayboxCell lostProviderFloor = FrontierGrayboxPlan.compile(state).cells().get(lostFloor);
            assertNotNull(lostProviderFloor, "the captured member starts on one retained provider floor");
            FrontierWorldState lossState = withExactSupportLoss(state, lostProviderFloor.position(), lostProviderFloor);
            FrontierSettlementAssaultBattlefield.Provider retainedProvider = FrontierV3GrayboxExecutor.admissionProvider(runtime, lossState).orElseThrow();
            FrontierSceneBehaviors.validatePrepared(lossState, prepared.get());
            assertFalse(FrontierV3SettlementAssaultSceneExecutor.submitPrepared(selected.get(), retainedProvider, prepared.get(),
                    rejectedSubmits::incrementAndGet),
                    "the trusted physical-executor prepared reducer may retain a locally clear exact roster, but final submission must reject its non-provider floor");
            assertEquals(0, rejectedSubmits.get(), "a rejected prepared command must never enter the physical-executor submit path");
            SceneLease forged = reducerAcceptedLostProviderHandoff(lossState, prepared.get(), prepared.get().members().getFirst().actorId(),
                    lostFloor);
            assertFalse(FrontierV3SettlementAssaultSceneExecutor.submitHandoff(retainedProvider, forged,
                    forged.memberBodies(lossState.actorLocations()), rejectedSubmits::incrementAndGet),
                    "the physical-executor handoff reducer may retain its exact moving body, but the final production boundary must reject a non-provider floor");
            assertEquals(0, rejectedSubmits.get(), "a rejected floor must never submit its trusted physical-executor handoff command");

            FrontierWorldState nonStructural = runtime.decodedState().orElseThrow();
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            assertTrue(FrontierV3SettlementAssaultSceneExecutor.tick(runtime, assaultTurn(
                    (candidate, provider, lease) -> { throw new AssertionError("an installed prepared scene owns its next registered turn"); })),
                    "the registered scene turn must consume the runtime-installed nonstructural prepared state");
            FrontierV3GrayboxExecutor.admissionProvider(runtime, nonStructural).orElseThrow();
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot retained = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(0, retained.freshnessConstructions());
            assertEquals(0, retained.planCompilations());
            assertEquals(1, retained.providerAcquisitions());
            assertEquals(0, retained.pointQueries(), "an active runtime-installed scene does not re-enumerate provider geometry");

            // The first scene deliberately does no work. It must not prevent a second
            // independent canonical assault from being admitted and receiving its own turn.
            AtomicReference<SceneLease> secondPrepared = new AtomicReference<>();
            var serviced = new java.util.ArrayList<SceneLeaseId>();
            var fairTurn = assaultTurn((candidate, provider, lease) -> {
                assertNull(secondPrepared.get(), "each admitted assault must retain one scene, not be prepared twice");
                secondPrepared.set(lease);
                assertTrue(FrontierV3SettlementAssaultSceneExecutor.submitPrepared(candidate, provider, lease,
                        () -> submit(runtime, state.bootstrap().worldId(), new SettlementAssaultSceneLeasePrepared(lease),
                                "command:fair-second-assault-prepare")));
            }, lease -> serviced.add(lease.id()));
            assertTrue(FrontierV3SettlementAssaultSceneExecutor.tick(runtime, fairTurn));
            assertNotNull(secondPrepared.get(), "waiting first scene may not own every admission turn");
            assertNotEquals(prepared.get().id(), secondPrepared.get().id());
            for (int tick = 0; tick < 3; tick++) assertTrue(FrontierV3SettlementAssaultSceneExecutor.tick(runtime, fairTurn));
            assertTrue(serviced.contains(prepared.get().id()));
            assertTrue(serviced.contains(secondPrepared.get().id()), "waiting first scene may not suppress the second scene");
            assertEquals(3, serviced.size(), "without new candidates each tick services exactly one active scene");

            FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> structuralRuntime = runtime(state);
            try {
                FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), structuralRuntime);
                GrayboxCell damaged = FrontierGrayboxPlan.compile(state).cells().values().stream()
                        .filter(cell -> cell.ownerId().equals(state.bootstrap().settlements().getFirst().structures().getFirst().id()))
                        .findFirst().orElseThrow();
                submit(structuralRuntime, state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.model.StructureDamaged(
                        damaged.ownerId(), damaged.position(), damaged.semanticPart(), "test:installed-structural-replacement"),
                        "command:projection-owned-scene-structural-change");
                FrontierWorldState stale = structuralRuntime.decodedState().orElseThrow();
                FrontierV3GrayboxExecutor.resetProjectionWork(structuralRuntime);
                assertFalse(FrontierV3SettlementAssaultSceneExecutor.tick(structuralRuntime, assaultTurn(
                        (candidate, provider, lease) -> { throw new AssertionError("a stale projection must not construct a lease"); })),
                        "an installed relevant structural replacement must fence scene admission until projection refresh");
                FrontierV3GrayboxExecutor.ProjectionWorkSnapshot fenced = FrontierV3GrayboxExecutor.projectionWork(structuralRuntime);
                assertEquals(1, fenced.compatibilityChecks());
                assertEquals(0, fenced.freshnessConstructions());
                assertEquals(0, fenced.planCompilations());
                assertEquals(0, fenced.providerAcquisitions());
                assertEquals(0, fenced.pointQueries());

                FrontierV3GrayboxExecutor.refresh(new FullyLoadedPhysicalWorld(), structuralRuntime, stale);
                AtomicReference<SceneLease> refreshed = new AtomicReference<>();
                assertTrue(FrontierV3SettlementAssaultSceneExecutor.tick(structuralRuntime, assaultTurn((candidate, provider, lease) -> {
                    refreshed.set(lease);
                    assertTrue(FrontierV3SettlementAssaultSceneExecutor.submitPrepared(candidate, provider, lease,
                            () -> submit(structuralRuntime, stale.bootstrap().worldId(), new SettlementAssaultSceneLeasePrepared(lease),
                                    "command:projection-owned-scene-refresh-prepare")));
                })), "only the refreshed projection may restore exact scene admission");
                assertEquals(SceneLeaseStatus.PREPARED, structuralRuntime.decodedState().orElseThrow().sceneLeases().get(refreshed.get().id()).status());
                FrontierV3GrayboxExecutor.ProjectionWorkSnapshot refreshedWork = FrontierV3GrayboxExecutor.projectionWork(structuralRuntime);
                assertEquals(1, refreshedWork.freshnessConstructions());
                assertEquals(1, refreshedWork.planCompilations(), "one relevant structural replacement compiles only in the projection owner");
                assertEquals(1, refreshedWork.providerAcquisitions());
                assertEquals((members + refreshed.get().members().size()) * 3, refreshedWork.pointQueries(),
                        "post-refresh candidate and final binding still consume only named provider cells");
            } finally { FrontierV3GrayboxExecutor.forget(structuralRuntime); structuralRuntime.shutdown(); }
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void projectionOwnedSnapshotIsTheOnlyBoundedProviderForProductionAssaultAdmission() {
        FrontierWorldState state = twoEligibleAssaults();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        try {
            assertTrue(FrontierV3AmbientActorExecutor.admissionPolicy(runtime, state).admissionFor(state)
                            .settlementAssaultCandidates().isEmpty(),
                    "without an earlier compatible projection, assault admission must defer instead of compiling graybox");

            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            FrontierSceneAdmission.ReservationAdmission initial = FrontierV3AmbientActorExecutor.admissionPolicy(runtime, state).admissionFor(state);
            assertEquals(2, initial.settlementAssaultCandidates().size(),
                    "the actor path must receive the snapshot produced by the preceding projector");
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot initialWork = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, initialWork.compatibilityChecks());
            assertEquals(0, initialWork.freshnessConstructions());
            assertEquals(0, initialWork.planCompilations());
            assertEquals(1, initialWork.providerAcquisitions());

            FrontierSettlementAssaultBattlefield.Provider snapshot = FrontierV3GrayboxExecutor.admissionProvider(runtime, state).orElseThrow();
            AtomicInteger queries = new AtomicInteger();
            FrontierSceneAdmission.ReservationAdmission counted = FrontierSceneAdmission.reservationAdmission(state, ignored -> Optional.of(position -> {
                queries.incrementAndGet();
                return snapshot.cellAt(position);
            }));
            int members = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                    .mapToInt(assault -> assault.attackerIds().size() + assault.defenderIds().size()).sum();
            assertEquals(members * 3, initialWork.pointQueries());
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
        FrontierWorldState state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(
                withPreparedLease(base, ambientResident), ambientResident);
        EphemeralStore store = new EphemeralStore();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state, List.of(), store);
        try {
            FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(
                    FrontierV3AmbientActorExecutor.entityId(state, ambientResident), ambientResident.value(), false, "RESIDENT",
                    "ACTOR_BODY", "LIVE_BODY", 0L,
                    io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, ambientResident).physicalEpoch(), "minecraft:villager");
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, true,
                    "an absent projection must fail closed through the same lifecycle/firewall composition");
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);

            int members = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                    .mapToInt(assault -> assault.attackerIds().size() + assault.defenderIds().size()).sum();
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED, false,
                    "the actual lifecycle recognition delegate and production firewall must retain the exact ambient resident");
            assertProjectionReadWork(runtime, members, "initial lifecycle recognition");

            FrontierV3ServerLifecycle.JoinFirewallProof inputFault = FrontierV3ServerLifecycle.composeSourceJoin(
                    () -> { FrontierGrayboxPlan.structuralInput(state); return FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED; }, () -> true);
            assertEquals(FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, inputFault.lifecycleAdmission(),
                    "fault control: the outer production join boundary catches the former pre-query structural-input fallback");
            assertTrue(SourceGrayboxEntityAdmission.rejectsSourceMob(inputFault, true, false),
                    "a structural-input fault must become the ordinary source-firewall cancellation, not an event exception");
            FrontierV3ServerLifecycle.JoinFirewallProof compileFault = FrontierV3ServerLifecycle.composeSourceJoin(
                    () -> FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED,
                    () -> { FrontierGrayboxPlan.compileStructuralBaseline(state); return true; });
            assertEquals(FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, compileFault.lifecycleAdmission(),
                    "fault control: the outer production join boundary catches a global compile from recognition");
            assertTrue(SourceGrayboxEntityAdmission.rejectsSourceMob(compileFault, true, false),
                    "a global compile fault must take the same fail-closed firewall result");

            GrayboxCell damaged = FrontierGrayboxPlan.compile(state).cells().values().stream()
                    .filter(cell -> cell.ownerId().equals(state.bootstrap().settlements().getFirst().structures().getFirst().id()))
                    .findFirst().orElseThrow();
            submit(runtime, state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.model.StructureDamaged(
                    damaged.ownerId(), damaged.position(), damaged.semanticPart(), "player:runtime-structural-change"), "command:runtime-structure-damage");

            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, true,
                    "an installed structural replacement must fence lifecycle recognition and cancel at the same source firewall");
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot fenced = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(1, fenced.compatibilityChecks());
            assertEquals(0, fenced.freshnessConstructions());
            assertEquals(0, fenced.planCompilations());
            assertEquals(0, fenced.providerAcquisitions());
            assertEquals(0, fenced.pointQueries());

            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED, false,
                    "only the projection owner may refresh the installed structural state and restore its exact joining body");
            assertProjectionReadWork(runtime, members, "projection refresh after installed structural change");

            FrontierV3GrayboxExecutor.forget(runtime);
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, true,
                    "forget/restart loses only the derived snapshot and must fence the same join/firewall composition");
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED, false,
                    "the projection owner reconstructs a forgotten snapshot without changing exact join ownership");

            FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> restarted = runtime(state, List.of(), store);
            try {
                assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, restarted.status().kind(), "the ordinary WAL runtime restart stays active");
                assertEquals(runtime.decodedState().orElseThrow(), restarted.decodedState().orElseThrow(),
                        "restart must recover the installed structural transition before join recognition runs");
                assertJoinFirewall(restarted, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED, true,
                        "a restarted runtime has no retained provider and must fence the same join/firewall composition");
                FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), restarted);
                assertJoinFirewall(restarted, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED, false,
                        "the restarted projection owner alone restores the exact join/firewall proof from recovered state");
            } finally { FrontierV3GrayboxExecutor.forget(restarted); restarted.shutdown(); }
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void scheduledNutrientTransitionRetainsProjectionCompatibilityThroughRuntimeJoinAndFirewall() {
        FrontierWorldState initial = nutrientRuntimeState();
        SubjectId resident = initial.actorLocations().keySet().stream()
                .filter(actor -> !initial.hiveColony().bioformLifecycles().containsKey(actor)).findFirst().orElseThrow();
        FrontierWorldState state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(
                withPreparedLease(initial, resident), resident);
        StrategicTask task = state.strategicPlans().tasks().get(new SubjectId("task:runtime-nutrient"));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state,
                List.of(io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess.start(task, 1L)));
        try {
            assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().toString());
            FrontierV3GrayboxExecutor.tick(new FullyLoadedPhysicalWorld(), runtime);
            runtime.advance(1, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 512))
                    .orElseThrow(() -> new IllegalStateException(runtime.status().toString()));
            assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().toString());
            FrontierWorldState nutrientReplacement = runtime.decodedState().orElseThrow();
            assertEquals(1, nutrientReplacement.hiveColony().nutrientTransfers().size(),
                    "the ordinary scheduled runtime transition must install its nutrient-only colony replacement");
            var retained = runtime.checkpointImage().orElseThrow().schedules();
            var validator = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed()).stateValidator();
            validator.validateScheduleChanges(nutrientReplacement, retained);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> validator.validateScheduleChanges(state, retained),
                    "the transfer continuation must fail when its exact transfer is absent, even while its task exists");

            FrontierV3GrayboxExecutor.resetProjectionWork(runtime);
            FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(
                    FrontierV3AmbientActorExecutor.entityId(nutrientReplacement, resident), resident.value(), false, "RESIDENT",
                    "ACTOR_BODY", "LIVE_BODY", 0L,
                    io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(nutrientReplacement, resident).physicalEpoch(), "minecraft:villager");
            assertJoinFirewall(runtime, carrier, FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED, false,
                    "the installed nutrient replacement must retain the exact joining body through the ordinary runtime provider and firewall");
            FrontierV3GrayboxExecutor.admissionProvider(runtime, nutrientReplacement).orElseThrow()
                    .cellAt(nutrientReplacement.actorLocations().get(resident).supportingSurface().support());
            FrontierV3GrayboxExecutor.ProjectionWorkSnapshot work = FrontierV3GrayboxExecutor.projectionWork(runtime);
            assertEquals(2, work.compatibilityChecks());
            assertEquals(0, work.freshnessConstructions());
            assertEquals(0, work.planCompilations());
            assertEquals(2, work.providerAcquisitions());
            assertEquals(1, work.pointQueries(), "the counters include the explicit bounded point lookup after callback work");
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

    private static SceneLease reducerAcceptedLostProviderHandoff(FrontierWorldState state, SceneLease lease, SubjectId actor,
                                                                   io.farfrontier.palemirror.frontier.v3.model.BlockPosition lostFloor) {
        Map<SubjectId, AmbientActorLease> hot = new LinkedHashMap<>(state.ambientLeases());
        BodyPosition original = state.actorLocations().get(actor).body();
        hot.put(actor, new AmbientActorLease(actor, original, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.GUARD, original));
        FrontierWorldState captureState = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(hot));
        SceneLease forged = lease.withAmbientHandoff(Set.of(actor));
        captureState.handoffAmbientScene(new SceneLeaseHandoff(forged, List.of(
                new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(actor,
                        BodyPosition.aboveSupportCell(lostFloor), state.actorLocations().get(actor).condition().health()))));
        return forged;
    }

    /** Exact delegate of the registered assault turn, without a fake {@code ServerLevel}. */
    private static FrontierV3SettlementAssaultSceneExecutor.Turn assaultTurn(PreparedAdmission prepared) {
        return assaultTurn(prepared, ignored -> { });
    }

    private static FrontierV3SettlementAssaultSceneExecutor.Turn assaultTurn(PreparedAdmission prepared,
                                                                          java.util.function.Consumer<SceneLease> executed) {
        return new FrontierV3SettlementAssaultSceneExecutor.Turn() {
            @Override public boolean demanded(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return true; }
            @Override public void execute(FrontierWorldState state, SceneLease lease) { executed.accept(lease); }
            @Override public void prepareMarch(SceneLease lease) {
                throw new AssertionError("the battle fixture must not select a march admission");
            }
            @Override public void prepare(io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate candidate,
                                          FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
                prepared.prepare(candidate, provider, lease);
            }
            @Override public void handoff(FrontierWorldState state, FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
                throw new AssertionError("the prepared fixture must not select an ambient handoff");
            }
        };
    }

    @FunctionalInterface
    private interface PreparedAdmission {
        void prepare(io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate candidate,
                     FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        return runtime(state, List.of());
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state,
                                                                                                    List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> schedules) {
        return runtime(state, schedules, new EphemeralStore());
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state,
                                                                                                    List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> schedules,
                                                                                                    EphemeralStore store) {
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(
                base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, store, 10_000);
    }

    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime, WorldId world,
                               FrontierPayload payload, String command) {
        io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        CommandId commandId = new CommandId(command);
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private static void assertProjectionReadWork(FrontierV3ServerRuntime<?, ?> runtime, int members, String phase) {
        FrontierV3GrayboxExecutor.ProjectionWorkSnapshot work = FrontierV3GrayboxExecutor.projectionWork(runtime);
        assertEquals(1, work.compatibilityChecks(), phase);
        assertEquals(0, work.freshnessConstructions(), phase);
        assertEquals(0, work.planCompilations(), phase);
        assertEquals(1, work.providerAcquisitions(), phase);
        assertEquals(members * 3, work.pointQueries(), phase);
    }

    private static void assertJoinFirewall(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                           FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier,
                                           FrontierV3ServerLifecycle.EntityJoinAdmission lifecycleAdmission,
                                           boolean expectedCancellation, String phase) {
        FrontierV3ServerLifecycle.JoinFirewallProof proof = FrontierV3ServerLifecycle.observeSourceJoin(runtime, carrier, lifecycleAdmission);
        assertEquals(expectedCancellation, SourceGrayboxEntityAdmission.rejectsSourceMob(proof, true, false), phase);
        assertEquals(!expectedCancellation, proof.verifiedV3Carrier(), phase);
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
        private final List<TransactionRecord> transactions = new ArrayList<>();
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.copyOf(transactions)); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            transactions.add(transaction);
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
                Optional.of(provider.semanticTarget()), Optional.of(provider.semanticPart()), "test:admission-policy-named-attacker-loss"));
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
        locations.put(attacker, new ActorLocation(BodyPosition.above(new SurfaceAnchor(substitute)), original.condition(), original.kind()));
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
        Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells();
        Map<SubjectId, ActorLocation> locations = new LinkedHashMap<>(state.actorLocations());
        place(locations, cells, state, firstSettlement, actors.subList(0, 3));
        place(locations, cells, state, secondSettlement, actors.subList(3, 6));
        SettlementAssault first = assault(new SubjectId("assault:admission-policy-first"), task, firstSighting,
                actors.subList(0, 3), firstSettlement, locations);
        SettlementAssault second = assault(new SubjectId("assault:admission-policy-second"), task, secondSighting,
                actors.subList(3, 6), secondSettlement, locations);
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations).strategicPlans(plans));
        state = io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admit(state, first,
                io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, first), Optional.empty(),
                FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().startSettlementAssault(first)));
        return io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admit(state, second,
                io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, second), Optional.empty(),
                FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().startSettlementAssault(second)));
    }

    private static FrontierWorldState nutrientRuntimeState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:runtime-nutrient"), 93L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:runtime-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:runtime-nutrient"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                List.of(), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static SettlementAssault assault(SubjectId id, StrategicTask task, HiveSettlementKnowledge.Sighting sighting,
                                             List<SubjectId> attackers, Settlement settlement, Map<SubjectId, ActorLocation> locations) {
        List<SubjectId> defenders = settlement.residents().stream().map(resident -> resident.id()).limit(3).toList();
        return new SettlementAssault(id, task.id(), task.ownerId(), sighting, attackers.getFirst(),
                attackers.stream().map(actor -> new SettlementAssaultAttacker(actor, List.of(locations.get(actor).supportingSurface().support()), 0)).toList(), defenders,
                SettlementAssaultStatus.COLD_COMBAT, 0, Optional.empty());
    }

    private static void place(Map<SubjectId, ActorLocation> locations, Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GrayboxCell> cells,
                              FrontierWorldState state, Settlement settlement, List<SubjectId> attackers) {
        List<SubjectId> members = new ArrayList<>(attackers);
        members.addAll(settlement.residents().stream().map(resident -> resident.id()).limit(3).toList());
        List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> floors = cells.entrySet().stream()
                .filter(entry -> entry.getValue().semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || entry.getValue().semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE)
                .map(Map.Entry::getKey).filter(position -> SettlementResidentIngressPlan.compile(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                        state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).perimeterSurfaces().contains(new SurfaceAnchor(position)))
                .filter(position -> !cells.containsKey(position.offset(0, 1, 0)) && !cells.containsKey(position.offset(0, 2, 0))).limit(members.size()).toList();
        assertEquals(members.size(), floors.size(), "fixture must retain declared perimeter floors for every named assault member");
        for (int index = 0; index < members.size(); index++) {
            SubjectId actor = members.get(index);
            locations.put(actor, new ActorLocation(BodyPosition.above(new SurfaceAnchor(floors.get(index))), locations.get(actor).condition(), locations.get(actor).kind()));
        }
    }
}
