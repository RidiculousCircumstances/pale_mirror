package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Naturally loaded infirmary treatment for one exact patient and retained local staff.
 *
 * <p>The canonical operation chooses people, building and the exact remedy.  This adapter only
 * references the existing physical bodies or materializes them at their current canonical
 * positions, walks those same bodies to visible infirmary positions, then makes the pre-existing
 * exact-consumption intent eligible.  It never teleports a person, creates a replacement or
 * writes an inventory slot.</p>
 */
final class FrontierV3MedicalTreatmentSceneExecutor {
    private FrontierV3MedicalTreatmentSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.MEDICAL_TREATMENT,
                lease -> execute(level, runtime, state, lease), () -> admit(level, runtime, state));
    }

    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Optional<FrontierMedicalTreatmentSceneSupport.Candidate> candidate = FrontierV3SceneDemand.nextDemandedCandidate(
                level, runtime, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.MEDICAL_TREATMENT, FrontierMedicalTreatmentSceneSupport.candidates(state),
                        FrontierMedicalTreatmentSceneSupport.Candidate::infirmaryAnchor, FrontierMedicalTreatmentSceneSupport.Candidate::operationId);
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
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(state, lease);
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
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease, FrontierV3ActorCarrierComposition.InventoryEntry.MEDICAL_TREATMENT);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        if (!atInfirmary(level, runtime, state, lease)) return;

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
        // Admission callbacks may confirm individual bodies before the complete group is ready.
        state = runtime.decodedState().orElseThrow();
        MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, FrontierSceneBehaviors.medicalTreatment(lease));
        var executions = MedicalExecutionAuthority.current(state, operation).members().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId::actorId,
                        java.util.function.Function.identity()));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure infirmary = settlement.structures().stream().filter(structure -> structure.id().equals(operation.infirmaryId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("medical operation infirmary is absent from its settlement"));
        SettlementInfirmaryTreatmentPort port = SettlementInfirmaryTreatmentPort.forInfirmary(infirmary);
        var stations = FrontierMedicalTreatmentSceneSupport.treatmentStations(state, operation);
        boolean allPresent = true;
        for (var member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !FrontierV3SceneExecutor.recognizes(runtime, body) || !body.isAlive()) return false;
            var station = stations.get(member.actorId());
            var actuation = FrontierV3ActorActuation.capture(state, body, executions.get(member.actorId()),
                    () -> runtime.decodedState().filter(current -> operation.equals(
                            current.humanPopulation().medicalOperations().get(operation.id()))
                            && lease.equals(current.sceneLeases().get(lease.id()))));
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body) || !actuation.current(body)) return false;
            if (!FrontierV3SemanticMovement.arrived(level, body, station)) {
                var hint = new ArrayList<>(port.arrivalSurfaces()); hint.add(station);
                FrontierV3GoalNavigation.pursue(level, body, new FrontierV3GoalNavigation.Goal(List.of(station),
                        TraversalCapability.PEDESTRIAN, new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds()),
                        Optional.empty(), hint), actuation);
                allPresent = false;
            }
        }
        return allPresent;
    }

    /** Capture exact live participants before a new effect; later receipts do not need these bodies. */
    static Optional<FrontierV3ExactConsumptionAuthority.Permission> consumptionAuthority(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent)) return Optional.empty();
        var operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        var group = MedicalExecutionAuthority.current(state, operation);
        var lease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isMedicalTreatment)
                .filter(scope -> scope.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.medicalTreatment(scope).operationId().equals(operation.id())).findFirst().orElseThrow();
        var stations = FrontierMedicalTreatmentSceneSupport.treatmentStations(state, operation);
        var permissions = new ArrayList<java.util.function.BooleanSupplier>();
        for (var execution : group.members()) {
            var entity = level.getEntity(SceneLease.deterministicEntityId(state.bootstrap().worldId(), execution.actorId()));
            if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, body)
                    || !FrontierV3SemanticMovement.arrived(level, body, stations.get(execution.actorId()))) return Optional.empty();
            var actuation = FrontierV3ActorActuation.capture(state, body, execution,
                    () -> runtime.decodedState().filter(current -> lease.equals(current.sceneLeases().get(lease.id()))
                            && current.humanPopulation().medicalOperations().get(operation.id()).active()
                            && group.equals(MedicalExecutionAuthority.current(current,
                                    current.humanPopulation().medicalOperations().get(operation.id())))));
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body) || !actuation.current(body)) return Optional.empty();
            permissions.add(() -> actuation.current(body) && body.isAlive()
                    && FrontierV3SemanticMovement.arrived(level, body, stations.get(execution.actorId())));
        }
        return Optional.of(() -> permissions.stream().allMatch(java.util.function.BooleanSupplier::getAsBoolean));
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
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) return;
            BodyPosition observed = FrontierV3BodyObservation.position(body);
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
