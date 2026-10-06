package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRecoveryConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
    /**
     * Each vanilla turn admits at most ten due/audit/save-boundary steps under the existing
     * 20ms cap. Empty canonical intervals do not consume one step per skipped tick.
     */
    private static final int FAST_FORWARD_SLICE_TICKS = 10;
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
    public enum DepotClickDisposition { NOT_OWNED, ACCEPTED, REJECTED }
    public static DepotClickDisposition beforeDepotMenuClick(ServerPlayer player,
            net.minecraft.world.inventory.AbstractContainerMenu menu, int slot, int button,
            net.minecraft.world.inventory.ClickType kind) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(player.getServer());
        if (runtime == null) return v3LaunchOwnsPhysicalWorld()
                && menu instanceof net.minecraft.world.inventory.ChestMenu
                ? DepotClickDisposition.REJECTED : DepotClickDisposition.NOT_OWNED;
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE)
            return FrontierV3DepotClickExecutor.ownsDepot(player, menu, runtime)
                    ? DepotClickDisposition.REJECTED : DepotClickDisposition.NOT_OWNED;
        return switch (FrontierV3DepotClickExecutor.before(player, menu, slot, button, kind, runtime)) {
            case NOT_OWNED -> DepotClickDisposition.NOT_OWNED;
            case ACCEPTED -> DepotClickDisposition.ACCEPTED;
            case REJECTED -> DepotClickDisposition.REJECTED;
        };
    }
    public static void afterDepotMenuClick(ServerPlayer player,
            net.minecraft.world.inventory.AbstractContainerMenu menu) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(player.getServer());
        if (runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE)
            FrontierV3DepotClickExecutor.after(player, menu, runtime);
    }
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
        return FrontierV3ServerDiagnostic.render(server, RUNTIMES.get(server), view, id);
    }

    static String performanceDiagnostic(MinecraftServer server,
                                        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime,
                                        CheckpointImage checkpoint, FrontierWorldState state) {
        return FrontierV3PerformanceDiagnostic.render(checkpoint, runtime.executionMetrics().snapshot(), state,
                FAST_FORWARD_REMAINING.getOrDefault(server, 0), FAST_FORWARD_TARGETS.get(server), FAST_FORWARD_FAILURES.get(server),
                FAST_FORWARD_OUTCOMES.get(server), FAST_FORWARD_SLICE_TELEMETRY.get(server), fastForwardRequests(server));
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
        RuntimeException startupFailure = null;
        try {
            RecoveryImage recovery = store.recover(configuration.worldId());
            configuration = FrontierWorldRecoveryConfiguration.select(configuration,
                    recovery.checkpoint().map(io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord::checkpoint));
            if (recovery.checkpoint().isEmpty() && recovery.walTail().isEmpty()) {
                var firstLedger = FrontierV3AmbientCarrierLedger.get(physicalWorld, configuration.worldId());
                var firstWorld = configuration.worldId();
                FrontierV3ActorFirstAdmissionBootstrap.initialize(firstLedger, configuration.initialState(), recovery,
                        () -> firstLedger.persist(physicalWorld, firstWorld));
            }
            var birthLedger = FrontierV3AmbientCarrierLedger.get(physicalWorld, configuration.worldId());
            var birthWorld = configuration.worldId();
            var birthCommitter = new FrontierV3ActorBirthCommitter(birthWorld, birthLedger,
                    () -> birthLedger.persist(physicalWorld, birthWorld), new FrontierStoreTransactionCommitter(store));
            runtime = FrontierV3ServerRuntime.startRecovered(configuration.withExecutionMetrics(metrics), store, recovery, 200,
                    diagnosticIdentity, birthCommitter);
            runtime.decodedState().ifPresent(recovered -> FrontierV3ActorBirthRecovery.retireUnpublished(recovered,
                    birthLedger, () -> birthLedger.persist(physicalWorld, birthWorld)));
        } catch (RuntimeException error) {
            startupFailure = error;
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
                FrontierV3CalendarBinding.attachActive(physicalWorld, runtime);
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
                startupFailure = error;
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
        FrontierV3StartupExposure.requireActive(runtime.status(), startupFailure);
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
            long admitted = runtime.canonicalState().orElseThrow().instant().ticks();
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
        long admitted = runtime.canonicalState().orElseThrow().instant().ticks();
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
            recordFastForwardRequest(server, "ABSOLUTE", 0, targetInstant, runtime.canonicalState().orElseThrow().instant().ticks(), null,
                    "REJECTED", "physical work is pending at admission");
            return false;
        }
        long admittedCheckpoint = runtime.canonicalState().orElseThrow().instant().ticks();
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
        Long checkpoint = RUNTIMES.containsKey(server) ? RUNTIMES.get(server).canonicalState().map(image -> image.instant().ticks()).orElse(null) : null;
        if (target == null) {
            // A pilot initial hold protects fixture construction from ordinary ticks before the
            // first real client can establish HOT ownership.  Its release is a control-only
            // hand-off at the current checkpoint, not a synthetic advance; publish the same
            // monotonic receipt shape so the client can prove that hand-off before continuing.
            if (!INITIAL_CANONICAL_HOLDS.containsKey(server) || checkpoint == null) return false;
            var released = nextFastForwardTargetOutcome(prior, checkpoint, checkpoint, checkpoint, "RELEASED", null);
            recordFastForwardRequest(server, "ABSOLUTE", 0, checkpoint, checkpoint, checkpoint, "RELEASED", null);
            FAST_FORWARD_OUTCOMES.put(server, released);
            INITIAL_CANONICAL_HOLDS.remove(server);
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
            runtime.beginPersistenceTurn();
            try {
                boolean active = runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE;
                boolean initialHold = INITIAL_CANONICAL_HOLDS.containsKey(server);
                boolean absoluteTargetPresent = FAST_FORWARD_TARGETS.containsKey(server);
                boolean fastForwardRemaining = FAST_FORWARD_REMAINING.containsKey(server);
                if (active && runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(initialHold, absoluteTargetPresent, fastForwardRemaining)) {
                    runObservedPhysicalTurn(FrontierV3PhysicalWorld.require(server), runtime);
                } else if (active && advancesOnlyQueuedCanonicalTime(fastForwardRemaining)) {
                    // A relative request owns exactly its admitted canonical interval. Advancing
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
            } finally { runtime.endPersistenceTurn(); }
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
    /** Fresh admission checks current owners even when a chunk stayed loaded across demand loss. */
    static boolean newSceneAdmissionReady(ServerLevel level, io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        var runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || !FrontierV3PhysicalWorld.isPhysical(level)) return true;
        return FrontierV3GrayboxExecutor.sceneEligible(runtime, position)
                && FrontierV3HotHandoff.inspect(level, runtime,
                        new net.minecraft.world.level.ChunkPos(position.x() >> 4, position.z() >> 4)).ready();
    }
    /** Before packet collection: hold presentation, not canonical time or chunk loading. */
    public static boolean chunkPresentationReady(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        if (!ownsPhysicalWorld(level.getServer()) || !FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        FrontierV3GrayboxExecutor.observePlayerIngress(runtime, chunk);
        return FrontierV3GrayboxExecutor.staticVisibilityComplete(runtime, chunk)
                && FrontierV3HotHandoff.inspect(level, runtime, chunk).presentable();
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
            io.farfrontier.palemirror.internal.calendar.MinecraftCalendarPresentation.detach(server);
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
        FrontierV3CargoCleanupPersistence.forget(runtime);
        FrontierV3CargoDeparturePersistence.forget(runtime);
        FrontierV3ActorAdoptionPersistence.forget(runtime);
        FrontierV3SceneDeparturePersistence.forget(runtime);
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
                updateFastForwardRequest(server, "ABSOLUTE", runtime.canonicalState().orElseThrow().instant().ticks(), "REJECTED", FAST_FORWARD_FAILURES.get(server));
                PaleMirrorMod.LOGGER.warn("Frontier v3 rejected absolute fast-forward target because physical work became pending: {}", physicalBlocker);
            } else {
                FAST_FORWARD_REMAINING.remove(server);
                FAST_FORWARD_FAILURES.put(server, "physical work became pending during the relative interval: " + physicalBlocker);
                PaleMirrorMod.LOGGER.warn("Frontier v3 rejected relative fast-forward because physical work became pending: {}", physicalBlocker);
                updateFastForwardRequest(server, "RELATIVE", runtime.canonicalState().orElseThrow().instant().ticks(), "REJECTED", FAST_FORWARD_FAILURES.get(server));
            }
            recordFastForwardSlice(server, sliceStarted, safetyNanos, advanceNanos, 0);
            return;
        }
        int allowed = Math.min(remaining, FrontierV3FastForwardSafety.MAX_COLD_INTERVAL_TICKS); int advanced = 0; int steps = 0;
        while (advanced < allowed && steps < FAST_FORWARD_SLICE_TICKS && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            safetyStarted = System.nanoTime();
            physicalStepRequired = requiresPhysicalStep(physicalWorld, runtime);
            safetyNanos += elapsedNanos(safetyStarted);
            if (physicalStepRequired) break;
            long advanceStarted = System.nanoTime();
            long before = runtime.canonicalState().orElseThrow().instant().ticks();
            var result = runtime.advanceColdInterval(allowed - advanced, FrontierV3RuntimeBudgets.fastForwardTick());
            advanceNanos += elapsedNanos(advanceStarted);
            if (result.isEmpty()) break;
            advanced += Math.toIntExact(result.orElseThrow().instant().ticks() - before); steps++;
            if (!fastForwardSliceTimeRemaining(elapsedNanos(sliceStarted))) break;
        }
        int next = remaining - advanced;
        if (next <= 0) {
            FAST_FORWARD_REMAINING.remove(server);
            Long target = FAST_FORWARD_TARGETS.get(server);
            if (target != null) recordFastForwardTarget(server, target, null, runtime.canonicalState().orElseThrow().instant().ticks(), "HELD", null);
            long reached = runtime.canonicalState().orElseThrow().instant().ticks();
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
                FrontierV3ResourceSiteExecutor.hasProjectionInFlight(runtime)
                        || unheldFieldWorldChange(physicalWorld, runtime) != null);
    }
    private static String physicalBlocker(ServerLevel physicalWorld,
                                          FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        String canonical = FrontierV3FastForwardSafety.blockingDescription(physicalWorld, runtime.decodedState().orElseThrow());
        String projection = FrontierV3ResourceSiteExecutor.hasProjectionInFlight(runtime)
                ? FrontierV3ResourceSiteExecutor.projectionBlockingDescription(runtime) : "";
        var unheldWorld = unheldFieldWorldChange(physicalWorld, runtime);
        if (unheldWorld != null) projection += (projection.isBlank() ? "" : ";") + "field-world-change="
                + unheldWorld.siteId().value();
        var unheldForeign = unheldFieldForeignChange(physicalWorld, runtime);
        if (unheldForeign != null) projection += (projection.isBlank() ? "" : ";") + "field-foreign-change="
                + unheldForeign.siteId().value();
        return FrontierV3FastForwardSafety.blockingDescription(canonical, projection);
    }
    private static FrontierV3ResourceFieldWorldChangeWitness unheldFieldWorldChange(
            ServerLevel physicalWorld, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return null;
        return FrontierV3ResourceSiteLedger.get(physicalWorld).pendingFieldWorldChanges().stream()
                .filter(change -> state.resourceSites().pendingWorldChange(change.siteId()) == null)
                .findFirst().orElse(null);
    }
    private static FrontierV3ResourceFieldForeignChangeWitness unheldFieldForeignChange(
            ServerLevel physicalWorld, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return null;
        return FrontierV3ResourceSiteLedger.get(physicalWorld).pendingFieldForeignChanges().stream()
                .filter(change -> state.resourceSites().pendingForeignChange(change.siteId()) == null)
                .findFirst().orElse(null);
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
            FrontierV3FastForwardReceiptValidation.request(requestId, kind, requestedTicks, targetInstant,
                    admittedCheckpointInstant, reachedCheckpointInstant, status, reason);
        }
    }
    record FastForwardTargetOutcome(long requestId, long targetInstant, Long admittedCheckpointInstant, Long reachedCheckpointInstant,
                                    String status, String failure) {
        FastForwardTargetOutcome {
            FrontierV3FastForwardReceiptValidation.target(requestId, targetInstant,
                    admittedCheckpointInstant, reachedCheckpointInstant, status, failure);
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
        return observeSourceJoin(level, runtime, entity);
    } // Both the host and isolated native runtimes use the same real join boundary.
    static JoinFirewallProof observeSourceJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity");
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE)
            return new JoinFirewallProof(EntityJoinAdmission.NOT_MANAGED, false);
        FrontierV3ActorBodyController.observeJoin(level, runtime, entity);
        JoinFirewallProof proof = observeSourceJoin(runtime, entity);
        if (proof.verifiedV3Carrier() && proof.lifecycleAdmission() != EntityJoinAdmission.DUPLICATE_UNINDEXED)
            FrontierV3ActorBodyController.confirmPresent(level, runtime, entity);
        FrontierV3CargoDepartureObserver.observeJoin(level, runtime, entity);
        return proof;
    }
    static JoinFirewallProof observeSourceJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity");
        return composeSourceJoin(() -> observeEntityJoin(runtime, entity),
                () -> entity.level() instanceof ServerLevel level && runtime.decodedState()
                        .map(state -> FrontierV3ActorBodyController.retainsRecordedBody(level, state, entity)
                                || FrontierV3ActorBodyController.recognizesRecordedBody(level, state, entity)).orElse(false));
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
                || runtime.decodedState().map(state -> FrontierV3ActorBodyController.recognizesRecordedBody(level, state, entity)).orElse(false)
                || FrontierV3SceneExecutor.recognizesDeclaration(runtime, entity));
    }
    static boolean recognizesManagedAmbientCarrier(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierV3AmbientCarrierRecognition.ManagedCarrier carrier) {
        return FrontierV3AmbientCarrierRecognition.recognizes(runtime, carrier);
    }
    public static boolean observeLivingDeath(ServerLevel level, Entity entity, Entity source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        return FrontierV3ActorBodyController.observeDeath(level, runtime, entity, source);
    }
    /** Observe final geometry without asking Minecraft to reload an unloading chunk. */
    public static void observeNaturalChunkUnload(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        var runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        runtime.decodedState().ifPresent(state -> FrontierV3ActorBodyController.observeTerrainDeparture(level, state, chunk));
    }

    /** Tracking-end precedes removal for hidden chunks and cannot certify final departure. */
    public static boolean observeFinalChunkDeparture(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            runtime.decodedState().ifPresent(state -> FrontierV3CargoFootprintObserver.observeRemoval(level, state.bootstrap().worldId(), entity,
                    state.fencedRecovery().cargoRetirements().pending().get(entity.getUUID())));
        }
        if (entity.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) return false;
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && (FrontierV3ActorBodyController.observeLeave(level, runtime, entity)
                    || FrontierV3CargoDepartureObserver.observeLeave(level, runtime, entity));
    }

    /** Observes the exact vanilla entity-storage write; never requests a save or a chunk. */
    public static java.util.concurrent.CompletableFuture<Void> writeEntityChunkWithFootprint(ServerLevel level,
            net.minecraft.world.level.ChunkPos chunk, net.minecraft.nbt.CompoundTag data,
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> write) {
        if (FrontierV3PhysicalWorld.isPhysical(level)) {
            try {
                FrontierV3CargoFootprintObserver.beforeWrite(level, chunk, data);
            } catch (java.io.IOException failure) {
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
            FrontierV3EntityWriteEpochs.began(level, chunk);
        }
        return write.get();
    }

    public static void observeEntityChunkWrite(ServerLevel level, net.minecraft.world.level.ChunkPos chunk,
                                               net.minecraft.nbt.CompoundTag data,
                                               java.util.concurrent.CompletableFuture<Void> written) {
        var runtime = RUNTIMES.get(level.getServer());
        if (FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, data, written);
            FrontierV3CargoDeparturePersistence.observeWrite(level, runtime, chunk, data, written);
            FrontierV3ActorAdoptionPersistence.observeWrite(level, runtime, chunk, data, written);
            FrontierV3SceneDeparturePersistence.observeWrite(level, runtime, chunk, data, written);
        }
    }

    public static void completeEntitySavePass(ServerLevel level, boolean complete,
                                              java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> synchronize) {
        var runtime = RUNTIMES.get(level.getServer());
        if (FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, complete, synchronize);
            FrontierV3CargoDeparturePersistence.completeSavePass(level, runtime, complete, synchronize);
            FrontierV3ActorAdoptionPersistence.completeSavePass(level, runtime, complete, synchronize);
            FrontierV3SceneDeparturePersistence.completeSavePass(level, runtime, complete, synchronize);
        }
    }

    /** Retain raw unload observations at the completed chunk-store boundary. */
    public static void persistRawEntityDepartures(ServerLevel level, Supplier<java.util.concurrent.CompletableFuture<Void>> synchronize) {
        var runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var world = state.bootstrap().worldId();
        try { FrontierV3AmbientCarrierLedger.get(level, world).persist(level, world); }
        catch (RuntimeException failure) {
            PaleMirrorMod.LOGGER.error("Scene raw departure publication failed; custody remains unresolved", failure);
        }
        try { FrontierV3CargoDepartureLedger.get(level, world).persist(level, world); }
        catch (RuntimeException failure) {
            PaleMirrorMod.LOGGER.error("Cargo raw departure publication failed; custody remains unresolved", failure);
        }
        // A positive completed store is not global save/adoption/cleanup absence evidence.
        FrontierV3SceneDeparturePersistence.confirmRecordedDepartures(level, runtime, synchronize);
        FrontierV3CargoDeparturePersistence.confirmRecordedDepartures(level, runtime, synchronize);
    }

    public static java.util.concurrent.CompletableFuture<java.util.Optional<net.minecraft.nbt.CompoundTag>> observeEntityChunkRead(
            ServerLevel level, net.minecraft.world.level.ChunkPos chunk,
            java.util.concurrent.CompletableFuture<java.util.Optional<net.minecraft.nbt.CompoundTag>> read) {
        var fenced = FrontierV3DepartureReturnReadFence.observeRead(level, chunk, read);
        var runtime = RUNTIMES.get(level.getServer());
        if (FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3CargoCleanupPersistence.observeRead(level, runtime, chunk, fenced);
        }
        return fenced;
    }
    private static boolean stopping(MinecraftServer server) {
        return STOPPING.containsKey(server);
    }
    /** Validate a supplied canonical owner against the actual server of a physical level. */
    static boolean ownsRuntime(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return RUNTIMES.get(Objects.requireNonNull(level, "physical level").getServer())
                == Objects.requireNonNull(runtime, "canonical runtime");
    }
    public static boolean blocksNativeCropGrowth(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(runtime, level, position);
    }
    /** Routes prepare/confirmed phases; the field owner alone decides whether this is an external change. */
    public static void observeFieldBlockWrite(ServerLevel level, BlockPos position,
                                              net.minecraft.world.level.block.state.BlockState replacement, boolean committed) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime != null) FrontierV3ResourceFieldWorldChangeExecutor.observeBlockWrite(level, runtime, position, replacement, committed);
    }

    /** Cancels only vanilla soil reversion in an active exact managed field footprint. */
    public static boolean blocksNativeSoilReversion(ServerLevel level, BlockPos position) {
        var runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(runtime, level, position);
    }
    /** Observes any soil mutation not covered by the managed-soil protection policy. */
    public static void observeFarmlandReversion(ServerLevel level, BlockPos position,
                                                net.minecraft.world.level.block.state.BlockState previous, Entity entity) {
        var runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null) return;
        var state = runtime.stateForNativeGrowthFence().orElse(null);
        if (state == null) return;
        var cell = new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(position.getX(), position.getY(), position.getZ());
        var site = state.resourceSiteDescriptors().values().stream()
                .filter(candidate -> candidate.soilSlots().contains(cell)).findFirst().orElse(null);
        if (site == null) return;
        // Only actual managed-soil changes reach this branch, never every entity/block tick.
        // The caller is retained explicitly; a null entity is not guessed to be drying.
        String caller = StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> frame.getClassName().equals("net.minecraft.world.level.block.FarmBlock"))
                .map(StackWalker.StackFrame::getMethodName)
                .filter(name -> name.equals("randomTick") || name.equals("tick") || name.equals("fallOn"))
                .findFirst().orElse("unknown"));
        PaleMirrorMod.LOGGER.warn("PMV3_SOIL_CHANGE site={} cell={} gameTime={} caller={} before={} after={} entity={} declared={} position={} fallDistance={} motion={}",
                site.id().value(), cell, level.getGameTime(), caller, previous, level.getBlockState(position),
                entity == null ? "none" : entity.getUUID(),
                entity == null ? "none" : FrontierV3ActorCarrierComposition.declaredBy(entity),
                entity == null ? "none" : entity.position(), entity == null ? "none" : entity.fallDistance,
                entity instanceof net.minecraft.world.entity.Mob mob ? FrontierV3ControlledMobMotion.motionObservation(mob) : "none");
    }
    /** Restores an exact owned crop only when another listener forced native growth past the pre-event fence. */
    public static boolean restoreNativeCropGrowthPostcondition(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null
                && FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(runtime, level, position);
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
    static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtimeFor(MinecraftServer server) {
        return RUNTIMES.get(server);
    }
    public static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, ServerPlayer player, Entity entity) {
        return FrontierV3ServerPhysicalInteractions.releaseCargoCarrier(level, player, entity);
    }
    public static boolean presentObjectBoard(ServerLevel level, ServerPlayer player, Entity entity) {
        return FrontierV3ServerPhysicalInteractions.presentObjectBoard(level, player, entity);
    }
    static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                        Entity entity, java.util.Optional<java.util.UUID> observerPlayerId) {
        return FrontierV3ServerPhysicalInteractions.releaseCargoCarrier(level, runtime, entity, observerPlayerId);
    }
    public enum CargoCarrierInteraction { NOT_MANAGED, RELEASED, REJECTED }
    public static void observeTerminalVehicleDamage(ServerLevel level, Entity entity, DamageSource source) {
        FrontierV3ServerPhysicalInteractions.observeTerminalVehicleDamage(level, entity, source);
    }
    public static boolean isLeasedRoadCargoCarrier(Entity entity) {
        return FrontierV3ServerPhysicalInteractions.isLeasedRoadCargoCarrier(entity);
    }
    static void observeTerminalVehicleDamage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             Entity entity, DamageSource source) {
        FrontierV3ServerPhysicalInteractions.observeTerminalVehicleDamage(level, runtime, entity, source);
    }
    public static ExactCustodyObservation observeExactItemPickup(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        return FrontierV3ServerPhysicalInteractions.observeExactItemPickup(level, player, itemEntity);
    }
    public static ExactCustodyObservation observeExactItemToss(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        return FrontierV3ServerPhysicalInteractions.observeExactItemToss(level, player, itemEntity);
    }
    public enum ExactCustodyObservation { NOT_MANAGED, ACCEPTED, REJECTED }
    public enum PlayerBreakDisposition { UNMANAGED, ACCEPTED, REJECTED }
    public static PlayerBreakDisposition observePlayerBreakPacket(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3ServerPhysicalInteractions.observePlayerBreakPacket(level, position, player);
    }
    public static boolean rejectBlockBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3ServerPhysicalInteractions.rejectBlockBreak(level, position, player);
    }
    public static boolean acceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3ServerPhysicalInteractions.acceptedPlayerBreak(level, position, player);
    }
    public static boolean consumeAcceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3ServerPhysicalInteractions.consumeAcceptedPlayerBreak(level, position, player);
    }
    public static void observePlayerBreakResult(ServerLevel level, BlockPos position, ServerPlayer player) {
        FrontierV3ServerPhysicalInteractions.observePlayerBreakResult(level, position, player);
    }
    public static void clearPlayerBreakDispositions(MinecraftServer server) {
        FrontierV3ServerPhysicalInteractions.clearPlayerBreakDispositions(server);
    }
    public static boolean observeExplosion(ServerLevel level, net.minecraft.world.level.Explosion explosion,
                                           java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        return FrontierV3ServerPhysicalInteractions.observeExplosion(level, explosion, affected, entities);
    }
    static boolean observeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    net.minecraft.world.level.Explosion explosion, java.util.List<BlockPos> affected,
                                    java.util.List<Entity> entities) {
        return FrontierV3ServerPhysicalInteractions.observeExplosion(level, runtime, explosion, affected, entities);
    }
    public enum EntityJoinAdmission { NOT_MANAGED, RETAINED, DUPLICATE_UNINDEXED }
    public record JoinFirewallProof(EntityJoinAdmission lifecycleAdmission, boolean verifiedV3Carrier) {
        public JoinFirewallProof {
            Objects.requireNonNull(lifecycleAdmission, "lifecycle admission");
        }
    }
    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
