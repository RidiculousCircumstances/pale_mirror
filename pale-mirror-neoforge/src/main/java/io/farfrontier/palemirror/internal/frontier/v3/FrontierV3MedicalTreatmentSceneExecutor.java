package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Naturally loaded infirmary treatment for one exact patient and retained local staff.
 *
 * <p>The canonical operation chooses people, building and the exact remedy.  This adapter only
 * transfers already owned ambient bodies or materializes them at their current canonical
 * positions, walks those same bodies to visible infirmary positions, then makes the pre-existing
 * exact-consumption intent eligible.  It never teleports a person, creates a replacement or
 * writes an inventory slot.</p>
 */
final class FrontierV3MedicalTreatmentSceneExecutor {
    private static final double READY_DISTANCE_SQUARED = 2.25D;

    private FrontierV3MedicalTreatmentSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isMedicalTreatment)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierMedicalTreatmentSceneSupport.Candidate> candidate = FrontierV3SceneExecutor.firstDemandedCandidate(
                level, FrontierMedicalTreatmentSceneSupport.candidates(state), FrontierMedicalTreatmentSceneSupport.Candidate::infirmaryAnchor);
        if (candidate.isEmpty()) return false;
        FrontierMedicalTreatmentSceneSupport.Candidate treatment = candidate.orElseThrow();
        SceneLease lease = lease(runtime, treatment);
        if (FrontierSceneAdmission.available(state, treatment.memberPositions().keySet())) {
            submit(runtime, "medical-scene-prepare", lease.id().value(), new MedicalTreatmentSceneLeasePrepared(lease));
        } else {
            handoff(level, runtime, state, lease);
        }
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierMedicalTreatmentSceneSupport.Candidate candidate) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String suffix = candidate.operationId().value().substring("medical:".length());
        SceneLeaseId id = new SceneLeaseId("lease:medical-" + suffix + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new MedicalTreatmentSceneCause(candidate.operationId()), candidate.infirmaryAnchor(),
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members,
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> assemble(level, runtime, state, lease);
            case HOT -> treat(level, runtime, state, lease);
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> recover(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    /** PREPARED owns real bodies but the remedy remains unavailable until all reach the infirmary. */
    private static void assemble(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 FrontierWorldState state, SceneLease lease) {
        if (!FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition())) return;
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        if (!atInfirmary(level, runtime, state, lease)) return;
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "medical_treatment_hot", lease,
                submit(runtime, "medical-scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    private static void treat(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              FrontierWorldState state, SceneLease lease) {
        MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, FrontierSceneBehaviors.medicalTreatment(lease));
        if (!operation.active() || operation.status() == MedicalEvacuationStatus.COMPLETED) {
            submit(runtime, "medical-scene-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, lease.handoffPosition());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) {
            submit(runtime, "medical-scene-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (!demand.active()) return;
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !FrontierV3SceneExecutor.recognizes(runtime, body) || !body.isAlive()) {
                conflict(level, runtime, lease, "hot-body-unavailable"); return;
            }
            if (!member.actorId().equals(operation.patientId()) && level.getGameTime() % 20L == 0L) {
                body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
    }

    /** Recovery has one special pre-effect branch: reassemble the same bodies before allowing a remedy. */
    private static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        if (!FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition()) || lease.recoveryEvidence().isPresent()) return;
        MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, FrontierSceneBehaviors.medicalTreatment(lease));
        if (operation.status() == MedicalEvacuationStatus.PREPARED && allExactBodiesPresent(level, runtime, lease)) {
            submit(runtime, "medical-scene-reassemble", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.PREPARED));
            return;
        }
        FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
    }

    private static boolean atInfirmary(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, SceneLease lease) {
        MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, FrontierSceneBehaviors.medicalTreatment(lease));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure infirmary = settlement.structures().stream().filter(structure -> structure.id().equals(operation.infirmaryId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("medical operation infirmary is absent from its settlement"));
        SettlementInfirmaryTreatmentPort port = SettlementInfirmaryTreatmentPort.forInfirmary(infirmary);
        boolean allPresent = true;
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index); Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !FrontierV3SceneExecutor.recognizes(runtime, body) || !body.isAlive()) return false;
            List<SurfaceAnchor> route = new ArrayList<>(port.arrivalSurfaces()); route.add(port.treatmentSurface(index));
            List<Vec3> targets = new ArrayList<>();
            List<BodyPosition> targetBodies = new ArrayList<>();
            for (SurfaceAnchor surface : route) {
                // Both public approach and facility cells are declared semantic support surfaces.
                // A missing/altered support stays a visible deferral; neither the executor nor a
                // heightmap lookup may choose a substitute column or a hidden level path.
                BlockPos standing = FrontierV3StandingPosition.aboveExactFloor(level, surface.support());
                if (standing == null) return false;
                targets.add(new Vec3(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D));
                targetBodies.add(new BodyPosition(standing.getX(), standing.getY(), standing.getZ()));
            }
            BodyPosition observed = new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
            Vec3 target = targets.get(ObservedTraversalCursor.nextTargetIndex(observed, targetBodies, 0));
            if (body.distanceToSqr(target) > READY_DISTANCE_SQUARED) {
                FrontierV3ControlledMobMotion.moveToward(level, body, target);
                allPresent = false;
            }
        }
        return allPresent;
    }

    /** Transfers only observed ambient Villagers, preserving their UUID and current position. */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != AmbientLeaseStatus.HOT) return;
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)) return;
            BodyPosition observed = new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
            captures.add(new SceneMemberPosition(member.actorId(), observed,
                    new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE))));
        }
        if (!captures.isEmpty()) {
            SceneLease capturedLease = lease.withAmbientHandoff(captures.stream()
                    .map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "medical_treatment_handoff", lease,
                    submit(runtime, "medical-scene-handoff", lease.id().value(), new MedicalTreatmentSceneLeaseHandoff(capturedLease, captures)));
        }
    }

    private static boolean allExactBodiesPresent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        return lease.members().stream().allMatch(member -> {
            Entity entity = level.getEntity(member.entityId());
            return entity instanceof Mob body && body.isAlive() && FrontierV3SceneExecutor.recognizes(runtime, body);
        });
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "medical_treatment_conflict:" + reason, lease,
                submit(runtime, "medical-scene-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
