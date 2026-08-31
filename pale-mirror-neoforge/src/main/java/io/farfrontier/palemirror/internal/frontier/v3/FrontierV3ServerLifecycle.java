package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
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
import java.util.Map;
import java.util.Objects;

/** Explicit development bridge; V3 has no production activation before the cutover gate. */
public final class FrontierV3ServerLifecycle {
    private static final String ENABLED_PROPERTY = "pale_mirror.frontier_v3.enabled";
    private static final String PILOT_RUN_ID_PROPERTY = "pale_mirror.frontier_v3.pilot.run_id";
    private static final Map<MinecraftServer, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection>> RUNTIMES = new IdentityHashMap<>();
    /** Servers whose world teardown has begun; their entity leaves are not gameplay observations. */
    private static final Map<MinecraftServer, Boolean> STOPPING = new IdentityHashMap<>();
    /** One bounded operator request per server; canonical progress itself remains in the WAL. */
    private static final Map<MinecraftServer, Integer> FAST_FORWARD_REMAINING = new IdentityHashMap<>();
    private static final WorkBudget TICK_BUDGET = new WorkBudget(128, 512);
    public static final int MAX_FAST_FORWARD_TICKS = 24_000;
    private static final int FAST_FORWARD_SLICE_TICKS = 512;

    private FrontierV3ServerLifecycle() { }

    /**
     * Reports the physical-world owner selected at server launch.
     *
     * <p>This is deliberately a launch-mode decision rather than a runtime-health decision.
     * A quarantined v3 world must stay visibly quarantined; it must never permit the frozen v2
     * source graybox to resume writing the same dimension as an implicit fallback.</p>
     */
    public static boolean ownsPhysicalWorld(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return v3LaunchOwnsPhysicalWorld();
    }

    /** Package-visible so the launch-mode exclusion remains directly testable without a server fixture. */
    static boolean v3LaunchOwnsPhysicalWorld() { return enabled(); }

    /**
     * Returns an operator-facing summary of the v3 world selected for this server.
     *
     * <p>In particular, this never delegates to the frozen v2 runtime.  A missing or
     * quarantined v3 runtime remains an explicit v3 failure, rather than becoming a plausible
     * but false zero-valued v2 status report.</p>
     */
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

