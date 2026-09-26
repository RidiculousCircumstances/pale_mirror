package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteLedger.ProjectionMode;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteExecutor.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteProjectionAdmission.*;

/** Fair, bounded recovery of naturally loaded canonical field projections. */
final class FrontierV3ResourceSiteRestartDispatcher {
    private FrontierV3ResourceSiteRestartDispatcher() { }

    static void reconcileOneAfterRestart(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Set<SubjectId> pending = RECOVERY_SITES.get(runtime); if (pending == null || pending.isEmpty()) return;
        var sites = state.resourceSiteDescriptors();
        var selected = nextRecoverySite(pending, RECOVERY_TURNS.computeIfAbsent(runtime, ignored -> new FrontierV3FairTurn<>()), id -> {
            ResourceSite site = sites.get(id);
            return site != null && loaded(level, site) && FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, site);
        });
        if (selected.isPresent()) {
            SubjectId siteId = selected.orElseThrow();
            ResourceSite site = state.resourceSite(siteId);
            var siteClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(siteId);
            if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim) {
                // A pending initialization resumes through the preparation intent; active
                // cells resume through the bounded projector/work owner. Neither has a
                // legacy stage claim for the whole-field restart classifier to inspect.
                pending.remove(siteId);
                if (pending.isEmpty()) { RECOVERY_SITES.remove(runtime); RECOVERY_TURNS.remove(runtime); }
                return;
            }
            if (siteClaim == null && baseline(level, site)) {
                // A fresh canonical field may already be mature (or have an active COLD
                // harvest) before its first physical visit.  The restart classifier ran
                // before the ordinary projector and used to mint a legacy stage/prefix
                // claim for this exact neutral surface.  That claim can no longer carry
                // paired HOT crop/hand work.  Leave the untouched surface to the same
                // durable cell-initialization owner used by ordinary first ingress.
                // A non-neutral or ambiguous surface still takes the restart path below.
                pending.remove(siteId);
                if (pending.isEmpty()) { RECOVERY_SITES.remove(runtime); RECOVERY_TURNS.remove(runtime); }
                return;
            }
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(siteId);
            PhysicalIntent intent = state.physicalIntents().values().stream().filter(candidate -> candidate.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION
                    && candidate.status() == PhysicalIntentStatus.CONFIRMED && candidate.causeSubjectId().equals(siteId)).findFirst().orElse(null);
            RestartReconciliation result;
            ResourceSiteHarvestJob harvest = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                    .map(ResourceSiteHarvestJob.class::cast).orElse(null);
            if (harvest != null && harvest.progress().completedCropSlots() > 0) {
                FrontierV3ResourceSiteLedger.Claim claim = FrontierV3ResourceSiteLedger.get(level).claim(site.id());
                Optional<ResourceSiteHarvestJob> unmaterialized = exactUnmaterializedColdHarvest(level, state, site,
                        ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                if (claim != null && claim.projection() != null) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else if (isExactSuccessorRegrowth(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                        harvest.progress().completedCropSlots(), claim)
                        || isExactTerminalPredecessor(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || isExactInterruptedStageZeroProjection(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || isExactUncommittedCurrentHarvest(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || unmaterialized.isPresent()) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else if (projectionInFlight(runtime, site.id())) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else {
                    HarvestRestartClassification classification = classifyHarvestRestart(level, FrontierV3ResourceSiteLedger.get(level), site, intent,
                            harvest.progress().completedCropSlots());
                    result = switch (classification.state()) {
                        case UNOBSERVED -> RestartReconciliation.DEFERRED;
                        case OWNED_EXACT -> RestartReconciliation.CURRENT;
                        case OWNED_BEHIND -> boundedRestartProjection(level, runtime, state, site,
                                ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                        case NEUTRAL_UNCLAIMED -> intent == null ? RestartReconciliation.CONFLICT
                                : boundedRestartProjection(level, runtime, state, site,
                                        ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                        case FOREIGN_OR_DAMAGED, UNKNOWN -> RestartReconciliation.CONFLICT;
                    };
                }
            } else if (harvest != null && isExactSuccessorRegrowth(level, state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (isExactDeferredHarvestReceipt(level, state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = RestartReconciliation.DEFERRED;
            } else if (isExactConfirmedHarvestRegrowth(level, state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (isExactComposedTerminalRegrowth(lifecycle, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id())
                    , FrontierV3ResourceSiteLedger.get(level).claim(site.id()) != null
                    && matchesClaim(level, site, FrontierV3ResourceSiteLedger.get(level).claim(site.id())))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (allowsOwnedStageCatchUp(FrontierV3ResourceSiteLedger.get(level).claim(site.id()),
                    lifecycle.growthStage(), 0)
                    && matchesClaim(level, site, FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (admitsMissingComposedTerminalRehydration(FrontierV3ResourceSiteLedger.get(level).claim(site.id()),
                    lifecycle, lifecycle.growthStage(), 0,
                    matches(level, site, lifecycle.growthStage())
                            || (lifecycle.growthStage() < ResourceSiteLifecycle.MATURE_STAGE
                            && matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS))
                            || exactComposedGrowthPredecessorStage(level, site, lifecycle.growthStage()) >= 0,
                    lifecycle.harvestLineage().map(lineage -> lineage.composedIntoCanonicalSuccessor(state)).orElse(false))) {
                // This re-enters projectLifecycleBounded, which persists the exact terminal
                // predecessor claim before writing its successor through the normal eight-cell
                // bounded cursor.  A retained preparation intent is not competing ownership.
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (intent == null) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else result = reconcileAfterRestart(level, FrontierV3ResourceSiteLedger.get(level), site, intent.id(), lifecycle.growthStage());
            if (result != RestartReconciliation.DEFERRED) pending.remove(siteId);
            if (result == RestartReconciliation.CONFLICT) {
                FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
                FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
                FrontierV3ResourceSiteConflictExecutor.recordLifecycleConflict(level, runtime, ledger, site,
                        firstMismatch(level, site, lifecycle.growthStage()).orElse(site.cropSlots().getFirst()),
                        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.RESTART_OBSERVATION_MISMATCH, LifecycleConflictOrigin.RESTART_RECONCILIATION,
                        "NOT_EVALUATED", claimPhysicalState(level, ledger, site, claim));
            }
            if (pending.isEmpty()) { RECOVERY_SITES.remove(runtime); RECOVERY_TURNS.remove(runtime); }
            return;
        }
    }
    /** A deferred reconciliation retains its record, not exclusive ownership of every turn. */
    static Optional<SubjectId> nextRecoverySite(Set<SubjectId> pending, FrontierV3FairTurn<SubjectId> turns,
                                               java.util.function.Predicate<SubjectId> eligible) {
        return turns.next(pending.stream().filter(eligible).toList(), java.util.function.Function.identity());
    }
    private static RestartReconciliation boundedRestartProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                   FrontierWorldState state, ResourceSite site, int desiredStage, int completedCropSlots) {
        StageProjectionResult projection = projectLifecycleBounded(level, runtime, state, site, desiredStage, completedCropSlots);
        return projection == StageProjectionResult.CONFLICT ? RestartReconciliation.CONFLICT
                : projection == StageProjectionResult.DEFERRED ? RestartReconciliation.DEFERRED : RestartReconciliation.CURRENT;
    }
}
