package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.model.AmbientPlacementPolicy;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseRestartAbsenceObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMeal;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMealHotHandReleased;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentTransitAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.HivePhysiologySupport;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody;
import io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssembly;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferral;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferred;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAccessPort;
import io.farfrontier.palemirror.frontier.v3.model.SettlementStructure;
import io.farfrontier.palemirror.frontier.v3.model.StructureKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
final class FrontierV3AmbientActorExecutor {
    static final String ACTOR_KEY = FrontierV3ActorCarrierComposition.ACTOR_KEY;
    static final String KIND_KEY = FrontierV3ActorCarrierComposition.KIND_KEY;
    static final String CUSTODY_EPOCH_KEY = FrontierV3ActorCarrierComposition.EPOCH_KEY;
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    static final long DRAIN_HYSTERESIS_TICKS = 200L;
    private static final int GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS = Integer.MAX_VALUE;
    private static final int MAX_ACTOR_PROBES_PER_TICK = 32;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Long>> COLD_DEMAND_SINCE = new IdentityHashMap<>();
    private FrontierV3AmbientActorExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3AmbientPendingAdmissions.clean(runtime);
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, state);
        state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3AmbientAdmissionPolicy.Session admissionPolicy = admissionPolicy(runtime, state);
        FrontierWorldState handoffState = state;
        FrontierV3AmbientActorReservationHandoff.run(level, runtime, handoffState, admissionPolicy,
                actorId -> FrontierV3AmbientPendingAdmissions.get(runtime, entityId(handoffState, actorId)) == null);
        state = runtime.decodedState().orElse(null);
        if (state == null) return;
        int admitted = 0;
        for (SubjectId actorId : FrontierV3ActorProbeSchedule.next(runtime, state, level, MAX_ACTOR_PROBES_PER_TICK,
                FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK)) {
            if (admitted >= FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK) return;
            state = runtime.decodedState().orElse(null);
            if (state == null) return;
            FrontierV3ActorBodyController.cleanRetired(level, state, actorId);
            FrontierV3ActorBodyController.progressDeparture(level, runtime, state, actorId);
            state = runtime.decodedState().orElse(null);
            if (state == null) return;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            if (FrontierV3AmbientPendingAdmissions.get(runtime, entityId(state, actorId)) != null) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            if (!HivePhysiologySupport.permitsAmbientLease(state, actorId)) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            var lease = state.ambientLeases().get(actorId);
            if (lease != null && (lease.status() == AmbientLeaseStatus.HOT || lease.status() == AmbientLeaseStatus.DRAINING
                    || lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)) {
                var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
                if (ledger.hasBodyDeparture(actorId)) {
                    Entity returned = level.getEntity(entityId(state, actorId));
                    if (returned != null) FrontierV3ActorBodyController.observeJoin(level, runtime, returned);
                    if (ledger.hasBodyDeparture(actorId)) {
                        if (releaseUnloadedReservedColdContinuation(level, runtime, state, actorId, lease)) admitted++;
                    // A returned mismatching body or unresolved witness must never resume a
                    // physical writer. Exact matching returns clear the witness at join.
                        continue;
                    }
                }
            }
            boolean successorSceneOwnsActor = state.sceneLeases().values().stream()
                    .filter(scene -> scene.status() != SceneLeaseStatus.CLOSED)
                    .anyMatch(scene -> scene.members().stream().anyMatch(member -> member.actorId().equals(actorId)));
            if (successorSceneOwnsActor) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            if (lease != null && lease.status() == AmbientLeaseStatus.DRAINING) {
                Entity body = level.getEntity(entityId(state, actorId));
                if (body instanceof Mob mob && release(runtime, mob).isPresent()) admitted++;
                // A draining owner cannot return to motion or prepare another authority.
                continue;
            }
            // A restart-unknown lease remains the exact physical owner until its declared
            // recovery owner has inspected the naturally loaded hand-off anchor.  In
            // particular, a retained harvest candidate reserves this farmer for its later
            // pre-lease scene hand-off; letting that reservation bypass recovery used to leave
            // the actor both canonically idle and indefinitely unavailable.  Settle the old
            // owner first, then let the next ordinary turn prepare/adopt the same farmer.
            if (lease != null && lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
                Entity body = level.getEntity(entityId(state, actorId));
                // Recovery is recognition, not an instruction to end this scope.
                // Saved departures have their independent body-owner protocol above;
                // an indexed survivor must not be drained merely because it returned.
                if (body != null && FrontierV3ActorBodyController.readyForExecution(level, state, List.of(
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId)))
                        && FrontierV3AmbientCarrierRecognition.recoverableOwnership(state,
                        FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(body),
                        FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()))) {
                    submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
                    admitted++;
                } else if (body == null && restartAbsenceIsObserved(level, runtime, state, actorId, lease)) {
                    var result = submit(runtime, "ambient-restart-absence", actorId.value(),
                            new AmbientLeaseRestartAbsenceObserved(actorId, lease.handoffBody()));
                    if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)
                        FrontierV3ActorBodyCustody.releaseUnstartedAbsence(level, runtime, actorId);
                    FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.ambientLeaseRecoveryCorrelation(actorId),
                            "ambient_restart_absence_observed", actorId, result);
                    admitted++;
                }
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            FrontierV3AmbientAdmissionPolicy.Decision handoff = FrontierV3AmbientActorReservationHandoff.runOne(
                    level, runtime, state, actorId, admissionPolicy);
            if (handoff.reserved()) {
                if (handoff.applied()) admitted++;
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            FrontierSceneAdmission.GenericAmbientAdmission genericAdmission = genericAmbientAdmission(runtime, state);
            if (genericAdmission.reserves(actorId)) {
                Optional<FrontierV3SceneBehaviorRegistry.StandingPositionProvider> preLeaseStanding = genericAdmission.preLeaseSceneCause(actorId)
                        .map(FrontierV3SceneBehaviorRegistry::preLeaseStandingPositionProvider);
                if (FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(preLeaseStanding.isPresent(),
                        genericAdmission.preLeaseDemandAnchor(actorId).map(anchor -> demand(level, anchor)).orElse(false))) {
                    if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                        submit(runtime, "ambient-pre-lease-prepare", actorId.value(),
                                new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId,
                                        runtime.canonicalState().orElseThrow().instant())));
                        admitted++;
                    } else if (lease.status() == AmbientLeaseStatus.PREPARED) {
                        Result result = materialize(level, runtime, state, actorId, lease.handoffBody(), preLeaseStanding.orElseThrow());
                        if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                            if (result != Result.PENDING) FrontierV3AmbientHotAdmission.confirmForHandoff(level, runtime, actorId);
                            admitted++;
                        }
                    } else if (lease.status() == AmbientLeaseStatus.HOT) {
                        Entity body = level.getEntity(entityId(state, actorId));
                        if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))) {
                            holdForPreLeaseHandoff(mob);
                        }
                    }
                } else if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))
                            && drainReservedColdContinuation(runtime, state, actorId, mob, lease)) admitted++;
                    // An unloaded serialized body is not a live physical owner.  Leaving its
                    // HOT lease open nevertheless made the same retained COLD production
                    // cursor reschedule forever: the production planner correctly refused to
                    // advance while an ambient owner existed, but this adapter had no loaded
                    // body from which to drain it.  Close only the exact reserved hand-off
                    // after its chunk is genuinely unloaded; a loaded missing/foreign body is
                    // deliberately not treated as a release observation.
                    else if (body == null && !level.hasChunkAt(lease.handoffBody().supportingSurface().support().x(),
                            lease.handoffBody().supportingSurface().support().z())
                            && releaseUnloadedReservedColdContinuation(level, runtime, state, actorId, lease)) admitted++;
                } else if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED
                        && abandonPreparedForReservation(level, runtime, state, actorId, lease).isPresent()) {
                    admitted++;
                }
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            long projectionTick = runtime.canonicalState().orElseThrow().instant().ticks(); BodyPosition projectedBody = state.actorMovements().containsKey(actorId) ? ActorMovementProcess.bodyAt(state, actorId, projectionTick)
                    : ResidentMealProcess.bodyAt(state, actorId, projectionTick);
            boolean demanded = demand(level, projectedBody.supportingSurface().support());
            if (!demanded) {
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))) {
                        FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
                        if (FrontierV3AmbientActorLocalTargets.directedGoal(lease)) {
                            pursueLocalGoal(level, runtime, state, actorId, mob, lease);
                            if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
                        }
                        if (drainAfterDemandHysteresis(level, runtime, actorId, mob)) admitted++;
                    }
                } else if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED
                        && abandonUndemandedPrepared(level, runtime, state, actorId, lease)) {
                    admitted++;
                    forgetColdDemand(runtime, actorId);
                } else {
                    forgetColdDemand(runtime, actorId);
                }
                continue;
            }
            forgetColdDemand(runtime, actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                submit(runtime, "ambient-prepare", actorId.value(), new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId, runtime.canonicalState().orElseThrow().instant())));
                admitted++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED) {
                Result result = materialize(level, runtime, state, actorId, lease.handoffBody());
                if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                    if (result != Result.PENDING) FrontierV3AmbientHotAdmission.confirmAndArm(level, runtime, actorId);
                    admitted++;
                }
                continue;
            }
            Entity body = level.getEntity(entityId(state, actorId));
            if (lease.status() == AmbientLeaseStatus.HOT && body instanceof Mob mob && owned(body, actorId, bioform(state, actorId))) {
                FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
                FrontierV3ScenePresentation.applyAmbientActorPresentation(mob, state, actorId, bioform(state, actorId));
                if (FrontierV3HotScoutObservation.observe(level, runtime, state, actorId, mob, lease)) {
                    admitted++;
                    continue;
                }
                if (pursueLocalGoal(level, runtime, state, actorId, mob, lease)) return;
                if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
            }
        }
    }
    static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody) {
        return materialize(level, state, actorId, canonicalBody, FrontierV3StandingPosition::aboveExactFloor);
    }
    static void holdForPreLeaseHandoff(Mob body) {
        FrontierV3GoalNavigation.quiesceStaleActuation(Objects.requireNonNull(body, "pre-lease handoff body"));
    }
    private static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody,
                                      FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPositionProvider) {
        FrontierV3ActorCarrierComposition.requireRole(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY,
                FrontierV3ActorCarrierComposition.Role.ADOPTER);
        if (!state.actorLocations().containsKey(actorId)) return Result.CONFLICT;
        UUID entityId = entityId(state, actorId); Entity existing = level.getEntity(entityId); boolean bioform = bioform(state, actorId);
        if (existing != null) {
            var binding = FrontierV3ActorOwnerBinding.from(existing).orElse(null);
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            if (!owned(existing, actorId, bioform) || !FrontierV3ActorBodyController.recognizes(state, existing)
                    || binding == null || !ledger.permitsRecordedOwner(binding) || ledger.hasBodyDeparture(actorId)
                    || ledger.hasDepartureConflict(actorId)) return Result.CONFLICT;
            if (existing instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
            if (existing instanceof Mob body) {
                if (!FrontierV3ActorCarryProjection.matches(state, actorId, body)
                        || !FrontierV3BakeryHandProjection.matchesAmbient(state, actorId, body)
                        || !FrontierV3ResidentMealHandProjection.matchesAmbient(state, actorId, body)) return Result.CONFLICT;
                // A live observed body already belongs to the actor. Preparing a
                // meal/work presentation cannot relocate it to a lease checkpoint.
            }
            return Result.CURRENT;
        }
        long ambientRevision = state.ambientLeases().containsKey(actorId) ? state.ambientLeases().get(actorId).revision() : 1L;
        long custodyEpoch = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId).physicalEpoch();
        FrontierV3ActorCarrierComposition.Declaration declaration = carrierDeclaration(state, actorId, entityId, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, custodyEpoch);
        var lease = state.ambientLeases().get(actorId);
        var candidates = lease != null && lease.status() == AmbientLeaseStatus.PREPARED && canonicalBody.equals(lease.handoffBody())
                ? AmbientPlacementPolicy.candidates(state, lease) : List.of(canonicalBody.supportingSurface());
        var result = FrontierV3ActorBodyController.materialize(level, state,
                new FrontierV3ActorBodyController.BirthRequest(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY,
                        FrontierV3ActorOwnerBinding.body(declaration), candidates,
                        new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(candidates, candidates)),
                        standingPositionProvider::resolve, body -> {
                            if (body instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
                            hydrateExactHeldEquipment(body, state, actorId);
                            if (!FrontierV3ActorCarryProjection.prepareNew(state, actorId, body)
                                    || !FrontierV3BakeryHandProjection.prepareAmbientNew(state, actorId, body)
                                    || !FrontierV3ResidentMealHandProjection.prepareAmbientNew(state, actorId, body)) return false;
                            FrontierV3ScenePresentation.applyAmbientActorPresentation(body, state, actorId, bioform);
                            return true;
                        }));
        return switch (result) {
            case APPLIED -> Result.APPLIED;
            case DEFERRED -> Result.DEFERRED;
            case CONFLICT -> Result.CONFLICT;
        };
    }
    private static void hydrateExactHeldEquipment(Mob body, FrontierWorldState state, SubjectId actorId) {
        if (!body.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return;
        state.inventory().actorItems(actorId).stream().filter(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                        || EngineeringToolCustody.isTool(item.itemKind()))
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.ExactItemStack::id)).findFirst()
                .ifPresent(item -> body.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3CargoHandoffExecutor.materializedStack(item)));
    }
    static Result materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody) {
        return materialize(level, runtime, state, actorId, canonicalBody, FrontierV3StandingPosition::aboveExactFloor);
    }
    private static Result materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                      FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody,
                                      FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPositionProvider) {
        Entity pending = FrontierV3AmbientPendingAdmissions.get(runtime, entityId(state, actorId));
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        if (ambient == null || !state.bootstrap().bounds().contains(canonicalBody.supportingSurface().support())) return Result.CONFLICT;
        Entity existing = level.getEntity(entityId(state, actorId));
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (pending != null) {
            return FrontierV3AmbientCarrierRecognition.recoverableOwnership(state,
                    FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(pending), ledger) ? Result.PENDING : Result.CONFLICT;
        }
        FrontierV3AmbientCarrierLedger.Reconciliation carrier = ledger.reconciliation(carrierDeclaration(state, actorId, entityId(state, actorId),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId).physicalEpoch()), existing != null);
        if (existing != null) {
            if (carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER) return Result.CONFLICT;
            if (!FrontierV3ActorBodyController.recognizes(state, existing)) return Result.CONFLICT;
            if (!FrontierV3AmbientCarrierRecognition.recoverableOwnership(state,
                    FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(existing), ledger)) return Result.CONFLICT;
            return materialize(level, state, actorId, canonicalBody, standingPositionProvider);
        }
        return materialize(level, state, actorId, canonicalBody, standingPositionProvider);
    }
    static UUID entityId(FrontierWorldState state, SubjectId actorId) { return io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId); }
    static FrontierV3ActorCarrierComposition.Declaration carrierDeclaration(FrontierWorldState state, SubjectId actorId, UUID entityId, FrontierV3ActorCarrierComposition.Representation representation, long epoch) {
        var actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("carrier declaration requires its exact canonical actor");
        return FrontierV3ActorCarrierComposition.fromCanonical(state, actorId, actor.kind(),
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, entityId, representation, 0L, epoch);
    }
    static boolean mayCreateFreshBody(boolean chunkLoaded, boolean entitiesLoaded, boolean exactHeadroom) {
        return chunkLoaded && entitiesLoaded && exactHeadroom;
    }
    static boolean restartAbsenceIsObserved(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state,
                                            SubjectId actorId, AmbientActorLease lease) {
        return restartAbsenceIsObserved(level, state, actorId, lease) && FrontierV3AmbientPendingAdmissions.get(runtime, entityId(state, actorId)) == null;
    }
    static boolean restartAbsenceIsObserved(ServerLevel level, FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        var location = state.actorLocations().get(actorId);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE
                && lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                && location.body().equals(lease.handoffBody())
                && level.hasChunkAt(minecraftBody(lease.handoffBody()))
                && level.areEntitiesLoaded(ChunkPos.asLong(minecraftBody(lease.handoffBody())))
                && restartCustodyIsRetained(state, actorId, lease,
                    FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()))
                && level.getEntity(entityId(state, actorId)) == null;
    }
    static boolean restartCustodyIsRetained(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease,
                                            FrontierV3AmbientCarrierLedger ledger) {
        // An empty loaded anchor is not proof that an old body never existed in
        // another saved chunk. Only a retained release fence permits closure here.
        // Historical lost custody needs the explicit offline all-dimension recovery.
        if (!lease.equals(state.ambientLeases().get(actorId)) || lease.status() != AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                || ledger.hasBodyDeparture(actorId) || ledger.hasDepartureConflict(actorId)) return false;
        var body = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId);
        // A prior inactive fence is not evidence that this incarnation was never attempted.
        // Already-running bodies need a saved departure or an exact storage inspection.
        return io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.require(state, body).phase()
                == io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.PREPARED
                && FrontierV3ActorBodyCustody.unstartedEvidence(body, state.actorLocations().get(actorId).kind(),
                    entityId(state, actorId), ledger.firstAdmission(actorId), ledger.inactiveCarrier(actorId),
                    ledger.pendingAdoption(actorId).isPresent());
    }
    static FrontierV3AmbientAdmissionDiagnostic admissionDiagnostic(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                     FrontierWorldState state, SubjectId actorId) {
        var location = state.actorLocations().get(actorId);
        if (location == null) return FrontierV3AmbientAdmissionDiagnostic.notCanonical();
        UUID expectedId = entityId(state, actorId);
        if (location.condition().status() != ActorLifeStatus.ALIVE) return FrontierV3AmbientAdmissionDiagnostic.terminal(expectedId);
        Entity existing = level.getEntity(expectedId);
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        Entity pending = FrontierV3AmbientPendingAdmissions.get(runtime, expectedId);
        // A durable PENDING creation plus its exact still-live pre-index body is
        // more specific than the unresolved-disk history alone. This is read-only
        // diagnostic precedence, never permission to admit another body.
        if (existing == null && pending != null && retainsPendingJoin(runtime, pending)
                && ledger.firstAdmission(actorId).filter(first -> first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                    && FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(pending)
                            .matches(first.attempt().orElseThrow().declaration())).isPresent()) {
            return FrontierV3AmbientAdmissionDiagnostic.pendingUnindexed(expectedId,
                    new BlockPosition(pending.getBlockX(), pending.getBlockY(), pending.getBlockZ()),
                    new ObservedPosition(pending.getX(), pending.getY(), pending.getZ()));
        }
        var unresolved = FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actorId);
        if (existing == null && unresolved.isPresent()) {
            return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, unresolved.orElseThrow());
        }
        if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED) {
            FrontierV3AmbientCarrierLedger.Reconciliation carrier = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId())
                    .reconciliation(carrierDeclaration(state, actorId, expectedId, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                            io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId).physicalEpoch()));
            if (existing != null && carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, "CONCURRENT_CUSTODY");
            }
            if (existing == null && (carrier == FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH
                    || carrier == FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION)) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, carrier.name());
            }
            if (existing == null && carrier == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                    && !hasUnusedFirstAdmission(ledger, carrierDeclaration(state, actorId, expectedId, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                            io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId).physicalEpoch()))) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, "MISSING");
            }
        }
        if (existing != null) {
            BlockPosition observedPosition = new BlockPosition(existing.getBlockX(), existing.getBlockY(), existing.getBlockZ());
            ObservedPosition observedExact = new ObservedPosition(existing.getX(), existing.getY(), existing.getZ());
            if (owned(existing, actorId, bioform(state, actorId))) {
                return FrontierV3AmbientAdmissionDiagnostic.indexed(expectedId, FrontierV3AmbientPendingAdmissions.get(runtime, expectedId) != null, observedPosition, observedExact,
                        existing instanceof Mob mob ? FrontierV3MobMotionLifecycle.trackerObservation(mob) : new FrontierV3ControlledMobMotion.TrackerObservation(0, 0),
                        existing instanceof Mob mob ? FrontierV3ControlledMobMotion.motionObservation(mob) : FrontierV3ControlledMobMotion.MotionObservation.idle());
            }
            if (FrontierV3SceneExecutor.recognizesDeclaration(runtime, existing)) {
                return FrontierV3AmbientAdmissionDiagnostic.sceneOwned(expectedId, observedPosition, observedExact,
                        existing instanceof Mob mob ? FrontierV3MobMotionLifecycle.trackerObservation(mob) : new FrontierV3ControlledMobMotion.TrackerObservation(0, 0),
                        existing instanceof Mob mob ? FrontierV3ControlledMobMotion.motionObservation(mob) : FrontierV3ControlledMobMotion.MotionObservation.idle());
            }
            return FrontierV3AmbientAdmissionDiagnostic.conflict(expectedId);
        }
        if (pending != null) return FrontierV3AmbientAdmissionDiagnostic.pendingUnindexed(expectedId,
                new BlockPosition(pending.getBlockX(), pending.getBlockY(), pending.getBlockZ()),
                new ObservedPosition(pending.getX(), pending.getY(), pending.getZ()));
        BlockPos anchor = minecraftBody(location.body());
        if (!level.hasChunkAt(anchor)) return FrontierV3AmbientAdmissionDiagnostic.unloaded(expectedId);
        if (!level.areEntitiesLoaded(ChunkPos.asLong(anchor))) return FrontierV3AmbientAdmissionDiagnostic.entityStoragePending(expectedId,
                new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, location.supportingSurface().support())) return FrontierV3AmbientAdmissionDiagnostic.blocked(expectedId, location.supportingSurface().support());
        if (FrontierV3BodyPlacement.occupied(level, bioform(state, actorId) ? EntityType.ZOMBIE : EntityType.VILLAGER, location.supportingSurface()))
            return FrontierV3AmbientAdmissionDiagnostic.occupied(expectedId, new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
        return FrontierV3AmbientAdmissionDiagnostic.ready(expectedId, new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
    }
    static JoinDisposition observeJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || !FrontierV3AmbientCarrierRecognition.recognizesOwnership(state,
                FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(entity))) return JoinDisposition.NOT_MANAGED;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return JoinDisposition.NOT_MANAGED;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return JoinDisposition.NOT_MANAGED; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || !entityId(state, actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return JoinDisposition.NOT_MANAGED;
        FrontierV3AmbientPendingAdmissions.RetainResult retained = FrontierV3AmbientPendingAdmissions.retain(runtime, entity, actorId);
        if (retained == FrontierV3AmbientPendingAdmissions.RetainResult.LIMIT_REACHED) return JoinDisposition.NOT_MANAGED;
        if (retained == FrontierV3AmbientPendingAdmissions.RetainResult.DUPLICATE_UNINDEXED) return JoinDisposition.DUPLICATE_UNINDEXED;
        if (entity instanceof Mob body) {
            FrontierV3GoalNavigation.quiesceStaleActuation(body);
            body.setNoAi(true);
        }
        // Joining retains the exact object only. Common indexed body confirmation
        // precedes scope recovery in the pending-admission drain, never the reverse.
        return JoinDisposition.RETAINED;
    }
    static boolean retainsPendingJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        Entity pending = FrontierV3AmbientPendingAdmissions.get(runtime, entity.getUUID());
        FrontierWorldState state = runtime.decodedState().orElse(null);
        return pending == entity && state != null
                && FrontierV3AmbientCarrierRecognition.recognizesOwnership(state,
                FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(entity));
    }
    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        return FrontierV3AmbientCarrierRecognition.recognizes(runtime, entity);
    }
    private static boolean demand(ServerLevel level, BlockPosition position) {
        return FrontierV3SceneExecutor.demandExists(level, position);
    }
    static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        var actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("actor kind is absent from canonical identity");
        return actor.kind() == ActorKind.BIOFORM;
    }
    static Bioform bioformProfile(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(actorId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a canonical Frontier v3 bioform: " + actorId));
    }
    static boolean owned(Entity entity, SubjectId actorId, boolean bioform) {
        return !entity.isRemoved() && actorId.value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && (bioform ? entity instanceof Zombie : entity instanceof Villager)
                && (bioform ? "BIOFORM" : "RESIDENT").equals(entity.getPersistentData().getString(KIND_KEY))
                && actorId.value().equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.ACTOR_KEY))
                && (bioform ? ActorKind.BIOFORM.name()
                : ActorKind.RESIDENT.name()).equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.KIND_KEY))
                && FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY.name().equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.OWNER_KEY))
                && FrontierV3ActorCarrierComposition.Representation.LIVE_BODY.name().equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY))
                && entity.getPersistentData().contains(FrontierV3ActorCarrierComposition.REVISION_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && entity.getPersistentData().getLong(FrontierV3ActorCarrierComposition.REVISION_KEY) == 0L
                && entity.getPersistentData().getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY) >= 1L;
    }
    static void configureBioform(Zombie body, Bioform bioform) {
        if (!body.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            body.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS, 0, true, false));
        }
        body.setCanPickUpLoot(false);
        if (body.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(bioform.isExplosiveAssaulter() ? Items.RED_WOOL
                    : bioform.isScout() ? Items.CYAN_WOOL : bioform.isDefender() ? Items.PURPLE_WOOL : Items.LIME_WOOL));
            body.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
    }
    static boolean pursueLocalGoal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        return FrontierV3AmbientMovementExecutor.pursueLocalGoal(level, runtime, state, actorId, body, lease);
    }
    static boolean drain(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        return body.level() == level && release(runtime, body).isPresent();
    }
    static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> drainForAdmission(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        return release(runtime, body);
    }
    /** Recorded canonical death retires custody; absence of a Minecraft entity does not. */
    static boolean recordedSceneDeath(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                      io.farfrontier.palemirror.frontier.v3.model.SceneMember member) {
        if (lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING
                || !lease.equals(state.sceneLeases().get(lease.id())) || !lease.members().contains(member)) return false;
        var actor = state.actorLocations().get(member.actorId());
        return actor != null && actor.condition().status() == ActorLifeStatus.DEAD;
    }
    enum SceneCarrierFenceResult {
        FENCED, RETIRED_BY_DEATH, ENTITY_UNAVAILABLE, ENTITY_DEAD, SCENE_OWNERSHIP_MISMATCH, UUID_MISMATCH,
        ACTOR_UNAVAILABLE, AMBIENT_AUTHORITY_OPEN, CARRIER_CONFLICT;

        boolean permitsRelease() { return this == FENCED || this == RETIRED_BY_DEATH; }
    }
    static boolean hasInactiveCarrier(ServerLevel level, FrontierWorldState state, SubjectId actorId) {
        return FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).hasCarrier(actorId);
    }
    private static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return Optional.empty();
        String rawActorId = body.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return Optional.empty();
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return Optional.empty(); }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(state, actorId).equals(body.getUUID())
                || !owned(body, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F
                || state.ambientLeases().get(actorId) == null
                || (state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT
                    && state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING
                    && state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.PREPARED
                    && state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)) return Optional.empty();
        // Closing an activity projection retains this exact loaded body. Actual
        // natural unload has its separate saved-departure and body-release protocol.
        if (!(body.level() instanceof ServerLevel level)) return Optional.empty();
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (!FrontierV3ResidentMealPhysicalEffect.release(level, runtime, state, actorId, body, lease)) return Optional.empty();
        long epoch = body.getPersistentData().getLong(CUSTODY_EPOCH_KEY);
        if (epoch < 1L || !FrontierV3ActorCarrierComposition.owns(body,
                carrierDeclaration(state, actorId, body.getUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, epoch))) return Optional.empty();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (ledger.hasBodyDeparture(actorId)
                || ledger.hasDepartureConflict(actorId)) return Optional.empty();
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) return Optional.empty();
        state = runtime.decodedState().orElseThrow();
        current = state.actorLocations().get(actorId);
        BodyPosition position = current.body(); FixedScalar health = current.condition().health();
        lease = state.ambientLeases().get(actorId);
        if (lease.status() != AmbientLeaseStatus.DRAINING && !(submit(runtime, "ambient-draining", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return Optional.empty();
        FrontierWorldState drained = runtime.decodedState().orElse(null);
        if (drained == null || drained.ambientLeases().get(actorId) == null
                || drained.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return Optional.empty();
        if (!(submit(runtime, "ambient-release", actorId.value(), new AmbientLeaseReleased(actorId, position, health))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return Optional.empty();
        FrontierWorldState released = runtime.decodedState().orElse(null);
        if (released == null || released.ambientLeases().get(actorId) == null
                || released.ambientLeases().get(actorId).status() != AmbientLeaseStatus.CLOSED) return Optional.empty();
        FrontierV3ActorCarryProjection.rememberConfirmed(released, actorId, body);
        return Optional.of(new FrontierV3AmbientAdmissionPolicy.EffectResult(drained, released));
    }
    /** Cancel admission, not a physical owner: historical bodies require their retained carrier. */
    static boolean abandonUndemandedPrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        if (lease.status() != AmbientLeaseStatus.PREPARED || !lease.equals(state.ambientLeases().get(actorId))) return false;
        UUID id = entityId(state, actorId);
        if (level.getEntity(id) != null || FrontierV3AmbientPendingAdmissions.get(runtime, id) != null) return false;
        return abandonPreparedForReservation(level, runtime, state, actorId, lease).isPresent();
    }

    static boolean hasUnusedFirstAdmission(FrontierV3AmbientCarrierLedger ledger,
                                           FrontierV3ActorCarrierComposition.Declaration declaration) {
        return FrontierV3ActorBodyController.hasUnusedFirstAdmission(ledger, declaration);
    }
    static boolean hasNeverCreatedFirstAdmission(FrontierV3AmbientCarrierLedger ledger,
                                                 FrontierV3ActorCarrierComposition.Declaration declaration) {
        return ledger.firstAdmission(declaration.actorId()).filter(value ->
                value.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                        && value.identity().matches(declaration)).isPresent();
    }
    static boolean preparedCancellationHasEvidence(FrontierV3AmbientCarrierLedger.Reconciliation carrier,
                                                   boolean hasNeverCreatedPermit) {
        return carrier == FrontierV3AmbientCarrierLedger.Reconciliation.READY
                || (hasNeverCreatedPermit && carrier == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER);
    }

    static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> abandonPreparedForReservation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                                    FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        if (lease.status() != AmbientLeaseStatus.PREPARED || !lease.equals(state.ambientLeases().get(actorId))
                || !state.equals(runtime.decodedState().orElse(null))) return Optional.empty();
        Entity body = level.getEntity(entityId(state, actorId));
        if (body != null) {
            if (!(body instanceof Mob mob) || !observedBody(mob).equals(lease.handoffBody())) return Optional.empty();
            return release(runtime, mob);
        }
        UUID id = entityId(state, actorId);
        if (FrontierV3AmbientPendingAdmissions.get(runtime, id) != null) return Optional.empty();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (ledger.pendingAdoption(actorId).isPresent()) return Optional.empty();
        var declaration = carrierDeclaration(state, actorId, id, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actorId).physicalEpoch());
        var carrier = ledger.reconciliation(declaration, false);
        if (!preparedCancellationHasEvidence(carrier, hasNeverCreatedFirstAdmission(ledger, declaration))) return Optional.empty();
        ledger.persist(level, state.bootstrap().worldId());
        if (!(submit(runtime, "ambient-reserved-draining", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING)) instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) {
            return Optional.empty();
        }
        FrontierWorldState drained = runtime.decodedState().orElse(null);
        if (drained == null || drained.ambientLeases().get(actorId) == null
                || drained.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return Optional.empty();
        var condition = drained.actorLocations().get(actorId);
        if (condition == null || condition.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        boolean released = submit(runtime, "ambient-reserved-release", actorId.value(),
                new AmbientLeaseReleased(actorId, lease.handoffBody(), condition.condition().health()))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
        if (!released) return Optional.empty();
        FrontierWorldState resulting = runtime.decodedState().orElse(null);
        if (resulting == null || resulting.ambientLeases().get(actorId) == null
                || resulting.ambientLeases().get(actorId).status() != AmbientLeaseStatus.CLOSED) return Optional.empty();
        FrontierV3ActorBodyCustody.releaseUnstartedAbsence(level, runtime, actorId);
        resulting = runtime.decodedState().orElseThrow();
        return Optional.of(new FrontierV3AmbientAdmissionPolicy.EffectResult(drained, resulting));
    }
    private static boolean drainReservedColdContinuation(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                          FrontierWorldState state, SubjectId actorId, Mob body,
                                                          AmbientActorLease lease) {
        if (!lease.equals(state.ambientLeases().get(actorId))
                || !FrontierV3SurfaceObservation.at(body, lease.handoffBody().supportingSurface())) return false;
        return release(runtime, body).isPresent();
    }
    /** Releases an exact ambient hand-off only once Minecraft has unloaded its whole chunk. */
    static boolean releaseUnloadedReservedColdContinuation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                    FrontierWorldState state, SubjectId actorId,
                                                                    AmbientActorLease lease) {
        if (!lease.equals(state.ambientLeases().get(actorId)) || level.getEntity(entityId(state, actorId)) != null
                || FrontierV3AmbientPendingAdmissions.get(runtime, entityId(state, actorId)) != null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var receipt = FrontierV3AmbientDepartureObserver.recordedDeparture(state, actorId, ledger).orElse(null);
        if (receipt == null || !ledger.savedAmbientDeparture(receipt) || ledger.hasDepartureConflict(actorId)
                || level.hasChunkAt(minecraftBody(receipt.observed().body()))) return false;
        if (!FrontierV3ActorBodyController.checkpointSavedDeparture(level, runtime, actorId)) return false;
        state = runtime.decodedState().orElseThrow();
        var release = new AmbientLeaseReleased(actorId, receipt.observed().body(), receipt.observed().health());
        // Closing a projection retains body custody. Registered execution owners
        // check pending effects; route checkpoint settlement belongs to body departure.
        if (!unloadedReleaseEligible(state, release)) return false;
        if (lease.status() != AmbientLeaseStatus.DRAINING && !(submit(runtime, "ambient-unloaded-reserved-draining", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        FrontierWorldState draining = runtime.decodedState().orElse(null);
        if (draining == null || draining.ambientLeases().get(actorId) == null
                || draining.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return false;
        if (!receipt.current(draining)) return false;
        if (!(submit(runtime, "ambient-unloaded-reserved-release", actorId.value(), release)
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        FrontierV3ActorBodyController.progressDeparture(level, runtime, runtime.decodedState().orElseThrow(), actorId);
        return true;
    }
    static boolean unloadedReleaseEligible(FrontierWorldState state, AmbientLeaseReleased release) {
        ResidentMeal meal = state.humanPopulation().meals().get(release.actorId());
        // A departure pose alone does not prove a physical food-hand reconciliation.
        if (meal != null && (meal.pendingPhysicalStep().isPresent()
                || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(meal.actorAccountId())))) return false;
        try {
            var draining = state.ambientLeases().get(release.actorId()).status() == AmbientLeaseStatus.DRAINING
                    ? state : io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(
                        state, release.actorId(), AmbientLeaseStatus.DRAINING);
            io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(draining, release);
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        FrontierV3AmbientPendingAdmissions.forget(runtime);
        COLD_DEMAND_SINCE.remove(runtime);
        FrontierV3ActorProbeSchedule.forget(runtime);
        FrontierV3AmbientActorCaches.forget(runtime);
    }
    static FrontierV3AmbientAdmissionPolicy.Session admissionPolicy(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                     FrontierWorldState state) {
        return FrontierV3AmbientAdmissionPolicy.begin(state, current -> FrontierSceneAdmission.reservationAdmission(current,
                ignored -> FrontierV3GrayboxExecutor.admissionProvider(runtime, current)));
    }
    private static FrontierSceneAdmission.GenericAmbientAdmission genericAmbientAdmission(FrontierV3ServerRuntime<?, ?> runtime,
                                                                                           FrontierWorldState state) {
        return FrontierV3AmbientActorCaches.genericAmbientAdmission(runtime, state);
    }
    static boolean drainAfterDemandHysteresis(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                      SubjectId actorId, Mob body) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (absentSince.size() >= FrontierV3AmbientPendingAdmissions.MAX_ENTRIES && !absentSince.containsKey(actorId)) return false;
        long started = absentSince.computeIfAbsent(actorId, ignored -> level.getGameTime());
        if (level.getGameTime() - started < DRAIN_HYSTERESIS_TICKS || playerWithin(level, body.blockPosition(), DRAIN_SAFE_RADIUS_BLOCKS)) return false;
        boolean drained = drain(level, runtime, body);
        if (drained) absentSince.remove(actorId);
        if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
        return drained;
    }
    private static boolean observeDirectedArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        return FrontierV3AmbientMovementExecutor.observeDirectedArrival(level, runtime, state, actorId, body, lease);
    }
    private static BlockPos minecraftFloor(BlockPosition floor) { return new BlockPos(floor.x(), floor.y(), floor.z()); }
    static BlockPos minecraftBody(BodyPosition body) { return new BlockPos(body.x(), body.y(), body.z()); }
    static BodyPosition observedBody(Entity entity) { return FrontierV3BodyObservation.position(entity); }
    private static void forgetColdDemand(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime); if (absentSince == null) return;
        absentSince.remove(actorId); if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
    }
    private static boolean playerWithin(ServerLevel level, BlockPos position, int radius) {
        return FrontierV3SceneDemand.observerWithin(level, List.of(position), radius);
    }
    static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                           String phase, String id, FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
    record ObservedPosition(double x, double y, double z) { }
    enum Result { APPLIED, CURRENT, PENDING, DEFERRED, CONFLICT } enum JoinDisposition { NOT_MANAGED, RETAINED, DUPLICATE_UNINDEXED }
}
