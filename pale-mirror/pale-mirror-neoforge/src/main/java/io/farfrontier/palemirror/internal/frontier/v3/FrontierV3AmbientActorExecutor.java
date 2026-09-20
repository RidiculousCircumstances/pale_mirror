package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorObserved;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
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
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentTransitAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolLeaseRecovered;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_ambient_actor";
    static final String KIND_KEY = "pale_mirror_frontier_v3_ambient_kind";
    static final String CUSTODY_EPOCH_KEY = "pale_mirror_frontier_v3_ambient_epoch";
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
        int admitted = resumeDemandedClosedSceneReturns(level, runtime, state);
        for (SubjectId actorId : FrontierV3ActorProbeSchedule.next(runtime, state, level, MAX_ACTOR_PROBES_PER_TICK,
                FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK)) {
            if (admitted >= FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK) return;
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
            boolean successorSceneOwnsActor = state.sceneLeases().values().stream()
                    .filter(scene -> scene.status() != SceneLeaseStatus.CLOSED)
                    .anyMatch(scene -> scene.members().stream().anyMatch(member -> member.actorId().equals(actorId)));
            if (lease != null && lease.status() == AmbientLeaseStatus.CLOSED && !successorSceneOwnsActor) {
                Entity stale = level.getEntity(entityId(state, actorId));
                if (stale != null && owned(stale, actorId, bioform(state, actorId))) stale.discard();
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
            }
            if (successorSceneOwnsActor) {
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
                        demand(level, location.supportingSurface().support()))) {
                    if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                        submit(runtime, "ambient-pre-lease-prepare", actorId.value(),
                                new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId,
                                        runtime.canonicalState().orElseThrow().instant())));
                        admitted++;
                    } else if (lease.status() == AmbientLeaseStatus.PREPARED) {
                        Result result = materialize(level, runtime, state, actorId, lease.handoffBody(), preLeaseStanding.orElseThrow());
                        if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                            if (result != Result.PENDING) submit(runtime, "ambient-pre-lease-hot", actorId.value(),
                                    new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
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
                } else if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED
                        && abandonPreparedForReservation(level, runtime, state, actorId, lease).isPresent()) {
                    admitted++;
                }
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            boolean demanded = demand(level, location.supportingSurface().support());
            if (!demanded) {
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))) {
                        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(mob);
                        FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
                        if (FrontierV3AmbientActorLocalTargets.directedGoal(lease)) {
                            pursueLocalGoal(level, runtime, state, actorId, mob, lease);
                            if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
                        }
                        if (drainAfterDemandHysteresis(level, runtime, actorId, mob)) admitted++;
                    } else if (drainObservedAfterDemandHysteresis(level, runtime, actorId)) {
                        admitted++;
                    }
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
            if (lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
                if (body != null && owned(body, actorId, bioform(state, actorId))) {
                    submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
                    admitted++;
                } else if (body == null && restartAbsenceIsObserved(level, runtime, state, actorId, lease)) {
                    submit(runtime, "ambient-restart-absence", actorId.value(),
                            new AmbientLeaseRestartAbsenceObserved(actorId, lease.handoffBody()));
                    admitted++;
                }
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.HOT && body instanceof Mob mob && owned(body, actorId, bioform(state, actorId))) {
                FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
                FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(mob);
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
    private static int resumeDemandedClosedSceneReturns(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                        FrontierWorldState state) {
        int admitted = 0;
        for (SubjectId actorId : FrontierV3SceneExecutor.retainedClosedActorsDemandedBy(level, state)) {
            if (admitted >= FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK) break;
            state = runtime.decodedState().orElse(null);
            if (state == null) return admitted;
            if (FrontierV3AmbientPendingAdmissions.get(runtime, entityId(state, actorId)) != null) continue;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !HivePhysiologySupport.permitsAmbientLease(state, actorId)) continue;
            var lease = state.ambientLeases().get(actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                submit(runtime, "ambient-closed-scene-return-prepare", actorId.value(),
                        new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId,
                                runtime.canonicalState().orElseThrow().instant())));
                admitted++;
                continue;
            }
            if (lease.status() != AmbientLeaseStatus.PREPARED) continue;
            Result result = materialize(level, runtime, state, actorId, lease.handoffBody());
            if (result == Result.APPLIED || result == Result.CURRENT) {
                FrontierV3AmbientHotAdmission.confirmAndArm(level, runtime, actorId);
                admitted++;
            }
        }
        return admitted;
    }
    static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody) {
        return materialize(level, state, actorId, canonicalBody, FrontierV3StandingPosition::aboveExactFloor);
    }
    static void holdForPreLeaseHandoff(Mob body) {
        FrontierV3ControlledMobMotion.stop(Objects.requireNonNull(body, "pre-lease handoff body"));
    }
    private static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody,
                                      FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPositionProvider) {
        FrontierV3ActorCarrierComposition.requireRole(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY,
                FrontierV3ActorCarrierComposition.Role.PRODUCER);
        if (!state.actorLocations().containsKey(actorId)) return Result.CONFLICT;
        UUID entityId = entityId(state, actorId); Entity existing = level.getEntity(entityId); boolean bioform = bioform(state, actorId);
        if (existing != null) {
            if (!owned(existing, actorId, bioform)) return Result.CONFLICT;
            if (existing instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
            if (existing instanceof Mob body) {
                body.getNavigation().stop();
                body.setNoAi(true);
            }
            return Result.CURRENT;
        }
        BlockPos position = standingPositionProvider.resolve(level, minecraftFloor(canonicalBody.supportingSurface().support()));
        if (position == null || !position.equals(minecraftBody(canonicalBody)) || !level.hasChunkAt(position)) return Result.DEFERRED;
        long ambientRevision = state.ambientLeases().containsKey(actorId) ? state.ambientLeases().get(actorId).revision() : 1L;
        FrontierV3ActorCarrierComposition.Declaration declaration = carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, entityId,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, ambientRevision, 1L);
        Mob body = FrontierV3ActorCarrierFactory.create(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY, level, declaration);
        body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired();
        body.getPersistentData().putLong(CUSTODY_EPOCH_KEY, 1L);
        body.setNoAi(true);
        if (body instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
        hydrateExactHeldEquipment(body, state, actorId);
        FrontierV3ScenePresentation.applyAmbientActorPresentation(body, state, actorId, bioform);
        body.getPersistentData().putString(ACTOR_KEY, actorId.value()); body.getPersistentData().putString(KIND_KEY, bioform ? "BIOFORM" : "RESIDENT");
        if (!level.noCollision(body, body.getBoundingBox()) || admissionColumnOccupied(level, body, body.getBoundingBox())) return Result.DEFERRED;
        return level.addFreshEntity(body) ? Result.APPLIED : Result.CONFLICT;
    }
    private static boolean admissionColumnOccupied(ServerLevel level, Mob candidate, AABB body) {
        return !level.getEntities(candidate, body.inflate(0.001D), entity -> entity instanceof LivingEntity living
                && living.isAlive() && !living.isSpectator()).isEmpty();
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
        if (pending != null && owned(pending, actorId, bioform(state, actorId))) return Result.PENDING;
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        if (ambient == null) return Result.CONFLICT;
        Entity existing = level.getEntity(entityId(state, actorId));
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        FrontierV3AmbientCarrierLedger.Reconciliation carrier = ledger.reconciliation(carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, entityId(state, actorId), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                ambient.revision(), 1L), existing != null);
        if (existing != null) {
            if (carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER) return Result.CONFLICT;
            if (!owned(existing, actorId, bioform(state, actorId))
                    && !FrontierV3SceneExecutor.adoptRetainedClosedBodyForAmbient(existing, state, actorId)) return Result.CONFLICT;
            return materialize(level, state, actorId, canonicalBody, standingPositionProvider);
        }
        BlockPos position = standingPositionProvider.resolve(level, minecraftFloor(canonicalBody.supportingSurface().support()));
        if (position == null || !position.equals(minecraftBody(canonicalBody))
                || !mayCreateFreshBody(level.hasChunkAt(position), level.areEntitiesLoaded(ChunkPos.asLong(position)), true)) return Result.DEFERRED;
        if (existing == null && carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                && carrier != FrontierV3AmbientCarrierLedger.Reconciliation.READY) return Result.CONFLICT;
        if (existing == null && carrier == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                && state.ambientLeases().get(actorId).revision() > 1L) return Result.CONFLICT;
        Result result = materialize(level, state, actorId, canonicalBody, standingPositionProvider);
        if (result != Result.APPLIED || carrier != FrontierV3AmbientCarrierLedger.Reconciliation.READY) return result;
        Entity reconstructed = level.getEntity(entityId(state, actorId));
        long reconstructionEpoch = ledger.reconstructionEpoch(actorId);
        if (!(reconstructed instanceof Mob) || !ledger.adopt(carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, reconstructed.getUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, ambient.revision(), reconstructionEpoch))) {
            if (reconstructed != null) reconstructed.discard();
            return Result.CONFLICT;
        }
        reconstructed.getPersistentData().putLong(CUSTODY_EPOCH_KEY, reconstructionEpoch);
        FrontierV3ActorCarrierComposition.stamp(reconstructed, carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, reconstructed.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, ambient.revision(), reconstructionEpoch));
        return result;
    }
    static UUID entityId(FrontierWorldState state, SubjectId actorId) { return io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId); }
    static FrontierV3ActorCarrierComposition.Declaration carrierDeclaration(FrontierWorldState state, SubjectId actorId,
                                                                             FrontierV3ActorCarrierComposition.Owner owner, UUID entityId,
                                                                             FrontierV3ActorCarrierComposition.Representation representation, long revision, long epoch) {
        return FrontierV3ActorCarrierComposition.fromCanonical(state, actorId, bioform(state, actorId)
                        ? FrontierV3ActorCarrierComposition.ActorKind.BIOFORM : FrontierV3ActorCarrierComposition.ActorKind.RESIDENT,
                owner, entityId, representation, revision, epoch);
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
                && level.getEntity(entityId(state, actorId)) == null;
    }
    static FrontierV3AmbientAdmissionDiagnostic admissionDiagnostic(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                     FrontierWorldState state, SubjectId actorId) {
        var location = state.actorLocations().get(actorId);
        if (location == null) return FrontierV3AmbientAdmissionDiagnostic.notCanonical();
        UUID expectedId = entityId(state, actorId);
        if (location.condition().status() != ActorLifeStatus.ALIVE) return FrontierV3AmbientAdmissionDiagnostic.terminal(expectedId);
        Entity existing = level.getEntity(expectedId);
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED) {
            FrontierV3AmbientCarrierLedger.Reconciliation carrier = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId())
                    .reconciliation(carrierDeclaration(state, actorId, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, expectedId,
                            FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 1L));
            if (existing != null && carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, "CONCURRENT_CUSTODY");
            }
            if (existing == null && (carrier == FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH
                    || carrier == FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION)) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, carrier.name());
            }
            if (existing == null && carrier == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER && lease.revision() > 1L) {
                return FrontierV3AmbientAdmissionDiagnostic.carrierAmbiguity(expectedId, "MISSING");
            }
        }
        if (existing != null) {
            BlockPosition observedPosition = new BlockPosition(existing.getBlockX(), existing.getBlockY(), existing.getBlockZ());
            ObservedPosition observedExact = new ObservedPosition(existing.getX(), existing.getY(), existing.getZ());
            if (owned(existing, actorId, bioform(state, actorId))) {
                return FrontierV3AmbientAdmissionDiagnostic.indexed(expectedId, FrontierV3AmbientPendingAdmissions.get(runtime, expectedId) != null, observedPosition, observedExact,
                        existing instanceof Mob mob ? FrontierV3MobMotionLifecycle.trackerObservation(mob) : new FrontierV3ControlledMobMotion.TrackerObservation(0, 0));
            }
            if (FrontierV3SceneExecutor.recognizes(runtime, existing)) {
                return FrontierV3AmbientAdmissionDiagnostic.sceneOwned(expectedId, observedPosition, observedExact,
                        existing instanceof Mob mob ? FrontierV3MobMotionLifecycle.trackerObservation(mob) : new FrontierV3ControlledMobMotion.TrackerObservation(0, 0));
            }
            return FrontierV3AmbientAdmissionDiagnostic.conflict(expectedId);
        }
        Entity pending = FrontierV3AmbientPendingAdmissions.get(runtime, expectedId);
        if (pending != null) return FrontierV3AmbientAdmissionDiagnostic.pendingUnindexed(expectedId,
                new BlockPosition(pending.getBlockX(), pending.getBlockY(), pending.getBlockZ()),
                new ObservedPosition(pending.getX(), pending.getY(), pending.getZ()));
        BlockPos anchor = minecraftBody(location.body());
        if (!level.hasChunkAt(anchor)) return FrontierV3AmbientAdmissionDiagnostic.unloaded(expectedId);
        if (!level.areEntitiesLoaded(ChunkPos.asLong(anchor))) return FrontierV3AmbientAdmissionDiagnostic.entityStoragePending(expectedId,
                new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, location.supportingSurface().support())) return FrontierV3AmbientAdmissionDiagnostic.blocked(expectedId, location.supportingSurface().support());
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
            body.getNavigation().stop();
            body.setNoAi(true);
        }
        if (FrontierV3AmbientCarrierRecognition.recognizes(runtime, entity)
                && state.ambientLeases().get(actorId) != null && state.ambientLeases().get(actorId).status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
            submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
        }
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
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT
                || !entityId(state, actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return false;
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "ambient-death", actorId.value(),
                new AmbientActorDied(actorId, observedBody(entity), cause));
        return true;
    }
    static boolean observeLeave(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, boolean serverStopping) {
        if (serverStopping) return false;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || !(entity instanceof Mob body)) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(state, actorId).equals(entity.getUUID())
                || !owned(entity, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT) return false;
        return false;
    }
    private static boolean demand(ServerLevel level, BlockPosition position) {
        return FrontierV3SceneExecutor.demandExists(level, position);
    }
    static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .map(Bioform::id).anyMatch(actorId::equals);
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
                && (bioform ? FrontierV3ActorCarrierComposition.ActorKind.BIOFORM.name()
                : FrontierV3ActorCarrierComposition.ActorKind.RESIDENT.name()).equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.KIND_KEY))
                && FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE.name().equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.OWNER_KEY))
                && FrontierV3ActorCarrierComposition.Representation.LIVE_BODY.name().equals(entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY))
                && entity.getPersistentData().getLong(FrontierV3ActorCarrierComposition.REVISION_KEY) >= 1L
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
    static boolean pursueLocalGoal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.TRANSIT) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
            if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                    || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) {
                body.getNavigation().stop();
                return false;
            }
        }
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL && !lease.goalBody().equals(state.actorLocations().get(actorId).body())) {
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) {
            HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
            HiveMobilization mobilization = assemblingMobilization(state, actorId);
            if (mobilization == null || member == null || member.arrived()) {
                body.getNavigation().stop();
                return false;
            }
            HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget)) {
                body.getNavigation().stop();
                return false;
            }
            if (!FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly-blocked",
                        actorId.value(), HiveMobilizationDiagnosticProducer.ASSEMBLY_PATH_BLOCKED.create(mobilization.id(),
                                new io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage(actorId,
                                        member.cursor(), member.nextSurface())));
                FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                        "hive_assembly_path_blocked", actorId, result);
                body.getNavigation().stop();
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_RETURN) {
            return FrontierV3HiveReturnMotion.pursue(level, runtime, state, actorId, body, lease);
        }
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, lease);
            if (member == null) {
                body.getNavigation().stop();
                return false;
            }
            RouteOperation operation = assemblingOperation(state, actorId);
            OperationAssembly assembly = operation.activeAssembly().orElseThrow();
            if (assembly.deferral().isPresent() && !assembly.deferral().orElseThrow().actorId().equals(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPosition obstruction = assemblyObstruction(level, state, operation, lease.goalBody().supportingSurface().support());
            if (obstruction != null) {
                OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(actorId, lease.goalBody().supportingSurface(), new SurfaceAnchor(obstruction),
                        OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION);
                if (!assembly.deferral().filter(deferral::equals).isPresent()) {
                    io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly-deferred", actorId.value(),
                            new OperationAssemblyDeferred(operation.id(), deferral));
                    FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_deferred", actorId, result);
                    body.getNavigation().stop();
                    return true;
                }
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
            if (member == null || member.arrived()) {
                body.getNavigation().stop();
                return false;
            }
            EngineeringWorkAssembly assembly = engineeringProject(state, actorId).assembly().orElseThrow();
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        FrontierV3ControlledMobMotion.followContinuously(level, body,
                FrontierV3AmbientActorLocalTargets.localTarget(state, actorId, lease, level.getGameTime()));
        return false;
    }
    static boolean drain(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        FrontierWorldState before = runtime.decodedState().orElse(null);
        if (before == null) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(body.getPersistentData().getString(ACTOR_KEY)); } catch (IllegalArgumentException invalid) { return false; }
        AmbientActorLease lease = before.ambientLeases().get(actorId);
        if (lease == null) return false;
        long epoch = Math.max(1L, body.getPersistentData().getLong(CUSTODY_EPOCH_KEY));
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, before.bootstrap().worldId());
        FrontierV3ActorCarrierComposition.Declaration carrier = carrierDeclaration(before, actorId, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                body.getUUID(), FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, lease.revision(), epoch);
        if (!ledger.canFence(carrier, lease.revision(), lease.revision())) return false;
        if (!ledger.fence(carrier, lease.revision(), lease.revision())) return false;
        Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> released = release(runtime, body, false);
        if (released.isEmpty()) return false;
        body.discard();
        return true;
    }
    static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> drainForAdmission(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        return release(runtime, body, true);
    }
    static boolean fenceClosedSceneBody(ServerLevel level, FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                        io.farfrontier.palemirror.frontier.v3.model.SceneMember member, Entity entity) {
        if (!fenceSceneBody(level, state, lease, member, entity, true)) return false;
        ((Mob) entity).discard();
        return true;
    }
    static SceneCarrierFenceResult fenceDrainingSceneBody(ServerLevel level, FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                                           io.farfrontier.palemirror.frontier.v3.model.SceneMember member, Entity entity) {
        return fenceSceneBodyResult(level, state, lease, member, entity, false);
    }
    private static boolean fenceSceneBody(ServerLevel level, FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                          io.farfrontier.palemirror.frontier.v3.model.SceneMember member, Entity entity, boolean closed) {
        return fenceSceneBodyResult(level, state, lease, member, entity, closed) == SceneCarrierFenceResult.FENCED;
    }
    private static SceneCarrierFenceResult fenceSceneBodyResult(ServerLevel level, FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                                                 io.farfrontier.palemirror.frontier.v3.model.SceneMember member, Entity entity, boolean closed) {
        boolean owned = closed ? FrontierV3SceneExecutor.ownedByClosedLease(entity, state, lease, member)
                : FrontierV3SceneExecutor.owned(entity, state, lease, member);
        if (!(entity instanceof Mob body)) return SceneCarrierFenceResult.ENTITY_UNAVAILABLE;
        if (!body.isAlive()) return SceneCarrierFenceResult.ENTITY_DEAD;
        if (!owned) return SceneCarrierFenceResult.SCENE_OWNERSHIP_MISMATCH;
        if (!entityId(state, member.actorId()).equals(body.getUUID())) return SceneCarrierFenceResult.UUID_MISMATCH;
        var actor = state.actorLocations().get(member.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return SceneCarrierFenceResult.ACTOR_UNAVAILABLE;
        AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
        if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED) return SceneCarrierFenceResult.AMBIENT_AUTHORITY_OPEN;
        long priorAmbientRevision = ambient == null ? 0L : ambient.revision();
        long physicalRevision = Math.max(1L, lease.revision());
        long epoch = Math.max(1L, body.getPersistentData().getLong(CUSTODY_EPOCH_KEY));
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        FrontierV3ActorCarrierComposition.Declaration carrier = carrierDeclaration(state, member.actorId(), FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                body.getUUID(), FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, physicalRevision, epoch);
        if (ledger.matchesCarrier(carrier, physicalRevision, priorAmbientRevision)) return SceneCarrierFenceResult.FENCED;
        if (!ledger.canFence(carrier, physicalRevision, priorAmbientRevision) || !ledger.fence(carrier, physicalRevision, priorAmbientRevision)) return SceneCarrierFenceResult.CARRIER_CONFLICT;
        return SceneCarrierFenceResult.FENCED;
    }
    enum SceneCarrierFenceResult {
        FENCED, ENTITY_UNAVAILABLE, ENTITY_DEAD, SCENE_OWNERSHIP_MISMATCH, UUID_MISMATCH,
        ACTOR_UNAVAILABLE, AMBIENT_AUTHORITY_OPEN, CARRIER_CONFLICT
    }
    static boolean hasInactiveCarrier(ServerLevel level, FrontierWorldState state, SubjectId actorId) {
        return FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).hasCarrier(actorId);
    }
    private static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body,
                                                                                      boolean discard) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return Optional.empty();
        String rawActorId = body.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return Optional.empty();
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return Optional.empty(); }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(state, actorId).equals(body.getUUID())
                || !owned(body, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT) return Optional.empty();
        BodyPosition position = observedBody(body);
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.TRANSIT) {
            if (journey == null) return Optional.empty();
            BodyPosition cursor = BodyPosition.above(new SurfaceAnchor(journey.currentPosition()));
            if (!position.equals(cursor)) return Optional.empty();
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : member.currentSurface().standingBody();
            if (cursor == null || !position.equals(cursor)) return Optional.empty();
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : BodyPosition.above(new SurfaceAnchor(member.currentPosition()));
            if (cursor == null || !position.equals(cursor)) return Optional.empty();
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) {
            HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : member.currentSurface().standingBody();
            if (cursor == null || !position.equals(cursor)) return Optional.empty();
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.HIVE_TASK_RETURN) {
            BodyPosition cursor = FrontierV3HiveReturnMotion.currentCursor(state, actorId, state.ambientLeases().get(actorId));
            if (cursor == null || !position.equals(cursor)) return Optional.empty();
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.SCOUT_PATROL) {
            position = observedBody(body);
        }
        FixedScalar health = new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE));
        if (!(submit(runtime, "ambient-draining", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return Optional.empty();
        FrontierWorldState drained = runtime.decodedState().orElse(null);
        if (drained == null || drained.ambientLeases().get(actorId) == null
                || drained.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return Optional.empty();
        if (!(submit(runtime, "ambient-release", actorId.value(), new AmbientLeaseReleased(actorId, position, health))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return Optional.empty();
        FrontierWorldState released = runtime.decodedState().orElse(null);
        if (released == null || released.ambientLeases().get(actorId) == null
                || released.ambientLeases().get(actorId).status() != AmbientLeaseStatus.CLOSED) return Optional.empty();
        if (discard) body.discard();
        return Optional.of(new FrontierV3AmbientAdmissionPolicy.EffectResult(drained, released));
    }
    static Optional<FrontierV3AmbientAdmissionPolicy.EffectResult> abandonPreparedForReservation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                                    FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        Entity body = level.getEntity(entityId(state, actorId));
        if (body != null && (!(body instanceof Mob mob) || !owned(mob, actorId, bioform(state, actorId))
                || !observedBody(mob).equals(lease.handoffBody()))) return Optional.empty();
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
        if (body != null) body.discard();
        return Optional.of(new FrontierV3AmbientAdmissionPolicy.EffectResult(drained, resulting));
    }
    private static boolean drainReservedColdContinuation(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                          FrontierWorldState state, SubjectId actorId, Mob body,
                                                          AmbientActorLease lease) {
        if (!lease.equals(state.ambientLeases().get(actorId))
                || !FrontierV3SurfaceObservation.at(body, lease.handoffBody().supportingSurface())) return false;
        if (!(submit(runtime, "ambient-reserved-cold-draining", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        FrontierWorldState draining = runtime.decodedState().orElse(null);
        if (draining == null || draining.ambientLeases().get(actorId) == null
                || draining.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return false;
        var condition = draining.actorLocations().get(actorId);
        if (condition == null || condition.condition().status() != ActorLifeStatus.ALIVE) return false;
        if (!(submit(runtime, "ambient-reserved-cold-release", actorId.value(),
                new AmbientLeaseReleased(actorId, lease.handoffBody(), condition.condition().health()))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        FrontierWorldState released = runtime.decodedState().orElse(null);
        if (released == null || released.ambientLeases().get(actorId) == null
                || released.ambientLeases().get(actorId).status() != AmbientLeaseStatus.CLOSED) return false;
        body.discard();
        return true;
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
    private static boolean drainAfterDemandHysteresis(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
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
    private static boolean drainObservedAfterDemandHysteresis(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                               SubjectId actorId) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (absentSince.size() >= FrontierV3AmbientPendingAdmissions.MAX_ENTRIES && !absentSince.containsKey(actorId)) return false;
        long started = absentSince.computeIfAbsent(actorId, ignored -> level.getGameTime());
        var observed = FrontierV3AmbientActorCaches.lastObserved(runtime, actorId);
        if (observed == null || level.getGameTime() - started < DRAIN_HYSTERESIS_TICKS
                || playerWithin(level, minecraftBody(observed.body()), DRAIN_SAFE_RADIUS_BLOCKS)) return false;
        return false;
    }
    private static boolean observeDirectedArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) return observeAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) return observeEngineeringAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) return observeHiveAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_RETURN) return FrontierV3HiveReturnMotion.observeArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL) return observeScoutPatrolArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.WORK) {
            return FrontierV3SurfaceObservation.at(body, lease.goalBody().supportingSurface())
                    && drainAfterDemandHysteresis(level, runtime, actorId, body);
        }
        if (lease.goal() != AmbientGoalKind.TRANSIT) return false;
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) return false;
        if (!observedBody(body).equals(lease.goalBody())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-transit", actorId.value(),
                new ResidentTransitAdvanced(actorId, journey.nextRouteIndex()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-transit:" + actorId.value(), "resident_transit_advanced", actorId, result);
        return true;
    }
    private static boolean observeAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        OperationAssembly.Member member = assemblyMember(state, actorId, lease);
        if (member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        RouteOperation operation = assemblingOperation(state, actorId); OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            body.getNavigation().stop();
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly", actorId.value(),
                new OperationAssemblyAdvanced(operation.id(), assembly.advance(actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeEngineeringAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                              FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || member == null || member.arrived()
                || !observedBody(body).equals(lease.goalBody())) return false;
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-engineering-assembly", actorId.value(),
                assemblyAdvanced(project, assembly.advance(actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "engineering:" + project.id().value(),
                "engineering_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeHiveAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
        if (mobilization == null || member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            body.getNavigation().stop();
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly", actorId.value(),
                new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, member.cursor()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                "hive_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeScoutPatrolArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                      SubjectId actorId, Mob body, AmbientActorLease lease) {
        BlockPosition current = state.actorLocations().get(actorId).supportingSurface().support();
        if (!observedBody(body).equals(lease.goalBody())) return false;
        BlockPosition expected = HiveScoutPatrolProcess.nextPosition(state, actorId, current);
        if (!lease.goalBody().supportingSurface().support().equals(expected)) {
            io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol-recover", actorId.value(),
                    new ScoutPatrolLeaseRecovered(actorId, current, lease.goalBody().supportingSurface().support(),
                            HiveScoutPatrolProcess.nextPosition(state, actorId, lease.goalBody().supportingSurface().support())));
            FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_lease_recovered", actorId, result);
            return true;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol", actorId.value(),
                new ScoutPatrolAdvanced(actorId, runtime.canonicalState().orElseThrow().instant().ticks(), lease.goalBody().supportingSurface().support(), java.util.Optional.of(current)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_advanced", actorId, result);
        return true;
    }
    private static RouteOperation assemblingOperation(FrontierWorldState state, SubjectId actorId) {
        return state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .findFirst().orElse(null);
    }
    private static BlockPosition assemblyObstruction(ServerLevel level, FrontierWorldState state, RouteOperation operation, BlockPosition target) {
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, target)) return target;
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(operation.settlementId())).findFirst().orElse(null);
        SettlementStructure hall = settlement == null ? null : settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst().orElse(null);
        BlockPosition throat = hall == null ? null : SettlementAccessPort.forHall(hall).throatSurface().support();
        return throat != null && !FrontierV3StandingPosition.hasExactStandingColumn(level, throat) ? throat : null;
    }
    private static OperationAssembly.Member assemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        RouteOperation operation = assemblingOperation(state, actorId);
        if (operation == null || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY) return null;
        OperationAssembly.Member member = operation.activeAssembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static EngineeringWorkOrder engineeringProject(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .filter(project -> project.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("engineering assembly owner is ambiguous"); }).orElse(null);
    }
    private static EngineeringWorkAssembly.Member engineeringAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY) return null;
        EngineeringWorkAssembly.Member member = project.assembly().orElseThrow().members().get(actorId);
        BlockPosition expected = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
        return lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(expected))) ? member : null;
    }
    private static HiveMobilization assemblingMobilization(FrontierWorldState state, SubjectId actorId) {
        return state.hiveColony().mobilizations().values().stream()
                .filter(mobilization -> mobilization.status() == HiveMobilizationStatus.ASSEMBLING)
                .filter(mobilization -> mobilization.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("ambient bioform belongs to more than one hive assembly"); })
                .orElse(null);
    }
    private static HiveTaskAssembly.Member hiveAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        if (mobilization == null || lease.goal() != AmbientGoalKind.HIVE_TASK_ASSEMBLY) return null;
        HiveTaskAssembly.Member member = mobilization.assembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static FrontierPayload assemblyAdvanced(EngineeringWorkOrder project, EngineeringWorkAssembly assembly) {
        return switch (project) {
            case RouteConstruction construction -> new RouteConstructionAssemblyAdvanced(construction.id(), assembly);
            case RouteMaintenance maintenance -> new RouteMaintenanceAssemblyAdvanced(maintenance.id(), assembly);
        };
    }
    private static BlockPos minecraftFloor(BlockPosition floor) { return new BlockPos(floor.x(), floor.y(), floor.z()); }
    private static BlockPos minecraftBody(BodyPosition body) { return new BlockPos(body.x(), body.y(), body.z()); }
    static BodyPosition observedBody(Entity entity) { return new BodyPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()); }
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
