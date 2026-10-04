package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExpeditionMarchIssue;
import io.farfrontier.palemirror.frontier.v3.model.ExpeditionMarchIssueKind;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultFormationObserved;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultMarchIssueObserved;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.TacticalPlanPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Loaded-chunk executor for the cargo-free, typed settlement-assault scene.
 *
 * <p>It deliberately has no route-operation or carrier path. The only state it may create is a
 * typed lease whose exact floor map was compiled by the canonical assault process. Minecraft
 * then supplies movement, melee and death; the existing durable strike/death boundaries retain
 * the resulting canonical consequences.</p>
 */
final class FrontierV3SettlementAssaultSceneExecutor {
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    private static final int MAX_RECOVERY_INSPECTIONS = 4_096;
    private static final long RECOVERY_INSPECTION_WINDOW_TICKS = 400L;
    /** Volatile inspection start only; canonical evidence is emitted after the bounded window. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> RECOVERY_INSPECTION_STARTED = new IdentityHashMap<>();

    private FrontierV3SettlementAssaultSceneExecutor() { }

    /** @return true when a typed assault scene owns this materialization turn. */
    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return tick(runtime, new Turn() {
            @Override public boolean demanded(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
                return FrontierV3SceneExecutor.demandExists(level, position);
            }

            @Override public void execute(FrontierWorldState state, SceneLease lease) {
                FrontierV3SettlementAssaultSceneExecutor.execute(level, runtime, state, lease);
            }

            @Override public void prepareMarch(SceneLease lease) {
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "expedition_march_prepared", lease,
                        submit(runtime, "expedition-march-prepare", new SettlementAssaultSceneLeasePrepared(lease)));
            }

            @Override public void prepare(SettlementAssaultSceneCandidate candidate,
                                          FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
                FrontierV3SettlementAssaultSceneExecutor.prepare(level, runtime, candidate, provider, lease);
            }

            @Override public void handoff(FrontierWorldState state, FrontierSettlementAssaultBattlefield.Provider provider,
                                          SceneLease lease) {
                FrontierV3SettlementAssaultSceneExecutor.handoff(level, runtime, state, provider, lease);
            }
        });
    }

    /**
     * The executable composition shared by the registered {@link #tick(ServerLevel, FrontierV3ServerRuntime)}
     * turn. Package scope permits a faithful no-Minecraft-body regression without an alternate
     * candidate/provider path.
     */
    static boolean tick(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Turn turn) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        forgetInactive(runtime, state);
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.SETTLEMENT_ASSAULT,
                lease -> turn.execute(state, lease), () -> admit(runtime, state, turn::demanded, (battle, lease, provider) -> {
                    if (FrontierSceneAdmission.available(state, battle.memberPositions().keySet())) turn.prepare(battle, provider, lease);
                    else turn.handoff(state, provider, lease);
                }, turn::prepareMarch));
    }

    interface Turn {
        boolean demanded(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position);
        void execute(FrontierWorldState state, SceneLease lease);
        void prepareMarch(SceneLease lease);
        void prepare(SettlementAssaultSceneCandidate candidate, FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease);
        void handoff(FrontierWorldState state, FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease);
    }

    /**
     * Complete scene-admission composition: projection-owned candidate selection and the
     * resulting prepare/handoff command share one no-derivation boundary.  A missing or stale
     * cursor has no default-provider escape hatch, and an accidental reducer fallback fails
     * closed before it can make the server tick compile global geometry.
     */
    static boolean admit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                         Predicate<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> demanded,
                         CandidateAdmission admission, java.util.function.Consumer<SceneLease> marchAdmission) {
        return FrontierGrayboxPlan.withoutStructuralDerivation(() -> {
            List<AssaultAdmission> candidates = new ArrayList<>();
            state.strategicPlans().settlementAssaults().values().stream()
                    .map(assault -> FrontierSettlementAssaultSceneSupport.marchCandidate(state, assault))
                    .flatMap(Optional::stream).filter(value -> !hasRetainedScene(state, value.assaultId()))
                    .filter(value -> demanded.test(value.handoffPosition()))
                    .filter(value -> FrontierSceneAdmission.available(state, value.memberPositions().keySet()))
                    .forEach(value -> candidates.add(new AssaultAdmission(value.assaultId(),
                            () -> marchAdmission.accept(lease(runtime, value)))));
            FrontierSettlementAssaultBattlefield.Provider provider = FrontierV3GrayboxExecutor.admissionProvider(runtime, state).orElse(null);
            if (provider != null) {
                FrontierSceneAdmission.settlementAssaultCandidates(state, ignored -> Optional.of(provider)).stream()
                        .filter(value -> !hasRetainedScene(state, value.assaultId()))
                        .filter(value -> demanded.test(value.handoffPosition()))
                        .forEach(value -> candidates.add(new AssaultAdmission(value.assaultId(),
                                () -> admission.admit(value, lease(runtime, value), provider))));
            }
            var selected = FrontierV3SceneTurnScheduler.candidate(runtime,
                    io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.SETTLEMENT_ASSAULT, candidates, AssaultAdmission::id);
            if (selected.isEmpty()) return false;
            selected.orElseThrow().admit().run();
            return true;
        });
    }

    private record AssaultAdmission(SubjectId id, Runnable admit) { }

    private static boolean hasRetainedScene(FrontierWorldState state, SubjectId assaultId) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isSettlementAssault)
                .anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                        && FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(assaultId));
    }

    @FunctionalInterface
    interface CandidateAdmission {
        void admit(SettlementAssaultSceneCandidate candidate, SceneLease lease, FrontierSettlementAssaultBattlefield.Provider provider);
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SettlementAssaultSceneCandidate candidate) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String suffix = candidate.assaultId().value().substring("assault:".length());
        SceneLeaseId id = new SceneLeaseId("lease:assault-" + suffix + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
    }

    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                SettlementAssaultSceneCandidate candidate, FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
        submitPrepared(candidate, provider, lease, () -> FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_prepared", lease,
                submit(runtime, "settlement-assault-prepare", new SettlementAssaultSceneLeasePrepared(lease))));
    }

    /** Claims only an existing exact ambient body; a partial hand-off waits rather than cloning it. */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            var ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != AmbientLeaseStatus.HOT) return;
            Entity body = level.getEntity(member.entityId());
            if (!(body instanceof Mob mob) || !mob.isAlive()
                    || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), bioform(state, member.actorId()))) return;
            captures.add(new SceneMemberPosition(member.actorId(), at(body), fixed(mob.getHealth())));
        }
        if (captures.isEmpty()) return;
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>(lease.memberBodies(state.actorLocations()));
        captures.forEach(capture -> positions.put(capture.actorId(),
                capture.body()));
        SceneLease handed = lease.withAmbientHandoff(captures.stream()
                .map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()));
        submitHandoff(provider, handed, positions, () -> FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_handoff", handed,
                submit(runtime, "settlement-assault-handoff", new SettlementAssaultSceneLeaseHandoff(handed, captures))));
    }

    /**
     * A PREPARED command has not observed movement yet: it must be byte-for-byte the selected
     * provider-approved candidate, not merely a locally clear collection of canonical bodies.
     */
    static boolean providerAuthorizesPreparedLease(SettlementAssaultSceneCandidate candidate,
                                                   FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease) {
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet())
                .equals(candidate.memberPositions().keySet())) return false;
        return providerAuthorizesHandoffLease(provider, lease, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()));
    }

    /** A captured HOT body may move, but its final submitted support still needs this provider. */
    static boolean providerAuthorizesHandoffLease(FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease,
                                                  Map<SubjectId, BodyPosition> observedBodies) {
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet())
                .equals(observedBodies.keySet())) return false;
        return lease.members().stream().allMatch(member -> FrontierSettlementAssaultBattlefield.providerAuthorizesFloor(provider,
                observedBodies.get(member.actorId()).supportingSurface().support()));
    }

    /** Final production authority boundary for a newly selected scene lease. */
    static boolean submitPrepared(SettlementAssaultSceneCandidate candidate, FrontierSettlementAssaultBattlefield.Provider provider,
                                  SceneLease lease, Runnable acceptedSubmit) {
        if (!providerAuthorizesPreparedLease(candidate, provider, lease)) return false;
        acceptedSubmit.run();
        return true;
    }

    /** Final production authority boundary after the moving ambient bodies are captured. */
    static boolean submitHandoff(FrontierSettlementAssaultBattlefield.Provider provider, SceneLease lease,
                                  Map<SubjectId, BodyPosition> observedBodies, Runnable acceptedSubmit) {
        if (!providerAuthorizesHandoffLease(provider, lease, observedBodies)) return false;
        acceptedSubmit.run();
        return true;
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(state, lease);
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, state, lease);
            case HOT -> executeHot(level, runtime, state, lease);
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void materializePrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease, FrontierV3ActorCarrierComposition.InventoryEntry.SETTLEMENT_ASSAULT);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_hot", lease,
                    submit(runtime, "settlement-assault-hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        } else if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) conflict(level, runtime, lease, "prepared-body-conflict");
    }

    private static void executeHot(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, SceneLease lease) {
        SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, FrontierSceneBehaviors.settlementAssault(lease));
        if (assault.tacticalPlan().phase() == TacticalPlanPhase.TRAVEL) { executeMarch(level, runtime, state, lease, assault); return; }
        if (assault.tacticalPlan().phase() == TacticalPlanPhase.RETREAT) {
            submit(runtime, "expedition-retreat-draining", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, lease.handoffPosition());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand, playerWithinSafeRadius(level, lease))) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_draining", lease,
                    submit(runtime, "settlement-assault-draining", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
            return;
        }
        if (!demand.active()) return;
        List<Body> bodies = bodies(level, runtime, state, lease);
        if (bodies.size() != lease.members().size()) {
            conflict(level, runtime, lease, "hot-body-unavailable");
            return;
        }
        var executions = io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state, assault);
        for (Body actor : bodies) FrontierV3GoalNavigation.pursueLocalFeetTarget(level, actor.mob(),
                target(state, lease, actor, bodies), new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds()),
                FrontierV3ActorActuation.capture(state, actor.mob(), executions.requireMember(actor.member().actorId()), runtime::decodedState));
        if (confirmedStrikeForThisLease(state, lease)) {
            // One HOT lease owns one exact COLD epoch.  Its durable receipt remains visible
            // across restart while ordinary demand loss decides when the completed lease drains;
            // only the next COLD admission may select the following epoch.

            return;
        }
        if (level.getGameTime() % 20L == 0L) {
            FrontierV3SceneExecutor.executeStrike(level, runtime, state, lease,
                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        }

    }

    private static void executeMarch(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, SceneLease lease, SettlementAssault assault) {
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, lease.handoffPosition());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand, playerWithinSafeRadius(level, lease))) {
            submit(runtime, "expedition-march-draining", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)); return;
        }
        if (assault.march().complete()) {
            // The last observed formation edge is durable.  Drain this travel-only lease even
            // while demand remains, so its ordinary release returns the same operation to COLD
            // contact admission instead of leaving a completed approach permanently HOT.
            submit(runtime, "expedition-march-complete", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (!demand.active()) return;
        var blocked = assault.march().memberTopologies().entrySet().stream()
                .filter(entry -> assault.march().cursor() < entry.getValue().edges().size()
                        && !entry.getValue().edgeAfterCursor(assault.march().cursor()).traversableBy(
                        io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.GROUND_BIOFORM))
                .findFirst();
        if (blocked.isPresent()) {
            marchIssue(level, runtime, lease, assault, blocked.orElseThrow().getKey(), ExpeditionMarchIssueKind.BLOCKED_EDGE);
            return;
        }
        Map<SubjectId, BodyPosition> targets;
        try { targets = assault.nextFormationBodies(); } catch (IllegalArgumentException invalid) { marchIssue(level, runtime, lease, assault,
                assault.overseerId(), ExpeditionMarchIssueKind.BLOCKED_EDGE); return; }
        boolean arrived = true;
        Set<java.util.UUID> members = lease.members().stream().map(SceneMember::entityId).collect(java.util.stream.Collectors.toSet());
        var executions = io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state, assault);
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId()); BodyPosition target = targets.get(member.actorId());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || target == null) { marchIssue(level, runtime, lease, assault,
                    member.actorId(), ExpeditionMarchIssueKind.MISSING_OWNED_BODY); return; }
            if (!FrontierV3SurfaceObservation.at(mob, target.supportingSurface())) {
                arrived = false;
                var bounds = mob.getBoundingBox().move(FrontierV3SurfaceObservation.point(target.supportingSurface()).subtract(mob.position()));
                if (level.getBlockCollisions(mob, bounds).iterator().hasNext() || !level.getEntities(mob, bounds, value -> !members.contains(value.getUUID())).isEmpty()) {
                    marchIssue(level, runtime, lease, assault, member.actorId(), ExpeditionMarchIssueKind.OCCUPIED_NEXT_BODY); return;
                }
                FrontierV3GoalNavigation.pursue(level, mob, FrontierV3GoalNavigation.Goal.station(target.supportingSurface(),
                        new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds())),
                        FrontierV3ActorActuation.capture(state, mob, executions.requireMember(member.actorId()), runtime::decodedState));
            }
        }
        if (arrived) {
            CommandResult result = submit(runtime, "expedition-march-observed", new SettlementAssaultFormationObserved(assault.id(), lease.id(), targets,
                    io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state, assault)));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "expedition_march_formation", lease, result);
        }

    }

    /** The durable ID is an exact lease association; a shared cause alone cannot fence a replay. */
    private static boolean confirmedStrikeForThisLease(FrontierWorldState state, SceneLease lease) {
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        if (cause == null) return false;
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.status() == PhysicalIntentStatus.CONFIRMED
                && SettlementAssaultCauseIdentity.belongsTo(cause.assaultId(), intent.causeSubjectId())
                && FrontierV3SettlementAssaultReceiptBinding.belongsToLease(state, lease, intent));
    }

    private static void reclaim(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        if (!FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition()) || lease.recoveryEvidence().isPresent()) return;
        if (completeOwnedBodySet(level, runtime, state, lease)) {
            forgetRecoveryInspection(runtime, lease.id());
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_reclaimed", lease,
                    submit(runtime, "settlement-assault-reclaimed", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
            return;
        }
        long started = recoveryInspectionStarted(runtime, lease.id(), level.getGameTime());
        if (level.getGameTime() - started < RECOVERY_INSPECTION_WINDOW_TICKS) return;
        Set<SubjectId> missing = lease.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(member -> !owns(runtime, level.getEntity(member.entityId()), member)).map(SceneMember::actorId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!missing.isEmpty()) {
            submit(runtime, "settlement-assault-recovery-unresolved", new SceneLeaseRecoveryUnresolved(lease.id(), missing, false));
            return;
        }
    }

    private static boolean completeOwnedBodySet(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                FrontierWorldState state, SceneLease lease) {
        return lease.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .allMatch(member -> owns(runtime, level.getEntity(member.entityId()), member));
    }

    private static List<Body> bodies(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, SceneLease lease) {
        return lease.members().stream().map(member -> {
            Entity entity = level.getEntity(member.entityId());
            return entity instanceof Mob mob && mob.isAlive() && owns(runtime, mob, member)
                    ? new Body(member, mob, bioform(state, member.actorId())) : null;
        }).filter(java.util.Objects::nonNull).sorted(Comparator.comparing(value -> value.member().actorId())).toList();
    }

    /**
     * The two actors named by the current canonical epoch must approach one another.  Choosing
     * merely a nearest opponent lets the named defender retreat forever while a different
     * resident remains locally reachable, so the physical executor can never submit the exact
     * durable strike it is required to confirm.
     */
    private static Vec3 target(FrontierWorldState state, SceneLease lease, Body actor, List<Body> bodies) {
        StrikePair pair = currentStrikePair(state, lease).orElse(null);
        if (pair != null) {
            SubjectId opponent = actor.member().actorId().equals(pair.attackerId()) ? pair.targetId()
                    : actor.member().actorId().equals(pair.targetId()) ? pair.attackerId() : null;
            if (opponent != null) return bodies.stream().filter(value -> value.member().actorId().equals(opponent)).findFirst()
                    .map(value -> value.mob().position()).orElse(actor.mob().position());
        }
        Body opponent = bodies.stream().filter(value -> value.bioform() != actor.bioform()).min(Comparator
                .comparingDouble((Body value) -> actor.mob().distanceToSqr(value.mob())).thenComparing(value -> value.member().actorId())).orElse(null);
        if (opponent == null) return actor.mob().position();
        Vec3 delta = opponent.mob().position().subtract(actor.mob().position());
        if (actor.bioform() || residentGuard(state, actor.member().actorId())) return opponent.mob().position();
        return delta.horizontalDistanceSqr() > 0.0001D ? actor.mob().position().subtract(delta.normalize().scale(5.0D)) : actor.mob().position();
    }

    private static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
        return state.humanPopulation().resident(actorId) != null && state.humanPopulation().resident(actorId).role()
                == io.farfrontier.palemirror.frontier.v3.model.ResidentRole.GUARD;
    }

    /** One exact HOT pair shared by local motion and the physical-intent executor. */
    static Optional<StrikePair> currentStrikePair(FrontierWorldState state, SceneLease lease) {
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) return Optional.empty();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
        return assault == null ? Optional.empty() : currentStrikePair(assault,
                SettlementAssaultCauseIdentity.hotEpoch(assault, state.physicalIntents().values()));
    }

    /** Pure canonical selection: epoch parity changes the attacking side, not the pair identity. */
    static Optional<StrikePair> currentStrikePair(SettlementAssault assault, long epoch) {
        List<SubjectId> attackers = ((epoch & 1L) == 0L ? assault.combatantAttackerIds() : assault.defenderIds()).stream().sorted().toList();
        List<SubjectId> targets = ((epoch & 1L) == 0L ? assault.defenderIds() : assault.combatantAttackerIds()).stream().sorted().toList();
        if (attackers.isEmpty() || targets.isEmpty()) return Optional.empty();
        return Optional.of(new StrikePair(attackers.get(Math.floorMod(epoch, attackers.size())), targets.get(Math.floorMod(epoch, targets.size()))));
    }

    record StrikePair(SubjectId attackerId, SubjectId targetId) { }

    private static boolean playerWithinSafeRadius(ServerLevel level, SceneLease lease) {
        List<BlockPos> positions = new ArrayList<>(List.of(new BlockPos(lease.handoffPosition().x(), lease.handoffPosition().y(), lease.handoffPosition().z())));
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (entity != null) positions.add(entity.blockPosition());
        }
        return FrontierV3SceneDemand.observerWithin(level, positions, DRAIN_SAFE_RADIUS_BLOCKS);
    }

    private static boolean owns(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, SceneMember member) {
        return entity != null && member.entityId().equals(entity.getUUID()) && FrontierV3SceneExecutor.recognizes(runtime, entity);
    }

    private static boolean bioform(FrontierWorldState state, SubjectId actor) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .anyMatch(value -> value.id().equals(actor));
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static void forgetInactive(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.get(runtime);
        if (inspections == null) return;
        inspections.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || !isAssault(lease) || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART;
        });
        if (inspections.isEmpty()) RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static long recoveryInspectionStarted(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime) {
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        Long started = inspections.get(leaseId);
        if (started != null) return started;
        if (inspections.size() >= MAX_RECOVERY_INSPECTIONS) return gameTime;
        inspections.put(leaseId, gameTime);
        return gameTime;
    }

    private static void forgetRecoveryInspection(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.get(runtime);
        if (inspections == null) return;
        inspections.remove(leaseId);
        if (inspections.isEmpty()) RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_conflict:" + reason, lease,
                submit(runtime, "settlement-assault-conflict", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }

    private static void marchIssue(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                   SettlementAssault assault, SubjectId member, ExpeditionMarchIssueKind kind) {
        ExpeditionMarchIssue issue = new ExpeditionMarchIssue(kind, member, edgeAtCursor(assault, member), assault.march().cursor());
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "expedition_march_" + kind.name().toLowerCase(java.util.Locale.ROOT), lease,
                submit(runtime, "expedition-march-issue", new SettlementAssaultMarchIssueObserved(assault.id(), lease.id(), issue, io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state(runtime), assault))));
    }

    private static io.farfrontier.palemirror.frontier.v3.model.TraversalEdgeId edgeAtCursor(SettlementAssault assault, SubjectId member) {
        var matching = assault.march().memberTopologies().get(member);
        if (matching != null && assault.march().cursor() < matching.edges().size()) {
            return matching.edgeAfterCursor(assault.march().cursor()).id();
        }
        return assault.march().memberTopologies().values().stream()
                .filter(topology -> assault.march().cursor() < topology.edges().size())
                .findFirst().orElseThrow().edgeAfterCursor(assault.march().cursor()).id();
    }

    private static BodyPosition at(Entity entity) { return FrontierV3BodyObservation.position(entity); }
    private static FixedScalar fixed(float health) { return new FixedScalar(Math.max(0L, Math.round(health * FixedScalar.SCALE))); }
    private static boolean isAssault(SceneLease lease) { return io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isSettlementAssault(lease); }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, "settlement-assault", payload);
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { return runtime.decodedState().orElse(null); }
    private record Body(SceneMember member, Mob mob, boolean bioform) { }
}
