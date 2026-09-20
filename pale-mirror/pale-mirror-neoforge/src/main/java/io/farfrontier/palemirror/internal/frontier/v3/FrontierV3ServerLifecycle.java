package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRecoveryConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.internal.presentation.PaleMirrorPlayerPresentation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.LevelResource;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
public final class FrontierV3ServerLifecycle {
    private static final String ENABLED_PROPERTY = "pale_mirror.frontier_v3.enabled";
    private static final String PILOT_RUN_ID_PROPERTY = "pale_mirror.frontier_v3.pilot.run_id";
    private static final String PILOT_INITIAL_CANONICAL_HOLD_PROPERTY = "pale_mirror.frontier_v3.pilot.initial_canonical_hold";
    private static final Map<MinecraftServer, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection>> RUNTIMES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Boolean> STOPPING = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> FAST_FORWARD_REMAINING = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Long> FAST_FORWARD_TARGETS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, String> FAST_FORWARD_FAILURES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, FastForwardTargetOutcome> FAST_FORWARD_OUTCOMES = new IdentityHashMap<>();
    /** Bounded operator receipts are presentation evidence only; canonical time remains runtime-owned. */
    private static final Map<MinecraftServer, List<FastForwardRequestOutcome>> FAST_FORWARD_REQUESTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, FastForwardSliceTelemetry> FAST_FORWARD_SLICE_TELEMETRY = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Boolean> INITIAL_CANONICAL_HOLDS = new IdentityHashMap<>();
    public static final int MAX_FAST_FORWARD_TICKS = 24_000;
    private static final int FAST_FORWARD_SLICE_TICKS = 8;
    private static final long MAX_FAST_FORWARD_SLICE_NANOS = 20_000_000L;
    private FrontierV3ServerLifecycle() { }
    private static void clearFastForwardState(MinecraftServer server) {
        STOPPING.remove(server);
        FAST_FORWARD_REMAINING.remove(server);
        FAST_FORWARD_TARGETS.remove(server);
        FAST_FORWARD_FAILURES.remove(server);
        FAST_FORWARD_OUTCOMES.remove(server);
        FAST_FORWARD_REQUESTS.remove(server);
        FAST_FORWARD_SLICE_TELEMETRY.remove(server);
        INITIAL_CANONICAL_HOLDS.remove(server);
    }
    static int fastForwardSliceTicks() { return FAST_FORWARD_SLICE_TICKS; }
    static List<FastForwardRequestOutcome> fastForwardRequests(MinecraftServer server) {
        return FAST_FORWARD_REQUESTS.getOrDefault(server, List.of());
    }
    public static String latestFastForwardReceipt(MinecraftServer server) {
        List<FastForwardRequestOutcome> requests = fastForwardRequests(server);
        if (requests.isEmpty()) return "no fast-forward receipt is available";
        FastForwardRequestOutcome value = requests.getLast();
        return "request=" + value.requestId() + " " + value.kind() + " status=" + value.status()
                + " requestedTicks=" + value.requestedTicks() + " target=" + value.targetInstant()
                + (value.reason() == null ? "" : " reason=" + value.reason());
    }
    public static boolean ownsPhysicalWorld(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return v3LaunchOwnsPhysicalWorld();
    }
    static boolean v3LaunchOwnsPhysicalWorld() { return enabled(); }
    public static boolean freezesLegacySourceRuntime() { return v3LaunchOwnsPhysicalWorld(); }
    public static String status(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!ownsPhysicalWorld(server)) return "Frontier v3 is not selected for this server.";
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null) {
            return "Frontier v3 owns Graybox, but no active runtime is available; inspect the server log.";
        }
        FrontierV3RuntimeStatus runtimeStatus = runtime.status();
        if (runtimeStatus.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return "Frontier v3 " + runtimeStatus.kind() + ": "
                    + runtimeStatus.detail().orElse("runtime is not active") + ".";
        }
        return runtime.projection(ProjectionQuery.summary())
                .map(FrontierV3ServerLifecycle::formatStatus)
                .orElse("Frontier v3 is ACTIVE, but its immutable status projection is unavailable.");
    }
    public static String diagnostic(MinecraftServer server, String view, String id) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(view, "view"); Objects.requireNonNull(id, "id");
        if (!"execution".equals(view) && !FrontierV3DiagnosticView.accepts(view, id)) {
            return FrontierV3DiagnosticJson.unavailableRuntime(view, id);
        }
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (!ownsPhysicalWorld(server) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3DiagnosticJson.unavailableRuntime(view, id);
        }
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        if ("execution".equals(view)) return FrontierV3PhysicalExecutionDiagnostic.render(checkpoint);
        if ("first_visibility".equals(view)) return FrontierV3DiagnosticJson.firstVisibility(id, checkpoint,
                FrontierV3GrayboxExecutor.firstVisibility(runtime, id));
        if ("status".equals(view)) return FrontierV3DiagnosticJson.operatorStatus(checkpoint, runtime.decodedState().orElseThrow(),
                fastForwardRequests(server), id);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        if ("settlement_population".equals(view)) {
            try {
                io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId = new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id);
                java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, FrontierV3AmbientAdmissionDiagnostic> admissions =
                        new java.util.LinkedHashMap<>();
                net.minecraft.server.level.ServerLevel level = FrontierV3PhysicalWorld.require(server);
                state.humanPopulation().residents().values().stream()
                        .filter(resident -> resident.settlementId().equals(settlementId))
                        .sorted(java.util.Comparator.comparing(resident -> resident.id().value()))
                        .forEach(resident -> admissions.put(resident.id(), FrontierV3AmbientActorExecutor.admissionDiagnostic(
                                level, runtime, state, resident.id())));
                return FrontierV3DiagnosticJson.settlementPopulation(checkpoint, state, id, admissions);
            } catch (IllegalArgumentException ignored) {
                return FrontierV3DiagnosticJson.settlementPopulation(checkpoint, state, id, java.util.Map.of());
            }
        }
        if ("performance".equals(view)) return FrontierV3PerformanceDiagnostic.render(checkpoint, runtime.executionMetrics().snapshot(), state,
                FAST_FORWARD_REMAINING.getOrDefault(server, 0), FAST_FORWARD_TARGETS.get(server), FAST_FORWARD_FAILURES.get(server),
                FAST_FORWARD_OUTCOMES.get(server), FAST_FORWARD_SLICE_TELEMETRY.get(server), fastForwardRequests(server));
        if ("projection_work".equals(view)) return FrontierV3ProjectionWorkDiagnostic.render(checkpoint, runtime);
        if ("player_resource".equals(view)) return FrontierV3PlayerResourceDiagnostic.render(checkpoint, state, server, id);
        if ("traversal_foundry".equals(view)) return FrontierV3TraversalFoundryDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        if ("hive_foundry".equals(view)) return FrontierV3HiveFoundryDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        if ("hive_mobilization".equals(view)) return FrontierV3HiveMobilizationDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        java.util.Optional<FrontierV3AmbientAdmissionDiagnostic> admission = java.util.Optional.empty();
        if ("actor".equals(view)) {
            try {
                admission = java.util.Optional.of(FrontierV3AmbientActorExecutor.admissionDiagnostic(
                        FrontierV3PhysicalWorld.require(server), runtime, state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness = java.util.Optional.empty();
        if ("intent".equals(view)) {
            try {
                harvestReadiness = FrontierV3ResourceSiteHarvestExecutor.readiness(
                        FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3SceneReadiness.Value> sceneReadiness = java.util.Optional.empty();
        if ("scene".equals(view)) {
            try {
                sceneReadiness = FrontierV3SceneReadiness.forSubject(FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3OperationAssemblyDiagnostic.Readiness> assemblyReadiness = java.util.Optional.empty();
        if ("operation".equals(view)) {
            try {
                assemblyReadiness = FrontierV3OperationAssemblyDiagnostic.readiness(FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3ContainerSurfaceExecutor.Readiness> containerReadiness = java.util.Optional.empty();
        if ("container".equals(view)) {
            try {
                containerReadiness = java.util.Optional.of(FrontierV3ContainerSurfaceExecutor.readiness(
                        FrontierV3PhysicalWorld.require(server), state, new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3EquipmentIssueExecutor.Readiness> equipmentIssueReadiness = java.util.Optional.empty();
        java.util.Optional<FrontierV3EquipmentReturnExecutor.Readiness> equipmentReturnReadiness = java.util.Optional.empty();
        if ("intent".equals(view)) {
            try {
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().get(
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(id));
                if (intent != null && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) {
                    equipmentIssueReadiness = java.util.Optional.of(FrontierV3EquipmentIssueExecutor.readinessDetail(
                            FrontierV3PhysicalWorld.require(server), state, intent));
                } else if (intent != null && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN) {
                    equipmentReturnReadiness = java.util.Optional.of(FrontierV3EquipmentReturnExecutor.readinessDetail(
                            FrontierV3PhysicalWorld.require(server), state, intent));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FrontierV3DiagnosticJson.render(view, id, checkpoint, state,
                "trace".equals(view) ? FrontierV3DiagnosticTrace.latest(server, id) : java.util.Optional.empty(), admission, harvestReadiness, sceneReadiness, assemblyReadiness,
                containerReadiness, equipmentIssueReadiness, equipmentReturnReadiness);
    }
    static FrontierV3PilotSceneDemandSnapshot pilotSceneDemandSnapshot(ServerLevel level, SubjectId assaultId) {
        Objects.requireNonNull(level, "pilot demand level");
        Objects.requireNonNull(assaultId, "pilot demand assault");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3PilotSceneDemandSnapshot.unavailable();
        }
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return FrontierV3PilotSceneDemandSnapshot.unavailable();
        return FrontierGrayboxPlan.withoutStructuralDerivation(() -> {
            var provider = FrontierV3GrayboxExecutor.admissionProvider(runtime, state).orElse(null);
            if (provider == null) return FrontierV3PilotSceneDemandSnapshot.unavailable();
            return FrontierV3PilotSceneDemandSnapshot.fromProviderCandidates(Optional.of(provider.getClass().getName()),
                    io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission.settlementAssaultCandidates(state, ignored -> Optional.of(provider)), assaultId,
                    anchor -> FrontierV3SceneDemand.observe(level, anchor));
        });
    }
    static String formatStatus(FrontierWorldProjection projection) {
        Objects.requireNonNull(projection, "projection");
        return "Frontier v3 ACTIVE | world=" + projection.worldId().value()
                + " rev=" + projection.revision().value() + " tick=" + projection.instant().ticks()
                + " | settlements=" + projection.settlementCount() + " residents=" + projection.residentCount()
                + " bioforms=" + projection.bioformCount() + " infectedCells=" + projection.infectedCellCount()
                + " | exactStacks=" + projection.itemStackCount() + " production=" + projection.activeProductionJobCount()
                + " routes=" + projection.activeRouteOperationCount()
                + " | effects prepared=" + projection.preparedPhysicalIntentCount() + " unknown=" + projection.unknownPhysicalIntentCount()
                + " | HOT scenes=" + projection.activeSceneLeaseCount() + "/unknown=" + projection.unknownSceneLeaseCount()
                + " ambient=" + projection.activeAmbientLeaseCount() + "/unknown=" + projection.unknownAmbientLeaseCount()
                + " | inventoryConflicts=" + projection.inventoryConflictCount();
    }
    public static void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (RUNTIMES.containsKey(server)) return;
        clearFastForwardState(server);
        if (!enabled()) return;
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        startConfigured(server, initialConfiguration(physicalWorld));
    }
    static void startModDevFixture(MinecraftServer server,
                                   io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(configuration, "configuration");
        clearFastForwardState(server);
        if (!enabled()) throw new IllegalStateException("Frontier v3 fixture bootstrap requires an enabled v3 launch");
        if (RUNTIMES.containsKey(server)) throw new IllegalStateException("Frontier v3 fixture bootstrap must run before the normal lifecycle");
        FrontierV3PhysicalWorld.require(server);
        startConfigured(server, configuration);
    }
    private static void startConfigured(MinecraftServer server,
                                        io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        FrontierV3PerformanceMetrics metrics = new FrontierV3PerformanceMetrics();
        FrontierFileStore store = new FrontierFileStore(server.getWorldPath(LevelResource.ROOT), FrontierWorldRuntimeDefinition.payloadCodecs());
        // The NeoForge host, not the reducer, supplies actual build/artifact/session provenance.
        var diagnosticIdentity = FrontierV3DiagnosticRuntimeIdentity.openServerSession();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime;
        try {
            RecoveryImage recovery = store.recover(configuration.worldId());
            configuration = FrontierWorldRecoveryConfiguration.select(configuration,
                    recovery.checkpoint().map(io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord::checkpoint));
            runtime = FrontierV3ServerRuntime.startRecovered(configuration.withExecutionMetrics(metrics), store, recovery, 200, diagnosticIdentity);
        } catch (RuntimeException error) {
            runtime = FrontierV3ServerRuntime.failedStart(configuration.withExecutionMetrics(metrics), store, 200, error, diagnosticIdentity);
        }
        RUNTIMES.put(server, runtime);
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE && Boolean.getBoolean(PILOT_INITIAL_CANONICAL_HOLD_PROPERTY)) {
            INITIAL_CANONICAL_HOLDS.put(server, Boolean.TRUE);
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            FrontierV3GrayboxExecutor.primeStructuralBaseline(runtime);
            FrontierV3ResourceSiteExecutor.beginRecovery(runtime);
            FrontierV3PlayerCustodyRecovery.beginRecovery(runtime);
            try {
                int uninspectable = FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, physicalWorld);
                int ambientUnknown = FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime);
                int sceneUnknown = FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime);
                int mobilizationUnknown = FrontierV3HiveMobilizationRestartSafety.quarantineReleasingMobilizations(runtime);
                if (uninspectable > 0) {
                    PaleMirrorMod.LOGGER.error("Frontier v3 quarantined {} uninspectable running physical intent(s) after restart", uninspectable);
                }
                if (ambientUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} ambient HOT lease(s) as UNKNOWN pending loaded-world recovery", ambientUnknown);
                }
                if (sceneUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} scene lease(s) as UNKNOWN pending loaded-world recovery", sceneUnknown);
                }
                if (mobilizationUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 conflicted {} cocoon release(s) with an uninspected restart outcome", mobilizationUnknown);
                }
            } catch (RuntimeException error) {
                runtime.quarantine(error);
            }
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            PaleMirrorMod.LOGGER.info("Frontier v3 runtime started for {}", server.getWorldPath(LevelResource.ROOT));
            String pilotRunId = System.getProperty(PILOT_RUN_ID_PROPERTY, "");
            if (!pilotRunId.isBlank()) {
                PaleMirrorMod.LOGGER.info("PMV3_PILOT_SERVER runId={} pid={}", pilotRunId, ProcessHandle.current().pid());
            }
        } else {
            PaleMirrorMod.LOGGER.error("Frontier v3 development runtime quarantined at startup: {}", runtime.status().detail().orElse("unknown"));
        }
    }
    private static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
    initialConfiguration(ServerLevel physicalWorld) {
        return initialConfiguration(FrontierV3PhysicalWorld.WORLD_ID, physicalWorld.getSeed());
    }
    static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
    initialConfiguration(io.farfrontier.palemirror.frontier.v3.api.WorldId worldId, long seed) {
        return FrontierWorldRuntimeDefinition.configuration(worldId, seed);
    }
    public static boolean requestFastForward(MinecraftServer server, int ticks) {
        Objects.requireNonNull(server, "server");
        if (ticks < 1 || ticks > MAX_FAST_FORWARD_TICKS || !ownsPhysicalWorld(server) || stopping(server)) return false;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE || FAST_FORWARD_REMAINING.containsKey(server) || FAST_FORWARD_TARGETS.containsKey(server)) {
            recordFastForwardRequest(server, "RELATIVE", ticks, null, null, null, "REJECTED", "another request is active or runtime is unavailable");
            return false;
        }
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        if (requiresPhysicalStep(physicalWorld, runtime)) {
            String blocker = physicalBlocker(physicalWorld, runtime);
            long admitted = runtime.checkpointImage().orElseThrow().instant().ticks();
            recordFastForwardRequest(server, "RELATIVE", ticks, admitted + ticks, admitted, admitted,
                    "REJECTED", "physical work is pending at admission: " + blocker);
            PaleMirrorMod.LOGGER.warn("Frontier v3 rejected relative fast-forward at admission because physical work is pending: {}", blocker);
            return false;
        }
        // A past terminal result stays visible in the bounded receipt history, but it must not
        // make the next admitted request look rejected in the live performance/status surface.
        FAST_FORWARD_FAILURES.remove(server);
        INITIAL_CANONICAL_HOLDS.remove(server);
        FAST_FORWARD_REMAINING.put(server, ticks);
        long admitted = runtime.checkpointImage().orElseThrow().instant().ticks();
        recordFastForwardRequest(server, "RELATIVE", ticks, admitted + ticks, admitted, null, "QUEUED", null);
        PaleMirrorMod.LOGGER.info("Frontier v3 queued operator fast-forward ticks={}", ticks);
        return true;
    }
    public static boolean requestFastForwardTo(MinecraftServer server, long targetInstant) {
        Objects.requireNonNull(server, "server");
        if (!ownsPhysicalWorld(server) || stopping(server) || FAST_FORWARD_REMAINING.containsKey(server) || FAST_FORWARD_TARGETS.containsKey(server)) {
            rejectFastForwardTarget(server, targetInstant, "another request is active or runtime is unavailable");
            recordFastForwardRequest(server, "ABSOLUTE", 0, targetInstant, null, null, "REJECTED", "another request is active or runtime is unavailable");
            return false;
        }
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            rejectFastForwardTarget(server, targetInstant, "runtime is not active");
            recordFastForwardRequest(server, "ABSOLUTE", 0, targetInstant, null, null, "REJECTED", "runtime is not active");
            return false;
        }
        if (requiresPhysicalStep(FrontierV3PhysicalWorld.require(server), runtime)) {
            rejectFastForwardTarget(server, targetInstant, "physical work is pending at admission");
            recordFastForwardRequest(server, "ABSOLUTE", 0, targetInstant, runtime.checkpointImage().orElseThrow().instant().ticks(), null,
                    "REJECTED", "physical work is pending at admission");
            return false;
        }
        long admittedCheckpoint = runtime.checkpointImage().orElseThrow().instant().ticks();
        OptionalInt delta = absoluteFastForwardDelta(admittedCheckpoint, targetInstant);
        if (delta.isEmpty()) {
            rejectFastForwardTarget(server, targetInstant, "target is crossed or unbounded");
            recordFastForwardRequest(server, "ABSOLUTE", 0, targetInstant, admittedCheckpoint, null, "REJECTED", "target is crossed or unbounded");
            return false;
        }
        FAST_FORWARD_FAILURES.remove(server);
        INITIAL_CANONICAL_HOLDS.remove(server);
        FAST_FORWARD_REMAINING.put(server, delta.getAsInt()); FAST_FORWARD_TARGETS.put(server, targetInstant);
        recordFastForwardTarget(server, targetInstant, admittedCheckpoint, null, "ADVANCING", null);
        recordFastForwardRequest(server, "ABSOLUTE", delta.getAsInt(), targetInstant, admittedCheckpoint, null, "QUEUED", null);
        PaleMirrorMod.LOGGER.info("Frontier v3 queued operator fast-forward target={}", targetInstant);
        return true;
    }
    public static boolean releaseFastForwardHold(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!ownsPhysicalWorld(server) || stopping(server) || FAST_FORWARD_REMAINING.containsKey(server)) return false;
        Long target = FAST_FORWARD_TARGETS.remove(server);
        FastForwardTargetOutcome prior = FAST_FORWARD_OUTCOMES.get(server);
        Long checkpoint = RUNTIMES.containsKey(server) ? RUNTIMES.get(server).checkpointImage().map(image -> image.instant().ticks()).orElse(null) : null;
        if (target == null) {
            // A pilot initial hold protects fixture construction from ordinary ticks before the
            // first real client can establish HOT ownership.  Its release is a control-only
            // hand-off at the current checkpoint, not a synthetic advance; publish the same
            // monotonic receipt shape so the client can prove that hand-off before continuing.
            if (INITIAL_CANONICAL_HOLDS.remove(server) == null || checkpoint == null) return false;
            FAST_FORWARD_OUTCOMES.put(server, nextFastForwardTargetOutcome(prior, checkpoint, checkpoint, checkpoint, "RELEASED", null));
            recordFastForwardRequest(server, "ABSOLUTE", 0, checkpoint, checkpoint, checkpoint, "RELEASED", null);
            return true;
        }
        FAST_FORWARD_OUTCOMES.put(server, nextFastForwardTargetOutcome(prior, target,
                prior == null ? checkpoint : prior.admittedCheckpointInstant(), checkpoint, "RELEASED", null));
        // Releasing a completed hold is itself an operator action. Retain a distinct receipt
        // instead of rewriting the target's completed/held outcome into a false new result.
        recordFastForwardRequest(server, "ABSOLUTE", 0, target, checkpoint, checkpoint, "RELEASED", null);
        return true;
    }
    static OptionalInt absoluteFastForwardDelta(long checkpointInstant, long targetInstant) {
        if (checkpointInstant < 0L || targetInstant <= checkpointInstant) return OptionalInt.empty();
        long delta;
        try { delta = Math.subtractExact(targetInstant, checkpointInstant); }
        catch (ArithmeticException ignored) { return OptionalInt.empty(); }
        return delta > MAX_FAST_FORWARD_TICKS ? OptionalInt.empty() : OptionalInt.of((int) delta);
    }
    static boolean runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(boolean initialHold,
                                                                          boolean absoluteTargetPresent,
                                                                          boolean fastForwardRemaining) {
        return initialHold || (absoluteTargetPresent && !fastForwardRemaining);
    }
    static boolean advancesOnlyQueuedCanonicalTime(boolean fastForwardRemaining) { return fastForwardRemaining; }
    public static void tick(MinecraftServer server) {
        if (stopping(server)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null) return;
        try {
            boolean active = runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE;
            boolean initialHold = INITIAL_CANONICAL_HOLDS.containsKey(server);
            boolean absoluteTargetPresent = FAST_FORWARD_TARGETS.containsKey(server);
            boolean fastForwardRemaining = FAST_FORWARD_REMAINING.containsKey(server);
            if (active && runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(initialHold, absoluteTargetPresent, fastForwardRemaining)) {
                runObservedPhysicalTurn(FrontierV3PhysicalWorld.require(server), runtime);
            } else if (active && advancesOnlyQueuedCanonicalTime(fastForwardRemaining)) {
                // A relative request owns exactly its admitted canonical interval.  Advancing
                // one ordinary tick beside each server slice would make wall-clock delivery
                // latency silently add canonical time beyond the declared receipt.
                advanceQueuedCanonicalTime(server, runtime);
            } else if (active && absoluteTargetPresent) {
                advanceQueuedCanonicalTime(server, runtime);
            } else if (active) {
                ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
                runObservedPhysicalTurn(physicalWorld, runtime);
                runtime.tick(FrontierV3RuntimeBudgets.ordinaryTick());
                if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) advanceQueuedCanonicalTime(server, runtime);
            }
        } catch (RuntimeException error) {
            PaleMirrorMod.LOGGER.error("Frontier v3 server tick failed before quarantine", error);
            runtime.quarantine(error);
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) {
            PaleMirrorMod.LOGGER.error("Frontier v3 development runtime quarantined: {}", runtime.status().detail().orElse("unknown"));
            releaseRuntime(server, runtime);
        }
    }
    public static void observeNaturalChunkLoad(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        Objects.requireNonNull(level, "first visibility level"); Objects.requireNonNull(chunk, "first visibility chunk");
        if (!ownsPhysicalWorld(level.getServer()) || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        FrontierV3GrayboxExecutor.observeNaturalChunkLoad(level, runtime, chunk);
    }
    public static void observePlayerIngress(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        Objects.requireNonNull(level, "first visibility level"); Objects.requireNonNull(chunk, "first visibility chunk");
        if (!ownsPhysicalWorld(level.getServer()) || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        FrontierV3GrayboxExecutor.observePlayerIngress(runtime, chunk);
    }
    static boolean sceneEligible(ServerLevel level, io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime == null || !FrontierV3PhysicalWorld.isPhysical(level) || FrontierV3GrayboxExecutor.sceneEligible(runtime, position);
    }
    static void runPhysicalTurn(ServerLevel physicalWorld, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        FrontierV3PhysicalExecutors.registry().tick(Objects.requireNonNull(physicalWorld, "physical world"),
                Objects.requireNonNull(runtime, "runtime"));
    }
    static void runObservedPhysicalTurn(ServerLevel physicalWorld,
                                        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        for (ServerPlayer player : physicalWorld.players()) {
            FrontierV3GrayboxExecutor.observePlayerIngress(runtime,
                    new net.minecraft.world.level.ChunkPos(player.blockPosition()));
        }
        runPhysicalTurn(physicalWorld, runtime);
    }
    public static void stop(MinecraftServer server) {
        try {
            FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
            if (runtime != null) releaseRuntime(server, runtime);
        } finally {
            FrontierV3DiagnosticTrace.forget(server);
            clearFastForwardState(server);
        }
    }
    private static void releaseRuntime(MinecraftServer server,
                                       FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        RUNTIMES.remove(server, runtime);
        releaseRuntime(runtime);
        FAST_FORWARD_REMAINING.remove(server); FAST_FORWARD_TARGETS.remove(server); FAST_FORWARD_FAILURES.remove(server);
        FAST_FORWARD_OUTCOMES.remove(server); FAST_FORWARD_SLICE_TELEMETRY.remove(server); INITIAL_CANONICAL_HOLDS.remove(server);
    }
    static void releaseRuntime(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        Objects.requireNonNull(runtime, "runtime");
        FrontierV3GrayboxExecutor.forgetFirstVisibility(runtime);
        FrontierV3GrayboxExecutor.forget(runtime);
        FrontierV3ResourceSiteExecutor.forget(runtime);
        FrontierV3PlayerCustodyRecovery.forget(runtime);
        FrontierV3InfectionOverlayExecutor.forget(runtime);
        FrontierV3ObjectBoardExecutor.forget(runtime);
        FrontierV3AmbientActorExecutor.forget(runtime);
        FrontierV3SceneExecutor.forget(runtime);
        FrontierV3SettlementAssaultSceneExecutor.forget(runtime);
        runtime.shutdown();
    }
    private static void advanceQueuedCanonicalTime(MinecraftServer server, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        long sliceStarted = System.nanoTime();
        long safetyNanos = 0L;
        long advanceNanos = 0L;
        Integer remaining = FAST_FORWARD_REMAINING.get(server);
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        if (remaining == null) return;
        long safetyStarted = System.nanoTime();
        boolean physicalStepRequired = requiresPhysicalStep(physicalWorld, runtime);
        safetyNanos += elapsedNanos(safetyStarted);
        if (physicalStepRequired) {
            safetyStarted = System.nanoTime();
            String physicalBlocker = physicalBlocker(physicalWorld, runtime);
            safetyNanos += elapsedNanos(safetyStarted);
            Long target = FAST_FORWARD_TARGETS.remove(server);
            if (target != null) {
                FAST_FORWARD_REMAINING.remove(server);
                FAST_FORWARD_FAILURES.put(server, "physical work became pending before the absolute target: " + physicalBlocker);
                recordFastForwardTarget(server, target, null, null, "REJECTED", FAST_FORWARD_FAILURES.get(server));
                updateFastForwardRequest(server, "ABSOLUTE", runtime.checkpointImage().orElseThrow().instant().ticks(), "REJECTED", FAST_FORWARD_FAILURES.get(server));
                PaleMirrorMod.LOGGER.warn("Frontier v3 rejected absolute fast-forward target because physical work became pending: {}", physicalBlocker);
            } else {
                FAST_FORWARD_REMAINING.remove(server);
                FAST_FORWARD_FAILURES.put(server, "physical work became pending during the relative interval: " + physicalBlocker);
                PaleMirrorMod.LOGGER.warn("Frontier v3 rejected relative fast-forward because physical work became pending: {}", physicalBlocker);
                updateFastForwardRequest(server, "RELATIVE", runtime.checkpointImage().orElseThrow().instant().ticks(), "REJECTED", FAST_FORWARD_FAILURES.get(server));
            }
            recordFastForwardSlice(server, sliceStarted, safetyNanos, advanceNanos, 0);
            return;
        }
        int allowed = Math.min(remaining, FAST_FORWARD_SLICE_TICKS); int advanced = 0;
        while (advanced < allowed && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            safetyStarted = System.nanoTime();
            physicalStepRequired = requiresPhysicalStep(physicalWorld, runtime);
            safetyNanos += elapsedNanos(safetyStarted);
            if (physicalStepRequired) break;
            long advanceStarted = System.nanoTime();
            boolean advancedOne = runtime.advance(1, FrontierV3RuntimeBudgets.fastForwardTick()).isPresent();
            advanceNanos += elapsedNanos(advanceStarted);
            if (!advancedOne) break;
            advanced++;
            if (!fastForwardSliceTimeRemaining(elapsedNanos(sliceStarted))) break;
        }
        int next = remaining - advanced;
        if (next <= 0) {
            FAST_FORWARD_REMAINING.remove(server);
            Long target = FAST_FORWARD_TARGETS.get(server);
            if (target != null) recordFastForwardTarget(server, target, null, runtime.checkpointImage().orElseThrow().instant().ticks(), "HELD", null);
            long reached = runtime.checkpointImage().orElseThrow().instant().ticks();
            updateFastForwardRequest(server, target == null ? "RELATIVE" : "ABSOLUTE", reached,
                    target == null ? "COMPLETED" : "HELD", null);
            PaleMirrorMod.LOGGER.info("Frontier v3 completed operator fast-forward{}", FAST_FORWARD_TARGETS.containsKey(server) ? " at held absolute target" : "");
        } else FAST_FORWARD_REMAINING.put(server, next);
        recordFastForwardSlice(server, sliceStarted, safetyNanos, advanceNanos, advanced);
    }
    private static boolean requiresPhysicalStep(ServerLevel physicalWorld,
                                                FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        return FrontierV3FastForwardSafety.requiresPhysicalStep(
                FrontierV3FastForwardSafety.requiresPhysicalStep(physicalWorld, runtime.decodedState().orElseThrow()),
                FrontierV3ResourceSiteExecutor.hasProjectionInFlight(runtime));
    }
    private static String physicalBlocker(ServerLevel physicalWorld,
                                          FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        String canonical = FrontierV3FastForwardSafety.blockingDescription(physicalWorld, runtime.decodedState().orElseThrow());
        String projection = FrontierV3ResourceSiteExecutor.hasProjectionInFlight(runtime)
                ? FrontierV3ResourceSiteExecutor.projectionBlockingDescription(runtime) : "";
        return FrontierV3FastForwardSafety.blockingDescription(canonical, projection);
    }
    static boolean fastForwardSliceTimeRemaining(long elapsedNanos) {
        if (elapsedNanos < 0L) throw new IllegalArgumentException("fast-forward elapsed time");
        return elapsedNanos < MAX_FAST_FORWARD_SLICE_NANOS;
    }
    private static long elapsedNanos(long started) { return Math.max(0L, System.nanoTime() - started); }
    private static void recordFastForwardSlice(MinecraftServer server, long sliceStarted, long safetyNanos, long advanceNanos, int advancedTicks) {
        FAST_FORWARD_SLICE_TELEMETRY.put(server, nextFastForwardSliceTelemetry(FAST_FORWARD_SLICE_TELEMETRY.get(server),
                elapsedNanos(sliceStarted), safetyNanos, advanceNanos, advancedTicks));
    }
    static FastForwardSliceTelemetry nextFastForwardSliceTelemetry(FastForwardSliceTelemetry prior, long totalNanos,
                                                                     long safetyNanos, long advanceNanos, int advancedTicks) {
        if (totalNanos < 0L || safetyNanos < 0L || advanceNanos < 0L || advancedTicks < 0) {
            throw new IllegalArgumentException("invalid fast-forward slice telemetry");
        }
        if (prior == null) return new FastForwardSliceTelemetry(1L, totalNanos, totalNanos, safetyNanos, safetyNanos,
                advanceNanos, advanceNanos, advancedTicks);
        return new FastForwardSliceTelemetry(Math.addExact(prior.samples(), 1L), Math.addExact(prior.totalNanos(), totalNanos),
                Math.max(prior.maxNanos(), totalNanos), Math.addExact(prior.safetyNanos(), safetyNanos),
                Math.max(prior.maxSafetyNanos(), safetyNanos), Math.addExact(prior.advanceNanos(), advanceNanos),
                Math.max(prior.maxAdvanceNanos(), advanceNanos), Math.addExact(prior.advancedTicks(), advancedTicks));
    }
    record FastForwardSliceTelemetry(long samples, long totalNanos, long maxNanos, long safetyNanos, long maxSafetyNanos,
                                     long advanceNanos, long maxAdvanceNanos, long advancedTicks) {
        FastForwardSliceTelemetry {
            if (samples < 1L || totalNanos < 0L || maxNanos < 0L || safetyNanos < 0L || maxSafetyNanos < 0L
                    || advanceNanos < 0L || maxAdvanceNanos < 0L || advancedTicks < 0L
                    || maxNanos > totalNanos || maxSafetyNanos > safetyNanos || maxAdvanceNanos > advanceNanos) {
                throw new IllegalArgumentException("invalid fast-forward slice telemetry");
            }
        }
    }
    static FastForwardTargetOutcome nextFastForwardTargetOutcome(FastForwardTargetOutcome prior, long target, Long admittedCheckpoint, Long reachedCheckpoint,
                                                                 String status, String failure) {
        long requestId = prior == null ? 1L : Math.addExact(prior.requestId(), 1L);
        return new FastForwardTargetOutcome(requestId, target, admittedCheckpoint, reachedCheckpoint, status, failure);
    }
    private static void rejectFastForwardTarget(MinecraftServer server, long target, String reason) {
        recordFastForwardTarget(server, target, null, null, "REJECTED", reason);
    }
    private static void recordFastForwardTarget(MinecraftServer server, long target, Long admittedCheckpoint, Long reachedCheckpoint,
                                                String status, String failure) {
        FastForwardTargetOutcome prior = FAST_FORWARD_OUTCOMES.get(server);
        if (prior != null && prior.targetInstant() == target && prior.status().equals("ADVANCING")
                && java.util.Set.of("HELD", "REJECTED").contains(status)) {
            FAST_FORWARD_OUTCOMES.put(server, new FastForwardTargetOutcome(prior.requestId(), target,
                    prior.admittedCheckpointInstant(), reachedCheckpoint, status, failure));
            return;
        }
        FAST_FORWARD_OUTCOMES.put(server, nextFastForwardTargetOutcome(prior, target, admittedCheckpoint, reachedCheckpoint, status, failure));
    }

    private static void recordFastForwardRequest(MinecraftServer server, String kind, int requestedTicks, Long target,
                                                 Long admittedCheckpoint, Long reachedCheckpoint, String status, String reason) {
        List<FastForwardRequestOutcome> prior = new ArrayList<>(fastForwardRequests(server));
        long requestId = prior.isEmpty() ? 1L : Math.addExact(prior.getLast().requestId(), 1L);
        prior.add(new FastForwardRequestOutcome(requestId, kind, requestedTicks, target, admittedCheckpoint, reachedCheckpoint, status, reason));
        if (prior.size() > 8) prior.removeFirst();
        FAST_FORWARD_REQUESTS.put(server, List.copyOf(prior));
    }

    private static void updateFastForwardRequest(MinecraftServer server, String kind, long reachedCheckpoint,
                                                 String status, String reason) {
        List<FastForwardRequestOutcome> prior = terminalizeQueuedFastForwardRequest(fastForwardRequests(server), kind,
                reachedCheckpoint, status, reason);
        if (prior.equals(fastForwardRequests(server))) {
            PaleMirrorMod.LOGGER.error("Frontier v3 could not correlate fast-forward terminal receipt kind={} status={}; retaining existing receipts", kind, status);
            return;
        }
        FAST_FORWARD_REQUESTS.put(server, prior);
    }

    /**
     * One request may be active at a time, so terminalization owns the queued receipt rather
     * than recomputing a target from transient driver maps.  In particular a relative request
     * retains its original target when physical work becomes pending between admission and the
     * next canonical slice.
     */
    static List<FastForwardRequestOutcome> terminalizeQueuedFastForwardRequest(List<FastForwardRequestOutcome> requests,
                                                                                  String kind, long reachedCheckpoint,
                                                                                  String status, String reason) {
        List<FastForwardRequestOutcome> prior = new ArrayList<>(Objects.requireNonNull(requests, "requests"));
        for (int index = prior.size() - 1; index >= 0; index--) {
            FastForwardRequestOutcome current = prior.get(index);
            if (current.kind().equals(kind) && current.status().equals("QUEUED")) {
                prior.set(index, new FastForwardRequestOutcome(current.requestId(), current.kind(), current.requestedTicks(),
                        current.targetInstant(), current.admittedCheckpointInstant(), reachedCheckpoint, status, reason));
                return List.copyOf(prior);
            }
        }
        return List.copyOf(prior);
    }

    /** Bounded in-game receipt; the runtime remains the sole owner of canonical time. */
    record FastForwardRequestOutcome(long requestId, String kind, int requestedTicks, Long targetInstant,
                                     Long admittedCheckpointInstant, Long reachedCheckpointInstant,
                                     String status, String reason) {
        FastForwardRequestOutcome {
            if (requestId < 1L || !java.util.Set.of("RELATIVE", "ABSOLUTE").contains(kind) || requestedTicks < 0
                    || !java.util.Set.of("QUEUED", "COMPLETED", "HELD", "REJECTED", "RELEASED").contains(status)
                    || ("RELATIVE".equals(kind) && (requestedTicks < 1
                    || (targetInstant == null && !"REJECTED".equals(status))))
                    || ("ABSOLUTE".equals(kind) && (targetInstant == null || targetInstant < 1L))
                    || (("QUEUED".equals(status) || "COMPLETED".equals(status) || "HELD".equals(status) || "RELEASED".equals(status))
                    && admittedCheckpointInstant == null)
                    || (("COMPLETED".equals(status) || "HELD".equals(status) || "RELEASED".equals(status)) && reachedCheckpointInstant == null)
                    || ("REJECTED".equals(status) != (reason != null))) {
                throw new IllegalArgumentException("invalid fast-forward request receipt");
            }
        }
    }
    record FastForwardTargetOutcome(long requestId, long targetInstant, Long admittedCheckpointInstant, Long reachedCheckpointInstant,
                                    String status, String failure) {
        FastForwardTargetOutcome {
            if (requestId < 1L || targetInstant < 1L || !java.util.Set.of("ADVANCING", "HELD", "REJECTED", "RELEASED").contains(status)
                    || (failure == null) != !"REJECTED".equals(status)
                    || (("HELD".equals(status) || "RELEASED".equals(status)) && !java.util.Objects.equals(reachedCheckpointInstant, targetInstant))
                    || (reachedCheckpointInstant != null && reachedCheckpointInstant < 0L)
                    || (admittedCheckpointInstant != null && admittedCheckpointInstant < 0L)) {
                throw new IllegalArgumentException("invalid fast-forward target outcome");
            }
        }
    }
    public static void beginStopping(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        STOPPING.put(server, Boolean.TRUE);
    }
    public static EntityJoinAdmission observeEntityJoin(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return EntityJoinAdmission.NOT_MANAGED;
        }
        return observeEntityJoin(runtime, entity);
    }
    static EntityJoinAdmission observeEntityJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity");
        return switch (FrontierV3AmbientActorExecutor.observeJoin(runtime, entity)) {
            case NOT_MANAGED -> EntityJoinAdmission.NOT_MANAGED;
            case RETAINED -> EntityJoinAdmission.RETAINED;
            case DUPLICATE_UNINDEXED -> EntityJoinAdmission.DUPLICATE_UNINDEXED;
        };
    }
    public static JoinFirewallProof observeSourceJoin(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return new JoinFirewallProof(EntityJoinAdmission.NOT_MANAGED, false);
        }
        return observeSourceJoin(runtime, entity);
    }
    static JoinFirewallProof observeSourceJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity");
        return composeSourceJoin(() -> observeEntityJoin(runtime, entity),
                () -> FrontierV3AmbientActorExecutor.retainsPendingJoin(runtime, entity)
                        || recognizesManagedAmbientCarrier(runtime, FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(entity))
                        || FrontierV3SceneExecutor.recognizes(runtime, entity));
    }
    static JoinFirewallProof observeSourceJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier,
                                               EntityJoinAdmission lifecycleAdmission) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(lifecycleAdmission, "lifecycle admission");
        return composeSourceJoin(() -> lifecycleAdmission, () -> recognizesManagedAmbientCarrier(runtime, carrier));
    }
    static JoinFirewallProof composeSourceJoin(Supplier<EntityJoinAdmission> lifecycle,
                                               BooleanSupplier recognition) {
        Objects.requireNonNull(lifecycle, "lifecycle action"); Objects.requireNonNull(recognition, "recognition action");
        try {
            return FrontierGrayboxPlan.withoutStructuralDerivation(
                    () -> new JoinFirewallProof(lifecycle.get(), recognition.getAsBoolean()));
        } catch (FrontierGrayboxPlan.StructuralDerivationForbiddenException forbidden) {
            return new JoinFirewallProof(EntityJoinAdmission.NOT_MANAGED, false);
        }
    }
    public static boolean recognizesManagedCarrier(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && (recognizesManagedAmbientCarrier(runtime, FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(entity))
                || FrontierV3SceneExecutor.recognizes(runtime, entity));
    }
    static boolean recognizesManagedAmbientCarrier(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier) {
        return FrontierV3AmbientCarrierRecognition.recognizes(runtime, carrier);
    }
    public static boolean observeLivingDeath(ServerLevel level, Entity entity, Entity source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        boolean sceneBody = FrontierV3SceneExecutor.recognizes(runtime, entity);
        boolean ambientBody = FrontierV3AmbientCarrierRecognition.recognizes(runtime, entity);
        if (!sceneBody && !ambientBody) return false;
        FrontierV3ActorEquipmentDeathExecutor.resolve(level, runtime, entity);
        return sceneBody ? FrontierV3SceneExecutor.observeDeath(runtime, entity, source)
                : FrontierV3AmbientActorExecutor.observeDeath(runtime, entity, source);
    }
    public static boolean observeEntityLeave(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3AmbientActorExecutor.observeLeave(runtime, entity, stopping(level.getServer()));
    }
    private static boolean stopping(MinecraftServer server) {
        return STOPPING.containsKey(server);
    }
    public static boolean blocksNativeCropGrowth(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(runtime, level, position);
    }
    static boolean normalDemandLossReleased(MinecraftServer server) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        return state.sceneLeases().values().stream()
                .filter(io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors::isResourceSiteHarvest)
                .noneMatch(lease -> lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED
                        || lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT
                        || lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING);
    }
    public static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return CargoCarrierInteraction.NOT_MANAGED;
        return releaseCargoCarrier(level, runtime, entity, java.util.Optional.of(player.getUUID()));
    }
    public static boolean presentObjectBoard(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String owner = entity.getPersistentData().getString("pale_mirror.frontier_v3.board_owner");
        if (owner.isBlank()) return false;
        var board = FrontierReadabilityPlan.compile(state).boards().get(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(owner));
        if (board == null || !FrontierV3ObjectBoardExecutor.isCurrentOwnedBoard(level, entity, board)) return false;
        PaleMirrorPlayerPresentation.inspect(player, "frontier-v3:board:" + owner, FrontierV3ObjectBoardCard.fromBoard(board));
        return true;
    }
    private static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                Entity entity, java.util.Optional<java.util.UUID> observerPlayerId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(observerPlayerId, "observer player id");
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return CargoCarrierInteraction.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return CargoCarrierInteraction.REJECTED;
        var lease = FrontierV3CargoCarrierExecutor.activeLease(state, entity);
        if (lease.isEmpty()) return CargoCarrierInteraction.NOT_MANAGED;
        if (!FrontierV3CargoCarrierExecutor.markReleasedCarrier(state, lease.orElseThrow(), entity)) return CargoCarrierInteraction.REJECTED;
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return CargoCarrierInteraction.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical("cargo-carrier-release", checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new CargoCarrierReleased(lease.orElseThrow().id(), io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease.orElseThrow()).cargoId(), entity.getUUID(), observerPlayerId)))
                .orElse(null);
        return result instanceof CommandResult.Accepted ? CargoCarrierInteraction.RELEASED : CargoCarrierInteraction.REJECTED;
    }
    public enum CargoCarrierInteraction { NOT_MANAGED, RELEASED, REJECTED }
    public static void observeTerminalVehicleDamage(ServerLevel level, Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        observeTerminalVehicleDamage(level, runtime, entity, source);
    }
    public static boolean isLeasedRoadCargoCarrier(Entity entity) {
        return entity instanceof MinecartChest
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.LEASE_KEY)
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.CARGO_KEY);
    }
    static void observeTerminalVehicleDamage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        CargoCarrierInteraction released = releaseCargoCarrier(level, runtime, entity, java.util.Optional.empty());
        if (released == CargoCarrierInteraction.REJECTED) {
            runtime.quarantine(new IllegalStateException("terminal vehicle damage cannot durably release one HOT cargo carrier"));
            return;
        }
        captureCargoCarrierImpact(level, runtime, entity, "terminal vehicle damage");
    }
    public static ExactCustodyObservation observeExactItemPickup(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return ExactCustodyObservation.REJECTED;
        var carrierId = FrontierV3CargoHandoffExecutor.worldCarrierId(itemEntity.getItem());
        if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
        var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
        var item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
        if (item.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
        return submitExactCustody(runtime, "world-pickup", item.orElseThrow().id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), source,
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Player(player.getUUID())));
    }
    public static ExactCustodyObservation observeExactItemToss(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return ExactCustodyObservation.REJECTED;
        var item = state.inventory().items().values().stream().filter(value -> value.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Player owner
                        && owner.playerId().equals(player.getUUID()))
                .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
        if (item.isEmpty()) {
            var carrierId = FrontierV3CargoHandoffExecutor.worldCarrierId(itemEntity.getItem());
            if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
            item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                    .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
            if (item.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
            return submitExactCustody(runtime, "world-retoss", item.orElseThrow().id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), source,
                            new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(itemEntity.getUUID())));
        }
        FrontierV3CargoHandoffExecutor.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
        return submitExactCustody(runtime, "player-toss", item.orElseThrow().id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), item.orElseThrow().custody(),
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(itemEntity.getUUID())));
    }
    private static ExactCustodyObservation submitExactCustody(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime,
                                                               String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return ExactCustodyObservation.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical(phase, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElse(null);
        return result instanceof CommandResult.Accepted ? ExactCustodyObservation.ACCEPTED : ExactCustodyObservation.REJECTED;
    }
    public enum ExactCustodyObservation { NOT_MANAGED, ACCEPTED, REJECTED }
    public enum PlayerBreakDisposition { UNMANAGED, ACCEPTED, REJECTED }
    public static PlayerBreakDisposition observePlayerBreakPacket(ServerLevel level, BlockPos position, ServerPlayer player) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position"); Objects.requireNonNull(player, "player");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return PlayerBreakDisposition.UNMANAGED;
        String cause = "player:" + player.getUUID();
        FrontierV3InfectionOverlayExecutor.BlockBreakObservation infection = FrontierV3InfectionOverlayExecutor.observeBlockBreak(runtime, level, position, cause);
        if (infection == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (infection == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        FrontierV3ResourceSiteExecutor.BlockBreakObservation resource = FrontierV3ResourceSiteExecutor.observeBlockBreak(runtime, level, position, cause);
        if (resource == FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (resource == FrontierV3ResourceSiteExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        FrontierV3GrayboxExecutor.BlockBreakObservation graybox = FrontierV3GrayboxExecutor.observeBlockBreak(runtime, level, position, cause);
        if (graybox == FrontierV3GrayboxExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (graybox == FrontierV3GrayboxExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        return PlayerBreakDisposition.UNMANAGED;
    }
    public static boolean rejectBlockBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return observePlayerBreakPacket(level, position, player) == PlayerBreakDisposition.REJECTED;
    }
    public static boolean acceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3PlayerBreakDisposition.accepted(level.getServer(), player.getUUID(), position);
    }
    public static boolean consumeAcceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3PlayerBreakDisposition.consume(level.getServer(), player.getUUID(), position);
    }
    public static void clearPlayerBreakDispositions(net.minecraft.server.MinecraftServer server) {
        FrontierV3PlayerBreakDisposition.clear(server);
    }
    public static boolean observeExplosion(ServerLevel level, net.minecraft.world.level.Explosion explosion,
                                           java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        return observeExplosion(level, runtime, explosion, affected, entities);
    }
    static boolean observeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    net.minecraft.world.level.Explosion explosion, java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(affected, "affected blocks");
        Entity directSource = explosion == null ? null : explosion.getDirectSourceEntity();
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> managed = FrontierV3ExplosionExecutionScope.currentIntent()
                .or(() -> FrontierV3BomberBomb.intentFor(explosion));
        for (Entity entity : entities) {
            CargoCarrierInteraction released = releaseCargoCarrier(level, runtime, entity, java.util.Optional.empty());
            if (released == CargoCarrierInteraction.REJECTED) {
                runtime.quarantine(new IllegalStateException("explosion cannot durably release one HOT cargo carrier"));
                return false;
            }
            if (managed.isEmpty()) captureCargoCarrierImpact(level, runtime, entity, "external explosion");
            if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) return false;
        }
        boolean resourceSite = managed.map(intent -> FrontierV3ResourceSiteExplosionExecutor.captureManaged(level, runtime, intent, affected))
                .orElseGet(() -> FrontierV3ResourceSiteExplosionExecutor.captureExternal(level, runtime, affected));
        boolean ordinary = managed.map(intent -> FrontierV3ExplosionExecutor.observeDetonation(level, runtime, intent, directSource, affected, entities))
                .orElseGet(() -> FrontierV3PhysicalObservationExecutor.captureExternalExplosion(level, runtime, affected));
        return resourceSite || ordinary;
    }
    private static void captureCargoCarrierImpact(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  Entity entity, String cause) {
        if (!(entity instanceof MinecartChest)) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) {
            runtime.quarantine(new IllegalStateException(cause + " has no canonical state"));
            return;
        }
        try {
            FrontierV3CargoCarrierImpactLedger.get(level).capture(level.getGameTime(), entity, state);
        } catch (RuntimeException error) {
            runtime.quarantine(error);
        }
    }
    public enum EntityJoinAdmission { NOT_MANAGED, RETAINED, DUPLICATE_UNINDEXED }
    public record JoinFirewallProof(EntityJoinAdmission lifecycleAdmission, boolean verifiedV3Carrier) {
        public JoinFirewallProof {
            Objects.requireNonNull(lifecycleAdmission, "lifecycle admission");
        }
    }
    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
