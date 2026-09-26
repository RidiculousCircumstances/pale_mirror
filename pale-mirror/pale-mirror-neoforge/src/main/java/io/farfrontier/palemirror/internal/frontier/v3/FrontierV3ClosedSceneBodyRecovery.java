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

/** Reconciliation of retained exact bodies after their scene has closed. */
final class FrontierV3ClosedSceneBodyRecovery {
    private FrontierV3ClosedSceneBodyRecovery() { }

    static void cleanClosedBodies(ServerLevel level, FrontierWorldState state) {
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED).forEach(lease -> lease.members().forEach(member -> {
            Entity entity = level.getEntity(member.entityId());
            // Historical cleanup still requires this exact lease, revision, UUID,
            // actor and body kind; a newer same-actor scene is not its projection.
            if (!ownedByClosedLease(entity, state, lease, member)) return;
            var actor = state.actorLocations().get(member.actorId());
            var live = FrontierV3ActorCarrierComposition.declaredBy(entity).orElseThrow();
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            // Covers a crash after canonical close but before physical discard.
            // A completed inactive disposition wins over presentation retention,
            // including harvest; it may never leave both representations current.
            if (actor.condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD
                    || ledger.fencesBody(live)) {
                if (entity instanceof Mob body) FrontierV3ControlledMobMotion.stop(body);
                entity.discard();
                return;
            }
            if (FrontierV3ResourceSiteHarvestSceneExecutor.retainsClosedBody(entity, state, lease, member)) {
                // A player-visible terminal/successor hand-off retains its body while demand
                // remains.  Once no natural interaction eligibility exists, fence exactly one
                // same-ID/same-UUID inactive carrier and remove this live physical custodian so
                // COLD can lawfully advance.  A failed fence is deliberately left visible and
                // blocks re-admission as local ambiguity.
                if (!demandExists(level, lease.handoffPosition()) && !playerWithinSafeRadius(level, lease)) {
                    FrontierV3AmbientActorExecutor.fenceClosedSceneBody(level, state, lease, member, entity);
                }
                return;
            }
            // A canonical stale-body tombstone alone is not reconstruction evidence.
            // Preserve an unfenced historical survivor instead of erasing it.
        }));
        // Exact cargo obligations survive historical scene compaction. Assault/body
        // history is neither an admission source nor a prerequisite for this cleanup.
        state.fencedRecovery().cargoRetirements().pending().values()
                .forEach(retirement -> FrontierV3CargoCarrierExecutor.cleanRetired(level, state, retirement));
    }

    /**
     * Transfers the one retained closed resource body into a newly PREPARED ambient lease.
     * This is a live-body hand-off, not carrier reconstruction: the exact UUID remains loaded
     * and there is no inactive carrier.  Any other closed projection stays historical/stale and
     * is deliberately not eligible for this conversion.
     */
    static boolean adoptRetainedClosedBodyForAmbient(Entity entity, FrontierWorldState state, SubjectId actorId) {
        if (!(entity instanceof Mob body) || entity.isRemoved()
                || !FrontierV3AmbientActorExecutor.entityId(state, actorId).equals(entity.getUUID())) return false;
        List<SceneLease> matches = state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)
                        && FrontierV3ResourceSiteHarvestSceneExecutor.retainsClosedBody(entity, state, lease, member)))
                .toList();
        if (matches.size() != 1) return false;
        var ambient = state.ambientLeases().get(actorId);
        var from = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        if (ambient == null || ambient.status() != AmbientLeaseStatus.PREPARED || from == null
                || !(entity.level() instanceof ServerLevel level)) return false;
        var target = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, entity.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, ambient.revision(), from.epoch());
        FrontierV3ControlledMobMotion.stop(body);
        return FrontierV3ActorHandoffAdmission.transfer(level, state.bootstrap().worldId(), entity, FrontierV3ActorOwnerBinding.ambient(target));
    }

    /**
     * Exact closed bodies waiting for their next shared ambient custody epoch.  This is a
     * registry query, rather than a resource-site scheduler: the typed behavior supplies the
     * retention predicate while the common ambient owner decides the return.  It is bounded by
     * the live closed-lease inventory and considers only naturally demanded hand-off anchors.
     */
    static List<SubjectId> retainedClosedActorsDemandedBy(ServerLevel level, FrontierWorldState state) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED)
                // The exact loaded closed body retains its shared ambient return attempt;
                // this registry neither loads a chunk nor creates a replacement body.
                .flatMap(lease -> lease.members().stream().filter(member -> {
                    Entity entity = level.getEntity(member.entityId());
                    return FrontierV3ResourceSiteHarvestSceneExecutor.retainsClosedBody(entity, state, lease, member);
                }).map(SceneMember::actorId)).distinct().sorted().toList();
    }

    /**
     * Read-only return gate. Empty local storage is not a physical generation or
     * historical release receipt. Only an already retained exact carrier permits
     * reconstruction; missing legacy history requires explicit recovery.
     */
    static ClosedSceneReturnRecovery inspectClosedHarvestReturn(ServerLevel level, FrontierWorldState state, SubjectId actorId) {
        List<SceneLease> matches = state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)
                        && member.entityId().equals(FrontierV3AmbientActorExecutor.entityId(state, actorId))))
                .sorted(Comparator.comparingLong(SceneLease::revision).reversed()).toList();
        if (matches.isEmpty()) return ClosedSceneReturnRecovery.NOT_RETAINED;
        if (matches.size() > 1 && matches.get(0).revision() == matches.get(1).revision()) return ClosedSceneReturnRecovery.CONFLICT;
        SceneLease lease = matches.getFirst();
        SceneMember member = lease.members().stream().filter(value -> value.actorId().equals(actorId)).findFirst().orElseThrow();
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        Entity existing = level.getEntity(member.entityId());
        if (existing != null) {
            // Closed history is not current custody. After the actual handoff the
            // ordinary ambient loop must execute this same body, not skip it forever.
            if (FrontierV3AmbientCarrierRecognition.recoverableOwnership(state,
                    FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(existing), ledger)) {
                return ClosedSceneReturnRecovery.NOT_RETAINED;
            }
            return FrontierV3ResourceSiteHarvestSceneExecutor.retainsClosedBody(existing, state, lease, member)
                    ? ClosedSceneReturnRecovery.LIVE_BODY : ClosedSceneReturnRecovery.CONFLICT;
        }
        var actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) return ClosedSceneReturnRecovery.CONFLICT;
        BlockPos current = new BlockPos(actor.body().x(), actor.body().y(), actor.body().z());
        if (!level.hasChunkAt(current) || !level.areEntitiesLoaded(ChunkPos.asLong(current))) return ClosedSceneReturnRecovery.PENDING;
        if (!ledger.hasCarrier(actorId) || ledger.pendingAdoption(actorId).isPresent()) return ClosedSceneReturnRecovery.CONFLICT;
        var ambient = state.ambientLeases().get(actorId);
        if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED
                && ambient.status() != AmbientLeaseStatus.PREPARED) return ClosedSceneReturnRecovery.CONFLICT;
        long nextRevision = ambient == null ? 1L : ambient.status() == AmbientLeaseStatus.PREPARED
                ? ambient.revision() : Math.addExact(ambient.revision(), 1L);
        var next = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actorId,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, nextRevision, ledger.reconstructionEpoch(actorId));
        return retainedClosedReturnAdmission(ledger, next);
    }
    static ClosedSceneReturnRecovery retainedClosedReturnAdmission(FrontierV3AmbientCarrierLedger ledger,
                                                                   FrontierV3ActorCarrierComposition.Declaration next) {
        return next.owner() == FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE
                && ledger.hasCarrier(next.actorId()) && ledger.pendingAdoption(next.actorId()).isEmpty()
                && next.epoch() == ledger.reconstructionEpoch(next.actorId())
                && ledger.reconciliation(next) == FrontierV3AmbientCarrierLedger.Reconciliation.READY
                ? ClosedSceneReturnRecovery.FENCED : ClosedSceneReturnRecovery.CONFLICT;
    }
}
