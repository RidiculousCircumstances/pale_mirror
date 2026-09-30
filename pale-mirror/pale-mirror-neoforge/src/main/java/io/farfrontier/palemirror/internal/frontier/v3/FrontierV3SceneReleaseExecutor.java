package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.OperationFront;
import io.farfrontier.palemirror.frontier.v3.model.ActorDirective;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravelAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3SceneExecutor.*;

/** Atomic physical release of an owned HOT scene back into canonical custody. */
final class FrontierV3SceneReleaseExecutor {
    private FrontierV3SceneReleaseExecutor() { }

    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease) {
        release(level, runtime, selectedLease, java.util.Optional.empty());
    }
    /** Generic lifecycle release; an enforced descriptor may supply one exact engine action binding. */
    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease,
                        java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        SceneLease lease = state.sceneLeases().get(selectedLease.id());
        if (lease == null || lease.status() != SceneLeaseStatus.DRAINING) return;
        if (FrontierSceneBehaviors.isProductionWork(lease)) {
            ProductionJob bakeryJob = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
            if (bakeryJob != null && bakeryJob.bakeryWork().isPresent()
                    && bakeryJob.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()) return;
        }
        var unfinishedStrike = state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                        && (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART))
                .filter(intent -> io.farfrontier.palemirror.frontier.v3.model.SceneStrikeStateSupport.boundTo(lease, intent))
                .min(Comparator.comparing(PhysicalIntent::id));
        if (unfinishedStrike.isPresent()) {
            executeStrike(level, runtime, state, lease, unfinishedStrike.orElseThrow().lifecycleOwner());
            return; // Re-read the resulting authority next turn before releasing any body.
        }
        if (FrontierV3SceneReleaseReadiness.awaitingEntityStorage(level, state, lease)) return;
        boolean hasCargoCarrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease);
        RouteOperation operation = hasCargoCarrier ? state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()) : null;
        boolean interrupted = hasCargoCarrier && operation != null && operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.INTERRUPTED;
        // Release requires current physical evidence, never a historical HOT sample.
        if (hasCargoCarrier && !interrupted && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) {
            var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
            var id = FrontierV3CargoCarrierExecutor.id(lease);
            var receipt = ledger.observation(id);
            if (level.getEntity(id) != null || ledger.conflicted(id) || receipt.isEmpty()
                    || !FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt.orElseThrow(), level.registryAccess())) {
                conflict(level, runtime, state, lease, "release-carrier-unavailable"); return;
            }
            // The unload callback alone is not proof that vanilla saved the cart. Wait
            // for the exact entity-region write and synchronization before cold custody.
            if (!ledger.savedObservation(receipt.orElseThrow())) return;
        }
        List<SceneMemberPosition> positions = new ArrayList<>();
        List<SceneMember> departedMembers = new ArrayList<>();
        List<Mob> retireLoadedBodies = new ArrayList<>();
        List<FrontierV3AmbientCarrierLedger.Carrier> releaseCarriers = new ArrayList<>();
        var actorLedger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var releasePolicy = FrontierV3SceneBehaviorRegistry.bodyReleasePolicy(lease.cause().kind());
        boolean retainVisible = releasePolicy
                .retainsLiveBody(demandExists(level, lease.handoffPosition()) || playerWithinSafeRadius(level, lease));
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (entity == null) {
                var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
                if (ledger.departure(member.actorId()).isPresent()
                        && !ledger.savedDeparture(ledger.departure(member.actorId()).orElseThrow())
                        && !ledger.hasDepartureConflict(member.actorId())
                        && FrontierV3SceneDepartureObserver.observedDeparture(state, lease, member, ledger).isPresent()) return;
                var receipt = FrontierV3SceneDepartureObserver.validDeparture(state, lease, member, ledger);
                if (receipt.isEmpty()) { conflict(level, runtime, state, lease, "release-departure-unproven"); return; }
                positions.add(receipt.orElseThrow().observed());
                departedMembers.add(member);
                releaseCarriers.add(receipt.orElseThrow().carrier());
                continue;
            }
            if (FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).departure(member.actorId()).isPresent()) {
                conflict(level, runtime, state, lease, "release-concurrent-departure-evidence"); return;
            }
            if (!owned(entity, state, lease, member)) {
                conflict(level, runtime, state, lease, "release-body-unavailable"); return;
            }
            if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F) {
                conflict(level, runtime, state, lease, "release-body-dead"); return;
            }
            if (!FrontierV3BakeryHandProjection.matchesCurrent(state, lease, member, body)) {
                conflict(level, runtime, state, lease, "release-bakery-hand-mismatch"); return;
            }
            long health = Math.round((double) body.getHealth() * FixedScalar.SCALE);
            var supported = releasePolicy.captureReleasedBody(level, body);
            if (supported.isEmpty()) return; // keep DRAINING until a real supported body can be captured
            BodyPosition observedBody = supported.orElseThrow();
            positions.add(new SceneMemberPosition(member.actorId(), observedBody, new FixedScalar(health)));
            if (!retainVisible) {
                var live = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
                var ambient = state.ambientLeases().get(member.actorId());
                if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED) {
                    releasePolicy.conflict(level, runtime, state, lease, "release-ambient-authority-open"); return;
                }
                releaseCarriers.add(new FrontierV3AmbientCarrierLedger.Carrier(live.inactiveCarrier(),
                        Math.max(1L, lease.revision()), ambient == null ? 0L : ambient.revision()));
                retireLoadedBodies.add(body);
            }
        }
        if (!actorLedger.canFenceAll(releaseCarriers)) {
            releasePolicy.conflict(level, runtime, state, lease, "release-survivor-fence-conflict"); return;
        }
        // A field worker may still carry an already-accounted HOT wheat part. Its physical
        // offhand, fungible binding and scene exit must close in the same WAL transition;
        // the generic release deliberately rejects a bound hand.
        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHandRelease harvestHandRelease = null;
        io.farfrontier.palemirror.frontier.v3.model.BakeryHotHandRelease bakeryHandRelease = null;
        if (io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport.hasBoundActorHand(state, lease)) {
            if (FrontierSceneBehaviors.isProductionWork(lease)) {
                ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
                if (job == null || job.bakeryWork().isEmpty() || lease.members().size() != 1) {
                    releasePolicy.conflict(level, runtime, state, lease, "release-bakery-hand-without-owner"); return;
                }
                var work = job.bakeryWork().orElseThrow();
                Entity carrier = level.getEntity(lease.members().getFirst().entityId());
                if (!(carrier instanceof Mob worker) || !owned(carrier, state, lease, lease.members().getFirst())) {
                    releasePolicy.conflict(level, runtime, state, lease, "release-bakery-hand-body-unavailable"); return;
                }
                var held = worker.getMainHandItem();
                var bindings = state.inventory().fungibleResources().bindings().values().stream()
                        .filter(value -> value.accountId().equals(work.actorAccountId())).toList();
                if (bindings.size() != 1 || held.isEmpty()) {
                    releasePolicy.conflict(level, runtime, state, lease, "release-bakery-hand-binding-unavailable"); return;
                }
                bakeryHandRelease = new io.farfrontier.palemirror.frontier.v3.model.BakeryHotHandRelease(
                        job.id(), work.actorAccountId(), bindings.getFirst().authorityEpoch(),
                        new io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation.Stack(
                                new io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ActorHand(
                                        job.workerId(), lease.members().getFirst().entityId()),
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount()),
                        new SceneLeaseReleased(lease.id(), positions));
                try {
                    io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.reduceBakeryHotHandRelease(
                            state, job.settlementId(), bakeryHandRelease);
                } catch (IllegalArgumentException invalid) {
                    releasePolicy.conflict(level, runtime, state, lease,
                            "release-bakery-hand-preflight:" + invalid.getMessage()); return;
                }
            } else if (!releasePolicy.permitsBoundActorHand() || lease.members().size() != 1) {
                releasePolicy.conflict(level, runtime, state, lease, "release-bound-hand-without-typed-owner"); return;
            } else {
            var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
            var job = io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport.require(state, cause);
            Entity carrier = level.getEntity(lease.members().getFirst().entityId());
            FrontierV3SceneDeparture.HandStack hand;
            if (carrier instanceof Mob worker && owned(carrier, state, lease, lease.members().getFirst())) {
                var held = worker.getOffhandItem();
                if (held.isEmpty() || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(held,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, held.getCount()))) {
                    FrontierV3ResourceSiteHarvestSceneExecutor.releaseCustodyConflict(level, runtime, state, lease,
                            "bound-hand-release-physical-foreign"); return;
                }
                hand = new FrontierV3SceneDeparture.HandStack("minecraft:wheat", held.getCount());
            } else if (carrier == null && departedMembers.contains(lease.members().getFirst())) {
                var departure = FrontierV3SceneDepartureObserver.validDeparture(state, lease,
                        lease.members().getFirst(), actorLedger).orElse(null);
                if (departure == null || departure.offhand().isEmpty()
                        || !departure.offhand().orElseThrow().itemKind().equals("minecraft:wheat")) {
                    FrontierV3ResourceSiteHarvestSceneExecutor.releaseCustodyConflict(level, runtime, state, lease,
                            "bound-hand-release-saved-hand-unavailable"); return;
                }
                hand = departure.offhand().orElseThrow();
            } else {
                FrontierV3ResourceSiteHarvestSceneExecutor.releaseCustodyConflict(level, runtime, state, lease,
                        "bound-hand-release-body-unavailable"); return;
            }
            harvestHandRelease = new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHandRelease(
                    job.siteId(), job.id(), job.actorAccountId(), lease.revision(),
                    new io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation.Stack(
                            new io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ActorHand(
                                    job.workerId(), lease.members().getFirst().entityId()), hand.itemKind(), hand.quantity()),
                    new SceneLeaseReleased(lease.id(), positions));
            try {
                io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceHandRelease(
                        state, state.resourceSite(job.siteId()).settlementId(), harvestHandRelease);
            } catch (IllegalArgumentException invalid) {
                FrontierV3ResourceSiteHarvestSceneExecutor.releaseCustodyConflict(level, runtime, state, lease,
                        "bound-hand-release-preflight:" + invalid.getMessage()); return;
            }
            }
        }
        if (!FrontierV3CargoDepartureObserver.prepareRelease(level, state, lease)) return;
        if (!actorLedger.fenceAll(releaseCarriers)) {
            releasePolicy.conflict(level, runtime, state, lease, "release-survivor-fence-conflict"); return;
        }
        if (!releaseCarriers.isEmpty()) actorLedger.persist(level, state.bootstrap().worldId());
        CommandResult result = releaseLoaded(runtime, lease, positions, binding, harvestHandRelease, bakeryHandRelease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_released", lease, result);
        if (result instanceof CommandResult.Accepted) {
            for (Mob body : retireLoadedBodies) {
                FrontierV3ControlledMobMotion.stop(body);
                body.discard();
            }
            for (SceneMember member : departedMembers) {
                FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).forgetDeparture(member.actorId());
            }
        }
    }
    static void releaseCustodyConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, SceneLease lease, String reason) {
        conflict(level, runtime, state, lease, reason);
    }
    /** A rejected release retains its same observation for diagnosis; it is never rewritten or guessed. */
    private static CommandResult releaseLoaded(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                               List<SceneMemberPosition> positions,
                                               java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding,
                                               io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHandRelease harvestHandRelease,
                                               io.farfrontier.palemirror.frontier.v3.model.BakeryHotHandRelease bakeryHandRelease) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload = bakeryHandRelease != null
                ? bakeryHandRelease : harvestHandRelease == null ? new SceneLeaseReleased(lease.id(), positions) : harvestHandRelease;
        CommandResult result = binding.map(action -> FrontierV3CommandSubmission.submitBound(runtime, "scene-release", lease.id().value(),
                        payload, action))
                .orElseGet(() -> submit(runtime, "scene-release", lease.id().value(), payload));
        if (result instanceof CommandResult.Accepted) forgetLeaseTransient(runtime, lease.id());
        return result;
    }
}
