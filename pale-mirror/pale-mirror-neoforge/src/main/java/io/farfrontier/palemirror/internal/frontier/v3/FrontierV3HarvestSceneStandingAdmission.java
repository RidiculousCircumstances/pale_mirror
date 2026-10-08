package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestScenePreparationAborted;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Exact-body physical gate for the harvest scene's reversible admission boundary. */
final class FrontierV3HarvestSceneStandingAdmission {
    private FrontierV3HarvestSceneStandingAdmission() { }

    static boolean abortObstructedBodyFreePreparation(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        if (!lease.ambientHandoffActorIds().isEmpty() || lease.members().size() != 1
                || lease.recoveryEvidence().isPresent()) return false;
        SceneMember member = lease.members().getFirst();
        var binding = state.fencedRecovery().current().get(
                ActorBodyId.recoveryBindingId(member.actorId()));
        if (binding == null || binding.phase() != FencedRecoveryPhase.PREPARED) return false;
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        if (lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CONFLICT) {
            var job = state.resourceSites().site(cause.siteId()).harvestJob(cause.jobId()).orElse(null);
            var field = FrontierV3ResourceSiteLedger.get(level);
            var position = lease.memberBody(state.actorLocations(), member.actorId());
            if (job == null || job.progress().hasPendingPhysicalWork()
                    || state.resourceSites().hasPendingWorldChange(cause.siteId())
                    || field.fieldDelivery(cause.siteId()) != null || field.fieldHandProjection(cause.siteId()) != null
                    || FrontierSceneLeaseStateSupport.hasBoundActorHand(state, lease)
                    || !FrontierV3SceneExecutor.entityStorageReady(level, new BlockPos(position.x(), position.y() - 1, position.z()))
                    || !FrontierV3ActorBodyCustody.unstartedAbsenceProven(level, runtime, state, member.actorId())) return false;
        } else if (!obstructedBodyFreeColumn(level, member.entityId(), lease.memberBody(state.actorLocations(), member.actorId()))) return false;
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "resource-site-harvest-preparation-aborted",
                lease.id().value(), new ResourceSiteHarvestScenePreparationAborted(lease.id(), cause.siteId(), cause.jobId(),
                        new ActorBodyId(member.actorId(), binding.authorityEpoch())));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_preparation_aborted", lease, result);
        if (result instanceof CommandResult.Accepted)
            FrontierV3ActorBodyCustody.releaseUnstartedAbsence(level, runtime, member.actorId());
        return result instanceof CommandResult.Accepted;
    }

    /** Wait for unloaded entity storage or an absent projected floor; deny only a hard body obstruction. */
    static boolean obstructedBodyFreeColumn(ServerLevel level, UUID entityId, BodyPosition body) {
        BlockPos floor = new BlockPos(body.x(), body.y() - 1, body.z());
        if (!FrontierV3SceneExecutor.entityStorageReady(level, floor)
                || level.getEntity(entityId) != null
                || FrontierV3ResourceSiteHarvestSceneExecutor.harvestStandingPosition(level, floor) != null) return false;
        BlockPos feet = floor.above();
        return hardBodyObstruction(level, feet) || hardBodyObstruction(level, feet.above());
    }

    private static boolean hardBodyObstruction(ServerLevel level, BlockPos cell) {
        return !level.getFluidState(cell).isEmpty()
                || level.getBlockState(cell).getCollisionShape(level, cell).max(Direction.Axis.Y) > 0.125D;
    }
}
