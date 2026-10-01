package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Naturally loaded, exact-worker workshop cycle; Minecraft observes but never owns job progress. */
final class FrontierV3ProductionWorkSceneExecutor {
    private static final double READY_DISTANCE_SQUARED = 2.25D;
    private FrontierV3ProductionWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        return FrontierV3SceneTurnScheduler.run(runtime, state, SceneCauseKind.PRODUCTION_WORK,
                lease -> execute(level, runtime, state, lease), () -> admit(level, runtime, state));
    }

    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Optional<FrontierProductionWorkSceneSupport.Candidate> candidate = FrontierV3SceneTurnScheduler.candidate(
                runtime, SceneCauseKind.PRODUCTION_WORK, FrontierProductionWorkSceneSupport.candidates(state).stream()
                .filter(value -> !ResidentActivityCoordinator.requestsYield(state, value.workerId(),
                        runtime.checkpointImage().orElseThrow().instant().ticks()))
                // A current exact depot custody epoch is physical eligibility, not presentation
                // demand.  It may therefore admit the same retained worker/workshop cycle while
                // the naturally loaded depot is ticking without a nearby player.  Both anchors
                // must still be naturally loaded; this selector never creates a ticket.
                .filter(value -> FrontierV3SceneExecutor.demandSnapshot(level, value.demandPosition()).active()
                        || ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(value.settlementId())))
                .filter(value -> level.hasChunkAt(new net.minecraft.core.BlockPos(value.demandPosition().x(), value.demandPosition().y(), value.demandPosition().z()))
                        && level.hasChunkAt(new net.minecraft.core.BlockPos(value.handoffPosition().x(), value.handoffPosition().y(), value.handoffPosition().z())))
                .toList(), FrontierProductionWorkSceneSupport.Candidate::jobId);
        if (candidate.isEmpty()) return false;
        FrontierProductionWorkSceneSupport.Candidate work = candidate.orElseThrow(); SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) prepare(level, runtime, lease); else handoff(level, runtime, state, lease);
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierProductionWorkSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        SceneLeaseId id = new SceneLeaseId("lease:production-work-" + candidate.jobId().value().substring("job:production-".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new ProductionWorkSceneCause(candidate.jobId()), candidate.handoffPosition(), checkpoint.instant(),
                checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> work(level, runtime, state, lease);
            case DRAINING -> {
                ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
                if (job != null && job.bakeryWork().isPresent()
                        && job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent())
                    FrontierV3BakeryWorkSceneExecutor.drainPending(level, runtime, state, lease, job);
                else FrontierV3SceneExecutor.release(level, runtime, lease);
            }
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }
    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_prepared", lease,
                submit(runtime, "production-work-prepare", lease.id().value(), new ProductionWorkSceneLeasePrepared(lease)));
    }
    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3ActorCarrierFactory.materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry.PRODUCTION_WORK, level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;

        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        FrontierV3DiagnosticTrace.recordProductionScene(level.getServer(), "production_work_hot", lease, job,
                submit(runtime, "production-work-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst(); AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
        Entity entity = level.getEntity(member.entityId());
        if (ambient == null || ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)) return;
        // An unstarted traversal may rebase to this observed body. Once work has progressed,
        // the same hand-off must match its retained station and preserve the topology/cursor;
        // the reducer rejects drift rather than restarting work or teleporting the worker.
        SceneMemberPosition capture = new SceneMemberPosition(member.actorId(), FrontierV3BodyObservation.position(body),
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE)));
        // Integer Minecraft cells are not themselves an observation of standing: a body in the
        // upper portion of that cell may still be rising, falling, or supported by a different
        // physical datum.  Rebasing a canonical production cursor from it would turn an
        // in-flight ambient pose into a false exact surface.  Keep ambient custody until this
        // same body is actually at, and collision-clear on, the one inferred support—no reset,
        // replacement, alternate floor, or desired-state repair is permitted here.
        if (!observedHandoffSurfaceIsCurrent(level, body, capture.body())) return;
        // `handoffPosition` is the durable first route surface used by prepared-scene
        // validation.  The reducer rebases the unstarted traversal from this same observation,
        // so retain the support beneath the captured body as well as the captured body itself.
        SceneLease captured = lease.withHandoffPosition(capture.body().supportingSurface().support())
                .withMemberPositions(Map.of(member.actorId(), capture.body())).withAmbientHandoff(Set.of(member.actorId()));
        CommandResult handoff = submit(runtime, "production-work-handoff", lease.id().value(),
                new ProductionWorkSceneLeaseHandoff(captured, List.of(capture)));
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        FrontierV3DiagnosticTrace.recordProductionScene(level.getServer(), "production_work_handoff", captured, job, handoff);
        // The accepted hand-off owns the same grounded body, not a new physical actor.  Its
        // support was just observed through the exact retained production surface, so a
        // historical vanilla fall counter is no longer an in-flight physical fact.  Clear only
        // at this accepted observation boundary; an unsupported or rejected body retains its
        // ordinary fall outcome.
        if (handoff instanceof CommandResult.Accepted) clearHistoricalFallDistanceAtAcceptedHandoff(level, body, capture.body());
    }
    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        if (job.bakeryWork().isPresent()) {
            SettlementStructure bakery = state.bootstrap().settlements().stream()
                    .filter(settlement -> settlement.id().equals(job.settlementId())).findFirst().orElseThrow()
                    .structures().stream().filter(structure -> structure.id().equals(job.facilityId()))
                    .findFirst().orElseThrow();
            FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, bakery.anchor());
            if (!demand.active() && !FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease)) {
                releaseBakeryBeforeBodyUnloads(level, runtime, lease);
                return;
            }
            // A loaded container retains physical inventory authority, not permission to keep
            // driving a worker through chunks after all observers have left the scene.
            if (!demand.active()) return;
            FrontierV3BakeryWorkSceneExecutor.work(level, runtime, state, lease, job);
            return;
        }
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(job.settlementId()))) {
            drain(level, runtime, lease, DrainReason.DEPOT_CONFLICT); return;
        }
        if (job.workProgress().terminalEffectEligible()) { drain(level, runtime, lease, DrainReason.TERMINAL_EFFECT_READY); return; }
        SettlementStructure workshop = state.bootstrap().settlements().stream().filter(s -> s.id().equals(job.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(s -> s.id().equals(job.facilityId())).findFirst().orElseThrow();
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT || !FrontierProductionWorkSceneSupport.hasPhysicalInput(state, job)) {
            drain(level, runtime, lease, DrainReason.FACILITY_OR_INPUT_UNAVAILABLE); return;
        }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, workshop.anchor());
        boolean operationalCustody = ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(job.settlementId()));
        if (!operationalCustody && FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) {
            drain(level, runtime, lease, DrainReason.DEMAND_HYSTERESIS_WITHOUT_CUSTODY); return;
        }
        if (!demand.active() && !operationalCustody) return;
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) { conflict(level, runtime, lease, "worker-unavailable"); return; }
        List<SurfaceAnchor> route = job.workTraversal().linearCorridorSurfaces(); SurfaceAnchor current = route.get(job.traversalCursor());
        int inputCursor = route.size() - 2;
        // Entity motion runs at the normal entity boundary, while a canonical command is
        // committed afterwards.  The exact body may therefore already be on the one retained
        // next surface when this tick reads the still-old cursor.  That is observed arrival,
        // not a conflict and not permission to skip an edge.  Any other displacement remains
        // a visible mismatch.
        if (!FrontierV3SemanticMovement.arrived(level, worker, current)) {
            if (job.traversalCursor() < route.size() - 1 && FrontierV3SemanticMovement.arrived(level, worker, route.get(job.traversalCursor() + 1))) {
                submit(runtime, "production-work-traversal", lease.id().value(),
                        new ProductionWorkTraversalAdvanced(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, route.get(job.traversalCursor() + 1)), job.traversalCursor() + 1));
            } else if (job.traversalCursor() < route.size() - 1
                    && FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(level, worker, current, route.get(job.traversalCursor() + 1))) {
                // Canonical cursor ownership changes only at an exact next-surface arrival.
                // Vanilla may nevertheless report the admitted body on one intermediate
                // support while it is crossing that same retained edge.  That is neither a
                // new route nor a completed edge; keep pursuing the named next surface rather
                // than turning an ordinary in-flight workshop approach into an off-contract
                // conflict (the same ownership rule used by field harvesting).
                pursueRetainedTraversalEdge(level, worker, current, route.get(job.traversalCursor() + 1));
            } else conflict(level, runtime, lease, "production-work-cursor-" + FrontierV3SemanticMovement.detail(
                    FrontierV3SemanticMovement.at(level, worker, current)));
            return;
        }
        if (job.workProgress().stage() == ProductionWorkProgress.Stage.APPROACH && job.traversalCursor() == inputCursor) {
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            submit(runtime, "production-work-input-ready", lease.id().value(), new ProductionWorkProgressed(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), ProductionWorkProgress.inputReady()));
            return;
        }
        if (job.traversalCursor() < route.size() - 1) {
            SurfaceAnchor next = route.get(job.traversalCursor() + 1);
            if (FrontierV3SemanticMovement.arrived(level, worker, next)) submit(runtime, "production-work-traversal", lease.id().value(), new ProductionWorkTraversalAdvanced(job.id(), lease.id(),
                    FrontierV3SurfaceObservation.observedAt(worker, next), job.traversalCursor() + 1));
            else if (!FrontierV3SemanticMovement.targetIsNavigable(level, worker, next)) blocked(level, runtime, lease, job, worker, current, next);
            else pursueRetainedTraversalEdge(level, worker, current, next);
            return;
        }
        var checkpoint = runtime.checkpointImage().orElseThrow();
        var binding = FrontierV3ContinuationBinding.require(checkpoint, job.id(), "frontier.settlement.production.task.complete");
        if (job.workProgress().stage() == ProductionWorkProgress.Stage.PROCESSING
                && checkpoint.instant().compareTo(binding.dueAt()) < 0) return;
        ProductionWorkProgress next = switch (job.workProgress().stage()) {
            case APPROACH -> ProductionWorkProgress.inputReady();
            case INPUT_READY -> ProductionWorkProgress.processing(0);
            case PROCESSING -> job.workProgress().completedTicks() + 1 == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS
                    ? ProductionWorkProgress.outputReady() : ProductionWorkProgress.processing(job.workProgress().completedTicks() + 1);
            case OUTPUT_READY -> throw new IllegalStateException("terminal work may not continue");
        };
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        FrontierV3CommandSubmission.submitBound(runtime, "production-work-progress", lease.id().value(),
                new ProductionWorkProgressed(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), next), binding);
    }
    /** Close a physically carried item and its scene in one turn before chunk expiry hides the body. */
    private static void releaseBakeryBeforeBodyUnloads(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        CommandResult result = submit(runtime, "bakery-work-draining-no-demand", lease.id().value(),
                new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_work_draining_no_demand", lease, result);
        if (!(result instanceof CommandResult.Accepted)) return;
        FrontierWorldState current = runtime.decodedState().orElseThrow();
        SceneLease draining = current.sceneLeases().get(lease.id());
        if (draining != null && draining.status() == SceneLeaseStatus.DRAINING)
            execute(level, runtime, current, draining);
    }
    /**
     * Keeps one admitted production body inside the fixed neighborhood of its current and next
     * canonical supports when a live body temporarily occupies the direct physical column.
     * This is actuator-only collision latitude: the target remains {@code next}, and only an
     * observation at that target can advance the durable traversal cursor.
     */
    static void pursueRetainedTraversalEdge(ServerLevel level, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        FrontierV3GoalNavigation.pursue(level, worker, FrontierV3GoalNavigation.Goal.station(next,
                new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.around(current.standingBody(), next.standingBody()))));
    }
    /** Tests the exact retained target support and body against loaded physical collision without choosing an alternate edge. */
    static boolean clearNextBody(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return FrontierV3SemanticMovement.targetIsNavigable(level, worker, surface);
    }

    /**
     * The ambient-to-production boundary accepts one physical observation only after its
     * inferred support is both geometrically current and collision-clear for that same body.
     * This is a read-only admission fence, not a pose correction.
     */
    static boolean observedHandoffSurfaceIsCurrent(ServerLevel level, Mob worker, BodyPosition observed) {
        SurfaceAnchor inferredSupport = observed.supportingSurface();
        return FrontierV3SemanticMovement.arrived(level, worker, inferredSupport) && clearNextBody(level, worker, inferredSupport);
    }

    /** Applies the accepted grounded hand-off fact without changing the retained body or route. */
    static boolean clearHistoricalFallDistanceAtAcceptedHandoff(ServerLevel level, Mob worker, BodyPosition observed) {
        if (!observedHandoffSurfaceIsCurrent(level, worker, observed)) return false;
        worker.fallDistance = 0.0F;
        return true;
    }
    /**
     * A scene conflict is deliberately retained rather than repaired.  Keep its exact observed
     * and expected cells in the durable trace so an operator can distinguish an ownership
     * hand-off defect from a genuine loaded-world displacement without treating a generic
     * CONFLICT phase as a cause.
     */
    private static String cursorBodyMismatch(ServerLevel level, int cursor, Mob worker, SurfaceAnchor expected) {
        BlockPos physicalCell = worker.blockPosition();
        BlockPos expectedSupport = new BlockPos(expected.x(), expected.y(), expected.z());
        String physicalCellBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(physicalCell).getBlock()).toString();
        String expectedSupportBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(expectedSupport).getBlock()).toString();
        Vec3 exact = worker.position();
        Vec3 velocity = worker.getDeltaMovement();
        return "cursor-body-mismatch:cursor=" + cursor
                + ":actual=" + worker.getBlockX() + "," + worker.getBlockY() + "," + worker.getBlockZ()
                + ":exact=" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f", exact.x, exact.y, exact.z)
                + ":velocity=" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f", velocity.x, velocity.y, velocity.z)
                + ":on-ground=" + worker.onGround()
                + ":body-cell=" + physicalCellBlock
                + ":expected-support=" + expectedSupportBlock
                + ":expected=" + expected.x() + "," + expected.y() + "," + expected.z();
    }
    /** A production drain is a bounded exact-job disposition; retain its source in the same trace as the release receipt. */
    private static void drain(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, DrainReason reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_draining:" + reason.traceName(), lease,
                submit(runtime, "production-work-draining-" + reason.traceName(), lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
    }

    /**
     * The retained next topology edge is authoritative.  A loaded collision may block it, but
     * cannot choose a detour or silently turn an admitted job into a generic scene release.
     * Keep the one bounded expected support in the causal trace alongside the ordinary command
     * that asks the pure owner to block and drain this exact job.
     */
    private static void blocked(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                ProductionJob job, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        String expected = "next-support-unavailable:" + next.x() + "," + next.y() + "," + next.z();
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_blocked:" + expected, lease,
                submit(runtime, "production-work-route-blocked", lease.id().value(),
                        new ProductionWorkTraversalBlocked(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current),
                                job.traversalCursor() + 1)));
    }

    private enum DrainReason {
        DEPOT_CONFLICT("depot-conflict"), TERMINAL_EFFECT_READY("terminal-effect-ready"),
        FACILITY_OR_INPUT_UNAVAILABLE("facility-or-input-unavailable"),
        DEMAND_HYSTERESIS_WITHOUT_CUSTODY("demand-hysteresis-without-custody");

        private final String traceName;
        DrainReason(String traceName) { this.traceName = traceName; }
        String traceName() { return traceName; }
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_conflict:" + reason, lease,
                submit(runtime, "production-work-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