    /**
     * Emits one bounded, immutable diagnostic document for an operator or external test pilot.
     * This is deliberately a read boundary: no command, schedule, chunk load or WAL write occurs here.
     */
    public static String diagnostic(MinecraftServer server, String view, String id) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(view, "view"); Objects.requireNonNull(id, "id");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (!ownsPhysicalWorld(server) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3DiagnosticJson.unavailableRuntime(view, id);
        }
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        if ("execution".equals(view)) return FrontierV3PhysicalExecutionDiagnostic.render(checkpoint);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        java.util.Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission = java.util.Optional.empty();
        if ("actor".equals(view)) {
            try {
                admission = java.util.Optional.of(FrontierV3AmbientActorExecutor.admissionDiagnostic(
                        FrontierV3PhysicalWorld.require(server), runtime, state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id)));
            } catch (IllegalArgumentException ignored) {
                // The immutable canonical formatter remains the source of the not-found response.
            }
        }
        java.util.Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness = java.util.Optional.empty();
        if ("intent".equals(view)) {
            try {
                harvestReadiness = FrontierV3ResourceSiteHarvestExecutor.readiness(
                        FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(id));
            } catch (IllegalArgumentException ignored) {
                // The immutable canonical formatter remains the source of the not-found response.
            }
        }
        java.util.Optional<FrontierV3SceneExecutor.Readiness> sceneReadiness = java.util.Optional.empty();
        if ("scene".equals(view)) {
            try {
                sceneReadiness = FrontierV3SceneExecutor.readiness(FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id));
            } catch (IllegalArgumentException ignored) {
                // The immutable canonical formatter remains the source of the not-found response.
            }
        }
        java.util.Optional<FrontierV3AmbientActorExecutor.AssemblyReadiness> assemblyReadiness = java.util.Optional.empty();
        if ("operation".equals(view)) {
            try {
                assemblyReadiness = FrontierV3AmbientActorExecutor.assemblyReadiness(FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id));
            } catch (IllegalArgumentException ignored) {
                // The immutable canonical formatter remains the source of the not-found response.
            }
        }
        return FrontierV3DiagnosticJson.render(view, id, checkpoint, state,
                "trace".equals(view) ? FrontierV3DiagnosticTrace.latest(server, id) : java.util.Optional.empty(), admission, harvestReadiness, sceneReadiness, assemblyReadiness);
    }

    /** Package-visible pure formatter, kept testable without a Minecraft server fixture. */
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
        STOPPING.remove(server); FAST_FORWARD_REMAINING.remove(server);
        if (!enabled() || RUNTIMES.containsKey(server)) return;
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        startConfigured(server, initialConfiguration(physicalWorld));
    }

    /**
     * Starts an explicit already-built configuration for a moddev-only source set.
     *
     * <p>The method intentionally accepts no profile/property and has package visibility: the
     * test-only bootstrap must select its catalog entry before this lifecycle begins.  The
     * packaged production mod has no caller in this package, while {@link #start(MinecraftServer)}
     * always uses the normal world bootstrap.</p>
     */
    static void startModDevFixture(MinecraftServer server,
                                   io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(configuration, "configuration");
        STOPPING.remove(server); FAST_FORWARD_REMAINING.remove(server);
        if (!enabled()) throw new IllegalStateException("Frontier v3 fixture bootstrap requires an enabled v3 launch");
        if (RUNTIMES.containsKey(server)) throw new IllegalStateException("Frontier v3 fixture bootstrap must run before the normal lifecycle");
        FrontierV3PhysicalWorld.require(server);
        startConfigured(server, configuration);
    }

    private static void startConfigured(MinecraftServer server,
                                        io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                configuration,
                new FrontierFileStore(server.getWorldPath(LevelResource.ROOT), FrontierWorldRuntimeDefinition.payloadCodecs()), 200);
        RUNTIMES.put(server, runtime);
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            FrontierV3ResourceSiteExecutor.beginRecovery(runtime);
            try {
                int uninspectable = FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, physicalWorld);
                int ambientUnknown = FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime);
                int sceneUnknown = FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime);
                if (uninspectable > 0) {
                    PaleMirrorMod.LOGGER.error("Frontier v3 quarantined {} uninspectable running physical intent(s) after restart", uninspectable);
                }
                if (ambientUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} ambient HOT lease(s) as UNKNOWN pending loaded-world recovery", ambientUnknown);
                }
                if (sceneUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} scene lease(s) as UNKNOWN pending loaded-world recovery", sceneUnknown);
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

    /** Production bootstrap: profile properties never participate in this decision. */
    private static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
    initialConfiguration(ServerLevel physicalWorld) {
        return initialConfiguration(FrontierV3PhysicalWorld.WORLD_ID, physicalWorld.getSeed());
    }

    static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
    initialConfiguration(io.farfrontier.palemirror.frontier.v3.api.WorldId worldId, long seed) {
        return FrontierWorldRuntimeDefinition.configuration(worldId, seed);
    }

    /**
     * Queues one bounded operator fast-forward. It advances the same canonical tick engine in
     * regular server-tick slices and pauses before physical work or a HOT scene could be skipped.
     */
    public static boolean requestFastForward(MinecraftServer server, int ticks) {
        Objects.requireNonNull(server, "server");
        if (ticks < 1 || ticks > MAX_FAST_FORWARD_TICKS || !ownsPhysicalWorld(server) || stopping(server)) return false;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE || FAST_FORWARD_REMAINING.containsKey(server)) return false;
        if (FrontierV3FastForwardSafety.requiresPhysicalStep(FrontierV3PhysicalWorld.require(server), runtime.decodedState().orElseThrow())) return false;
        FAST_FORWARD_REMAINING.put(server, ticks);
        PaleMirrorMod.LOGGER.info("Frontier v3 queued operator fast-forward ticks={}", ticks);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (stopping(server)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null) return;
        try {
            if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
                ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
                FrontierV3PhysicalExecutors.registry().tick(physicalWorld, runtime);
                runtime.tick(TICK_BUDGET);
                advanceQueuedCanonicalTime(server, runtime);
            }
        } catch (RuntimeException error) {
            PaleMirrorMod.LOGGER.error("Frontier v3 server tick failed before quarantine", error);
            runtime.quarantine(error);
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) {
            PaleMirrorMod.LOGGER.error("Frontier v3 development runtime quarantined: {}", runtime.status().detail().orElse("unknown"));
            RUNTIMES.remove(server); FAST_FORWARD_REMAINING.remove(server);
        }
    }

    public static void stop(MinecraftServer server) {
        try {
            FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.remove(server);
            if (runtime != null) {
                FrontierV3GrayboxExecutor.forget(runtime);
                FrontierV3ResourceSiteExecutor.forget(runtime);
                FrontierV3InfectionOverlayExecutor.forget(runtime);
                FrontierV3ObjectBoardExecutor.forget(runtime);
                FrontierV3AmbientActorExecutor.forget(runtime);
                FrontierV3SceneExecutor.forget(runtime);
                FrontierV3SettlementAssaultSceneExecutor.forget(runtime);
                runtime.shutdown();
            }
        } finally {
            FrontierV3DiagnosticTrace.forget(server);
            STOPPING.remove(server); FAST_FORWARD_REMAINING.remove(server);
        }
    }

    private static void advanceQueuedCanonicalTime(MinecraftServer server, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        Integer remaining = FAST_FORWARD_REMAINING.get(server);
        ServerLevel physicalWorld = FrontierV3PhysicalWorld.require(server);
        if (remaining == null || FrontierV3FastForwardSafety.requiresPhysicalStep(physicalWorld, runtime.decodedState().orElseThrow())) return;
        int allowed = Math.min(remaining, FAST_FORWARD_SLICE_TICKS); int advanced = 0;
        while (advanced < allowed && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && !FrontierV3FastForwardSafety.requiresPhysicalStep(physicalWorld, runtime.decodedState().orElseThrow())) {
            if (runtime.advance(1, TICK_BUDGET).isEmpty()) break;
            advanced++;
        }
        int next = remaining - advanced;
        if (next <= 0) {
            FAST_FORWARD_REMAINING.remove(server);
            PaleMirrorMod.LOGGER.info("Frontier v3 completed operator fast-forward");
        } else FAST_FORWARD_REMAINING.put(server, next);
    }

    /** Marks the beginning of orderly shutdown before Minecraft emits entity-unload events. */
    public static void beginStopping(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        STOPPING.put(server, Boolean.TRUE);
    }

    /** Retains an exact restored ambient body until ServerLevel publishes its UUID index. */
    public static boolean observeEntityJoin(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3AmbientActorExecutor.observeJoin(runtime, entity);
    }

    /**
     * Lets the shared Graybox admission boundary admit only an exact active V3
     * ambient or scene carrier. This is a predicate only; EntityJoin observation
     * remains the sole lifecycle mutation path.
     */
    public static boolean recognizesManagedCarrier(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && (FrontierV3AmbientActorExecutor.recognizes(runtime, entity) || FrontierV3SceneExecutor.recognizes(runtime, entity));
    }

    /** Returns true only when this v3 runtime durably accepted the managed HOT death. */
    public static boolean observeLivingDeath(ServerLevel level, Entity entity, Entity source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && (FrontierV3SceneExecutor.observeDeath(runtime, entity, source)
                || FrontierV3AmbientActorExecutor.observeDeath(runtime, entity, source));
    }

    /** Captures an ambient body on normal world departure; false means it is not v3-owned. */
    public static boolean observeEntityLeave(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3AmbientActorExecutor.observeLeave(runtime, entity, stopping(level.getServer()));
    }

    private static boolean stopping(MinecraftServer server) {
        return STOPPING.containsKey(server);
    }

    /** Keeps an exact owned field on the canonical growth clock rather than Vanilla random ticks. */
    public static boolean blocksNativeCropGrowth(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(runtime, level, position);
    }

    /**
     * Durably releases an exact HOT shipment before vanilla opens its chest-minecart UI. The cart
     * remains in the world and each later player/drop/hopper move is observed from its stable
     * world-carrier identity; a rejected release must keep the interaction closed.
     */
    public static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return CargoCarrierInteraction.NOT_MANAGED;
        return releaseCargoCarrier(level, runtime, entity, java.util.Optional.of(player.getUUID()));
    }

    /**
     * Shows the current immutable v3 explanation for one exact owned board.  This is a read-only
     * presentation boundary: it does not create a command, retain an event or alter the board.
     */
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

    /** Makes a carrier physically accountable before a real world effect may destroy it. */
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
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return CargoCarrierInteraction.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical("cargo-carrier-release", checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new CargoCarrierReleased(lease.orElseThrow().id(), io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease.orElseThrow()).cargoId(), entity.getUUID(), observerPlayerId)))
                .orElse(null);
        return result instanceof CommandResult.Accepted ? CargoCarrierInteraction.RELEASED : CargoCarrierInteraction.REJECTED;
    }

    public enum CargoCarrierInteraction { NOT_MANAGED, RELEASED, REJECTED }

    /**
     * Captures a fatal non-explosion vehicle hit before vanilla discards its chest inventory.
     * Explosion detonation has a separate pre-effect bridge and must not create duplicate impact
     * evidence through this general vehicle hook.
     */
    public static void observeTerminalVehicleDamage(ServerLevel level, Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        observeTerminalVehicleDamage(level, runtime, entity, source);
    }

    /**
     * Identifies the narrow physical transport representation whose movement is owned by the
     * scene executor. This is a read-only tag check for the Minecart mixin; canonical ownership
     * is still validated separately before any cargo observation or mutation.
     */
    public static boolean isLeasedRoadCargoCarrier(Entity entity) {
        return entity instanceof MinecartChest
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.LEASE_KEY)
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.CARGO_KEY);
    }

    /** Package-visible so the materialized consequence test uses the same pre-destruction path. */
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

    /** Admits one real world-drop pickup only after its exact custody receipt is durable. */
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

    /** Captures a player toss as a new exact physical carrier before Minecraft releases the item entity. */
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
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return ExactCustodyObservation.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical(phase, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElse(null);
        return result instanceof CommandResult.Accepted ? ExactCustodyObservation.ACCEPTED : ExactCustodyObservation.REJECTED;
    }

    public enum ExactCustodyObservation { NOT_MANAGED, ACCEPTED, REJECTED }

    /** True means the v3-owned break was not durably accepted and Minecraft must not apply it. */
    public static boolean rejectBlockBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position"); Objects.requireNonNull(player, "player");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        String cause = "player:" + player.getUUID();
        if (FrontierV3InfectionOverlayExecutor.observeBlockBreak(runtime, level, position, cause)
                == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.REJECTED) return true;
        if (FrontierV3ResourceSiteExecutor.observeBlockBreak(runtime, level, position, cause)
                == FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED) return true;
        return FrontierV3GrayboxExecutor.observeBlockBreak(runtime, level, position, cause)
                == FrontierV3GrayboxExecutor.BlockBreakObservation.REJECTED;
    }

    /** Routes a real blast either to its active v3 intent or to the ordinary external-effect observer. */
    public static boolean observeExplosion(ServerLevel level, net.minecraft.world.level.Explosion explosion,
                                           java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        return observeExplosion(level, runtime, explosion, affected, entities);
    }

    /** One shared server-thread bridge for the production host and real-world integration proofs. */
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

    /** Retains a released chest cart even if its HOT lease was released by an earlier player interaction. */
    private static void captureCargoCarrierImpact(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  Entity entity, String cause) {
        if (!(entity instanceof MinecartChest)) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) {
            runtime.quarantine(new IllegalStateException(cause + " has no canonical state"));
            return;
        }
        if (!state.inventory().worldCarrierItems().containsKey(entity.getUUID())) return;
        try {
            FrontierV3CargoCarrierImpactLedger.get(level).capture(level.getGameTime(), entity, state);
        } catch (RuntimeException error) {
            runtime.quarantine(error);
        }
    }

    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
